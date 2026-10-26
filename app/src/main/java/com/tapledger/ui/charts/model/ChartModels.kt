package com.tapledger.ui.charts.model

import androidx.compose.runtime.Immutable
import com.tapledger.core.model.BudgetBucket

/**
 * Immutable Chart Models for Visual Analytics V1 through V13 per Flow.md §17.2.
 * Zero Float, Double, or BigDecimal. All values are signed Long paise, Int bp, or Int permille.
 */

// --- V1: Dashboard Hero Ring ---
@Immutable
data class DashboardHeroRingModel(
    val spentPaise: Long,
    val budgetPaise: Long,
    val remainingPaise: Long,
    val percentageBp: Int,
    val paceBp: Int?,
    val isExceeded: Boolean,
    val isWarning: Boolean,
    val contentDescription: String
)

// --- V2: Burn-up Line ---
@Immutable
data class BurnUpPoint(
    val dayIndex: Int,
    val epochMillis: Long,
    val netSpentPaise: Long
)

@Immutable
data class BurnUpTableRow(
    val dateLabel: String,
    val netSpentFormatted: String,
    val paceFormatted: String,
    val budgetCapFormatted: String
)

@Immutable
data class BurnUpChartModel(
    val cumulativePoints: List<BurnUpPoint>,
    val pacePoints: List<BurnUpPoint>,
    val budgetCapPaise: Long,
    val projectionPaise: Long?,
    val projectionPoint: BurnUpPoint?,
    val lastCumulativePaise: Long,
    val contentDescription: String,
    val tableRows: List<BurnUpTableRow>
)

// --- V3: Bucket Rings 50/30/20 ---
@Immutable
data class BucketRingItem(
    val bucket: BudgetBucket,
    val spentPaise: Long,
    val budgetPaise: Long,
    val percentageBp: Int,
    val isGoalAchieved: Boolean,
    val isExceeded: Boolean,
    val isWarning: Boolean
)

@Immutable
data class BucketRingsModel(
    val needs: BucketRingItem,
    val wants: BucketRingItem,
    val savings: BucketRingItem,
    val contentDescription: String
)

// --- V4: Six-Period Trend ---
@Immutable
data class PeriodTrendBar(
    val periodId: Long,
    val label: String,
    val incomePaise: Long,
    val spentPaise: Long,
    val savedPaise: Long,
    val savingsRateBp: Int?,
    val isInProgress: Boolean
)

@Immutable
data class TrendTableRow(
    val periodLabel: String,
    val incomeFormatted: String,
    val spentFormatted: String,
    val savedFormatted: String,
    val savingsRateFormatted: String,
    val statusLabel: String
)

@Immutable
data class SixPeriodTrendModel(
    val periods: List<PeriodTrendBar>,
    val contentDescription: String,
    val tableRows: List<TrendTableRow>
)

// --- V5: Envelope Bullet Bars ---
@Immutable
data class EnvelopeBulletBarItem(
    val categoryId: Long,
    val categoryName: String,
    val bucket: BudgetBucket,
    val spentPaise: Long,
    val baseAllocationPaise: Long,
    val adjustedBudgetPaise: Long,
    val advPosPaise: Long,
    val advNegPaise: Long,
    val percentageBp: Int,
    val isExceeded: Boolean,
    val isWarning: Boolean,
    val isGoalAchieved: Boolean
)

@Immutable
data class BulletBarsModel(
    val envelopes: List<EnvelopeBulletBarItem>,
    val contentDescription: String
)

// --- V6: Category Donut ---
@Immutable
data class CategoryDonutSlice(
    val categoryId: Long,
    val categoryName: String,
    val amountPaise: Long,
    val percentageBp: Int,
    val percentageFormatted: String
)

@Immutable
data class CategoryRefundItem(
    val categoryId: Long,
    val categoryName: String,
    val netRefundPaise: Long,
    val refundFormatted: String
)

@Immutable
data class CategoryDonutModel(
    val slices: List<CategoryDonutSlice>,
    val refundCategories: List<CategoryRefundItem>,
    val totalSpentPaise: Long,
    val contentDescription: String
)

// --- V7: Spending Calendar Heatmap ---
@Immutable
data class HeatmapDayCell(
    val epochMillis: Long,
    val dayOfMonth: Int,
    val netSpentPaise: Long,
    val intensityLevel: Int // 0..4
)

@Immutable
data class SpendingHeatmapModel(
    val cells: List<HeatmapDayCell>,
    val totalSpentPaise: Long,
    val activeDaysCount: Int,
    val contentDescription: String
)

// --- V8: Top Merchants ---
@Immutable
data class TopMerchantItem(
    val merchantName: String,
    val totalSpentPaise: Long,
    val transactionCount: Int,
    val percentageBp: Int,
    val amountFormatted: String
)

@Immutable
data class TopMerchantsModel(
    val merchants: List<TopMerchantItem>,
    val totalCategorizedExpensePaise: Long,
    val contentDescription: String
)

// --- V9: Net Worth Line / Area ---
@Immutable
data class NetWorthPoint(
    val timestamp: Long,
    val label: String,
    val netWorthPaise: Long,
    val assetsPaise: Long,
    val liabilitiesPaise: Long
)

@Immutable
data class NetWorthTableRow(
    val dateLabel: String,
    val netWorthFormatted: String,
    val assetsFormatted: String,
    val liabilitiesFormatted: String
)

@Immutable
data class NetWorthChartModel(
    val points: List<NetWorthPoint>,
    val currentNetWorthPaise: Long,
    val unassignedCount: Int,
    val unassignedExcludedMessage: String?,
    val contentDescription: String,
    val tableRows: List<NetWorthTableRow>
)

// --- V10: Account Composition ---
@Immutable
data class AccountCompositionBarItem(
    val accountId: Long,
    val accountName: String,
    val balancePaise: Long,
    val isAsset: Boolean,
    val absBalancePaise: Long
)

enum class UtilizationZone {
    NORMAL, // < 30%
    CAUTION, // 30% - 70%
    HIGH // > 70%
}

@Immutable
data class CreditCardUtilizationGaugeItem(
    val accountId: Long,
    val accountName: String,
    val balancePaise: Long,
    val creditLimitPaise: Long,
    val utilizationBp: Int,
    val zone: UtilizationZone
)

@Immutable
data class AccountCompositionModel(
    val accounts: List<AccountCompositionBarItem>,
    val totalAssetsPaise: Long,
    val totalLiabilitiesPaise: Long,
    val netWorthPaise: Long,
    val creditCardGauges: List<CreditCardUtilizationGaugeItem>,
    val unassignedCount: Int,
    val unassignedExcludedMessage: String?,
    val contentDescription: String
)

// --- V11: Advance Rollover Bars ---
@Immutable
data class AdvanceRolloverBarItem(
    val categoryId: Long,
    val categoryName: String,
    val carryPaise: Long,
    val sweepPaise: Long,
    val appliedDeficitPaise: Long,
    val uncoveredPoolPaise: Long
)

@Immutable
data class AdvanceRolloverTableRow(
    val categoryName: String,
    val carryFormatted: String,
    val sweepFormatted: String,
    val appliedDeficitFormatted: String,
    val uncoveredPoolFormatted: String
)

@Immutable
data class AdvanceRolloverChartModel(
    val rows: List<AdvanceRolloverBarItem>,
    val contentDescription: String,
    val tableRows: List<AdvanceRolloverTableRow>
)

// --- V13: Diagnostics Severity Bar ---
@Immutable
data class DiagnosticsSeverityBarModel(
    val criticalCount: Int,
    val errorCount: Int,
    val warningCount: Int,
    val totalIssues: Int,
    val contentDescription: String
)
