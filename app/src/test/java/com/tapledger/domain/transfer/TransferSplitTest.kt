package com.tapledger.domain.transfer

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
import com.tapledger.core.model.TransactionStatus
import com.tapledger.data.repository.TransactionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test

/**
 * Validates Flow.md §7.2, §8.3: Transfer Split Allocation Invariants
 * - Standard transfers cannot carry budgetary category allocations.
 * - Savings account leg of a transfer cannot carry allocations.
 * - Operational leg of a savings transfer can only carry SAVINGS bucket categories.
 */
class TransferSplitTest {

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
        override suspend fun getRefundsForTransaction(originalTransactionId: Long): List<TransactionEntity> = emptyList()
        override suspend fun getTransactionsForAccountSync(accountId: Long): List<TransactionEntity> = emptyList()
    }

    private class FakeTransactionAllocationDao : TransactionAllocationDao {
        val allocations = mutableListOf<TransactionAllocationEntity>()
        private var nextId = 1L

        override suspend fun insert(allocation: TransactionAllocationEntity): Long {
            val id = if (allocation.id == 0L) nextId++ else allocation.id
            allocations.add(allocation.copy(id = id))
            return id
        }
        override suspend fun insertAll(allocations: List<TransactionAllocationEntity>): List<Long> = allocations.map { insert(it) }
        override suspend fun update(allocation: TransactionAllocationEntity) {
            val idx = allocations.indexOfFirst { it.id == allocation.id }
            if (idx >= 0) allocations[idx] = allocation
        }
        override suspend fun deleteForTransaction(transactionId: Long) { allocations.removeAll { it.transactionId == transactionId } }
        override suspend fun getForTransaction(transactionId: Long): List<TransactionAllocationEntity> = allocations.filter { it.transactionId == transactionId }
        override fun observeForTransaction(transactionId: Long): Flow<List<TransactionAllocationEntity>> = emptyFlow()
        override suspend fun getAllocationsInPeriod(startEpochMillis: Long, endEpochMillis: Long): List<TransactionAllocationEntity> = emptyList()
        override suspend fun getAllocationsForCategoryInPeriod(categoryId: Long, startEpochMillis: Long, endEpochMillis: Long): List<TransactionAllocationEntity> = emptyList()
        override suspend fun getAllTransactionAllocationsSync(): List<TransactionAllocationEntity> = allocations
    }

    private class FakeAuditLogDao : AuditLogDao {
        val logs = mutableListOf<AuditLogEntity>()
        private var nextId = 1L

        override suspend fun insert(entry: AuditLogEntity): Long {
            val id = if (entry.id == 0L) nextId++ else entry.id
            logs.add(entry.copy(id = id))
            return id
        }
        override fun observeForTransaction(transactionId: Long): Flow<List<AuditLogEntity>> = emptyFlow()
        override suspend fun getForTransactionSync(transactionId: Long): List<AuditLogEntity> = logs.filter { it.transactionId == transactionId }
        override fun observeRecentAudits(limit: Int): Flow<List<AuditLogEntity>> = emptyFlow()
    }

    private lateinit var transactionDao: FakeTransactionDao
    private lateinit var allocationDao: FakeTransactionAllocationDao
    private lateinit var auditLogDao: FakeAuditLogDao
    private lateinit var accountDao: FakeAccountDao
    private lateinit var categoryDao: FakeCategoryDao
    private lateinit var budgetPeriodDao: FakeBudgetPeriodDao
    private lateinit var repository: TransactionRepository

    @Before
    fun setUp() = runBlocking {
        budgetPeriodDao = FakeBudgetPeriodDao()
        transactionDao = FakeTransactionDao()
        allocationDao = FakeTransactionAllocationDao()
        auditLogDao = FakeAuditLogDao()
        accountDao = FakeAccountDao()
        categoryDao = FakeCategoryDao()

        // Operational accounts: HDFC (1), SBI (2)
        accountDao.insert(AccountEntity(id = 1, name = "HDFC Checking", type = AccountType.BANK, identifierLast4 = "1111", openingBalancePaise = 50_000_00L, openingBalanceDate = 0L, createdAt = 0L, updatedAt = 0L))
        accountDao.insert(AccountEntity(id = 2, name = "SBI Checking", type = AccountType.BANK, identifierLast4 = "2222", openingBalancePaise = 20_000_00L, openingBalanceDate = 0L, createdAt = 0L, updatedAt = 0L))

        // Savings Account: PPF (id=3)
        accountDao.insert(AccountEntity(id = 3, name = "PPF Account", type = AccountType.BANK, identifierLast4 = "3333", openingBalancePaise = 0L, openingBalanceDate = 0L, isSavings = true, createdAt = 0L, updatedAt = 0L))

        // Categories: Groceries (Needs, 101), Dining (Wants, 102), Savings Goal (Savings, 103)
        categoryDao.insert(CategoryEntity(id = 101, name = "Groceries", defaultBucket = BudgetBucket.NEEDS, defaultSortOrder = 1))
        categoryDao.insert(CategoryEntity(id = 102, name = "Dining", defaultBucket = BudgetBucket.WANTS, defaultSortOrder = 2))
        categoryDao.insert(CategoryEntity(id = 103, name = "Long Term Savings", defaultBucket = BudgetBucket.SAVINGS, defaultSortOrder = 3))

        repository = TransactionRepository(
            transactionRunner = DirectTransactionRunner(),
            transactionDao = transactionDao,
            transactionAllocationDao = allocationDao,
            auditLogDao = auditLogDao,
            accountDao = accountDao,
            categoryDao = categoryDao,
            budgetPeriodDao = budgetPeriodDao
        )
    }

    @Test
    fun testNonBudgetaryTransferAllocation() = runBlocking {
        // Standard transfer between HDFC and SBI
        val (outId, inId) = repository.recordTransfer(
            fromAccountId = 1L,
            toAccountId = 2L,
            amountPaise = 10_000_00L,
            occurredAt = 5000L
        )

        // Attempting to split/categorize standard transfer leg must throw IllegalArgumentException (§7.2)
        val exception = assertThrows(IllegalArgumentException::class.java) {
            runBlocking {
                repository.splitTransaction(
                    transactionId = outId,
                    allocations = listOf(Pair(101L, 10_000_00L))
                )
            }
        }
        assertThat(exception.message).contains("Standard non-savings transfers cannot have budgetary category allocations")
    }

    @Test
    fun testSavingsTransferSavingsLegCannotHaveAllocations() = runBlocking {
        // Transfer from HDFC (1) to PPF (3, savings)
        val (outId, inId) = repository.recordTransfer(
            fromAccountId = 1L,
            toAccountId = 3L,
            amountPaise = 10_000_00L,
            occurredAt = 5000L
        )

        // PPF is savings account -> inLeg cannot have category allocations (§8.3)
        val exception = assertThrows(IllegalArgumentException::class.java) {
            runBlocking {
                repository.splitTransaction(
                    transactionId = inId,
                    allocations = listOf(Pair(103L, 10_000_00L))
                )
            }
        }
        assertThat(exception.message).contains("Savings account leg of a transfer cannot have category allocations")
    }

    @Test
    fun testSavingsTransferAllocationMustBeSavingsBucket() = runBlocking {
        // Transfer from HDFC (1) to PPF (3, savings)
        val (outId, inId) = repository.recordTransfer(
            fromAccountId = 1L,
            toAccountId = 3L,
            amountPaise = 10_000_00L,
            occurredAt = 5000L
        )

        // 1. Trying to assign a NEEDS category (101) to operational leg throws IllegalArgumentException (§8.3)
        val needsEx = assertThrows(IllegalArgumentException::class.java) {
            runBlocking {
                repository.splitTransaction(
                    transactionId = outId,
                    allocations = listOf(Pair(101L, 10_000_00L))
                )
            }
        }
        assertThat(needsEx.message).contains("Savings transfer allocations must target SAVINGS bucket categories")

        // 2. Trying to assign a WANTS category (102) throws IllegalArgumentException (§8.3)
        val wantsEx = assertThrows(IllegalArgumentException::class.java) {
            runBlocking {
                repository.splitTransaction(
                    transactionId = outId,
                    allocations = listOf(Pair(102L, 10_000_00L))
                )
            }
        }
        assertThat(wantsEx.message).contains("Savings transfer allocations must target SAVINGS bucket categories")

        // 3. Assigning SAVINGS category (103) succeeds (§8.3)
        repository.splitTransaction(
            transactionId = outId,
            allocations = listOf(Pair(103L, 10_000_00L))
        )
        val leg = repository.getTransactionById(outId)!!
        assertThat(leg.status).isEqualTo(TransactionStatus.CATEGORIZED)
    }
}
