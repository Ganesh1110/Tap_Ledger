package com.tapledger.ui.screens.insights

import com.google.common.truth.Truth.assertThat
import com.tapledger.core.database.entity.BudgetPeriodEntity
import com.tapledger.core.database.entity.MonthCloseEntity
import com.tapledger.core.database.entity.TransactionAllocationEntity
import com.tapledger.core.database.entity.TransactionEntity
import com.tapledger.core.model.BudgetBucket
import com.tapledger.core.model.BudgetPeriodStatus
import com.tapledger.core.model.MonthCloseReconciliationStatus
import com.tapledger.core.model.TransactionDirection
import com.tapledger.core.model.TransactionKind
import com.tapledger.core.model.TransactionSource
import com.tapledger.core.model.TransactionStatus
import com.tapledger.domain.model.BudgetPeriodSummary
import com.tapledger.ui.charts.builder.DashboardChartBuilder
import com.tapledger.ui.charts.builder.InsightsChartBuilder
import com.tapledger.ui.charts.model.ChartState
import com.tapledger.ui.charts.util.ChartMath
import org.junit.Test

/**
 * Unit tests verifying Insights screen visuals (Phase 7):
 * - V4 12-Period Cash Flow Trend (up to 11 closed + 1 in progress)
 * - V8 Top Merchants (top 8 by categorized expense, normalized merchant key, basis points)
 * - V7 Spending Calendar Heatmap integration
 */
class InsightsVisualsTest {

    private val baseEpoch = 1735689600000L // 2025-01-01 00:00:00 IST

    @Test
    fun testTwelvePeriodTrendGeneratesElevenClosedPlusOneInProgress() {
        val periods = (1..12).map { idx ->
            BudgetPeriodEntity(
                id = idx.toLong(),
                year = 2025 + (idx - 1) / 12,
                month = ((idx - 1) % 12) + 1,
                startAt = baseEpoch + (idx - 1) * 30 * ChartMath.MILLIS_PER_IST_DAY,
                endAt = baseEpoch + idx * 30 * ChartMath.MILLIS_PER_IST_DAY,
                incomePaise = 100_000_00L,
                status = if (idx == 12) BudgetPeriodStatus.OPEN else BudgetPeriodStatus.CLOSED,
                createdAt = baseEpoch,
                updatedAt = baseEpoch
            )
        }

        val closedCloses = (1..11).map { idx ->
            MonthCloseEntity(
                id = idx.toLong(),
                budgetPeriodId = idx.toLong(),
                closedAt = baseEpoch + idx * 30 * ChartMath.MILLIS_PER_IST_DAY,
                plannedIncomePaise = 100_000_00L,
                actualIncomePaise = 100_000_00L,
                totalBudgetedPaise = 100_000_00L,
                totalSpentPaise = 80_000_00L,
                totalSavedPaise = 20_000_00L,
                reconciliationStatus = MonthCloseReconciliationStatus.RECONCILED,
                overrideReason = null,
                closingBalancesJson = "{}"
            )
        }

        val openSummary = BudgetPeriodSummary(
            period = periods[11],
            totalIncomePaise = 100_000_00L,
            totalBudgetedPaise = 100_000_00L,
            unallocatedBudgetPaise = 0L,
            totalSpentPaise = 60_000_00L,
            totalRemainingPaise = 40_000_00L,
            bucketTotals = mapOf(
                BudgetBucket.NEEDS to 50_000_00L,
                BudgetBucket.WANTS to 30_000_00L,
                BudgetBucket.SAVINGS to 20_000_00L
            ),
            bucketSpent = mapOf(
                BudgetBucket.NEEDS to 40_000_00L,
                BudgetBucket.WANTS to 20_000_00L,
                BudgetBucket.SAVINGS to 0L
            ),
            categorySummaries = emptyList(),
            dashboardHeroSpendPaise = 60_000_00L,
            dashboardHeroBudgetPaise = 80_000_00L,
            actualSavingsPaise = 15_000_00L,
            actualIncomePaise = 100_000_00L
        )

        val resultState = DashboardChartBuilder.buildSixPeriodTrend(
            closedCloses = closedCloses,
            allPeriods = periods,
            currentSummary = openSummary,
            currentPeriodId = 12L,
            maxClosedPeriods = 11
        )

        assertThat(resultState).isInstanceOf(ChartState.Data::class.java)
        val data = (resultState as ChartState.Data).data

        // Total 12 periods: 11 closed + 1 in progress
        assertThat(data.periods).hasSize(12)
        assertThat(data.tableRows).hasSize(12)

        // First 11 are closed, 12th is in progress
        for (i in 0 until 11) {
            assertThat(data.periods[i].isInProgress).isFalse()
            assertThat(data.periods[i].incomePaise).isEqualTo(100_000_00L)
            assertThat(data.periods[i].spentPaise).isEqualTo(80_000_00L)
            assertThat(data.periods[i].savedPaise).isEqualTo(20_000_00L)
            assertThat(data.periods[i].savingsRateBp).isEqualTo(2000) // 20%
        }

        val inProgressBar = data.periods[11]
        assertThat(inProgressBar.isInProgress).isTrue()
        assertThat(inProgressBar.spentPaise).isEqualTo(60_000_00L) // 40k Needs + 20k Wants
        assertThat(inProgressBar.savedPaise).isEqualTo(15_000_00L)
    }

    @Test
    fun testTopMerchantsRankingAndTopEightCutoff() {
        // Create 10 merchants with different expense amounts
        val txs = (1..10).map { idx ->
            TransactionEntity(
                id = idx.toLong(),
                accountId = 1L,
                amountPaise = idx * 100_00L, // 100, 200, ... 1000 rupees
                occurredAt = baseEpoch + idx * 1000L,
                source = TransactionSource.MANUAL,
                merchantRaw = "Merchant $idx Raw",
                merchantNormalized = "Merchant $idx Normalized",
                direction = TransactionDirection.OUT,
                kind = TransactionKind.EXPENSE,
                status = TransactionStatus.CATEGORIZED,
                createdAt = baseEpoch,
                updatedAt = baseEpoch
            )
        }

        val resultState = InsightsChartBuilder.buildTopMerchants(txs)
        assertThat(resultState).isInstanceOf(ChartState.Data::class.java)
        val data = (resultState as ChartState.Data).data

        // Only top 8 should be included
        assertThat(data.merchants).hasSize(8)

        // Ranked descending by amount: Merchant 10 first, then 9, ... down to 3
        assertThat(data.merchants[0].merchantName).isEqualTo("Merchant 10 Normalized")
        assertThat(data.merchants[0].totalSpentPaise).isEqualTo(1000_00L)
        assertThat(data.merchants[7].merchantName).isEqualTo("Merchant 3 Normalized")
        assertThat(data.merchants[7].totalSpentPaise).isEqualTo(300_00L)

        // Total categorized expense across all 10 is (1+..+10)*100_00 = 55*100_00 = 5500_00L
        assertThat(data.totalCategorizedExpensePaise).isEqualTo(5500_00L)

        // Merchant 10 basis points: floor(1000_00 * 10000 / 5500_00) = 1818 bp (18.18%)
        assertThat(data.merchants[0].percentageBp).isEqualTo(1818)
    }

    @Test
    fun testTopMerchantsExcludesPendingVoidedIgnoredAndInflow() {
        val txs = listOf(
            TransactionEntity(
                id = 1L,
                accountId = 1L,
                amountPaise = 500_00L,
                occurredAt = baseEpoch,
                source = TransactionSource.MANUAL,
                merchantRaw = "Swiggy",
                merchantNormalized = "Swiggy",
                direction = TransactionDirection.OUT,
                kind = TransactionKind.EXPENSE,
                status = TransactionStatus.CATEGORIZED,
                createdAt = baseEpoch,
                updatedAt = baseEpoch
            ),
            TransactionEntity(
                id = 2L,
                accountId = 1L,
                amountPaise = 1000_00L,
                occurredAt = baseEpoch,
                source = TransactionSource.MANUAL,
                merchantRaw = "Employer",
                merchantNormalized = "Employer",
                direction = TransactionDirection.IN,
                kind = TransactionKind.INCOME,
                status = TransactionStatus.CATEGORIZED,
                createdAt = baseEpoch,
                updatedAt = baseEpoch
            ),
            TransactionEntity(
                id = 3L,
                accountId = 1L,
                amountPaise = 300_00L,
                occurredAt = baseEpoch,
                source = TransactionSource.MANUAL,
                merchantRaw = "Amazon",
                merchantNormalized = "Amazon",
                direction = TransactionDirection.OUT,
                kind = TransactionKind.EXPENSE,
                status = TransactionStatus.PENDING,
                createdAt = baseEpoch,
                updatedAt = baseEpoch
            ),
            TransactionEntity(
                id = 4L,
                accountId = 1L,
                amountPaise = 400_00L,
                occurredAt = baseEpoch,
                source = TransactionSource.MANUAL,
                merchantRaw = "Flipkart",
                merchantNormalized = "Flipkart",
                direction = TransactionDirection.OUT,
                kind = TransactionKind.EXPENSE,
                status = TransactionStatus.VOIDED,
                createdAt = baseEpoch,
                updatedAt = baseEpoch
            )
        )

        val resultState = InsightsChartBuilder.buildTopMerchants(txs)
        assertThat(resultState).isInstanceOf(ChartState.Data::class.java)
        val data = (resultState as ChartState.Data).data

        // Only Swiggy is eligible (CATEGORIZED, OUT, EXPENSE)
        assertThat(data.merchants).hasSize(1)
        assertThat(data.merchants[0].merchantName).isEqualTo("Swiggy")
        assertThat(data.merchants[0].totalSpentPaise).isEqualTo(500_00L)
        assertThat(data.merchants[0].percentageBp).isEqualTo(10_000) // 100% of eligible
    }

    @Test
    fun testEmptyMerchantsReturnsEmptyListWithSafeDescription() {
        val resultState = InsightsChartBuilder.buildTopMerchants(emptyList())
        assertThat(resultState).isInstanceOf(ChartState.Data::class.java)
        val data = (resultState as ChartState.Data).data
        assertThat(data.merchants).isEmpty()
        assertThat(data.totalCategorizedExpensePaise).isEqualTo(0L)
    }

    @Test
    fun testSpendingHeatmapExcludesSavingsAndComputesQuantiles() {
        val period = BudgetPeriodEntity(
            id = 1L,
            year = 2025,
            month = 1,
            startAt = baseEpoch,
            endAt = baseEpoch + 10 * ChartMath.MILLIS_PER_IST_DAY,
            incomePaise = 100_000_00L,
            status = BudgetPeriodStatus.OPEN,
            createdAt = baseEpoch,
            updatedAt = baseEpoch
        )

        val txs = listOf(
            TransactionEntity(
                id = 1L,
                accountId = 1L,
                amountPaise = 100_00L,
                occurredAt = baseEpoch + 1 * ChartMath.MILLIS_PER_IST_DAY + 1000L, // Day 1
                source = TransactionSource.MANUAL,
                merchantRaw = "Restaurant",
                merchantNormalized = "Restaurant",
                direction = TransactionDirection.OUT,
                kind = TransactionKind.EXPENSE,
                status = TransactionStatus.CATEGORIZED,
                createdAt = baseEpoch,
                updatedAt = baseEpoch
            ),
            TransactionEntity(
                id = 2L,
                accountId = 1L,
                amountPaise = 500_00L,
                occurredAt = baseEpoch + 2 * ChartMath.MILLIS_PER_IST_DAY + 1000L, // Day 2
                source = TransactionSource.MANUAL,
                merchantRaw = "Bank RD",
                merchantNormalized = "Bank RD",
                direction = TransactionDirection.OUT,
                kind = TransactionKind.EXPENSE,
                status = TransactionStatus.CATEGORIZED,
                createdAt = baseEpoch,
                updatedAt = baseEpoch
            )
        )

        val allocs = listOf(
            TransactionAllocationEntity(1L, 1L, 10L, 100_00L),
            TransactionAllocationEntity(2L, 2L, 20L, 500_00L) // Category 20 is SAVINGS
        )

        val categoryBuckets = mapOf(
            10L to BudgetBucket.NEEDS,
            20L to BudgetBucket.SAVINGS
        )

        val resultState = InsightsChartBuilder.buildSpendingHeatmap(period, txs, allocs, categoryBuckets)
        assertThat(resultState).isInstanceOf(ChartState.Data::class.java)
        val data = (resultState as ChartState.Data).data

        // 10 days
        assertThat(data.cells).hasSize(10)
        // Day 1 has 100_00 spend
        assertThat(data.cells[1].netSpentPaise).isEqualTo(100_00L)
        assertThat(data.cells[1].intensityLevel).isGreaterThan(0)
        // Day 2 was Savings so it must be 0L and intensity 0
        assertThat(data.cells[2].netSpentPaise).isEqualTo(0L)
        assertThat(data.cells[2].intensityLevel).isEqualTo(0)
    }
}
