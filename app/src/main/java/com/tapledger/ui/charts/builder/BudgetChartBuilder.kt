package com.tapledger.ui.charts.builder

import com.tapledger.core.model.BudgetBucket
import com.tapledger.core.model.MoneyFormatter
import com.tapledger.domain.model.BudgetPeriodSummary
import com.tapledger.ui.charts.model.BulletBarsModel
import com.tapledger.ui.charts.model.CategoryDonutModel
import com.tapledger.ui.charts.model.CategoryDonutSlice
import com.tapledger.ui.charts.model.CategoryRefundItem
import com.tapledger.ui.charts.model.ChartState
import com.tapledger.ui.charts.model.EnvelopeBulletBarItem
import com.tapledger.ui.charts.util.ChartMath

/**
 * Pure builders for Budget visual analytics (V5 Bullet Bars, V6 Category Donut) per Flow.md §17.2.
 */
object BudgetChartBuilder {

    /**
     * V5 Envelope bullet bars: per category, spent vs AdjustedBudget, base marker,
     * carried-in segment (AdvPos), applied-deficit segment (AdvNeg), and D8 overflow.
     * Savings uses goal styling.
     */
    fun buildEnvelopeBulletBars(
        summary: BudgetPeriodSummary,
        categoryNames: Map<Long, String> = emptyMap(),
        advPosPerCategory: Map<Long, Long> = emptyMap(),
        advNegPerCategory: Map<Long, Long> = emptyMap()
    ): ChartState<BulletBarsModel> {
        return try {
            val envelopes = summary.categorySummaries.map { cat ->
                val catId = cat.allocation.categoryId
                val bucket = cat.allocation.bucketSnapshot
                val isSavings = bucket == BudgetBucket.SAVINGS
                val spent = cat.netSpentPaise
                val adjustedBudget = cat.adjustedBudgetPaise
                val base = cat.baseBudgetPaise
                val advPos = advPosPerCategory[catId] ?: 0L
                val advNeg = advNegPerCategory[catId] ?: 0L
                val name = categoryNames[catId] ?: "Category #$catId"

                val percentageBp = ChartMath.computeBasisPoints(spent, adjustedBudget)
                val isGoalAchieved = isSavings && percentageBp >= 10000
                val isExceeded = !isSavings && spent > adjustedBudget && adjustedBudget > 0L
                val isWarning = !isSavings && percentageBp in 8000..10000 && !isExceeded

                EnvelopeBulletBarItem(
                    categoryId = catId,
                    categoryName = name,
                    bucket = bucket,
                    spentPaise = spent,
                    baseAllocationPaise = base,
                    adjustedBudgetPaise = adjustedBudget,
                    advPosPaise = advPos,
                    advNegPaise = advNeg,
                    percentageBp = percentageBp,
                    isExceeded = isExceeded,
                    isWarning = isWarning,
                    isGoalAchieved = isGoalAchieved
                )
            }

            if (envelopes.isEmpty()) {
                return ChartState.Empty("No budget envelopes found for this period")
            }

            val contentDesc = buildString {
                append("Budget Envelopes: ").append(envelopes.size).append(" categories. ")
                val exceededCount = envelopes.count { it.isExceeded }
                if (exceededCount > 0) append(exceededCount).append(" envelopes exceeded. ")
                val warningCount = envelopes.count { it.isWarning }
                if (warningCount > 0) append(warningCount).append(" envelopes warning. ")
                val goalsCount = envelopes.count { it.isGoalAchieved }
                if (goalsCount > 0) append(goalsCount).append(" savings goals achieved.")
            }

            ChartState.Data(
                BulletBarsModel(
                    envelopes = envelopes,
                    contentDescription = contentDesc
                )
            )
        } catch (e: ArithmeticException) {
            ChartState.Error("Arithmetic overflow calculating bullet bars: ${e.message}")
        }
    }

    /**
     * V6 Category donut: Needs+Wants categories with NetSpent > 0, top 6 plus "Other",
     * D2 percentages apportioned in bp to sum to exactly 10,000 bp.
     * Net-negative (refund-heavy) categories are excluded from donut and listed as "Net refund".
     */
    fun buildCategoryDonut(
        summary: BudgetPeriodSummary,
        categoryNames: Map<Long, String> = emptyMap()
    ): ChartState<CategoryDonutModel> {
        return try {
            val nonSavings = summary.categorySummaries.filter { it.allocation.bucketSnapshot != BudgetBucket.SAVINGS }

            val positiveSpendCategories = nonSavings.filter { it.netSpentPaise > 0L }.sortedByDescending { it.netSpentPaise }
            val netRefundCategories = nonSavings.filter { it.netSpentPaise < 0L }.map {
                val catId = it.allocation.categoryId
                val name = categoryNames[catId] ?: "Category #$catId"
                CategoryRefundItem(
                    categoryId = catId,
                    categoryName = name,
                    netRefundPaise = -it.netSpentPaise,
                    refundFormatted = MoneyFormatter.formatPaiseToRupees(-it.netSpentPaise)
                )
            }

            if (positiveSpendCategories.isEmpty()) {
                if (netRefundCategories.isEmpty()) {
                    return ChartState.Empty("No spending recorded in this period")
                }
                return ChartState.Data(
                    CategoryDonutModel(
                        slices = emptyList(),
                        refundCategories = netRefundCategories,
                        totalSpentPaise = 0L,
                        contentDescription = "No categorized spending recorded in this period"
                    )
                )
            }

            // Top 6 plus "Other"
            val topSlices: List<Pair<Long, Long>>
            val categoryNameMap = mutableMapOf<Long, String>()

            if (positiveSpendCategories.size <= 7) {
                topSlices = positiveSpendCategories.map {
                    val catId = it.allocation.categoryId
                    val name = categoryNames[catId] ?: "Category #$catId"
                    categoryNameMap[catId] = name
                    Pair(catId, it.netSpentPaise)
                }
            } else {
                val top6 = positiveSpendCategories.take(6)
                val other = positiveSpendCategories.drop(6)
                val otherTotal = other.sumOf { it.netSpentPaise }

                val slicesList = mutableListOf<Pair<Long, Long>>()
                for (cat in top6) {
                    val catId = cat.allocation.categoryId
                    val name = categoryNames[catId] ?: "Category #$catId"
                    categoryNameMap[catId] = name
                    slicesList.add(Pair(catId, cat.netSpentPaise))
                }
                val otherId = -1L
                categoryNameMap[otherId] = "Other"
                slicesList.add(Pair(otherId, otherTotal))
                topSlices = slicesList
            }

            // D2 Largest remainder apportionment in bp so slices sum to exactly 10,000 bp
            val apportionedBp = ChartMath.apportionBasisPoints(topSlices)
            val totalSpentPaise = topSlices.sumOf { it.second }

            val slices = topSlices.map { (catId, amount) ->
                val bp = apportionedBp[catId] ?: 0
                CategoryDonutSlice(
                    categoryId = catId,
                    categoryName = categoryNameMap[catId] ?: "Unknown",
                    amountPaise = amount,
                    percentageBp = bp,
                    percentageFormatted = ChartMath.formatBasisPoints(bp)
                )
            }

            val contentDesc = buildString {
                append("Spending distribution across ").append(slices.size).append(" categories. ")
                append("Total spend: ").append(MoneyFormatter.formatPaiseToRupees(totalSpentPaise)).append(". ")
                slices.forEach {
                    append(it.categoryName).append(": ").append(it.percentageFormatted).append(" (").append(MoneyFormatter.formatPaiseToRupees(it.amountPaise)).append("). ")
                }
                if (netRefundCategories.isNotEmpty()) {
                    append("Net refunds: ").append(netRefundCategories.size).append(" categories.")
                }
            }

            ChartState.Data(
                CategoryDonutModel(
                    slices = slices,
                    refundCategories = netRefundCategories,
                    totalSpentPaise = totalSpentPaise,
                    contentDescription = contentDesc
                )
            )
        } catch (e: ArithmeticException) {
            ChartState.Error("Arithmetic overflow calculating category donut: ${e.message}")
        }
    }
}
