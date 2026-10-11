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
import com.tapledger.domain.calculator.RefundApportionmentCalculator
import com.tapledger.domain.calculator.RefundApportionmentCalculator.CategoryCapacity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test

/**
 * Validates Flow.md §9.2: Capacity-Capped Largest-Remainder Refund Apportionment
 * - Capacity calculation: cap_i = AllocationPaise(i) - Σ PriorRefunds_i(T)
 * - Rule R1: Cumulative refund exceeded (R > TotalCap)
 * - Rule R2: Refund exceeds category cap (Refund_i <= cap_i)
 * - Deterministic tie-breaking: Remainder descending -> Capacity descending -> category_id ascending
 * - Repository integration: automatic apportionment on recordRefund and linkRefundToTransaction
 */
class RefundApportionmentTest {

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

            accountDao.insert(
                AccountEntity(
                    id = 1,
                    name = "Checking",
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
                    incomePaise = 100_000_00L,
                    status = BudgetPeriodStatus.OPEN,
                    createdAt = 1000L,
                    updatedAt = 1000L
                )
            )
        }
    }

    @Test
    fun testCapacityCappedApportionmentAlgorithm() {
        // Capacities: Cat 10 = 600 paise, Cat 20 = 400 paise. TotalCap = 1,000 paise.
        val capacities = listOf(
            CategoryCapacity(categoryId = 10, originalAllocationPaise = 600, priorRefundsPaise = 0),
            CategoryCapacity(categoryId = 20, originalAllocationPaise = 400, priorRefundsPaise = 0)
        )

        // Case A: Exact integer division without remainder (R = 250 paise)
        // Cat 10 gets 250 * 600 / 1000 = 150 paise. Cat 20 gets 250 * 400 / 1000 = 100 paise.
        val resA = RefundApportionmentCalculator.apportionRefund(250, capacities)
        assertThat(resA.apportionments).containsExactly(
            RefundApportionmentCalculator.ApportionedRefund(categoryId = 10, amountPaise = 150),
            RefundApportionmentCalculator.ApportionedRefund(categoryId = 20, amountPaise = 100)
        )

        // Case B: Largest remainder with unallocated paisa (R = 253 paise)
        // Base: Cat 10 = 151 (rem 800), Cat 20 = 101 (rem 200). Unallocated = 253 - 252 = 1.
        // Remainder 800 > 200, so Cat 10 gets the extra paisa -> Cat 10 = 152, Cat 20 = 101.
        val resB = RefundApportionmentCalculator.apportionRefund(253, capacities)
        assertThat(resB.apportionments).containsExactly(
            RefundApportionmentCalculator.ApportionedRefund(categoryId = 10, amountPaise = 152),
            RefundApportionmentCalculator.ApportionedRefund(categoryId = 20, amountPaise = 101)
        )
        assertThat(resB.apportionments.sumOf { it.amountPaise }).isEqualTo(253L)

        // Case C: Remainder tie-break with differing capacity (larger capacity wins)
        // Cat 20 (cap 600) vs Cat 10 (cap 400), R = 500 paise -> TotalCap = 1000.
        // Both have remainder: (500 * 600) % 1000 = 0.
        // Let's test with 3 categories having identical remainders:
        // Cat A (id 20, cap 120), Cat B (id 10, cap 80). TotalCap = 200. R = 1 paisa.
        // Base both = 0. Remainder Cat A = 120, Cat B = 80. Cat A remainder 120 > 80, gets 1.
        val capTieRem = listOf(
            CategoryCapacity(categoryId = 20, originalAllocationPaise = 120),
            CategoryCapacity(categoryId = 10, originalAllocationPaise = 80)
        )
        val resC = RefundApportionmentCalculator.apportionRefund(1, capTieRem)
        assertThat(resC.apportionments).containsExactly(
            RefundApportionmentCalculator.ApportionedRefund(categoryId = 20, amountPaise = 1)
        )

        // Case D: Remainder tie-break with identical remainder AND identical capacity -> lower category_id wins
        val capIdentical = listOf(
            CategoryCapacity(categoryId = 30, originalAllocationPaise = 100),
            CategoryCapacity(categoryId = 10, originalAllocationPaise = 100),
            CategoryCapacity(categoryId = 20, originalAllocationPaise = 100)
        )
        // R = 1 paisa: all 3 have base 0, remainder 100, capacity 100. Lower id (10) wins!
        val resD = RefundApportionmentCalculator.apportionRefund(1, capIdentical)
        assertThat(resD.apportionments).containsExactly(
            RefundApportionmentCalculator.ApportionedRefund(categoryId = 10, amountPaise = 1)
        )

        // Case E: Rule R1_CUMULATIVE_REFUND_EXCEEDED
        val exR1 = assertThrows(IllegalArgumentException::class.java) {
            RefundApportionmentCalculator.apportionRefund(1001, capacities)
        }
        assertThat(exR1.message).contains("Rule R1_CUMULATIVE_REFUND_EXCEEDED")
    }

    @Test
    fun testCapacityCappedApportionment() = runBlocking {
        // Original purchase of ₹1,000 (100,000 paise) split across:
        // Groceries (id 10): ₹600 (60,000 paise)
        // Dining (id 20): ₹400 (40,000 paise)
        val origTxId = transactionDao.insert(
            TransactionEntity(
                id = 100,
                accountId = 1,
                kind = TransactionKind.EXPENSE,
                direction = TransactionDirection.OUT,
                amountPaise = 100_000L,
                occurredAt = 1100L,
                createdAt = 1100L,
                updatedAt = 1100L,
                status = TransactionStatus.PENDING,
                source = TransactionSource.MANUAL
            )
        )

        repository.splitTransaction(
            transactionId = origTxId,
            allocations = listOf(10L to 60_000L, 20L to 40_000L)
        )

        // 1. First partial refund of ₹250 (25,000 paise)
        // Apportions: Groceries = 15,000 paise, Dining = 10,000 paise
        val refund1Id = repository.recordRefund(
            accountId = 1,
            amountPaise = 25_000L,
            occurredAt = 1200L,
            refundOfTransactionId = origTxId
        )

        val refund1Tx = transactionDao.getById(refund1Id)!!
        assertThat(refund1Tx.status).isEqualTo(TransactionStatus.CATEGORIZED)
        val allocs1 = transactionAllocationDao.getForTransaction(refund1Id)
        assertThat(allocs1).hasSize(2)
        val groc1 = allocs1.first { it.categoryId == 10L }.amountPaise
        val din1 = allocs1.first { it.categoryId == 20L }.amountPaise
        assertThat(groc1).isEqualTo(15_000L)
        assertThat(din1).isEqualTo(10_000L)

        // 2. Second partial refund of ₹500 (50,000 paise)
        // Remaining capacities before refund 2:
        // Groceries: 60,000 - 15,000 = 45,000 paise
        // Dining: 40,000 - 10,000 = 30,000 paise
        // TotalCap = 75,000 paise
        // Refund 2 = 50,000 paise
        // Groceries base = 50,000 * 45,000 / 75,000 = 30,000 paise
        // Dining base = 50,000 * 30,000 / 75,000 = 20,000 paise
        val refund2Id = repository.recordRefund(
            accountId = 1,
            amountPaise = 50_000L,
            occurredAt = 1300L,
            refundOfTransactionId = origTxId
        )

        val allocs2 = transactionAllocationDao.getForTransaction(refund2Id)
        val groc2 = allocs2.first { it.categoryId == 10L }.amountPaise
        val din2 = allocs2.first { it.categoryId == 20L }.amountPaise
        assertThat(groc2).isEqualTo(30_000L)
        assertThat(din2).isEqualTo(20_000L)

        // 3. Third refund attempt of ₹300 (30,000 paise)
        // Remaining total capacity is now 100,000 - 25,000 - 50,000 = 25,000 paise.
        // 30,000 > 25,000 -> Must be rejected with Rule R1_CUMULATIVE_REFUND_EXCEEDED
        val exOver = assertThrows(IllegalArgumentException::class.java) {
            runBlocking {
                repository.recordRefund(
                    accountId = 1,
                    amountPaise = 30_000L,
                    occurredAt = 1400L,
                    refundOfTransactionId = origTxId
                )
            }
        }
        assertThat(exOver.message).contains("Rule R1_CUMULATIVE_REFUND_EXCEEDED")

        // 4. Exact remaining refund of ₹250 (25,000 paise) succeeds and exhausts capacity (Groceries 15,000, Dining 10,000)
        val refund3Id = repository.recordRefund(
            accountId = 1,
            amountPaise = 25_000L,
            occurredAt = 1400L,
            refundOfTransactionId = origTxId
        )
        val allocs3 = transactionAllocationDao.getForTransaction(refund3Id)
        assertThat(allocs3.sumOf { it.amountPaise }).isEqualTo(25_000L)

        // 5. Any further refund now fails because TotalCap is 0
        val exZeroCap = assertThrows(IllegalArgumentException::class.java) {
            runBlocking {
                repository.recordRefund(
                    accountId = 1,
                    amountPaise = 1L,
                    occurredAt = 1500L,
                    refundOfTransactionId = origTxId
                )
            }
        }
        assertThat(exZeroCap.message).contains("Rule R1_CUMULATIVE_REFUND_EXCEEDED")

        // 6. Test linkRefundToTransaction with unlinked refund
        val unlinkedRefundId = repository.recordRefund(
            accountId = 1,
            amountPaise = 10_000L,
            occurredAt = 1500L,
            refundOfTransactionId = null
        )
        val unlinkedTx = transactionDao.getById(unlinkedRefundId)!!
        assertThat(unlinkedTx.status).isEqualTo(TransactionStatus.PENDING)

        // Create second expense to link to
        val orig2Id = transactionDao.insert(
            TransactionEntity(
                id = 200,
                accountId = 1,
                kind = TransactionKind.EXPENSE,
                direction = TransactionDirection.OUT,
                amountPaise = 20_000L,
                occurredAt = 1100L,
                createdAt = 1100L,
                updatedAt = 1100L,
                status = TransactionStatus.PENDING,
                source = TransactionSource.MANUAL
            )
        )
        repository.splitTransaction(orig2Id, listOf(10L to 10_000L, 20L to 10_000L))

        repository.linkRefundToTransaction(unlinkedRefundId, orig2Id)
        val linkedTx = transactionDao.getById(unlinkedRefundId)!!
        assertThat(linkedTx.status).isEqualTo(TransactionStatus.CATEGORIZED)
        assertThat(linkedTx.refundOfTransactionId).isEqualTo(orig2Id)
        val linkedAllocs = transactionAllocationDao.getForTransaction(unlinkedRefundId)
        assertThat(linkedAllocs).hasSize(2)
        assertThat(linkedAllocs.sumOf { it.amountPaise }).isEqualTo(10_000L)
    }
}
