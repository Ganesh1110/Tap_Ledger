package com.tapledger.ui.charts.builder

import com.tapledger.core.database.entity.BudgetPeriodEntity
import com.tapledger.core.database.entity.TransactionAllocationEntity
import com.tapledger.core.database.entity.TransactionEntity
import com.tapledger.core.model.BudgetBucket
import com.tapledger.core.model.MoneyFormatter
import com.tapledger.core.model.TransactionDirection
import com.tapledger.core.model.TransactionKind
import com.tapledger.core.model.TransactionStatus
import com.tapledger.ui.charts.model.ChartState
import com.tapledger.ui.charts.model.HeatmapDayCell
import com.tapledger.ui.charts.model.SpendingHeatmapModel
import com.tapledger.ui.charts.model.TopMerchantItem
import com.tapledger.ui.charts.model.TopMerchantsModel
import com.tapledger.ui.charts.util.ChartMath

/**
 * Pure builders for Insights visual analytics (V7 Spending Calendar Heatmap, V8 Top Merchants) per Flow.md §17.2.
 */
object InsightsChartBuilder {

    /**
     * V7 Spending calendar heatmap: daily Needs+Wants NetSpent in 5 intensity levels by integer quantile.
     */
    fun buildSpendingHeatmap(
        period: BudgetPeriodEntity,
        transactions: List<TransactionEntity>,
        allocations: List<TransactionAllocationEntity>,
        categoryBuckets: Map<Long, BudgetBucket>
    ): ChartState<SpendingHeatmapModel> {
        return try {
            val totalDays = ((period.endAt - period.startAt) / ChartMath.MILLIS_PER_IST_DAY).toInt().coerceAtLeast(1)
            val txMap = transactions.associateBy { it.id }

            val dailySpend = LongArray(totalDays) { 0L }

            for (ta in allocations) {
                val tx = txMap[ta.transactionId] ?: continue
                if (tx.status != TransactionStatus.CATEGORIZED) continue
                if (tx.occurredAt < period.startAt || tx.occurredAt >= period.endAt) continue

                val bucket = categoryBuckets[ta.categoryId] ?: continue
                if (bucket == BudgetBucket.SAVINGS) continue // Needs + Wants only

                val dayIdx = ((tx.occurredAt - period.startAt) / ChartMath.MILLIS_PER_IST_DAY).toInt().coerceIn(0, totalDays - 1)
                val delta = when (tx.kind) {
                    TransactionKind.EXPENSE, TransactionKind.FEE, TransactionKind.INTEREST_CHARGED -> ta.amountPaise
                    TransactionKind.REFUND -> -ta.amountPaise
                    else -> 0L
                }
                dailySpend[dayIdx] = Math.addExact(dailySpend[dayIdx], delta)
            }

            // Integer quantile calculation over positive spend days
            val positiveSpends = dailySpend.filter { it > 0L }.sorted()
            val positiveCount = positiveSpends.size

            fun getIntensityLevel(spend: Long): Int {
                if (spend <= 0L || positiveCount == 0) return 0
                val rank = positiveSpends.indexOfFirst { it >= spend }
                if (rank < 0) return 4
                // 4 quantiles for positive days: 1, 2, 3, 4
                return (1 + (rank * 4) / positiveCount).coerceIn(1, 4)
            }

            val cells = mutableListOf<HeatmapDayCell>()
            var totalSpent = 0L
            var activeDays = 0

            for (day in 0 until totalDays) {
                val spend = dailySpend[day]
                if (spend > 0L) {
                    totalSpent = Math.addExact(totalSpent, spend)
                    activeDays++
                }
                val epoch = period.startAt + day.toLong() * ChartMath.MILLIS_PER_IST_DAY
                val dayOfMonth = day + 1
                val intensity = getIntensityLevel(spend)

                cells.add(
                    HeatmapDayCell(
                        epochMillis = epoch,
                        dayOfMonth = dayOfMonth,
                        netSpentPaise = spend,
                        intensityLevel = intensity
                    )
                )
            }

            val contentDesc = buildString {
                append("Spending Calendar Heatmap: ").append(totalDays).append(" days, ")
                append(activeDays).append(" active spending days. ")
                append("Total spend: ").append(MoneyFormatter.formatPaiseToRupees(totalSpent))
            }

            ChartState.Data(
                SpendingHeatmapModel(
                    cells = cells,
                    totalSpentPaise = totalSpent,
                    activeDaysCount = activeDays,
                    contentDescription = contentDesc
                )
            )
        } catch (e: ArithmeticException) {
            ChartState.Error("Arithmetic overflow calculating spending heatmap: ${e.message}")
        }
    }

    /**
     * V8 Top merchants: top 8 by CATEGORIZED expense, grouped by normalized merchant key.
     */
    fun buildTopMerchants(transactions: List<TransactionEntity>): ChartState<TopMerchantsModel> {
        return try {
            // Group by merchant key for CATEGORIZED debit expenses
            val eligibleTxs = transactions.filter {
                it.status == TransactionStatus.CATEGORIZED &&
                it.direction == TransactionDirection.OUT &&
                (it.kind == TransactionKind.EXPENSE || it.kind == TransactionKind.FEE || it.kind == TransactionKind.INTEREST_CHARGED)
            }

            if (eligibleTxs.isEmpty()) {
                return ChartState.Data(
                    TopMerchantsModel(
                        merchants = emptyList(),
                        totalCategorizedExpensePaise = 0L,
                        contentDescription = "No categorized expenses found"
                    )
                )
            }

            val totalCategorizedExpense = eligibleTxs.sumOf { it.amountPaise }

            val grouped = eligibleTxs.groupBy {
                it.merchantNormalized ?: it.merchantRaw ?: "Unknown Merchant"
            }

            val merchantTotals = grouped.map { (name, txs) ->
                val spent = txs.sumOf { it.amountPaise }
                val count = txs.size
                Triple(name, spent, count)
            }.sortedByDescending { it.second }.take(8)

            val merchants = merchantTotals.map { (name, spent, count) ->
                val bp = ChartMath.computeBasisPoints(spent, totalCategorizedExpense)
                TopMerchantItem(
                    merchantName = name,
                    totalSpentPaise = spent,
                    transactionCount = count,
                    percentageBp = bp,
                    amountFormatted = MoneyFormatter.formatPaiseToRupees(spent)
                )
            }

            val contentDesc = buildString {
                append("Top ").append(merchants.size).append(" merchants. ")
                append("Total spend: ").append(MoneyFormatter.formatPaiseToRupees(totalCategorizedExpense)).append(". ")
                merchants.forEach {
                    append(it.merchantName).append(": ").append(it.amountFormatted).append(" (").append(ChartMath.formatBasisPoints(it.percentageBp)).append("). ")
                }
            }

            ChartState.Data(
                TopMerchantsModel(
                    merchants = merchants,
                    totalCategorizedExpensePaise = totalCategorizedExpense,
                    contentDescription = contentDesc
                )
            )
        } catch (e: ArithmeticException) {
            ChartState.Error("Arithmetic overflow calculating top merchants: ${e.message}")
        }
    }
}
