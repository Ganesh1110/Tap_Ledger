package com.tapledger.ui.screens.categories

import com.google.common.truth.Truth.assertThat
import com.tapledger.core.database.entity.BudgetAllocationEntity
import com.tapledger.core.database.entity.BudgetPeriodEntity
import com.tapledger.core.database.entity.CategoryEntity
import com.tapledger.core.database.entity.TransactionAllocationEntity
import com.tapledger.core.database.entity.TransactionEntity
import com.tapledger.core.model.AdvancePolicy
import com.tapledger.core.model.BudgetBucket
import com.tapledger.core.model.BudgetPeriodStatus
import com.tapledger.core.model.TransactionDirection
import com.tapledger.core.model.TransactionKind
import com.tapledger.core.model.TransactionSource
import com.tapledger.core.model.TransactionStatus
import com.tapledger.domain.calculator.BudgetCalculator
import com.tapledger.domain.model.BudgetPeriodSummary
import com.tapledger.ui.charts.builder.BudgetChartBuilder
import com.tapledger.ui.charts.model.ChartState
import org.junit.Test

/**
 * Named test suite proving V5 (Envelope Bullet Bars) and V6 (Category Donut) requirements
 * on the Budget / Category Management screen per Flow.md §17.2 and §17.7.
 */
class BudgetVisualsTest {

    private val startAt = 1_700_000_000_000L
    private val endAt = startAt + 30L * 86_400_000L

    private val openPeriod = BudgetPeriodEntity(
        id = 1L,
        year = 2026,
        month = 10,
        startAt = startAt,
        endAt = endAt,
        incomePaise = 100_000_00L,
        status = BudgetPeriodStatus.OPEN,
        isReviewed = true,
        createdAt = startAt,
        updatedAt = startAt
    )

    private val catGroceries = CategoryEntity(id = 1L, name = "Groceries", defaultBucket = BudgetBucket.NEEDS, defaultAdvancePolicy = AdvancePolicy.CARRY_TO_ADVANCE, defaultSortOrder = 1, createdAt = startAt, updatedAt = startAt)
    private val catRent = CategoryEntity(id = 2L, name = "Rent", defaultBucket = BudgetBucket.NEEDS, defaultAdvancePolicy = AdvancePolicy.EXPIRE, defaultSortOrder = 2, createdAt = startAt, updatedAt = startAt)
    private val catDining = CategoryEntity(id = 3L, name = "Dining Out", defaultBucket = BudgetBucket.WANTS, defaultAdvancePolicy = AdvancePolicy.EXPIRE, defaultSortOrder = 3, createdAt = startAt, updatedAt = startAt)
    private val catMovies = CategoryEntity(id = 4L, name = "Movies", defaultBucket = BudgetBucket.WANTS, defaultAdvancePolicy = AdvancePolicy.EXPIRE, defaultSortOrder = 4, createdAt = startAt, updatedAt = startAt)
    private val catEmergency = CategoryEntity(id = 5L, name = "Emergency Fund", defaultBucket = BudgetBucket.SAVINGS, defaultAdvancePolicy = AdvancePolicy.SWEEP_TO_SAVINGS, defaultSortOrder = 5, createdAt = startAt, updatedAt = startAt)

    private val categoryNames = mapOf(
        1L to "Groceries",
        2L to "Rent",
        3L to "Dining Out",
        4L to "Movies",
        5L to "Emergency Fund"
    )

    private fun createSummary(
        allocations: List<BudgetAllocationEntity>,
        transactions: List<TransactionEntity>,
        transAllocs: List<TransactionAllocationEntity>
    ): BudgetPeriodSummary {
        return BudgetCalculator.calculateBudgetPeriodSummary(
            period = openPeriod,
            allocations = allocations,
            adjustments = emptyList(),
            transactions = transactions,
            allocationsForTransactions = transAllocs
        )
    }

    // =========================================================================
    // V5: Envelope Bullet Bars (S_Budget)
    // =========================================================================

    @Test
    fun testV5_BulletBars_RendersSpentVsAdjustedBudgetWithCarryAndDeficit() {
        val allocs = listOf(
            BudgetAllocationEntity(id = 1L, budgetPeriodId = 1L, categoryId = 1L, amountPaise = 20_000_00L, bucketSnapshot = BudgetBucket.NEEDS, advancePolicySnapshot = AdvancePolicy.CARRY_TO_ADVANCE, categoryNameSnapshot = "Groceries", sortOrderSnapshot = 1, createdAt = startAt, updatedAt = startAt),
            BudgetAllocationEntity(id = 2L, budgetPeriodId = 1L, categoryId = 3L, amountPaise = 15_000_00L, bucketSnapshot = BudgetBucket.WANTS, advancePolicySnapshot = AdvancePolicy.EXPIRE, categoryNameSnapshot = "Dining Out", sortOrderSnapshot = 2, createdAt = startAt, updatedAt = startAt)
        )
        val tx1 = TransactionEntity(id = 1L, accountId = 1L, kind = TransactionKind.EXPENSE, direction = TransactionDirection.OUT, amountPaise = 12_000_00L, status = TransactionStatus.CATEGORIZED, source = TransactionSource.MANUAL, occurredAt = startAt + 1000L, createdAt = startAt, updatedAt = startAt)
        val ta1 = TransactionAllocationEntity(id = 1L, transactionId = 1L, categoryId = 1L, amountPaise = 12_000_00L)
        val summary = createSummary(allocs, listOf(tx1), listOf(ta1))

        // AdvPos (carried-in) on Groceries = ₹2,000; AdvNeg (applied deficit) on Dining Out = ₹1,000
        val advPosMap = mapOf(1L to 2_000_00L)
        val advNegMap = mapOf(3L to 1_000_00L)

        val result = BudgetChartBuilder.buildEnvelopeBulletBars(summary, categoryNames, advPosMap, advNegMap)
        assertThat(result).isInstanceOf(ChartState.Data::class.java)
        val data = (result as ChartState.Data).data

        val groceriesItem = data.envelopes.first { it.categoryId == 1L }
        assertThat(groceriesItem.categoryName).isEqualTo("Groceries")
        assertThat(groceriesItem.spentPaise).isEqualTo(12_000_00L)
        assertThat(groceriesItem.baseAllocationPaise).isEqualTo(20_000_00L)
        assertThat(groceriesItem.advPosPaise).isEqualTo(2_000_00L)
        assertThat(groceriesItem.advNegPaise).isEqualTo(0L)
        // 12,000 / 20,000 = 6000 bp (60.00%)
        assertThat(groceriesItem.percentageBp).isEqualTo(6000)
        assertThat(groceriesItem.isExceeded).isFalse()

        val diningItem = data.envelopes.first { it.categoryId == 3L }
        assertThat(diningItem.categoryName).isEqualTo("Dining Out")
        assertThat(diningItem.spentPaise).isEqualTo(0L)
        assertThat(diningItem.advPosPaise).isEqualTo(0L)
        assertThat(diningItem.advNegPaise).isEqualTo(1_000_00L)
    }

    @Test
    fun testV5_BulletBars_D8OverflowWhenPercentageExceeds100() {
        val allocs = listOf(
            BudgetAllocationEntity(id = 1L, budgetPeriodId = 1L, categoryId = 1L, amountPaise = 10_000_00L, bucketSnapshot = BudgetBucket.NEEDS, advancePolicySnapshot = AdvancePolicy.EXPIRE, categoryNameSnapshot = "Groceries", sortOrderSnapshot = 1, createdAt = startAt, updatedAt = startAt)
        )
        // Spent ₹15,000 on a ₹10,000 budget (150%)
        val tx1 = TransactionEntity(id = 1L, accountId = 1L, kind = TransactionKind.EXPENSE, direction = TransactionDirection.OUT, amountPaise = 15_000_00L, status = TransactionStatus.CATEGORIZED, source = TransactionSource.MANUAL, occurredAt = startAt + 1000L, createdAt = startAt, updatedAt = startAt)
        val ta1 = TransactionAllocationEntity(id = 1L, transactionId = 1L, categoryId = 1L, amountPaise = 15_000_00L)
        val summary = createSummary(allocs, listOf(tx1), listOf(ta1))

        val result = BudgetChartBuilder.buildEnvelopeBulletBars(summary, categoryNames)
        assertThat(result).isInstanceOf(ChartState.Data::class.java)
        val item = (result as ChartState.Data).data.envelopes.first { it.categoryId == 1L }

        assertThat(item.spentPaise).isEqualTo(15_000_00L)
        assertThat(item.adjustedBudgetPaise).isEqualTo(10_000_00L)
        assertThat(item.percentageBp).isEqualTo(15000)
        assertThat(item.isExceeded).isTrue()
    }

    @Test
    fun testV5_BulletBars_SavingsGoalStylingNeverExceeded() {
        val allocs = listOf(
            BudgetAllocationEntity(id = 1L, budgetPeriodId = 1L, categoryId = 5L, amountPaise = 10_000_00L, bucketSnapshot = BudgetBucket.SAVINGS, advancePolicySnapshot = AdvancePolicy.SWEEP_TO_SAVINGS, categoryNameSnapshot = "Emergency Fund", sortOrderSnapshot = 1, createdAt = startAt, updatedAt = startAt)
        )
        // Saved ₹12,000 on ₹10,000 goal
        val tx1 = TransactionEntity(id = 1L, accountId = 1L, kind = TransactionKind.EXPENSE, direction = TransactionDirection.OUT, amountPaise = 12_000_00L, status = TransactionStatus.CATEGORIZED, source = TransactionSource.MANUAL, occurredAt = startAt + 1000L, createdAt = startAt, updatedAt = startAt)
        val ta1 = TransactionAllocationEntity(id = 1L, transactionId = 1L, categoryId = 5L, amountPaise = 12_000_00L)
        val summary = createSummary(allocs, listOf(tx1), listOf(ta1))

        val result = BudgetChartBuilder.buildEnvelopeBulletBars(summary, categoryNames)
        assertThat(result).isInstanceOf(ChartState.Data::class.java)
        val item = (result as ChartState.Data).data.envelopes.first { it.categoryId == 5L }

        assertThat(item.bucket).isEqualTo(BudgetBucket.SAVINGS)
        // Savings goals are NEVER marked exceeded (Flow.md §8.4 & §17.2 V5)
        assertThat(item.isExceeded).isFalse()
        assertThat(item.isGoalAchieved).isTrue()
    }

    // =========================================================================
    // V6: Category Donut (S_Budget toggle)
    // =========================================================================

    @Test
    fun testV6_CategoryDonut_D2ApportionmentSumsExactlyTo10000Bp() {
        val allocs = listOf(
            BudgetAllocationEntity(id = 1L, budgetPeriodId = 1L, categoryId = 1L, amountPaise = 50_000_00L, bucketSnapshot = BudgetBucket.NEEDS, advancePolicySnapshot = AdvancePolicy.EXPIRE, categoryNameSnapshot = "Groceries", sortOrderSnapshot = 1, createdAt = startAt, updatedAt = startAt),
            BudgetAllocationEntity(id = 2L, budgetPeriodId = 1L, categoryId = 2L, amountPaise = 50_000_00L, bucketSnapshot = BudgetBucket.NEEDS, advancePolicySnapshot = AdvancePolicy.EXPIRE, categoryNameSnapshot = "Rent", sortOrderSnapshot = 2, createdAt = startAt, updatedAt = startAt),
            BudgetAllocationEntity(id = 3L, budgetPeriodId = 1L, categoryId = 3L, amountPaise = 50_000_00L, bucketSnapshot = BudgetBucket.WANTS, advancePolicySnapshot = AdvancePolicy.EXPIRE, categoryNameSnapshot = "Dining Out", sortOrderSnapshot = 3, createdAt = startAt, updatedAt = startAt)
        )
        // Three categories spending 100, 100, 100 (each 1/3 = 33.33%)
        val txs = listOf(
            TransactionEntity(id = 1L, accountId = 1L, kind = TransactionKind.EXPENSE, direction = TransactionDirection.OUT, amountPaise = 100_00L, status = TransactionStatus.CATEGORIZED, source = TransactionSource.MANUAL, occurredAt = startAt + 1000L, createdAt = startAt, updatedAt = startAt),
            TransactionEntity(id = 2L, accountId = 1L, kind = TransactionKind.EXPENSE, direction = TransactionDirection.OUT, amountPaise = 100_00L, status = TransactionStatus.CATEGORIZED, source = TransactionSource.MANUAL, occurredAt = startAt + 2000L, createdAt = startAt, updatedAt = startAt),
            TransactionEntity(id = 3L, accountId = 1L, kind = TransactionKind.EXPENSE, direction = TransactionDirection.OUT, amountPaise = 100_00L, status = TransactionStatus.CATEGORIZED, source = TransactionSource.MANUAL, occurredAt = startAt + 3000L, createdAt = startAt, updatedAt = startAt)
        )
        val tas = listOf(
            TransactionAllocationEntity(id = 1L, transactionId = 1L, categoryId = 1L, amountPaise = 100_00L),
            TransactionAllocationEntity(id = 2L, transactionId = 2L, categoryId = 2L, amountPaise = 100_00L),
            TransactionAllocationEntity(id = 3L, transactionId = 3L, categoryId = 3L, amountPaise = 100_00L)
        )
        val summary = createSummary(allocs, txs, tas)

        val result = BudgetChartBuilder.buildCategoryDonut(summary, categoryNames)
        assertThat(result).isInstanceOf(ChartState.Data::class.java)
        val data = (result as ChartState.Data).data

        assertThat(data.slices).hasSize(3)
        val totalBp = data.slices.sumOf { it.percentageBp }
        // D2 invariant: largest remainder ensures sum is exactly 10,000 bp
        assertThat(totalBp).isEqualTo(10_000)
    }

    @Test
    fun testV6_CategoryDonut_Top6PlusOtherGrouping() {
        val allocs = (1L..8L).map { id ->
            BudgetAllocationEntity(id = id, budgetPeriodId = 1L, categoryId = id, amountPaise = 100_000_00L, bucketSnapshot = BudgetBucket.NEEDS, advancePolicySnapshot = AdvancePolicy.EXPIRE, categoryNameSnapshot = "Cat $id", sortOrderSnapshot = id.toInt(), createdAt = startAt, updatedAt = startAt)
        }
        val names = (1L..8L).associateWith { "Cat $it" }

        // 8 categories with descending spending: 800, 700, 600, 500, 400, 300, 200, 100
        val txs = (1L..8L).map { id ->
            TransactionEntity(id = id, accountId = 1L, kind = TransactionKind.EXPENSE, direction = TransactionDirection.OUT, amountPaise = (9L - id) * 100_00L, status = TransactionStatus.CATEGORIZED, source = TransactionSource.MANUAL, occurredAt = startAt + id * 1000L, createdAt = startAt, updatedAt = startAt)
        }
        val tas = (1L..8L).map { id ->
            TransactionAllocationEntity(id = id, transactionId = id, categoryId = id, amountPaise = (9L - id) * 100_00L)
        }
        val summary = createSummary(allocs, txs, tas)

        val result = BudgetChartBuilder.buildCategoryDonut(summary, names)
        assertThat(result).isInstanceOf(ChartState.Data::class.java)
        val data = (result as ChartState.Data).data

        // Top 6 + 1 "Other" = 7 slices
        assertThat(data.slices).hasSize(7)
        assertThat(data.slices.last().categoryName).isEqualTo("Other")
        assertThat(data.slices.last().categoryId).isEqualTo(-1L)
        val otherSpent = data.slices.last().amountPaise
        // Other should be the sum of 7th (200) and 8th (100) = 300
        assertThat(otherSpent).isEqualTo(300_00L)
        assertThat(data.slices.sumOf { it.percentageBp }).isEqualTo(10_000)
    }

    @Test
    fun testV6_CategoryDonut_ExcludesRefundHeavyNetNegativeCategories() {
        val allocs = listOf(
            BudgetAllocationEntity(id = 1L, budgetPeriodId = 1L, categoryId = 1L, amountPaise = 50_000_00L, bucketSnapshot = BudgetBucket.NEEDS, advancePolicySnapshot = AdvancePolicy.EXPIRE, categoryNameSnapshot = "Groceries", sortOrderSnapshot = 1, createdAt = startAt, updatedAt = startAt),
            BudgetAllocationEntity(id = 2L, budgetPeriodId = 1L, categoryId = 3L, amountPaise = 50_000_00L, bucketSnapshot = BudgetBucket.WANTS, advancePolicySnapshot = AdvancePolicy.EXPIRE, categoryNameSnapshot = "Dining Out", sortOrderSnapshot = 2, createdAt = startAt, updatedAt = startAt)
        )
        // Groceries spent ₹500. Dining Out has refund ₹200 (IN direction) with net-negative spent (-₹200).
        val tx1 = TransactionEntity(id = 1L, accountId = 1L, kind = TransactionKind.EXPENSE, direction = TransactionDirection.OUT, amountPaise = 500_00L, status = TransactionStatus.CATEGORIZED, source = TransactionSource.MANUAL, occurredAt = startAt + 1000L, createdAt = startAt, updatedAt = startAt)
        val ta1 = TransactionAllocationEntity(id = 1L, transactionId = 1L, categoryId = 1L, amountPaise = 500_00L)

        val tx2 = TransactionEntity(id = 2L, accountId = 1L, kind = TransactionKind.REFUND, direction = TransactionDirection.IN, amountPaise = 200_00L, status = TransactionStatus.CATEGORIZED, source = TransactionSource.MANUAL, occurredAt = startAt + 2000L, createdAt = startAt, updatedAt = startAt)
        val ta2 = TransactionAllocationEntity(id = 2L, transactionId = 2L, categoryId = 3L, amountPaise = 200_00L)

        val summary = createSummary(allocs, listOf(tx1, tx2), listOf(ta1, ta2))

        val result = BudgetChartBuilder.buildCategoryDonut(summary, categoryNames)
        assertThat(result).isInstanceOf(ChartState.Data::class.java)
        val data = (result as ChartState.Data).data

        // Dining Out has net-negative spent (-₹200), so it must be EXCLUDED from slices
        assertThat(data.slices).hasSize(1)
        assertThat(data.slices.first().categoryName).isEqualTo("Groceries")
        assertThat(data.slices.first().percentageBp).isEqualTo(10_000)

        // Dining Out must appear in refundCategories as Net refund
        assertThat(data.refundCategories).hasSize(1)
        val refund = data.refundCategories.first()
        assertThat(refund.categoryName).isEqualTo("Dining Out")
        assertThat(refund.netRefundPaise).isEqualTo(200_00L)
    }

    @Test
    fun testV6_CategoryDonut_EmptyStateWhenZeroSpending() {
        val allocs = listOf(
            BudgetAllocationEntity(id = 1L, budgetPeriodId = 1L, categoryId = 1L, amountPaise = 50_000_00L, bucketSnapshot = BudgetBucket.NEEDS, advancePolicySnapshot = AdvancePolicy.EXPIRE, categoryNameSnapshot = "Groceries", sortOrderSnapshot = 1, createdAt = startAt, updatedAt = startAt)
        )
        val summary = createSummary(allocs, emptyList(), emptyList())

        val result = BudgetChartBuilder.buildCategoryDonut(summary, categoryNames)
        assertThat(result).isInstanceOf(ChartState.Empty::class.java)
    }
}
