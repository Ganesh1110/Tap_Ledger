package com.tapledger.ui.screens.dashboard

import com.google.common.truth.Truth.assertThat
import com.tapledger.core.database.dao.BudgetPeriodDao
import com.tapledger.core.database.dao.MonthCloseDao
import com.tapledger.core.database.dao.TransactionAllocationDao
import com.tapledger.core.database.dao.TransactionDao
import com.tapledger.core.database.entity.BudgetAllocationEntity
import com.tapledger.core.database.entity.BudgetPeriodEntity
import com.tapledger.core.database.entity.CategoryEntity
import com.tapledger.core.database.entity.MonthCloseEntity
import com.tapledger.core.database.entity.TransactionAllocationEntity
import com.tapledger.core.database.entity.TransactionEntity
import com.tapledger.core.model.AdvancePolicy
import com.tapledger.core.model.BudgetBucket
import com.tapledger.core.model.BudgetPeriodStatus
import com.tapledger.core.model.MonthCloseReconciliationStatus
import com.tapledger.core.model.TransactionDirection
import com.tapledger.core.model.TransactionKind
import com.tapledger.core.model.TransactionSource
import com.tapledger.core.model.TransactionStatus
import com.tapledger.domain.calculator.BudgetCalculator
import com.tapledger.domain.model.BudgetPeriodSummary
import com.tapledger.ui.charts.builder.DashboardChartBuilder
import com.tapledger.ui.charts.model.ChartState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import org.junit.Test

/**
 * Named test suite proving V1, V2, V3, and V4 visual requirements on the Dashboard screen
 * per Flow.md §17.2 and IMPLEMENTATION_AUDIT.md.
 */
class DashboardVisualsTest {

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

    private val catNeeds = CategoryEntity(id = 10L, name = "Groceries", defaultBucket = BudgetBucket.NEEDS, defaultAdvancePolicy = AdvancePolicy.EXPIRE, defaultSortOrder = 1, createdAt = startAt, updatedAt = startAt)
    private val catWants = CategoryEntity(id = 20L, name = "Entertainment", defaultBucket = BudgetBucket.WANTS, defaultAdvancePolicy = AdvancePolicy.EXPIRE, defaultSortOrder = 2, createdAt = startAt, updatedAt = startAt)
    private val catSavings = CategoryEntity(id = 30L, name = "Mutual Funds", defaultBucket = BudgetBucket.SAVINGS, defaultAdvancePolicy = AdvancePolicy.CARRY_TO_ADVANCE, defaultSortOrder = 3, createdAt = startAt, updatedAt = startAt)

    private val allocNeeds = BudgetAllocationEntity(id = 1L, budgetPeriodId = 1L, categoryId = 10L, amountPaise = 50_000_00L, bucketSnapshot = BudgetBucket.NEEDS, advancePolicySnapshot = AdvancePolicy.EXPIRE, categoryNameSnapshot = "Groceries", sortOrderSnapshot = 1, createdAt = startAt, updatedAt = startAt)
    private val allocWants = BudgetAllocationEntity(id = 2L, budgetPeriodId = 1L, categoryId = 20L, amountPaise = 30_000_00L, bucketSnapshot = BudgetBucket.WANTS, advancePolicySnapshot = AdvancePolicy.EXPIRE, categoryNameSnapshot = "Entertainment", sortOrderSnapshot = 2, createdAt = startAt, updatedAt = startAt)
    private val allocSavings = BudgetAllocationEntity(id = 3L, budgetPeriodId = 1L, categoryId = 30L, amountPaise = 20_000_00L, bucketSnapshot = BudgetBucket.SAVINGS, advancePolicySnapshot = AdvancePolicy.CARRY_TO_ADVANCE, categoryNameSnapshot = "Mutual Funds", sortOrderSnapshot = 3, createdAt = startAt, updatedAt = startAt)

    private fun createSummary(
        txs: List<TransactionEntity>,
        allocs: List<TransactionAllocationEntity>
    ): BudgetPeriodSummary {
        return BudgetCalculator.calculateBudgetPeriodSummary(
            period = openPeriod,
            allocations = listOf(allocNeeds, allocWants, allocSavings),
            adjustments = emptyList(),
            transactions = txs,
            allocationsForTransactions = allocs
        )
    }

    // --- V1: Dashboard Hero Ring ---
    @Test
    fun testV1_HeroRing_CalculatesPaceWhenOpenAndOmitsWhenClosed() {
        val tx1 = TransactionEntity(id = 1L, accountId = 1L, kind = TransactionKind.EXPENSE, direction = TransactionDirection.OUT, amountPaise = 20_000_00L, status = TransactionStatus.CATEGORIZED, source = TransactionSource.MANUAL, occurredAt = startAt + 1000L, createdAt = startAt, updatedAt = startAt)
        val ta1 = TransactionAllocationEntity(id = 1L, transactionId = 1L, categoryId = 10L, amountPaise = 20_000_00L)
        val summary = createSummary(listOf(tx1), listOf(ta1))

        // Open period halfway through (day 15)
        val halfwayNow = startAt + 15L * 86_400_000L
        val heroOpen = (DashboardChartBuilder.buildHeroRing(summary, isPeriodClosed = false, nowIST = halfwayNow) as ChartState.Data).data

        assertThat(heroOpen.spentPaise).isEqualTo(20_000_00L)
        assertThat(heroOpen.budgetPaise).isEqualTo(80_000_00L) // Needs (50k) + Wants (30k)
        assertThat(heroOpen.remainingPaise).isEqualTo(60_000_00L)
        assertThat(heroOpen.paceBp).isNotNull()
        assertThat(heroOpen.isExceeded).isFalse()
        assertThat(heroOpen.contentDescription).contains("Budget spent")

        // Closed period omits pace tick
        val heroClosed = (DashboardChartBuilder.buildHeroRing(summary, isPeriodClosed = true, nowIST = endAt) as ChartState.Data).data
        assertThat(heroClosed.paceBp).isNull()
    }

    // --- V2: Burn-Up Line ---
    @Test
    fun testV2_BurnUpLine_LastCumulativeEqualsHeroSpend_AndProjectionRequirements() {
        val tx1 = TransactionEntity(id = 1L, accountId = 1L, kind = TransactionKind.EXPENSE, direction = TransactionDirection.OUT, amountPaise = 10_000_00L, status = TransactionStatus.CATEGORIZED, source = TransactionSource.MANUAL, occurredAt = startAt + 86_400_000L, createdAt = startAt, updatedAt = startAt)
        val ta1 = TransactionAllocationEntity(id = 1L, transactionId = 1L, categoryId = 10L, amountPaise = 10_000_00L)

        val tx2 = TransactionEntity(id = 2L, accountId = 1L, kind = TransactionKind.EXPENSE, direction = TransactionDirection.OUT, amountPaise = 5_000_00L, status = TransactionStatus.CATEGORIZED, source = TransactionSource.MANUAL, occurredAt = startAt + 6L * 86_400_000L, createdAt = startAt, updatedAt = startAt)
        val ta2 = TransactionAllocationEntity(id = 2L, transactionId = 2L, categoryId = 20L, amountPaise = 5_000_00L)

        val summary = createSummary(listOf(tx1, tx2), listOf(ta1, ta2))

        // Day 7 (elapsedDays = 7 >= 5 -> Projection must be present per D4)
        val nowDay7 = startAt + 6L * 86_400_000L + 1000L
        val burnUpState = DashboardChartBuilder.buildBurnUpChart(
            summary = summary,
            period = openPeriod,
            transactions = listOf(tx1, tx2),
            allocations = listOf(ta1, ta2),
            nowIST = nowDay7
        )
        val burnUp = (burnUpState as ChartState.Data).data

        // Invariant: last cumulative point == DashboardHeroSpend
        assertThat(burnUp.lastCumulativePaise).isEqualTo(summary.dashboardHeroSpendPaise)
        assertThat(burnUp.lastCumulativePaise).isEqualTo(15_000_00L)

        // Projection present because elapsedDays >= 5
        assertThat(burnUp.projectionPaise).isNotNull()
        assertThat(burnUp.tableRows).isNotEmpty()

        // Day 2 (elapsedDays = 2 < 5 -> Projection MUST be null per D4)
        val nowDay2 = startAt + 1L * 86_400_000L + 1000L
        val burnUpEarly = (DashboardChartBuilder.buildBurnUpChart(
            summary = summary,
            period = openPeriod,
            transactions = listOf(tx1),
            allocations = listOf(ta1),
            nowIST = nowDay2
        ) as ChartState.Data).data

        assertThat(burnUpEarly.projectionPaise).isNull()
    }

    // --- V3: 50/30/20 Bucket Rings ---
    @Test
    fun testV3_BucketRings_CalculatesAllThreeBuckets_SavingsGoalAchieved() {
        val txNeeds = TransactionEntity(id = 1L, accountId = 1L, kind = TransactionKind.EXPENSE, direction = TransactionDirection.OUT, amountPaise = 25_000_00L, status = TransactionStatus.CATEGORIZED, source = TransactionSource.MANUAL, occurredAt = startAt, createdAt = startAt, updatedAt = startAt)
        val taNeeds = TransactionAllocationEntity(id = 1L, transactionId = 1L, categoryId = 10L, amountPaise = 25_000_00L)

        val txWants = TransactionEntity(id = 2L, accountId = 1L, kind = TransactionKind.EXPENSE, direction = TransactionDirection.OUT, amountPaise = 15_000_00L, status = TransactionStatus.CATEGORIZED, source = TransactionSource.MANUAL, occurredAt = startAt, createdAt = startAt, updatedAt = startAt)
        val taWants = TransactionAllocationEntity(id = 2L, transactionId = 2L, categoryId = 20L, amountPaise = 15_000_00L)

        val txSavings = TransactionEntity(id = 3L, accountId = 1L, kind = TransactionKind.TRANSFER, direction = TransactionDirection.OUT, amountPaise = 20_000_00L, status = TransactionStatus.CATEGORIZED, source = TransactionSource.MANUAL, occurredAt = startAt, createdAt = startAt, updatedAt = startAt)
        val taSavings = TransactionAllocationEntity(id = 3L, transactionId = 3L, categoryId = 30L, amountPaise = 20_000_00L)

        val summary = createSummary(listOf(txNeeds, txWants, txSavings), listOf(taNeeds, taWants, taSavings))
        val rings = (DashboardChartBuilder.buildBucketRings(summary) as ChartState.Data).data

        // Needs: 25k of 50k (50%)
        assertThat(rings.needs.spentPaise).isEqualTo(25_000_00L)
        assertThat(rings.needs.budgetPaise).isEqualTo(50_000_00L)
        assertThat(rings.needs.percentageBp).isEqualTo(5_000)

        // Wants: 15k of 30k (50%)
        assertThat(rings.wants.spentPaise).isEqualTo(15_000_00L)
        assertThat(rings.wants.budgetPaise).isEqualTo(30_000_00L)
        assertThat(rings.wants.percentageBp).isEqualTo(5_000)

        // Savings: 20k of 20k (100% -> goal achieved!)
        assertThat(rings.savings.spentPaise).isEqualTo(20_000_00L)
        assertThat(rings.savings.isGoalAchieved).isTrue()
        assertThat(rings.savings.isExceeded).isFalse() // Never exceeded
    }

    // --- V4: Six-Period Trend ---
    @Test
    fun testV4_SixPeriodTrend_PresentsClosedHistoricalAndOpenInProgress() {
        val historicalClose = MonthCloseEntity(
            id = 1L,
            budgetPeriodId = 10L,
            closedAt = startAt - 1000L,
            plannedIncomePaise = 50_000_00L,
            actualIncomePaise = 55_000_00L,
            totalBudgetedPaise = 50_000_00L,
            totalSpentPaise = 40_000_00L,
            totalSavedPaise = 15_000_00L,
            reconciliationStatus = MonthCloseReconciliationStatus.RECONCILED,
            closingBalancesJson = "{}"
        )

        val pastPeriod = BudgetPeriodEntity(
            id = 10L,
            year = 2026,
            month = 9,
            startAt = startAt - 30L * 86_400_000L,
            endAt = startAt,
            incomePaise = 50_000_00L,
            status = BudgetPeriodStatus.CLOSED,
            isReviewed = true,
            createdAt = startAt,
            updatedAt = startAt
        )

        val currentSummary = createSummary(emptyList(), emptyList())

        val trend = (DashboardChartBuilder.buildSixPeriodTrend(
            closedCloses = listOf(historicalClose),
            allPeriods = listOf(pastPeriod, openPeriod),
            currentSummary = currentSummary,
            currentPeriodId = openPeriod.id
        ) as ChartState.Data).data

        assertThat(trend.periods).hasSize(2)

        val barClosed = trend.periods[0]
        assertThat(barClosed.incomePaise).isEqualTo(55_000_00L)
        assertThat(barClosed.spentPaise).isEqualTo(40_000_00L)
        assertThat(barClosed.savedPaise).isEqualTo(15_000_00L)
        assertThat(barClosed.isInProgress).isFalse()

        val barOpen = trend.periods[1]
        assertThat(barOpen.isInProgress).isTrue()
        assertThat(barOpen.label).isEqualTo("10/26")
    }
}
