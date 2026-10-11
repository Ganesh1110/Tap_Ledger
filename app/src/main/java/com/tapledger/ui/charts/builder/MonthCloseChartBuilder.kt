package com.tapledger.ui.charts.builder

import com.tapledger.core.model.MoneyFormatter
import com.tapledger.domain.monthclose.MonthCloseEngine
import com.tapledger.ui.charts.model.AdvanceRolloverBarItem
import com.tapledger.ui.charts.model.AdvanceRolloverChartModel
import com.tapledger.ui.charts.model.AdvanceRolloverTableRow
import com.tapledger.ui.charts.model.ChartState

/**
 * Pure builder for Month Close visual analytics (V11 Advance Rollover Bars) per Flow.md §17.2.
 */
object MonthCloseChartBuilder {

    /**
     * V11 Advance rollover bars: per-category diverging bars for carry (POSITIVE), sweep,
     * applied deficit (NEGATIVE), and uncovered pool (hatched).
     * Data comes from read-only MonthCloseEngine.preview(periodId).
     */
    fun buildAdvanceRolloverBars(
        previewResult: MonthCloseEngine.MonthClosePreviewResult
    ): ChartState<AdvanceRolloverChartModel> {
        return try {
            val barItems = previewResult.rows.map { row ->
                AdvanceRolloverBarItem(
                    categoryId = row.categoryId,
                    categoryName = row.categoryName,
                    carryPaise = row.carryPaise,
                    sweepPaise = row.sweepPaise,
                    appliedDeficitPaise = row.appliedDeficitPaise,
                    uncoveredPoolPaise = row.uncoveredPoolPaise
                )
            }

            if (barItems.isEmpty()) {
                return ChartState.Empty("No advance adjustments, deficits, or rollovers to carry forward")
            }

            val tableRows = barItems.map { item ->
                AdvanceRolloverTableRow(
                    categoryName = item.categoryName,
                    carryFormatted = MoneyFormatter.formatPaiseToRupees(item.carryPaise),
                    sweepFormatted = MoneyFormatter.formatPaiseToRupees(item.sweepPaise),
                    appliedDeficitFormatted = MoneyFormatter.formatPaiseToRupees(item.appliedDeficitPaise),
                    uncoveredPoolFormatted = MoneyFormatter.formatPaiseToRupees(item.uncoveredPoolPaise)
                )
            }

            val contentDesc = buildString {
                append("Advance rollover preview for ").append(barItems.size).append(" categories. ")
                val totalCarry = barItems.sumOf { it.carryPaise }
                val totalDeficit = barItems.sumOf { it.appliedDeficitPaise }
                val totalPool = barItems.sumOf { it.uncoveredPoolPaise }
                if (totalCarry > 0L) append("Carrying forward: ").append(MoneyFormatter.formatPaiseToRupees(totalCarry)).append(". ")
                if (totalDeficit > 0L) append("Applied deficits: ").append(MoneyFormatter.formatPaiseToRupees(totalDeficit)).append(". ")
                if (totalPool > 0L) append("Uncovered pool: ").append(MoneyFormatter.formatPaiseToRupees(totalPool)).append(".")
            }

            ChartState.Data(
                AdvanceRolloverChartModel(
                    rows = barItems,
                    contentDescription = contentDesc,
                    tableRows = tableRows
                )
            )
        } catch (e: ArithmeticException) {
            ChartState.Error("Arithmetic overflow calculating advance rollover preview: ${e.message}")
        }
    }
}
