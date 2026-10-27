package com.tapledger.data.repository

import com.tapledger.core.database.dao.AccountDao
import com.tapledger.core.database.dao.CategoryDao
import com.tapledger.core.database.dao.LoanDao
import com.tapledger.core.database.dao.TransactionDao
import com.tapledger.core.database.entity.AccountEntity
import com.tapledger.core.database.entity.CategoryEntity
import com.tapledger.core.database.entity.LoanEntity
import com.tapledger.core.model.AccountType
import com.tapledger.core.model.AdvancePolicy
import com.tapledger.core.model.BudgetBucket
import com.tapledger.core.util.DateTimeUtils
import com.tapledger.domain.calculator.AccountBalanceCalculator
import com.tapledger.domain.calculator.LoanCalculator
import kotlinx.coroutines.flow.Flow
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

data class EmiPaymentResult(
    val transferLegId: Long?,
    val interestExpenseId: Long?,
    val principalPaidPaise: Long,
    val interestPaidPaise: Long,
    val totalPaidPaise: Long,
    val remainingPrincipalPaise: Long
)

@Singleton
class LoanRepository @Inject constructor(
    private val loanDao: LoanDao,
    private val accountDao: AccountDao,
    private val categoryDao: CategoryDao,
    private val transactionDao: TransactionDao,
    private val accountRepository: AccountRepository,
    private val transactionRepository: TransactionRepository,
    private val loanCalculator: LoanCalculator
) {

    fun observeAllActiveLoans(): Flow<List<LoanEntity>> = loanDao.observeAllActive()

    fun observeAllLoans(): Flow<List<LoanEntity>> = loanDao.observeAll()

    suspend fun getAllActiveLoansSync(): List<LoanEntity> = loanDao.getAllActiveSync()

    suspend fun getLoanById(id: Long): LoanEntity? = loanDao.getById(id)

    suspend fun getLoanByAccountId(accountId: Long): LoanEntity? = loanDao.getByAccountId(accountId)

    suspend fun createLoan(
        name: String,
        principalPaise: Long,
        annualRateBp: Int,
        tenureMonths: Int,
        emiDay: Int,
        linkedBankAccountId: Long?,
        startDateMs: Long = DateTimeUtils.nowEpochMillis(),
        existingAccountId: Long? = null
    ): LoanEntity {
        require(principalPaise > 0L) { "Loan principal must be positive" }
        require(tenureMonths > 0) { "Tenure must be at least 1 month" }
        require(emiDay in 1..31) { "EMI day must be between 1 and 31" }

        val calculatedEmi = loanCalculator.calculateEmi(principalPaise, annualRateBp, tenureMonths)

        // Resolve or create account with AccountType.LOAN
        val accountId = if (existingAccountId != null) {
            existingAccountId
        } else {
            accountRepository.createAccount(
                name = name,
                type = AccountType.LOAN,
                identifierLast4 = null,
                openingBalancePaise = -principalPaise, // Liabilities are negative or outstanding
                openingBalanceDate = startDateMs
            )
        }

        val now = DateTimeUtils.nowEpochMillis()
        val loan = LoanEntity(
            accountId = accountId,
            name = name,
            principalPaise = principalPaise,
            annualInterestRateBp = annualRateBp,
            tenureMonths = tenureMonths,
            emiDay = emiDay,
            emiAmountPaise = calculatedEmi,
            linkedBankAccountId = linkedBankAccountId,
            startDateMs = startDateMs,
            isActive = true,
            createdAt = now
        )

        val loanId = loanDao.insert(loan)
        return loan.copy(id = loanId)
    }

    /**
     * Executes one-tap Pay EMI:
     * 1. Calculates interest vs principal breakdown for the installment.
     * 2. Transfers principal portion from Bank to Loan Account (reducing liability).
     * 3. Records interest portion as a Needs expense on the Bank account.
     */
    suspend fun payEmi(
        loanId: Long,
        fromAccountId: Long,
        paymentDateMs: Long = DateTimeUtils.nowEpochMillis(),
        customAmountPaise: Long? = null
    ): EmiPaymentResult {
        val loan = loanDao.getById(loanId) ?: throw IllegalArgumentException("Loan not found: $loanId")
        val loanAccount = accountDao.getById(loan.accountId) ?: throw IllegalArgumentException("Loan account not found")

        val txs = transactionDao.getBalanceTransactionsForAccount(loan.accountId, 0L)
        val balanceSummary = AccountBalanceCalculator.calculateAccountBalance(loanAccount, txs, asOfEpochMillis = paymentDateMs)
        // Current outstanding principal (positive magnitude)
        val outstandingPrincipal = kotlin.math.abs(balanceSummary.currentBalancePaise)

        val totalPayment = customAmountPaise ?: loan.emiAmountPaise
        require(totalPayment > 0L) { "Payment amount must be positive" }

        val interestNum = outstandingPrincipal * loan.annualInterestRateBp.toLong()
        val interestDivisor = 120_000L
        val calculatedInterest = (interestNum + (interestDivisor / 2L)) / interestDivisor
        val interestPortion = minOf(totalPayment, calculatedInterest)
        val principalPortion = minOf(outstandingPrincipal, maxOf(0L, totalPayment - interestPortion))

        // 1. Record Principal Transfer (Bank -> Loan account)
        var transferLegId: Long? = null
        if (principalPortion > 0L) {
            val (outId, _) = transactionRepository.recordTransfer(
                fromAccountId = fromAccountId,
                toAccountId = loan.accountId,
                amountPaise = principalPortion,
                occurredAt = paymentDateMs,
                note = "EMI Principal: ${loan.name}"
            )
            transferLegId = outId
        }

        // 2. Record Interest as Needs Expense
        var interestExpenseId: Long? = null
        if (interestPortion > 0L) {
            val interestCategory = getOrCreateLoanInterestCategory()
            interestExpenseId = transactionRepository.recordExpense(
                accountId = fromAccountId,
                amountPaise = interestPortion,
                occurredAt = paymentDateMs,
                merchantRaw = "${loan.name} (Interest)",
                note = "EMI Interest: ${loan.name}",
                allocations = listOf(interestCategory.id to interestPortion)
            )
        }

        val newRemaining = maxOf(0L, outstandingPrincipal - principalPortion)

        // If outstanding principal is cleared, mark loan as inactive/settled
        if (newRemaining == 0L) {
            loanDao.update(loan.copy(isActive = false))
        }

        return EmiPaymentResult(
            transferLegId = transferLegId,
            interestExpenseId = interestExpenseId,
            principalPaidPaise = principalPortion,
            interestPaidPaise = interestPortion,
            totalPaidPaise = principalPortion + interestPortion,
            remainingPrincipalPaise = newRemaining
        )
    }

    /**
     * Executes part-prepayment directly against loan principal.
     */
    suspend fun recordPartPrepayment(
        loanId: Long,
        fromAccountId: Long,
        prepaymentPaise: Long,
        paymentDateMs: Long = DateTimeUtils.nowEpochMillis()
    ): Long {
        require(prepaymentPaise > 0L) { "Prepayment amount must be positive" }
        val loan = loanDao.getById(loanId) ?: throw IllegalArgumentException("Loan not found: $loanId")

        val (outId, _) = transactionRepository.recordTransfer(
            fromAccountId = fromAccountId,
            toAccountId = loan.accountId,
            amountPaise = prepaymentPaise,
            occurredAt = paymentDateMs,
            note = "Part-prepayment: ${loan.name}"
        )
        return outId
    }

    suspend fun getOrCreateLoanInterestCategory(): CategoryEntity {
        val existing = categoryDao.getAllCategoriesSync().firstOrNull {
            it.name.contains("Loan Interest", ignoreCase = true) ||
            it.name.contains("EMI Interest", ignoreCase = true)
        }
        if (existing != null) return existing

        val now = DateTimeUtils.nowEpochMillis()
        val newCat = CategoryEntity(
            name = "Loan Interest & Charges",
            defaultBucket = BudgetBucket.NEEDS,
            defaultSortOrder = 20,
            defaultAdvancePolicy = AdvancePolicy.CARRY_TO_ADVANCE,
            createdAt = now,
            updatedAt = now
        )
        val id = categoryDao.insert(newCat)
        return newCat.copy(id = id)
    }
}
