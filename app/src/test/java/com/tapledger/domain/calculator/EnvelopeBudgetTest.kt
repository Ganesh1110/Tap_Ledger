package com.tapledger.domain.calculator

import com.google.common.truth.Truth.assertThat
import com.tapledger.core.database.DatabaseTransactionRunner
import com.tapledger.core.database.dao.AccountDao
import com.tapledger.core.database.dao.AdvanceLedgerDao
import com.tapledger.core.database.dao.AuditLogDao
import com.tapledger.core.database.dao.BudgetAdjustmentDao
import com.tapledger.core.database.dao.BudgetAllocationDao
import com.tapledger.core.database.dao.BudgetPeriodDao
import com.tapledger.core.database.dao.CategoryDao
import com.tapledger.core.database.entity.AccountEntity
import com.tapledger.core.database.entity.AdvanceLedgerEntity
import com.tapledger.core.database.entity.BudgetAdjustmentEntity
import com.tapledger.core.database.entity.BudgetAllocationEntity
import com.tapledger.core.database.entity.BudgetPeriodEntity
import com.tapledger.core.database.entity.CategoryEntity
import com.tapledger.core.database.entity.TransactionAllocationEntity
import com.tapledger.core.database.entity.TransactionEntity
import com.tapledger.core.model.AccountType
import com.tapledger.core.model.AdvanceDirection
import com.tapledger.core.model.AdvancePolicy
import com.tapledger.core.model.BudgetBucket
import com.tapledger.core.model.BudgetPeriodStatus
import com.tapledger.core.model.TransactionDirection
import com.tapledger.core.model.TransactionKind
import com.tapledger.core.model.TransactionSource
import com.tapledger.core.model.TransactionStatus
import com.tapledger.data.repository.BudgetRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test

/**
 * Validates Flow.md §8: 50/30/20 Envelope Budgeting System
 * - §8.1: Zero-Remainder Target Math & Dynamic Actual Income
 * - §8.2: Adjusted Budget with Advance Ledger & Rule B2 Invariant Guard
 * - §8.3: Net Spending Formulation (Expenses, Refunds, Savings Contributions & Withdrawals)
 * - §8.4: Budget Evaluation Order & Savings Goal Semantics
 * - §8.5: Dashboard Hero (Needs + Wants) vs Actual Savings
 */
class EnvelopeBudgetTest {

    private class DirectTransactionRunner : DatabaseTransactionRunner {
        override suspend fun <R> run(block: suspend () -> R): R = block()
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

    private class FakeBudgetAllocationDao : BudgetAllocationDao() {
        val allocations = mutableListOf<BudgetAllocationEntity>()
        val adjustments = mutableListOf<BudgetAdjustmentEntity>()
        val advances = mutableListOf<AdvanceLedgerEntity>()
        private var nextId = 1L

        override suspend fun getAdjustmentsForPeriod(periodId: Long): List<BudgetAdjustmentEntity> =
            adjustments.filter { it.budgetPeriodId == periodId }

        override suspend fun getAdvancesForPeriod(periodId: Long): List<AdvanceLedgerEntity> =
            advances.filter { it.appliedToPeriodId == periodId }

        override suspend fun insertInternal(allocation: BudgetAllocationEntity): Long {
            val id = if (allocation.id == 0L) nextId++ else allocation.id
            allocations.add(allocation.copy(id = id))
            return id
        }

        override suspend fun insertAllInternal(allocations: List<BudgetAllocationEntity>): List<Long> =
            allocations.map { insertInternal(it) }

        override suspend fun updateInternal(allocation: BudgetAllocationEntity) {
            val idx = allocations.indexOfFirst { it.id == allocation.id }
            if (idx >= 0) allocations[idx] = allocation
        }

        override fun observeAllocationsForPeriod(periodId: Long): Flow<List<BudgetAllocationEntity>> = emptyFlow()
        override suspend fun getAllocationsForPeriodSync(periodId: Long): List<BudgetAllocationEntity> =
            allocations.filter { it.budgetPeriodId == periodId }

        override suspend fun getAllAllocationsSync(): List<BudgetAllocationEntity> = allocations
        override suspend fun getAllocation(periodId: Long, categoryId: Long): BudgetAllocationEntity? =
            allocations.firstOrNull { it.budgetPeriodId == periodId && it.categoryId == categoryId }
    }

    private class FakeBudgetAdjustmentDao(private val allocationDao: FakeBudgetAllocationDao) : BudgetAdjustmentDao {
        private var nextId = 1L

        override suspend fun insert(adjustment: BudgetAdjustmentEntity): Long {
            val id = if (adjustment.id == 0L) nextId++ else adjustment.id
            val entity = adjustment.copy(id = id)
            allocationDao.adjustments.add(entity)
            return id
        }

        override fun observeAdjustmentsForPeriod(periodId: Long): Flow<List<BudgetAdjustmentEntity>> = emptyFlow()
        override suspend fun getAdjustmentsForPeriodSync(periodId: Long): List<BudgetAdjustmentEntity> =
            allocationDao.adjustments.filter { it.budgetPeriodId == periodId }
    }

    private lateinit var categoryDao: FakeCategoryDao
    private lateinit var budgetPeriodDao: FakeBudgetPeriodDao
    private lateinit var budgetAllocationDao: FakeBudgetAllocationDao
    private lateinit var budgetAdjustmentDao: FakeBudgetAdjustmentDao
    private lateinit var budgetRepository: BudgetRepository

    private val testPeriod = BudgetPeriodEntity(
        id = 1,
        year = 2026,
        month = 10,
        startAt = 1000L,
        endAt = 2000L,
        incomePaise = 100_000_00L, // Planned income: ₹1,00,000
        status = BudgetPeriodStatus.OPEN,
        createdAt = 1000L,
        updatedAt = 1000L
    )

    @Before
    fun setUp() {
        runBlocking {
            categoryDao = FakeCategoryDao()
            budgetPeriodDao = FakeBudgetPeriodDao()
            budgetAllocationDao = FakeBudgetAllocationDao()
            budgetAdjustmentDao = FakeBudgetAdjustmentDao(budgetAllocationDao)

            budgetRepository = BudgetRepository(
                transactionRunner = DirectTransactionRunner(),
                categoryDao = categoryDao,
                budgetPeriodDao = budgetPeriodDao,
                budgetAllocationDao = budgetAllocationDao,
                budgetAdjustmentDao = budgetAdjustmentDao
            )

            budgetPeriodDao.insert(testPeriod)

            // Seed Categories
            categoryDao.insert(CategoryEntity(id = 1, name = "Groceries", defaultBucket = BudgetBucket.NEEDS, defaultSortOrder = 1))
            categoryDao.insert(CategoryEntity(id = 2, name = "Rent", defaultBucket = BudgetBucket.NEEDS, defaultSortOrder = 2))
            categoryDao.insert(CategoryEntity(id = 3, name = "Dining", defaultBucket = BudgetBucket.WANTS, defaultSortOrder = 3))
            categoryDao.insert(CategoryEntity(id = 4, name = "Emergency Fund", defaultBucket = BudgetBucket.SAVINGS, defaultSortOrder = 4))
        }
    }

    @Test
    fun testZeroRemainder503020Split() {
        // Test with prime non-divisible amount: ₹50,001.07 = 5,000,107 paise
        val plannedIncomePaise = 5_000_107L

        val targets = BudgetCalculator.calculate503020Targets(plannedIncomePaise)

        // Needs = floor(5,000,107 * 50 / 100) = 2,500,053
        assertThat(targets.needsPaise).isEqualTo(2_500_053L)
        // Wants = floor(5,000,107 * 30 / 100) = 1,500,032
        assertThat(targets.wantsPaise).isEqualTo(1_500_032L)
        // Savings = 5,000,107 - 2,500,053 - 1,500,032 = 1,000,022
        assertThat(targets.savingsPaise).isEqualTo(1_000_022L)

        // Strict Invariant (§8.1): Needs + Wants + Savings == PlannedIncome
        assertThat(targets.needsPaise + targets.wantsPaise + targets.savingsPaise).isEqualTo(plannedIncomePaise)

        // Dynamic Actual Income derivation (§8.1)
        val salaryTx = TransactionEntity(
            id = 101,
            accountId = 1,
            kind = TransactionKind.INCOME,
            direction = TransactionDirection.IN,
            amountPaise = 80_000_00L,
            occurredAt = 1200L,
            createdAt = 1200L,
            updatedAt = 1200L,
            status = TransactionStatus.CONFIRMED,
            source = TransactionSource.NOTIFICATION
        )
        val interestTx = TransactionEntity(
            id = 102,
            accountId = 1,
            kind = TransactionKind.INTEREST_EARNED,
            direction = TransactionDirection.IN,
            amountPaise = 1_500_00L,
            occurredAt = 1300L,
            createdAt = 1300L,
            updatedAt = 1300L,
            status = TransactionStatus.CATEGORIZED,
            source = TransactionSource.NOTIFICATION
        )
        val transferInTx = TransactionEntity(
            id = 103,
            accountId = 1,
            kind = TransactionKind.TRANSFER,
            direction = TransactionDirection.IN,
            amountPaise = 20_000_00L,
            occurredAt = 1400L,
            createdAt = 1400L,
            updatedAt = 1400L,
            status = TransactionStatus.CONFIRMED,
            source = TransactionSource.MANUAL
        )
        val pendingIncome = TransactionEntity(
            id = 104,
            accountId = 1,
            kind = TransactionKind.INCOME,
            direction = TransactionDirection.IN,
            amountPaise = 10_000_00L,
            occurredAt = 1500L,
            createdAt = 1500L,
            updatedAt = 1500L,
            status = TransactionStatus.PENDING,
            source = TransactionSource.NOTIFICATION
        )

        // ActualIncome strictly includes CONFIRMED/CATEGORIZED INCOME & INTEREST_EARNED (excludes transfers & pending)
        val actual = BudgetCalculator.calculateActualIncome(listOf(salaryTx, interestTx, transferInTx, pendingIncome))
        assertThat(actual).isEqualTo(81_500_00L) // 80,000 + 1,500 = 81,500
    }

    @Test
    fun testAdjustedBudgetWithAdvanceLedger() {
        // Base Allocation for Groceries (id=1): ₹10,000
        val baseAlloc = BudgetAllocationEntity(
            id = 10,
            budgetPeriodId = 1,
            categoryId = 1,
            categoryNameSnapshot = "Groceries",
            bucketSnapshot = BudgetBucket.NEEDS,
            sortOrderSnapshot = 1,
            advancePolicySnapshot = AdvancePolicy.CARRY_TO_ADVANCE,
            amountPaise = 10_000_00L,
            createdAt = 1000L,
            updatedAt = 1000L
        )

        // Budget Adjustment: Transfer IN ₹2,000 from Rent (id=2) to Groceries (id=1)
        val adjIn = BudgetAdjustmentEntity(
            id = 1,
            budgetPeriodId = 1,
            fromCategoryId = 2,
            toCategoryId = 1,
            amountPaise = 2_000_00L,
            createdAt = 1100L
        )

        // Advance Ledger entries for Groceries:
        // 1. Positive Advance: ₹1,500 (carried surplus from prior month)
        val advPos = AdvanceLedgerEntity(
            id = 1,
            sourcePeriodId = 0,
            appliedToPeriodId = 1,
            categoryId = 1,
            amountPaise = 1_500_00L,
            direction = AdvanceDirection.POSITIVE,
            createdAt = 1000L
        )
        // 2. Negative Advance: ₹3,000 (committed deficit from prior month)
        val advNeg = AdvanceLedgerEntity(
            id = 2,
            sourcePeriodId = 0,
            appliedToPeriodId = 1,
            categoryId = 1,
            amountPaise = 3_000_00L,
            direction = AdvanceDirection.NEGATIVE,
            createdAt = 1000L
        )

        val summary = BudgetCalculator.calculateBudgetPeriodSummary(
            period = testPeriod,
            allocations = listOf(baseAlloc),
            adjustments = listOf(adjIn),
            transactions = emptyList(),
            allocationsForTransactions = emptyList(),
            advances = listOf(advPos, advNeg)
        )

        // AdjustedBudget(C) = Base (10,000) + AdjIn (2,000) - AdjOut (0) + AdvPos (1,500) - AdvNeg (3,000)
        // = 10,000 + 2,000 + 1,500 - 3,000 = ₹10,500
        val catSummary = summary.categorySummaries.first()
        assertThat(catSummary.baseBudgetPaise).isEqualTo(10_000_00L)
        assertThat(catSummary.adjustedBudgetPaise).isEqualTo(10_500_00L)
    }

    @Test
    fun testSavingsWithdrawalMechanics() {
        // Emergency Fund (id=4, SAVINGS bucket): Adjusted Budget ₹20,000
        val savingsAlloc = BudgetAllocationEntity(
            id = 40,
            budgetPeriodId = 1,
            categoryId = 4,
            categoryNameSnapshot = "Emergency Fund",
            bucketSnapshot = BudgetBucket.SAVINGS,
            sortOrderSnapshot = 4,
            advancePolicySnapshot = AdvancePolicy.EXPIRE,
            amountPaise = 20_000_00L,
            createdAt = 1000L,
            updatedAt = 1000L
        )

        // Dining (id=3, WANTS bucket): Adjusted Budget ₹10,000
        val diningAlloc = BudgetAllocationEntity(
            id = 30,
            budgetPeriodId = 1,
            categoryId = 3,
            categoryNameSnapshot = "Dining",
            bucketSnapshot = BudgetBucket.WANTS,
            sortOrderSnapshot = 3,
            advancePolicySnapshot = AdvancePolicy.EXPIRE,
            amountPaise = 10_000_00L,
            createdAt = 1000L,
            updatedAt = 1000L
        )

        // 1. Savings Contribution T_sav_contrib: Transfer OUT of ₹25,000 to savings account
        val contribTx = TransactionEntity(
            id = 201,
            accountId = 1,
            kind = TransactionKind.TRANSFER,
            direction = TransactionDirection.OUT,
            amountPaise = 25_000_00L,
            occurredAt = 1200L,
            createdAt = 1200L,
            updatedAt = 1200L,
            status = TransactionStatus.CATEGORIZED,
            source = TransactionSource.MANUAL
        )
        val contribAlloc = TransactionAllocationEntity(id = 1, transactionId = 201, categoryId = 4, amountPaise = 25_000_00L)

        // 2. Savings Withdrawal T_sav_withdraw: Transfer IN of ₹5,000 from savings account
        val withdrawTx = TransactionEntity(
            id = 202,
            accountId = 1,
            kind = TransactionKind.TRANSFER,
            direction = TransactionDirection.IN,
            amountPaise = 5_000_00L,
            occurredAt = 1400L,
            createdAt = 1400L,
            updatedAt = 1400L,
            status = TransactionStatus.CATEGORIZED,
            source = TransactionSource.MANUAL
        )
        val withdrawAlloc = TransactionAllocationEntity(id = 2, transactionId = 202, categoryId = 4, amountPaise = 5_000_00L)

        // 3. Regular Dining expense of ₹11,000 (exceeds ₹10,000 budget)
        val diningTx = TransactionEntity(
            id = 203,
            accountId = 1,
            kind = TransactionKind.EXPENSE,
            direction = TransactionDirection.OUT,
            amountPaise = 11_000_00L,
            occurredAt = 1500L,
            createdAt = 1500L,
            updatedAt = 1500L,
            status = TransactionStatus.CATEGORIZED,
            source = TransactionSource.MANUAL
        )
        val diningTxAlloc = TransactionAllocationEntity(id = 3, transactionId = 203, categoryId = 3, amountPaise = 11_000_00L)

        val summary = BudgetCalculator.calculateBudgetPeriodSummary(
            period = testPeriod,
            allocations = listOf(savingsAlloc, diningAlloc),
            adjustments = emptyList(),
            transactions = listOf(contribTx, withdrawTx, diningTx),
            allocationsForTransactions = listOf(contribAlloc, withdrawAlloc, diningTxAlloc)
        )

        val savingsSummary = summary.categorySummaries.first { it.allocation.categoryId == 4L }
        val diningSummary = summary.categorySummaries.first { it.allocation.categoryId == 3L }

        // NetSpent on Savings = 25,000 (contrib) - 5,000 (withdraw) = ₹20,000
        assertThat(savingsSummary.netSpentPaise).isEqualTo(20_000_00L)
        assertThat(savingsSummary.remainingPaise).isEqualTo(0L)
        assertThat(savingsSummary.percentageUsed).isEqualTo(100)
        // Savings Goal Semantics (§8.4): isExceeded is ALWAYS false for savings; isGoalAchieved is true
        assertThat(savingsSummary.isExceeded).isFalse()
        assertThat(savingsSummary.isGoalAchieved).isTrue()

        // Dining (Wants): NetSpent = 11,000 > 10,000 -> isExceeded is TRUE
        assertThat(diningSummary.isExceeded).isTrue()
        assertThat(diningSummary.isGoalAchieved).isFalse()

        // Dashboard Hero Metrics (§8.5):
        // DashboardHeroSpend sums NEEDS + WANTS only = ₹11,000 (excludes savings ₹20,000)
        assertThat(summary.dashboardHeroSpendPaise).isEqualTo(11_000_00L)
        assertThat(summary.dashboardHeroBudgetPaise).isEqualTo(10_000_00L)
        // ActualSavings sums SAVINGS only = ₹20,000
        assertThat(summary.actualSavingsPaise).isEqualTo(20_000_00L)
    }

    @Test
    fun testInterEnvelopeTransferWithGuard() = runBlocking {
        // Rent (id=2): Base Budget ₹30,000
        val rentAlloc = BudgetAllocationEntity(
            id = 20,
            budgetPeriodId = 1,
            categoryId = 2,
            categoryNameSnapshot = "Rent",
            bucketSnapshot = BudgetBucket.NEEDS,
            sortOrderSnapshot = 2,
            advancePolicySnapshot = AdvancePolicy.CARRY_TO_ADVANCE,
            amountPaise = 30_000_00L,
            createdAt = 1000L,
            updatedAt = 1000L
        )
        // Dining (id=3): Base Budget ₹10,000
        val diningAlloc = BudgetAllocationEntity(
            id = 30,
            budgetPeriodId = 1,
            categoryId = 3,
            categoryNameSnapshot = "Dining",
            bucketSnapshot = BudgetBucket.WANTS,
            sortOrderSnapshot = 3,
            advancePolicySnapshot = AdvancePolicy.EXPIRE,
            amountPaise = 10_000_00L,
            createdAt = 1000L,
            updatedAt = 1000L
        )

        budgetAllocationDao.insertAll(listOf(rentAlloc, diningAlloc))

        // 1. Transfer ₹10,000 from Rent to Dining: Rent has ₹30,000 headroom -> succeeds
        budgetRepository.recordBudgetAdjustment(
            periodId = 1,
            fromCategoryId = 2,
            toCategoryId = 3,
            amountPaise = 10_000_00L,
            reason = "Reallocate rent surplus"
        )

        val adjustments = budgetAdjustmentDao.getAdjustmentsForPeriodSync(1)
        assertThat(adjustments).hasSize(1)

        // Rent remaining headroom is now 30,000 - 10,000 = ₹20,000
        // 2. Transferring ₹25,000 from Rent exceeds remaining headroom -> MUST FAIL with IllegalArgumentException (§8.2, Rule B2)
        val ex = assertThrows(IllegalArgumentException::class.java) {
            runBlocking {
                budgetRepository.recordBudgetAdjustment(
                    periodId = 1,
                    fromCategoryId = 2,
                    toCategoryId = 3,
                    amountPaise = 25_000_00L,
                    reason = "Overdraft transfer"
                )
            }
        }
        assertThat(ex.message).contains("Rule B2: AdjustedBudget >= 0")

        // 3. Universal Zero-Base Seeding Invariant (§8.2):
        // Emergency Fund (id=4) has no allocation row in period 1 yet.
        // Calling ensureAllocationExists automatically seeds an allocation row with amount_paise = 0L.
        val seeded = budgetRepository.ensureAllocationExists(periodId = 1, categoryId = 4)
        assertThat(seeded.amountPaise).isEqualTo(0L)
        assertThat(seeded.bucketSnapshot).isEqualTo(BudgetBucket.SAVINGS)
        assertThat(budgetAllocationDao.getAllocationsForPeriodSync(1)).hasSize(3)
    }
}
