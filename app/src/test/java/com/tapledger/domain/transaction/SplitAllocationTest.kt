package com.tapledger.domain.transaction

import com.google.common.truth.Truth.assertThat
import com.tapledger.core.database.DatabaseTransactionRunner
import com.tapledger.core.database.dao.AccountDao
import com.tapledger.core.database.dao.AuditLogDao
import com.tapledger.core.database.dao.BudgetPeriodDao
import com.tapledger.core.database.dao.CategoryDao
import com.tapledger.core.database.dao.TransactionAllocationDao
import com.tapledger.core.database.dao.TransactionDao
import com.tapledger.core.database.entity.AccountEntity
import com.tapledger.core.database.entity.AuditLogEntity
import com.tapledger.core.database.entity.BudgetPeriodEntity
import com.tapledger.core.database.entity.CategoryEntity
import com.tapledger.core.database.entity.TransactionAllocationEntity
import com.tapledger.core.database.entity.TransactionEntity
import com.tapledger.core.model.AccountType
import com.tapledger.core.model.BudgetBucket
import com.tapledger.core.model.BudgetPeriodStatus
import com.tapledger.core.model.TransactionDirection
import com.tapledger.core.model.TransactionKind
import com.tapledger.core.model.TransactionSource
import com.tapledger.core.model.TransactionStatus
import com.tapledger.data.repository.TransactionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test

/**
 * Validates Flow.md §9.1: Multi-Split Category Allocation
 * - Rule A1: Σ Allocations == transaction.amount_paise
 * - Rule A3: Non-positive allocation (amount_paise > 0)
 * - Rule A4: Duplicate category in split rejected
 * - Transaction status lifecycle transition (PENDING -> CATEGORIZED -> PENDING)
 */
class SplitAllocationTest {

    private class DirectTransactionRunner : DatabaseTransactionRunner {
        override suspend fun <R> run(block: suspend () -> R): R = block()
    }

    private class FakeAccountDao : AccountDao {
        val storage = mutableMapOf<Long, AccountEntity>()
        private var nextId = 1L
        override suspend fun insert(account: AccountEntity): Long {
            val id = if (account.id == 0L) nextId++ else account.id
            storage[id] = account.copy(id = id)
            return id
        }
        override suspend fun insertAll(accounts: List<AccountEntity>): List<Long> = accounts.map { insert(it) }
        override suspend fun update(account: AccountEntity) { storage[account.id] = account }
        override suspend fun getById(id: Long): AccountEntity? = storage[id]
        override fun getAllActiveAccounts(): Flow<List<AccountEntity>> = emptyFlow()
        override suspend fun getAllAccountsSync(): List<AccountEntity> = storage.values.toList()
        override suspend fun findByLast4(last4: String): AccountEntity? = storage.values.firstOrNull { it.identifierLast4 == last4 && it.isActive }
        override suspend fun findAllActiveByLast4(last4: String): List<AccountEntity> = storage.values.filter { it.identifierLast4 == last4 && it.isActive }
        override suspend fun getActiveBankAccounts(): List<AccountEntity> = storage.values.filter { it.type == AccountType.BANK && it.isActive }
    }

    private class FakeCategoryDao : CategoryDao {
        val storage = mutableMapOf<Long, CategoryEntity>()
        private var nextId = 1L
        override suspend fun insert(category: CategoryEntity): Long {
            val id = if (category.id == 0L) nextId++ else category.id
            storage[id] = category.copy(id = id)
            return id
        }
        override suspend fun insertAll(categories: List<CategoryEntity>): List<Long> = categories.map { insert(it) }
        override suspend fun update(category: CategoryEntity) { storage[category.id] = category }
        override suspend fun getById(id: Long): CategoryEntity? = storage[id]
        override fun getAllActiveCategories(): Flow<List<CategoryEntity>> = emptyFlow()
        override suspend fun getAllCategoriesSync(): List<CategoryEntity> = storage.values.toList()
        override suspend fun getByName(name: String): CategoryEntity? = storage.values.firstOrNull { it.name == name }
        override suspend fun getMaxSortOrder(): Int? = storage.values.maxOfOrNull { it.defaultSortOrder }
    }

    private class FakeBudgetPeriodDao : BudgetPeriodDao {
        val storage = mutableMapOf<Long, BudgetPeriodEntity>()
        private var nextId = 1L
        override suspend fun insert(budgetPeriod: BudgetPeriodEntity): Long {
            val id = if (budgetPeriod.id == 0L) nextId++ else budgetPeriod.id
            storage[id] = budgetPeriod.copy(id = id)
            return id
        }
        override suspend fun update(budgetPeriod: BudgetPeriodEntity) { storage[budgetPeriod.id] = budgetPeriod }
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
        override suspend fun getBalanceTransactionsForAccount(accountId: Long, sinceEpochMillis: Long): List<TransactionEntity> = emptyList()
        override fun observeBalanceTransactionsForAccount(accountId: Long, sinceEpochMillis: Long): Flow<List<TransactionEntity>> = emptyFlow()
        override suspend fun getTransactionsInPeriod(startEpochMillis: Long, endEpochMillis: Long): List<TransactionEntity> = emptyList()
        override fun observeTransactionsInPeriod(startEpochMillis: Long, endEpochMillis: Long): Flow<List<TransactionEntity>> = emptyFlow()
        override suspend fun findBySourceFingerprint(fingerprint: String): TransactionEntity? = null
        override suspend fun findByUpiReference(upiRef: String): TransactionEntity? = null
        override suspend fun findByExactKey(accountId: Long, upiRef: String, direction: TransactionDirection, amountPaise: Long): TransactionEntity? = null
        override suspend fun findByExactKeyUnassigned(upiRef: String, direction: TransactionDirection, amountPaise: Long): TransactionEntity? = null
        override suspend fun findByUpiDirectionAmountAcrossAllAccounts(upiRef: String, direction: TransactionDirection, amountPaise: Long): TransactionEntity? = null
        override suspend fun findFuzzyCandidates(accountId: Long?, direction: TransactionDirection, amountPaise: Long, startWindow: Long, endWindow: Long): List<TransactionEntity> = emptyList()
        override suspend fun findTransferPairCandidates(excludeAccountId: Long, oppositeDirection: TransactionDirection, amountPaise: Long, startWindow: Long, endWindow: Long): List<TransactionEntity> = emptyList()
        override suspend fun getTransferLegs(transferGroupId: String): List<TransactionEntity> = storage.values.filter { it.transferGroupId == transferGroupId }
        override suspend fun getRefundsForTransaction(originalTransactionId: Long): List<TransactionEntity> = storage.values.filter { it.refundOfTransactionId == originalTransactionId }
        override suspend fun getTransactionsForAccountSync(accountId: Long): List<TransactionEntity> = storage.values.filter { it.accountId == accountId && it.status != TransactionStatus.VOIDED }
    }

    private class FakeTransactionAllocationDao : TransactionAllocationDao {
        val storage = mutableMapOf<Long, TransactionAllocationEntity>()
        private var nextId = 1L
        override suspend fun insert(allocation: TransactionAllocationEntity): Long {
            val id = if (allocation.id == 0L) nextId++ else allocation.id
            storage[id] = allocation.copy(id = id)
            return id
        }
        override suspend fun insertAll(allocations: List<TransactionAllocationEntity>): List<Long> = allocations.map { insert(it) }
        override suspend fun update(allocation: TransactionAllocationEntity) { storage[allocation.id] = allocation }
        override suspend fun deleteForTransaction(transactionId: Long) {
            storage.entries.removeIf { it.value.transactionId == transactionId }
        }
        override suspend fun getForTransaction(transactionId: Long): List<TransactionAllocationEntity> =
            storage.values.filter { it.transactionId == transactionId }
        override fun observeForTransaction(transactionId: Long): Flow<List<TransactionAllocationEntity>> = emptyFlow()
        override suspend fun getAllocationsInPeriod(startEpochMillis: Long, endEpochMillis: Long): List<TransactionAllocationEntity> = emptyList()
        override suspend fun getAllocationsForCategoryInPeriod(categoryId: Long, startEpochMillis: Long, endEpochMillis: Long): List<TransactionAllocationEntity> = emptyList()
        override suspend fun getAllTransactionAllocationsSync(): List<TransactionAllocationEntity> = storage.values.toList()
        override fun observeAllAllocations(): Flow<List<TransactionAllocationEntity>> = emptyFlow()
    }

    private class FakeAuditLogDao : AuditLogDao {
        val storage = mutableListOf<AuditLogEntity>()
        override suspend fun insert(entry: AuditLogEntity): Long {
            storage.add(entry)
            return storage.size.toLong()
        }
        override fun observeForTransaction(transactionId: Long): Flow<List<AuditLogEntity>> = emptyFlow()
        override suspend fun getForTransactionSync(transactionId: Long): List<AuditLogEntity> =
            storage.filter { it.transactionId == transactionId }
        override fun observeRecentAudits(limit: Int): Flow<List<AuditLogEntity>> = emptyFlow()
    }

    private lateinit var accountDao: FakeAccountDao
    private lateinit var categoryDao: FakeCategoryDao
    private lateinit var budgetPeriodDao: FakeBudgetPeriodDao
    private lateinit var transactionDao: FakeTransactionDao
    private lateinit var transactionAllocationDao: FakeTransactionAllocationDao
    private lateinit var auditLogDao: FakeAuditLogDao
    private lateinit var repository: TransactionRepository

    @Before
    fun setUp() {
        runBlocking {
            accountDao = FakeAccountDao()
            categoryDao = FakeCategoryDao()
            budgetPeriodDao = FakeBudgetPeriodDao()
            transactionDao = FakeTransactionDao()
            transactionAllocationDao = FakeTransactionAllocationDao()
            auditLogDao = FakeAuditLogDao()

            repository = TransactionRepository(
                transactionRunner = DirectTransactionRunner(),
                transactionDao = transactionDao,
                transactionAllocationDao = transactionAllocationDao,
                auditLogDao = auditLogDao,
                accountDao = accountDao,
                categoryDao = categoryDao,
                budgetPeriodDao = budgetPeriodDao
            )

            // Seed Account & Period
            accountDao.insert(
                AccountEntity(
                    id = 1,
                    name = "HDFC Checking",
                    type = AccountType.BANK,
                    identifierLast4 = "1234",
                    openingBalancePaise = 100_000_00L,
                    openingBalanceDate = 1000L,
                    createdAt = 1000L,
                    updatedAt = 1000L
                )
            )
            categoryDao.insert(CategoryEntity(id = 10, name = "Groceries", defaultBucket = BudgetBucket.NEEDS, defaultSortOrder = 1))
            categoryDao.insert(CategoryEntity(id = 20, name = "Dining", defaultBucket = BudgetBucket.WANTS, defaultSortOrder = 2))
            categoryDao.insert(CategoryEntity(id = 30, name = "Entertainment", defaultBucket = BudgetBucket.WANTS, defaultSortOrder = 3))

            budgetPeriodDao.insert(
                BudgetPeriodEntity(
                    id = 1,
                    year = 2026,
                    month = 10,
                    startAt = 1000L,
                    endAt = 2000L,
                    incomePaise = 50_000_00L,
                    status = BudgetPeriodStatus.OPEN,
                    createdAt = 1000L,
                    updatedAt = 1000L
                )
            )
        }
    }

    @Test
    fun testSplitSumValidation() = runBlocking {
        // Transaction of ₹1,000 (100,000 paise)
        val txId = transactionDao.insert(
            TransactionEntity(
                id = 1,
                accountId = 1,
                kind = TransactionKind.EXPENSE,
                direction = TransactionDirection.OUT,
                amountPaise = 100_000L,
                occurredAt = 1200L,
                createdAt = 1200L,
                updatedAt = 1200L,
                status = TransactionStatus.PENDING,
                source = TransactionSource.MANUAL
            )
        )

        // 1. Incomplete split (₹600 + ₹300 = ₹900 != ₹1,000) -> Must fail Rule A1
        val ex1 = assertThrows(IllegalArgumentException::class.java) {
            runBlocking {
                repository.splitTransaction(
                    transactionId = txId,
                    allocations = listOf(10L to 60_000L, 20L to 30_000L)
                )
            }
        }
        assertThat(ex1.message).contains("must equal transaction amount")

        // 2. Overallocated split (₹600 + ₹500 = ₹1,100 != ₹1,000) -> Must fail Rule A1
        val ex2 = assertThrows(IllegalArgumentException::class.java) {
            runBlocking {
                repository.splitTransaction(
                    transactionId = txId,
                    allocations = listOf(10L to 60_000L, 20L to 50_000L)
                )
            }
        }
        assertThat(ex2.message).contains("must equal transaction amount")

        // 3. Non-positive allocation (₹1,000 and ₹0) -> Must fail Rule A3
        val ex3 = assertThrows(IllegalArgumentException::class.java) {
            runBlocking {
                repository.splitTransaction(
                    transactionId = txId,
                    allocations = listOf(10L to 100_000L, 20L to 0L)
                )
            }
        }
        assertThat(ex3.message).contains("positive")

        // 4. Duplicate category in split -> Must fail Rule A4
        val ex4 = assertThrows(IllegalArgumentException::class.java) {
            runBlocking {
                repository.splitTransaction(
                    transactionId = txId,
                    allocations = listOf(10L to 50_000L, 10L to 50_000L)
                )
            }
        }
        assertThat(ex4.message).contains("Duplicate category")

        // 5. Valid exact multi-split (Groceries ₹600 + Dining ₹300 + Entertainment ₹100 = ₹1,000) -> Succeeds
        repository.splitTransaction(
            transactionId = txId,
            allocations = listOf(10L to 60_000L, 20L to 30_000L, 30L to 10_000L),
            reason = "Grocery & Dining run"
        )

        val txAfter = transactionDao.getById(txId)!!
        assertThat(txAfter.status).isEqualTo(TransactionStatus.CATEGORIZED)

        val allocations = transactionAllocationDao.getForTransaction(txId)
        assertThat(allocations).hasSize(3)
        assertThat(allocations.sumOf { it.amountPaise }).isEqualTo(100_000L)

        // 6. Uncategorizing reverts to PENDING and clears allocations
        repository.uncategorizeTransaction(txId)
        val txUncat = transactionDao.getById(txId)!!
        assertThat(txUncat.status).isEqualTo(TransactionStatus.PENDING)
        assertThat(transactionAllocationDao.getForTransaction(txId)).isEmpty()
    }
}
