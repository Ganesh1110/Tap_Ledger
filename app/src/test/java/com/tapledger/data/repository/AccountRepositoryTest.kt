package com.tapledger.data.repository

import com.google.common.truth.Truth.assertThat
import com.tapledger.core.database.dao.AccountDao
import com.tapledger.core.database.dao.BudgetPeriodDao
import com.tapledger.core.database.dao.TransactionDao
import com.tapledger.core.database.entity.AccountEntity
import com.tapledger.core.database.entity.BudgetPeriodEntity
import com.tapledger.core.database.entity.TransactionEntity
import com.tapledger.core.model.AccountType
import com.tapledger.core.model.BudgetPeriodStatus
import com.tapledger.core.model.TransactionDirection
import com.tapledger.core.model.TransactionKind
import com.tapledger.core.model.TransactionSource
import com.tapledger.core.model.TransactionStatus
import com.tapledger.core.util.DateTimeUtils
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test

/**
 * Validates Flow.md §6.1, §6.2: Account Lifecycle Rules
 * - Rule 5 (§6.1): Opening balance date must fall within a currently OPEN period on create.
 * - Opening Balance Lock (§6.1): Opening balance is locked from edits once any CLOSED period satisfies end_at > StartOfDayIST(opening_balance_date).
 * - Deactivation Rule (§6.2): Deactivating an account is blocked unless Balance(A, now) == 0L.
 */
class AccountRepositoryTest {

    private class FakeAccountDao : AccountDao {
        val storage = mutableMapOf<Long, AccountEntity>()
        private var nextId = 1L

        override suspend fun insert(account: AccountEntity): Long {
            val id = if (account.id == 0L) nextId++ else account.id
            storage[id] = account.copy(id = id)
            return id
        }

        override suspend fun insertAll(accounts: List<AccountEntity>): List<Long> =
            accounts.map { insert(it) }

        override suspend fun update(account: AccountEntity) {
            storage[account.id] = account
        }

        override suspend fun getById(id: Long): AccountEntity? = storage[id]
        override fun getAllActiveAccounts(): Flow<List<AccountEntity>> = emptyFlow()
        override suspend fun getAllAccountsSync(): List<AccountEntity> = storage.values.toList()
        override suspend fun findByLast4(last4: String): AccountEntity? = storage.values.firstOrNull { it.identifierLast4 == last4 && it.isActive }
        override suspend fun findAllActiveByLast4(last4: String): List<AccountEntity> = storage.values.filter { it.identifierLast4 == last4 && it.isActive }
        override suspend fun getActiveBankAccounts(): List<AccountEntity> = storage.values.filter { it.type == AccountType.BANK && it.isActive }
    }

    private class FakeBudgetPeriodDao : BudgetPeriodDao {
        val storage = mutableMapOf<Long, BudgetPeriodEntity>()
        private var nextId = 1L

        override suspend fun insert(budgetPeriod: BudgetPeriodEntity): Long {
            val id = if (budgetPeriod.id == 0L) nextId++ else budgetPeriod.id
            storage[id] = budgetPeriod.copy(id = id)
            return id
        }

        override suspend fun update(budgetPeriod: BudgetPeriodEntity) {
            storage[budgetPeriod.id] = budgetPeriod
        }

        override suspend fun getById(id: Long): BudgetPeriodEntity? = storage[id]
        override suspend fun getByYearMonth(year: Int, month: Int): BudgetPeriodEntity? = storage.values.firstOrNull { it.year == year && it.month == month }
        override fun observeByYearMonth(year: Int, month: Int): Flow<BudgetPeriodEntity?> = emptyFlow()
        override fun getAllPeriods(): Flow<List<BudgetPeriodEntity>> = emptyFlow()
        override suspend fun getAllPeriodsSync(): List<BudgetPeriodEntity> = storage.values.toList()
        override suspend fun getPeriodForTimestamp(timestamp: Long): BudgetPeriodEntity? = storage.values.firstOrNull { timestamp >= it.startAt && timestamp < it.endAt }
        override suspend fun getEarliestPeriod(): BudgetPeriodEntity? = storage.values.minByOrNull { it.startAt }
        override suspend fun getLatestPeriod(): BudgetPeriodEntity? = storage.values.maxByOrNull { it.startAt }
        override suspend fun getOpenPeriodForTimestamp(timestamp: Long): BudgetPeriodEntity? = storage.values.firstOrNull { it.status == BudgetPeriodStatus.OPEN && timestamp >= it.startAt && timestamp < it.endAt }
        override suspend fun getFirstOpenPeriod(): BudgetPeriodEntity? = storage.values.filter { it.status == BudgetPeriodStatus.OPEN }.minByOrNull { it.startAt }
        override suspend fun getClosedPeriodEndingAfter(startOfDayTimestamp: Long): BudgetPeriodEntity? =
            storage.values.firstOrNull { it.status == BudgetPeriodStatus.CLOSED && it.endAt > startOfDayTimestamp }
        override suspend fun getClosedPeriodForTimestamp(timestamp: Long): BudgetPeriodEntity? =
            storage.values.firstOrNull { it.status == BudgetPeriodStatus.CLOSED && timestamp >= it.startAt && timestamp < it.endAt }
    }

    private class FakeTransactionDao : TransactionDao {
        val storage = mutableMapOf<Long, TransactionEntity>()
        private var nextId = 1L

        override suspend fun insert(transaction: TransactionEntity): Long {
            val id = if (transaction.id == 0L) nextId++ else transaction.id
            storage[id] = transaction.copy(id = id)
            return id
        }

        override suspend fun insertAll(transactions: List<TransactionEntity>): List<Long> = transactions.map { insert(it) }
        override suspend fun update(transaction: TransactionEntity) { storage[transaction.id] = transaction }
        override suspend fun getById(id: Long): TransactionEntity? = storage[id]
        override fun observeById(id: Long): Flow<TransactionEntity?> = emptyFlow()
        override fun observeAllTransactions(): Flow<List<TransactionEntity>> = emptyFlow()
        override suspend fun getAllTransactionsSync(): List<TransactionEntity> = storage.values.toList()
        override fun observePendingTransactions(): Flow<List<TransactionEntity>> = emptyFlow()
        override fun observePendingCount(): Flow<Int> = emptyFlow()
        override suspend fun getPendingCountSync(): Int = storage.values.count { it.status == TransactionStatus.PENDING }

        override suspend fun getBalanceTransactionsForAccount(accountId: Long, sinceEpochMillis: Long): List<TransactionEntity> {
            return storage.values.filter {
                it.accountId == accountId &&
                it.status in listOf(TransactionStatus.PENDING, TransactionStatus.CONFIRMED, TransactionStatus.CATEGORIZED) &&
                it.occurredAt >= sinceEpochMillis
            }
        }

        override fun observeBalanceTransactionsForAccount(accountId: Long, sinceEpochMillis: Long): Flow<List<TransactionEntity>> = emptyFlow()

        override suspend fun getTransactionsInPeriod(startEpochMillis: Long, endEpochMillis: Long): List<TransactionEntity> {
            return storage.values.filter {
                it.occurredAt >= startEpochMillis && it.occurredAt < endEpochMillis &&
                it.status in listOf(TransactionStatus.PENDING, TransactionStatus.CONFIRMED, TransactionStatus.CATEGORIZED)
            }
        }

        override fun observeTransactionsInPeriod(startEpochMillis: Long, endEpochMillis: Long): Flow<List<TransactionEntity>> = emptyFlow()
        override suspend fun findBySourceFingerprint(fingerprint: String): TransactionEntity? = null
        override suspend fun findByUpiReference(upiRef: String): TransactionEntity? = null
        override suspend fun findByExactKey(accountId: Long, upiRef: String, direction: TransactionDirection, amountPaise: Long): TransactionEntity? = null
        override suspend fun findByExactKeyUnassigned(upiRef: String, direction: TransactionDirection, amountPaise: Long): TransactionEntity? = null
        override suspend fun findByUpiDirectionAmountAcrossAllAccounts(upiRef: String, direction: TransactionDirection, amountPaise: Long): TransactionEntity? = null
        override suspend fun findFuzzyCandidates(accountId: Long?, direction: TransactionDirection, amountPaise: Long, startWindow: Long, endWindow: Long): List<TransactionEntity> = emptyList()
        override suspend fun findTransferPairCandidates(excludeAccountId: Long, oppositeDirection: TransactionDirection, amountPaise: Long, startWindow: Long, endWindow: Long): List<TransactionEntity> = emptyList()
        override suspend fun getTransferLegs(transferGroupId: String): List<TransactionEntity> = emptyList()
        override suspend fun getRefundsForTransaction(originalTransactionId: Long): List<TransactionEntity> = emptyList()
        override suspend fun getTransactionsForAccountSync(accountId: Long): List<TransactionEntity> = emptyList()
    }

    private lateinit var accountDao: FakeAccountDao
    private lateinit var budgetPeriodDao: FakeBudgetPeriodDao
    private lateinit var transactionDao: FakeTransactionDao
    private lateinit var repository: AccountRepository

    @Before
    fun setUp() {
        accountDao = FakeAccountDao()
        budgetPeriodDao = FakeBudgetPeriodDao()
        transactionDao = FakeTransactionDao()
        repository = AccountRepository(accountDao, budgetPeriodDao, transactionDao)
    }

    @Test
    fun createAccount_whenOpeningDateFallsInClosedPeriod_throwsException() = runBlocking {
        // Setup: January 2024 is CLOSED [1704067200000L, 1706745600000L)
        val janStart = 1704067200000L
        val janEnd = 1706745600000L
        budgetPeriodDao.insert(
            BudgetPeriodEntity(
                id = 1,
                year = 2024,
                month = 1,
                startAt = janStart,
                endAt = janEnd,
                incomePaise = 0L,
                status = BudgetPeriodStatus.CLOSED,
                createdAt = 0L,
                updatedAt = 0L
            )
        )

        // Attempt to create account with opening date in Jan 2024
        val openingDate = janStart + 86400_000L * 5 // Jan 6

        val exception = assertThrows(IllegalStateException::class.java) {
            runBlocking {
                repository.createAccount(
                    name = "Old Account",
                    type = AccountType.BANK,
                    identifierLast4 = "1111",
                    openingBalancePaise = 10_000_00L,
                    openingBalanceDate = openingDate
                )
            }
        }
        assertThat(exception.message).contains("opening_balance_date must fall within a currently OPEN period")
    }

    @Test
    fun createAccount_whenOpeningDateNotInClosedPeriod_succeeds() = runBlocking {
        // Setup: Jan 2024 closed, Feb 2024 open
        val janStart = 1704067200000L
        val janEnd = 1706745600000L
        budgetPeriodDao.insert(
            BudgetPeriodEntity(
                id = 1,
                year = 2024,
                month = 1,
                startAt = janStart,
                endAt = janEnd,
                incomePaise = 0L,
                status = BudgetPeriodStatus.CLOSED,
                createdAt = 0L,
                updatedAt = 0L
            )
        )

        // Opening date in February 2024 (after janEnd)
        val openingDateFeb = janEnd + 86400_000L * 2
        val accountId = repository.createAccount(
            name = "Feb Account",
            type = AccountType.BANK,
            identifierLast4 = "2222",
            openingBalancePaise = 20_000_00L,
            openingBalanceDate = openingDateFeb
        )

        val created = repository.getAccountById(accountId)
        assertThat(created).isNotNull()
        assertThat(created?.openingBalancePaise).isEqualTo(20_000_00L)
    }

    @Test
    fun updateAccount_openingBalanceLockedWhenCoveredByClosedPeriod() = runBlocking {
        val openingDate = 1704067200000L // Jan 1, 2024
        val accountId = repository.createAccount(
            name = "Salary Account",
            type = AccountType.BANK,
            identifierLast4 = "3333",
            openingBalancePaise = 50_000_00L,
            openingBalanceDate = openingDate
        )

        // Now January 2024 is CLOSED
        budgetPeriodDao.insert(
            BudgetPeriodEntity(
                id = 1,
                year = 2024,
                month = 1,
                startAt = openingDate,
                endAt = openingDate + 86400_000L * 31,
                incomePaise = 0L,
                status = BudgetPeriodStatus.CLOSED,
                createdAt = 0L,
                updatedAt = 0L
            )
        )

        val existing = repository.getAccountById(accountId)!!

        // Try to update opening balance amount
        val exceptionAmount = assertThrows(IllegalStateException::class.java) {
            runBlocking {
                repository.updateAccount(existing.copy(openingBalancePaise = 60_000_00L))
            }
        }
        assertThat(exceptionAmount.message).contains("opening balance is locked from edits")

        // Try to update opening balance date
        val exceptionDate = assertThrows(IllegalStateException::class.java) {
            runBlocking {
                repository.updateAccount(existing.copy(openingBalanceDate = openingDate + 1000L))
            }
        }
        assertThat(exceptionDate.message).contains("opening balance is locked from edits")

        // Updating unrelated field (e.g. name) succeeds
        repository.updateAccount(existing.copy(name = "Salary Account Renamed"))
        val updated = repository.getAccountById(accountId)!!
        assertThat(updated.name).isEqualTo("Salary Account Renamed")
    }

    @Test
    fun deactivateAccount_whenBalanceNonZero_throwsException() = runBlocking {
        val accountId = repository.createAccount(
            name = "Active Bank",
            type = AccountType.BANK,
            identifierLast4 = "4444",
            openingBalancePaise = 10_000_00L // Balance is +₹10,000
        )
        val existing = repository.getAccountById(accountId)!!

        val exception = assertThrows(IllegalStateException::class.java) {
            runBlocking {
                repository.updateAccount(existing.copy(isActive = false))
            }
        }
        assertThat(exception.message).contains("Cannot deactivate account")
        assertThat(exception.message).contains("non-zero balance")
    }

    @Test
    fun deactivateAccount_whenBalanceZero_succeeds() = runBlocking {
        val accountId = repository.createAccount(
            name = "Zero Balance Bank",
            type = AccountType.BANK,
            identifierLast4 = "5555",
            openingBalancePaise = 10_000_00L
        )

        // Add expense of ₹10,000 so current balance becomes 0
        transactionDao.insert(
            TransactionEntity(
                id = 1,
                accountId = accountId,
                kind = TransactionKind.EXPENSE,
                direction = TransactionDirection.OUT,
                amountPaise = 10_000_00L,
                occurredAt = DateTimeUtils.nowEpochMillis(),
                createdAt = DateTimeUtils.nowEpochMillis(),
                updatedAt = DateTimeUtils.nowEpochMillis(),
                status = TransactionStatus.CATEGORIZED,
                source = TransactionSource.MANUAL
            )
        )

        val existing = repository.getAccountById(accountId)!!
        repository.updateAccount(existing.copy(isActive = false))

        val updated = repository.getAccountById(accountId)!!
        assertThat(updated.isActive).isFalse()
    }
}
