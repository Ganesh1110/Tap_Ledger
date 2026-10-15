package com.tapledger.ui.charts.util

import com.tapledger.core.model.MoneyFormatter
import com.tapledger.ui.charts.model.AccountCompositionModel
import com.tapledger.ui.charts.model.AdvanceRolloverChartModel
import com.tapledger.ui.charts.model.BucketRingsModel
import com.tapledger.ui.charts.model.BulletBarsModel
import com.tapledger.ui.charts.model.BurnUpChartModel
import com.tapledger.ui.charts.model.CategoryDonutModel
import com.tapledger.ui.charts.model.DashboardHeroRingModel
import com.tapledger.ui.charts.model.DiagnosticsSeverityBarModel
import com.tapledger.ui.charts.model.NetWorthChartModel
import com.tapledger.ui.charts.model.ReconciliationBridgeModel
import com.tapledger.ui.charts.model.SixPeriodTrendModel
import com.tapledger.ui.charts.model.SpendingHeatmapModel
import com.tapledger.ui.charts.model.TopMerchantsModel

/**
 * Pure calculation helper to generate plain-language one-sentence summaries for V1–V13 charts.
 * Adheres strictly to Flow.md §1.1 and §17.1 (Zero Float / Zero Double).
 */
object ChartSummaryHelper {

    fun summarizeV1HeroRing(model: DashboardHeroRingModel): String {
        val pctUsed = ChartMath.formatBasisPoints(model.percentageBp)
        return if (model.isExceeded) {
            val overPaise = model.spentPaise - model.budgetPaise
            "${MoneyFormatter.formatCompactIndian(overPaise)} over budget • $pctUsed used"
        } else {
            val paceStr = when {
                model.paceBp == null -> "on pace"
                model.percentageBp > model.paceBp + 500 -> "behind pace"
                model.percentageBp < model.paceBp - 500 -> "ahead of pace"
                else -> "on pace"
            }
            "${MoneyFormatter.formatCompactIndian(model.remainingPaise)} left • $paceStr"
        }
    }

    fun summarizeV2BurnUp(model: BurnUpChartModel): String {
        val spent = MoneyFormatter.formatCompactIndian(model.lastCumulativePaise)
        val budget = MoneyFormatter.formatCompactIndian(model.budgetCapPaise)
        val proj = model.projectionPaise?.let {
            " • proj. ${MoneyFormatter.formatCompactIndian(it)}"
        } ?: " • on track"
        return "Spent $spent of $budget$proj"
    }

    fun summarizeV3BucketRings(model: BucketRingsModel): String {
        val nwStatus = if (model.needs.isExceeded || model.wants.isExceeded) "over budget" else "on track"
        val savingsStatus = if (model.savings.isGoalAchieved) "Savings goal achieved" else "Savings in progress"
        return "Needs & Wants $nwStatus • $savingsStatus"
    }

    fun summarizeV4Trend(model: SixPeriodTrendModel): String {
        val latest = model.periods.lastOrNull() ?: return "No historical trend periods"
        val income = MoneyFormatter.formatCompactIndian(latest.incomePaise)
        val spent = MoneyFormatter.formatCompactIndian(latest.spentPaise)
        val rate = latest.savingsRateBp?.let { "${it / 100}% saved" } ?: "0% saved"
        return "Income $income, spent $spent • $rate"
    }

    fun summarizeV5BulletBars(model: BulletBarsModel): String {
        if (model.envelopes.isEmpty()) return "No envelope categories defined"
        val topEnvelope = model.envelopes.maxByOrNull { it.spentPaise } ?: model.envelopes.first()
        val exceededCount = model.envelopes.count { it.isExceeded }
        val pct = ChartMath.formatBasisPoints(topEnvelope.percentageBp)
        val status = if (exceededCount > 0) {
            "$exceededCount ${if (exceededCount == 1) "category" else "categories"} over budget"
        } else {
            "all categories on track"
        }
        return "${topEnvelope.categoryName} is $pct used • $status"
    }

    fun summarizeV6Donut(model: CategoryDonutModel): String {
        val top = model.slices.firstOrNull() ?: return "No spending recorded in this period"
        return "${top.categoryName} is your biggest spend: ${top.percentageFormatted}"
    }

    fun summarizeV7Heatmap(model: SpendingHeatmapModel): String {
        if (model.cells.isEmpty()) return "No spending days recorded"
        val maxCell = model.cells.maxByOrNull { it.netSpentPaise }
        val maxSpend = maxCell?.let { MoneyFormatter.formatCompactIndian(it.netSpentPaise) } ?: "₹0"
        return "${model.activeDaysCount} active spending days • peak day $maxSpend"
    }

    fun summarizeV8TopMerchants(model: TopMerchantsModel): String {
        val top = model.merchants.firstOrNull() ?: return "No merchant spending recorded"
        val pct = ChartMath.formatBasisPoints(top.percentageBp)
        return "${top.merchantName} is your top merchant: ${top.amountFormatted} ($pct)"
    }

    fun summarizeV9NetWorth(model: NetWorthChartModel): String {
        val first = model.points.firstOrNull()
        val last = model.points.lastOrNull()
        val currentStr = MoneyFormatter.formatCompactIndian(model.currentNetWorthPaise)
        if (first == null || last == null || model.points.size <= 1) {
            return "Current net worth is $currentStr"
        }
        val diffPaise = last.netWorthPaise - first.netWorthPaise
        val trendWord = if (diffPaise >= 0L) "up" else "down"
        val diffStr = MoneyFormatter.formatCompactIndian(kotlin.math.abs(diffPaise))
        return "Net worth $currentStr • $trendWord $diffStr"
    }

    fun summarizeV10AccountComposition(model: AccountCompositionModel): String {
        val assets = MoneyFormatter.formatCompactIndian(model.totalAssetsPaise)
        val liab = MoneyFormatter.formatCompactIndian(model.totalLiabilitiesPaise)
        val maxCc = model.creditCardGauges.maxByOrNull { it.utilizationBp }
        val ccText = maxCc?.let {
            "${ChartMath.formatBasisPoints(it.utilizationBp)} CC utilization"
        } ?: "0% CC utilization"
        return "$assets assets vs $liab liabilities • $ccText"
    }

    fun summarizeV11AdvanceRollover(model: AdvanceRolloverChartModel): String {
        var totalCarry = 0L
        var totalSweep = 0L
        model.rows.forEach {
            totalCarry += it.carryPaise
            totalSweep += it.sweepPaise
        }
        return "${MoneyFormatter.formatCompactIndian(totalCarry)} carried forward • ${MoneyFormatter.formatCompactIndian(totalSweep)} swept"
    }

    fun summarizeV12Reconciliation(model: ReconciliationBridgeModel): String {
        return if (model.isReconciled) {
            "Fully reconciled with statement closing balance"
        } else {
            "${MoneyFormatter.formatCompactIndian(model.residualPaise)} discrepancy requires review"
        }
    }

    fun summarizeV13Diagnostics(model: DiagnosticsSeverityBarModel): String {
        return if (model.totalIssues == 0) {
            "All financial data clean • 0 issues found"
        } else {
            "${model.totalIssues} issues found (${model.criticalCount} critical, ${model.errorCount} error)"
        }
    }
}
