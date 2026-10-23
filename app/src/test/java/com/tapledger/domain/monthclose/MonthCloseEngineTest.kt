package com.tapledger.domain.monthclose

import com.google.common.truth.Truth.assertThat
import com.tapledger.core.database.DatabaseTransactionRunner
import com.tapledger.core.database.dao.AccountDao
import com.tapledger.core.database.dao.AdvanceLedgerDao
import com.tapledger.core.database.dao.BudgetAdjustmentDao
import com.tapledger.core.database.dao.BudgetAllocationDao
import com.tapledger.core.database.dao.BudgetPeriodDao
import com.tapledger.core.database.dao.CategoryDao
import com.tapledger.core.database.dao.MonthCloseDao
import com.tapledger.core.database.dao.SavingsTransferPlanDao
import com.tapledger.core.database.dao.TransactionAllocationDao
import com.tapledger.core.database.dao.TransactionDao
import com.tapledger.core.database.entity.AccountEntity
import com.tapledger.core.database.entity.AdvanceLedgerEntity
import com.tapledger.core.database.entity.BudgetAdjustmentEntity
import com.tapledger.core.database.entity.BudgetAllocationEntity
import com.tapledger.core.database.entity.BudgetPeriodEntity
import com.tapledger.core.database.entity.CategoryEntity
import com.tapledger.core.database.entity.MonthCloseEntity
import com.tapledger.core.database.entity.SavingsTransferPlanEntity
import com.tapledger.core.database.entity.TransactionAllocationEntity
import com.tapledger.core.database.entity.TransactionEntity
import com.tapledger.core.model.AccountType
import com.tapledger.core.model.AdvanceDirection
import com.tapledger.core.model.AdvancePolicy
import com.tapledger.core.model.BudgetBucket
import com.tapledger.core.model.BudgetPeriodStatus
import com.tapledger.core.model.MonthCloseReconciliationStatus
import com.tapledger.core.model.TransactionDirection
import com.tapledger.core.model.TransactionKind
import com.tapledger.core.model.TransactionSource
import com.tapledger.core.model.TransactionStatus
import com.tapledger.core.model.TransferPlanKind
import com.tapledger.core.model.TransferPlanStatus
import com.tapledger.core.util.DateTimeUtils
import com.tapledger.data.repository.BudgetRepository
import com.tapledger.domain.validator.DataIntegrityChecker
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test

/**
 * Validates Flow.md §10: Budget Period Lifecycle & Ordered Month-Close Engine
 * - §10.2: Pre-close checklist gates
 * - §10.3: Sequential close order
 * - §10.3: Headroom-capped deficit carry-forward & prioritized absorption
 * - §10.4: Atomic period reopen rollback
 */
class MonthCloseEngineTest {

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
        override suspend fun getByYearMonth(year: Int, month: Int): BudgetPeriodEntity? =
            storage.values.firstOrNull { it.year == year && it.month == month }
        override fun observeByYearMonth(year: Int, month: Int): Flow<BudgetPeriodEntity?> = emptyFlow()
        override fun getAllPeriods(): Flow<List<BudgetPeriodEntity>> = emptyFlow()
        override suspend fun getAllPeriodsSync(): List<BudgetPeriodEntity> = storage.values.toList()
        override suspend fun getPeriodForTimestamp(timestamp: Long): BudgetPeriodEntity? =
            storage.values.firstOrNull { timestamp >= it.startAt && timestamp < it.endAt }
        override suspend fun getEarliestPeriod(): BudgetPeriodEntity? = storage.values.minByOrNull { it.startAt }
        override suspend fun getLatestPeriod(): BudgetPeriodEntity? = storage.values.maxByOrNull { it.startAt }
        override suspend fun getOpenPeriodForTimestamp(timestamp: Long): BudgetPeriodEntity? =
            storage.values.firstOrNull { it.status == BudgetPeriodStatus.OPEN && timestamp >= it.startAt && timestamp < it.endAt }
        override suspend fun getFirstOpenPeriod(): BudgetPeriodEntity? =
            storage.values.filter { it.status == BudgetPeriodStatus.OPEN }.minByOrNull { it.startAt }
        override suspend fun getClosedPeriodEndingAfter(startOfDayTimestamp: Long): BudgetPeriodEntity? =
            storage.values.firstOrNull { it.status == BudgetPeriodStatus.CLOSED && it.endAt > startOfDayTimestamp }
        override suspend fun getClosedPeriodForTimestamp(timestamp: Long): BudgetPeriodEntity? =
            storage.values.firstOrNull { it.status == BudgetPeriodStatus.CLOSED && timestamp >= it.startAt && timestamp < it.endAt }
    }

    private class FakeBudgetAllocationDao : BudgetAllocationDao() {
        val storage = mutableMapOf<Long, BudgetAllocationEntity>()
        private var nextId = 1L

        override suspend fun getAdjustmentsForPeriod(periodId: Long): List<BudgetAdjustmentEntity> = emptyList()
        override suspend fun getAdvancesForPeriod(periodId: Long): List<AdvanceLedgerEntity> = emptyList()

        override suspend fun insertInternal(allocation: BudgetAllocationEntity): Long {
            val id = if (allocation.id == 0L) nextId++ else allocation.id
            storage[id] = allocation.copy(id = id)
            return id
        }

        override suspend fun insertAllInternal(allocations: List<BudgetAllocationEntity>): List<Long> =
            allocations.map { insertInternal(it) }

        override suspend fun updateInternal(allocation: BudgetAllocationEntity) {
            storage[allocation.id] = allocation
        }

        override fun observeAllocationsForPeriod(periodId: Long): Flow<List<BudgetAllocationEntity>> = emptyFlow()
        override suspend fun getAllocationsForPeriodSync(periodId: Long): List<BudgetAllocationEntity> =
            storage.values.filter { it.budgetPeriodId == periodId }
        override suspend fun getAllAllocationsSync(): List<BudgetAllocationEntity> = storage.values.toList()
        override suspend fun getAllocation(periodId: Long, categoryId: Long): BudgetAllocationEntity? =
            storage.values.firstOrNull { it.budgetPeriodId == periodId && it.categoryId == categoryId }
        override suspend fun deleteAllocation(periodId: Long, categoryId: Long) {
            storage.entries.removeIf { it.value.budgetPeriodId == periodId && it.value.categoryId == categoryId }
        }
    }

    private class FakeBudgetAdjustmentDao : BudgetAdjustmentDao {
        val storage = mutableListOf<BudgetAdjustmentEntity>()
        private var nextId = 1L
        override suspend fun insert(adjustment: BudgetAdjustmentEntity): Long {
            val id = if (adjustment.id == 0L) nextId++ else adjustment.id
            storage.add(adjustment.copy(id = id))
            return id
        }
        override fun observeAdjustmentsForPeriod(periodId: Long): Flow<List<BudgetAdjustmentEntity>> = emptyFlow()
        override suspend fun getAdjustmentsForPeriodSync(periodId: Long): List<BudgetAdjustmentEntity> =
            storage.filter { it.budgetPeriodId == periodId }
        override suspend fun getAllAdjustmentsSync(): List<BudgetAdjustmentEntity> = storage.toList()
    }

    private class FakeAdvanceLedgerDao : AdvanceLedgerDao {
        val storage = mutableListOf<AdvanceLedgerEntity>()
        private var nextId = 1L
        override suspend fun insert(entry: AdvanceLedgerEntity): Long {
            val id = if (entry.id == 0L) nextId++ else entry.id
            storage.add(entry.copy(id = id))
            return id
        }
        override suspend fun insertAll(entries: List<AdvanceLedgerEntity>): List<Long> = entries.map { insert(it) }
        override fun observeAppliedToPeriod(periodId: Long): Flow<List<AdvanceLedgerEntity>> = emptyFlow()
        override suspend fun getAppliedToPeriodSync(periodId: Long): List<AdvanceLedgerEntity> =
            storage.filter { it.appliedToPeriodId == periodId }
        override suspend fun getSourcePeriodSync(periodId: Long): List<AdvanceLedgerEntity> =
            storage.filter { it.sourcePeriodId == periodId }
        override suspend fun getUnassignedDeficitPoolSync(): List<AdvanceLedgerEntity> =
            storage.filter { it.appliedToPeriodId == null && it.direction == AdvanceDirection.NEGATIVE }
        override suspend fun getUnassignedPoolSync(): List<AdvanceLedgerEntity> =
            storage.filter { it.appliedToPeriodId == null }
        override suspend fun getForCategory(categoryId: Long): List<AdvanceLedgerEntity> =
            storage.filter { it.categoryId == categoryId }
        override suspend fun getAllAdvancesSync(): List<AdvanceLedgerEntity> = storage.toList()
        override suspend fun deleteForSourcePeriod(sourcePeriodId: Long) {
            storage.removeIf { it.sourcePeriodId == sourcePeriodId }
        }
    }

    private class FakeMonthCloseDao : MonthCloseDao {
        val storage = mutableMapOf<Long, MonthCloseEntity>()
        private var nextId = 1L
        override suspend fun insert(monthClose: MonthCloseEntity): Long {
            val id = if (monthClose.id == 0L) nextId++ else monthClose.id
            storage[id] = monthClose.copy(id = id)
            return id
        }
        override suspend fun getByPeriodId(periodId: Long): MonthCloseEntity? =
            storage.values.firstOrNull { it.budgetPeriodId == periodId }
        override fun observeByPeriodId(periodId: Long): Flow<MonthCloseEntity?> = emptyFlow()
        override suspend fun deleteForPeriod(periodId: Long) {
            storage.entries.removeIf { it.value.budgetPeriodId == periodId }
        }
        override suspend fun getAllMonthClosesSync(): List<MonthCloseEntity> = storage.values.toList()
        override fun getAllMonthClosesFlow(): Flow<List<MonthCloseEntity>> = flowOf(storage.values.toList())
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
        override suspend fun getTransactionsInPeriod(startEpochMillis: Long, endEpochMillis: Long): List<TransactionEntity> =
            storage.values.filter { it.occurredAt in startEpochMillis until endEpochMillis }
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
        override suspend fun getTransactionsForAccountSync(accountId: Long): List<TransactionEntity> =
            storage.values.filter { it.accountId == accountId && it.status != TransactionStatus.VOIDED }
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
        override suspend fun getAllocationsInPeriod(startEpochMillis: Long, endEpochMillis: Long): List<TransactionAllocationEntity> =
            storage.values.toList()
        override suspend fun getAllocationsForCategoryInPeriod(categoryId: Long, startEpochMillis: Long, endEpochMillis: Long): List<TransactionAllocationEntity> = emptyList()
        override suspend fun getAllTransactionAllocationsSync(): List<TransactionAllocationEntity> = storage.values.toList()
        override fun observeAllAllocations(): Flow<List<TransactionAllocationEntity>> = emptyFlow()
    }

    private class FakeSavingsTransferPlanDao : SavingsTransferPlanDao {
        val plans = mutableMapOf<Long, SavingsTransferPlanEntity>()
        private var nextId = 1L

        override suspend fun insert(plan: SavingsTransferPlanEntity): Long {
            val id = if (plan.id == 0L) nextId++ else plan.id
            plans[id] = plan.copy(id = id)
            return id
        }
        override suspend fun insertOrUpdate(plan: SavingsTransferPlanEntity): Long {
            val id = if (plan.id == 0L) nextId++ else plan.id
            plans[id] = plan.copy(id = id)
            return id
        }
        override suspend fun update(plan: SavingsTransferPlanEntity) { plans[plan.id] = plan }
        override suspend fun delete(plan: SavingsTransferPlanEntity) { plans.remove(plan.id) }
        override suspend fun getById(id: Long): SavingsTransferPlanEntity? = plans[id]
        override fun getByIdFlow(id: Long): Flow<SavingsTransferPlanEntity?> = emptyFlow()
        override suspend fun getByPeriodAndKind(periodId: Long, kind: TransferPlanKind): SavingsTransferPlanEntity? =
            plans.values.find { it.periodId == periodId && it.kind == kind }
        override fun getByPeriodAndKindFlow(periodId: Long, kind: TransferPlanKind): Flow<SavingsTransferPlanEntity?> = emptyFlow()
        override suspend fun getPlansForPeriod(periodId: Long): List<SavingsTransferPlanEntity> =
            plans.values.filter { it.periodId == periodId }
        override fun getPlansForPeriodFlow(periodId: Long): Flow<List<SavingsTransferPlanEntity>> = emptyFlow()
        override fun getActivePlansForPeriodFlow(periodId: Long): Flow<List<SavingsTransferPlanEntity>> = emptyFlow()
        override suspend fun getPlansByStatus(status: TransferPlanStatus): List<SavingsTransferPlanEntity> =
            plans.values.filter { it.status == status }
        override fun getPlansByStatusFlow(status: TransferPlanStatus): Flow<List<SavingsTransferPlanEntity>> = emptyFlow()
        override suspend fun getByTransferGroupId(transferGroupId: String): SavingsTransferPlanEntity? =
            plans.values.find { it.transferGroupId == transferGroupId }
        override suspend fun findMarkedMovedPlans(): List<SavingsTransferPlanEntity> =
            plans.values.filter { it.status == TransferPlanStatus.MARKED_MOVED }
        override fun findMarkedMovedPlansFlow(): Flow<List<SavingsTransferPlanEntity>> = emptyFlow()
        override suspend fun expireSuggestedPlansForPeriod(periodId: Long) {
            plans.values.filter { it.periodId == periodId && it.status == TransferPlanStatus.SUGGESTED }.forEach {
                plans[it.id] = it.copy(status = TransferPlanStatus.EXPIRED)
            }
        }
        override suspend fun getAllPlansSync(): List<SavingsTransferPlanEntity> = plans.values.toList()
    }

    private lateinit var accountDao: FakeAccountDao
    private lateinit var categoryDao: FakeCategoryDao
    private lateinit var budgetPeriodDao: FakeBudgetPeriodDao
    private lateinit var budgetAllocationDao: FakeBudgetAllocationDao
    private lateinit var budgetAdjustmentDao: FakeBudgetAdjustmentDao
    private lateinit var advanceLedgerDao: FakeAdvanceLedgerDao
    private lateinit var monthCloseDao: FakeMonthCloseDao
    private lateinit var transactionDao: FakeTransactionDao
    private lateinit var transactionAllocationDao: FakeTransactionAllocationDao
    private lateinit var savingsTransferPlanDao: FakeSavingsTransferPlanDao
    private lateinit var budgetRepository: BudgetRepository
    private lateinit var monthCloseEngine: MonthCloseEngine

    @Before
    fun setUp() {
        accountDao = FakeAccountDao()
        categoryDao = FakeCategoryDao()
        budgetPeriodDao = FakeBudgetPeriodDao()
        budgetAllocationDao = FakeBudgetAllocationDao()
        budgetAdjustmentDao = FakeBudgetAdjustmentDao()
        advanceLedgerDao = FakeAdvanceLedgerDao()
        monthCloseDao = FakeMonthCloseDao()
        transactionDao = FakeTransactionDao()
        transactionAllocationDao = FakeTransactionAllocationDao()
        savingsTransferPlanDao = FakeSavingsTransferPlanDao()

        budgetRepository = BudgetRepository(
            transactionRunner = DirectTransactionRunner(),
            categoryDao = categoryDao,
            budgetPeriodDao = budgetPeriodDao,
            budgetAllocationDao = budgetAllocationDao,
            budgetAdjustmentDao = budgetAdjustmentDao
        )

        monthCloseEngine = MonthCloseEngine(
            transactionRunner = DirectTransactionRunner(),
            budgetPeriodDao = budgetPeriodDao,
            monthCloseDao = monthCloseDao,
            advanceLedgerDao = advanceLedgerDao,
            budgetAllocationDao = budgetAllocationDao,
            budgetAdjustmentDao = budgetAdjustmentDao,
            transactionDao = transactionDao,
            transactionAllocationDao = transactionAllocationDao,
            accountDao = accountDao,
            categoryDao = categoryDao,
            budgetRepository = budgetRepository,
            dataIntegrityChecker = DataIntegrityChecker(),
            savingsTransferPlanDao = savingsTransferPlanDao
        )
    }

    @Test
    fun testPreCloseChecklistGates() = runBlocking {
        // Seed an account and categories
        accountDao.insert(
            AccountEntity(
                id = 1,
                name = "Checking",
                type = AccountType.BANK,
                identifierLast4 = "1234",
                openingBalancePaise = 50_000_00L,
                openingBalanceDate = 1000L,
                createdAt = 1000L,
                updatedAt = 1000L
            )
        )
        categoryDao.insert(CategoryEntity(id = 1, name = "Groceries", defaultBucket = BudgetBucket.NEEDS, defaultSortOrder = 1))

        // Period 1: in the future (endAt > now)
        val futurePeriod = budgetPeriodDao.insert(
            BudgetPeriodEntity(
                id = 1,
                year = 2099,
                month = 1,
                startAt = 4000_000_000_000L,
                endAt = 4001_000_000_000L,
                incomePaise = 50_000_00L,
                isReviewed = true,
                status = BudgetPeriodStatus.OPEN,
                createdAt = 1000L,
                updatedAt = 1000L
            )
        )

        // Gate 1: cannot close future period
        val ex1 = assertThrows(IllegalArgumentException::class.java) {
            runBlocking { monthCloseEngine.closeMonth(futurePeriod) }
        }
        assertThat(ex1.message).contains("before its end date")

        // Create a past period (2020-01)
        val pastPeriodId = budgetPeriodDao.insert(
            BudgetPeriodEntity(
                id = 2,
                year = 2020,
                month = 1,
                startAt = 1577836800000L, // 2020-01-01 IST
                endAt = 1580515200000L,   // 2020-02-01 IST
                incomePaise = 50_000_00L,
                isReviewed = false,
                status = BudgetPeriodStatus.OPEN,
                createdAt = 1000L,
                updatedAt = 1000L
            )
        )

        // Gate 3: is_reviewed == false blocks close
        val ex3 = assertThrows(IllegalArgumentException::class.java) {
            runBlocking { monthCloseEngine.closeMonth(pastPeriodId) }
        }
        assertThat(ex3.message).contains("is_reviewed == false")

        // Mark isReviewed = true
        val pastPeriod = budgetPeriodDao.getById(pastPeriodId)!!
        budgetPeriodDao.update(pastPeriod.copy(isReviewed = true))

        // Gate 4: pending transaction exists before period end
        val pendingTxId = transactionDao.insert(
            TransactionEntity(
                id = 101,
                accountId = 1,
                kind = TransactionKind.EXPENSE,
                direction = TransactionDirection.OUT,
                amountPaise = 500_00L,
                occurredAt = 1578000000000L, // inside period
                createdAt = 1000L,
                updatedAt = 1000L,
                status = TransactionStatus.PENDING,
                source = TransactionSource.MANUAL
            )
        )
        val ex4 = assertThrows(IllegalStateException::class.java) {
            runBlocking { monthCloseEngine.closeMonth(pastPeriodId) }
        }
        assertThat(ex4.message).contains("pending transactions exist")

        // Resolve pending transaction
        transactionDao.update(transactionDao.getById(pendingTxId)!!.copy(status = TransactionStatus.CONFIRMED))

        // Gate 5: unassigned account transaction exists
        val unassignedTxId = transactionDao.insert(
            TransactionEntity(
                id = 102,
                accountId = null,
                kind = TransactionKind.EXPENSE,
                direction = TransactionDirection.OUT,
                amountPaise = 200_00L,
                occurredAt = 1578100000000L,
                createdAt = 1000L,
                updatedAt = 1000L,
                status = TransactionStatus.CONFIRMED,
                source = TransactionSource.MANUAL
            )
        )
        val ex5 = assertThrows(IllegalStateException::class.java) {
            runBlocking { monthCloseEngine.closeMonth(pastPeriodId) }
        }
        assertThat(ex5.message).contains("unassigned transactions exist")

        // Resolve unassigned transaction
        transactionDao.update(transactionDao.getById(unassignedTxId)!!.copy(accountId = 1))

        // Pre-close checklist evaluation now reports canClose = true
        val checklist = monthCloseEngine.evaluatePreCloseChecklist(pastPeriodId)
        assertThat(checklist.canClose).isTrue()
    }

    @Test
    fun testSequentialCloseOrder() = runBlocking {
        // Create Period 1 (2020-01) and Period 2 (2020-02)
        val p1 = budgetPeriodDao.insert(
            BudgetPeriodEntity(
                id = 10,
                year = 2020,
                month = 1,
                startAt = 1577836800000L,
                endAt = 1580515200000L,
                incomePaise = 50_000_00L,
                isReviewed = true,
                status = BudgetPeriodStatus.OPEN,
                createdAt = 1000L,
                updatedAt = 1000L
            )
        )
        val p2 = budgetPeriodDao.insert(
            BudgetPeriodEntity(
                id = 20,
                year = 2020,
                month = 2,
                startAt = 1580515200000L,
                endAt = 1583020800000L,
                incomePaise = 50_000_00L,
                isReviewed = true,
                status = BudgetPeriodStatus.OPEN,
                createdAt = 1000L,
                updatedAt = 1000L
            )
        )

        // Attempt to close Period 2 while Period 1 is OPEN -> Must fail sequential close order
        val ex = assertThrows(IllegalStateException::class.java) {
            runBlocking { monthCloseEngine.closeMonth(p2) }
        }
        assertThat(ex.message).contains("earlier period (2020-1) is still OPEN")
    }

    @Test
    fun testHeadroomCappedDeficitAbsorption() = runBlocking {
        // Seed account
        accountDao.insert(
            AccountEntity(
                id = 1,
                name = "Checking",
                type = AccountType.BANK,
                identifierLast4 = "1234",
                openingBalancePaise = 500_000_00L,
                openingBalanceDate = 1000L,
                createdAt = 1000L,
                updatedAt = 1000L
            )
        )

        // Seed Categories
        categoryDao.insert(CategoryEntity(id = 1, name = "Groceries", defaultBucket = BudgetBucket.NEEDS, defaultSortOrder = 1))
        categoryDao.insert(CategoryEntity(id = 2, name = "Emergency Fund", defaultBucket = BudgetBucket.SAVINGS, defaultSortOrder = 2))

        // Month M: 2020-01 (past)
        val pMId = budgetPeriodDao.insert(
            BudgetPeriodEntity(
                id = 1,
                year = 2020,
                month = 1,
                startAt = 1577836800000L,
                endAt = 1580515200000L,
                incomePaise = 100_000_00L,
                isReviewed = true,
                status = BudgetPeriodStatus.OPEN,
                createdAt = 1000L,
                updatedAt = 1000L
            )
        )

        // Next Month M+1: 2020-02 (past) with Base Allocations:
        // Groceries (Cat 1): Base = ₹10,000 (Headroom = ₹10,000)
        // Emergency Fund (Cat 2): Base = ₹20,000
        val pMPlus1Id = budgetPeriodDao.insert(
            BudgetPeriodEntity(
                id = 2,
                year = 2020,
                month = 2,
                startAt = 1580515200000L,
                endAt = 1583020800000L,
                incomePaise = 100_000_00L,
                isReviewed = true,
                status = BudgetPeriodStatus.OPEN,
                createdAt = 1000L,
                updatedAt = 1000L
            )
        )
        budgetAllocationDao.insert(
            BudgetAllocationEntity(
                id = 21,
                budgetPeriodId = pMPlus1Id,
                categoryId = 1,
                categoryNameSnapshot = "Groceries",
                bucketSnapshot = BudgetBucket.NEEDS,
                sortOrderSnapshot = 1,
                advancePolicySnapshot = AdvancePolicy.EXPIRE,
                amountPaise = 10_000_00L,
                createdAt = 1000L,
                updatedAt = 1000L
            )
        )
        budgetAllocationDao.insert(
            BudgetAllocationEntity(
                id = 22,
                budgetPeriodId = pMPlus1Id,
                categoryId = 2,
                categoryNameSnapshot = "Emergency Fund",
                bucketSnapshot = BudgetBucket.SAVINGS,
                sortOrderSnapshot = 2,
                advancePolicySnapshot = AdvancePolicy.CARRY_TO_ADVANCE,
                amountPaise = 20_000_00L,
                createdAt = 1000L,
                updatedAt = 1000L
            )
        )

        // Setup Month M allocations:
        budgetAllocationDao.insert(
            BudgetAllocationEntity(
                id = 11,
                budgetPeriodId = pMId,
                categoryId = 1,
                categoryNameSnapshot = "Groceries",
                bucketSnapshot = BudgetBucket.NEEDS,
                sortOrderSnapshot = 1,
                advancePolicySnapshot = AdvancePolicy.EXPIRE,
                amountPaise = 10_000_00L,
                createdAt = 1000L,
                updatedAt = 1000L
            )
        )
        budgetAllocationDao.insert(
            BudgetAllocationEntity(
                id = 12,
                budgetPeriodId = pMId,
                categoryId = 2,
                categoryNameSnapshot = "Emergency Fund",
                bucketSnapshot = BudgetBucket.SAVINGS,
                sortOrderSnapshot = 2,
                advancePolicySnapshot = AdvancePolicy.CARRY_TO_ADVANCE,
                amountPaise = 20_000_00L,
                createdAt = 1000L,
                updatedAt = 1000L
            )
        )

        // 1. Seed Unassigned Historical Pool for Groceries:
        // Older debt in unassigned pool = ₹4,000 (4,000,00 paise)
        advanceLedgerDao.insert(
            AdvanceLedgerEntity(
                id = 1,
                sourcePeriodId = 999L,
                appliedToPeriodId = null,
                categoryId = 1,
                amountPaise = 4_000_00L,
                direction = AdvanceDirection.NEGATIVE,
                reason = "Historical debt in unassigned pool",
                createdAt = 500L
            )
        )

        // 2. Month M spending:
        // Groceries spending = ₹18,000 (Budget was ₹10,000 -> Overspent by ₹8,000)
        transactionDao.insert(
            TransactionEntity(
                id = 1,
                accountId = 1,
                kind = TransactionKind.EXPENSE,
                direction = TransactionDirection.OUT,
                amountPaise = 18_000_00L,
                occurredAt = 1577900000000L,
                createdAt = 1000L,
                updatedAt = 1000L,
                status = TransactionStatus.CATEGORIZED,
                source = TransactionSource.MANUAL
            )
        )
        transactionAllocationDao.insert(
            TransactionAllocationEntity(id = 1, transactionId = 1, categoryId = 1, amountPaise = 18_000_00L)
        )

        // Emergency Fund (SAVINGS bucket): Spent ₹15,000 (Target was ₹20,000 -> Under-saved by ₹5,000)
        transactionDao.insert(
            TransactionEntity(
                id = 2,
                accountId = 1,
                kind = TransactionKind.TRANSFER,
                direction = TransactionDirection.OUT,
                amountPaise = 15_000_00L,
                occurredAt = 1577900000000L,
                createdAt = 1000L,
                updatedAt = 1000L,
                status = TransactionStatus.CATEGORIZED,
                source = TransactionSource.MANUAL
            )
        )
        transactionAllocationDao.insert(
            TransactionAllocationEntity(id = 2, transactionId = 2, categoryId = 2, amountPaise = 15_000_00L)
        )

        // Close Month M!
        val closeId = monthCloseEngine.closeMonth(pMId)
        assertThat(closeId).isGreaterThan(0L)

        // Verify period M is CLOSED
        val closedM = budgetPeriodDao.getById(pMId)!!
        assertThat(closedM.status).isEqualTo(BudgetPeriodStatus.CLOSED)
        assertThat(closedM.closedAt).isNotNull()

        // Verify Advance Ledger entries:
        // Groceries M+1 initial headroom was ₹10,000.
        // Priority 1: Historical pool of ₹4,000 absorbed first:
        // - Offset row (applied_to = NULL, direction = POSITIVE, ₹4,000)
        // - Application row in M+1 (applied_to = 2, direction = NEGATIVE, ₹4,000)
        // Remaining headroom in M+1 is now 10,000 - 4,000 = ₹6,000.
        // Priority 2: New overspending deficit is ₹8,000.
        // - Applied to M+1 = min(8000, 6000) = ₹6,000 (applied_to = 2, direction = NEGATIVE, ₹6,000)
        // - Uncovered new deficit = 8,000 - 6,000 = ₹2,000 sent to pool (applied_to = NULL, direction = NEGATIVE, ₹2,000)
        val allAdvances = advanceLedgerDao.storage

        val offsetRow = allAdvances.firstOrNull { it.appliedToPeriodId == null && it.direction == AdvanceDirection.POSITIVE && it.categoryId == 1L }
        assertThat(offsetRow).isNotNull()
        assertThat(offsetRow!!.amountPaise).isEqualTo(4_000_00L)

        val appliedRowsMPlus1 = allAdvances.filter { it.appliedToPeriodId == pMPlus1Id && it.direction == AdvanceDirection.NEGATIVE && it.categoryId == 1L }
        assertThat(appliedRowsMPlus1.sumOf { it.amountPaise }).isEqualTo(10_000_00L) // 4,000 + 6,000 = 10,000 (exact headroom)

        val newUncoveredPoolRow = allAdvances.firstOrNull { it.appliedToPeriodId == null && it.direction == AdvanceDirection.NEGATIVE && it.sourcePeriodId == pMId && it.categoryId == 1L }
        assertThat(newUncoveredPoolRow).isNotNull()
        assertThat(newUncoveredPoolRow!!.amountPaise).isEqualTo(2_000_00L)

        // Emergency Fund under-saved shortfall (₹5,000) carried forward as POSITIVE advance (§8.4)
        val savingsShortfallRow = allAdvances.firstOrNull { it.appliedToPeriodId == pMPlus1Id && it.direction == AdvanceDirection.POSITIVE && it.categoryId == 2L }
        assertThat(savingsShortfallRow).isNotNull()
        assertThat(savingsShortfallRow!!.amountPaise).isEqualTo(5_000_00L)

        // Verify month_closes record
        val monthClose = monthCloseDao.getByPeriodId(pMId)!!
        assertThat(monthClose.totalSpentPaise).isEqualTo(18_000_00L)
        assertThat(monthClose.totalSavedPaise).isEqualTo(15_000_00L)
    }

    @Test
    fun testAtomicPeriodReopenRollback() = runBlocking {
        // Create 2 closed periods
        val p1 = budgetPeriodDao.insert(
            BudgetPeriodEntity(
                id = 1,
                year = 2020,
                month = 1,
                startAt = 1000L,
                endAt = 2000L,
                incomePaise = 50_000_00L,
                isReviewed = true,
                status = BudgetPeriodStatus.CLOSED,
                closedAt = 2000L,
                createdAt = 1000L,
                updatedAt = 2000L
            )
        )
        val p2 = budgetPeriodDao.insert(
            BudgetPeriodEntity(
                id = 2,
                year = 2020,
                month = 2,
                startAt = 2000L,
                endAt = 3000L,
                incomePaise = 50_000_00L,
                isReviewed = true,
                status = BudgetPeriodStatus.CLOSED,
                closedAt = 3000L,
                createdAt = 1000L,
                updatedAt = 3000L
            )
        )

        monthCloseDao.insert(
            MonthCloseEntity(
                id = 1,
                budgetPeriodId = 1,
                closedAt = 2000L,
                plannedIncomePaise = 50_000_00L,
                actualIncomePaise = 50_000_00L,
                totalBudgetedPaise = 50_000_00L,
                totalSpentPaise = 50_000_00L,
                totalSavedPaise = 0L,
                reconciliationStatus = MonthCloseReconciliationStatus.RECONCILED,
                closingBalancesJson = "{}"
            )
        )
        monthCloseDao.insert(
            MonthCloseEntity(
                id = 2,
                budgetPeriodId = 2,
                closedAt = 3000L,
                plannedIncomePaise = 50_000_00L,
                actualIncomePaise = 50_000_00L,
                totalBudgetedPaise = 50_000_00L,
                totalSpentPaise = 50_000_00L,
                totalSavedPaise = 0L,
                reconciliationStatus = MonthCloseReconciliationStatus.RECONCILED,
                closingBalancesJson = "{}"
            )
        )

        advanceLedgerDao.insert(
            AdvanceLedgerEntity(
                id = 10,
                sourcePeriodId = 2,
                appliedToPeriodId = 3,
                categoryId = 1,
                amountPaise = 5_000_00L,
                direction = AdvanceDirection.NEGATIVE,
                reason = "Deficit from P2",
                createdAt = 3000L
            )
        )

        // 1. Attempting to reopen Period 1 while Period 2 is CLOSED must fail (§10.1, §10.4)
        val ex = assertThrows(IllegalStateException::class.java) {
            runBlocking { monthCloseEngine.reopenMonth(p1) }
        }
        assertThat(ex.message).contains("latest closed budget period")

        // 2. Reopening Period 2 (latest closed) succeeds
        monthCloseEngine.reopenMonth(p2)

        val reopenedP2 = budgetPeriodDao.getById(p2)!!
        assertThat(reopenedP2.status).isEqualTo(BudgetPeriodStatus.OPEN)
        assertThat(reopenedP2.closedAt).isNull()

        // Period 2's generated rows in advance_ledger are cleanly deleted
        assertThat(advanceLedgerDao.storage.filter { it.sourcePeriodId == p2 }).isEmpty()

        // Period 2's month_closes record is cleanly deleted
        assertThat(monthCloseDao.getByPeriodId(p2)).isNull()

        // 3. Now Period 1 is the latest closed period -> Reopening Period 1 succeeds
        monthCloseEngine.reopenMonth(p1)
        val reopenedP1 = budgetPeriodDao.getById(p1)!!
        assertThat(reopenedP1.status).isEqualTo(BudgetPeriodStatus.OPEN)
        assertThat(monthCloseDao.getByPeriodId(p1)).isNull()
    }

    @Test
    fun testMonthClose_expiresOpenSuggestedSavingsTransferPlans() = runBlocking {
        accountDao.insert(
            AccountEntity(
                id = 1,
                name = "Checking",
                type = AccountType.BANK,
                identifierLast4 = "1234",
                openingBalancePaise = 50_000_00L,
                openingBalanceDate = 1000L,
                createdAt = 1000L,
                updatedAt = 1000L
            )
        )
        categoryDao.insert(CategoryEntity(id = 1, name = "Groceries", defaultBucket = BudgetBucket.NEEDS, defaultSortOrder = 1))

        val periodId = budgetPeriodDao.insert(
            BudgetPeriodEntity(
                id = 10,
                year = 2020,
                month = 1,
                startAt = 1577836800000L,
                endAt = 1580515200000L,
                incomePaise = 50_000_00L,
                isReviewed = true,
                status = BudgetPeriodStatus.OPEN,
                createdAt = 1000L,
                updatedAt = 1000L
            )
        )

        val planSuggested = SavingsTransferPlanEntity(
            id = 1L,
            periodId = periodId,
            kind = TransferPlanKind.PAYDAY,
            sourceAccountId = 1L,
            destAccountId = 2L,
            suggestedPaise = 10_000_00L,
            status = TransferPlanStatus.SUGGESTED,
            createdAt = 1000L
        )
        val planMoved = SavingsTransferPlanEntity(
            id = 2L,
            periodId = periodId,
            kind = TransferPlanKind.SWEEP,
            sourceAccountId = 1L,
            destAccountId = 2L,
            suggestedPaise = 5_000_00L,
            movedPaise = 5_000_00L,
            status = TransferPlanStatus.MARKED_MOVED,
            transferGroupId = "xfer-2",
            createdAt = 1000L
        )
        val planVerified = SavingsTransferPlanEntity(
            id = 3L,
            periodId = periodId,
            kind = TransferPlanKind.MANUAL,
            sourceAccountId = 1L,
            destAccountId = 2L,
            suggestedPaise = 2_000_00L,
            movedPaise = 2_000_00L,
            status = TransferPlanStatus.VERIFIED,
            transferGroupId = "xfer-3",
            createdAt = 1000L
        )
        savingsTransferPlanDao.insert(planSuggested)
        savingsTransferPlanDao.insert(planMoved)
        savingsTransferPlanDao.insert(planVerified)

        monthCloseEngine.closeMonth(periodId)

        // SUGGESTED plan must transition to EXPIRED
        assertThat(savingsTransferPlanDao.getById(1L)?.status).isEqualTo(TransferPlanStatus.EXPIRED)
        // MARKED_MOVED and VERIFIED plans remain intact
        assertThat(savingsTransferPlanDao.getById(2L)?.status).isEqualTo(TransferPlanStatus.MARKED_MOVED)
        assertThat(savingsTransferPlanDao.getById(3L)?.status).isEqualTo(TransferPlanStatus.VERIFIED)
    }
}
