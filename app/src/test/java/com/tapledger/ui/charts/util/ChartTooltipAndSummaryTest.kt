package com.tapledger.ui.charts.util

import com.google.common.truth.Truth.assertThat
import com.tapledger.core.model.BudgetBucket
import com.tapledger.ui.charts.model.*
import com.tapledger.ui.charts.render.ChartTooltipPositioner
import org.junit.Test

/**
 * Unit tests verifying:
 * 1. ChartTooltip positioning, edge clamping, and flip-down behavior (Flow.md §17.1 D10).
 * 2. Compact screen / large font threshold logic (Flow.md §17.1 D12).
 * 3. Plain-language summary string generation for V1–V13 models (Flow.md §17.1 D11).
 */
class ChartTooltipAndSummaryTest {

    // =========================================================================
    // 1. Tooltip Positioning and Clamping Tests
    // =========================================================================

    @Test
    fun tooltipPosition_fitsAbove_placesTooltipAboveTouchPoint() {
        val (x, y) = ChartTooltipPositioner.calculateTooltipOffset(
            touchX = 200f,
            touchY = 300f,
            containerWidth = 400f,
            containerHeight = 600f,
            tooltipWidth = 180f,
            tooltipHeight = 64f,
            margin = 8f,
            verticalOffset = 56f,
            flipDownOffset = 24f
        )
        // Expected X: 200 - 90 = 110 (within 8..212)
        assertThat(x).isEqualTo(110f)
        // Expected Y: 300 - 56 - 64 = 180 (>= 8, fits above)
        assertThat(y).isEqualTo(180f)
    }

    @Test
    fun tooltipPosition_leftEdgeClamped_doesNotClipLeftMargin() {
        val (x, _) = ChartTooltipPositioner.calculateTooltipOffset(
            touchX = 20f,
            touchY = 300f,
            containerWidth = 400f,
            containerHeight = 600f,
            tooltipWidth = 180f,
            tooltipHeight = 64f,
            margin = 8f,
            verticalOffset = 56f,
            flipDownOffset = 24f
        )
        // Raw X: 20 - 90 = -70 -> clamped to margin 8f
        assertThat(x).isEqualTo(8f)
    }

    @Test
    fun tooltipPosition_rightEdgeClamped_doesNotClipRightMargin() {
        val (x, _) = ChartTooltipPositioner.calculateTooltipOffset(
            touchX = 390f,
            touchY = 300f,
            containerWidth = 400f,
            containerHeight = 600f,
            tooltipWidth = 180f,
            tooltipHeight = 64f,
            margin = 8f,
            verticalOffset = 56f,
            flipDownOffset = 24f
        )
        // Raw X: 390 - 90 = 300 -> clamped to 400 - 180 - 8 = 212f
        assertThat(x).isEqualTo(212f)
    }

    @Test
    fun tooltipPosition_nearTop_flipsBelowTouchPoint() {
        val (_, y) = ChartTooltipPositioner.calculateTooltipOffset(
            touchX = 200f,
            touchY = 40f, // 40 - 56 - 64 = -80 < margin (8f), does not fit above!
            containerWidth = 400f,
            containerHeight = 600f,
            tooltipWidth = 180f,
            tooltipHeight = 64f,
            margin = 8f,
            verticalOffset = 56f,
            flipDownOffset = 24f
        )
        // Flips below: 40 + 24 = 64f
        assertThat(y).isEqualTo(64f)
    }

    @Test
    fun tooltipPosition_nearBottomFlipped_clampsToBottomMargin() {
        val (_, y) = ChartTooltipPositioner.calculateTooltipOffset(
            touchX = 200f,
            touchY = 10f, // doesn't fit above
            containerWidth = 400f,
            containerHeight = 100f, // small container
            tooltipWidth = 180f,
            tooltipHeight = 64f,
            margin = 8f,
            verticalOffset = 56f,
            flipDownOffset = 50f // 10 + 50 = 60 -> clamped to 100 - 64 - 8 = 28f
        )
        assertThat(y).isEqualTo(28f)
    }

    // =========================================================================
    // 2. Compact Screen / Large Font Scale Threshold Tests
    // =========================================================================

    @Test
    fun isCompactScreen_evaluatesCorrectlyAcrossWidthAndFontScale() {
        // Narrow phone (width < 360dp) -> compact
        assertThat(ChartTooltipPositioner.isCompactScreen(screenWidthDp = 320, fontScale = 1.0f)).isTrue()
        assertThat(ChartTooltipPositioner.isCompactScreen(screenWidthDp = 359, fontScale = 1.0f)).isTrue()

        // Standard phone (360dp) with normal font -> regular
        assertThat(ChartTooltipPositioner.isCompactScreen(screenWidthDp = 360, fontScale = 1.0f)).isFalse()
        assertThat(ChartTooltipPositioner.isCompactScreen(screenWidthDp = 412, fontScale = 1.0f)).isFalse()

        // Standard or large phone with large font (> 1.15) -> compact
        assertThat(ChartTooltipPositioner.isCompactScreen(screenWidthDp = 360, fontScale = 1.16f)).isTrue()
        assertThat(ChartTooltipPositioner.isCompactScreen(screenWidthDp = 412, fontScale = 1.30f)).isTrue()

        // Exact boundary 1.15f -> regular
        assertThat(ChartTooltipPositioner.isCompactScreen(screenWidthDp = 360, fontScale = 1.15f)).isFalse()
    }

    // =========================================================================
    // 3. Plain-Language Summary Generator Tests (V1–V13)
    // =========================================================================

    @Test
    fun summarizeV1HeroRing_aheadOfPaceAndExceeded() {
        val aheadModel = DashboardHeroRingModel(
            spentPaise = 20_000_00L,
            budgetPaise = 30_000_00L,
            remainingPaise = 10_000_00L,
            percentageBp = 6_666,
            paceBp = 8_000,
            isExceeded = false,
            isWarning = false,
            contentDescription = ""
        )
        assertThat(ChartSummaryHelper.summarizeV1HeroRing(aheadModel))
            .isEqualTo("₹10K left • ahead of pace")

        val exceededModel = DashboardHeroRingModel(
            spentPaise = 35_000_00L,
            budgetPaise = 30_000_00L,
            remainingPaise = 0L,
            percentageBp = 11_666,
            paceBp = 10_000,
            isExceeded = true,
            isWarning = false,
            contentDescription = ""
        )
        assertThat(ChartSummaryHelper.summarizeV1HeroRing(exceededModel))
            .isEqualTo("₹5K over budget • 116.66% used")
    }

    @Test
    fun summarizeV2BurnUp_onTrackAndProjected() {
        val model = BurnUpChartModel(
            cumulativePoints = emptyList(),
            pacePoints = emptyList(),
            budgetCapPaise = 50_000_00L,
            projectionPaise = 45_000_00L,
            projectionPoint = null,
            lastCumulativePaise = 20_000_00L,
            contentDescription = "",
            tableRows = emptyList()
        )
        assertThat(ChartSummaryHelper.summarizeV2BurnUp(model))
            .isEqualTo("Spent ₹20K of ₹50K • proj. ₹45K")
    }

    @Test
    fun summarizeV3BucketRings_onTrackAndOverBudget() {
        val onTrack = BucketRingsModel(
            needs = BucketRingItem(BudgetBucket.NEEDS, 10_000_00L, 20_000_00L, 5_000, false, false, false),
            wants = BucketRingItem(BudgetBucket.WANTS, 5_000_00L, 10_000_00L, 5_000, false, false, false),
            savings = BucketRingItem(BudgetBucket.SAVINGS, 5_000_00L, 5_000_00L, 10_000, true, false, false),
            contentDescription = ""
        )
        assertThat(ChartSummaryHelper.summarizeV3BucketRings(onTrack))
            .isEqualTo("Needs & Wants on track • Savings goal achieved")

        val exceeded = onTrack.copy(
            needs = onTrack.needs.copy(isExceeded = true),
            savings = onTrack.savings.copy(isGoalAchieved = false)
        )
        assertThat(ChartSummaryHelper.summarizeV3BucketRings(exceeded))
            .isEqualTo("Needs & Wants over budget • Savings in progress")
    }

    @Test
    fun summarizeV4Trend_formatsLatestPeriod() {
        val model = SixPeriodTrendModel(
            periods = listOf(
                PeriodTrendBar(1L, "Feb", 50_000_00L, 30_000_00L, 20_000_00L, 4_000, false),
                PeriodTrendBar(2L, "Mar", 60_000_00L, 40_000_00L, 20_000_00L, 3_333, true)
            ),
            contentDescription = "",
            tableRows = emptyList()
        )
        assertThat(ChartSummaryHelper.summarizeV4Trend(model))
            .isEqualTo("Income ₹60K, spent ₹40K • 33% saved")
    }

    @Test
    fun summarizeV5BulletBars_summarizesTopCategoryAndStatus() {
        val model = BulletBarsModel(
            envelopes = listOf(
                EnvelopeBulletBarItem(1L, "Groceries", BudgetBucket.NEEDS, 8_000_00L, 10_000_00L, 10_000_00L, 0L, 0L, 8_000, false, false, false),
                EnvelopeBulletBarItem(2L, "Fuel", BudgetBucket.NEEDS, 3_000_00L, 5_000_00L, 5_000_00L, 0L, 0L, 6_000, false, false, false)
            ),
            contentDescription = ""
        )
        assertThat(ChartSummaryHelper.summarizeV5BulletBars(model))
            .isEqualTo("Groceries is 80.00% used • all categories on track")

        val overModel = BulletBarsModel(
            envelopes = listOf(
                EnvelopeBulletBarItem(1L, "Groceries", BudgetBucket.NEEDS, 12_000_00L, 10_000_00L, 10_000_00L, 0L, 0L, 12_000, true, false, false)
            ),
            contentDescription = ""
        )
        assertThat(ChartSummaryHelper.summarizeV5BulletBars(overModel))
            .isEqualTo("Groceries is 120.00% used • 1 category over budget")
    }

    @Test
    fun summarizeV6Donut_identifiesTopCategoryShare() {
        val model = CategoryDonutModel(
            slices = listOf(
                CategoryDonutSlice(1L, "Food", 6_000_00L, 6_000, "60%"),
                CategoryDonutSlice(2L, "Rent", 4_000_00L, 4_000, "40%")
            ),
            refundCategories = emptyList(),
            totalSpentPaise = 10_000_00L,
            contentDescription = ""
        )
        assertThat(ChartSummaryHelper.summarizeV6Donut(model))
            .isEqualTo("Food is your biggest spend: 60%")
    }

    @Test
    fun summarizeV7Heatmap_identifiesPeakDay() {
        val model = SpendingHeatmapModel(
            cells = listOf(
                HeatmapDayCell(1000L, 1, 500_00L, 1),
                HeatmapDayCell(2000L, 15, 4_500_00L, 4)
            ),
            totalSpentPaise = 5_000_00L,
            activeDaysCount = 2,
            contentDescription = ""
        )
        assertThat(ChartSummaryHelper.summarizeV7Heatmap(model))
            .isEqualTo("2 active spending days • peak day ₹4.5K")
    }

    @Test
    fun summarizeV8TopMerchants_identifiesTopMerchant() {
        val model = TopMerchantsModel(
            merchants = listOf(
                TopMerchantItem("Swiggy", 7_000_00L, 14, 3_500, "₹7,000"),
                TopMerchantItem("Amazon", 5_000_00L, 6, 2_500, "₹5,000")
            ),
            totalCategorizedExpensePaise = 20_000_00L,
            contentDescription = ""
        )
        assertThat(ChartSummaryHelper.summarizeV8TopMerchants(model))
            .isEqualTo("Swiggy is your top merchant: ₹7,000 (35.00%)")
    }

    @Test
    fun summarizeV9NetWorth_evaluatesTrend() {
        val model = NetWorthChartModel(
            points = listOf(
                NetWorthPoint(1000L, "Jan", 100_000_00L, 150_000_00L, 50_000_00L),
                NetWorthPoint(2000L, "Apr", 125_000_00L, 175_000_00L, 50_000_00L)
            ),
            currentNetWorthPaise = 125_000_00L,
            unassignedCount = 0,
            unassignedExcludedMessage = null,
            contentDescription = "",
            tableRows = emptyList()
        )
        assertThat(ChartSummaryHelper.summarizeV9NetWorth(model))
            .isEqualTo("Net worth ₹1.2L • up ₹25K")
    }

    @Test
    fun summarizeV10AccountComposition_netAssetsAndCreditCardCaution() {
        val model = AccountCompositionModel(
            accounts = listOf(
                AccountCompositionBarItem(1L, "HDFC", 100_000_00L, true, 100_000_00L),
                AccountCompositionBarItem(2L, "ICICI CC", -20_000_00L, false, 20_000_00L)
            ),
            totalAssetsPaise = 100_000_00L,
            totalLiabilitiesPaise = 20_000_00L,
            netWorthPaise = 80_000_00L,
            creditCardGauges = listOf(
                CreditCardUtilizationGaugeItem(2L, "ICICI CC", 20_000_00L, 100_000_00L, 2_000, UtilizationZone.NORMAL)
            ),
            unassignedCount = 0,
            unassignedExcludedMessage = null,
            contentDescription = ""
        )
        assertThat(ChartSummaryHelper.summarizeV10AccountComposition(model))
            .isEqualTo("₹1L assets vs ₹20K liabilities • 20.00% CC utilization")
    }

    @Test
    fun summarizeV11AdvanceRollover_carryAndSweep() {
        val model = AdvanceRolloverChartModel(
            rows = listOf(
                AdvanceRolloverBarItem(1L, "Salary Advance", carryPaise = 5_000_00L, sweepPaise = 2_000_00L, appliedDeficitPaise = 0L, uncoveredPoolPaise = 0L)
            ),
            contentDescription = "",
            tableRows = emptyList()
        )
        assertThat(ChartSummaryHelper.summarizeV11AdvanceRollover(model))
            .isEqualTo("₹5K carried forward • ₹2K swept")
    }

    @Test
    fun summarizeV12Reconciliation_reconciledVsDiscrepancy() {
        val reconciledModel = ReconciliationBridgeModel(
            steps = emptyList(),
            appClosingPaise = 15_000_00L,
            stmtClosingPaise = 15_000_00L,
            residualPaise = 0L,
            isReconciled = true,
            contentDescription = "",
            tableRows = emptyList()
        )
        assertThat(ChartSummaryHelper.summarizeV12Reconciliation(reconciledModel))
            .isEqualTo("Fully reconciled with statement closing balance")

        val discrepancyModel = reconciledModel.copy(
            residualPaise = 1_500_00L,
            isReconciled = false
        )
        assertThat(ChartSummaryHelper.summarizeV12Reconciliation(discrepancyModel))
            .isEqualTo("₹1.5K discrepancy requires review")
    }

    @Test
    fun summarizeV13Diagnostics_cleanLedgerVsViolations() {
        val cleanModel = DiagnosticsSeverityBarModel(
            criticalCount = 0,
            errorCount = 0,
            warningCount = 0,
            totalIssues = 0,
            contentDescription = ""
        )
        assertThat(ChartSummaryHelper.summarizeV13Diagnostics(cleanModel))
            .isEqualTo("All financial data clean • 0 issues found")

        val dirtyModel = DiagnosticsSeverityBarModel(
            criticalCount = 1,
            errorCount = 2,
            warningCount = 4,
            totalIssues = 7,
            contentDescription = ""
        )
        assertThat(ChartSummaryHelper.summarizeV13Diagnostics(dirtyModel))
            .isEqualTo("7 issues found (1 critical, 2 error)")
    }
}
