package com.tapledger.domain.monthclose

import com.google.common.truth.Truth.assertThat
import com.tapledger.core.database.DatabaseTransactionRunner
import com.tapledger.core.database.dao.BudgetAllocationDao
import com.tapledger.core.database.dao.BudgetPeriodDao
import com.tapledger.core.database.dao.CategoryDao
import com.tapledger.core.database.entity.BudgetAllocationEntity
import com.tapledger.core.database.entity.BudgetPeriodEntity
import com.tapledger.core.database.entity.CategoryEntity
import com.tapledger.core.model.AdvancePolicy
import com.tapledger.core.model.BudgetBucket
import com.tapledger.core.model.BudgetPeriodStatus
import com.tapledger.core.util.DateTimeUtils
import com.tapledger.data.repository.BudgetRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

/**
 * Validates Flow.md §10.1 & §3.1: Multi-Period Lifecycle Architecture & Bounds
 * - Contiguous half-open intervals [start_at, end_at) in Asia/Kolkata
 * - Contiguous get-or-create initialization with auto-population of allocations from latest period
 * - is_reviewed = false initialization
 * - Bounded period creation: capped at now + 3 months
 */
class BudgetPeriodLifecycleTest {

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

        override suspend fun getAdjustmentsForPeriod(periodId: Long): List<com.tapledger.core.database.entity.BudgetAdjustmentEntity> = emptyList()
        override suspend fun getAdvancesForPeriod(periodId: Long): List<com.tapledger.core.database.entity.AdvanceLedgerEntity> = emptyList()

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
    }

    private lateinit var categoryDao: FakeCategoryDao
    private lateinit var budgetPeriodDao: FakeBudgetPeriodDao
    private lateinit var budgetAllocationDao: FakeBudgetAllocationDao
    private lateinit var budgetRepository: BudgetRepository

    @Before
    fun setUp() {
        categoryDao = FakeCategoryDao()
        budgetPeriodDao = FakeBudgetPeriodDao()
        budgetAllocationDao = FakeBudgetAllocationDao()
        budgetRepository = BudgetRepository(
            transactionRunner = DirectTransactionRunner(),
            categoryDao = categoryDao,
            budgetPeriodDao = budgetPeriodDao,
            budgetAllocationDao = budgetAllocationDao,
            budgetAdjustmentDao = object : com.tapledger.core.database.dao.BudgetAdjustmentDao {
                override suspend fun insert(adjustment: com.tapledger.core.database.entity.BudgetAdjustmentEntity): Long = 1L
                override fun observeAdjustmentsForPeriod(periodId: Long): Flow<List<com.tapledger.core.database.entity.BudgetAdjustmentEntity>> = emptyFlow()
                override suspend fun getAdjustmentsForPeriodSync(periodId: Long): List<com.tapledger.core.database.entity.BudgetAdjustmentEntity> = emptyList()
                override suspend fun getAllAdjustmentsSync(): List<com.tapledger.core.database.entity.BudgetAdjustmentEntity> = emptyList()
            }
        )
    }

    @Test
    fun testHalfOpenPeriodBoundaries() {
        // Year 2026, Month 10 in Asia/Kolkata
        val startAt = DateTimeUtils.getStartOfMonthEpochMillis(2026, 10)
        val endAt = DateTimeUtils.getEndOfMonthEpochMillis(2026, 10)

        val startZdt = DateTimeUtils.toZonedDateTime(startAt)
        assertThat(startZdt.year).isEqualTo(2026)
        assertThat(startZdt.monthValue).isEqualTo(10)
        assertThat(startZdt.dayOfMonth).isEqualTo(1)
        assertThat(startZdt.hour).isEqualTo(0)
        assertThat(startZdt.minute).isEqualTo(0)
        assertThat(startZdt.second).isEqualTo(0)

        // Upper bound strictly starts at next month 1st 00:00:00 (half-open [start_at, end_at))
        val endZdt = DateTimeUtils.toZonedDateTime(endAt)
        assertThat(endZdt.year).isEqualTo(2026)
        assertThat(endZdt.monthValue).isEqualTo(11)
        assertThat(endZdt.dayOfMonth).isEqualTo(1)
        assertThat(endZdt.hour).isEqualTo(0)
        assertThat(endZdt.minute).isEqualTo(0)
        assertThat(endZdt.second).isEqualTo(0)

        // Exactly last millisecond of October (23:59:59.999) falls inside [start_at, end_at)
        val lastMilliOctober = endAt - 1L
        assertThat(lastMilliOctober >= startAt && lastMilliOctober < endAt).isTrue()

        // 00:00:00.000 of November does NOT fall inside October
        assertThat(endAt >= startAt && endAt < endAt).isFalse()
    }

    @Test
    fun testContiguousPeriodAutoPopulation() = runBlocking {
        // Seed category template
        categoryDao.insert(CategoryEntity(id = 1, name = "Groceries", defaultBucket = BudgetBucket.NEEDS, defaultSortOrder = 1))
        categoryDao.insert(CategoryEntity(id = 2, name = "Dining", defaultBucket = BudgetBucket.WANTS, defaultSortOrder = 2))

        val nowYm = DateTimeUtils.toYearMonth(DateTimeUtils.nowEpochMillis())
        val p1 = budgetRepository.getOrCreateBudgetPeriod(nowYm.year, nowYm.monthValue, defaultIncomePaise = 80_000_00L)
        val a1 = budgetAllocationDao.getAllocation(p1.id, 1)!!
        budgetAllocationDao.update(a1.copy(amountPaise = 30_000_00L))
        val a2 = budgetAllocationDao.getAllocation(p1.id, 2)!!
        budgetAllocationDao.update(a2.copy(amountPaise = 20_000_00L))

        // Now jump 2 months ahead (e.g. now + 2 months)
        val targetYm = nowYm.plusMonths(2)
        val p3 = budgetRepository.getOrCreateBudgetPeriod(targetYm.year, targetYm.monthValue)

        // Intermediate period must have been created contiguously
        val intermediateYm = nowYm.plusMonths(1)
        val p2 = budgetPeriodDao.getByYearMonth(intermediateYm.year, intermediateYm.monthValue)
        assertThat(p2).isNotNull()

        // Both p2 and p3 inherited incomePaise from p1
        assertThat(p2!!.incomePaise).isEqualTo(80_000_00L)
        assertThat(p3.incomePaise).isEqualTo(80_000_00L)

        // Both p2 and p3 initialized with isReviewed = false (Flow.md §10.1)
        assertThat(p2.isReviewed).isFalse()
        assertThat(p3.isReviewed).isFalse()

        // Allocations automatically copied from latest existing period
        val p3Allocs = budgetAllocationDao.getAllocationsForPeriodSync(p3.id)
        val grocAlloc = p3Allocs.first { it.categoryId == 1L }
        assertThat(grocAlloc.amountPaise).isEqualTo(30_000_00L)
    }

    @Test
    fun testBoundedPeriodCreationCap() {
        val nowYm = DateTimeUtils.toYearMonth(DateTimeUtils.nowEpochMillis())

        // 3 months ahead is allowed
        runBlocking {
            val allowedYm = nowYm.plusMonths(3)
            val allowedPeriod = budgetRepository.getOrCreateBudgetPeriod(allowedYm.year, allowedYm.monthValue)
            assertThat(allowedPeriod).isNotNull()
        }

        // 4 months ahead must be rejected (§3.1, §10.1)
        val forbiddenYm = nowYm.plusMonths(4)
        val ex = assertThrows(IllegalArgumentException::class.java) {
            runBlocking {
                budgetRepository.getOrCreateBudgetPeriod(forbiddenYm.year, forbiddenYm.monthValue)
            }
        }
        assertThat(ex.message).contains("more than 3 months in the future")
    }

    @Test
    fun testUpdateCategoryUpdatesBucketSnapshotInOpenPeriods() = runBlocking {
        val nowYm = DateTimeUtils.toYearMonth(DateTimeUtils.nowEpochMillis())
        val period = budgetRepository.getOrCreateBudgetPeriod(nowYm.year, nowYm.monthValue)

        val catId = budgetRepository.createCategory(
            name = "Post Office",
            defaultBucket = BudgetBucket.NEEDS,
            defaultAdvancePolicy = AdvancePolicy.CARRY_TO_ADVANCE,
            initialAllocationPaise = 5_000_00L,
            periodId = period.id
        )

        // Verify initial snapshot is NEEDS
        val initialAlloc = budgetAllocationDao.getAllocation(period.id, catId)
        assertThat(initialAlloc).isNotNull()
        assertThat(initialAlloc!!.bucketSnapshot).isEqualTo(BudgetBucket.NEEDS)

        // User updates category to WANTS
        val category = categoryDao.getById(catId)!!
        val updatedCategory = category.copy(defaultBucket = BudgetBucket.WANTS)
        budgetRepository.updateCategory(
            category = updatedCategory,
            periodId = period.id,
            newAmountPaise = 5_000_00L
        )

        // Verify snapshot in open period is updated to WANTS
        val updatedAlloc = budgetAllocationDao.getAllocation(period.id, catId)
        assertThat(updatedAlloc).isNotNull()
        assertThat(updatedAlloc!!.bucketSnapshot).isEqualTo(BudgetBucket.WANTS)
    }
}
