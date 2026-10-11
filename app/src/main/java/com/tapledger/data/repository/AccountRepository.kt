package com.tapledger.data.repository

import com.tapledger.core.database.dao.AccountDao
import com.tapledger.core.database.dao.BudgetPeriodDao
import com.tapledger.core.database.dao.TransactionDao
import com.tapledger.core.database.entity.AccountEntity
import com.tapledger.core.model.AccountType
import com.tapledger.core.util.DateTimeUtils
import com.tapledger.domain.calculator.AccountBalanceCalculator
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AccountRepository @Inject constructor(
    private val accountDao: AccountDao,
    private val budgetPeriodDao: BudgetPeriodDao,
    private val transactionDao: TransactionDao
) {
    fun getAllActiveAccounts(): Flow<List<AccountEntity>> = accountDao.getAllActiveAccounts()

    suspend fun getAllAccountsSync(): List<AccountEntity> = accountDao.getAllAccountsSync()

    suspend fun getAccountById(id: Long): AccountEntity? = accountDao.getById(id)

    suspend fun findByLast4(last4: String): AccountEntity? = accountDao.findByLast4(last4)

    /**
     * Creates an account (Flow.md §6.1).
     * Invariant: A new account's opening_balance_date must fall within a currently OPEN period.
     */
    suspend fun createAccount(
        name: String,
        type: AccountType,
        identifierLast4: String?,
        openingBalancePaise: Long,
        openingBalanceDate: Long = DateTimeUtils.nowEpochMillis(),
        creditLimitPaise: Long? = null,
        statementDay: Int? = null,
        dueDay: Int? = null,
        isSavings: Boolean = false
    ): Long {
        val startOfDay = DateTimeUtils.getStartOfDayEpochMillis(openingBalanceDate)
        val closedPeriod = budgetPeriodDao.getClosedPeriodEndingAfter(startOfDay)
        if (closedPeriod != null) {
            throw IllegalStateException("A new account's opening_balance_date must fall within a currently OPEN period (Flow.md §6.1)")
        }

        val now = DateTimeUtils.nowEpochMillis()
        val entity = AccountEntity(
            name = name,
            type = type,
            identifierLast4 = identifierLast4,
            openingBalancePaise = openingBalancePaise,
            openingBalanceDate = openingBalanceDate,
            creditLimitPaise = creditLimitPaise,
            statementDay = statementDay,
            dueDay = dueDay,
            isSavings = isSavings,
            isActive = true,
            createdAt = now,
            updatedAt = now
        )
        return accountDao.insert(entity)
    }

    /**
     * Updates an account (Flow.md §6.1, §6.2).
     * - Opening Balance Lock: Opening balance is locked from edits once any CLOSED period satisfies end_at > StartOfDayIST(opening_balance_date).
     * - Deactivation Rule: Deactivating an account is blocked unless Balance(A, now) == 0L.
     */
    suspend fun updateAccount(account: AccountEntity) {
        val existing = accountDao.getById(account.id)
            ?: throw IllegalArgumentException("Account #${account.id} not found")

        // 1. Opening Balance Lock (§6.1)
        if (account.openingBalancePaise != existing.openingBalancePaise || account.openingBalanceDate != existing.openingBalanceDate) {
            val existingStartOfDay = DateTimeUtils.getStartOfDayEpochMillis(existing.openingBalanceDate)
            val closedPeriod = budgetPeriodDao.getClosedPeriodEndingAfter(existingStartOfDay)
            if (closedPeriod != null) {
                throw IllegalStateException("Account opening balance is locked from edits because a closed budget period covers the opening date (Flow.md §6.1)")
            }
        }

        // 2. Deactivation Rule (§6.2)
        if (!account.isActive && existing.isActive) {
            val txs = transactionDao.getBalanceTransactionsForAccount(account.id, 0L)
            val balanceSummary = AccountBalanceCalculator.calculateAccountBalance(existing, txs)
            if (balanceSummary.currentBalancePaise != 0L) {
                throw IllegalStateException("Cannot deactivate account #${account.id} with non-zero balance (${balanceSummary.currentBalancePaise} paise) (Flow.md §6.2)")
            }
        }

        val updated = account.copy(updatedAt = DateTimeUtils.nowEpochMillis())
        accountDao.update(updated)
    }
}
