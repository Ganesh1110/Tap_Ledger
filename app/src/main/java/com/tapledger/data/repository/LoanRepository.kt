package com.tapledger.data.repository

import com.tapledger.core.database.DatabaseTransactionRunner
import com.tapledger.core.database.dao.AccountDao
import com.tapledger.core.database.dao.CategoryDao
import com.tapledger.core.database.dao.LoanDao
import com.tapledger.core.database.dao.TransactionDao
import com.tapledger.core.database.entity.AccountEntity
import com.tapledger.core.database.entity.CategoryEntity
import com.tapledger.core.database.entity.LoanEntity
import com.tapledger.core.database.entity.LoanScheduleInstallmentEntity
import com.tapledger.core.database.entity.LoanScheduleVersionEntity
import com.tapledger.core.model.AccountType
import com.tapledger.core.model.AdvancePolicy
import com.tapledger.core.model.BudgetBucket
import com.tapledger.core.util.DateTimeUtils
import com.tapledger.domain.calculator.AccountBalanceCalculator
import com.tapledger.domain.calculator.ForeclosureSettlement
import com.tapledger.domain.calculator.InterestCalculationMethod
import com.tapledger.domain.calculator.LoanCalculator
import com.tapledger.domain.calculator.PrepaymentAllocationBreakdown
import com.tapledger.domain.calculator.PrepaymentStrategy
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

data class PartPrepaymentExecutionResult(
    val triggerTransactionId: Long?,
    val feeExpenseId: Long?,
    val interestExpenseId: Long?,
    val principalTransferId: Long?,
    val allocation: PrepaymentAllocationBreakdown,
    val scheduleVersionNumber: Int,
    val newTenureMonths: Int,
    val newEmiPaise: Long
)

data class ForeclosureExecutionResult(
    val feeExpenseId: Long?,
    val interestExpenseId: Long?,
    val principalTransferId: Long?,
    val settlement: ForeclosureSettlement
)

@Singleton
class LoanRepository @Inject constructor(
    private val loanDao: LoanDao,
    private val accountDao: AccountDao,
    private val categoryDao: CategoryDao,
    private val transactionDao: TransactionDao,
    private val accountRepository: AccountRepository,
    private val transactionRepository: TransactionRepository,
    private val loanCalculator: LoanCalculator,
    private val transactionRunner: DatabaseTransactionRunner
) {

    fun observeAllActiveLoans(): Flow<List<LoanEntity>> = loanDao.observeAllActive()

    fun observeAllLoans(): Flow<List<LoanEntity>> = loanDao.observeAll()

    suspend fun getAllActiveLoansSync(): List<LoanEntity> = loanDao.getAllActiveSync()

    suspend fun getLoanById(id: Long): LoanEntity? = loanDao.getById(id)

    suspend fun getLoanByAccountId(accountId: Long): LoanEntity? = loanDao.getByAccountId(accountId)

    fun observeScheduleVersionsForLoan(loanId: Long): Flow<List<LoanScheduleVersionEntity>> =
        loanDao.observeScheduleVersionsForLoan(loanId)

    suspend fun getScheduleVersionsForLoan(loanId: Long): List<LoanScheduleVersionEntity> =
        loanDao.getScheduleVersionsForLoan(loanId)

    fun observeInstallmentsForVersion(versionId: Long): Flow<List<LoanScheduleInstallmentEntity>> =
        loanDao.observeInstallmentsForVersion(versionId)

    suspend fun getInstallmentsForVersion(versionId: Long): List<LoanScheduleInstallmentEntity> =
        loanDao.getInstallmentsForVersion(versionId)

    suspend fun createLoan(
        name: String,
        principalPaise: Long,
        annualRateBp: Int,
        tenureMonths: Int,
        emiDay: Int,
        linkedBankAccountId: Long?,
        calculationMethod: InterestCalculationMethod = InterestCalculationMethod.MONTHLY_REDUCING,
        startDateMs: Long = DateTimeUtils.nowEpochMillis(),
        existingAccountId: Long? = null
    ): LoanEntity {
        require(principalPaise > 0L) { "Loan principal must be positive" }
        require(tenureMonths > 0) { "Tenure must be at least 1 month" }
        require(emiDay in 1..31) { "EMI day must be between 1 and 31" }

        val calculatedEmi = loanCalculator.calculateEmi(principalPaise, annualRateBp, tenureMonths)

        return transactionRunner.run {
            // Resolve or create account with AccountType.LOAN
            val accountId = if (existingAccountId != null) {
                existingAccountId
            } else {
                accountRepository.createAccount(
                    name = name,
                    type = AccountType.LOAN,
                    identifierLast4 = null,
                    openingBalancePaise = -principalPaise, // Liabilities are negative
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
                calculationMethod = calculationMethod.name,
                isActive = true,
                createdAt = now
            )

            val loanId = loanDao.insert(loan)
            val createdLoan = loan.copy(id = loanId)

            // Generate and persist Original Schedule (Version 1)
            val schedule = loanCalculator.generateAmortizationSchedule(
                principalPaise = principalPaise,
                annualRateBp = annualRateBp,
                tenureMonths = tenureMonths,
                startDateMs = startDateMs
            )

            val version1 = LoanScheduleVersionEntity(
                loanId = loanId,
                versionNumber = 1,
                triggerType = "ORIGINAL",
                triggerTransactionId = null,
                effectiveDateMs = startDateMs,
                principalPaise = principalPaise,
                emiPaise = calculatedEmi,
                tenureMonths = tenureMonths,
                annualInterestRateBp = annualRateBp,
                notes = "Original Amortization Schedule",
                createdAt = now
            )
            val version1Id = loanDao.insertScheduleVersion(version1)

            val installmentEntities = schedule.map { inst ->
                LoanScheduleInstallmentEntity(
                    scheduleVersionId = version1Id,
                    monthIndex = inst.monthIndex,
                    dueDateMs = inst.dueDateMs,
                    openingPrincipalPaise = inst.openingPrincipalPaise,
                    emiPaise = inst.emiPaise,
                    interestPaise = inst.interestPaise,
                    principalPaidPaise = inst.principalPaidPaise,
                    closingPrincipalPaise = inst.closingPrincipalPaise
                )
            }
            loanDao.insertScheduleInstallments(installmentEntities)

            createdLoan
        }
    }

    /**
     * Executes one-tap Pay EMI atomically:
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

        return transactionRunner.run {
            val txs = transactionDao.getBalanceTransactionsForAccount(loan.accountId, 0L)
            val balanceSummary = AccountBalanceCalculator.calculateAccountBalance(loanAccount, txs, asOfEpochMillis = paymentDateMs)
            val outstandingPrincipal = kotlin.math.abs(balanceSummary.currentBalancePaise)

            val totalPayment = customAmountPaise ?: loan.emiAmountPaise
            require(totalPayment > 0L) { "Payment amount must be positive" }

            val interestNum = outstandingPrincipal * loan.annualInterestRateBp.toLong()
            val interestDivisor = 120_000L
            val calculatedInterest = if (loan.annualInterestRateBp > 0) {
                (interestNum + (interestDivisor / 2L)) / interestDivisor
            } else 0L

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
                loanDao.update(loan.copy(isActive = false, closedAt = paymentDateMs))
            }

            EmiPaymentResult(
                transferLegId = transferLegId,
                interestExpenseId = interestExpenseId,
                principalPaidPaise = principalPortion,
                interestPaidPaise = interestPortion,
                totalPaidPaise = principalPortion + interestPortion,
                remainingPrincipalPaise = newRemaining
            )
        }
    }

    /**
     * Executes an auditable, financially precise part-prepayment:
     * 1. Priority Allocation: Fees -> Accrued Interest -> Principal Reduction.
     * 2. Atomic Room entries: Fee expense + Interest expense + Principal transfer.
     * 3. Amortization recalculation with immutable Schedule Versioning (preserving historical schedules).
     */
    suspend fun recordPartPrepayment(
        loanId: Long,
        fromAccountId: Long,
        totalPaymentPaise: Long,
        strategy: PrepaymentStrategy,
        fixedFeePaise: Long = 0L,
        percentageFeeBp: Int = 0,
        paymentDateMs: Long = DateTimeUtils.nowEpochMillis()
    ): PartPrepaymentExecutionResult {
        require(totalPaymentPaise > 0L) { "Prepayment amount must be positive" }
        val loan = loanDao.getById(loanId) ?: throw IllegalArgumentException("Loan not found: $loanId")
        val loanAccount = accountDao.getById(loan.accountId) ?: throw IllegalArgumentException("Loan account not found")

        return transactionRunner.run {
            val txs = transactionDao.getBalanceTransactionsForAccount(loan.accountId, 0L)
            val balanceSummary = AccountBalanceCalculator.calculateAccountBalance(loanAccount, txs, asOfEpochMillis = paymentDateMs)
            val outstandingPrincipal = kotlin.math.abs(balanceSummary.currentBalancePaise)

            // Determine days elapsed since last transaction or loan start
            val lastTxDate = txs.maxOfOrNull { it.occurredAt } ?: loan.startDateMs
            val daysElapsed = maxOf(0, ((paymentDateMs - lastTxDate) / 86_400_000L).toInt())

            val calcMethod = try {
                InterestCalculationMethod.valueOf(loan.calculationMethod)
            } catch (e: Exception) {
                InterestCalculationMethod.MONTHLY_REDUCING
            }

            // 1. Calculate Waterfall Allocation
            val allocation = loanCalculator.calculatePrepaymentAllocation(
                totalPaymentPaise = totalPaymentPaise,
                currentOutstandingPaise = outstandingPrincipal,
                annualRateBp = loan.annualInterestRateBp,
                daysElapsedSinceLastPayment = daysElapsed,
                fixedFeePaise = fixedFeePaise,
                percentageFeeBp = percentageFeeBp,
                method = calcMethod
            )

            // 2. Record Fee Expense if applicable
            var feeExpenseId: Long? = null
            if (allocation.feeChargesPaise > 0L) {
                val chargesCategory = getOrCreateLoanChargesCategory()
                feeExpenseId = transactionRepository.recordExpense(
                    accountId = fromAccountId,
                    amountPaise = allocation.feeChargesPaise,
                    occurredAt = paymentDateMs,
                    merchantRaw = "${loan.name} (Prepayment Charges)",
                    note = "Part-payment processing fee: ${loan.name}",
                    allocations = listOf(chargesCategory.id to allocation.feeChargesPaise)
                )
            }

            // 3. Record Accrued Interest Expense if applicable
            var interestExpenseId: Long? = null
            if (allocation.accruedInterestPaise > 0L) {
                val interestCategory = getOrCreateLoanInterestCategory()
                interestExpenseId = transactionRepository.recordExpense(
                    accountId = fromAccountId,
                    amountPaise = allocation.accruedInterestPaise,
                    occurredAt = paymentDateMs,
                    merchantRaw = "${loan.name} (Accrued Interest)",
                    note = "Accrued interest on part-payment: ${loan.name}",
                    allocations = listOf(interestCategory.id to allocation.accruedInterestPaise)
                )
            }

            // 4. Record Principal Reduction Transfer
            var principalTransferId: Long? = null
            if (allocation.principalReductionPaise > 0L) {
                val (outId, _) = transactionRepository.recordTransfer(
                    fromAccountId = fromAccountId,
                    toAccountId = loan.accountId,
                    amountPaise = allocation.principalReductionPaise,
                    occurredAt = paymentDateMs,
                    note = "Part-prepayment principal reduction: ${loan.name}"
                )
                principalTransferId = outId
            }

            val newOutstandingPrincipal = allocation.newOutstandingPrincipalPaise

            // 5. Calculate Revised Schedule Projections
            val latestVersion = loanDao.getLatestScheduleVersion(loanId)
            val nextVersionNumber = (latestVersion?.versionNumber ?: 1) + 1

            val (revisedTenure, revisedEmi, revisedInstallments) = if (newOutstandingPrincipal <= 0L) {
                // Fully settled
                Triple(0, 0L, emptyList<LoanScheduleInstallmentEntity>())
            } else {
                when (strategy) {
                    PrepaymentStrategy.REDUCE_TENURE -> {
                        // Keep EMI same, calculate shorter tenure
                        var simBalance = newOutstandingPrincipal
                        var countMonths = 0
                        val instList = mutableListOf<LoanScheduleInstallmentEntity>()
                        val approxMonthMs = 30L * 86_400_000L

                        while (simBalance > 0L && countMonths < 600) {
                            countMonths++
                            val interest = if (loan.annualInterestRateBp > 0) {
                                (simBalance * loan.annualInterestRateBp.toLong() + 60_000L) / 120_000L
                            } else 0L
                            val isLast = simBalance <= (loan.emiAmountPaise - interest)
                            val principalPaid = if (isLast) simBalance else minOf(simBalance, maxOf(0L, loan.emiAmountPaise - interest))
                            val actualEmi = principalPaid + interest
                            val closingBal = maxOf(0L, simBalance - principalPaid)
                            val dueDate = paymentDateMs + (countMonths.toLong() * approxMonthMs)

                            instList.add(
                                LoanScheduleInstallmentEntity(
                                    scheduleVersionId = 0L, // will be updated below
                                    monthIndex = countMonths,
                                    dueDateMs = dueDate,
                                    openingPrincipalPaise = simBalance,
                                    emiPaise = actualEmi,
                                    interestPaise = interest,
                                    principalPaidPaise = principalPaid,
                                    closingPrincipalPaise = closingBal
                                )
                            )
                            simBalance = closingBal
                        }
                        Triple(countMonths, loan.emiAmountPaise, instList)
                    }
                    PrepaymentStrategy.REDUCE_EMI -> {
                        // Keep remaining tenure, calculate new lower EMI
                        val remainingTenure = maxOf(1, loan.tenureMonths)
                        val newEmi = loanCalculator.calculateEmi(newOutstandingPrincipal, loan.annualInterestRateBp, remainingTenure)
                        val simSchedule = loanCalculator.generateAmortizationSchedule(
                            principalPaise = newOutstandingPrincipal,
                            annualRateBp = loan.annualInterestRateBp,
                            tenureMonths = remainingTenure,
                            startDateMs = paymentDateMs
                        )
                        val instList = simSchedule.map { inst ->
                            LoanScheduleInstallmentEntity(
                                scheduleVersionId = 0L,
                                monthIndex = inst.monthIndex,
                                dueDateMs = inst.dueDateMs,
                                openingPrincipalPaise = inst.openingPrincipalPaise,
                                emiPaise = inst.emiPaise,
                                interestPaise = inst.interestPaise,
                                principalPaidPaise = inst.principalPaidPaise,
                                closingPrincipalPaise = inst.closingPrincipalPaise
                            )
                        }
                        Triple(remainingTenure, newEmi, instList)
                    }
                }
            }

            // 6. Save new Schedule Version (Projection)
            val newVersionEntity = LoanScheduleVersionEntity(
                loanId = loanId,
                versionNumber = nextVersionNumber,
                triggerType = "PART_PREPAYMENT_${strategy.name}",
                triggerTransactionId = principalTransferId ?: interestExpenseId ?: feeExpenseId,
                effectiveDateMs = paymentDateMs,
                principalPaise = newOutstandingPrincipal,
                emiPaise = revisedEmi,
                tenureMonths = revisedTenure,
                annualInterestRateBp = loan.annualInterestRateBp,
                notes = "Revised projection after prepayment of ₹${totalPaymentPaise / 100L} (${strategy.name})",
                createdAt = paymentDateMs
            )
            val newVersionId = loanDao.insertScheduleVersion(newVersionEntity)

            val finalizedInstallments = revisedInstallments.map { it.copy(scheduleVersionId = newVersionId) }
            if (finalizedInstallments.isNotEmpty()) {
                loanDao.insertScheduleInstallments(finalizedInstallments)
            }

            // 7. Update Loan entity terms
            val updatedLoan = loan.copy(
                tenureMonths = revisedTenure,
                emiAmountPaise = revisedEmi,
                isActive = newOutstandingPrincipal > 0L,
                closedAt = if (newOutstandingPrincipal <= 0L) paymentDateMs else null
            )
            loanDao.update(updatedLoan)

            PartPrepaymentExecutionResult(
                triggerTransactionId = principalTransferId ?: interestExpenseId ?: feeExpenseId,
                feeExpenseId = feeExpenseId,
                interestExpenseId = interestExpenseId,
                principalTransferId = principalTransferId,
                allocation = allocation,
                scheduleVersionNumber = nextVersionNumber,
                newTenureMonths = revisedTenure,
                newEmiPaise = revisedEmi
            )
        }
    }

    /**
     * Executes early loan foreclosure (full settlement):
     * Settles remaining principal, accrued interest, and optional foreclosure charges,
     * closing the loan cleanly.
     */
    suspend fun recordForeclosure(
        loanId: Long,
        fromAccountId: Long,
        fixedFeePaise: Long = 0L,
        percentageFeeBp: Int = 0,
        foreclosureDateMs: Long = DateTimeUtils.nowEpochMillis()
    ): ForeclosureExecutionResult {
        val loan = loanDao.getById(loanId) ?: throw IllegalArgumentException("Loan not found: $loanId")
        val loanAccount = accountDao.getById(loan.accountId) ?: throw IllegalArgumentException("Loan account not found")

        return transactionRunner.run {
            val txs = transactionDao.getBalanceTransactionsForAccount(loan.accountId, 0L)
            val balanceSummary = AccountBalanceCalculator.calculateAccountBalance(loanAccount, txs, asOfEpochMillis = foreclosureDateMs)
            val outstandingPrincipal = kotlin.math.abs(balanceSummary.currentBalancePaise)

            val lastTxDate = txs.maxOfOrNull { it.occurredAt } ?: loan.startDateMs
            val daysElapsed = maxOf(0, ((foreclosureDateMs - lastTxDate) / 86_400_000L).toInt())

            val calcMethod = try {
                InterestCalculationMethod.valueOf(loan.calculationMethod)
            } catch (e: Exception) {
                InterestCalculationMethod.MONTHLY_REDUCING
            }

            val settlement = loanCalculator.calculateForeclosureSettlement(
                outstandingPrincipalPaise = outstandingPrincipal,
                annualRateBp = loan.annualInterestRateBp,
                daysElapsedSinceLastPayment = daysElapsed,
                fixedFeePaise = fixedFeePaise,
                percentageFeeBp = percentageFeeBp,
                method = calcMethod
            )

            // 1. Pay foreclosure fee if any
            var feeExpenseId: Long? = null
            if (settlement.foreclosureFeePaise > 0L) {
                val chargesCategory = getOrCreateLoanChargesCategory()
                feeExpenseId = transactionRepository.recordExpense(
                    accountId = fromAccountId,
                    amountPaise = settlement.foreclosureFeePaise,
                    occurredAt = foreclosureDateMs,
                    merchantRaw = "${loan.name} (Foreclosure Charges)",
                    note = "Loan Foreclosure Charges: ${loan.name}",
                    allocations = listOf(chargesCategory.id to settlement.foreclosureFeePaise)
                )
            }

            // 2. Pay accrued interest
            var interestExpenseId: Long? = null
            if (settlement.accruedInterestPaise > 0L) {
                val interestCategory = getOrCreateLoanInterestCategory()
                interestExpenseId = transactionRepository.recordExpense(
                    accountId = fromAccountId,
                    amountPaise = settlement.accruedInterestPaise,
                    occurredAt = foreclosureDateMs,
                    merchantRaw = "${loan.name} (Foreclosure Interest)",
                    note = "Accrued interest upon foreclosure: ${loan.name}",
                    allocations = listOf(interestCategory.id to settlement.accruedInterestPaise)
                )
            }

            // 3. Clear remaining principal via transfer
            var principalTransferId: Long? = null
            if (settlement.outstandingPrincipalPaise > 0L) {
                val (outId, _) = transactionRepository.recordTransfer(
                    fromAccountId = fromAccountId,
                    toAccountId = loan.accountId,
                    amountPaise = settlement.outstandingPrincipalPaise,
                    occurredAt = foreclosureDateMs,
                    note = "Full Foreclosure Settlement: ${loan.name}"
                )
                principalTransferId = outId
            }

            // 4. Update loan status to inactive & closed
            loanDao.update(loan.copy(isActive = false, closedAt = foreclosureDateMs))

            // 5. Record Foreclosure Schedule Version
            val latestVersion = loanDao.getLatestScheduleVersion(loanId)
            val nextVersionNumber = (latestVersion?.versionNumber ?: 1) + 1
            val foreclosureVersion = LoanScheduleVersionEntity(
                loanId = loanId,
                versionNumber = nextVersionNumber,
                triggerType = "FORECLOSURE",
                triggerTransactionId = principalTransferId ?: interestExpenseId ?: feeExpenseId,
                effectiveDateMs = foreclosureDateMs,
                principalPaise = 0L,
                emiPaise = 0L,
                tenureMonths = 0,
                annualInterestRateBp = loan.annualInterestRateBp,
                notes = "Loan closed via full foreclosure settlement",
                createdAt = foreclosureDateMs
            )
            loanDao.insertScheduleVersion(foreclosureVersion)

            ForeclosureExecutionResult(
                feeExpenseId = feeExpenseId,
                interestExpenseId = interestExpenseId,
                principalTransferId = principalTransferId,
                settlement = settlement
            )
        }
    }

    suspend fun getOrCreateLoanInterestCategory(): CategoryEntity {
        val existing = categoryDao.getAllCategoriesSync().firstOrNull {
            it.name.contains("Loan Interest", ignoreCase = true) ||
            it.name.contains("EMI Interest", ignoreCase = true)
        }
        if (existing != null) return existing

        val now = DateTimeUtils.nowEpochMillis()
        val newCat = CategoryEntity(
            name = "Loan Interest",
            defaultBucket = BudgetBucket.NEEDS,
            defaultSortOrder = 20,
            defaultAdvancePolicy = AdvancePolicy.CARRY_TO_ADVANCE,
            createdAt = now,
            updatedAt = now
        )
        val id = categoryDao.insert(newCat)
        return newCat.copy(id = id)
    }

    suspend fun getOrCreateLoanChargesCategory(): CategoryEntity {
        val existing = categoryDao.getAllCategoriesSync().firstOrNull {
            it.name.contains("Loan Charges", ignoreCase = true) ||
            it.name.contains("Loan Fees", ignoreCase = true)
        }
        if (existing != null) return existing

        val now = DateTimeUtils.nowEpochMillis()
        val newCat = CategoryEntity(
            name = "Loan Charges & Fees",
            defaultBucket = BudgetBucket.NEEDS,
            defaultSortOrder = 21,
            defaultAdvancePolicy = AdvancePolicy.CARRY_TO_ADVANCE,
            createdAt = now,
            updatedAt = now
        )
        val id = categoryDao.insert(newCat)
        return newCat.copy(id = id)
    }
}

