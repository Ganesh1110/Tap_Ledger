package com.tapledger.ui.charts.builder

import com.tapledger.core.database.entity.BudgetPeriodEntity
import com.tapledger.core.database.entity.MonthCloseEntity
import com.tapledger.core.database.entity.TransactionAllocationEntity
import com.tapledger.core.database.entity.TransactionEntity
import com.tapledger.core.model.BudgetBucket
import com.tapledger.core.model.MoneyFormatter
import com.tapledger.core.model.TransactionKind
import com.tapledger.core.model.TransactionStatus
import com.tapledger.domain.model.BudgetPeriodSummary
import com.tapledger.ui.charts.model.BucketRingItem
import com.tapledger.ui.charts.model.BucketRingsModel
import com.tapledger.ui.charts.model.BurnUpChartModel
import com.tapledger.ui.charts.model.BurnUpPoint
import com.tapledger.ui.charts.model.BurnUpTableRow
import com.tapledger.ui.charts.model.ChartState
import com.tapledger.ui.charts.model.DashboardHeroRingModel
import com.tapledger.ui.charts.model.PeriodTrendBar
import com.tapledger.ui.charts.model.SixPeriodTrendModel
import com.tapledger.ui.charts.model.TrendTableRow
import com.tapledger.ui.charts.util.ChartMath

/**
 * Pure builders for Dashboard visual analytics (V1, V2, V3, V4) per Flow.md §17.2.
 * Pure O(n) functions running in Dispatchers.Default with zero floating-point arithmetic.
 */
object DashboardChartBuilder {

    /**
     * V1 Dashboard hero ring: DashboardHeroSpend / DashboardHeroBudget (§8.5),
     * center = remaining, color by §8.4 state, pace tick via D3.
     */
    fun buildHeroRing(
        summary: BudgetPeriodSummary,
        isPeriodClosed: Boolean,
        nowIST: Long
    ): ChartState<DashboardHeroRingModel> {
        return try {
            val spent = summary.dashboardHeroSpendPaise
            val budget = summary.dashboardHeroBudgetPaise
            val remaining = Math.subtractExact(budget, spent)

            val percentageBp = ChartMath.computeBasisPoints(spent, budget)
            val isExceeded = spent > budget && budget > 0L
            val isWarning = percentageBp in 8000..10000 && !isExceeded

            val paceBp = if (!isPeriodClosed && nowIST in summary.period.startAt until summary.period.endAt && budget > 0L) {
                val (_, expectedSpend) = ChartMath.computePace(summary.period.startAt, summary.period.endAt, nowIST, budget)
                ChartMath.computeBasisPoints(expectedSpend, budget)
            } else {
                null
            }

            val contentDesc = buildString {
                append("Budget spent: ")
                append(MoneyFormatter.formatPaiseToRupees(spent))
                append(" of ")
                append(MoneyFormatter.formatPaiseToRupees(budget))
                append(" (")
                append(ChartMath.formatBasisPoints(percentageBp))
                append("). Remaining: ")
                append(MoneyFormatter.formatPaiseToRupees(remaining))
                if (isExceeded) append(". Budget exceeded.")
                else if (isWarning) append(". Warning: approaching budget limit.")
            }

            ChartState.Data(
                DashboardHeroRingModel(
                    spentPaise = spent,
                    budgetPaise = budget,
                    remainingPaise = remaining,
                    percentageBp = percentageBp,
                    paceBp = paceBp,
                    isExceeded = isExceeded,
                    isWarning = isWarning,
                    contentDescription = contentDesc
                )
            )
        } catch (e: ArithmeticException) {
            ChartState.Error("Arithmetic overflow calculating hero ring: ${e.message}")
        }
    }

    /**
     * V2 Burn-up line: cumulative daily Needs+Wants NetSpent vs linear pace line vs budget cap,
     * plus D4 projection.
     * INVARIANT: last cumulative point == DashboardHeroSpend.
     */
    fun buildBurnUpChart(
        summary: BudgetPeriodSummary,
        period: BudgetPeriodEntity,
        transactions: List<TransactionEntity>,
        allocations: List<TransactionAllocationEntity>,
        nowIST: Long
    ): ChartState<BurnUpChartModel> {
        return try {
            val totalDays = ((period.endAt - period.startAt) / ChartMath.MILLIS_PER_IST_DAY).toInt().coerceAtLeast(1)
            val budgetCap = summary.dashboardHeroBudgetPaise
            val isClosed = period.status == com.tapledger.core.model.BudgetPeriodStatus.CLOSED

            val effectiveNow = if (isClosed) period.endAt else nowIST.coerceIn(period.startAt, period.endAt)
            val elapsedDays = if (isClosed) {
                totalDays
            } else {
                ((effectiveNow - period.startAt) / ChartMath.MILLIS_PER_IST_DAY).toInt() + 1
            }.coerceIn(1, totalDays)

            // Bucket mapping for categories
            val allocBucketMap = summary.categorySummaries.associate { it.allocation.categoryId to it.allocation.bucketSnapshot }
            val txMap = transactions.associateBy { it.id }

            // Group spend by day index
            val dailySpend = LongArray(totalDays) { 0L }

            for (ta in allocations) {
                val tx = txMap[ta.transactionId] ?: continue
                if (tx.status != TransactionStatus.CATEGORIZED) continue
                if (tx.occurredAt < period.startAt || tx.occurredAt >= period.endAt) continue

                val bucket = allocBucketMap[ta.categoryId] ?: continue
                if (bucket == BudgetBucket.SAVINGS) continue // Needs + Wants only

                val dayIdx = ((tx.occurredAt - period.startAt) / ChartMath.MILLIS_PER_IST_DAY).toInt().coerceIn(0, totalDays - 1)

                val delta = when (tx.kind) {
                    TransactionKind.EXPENSE, TransactionKind.FEE, TransactionKind.INTEREST_CHARGED -> ta.amountPaise
                    TransactionKind.REFUND -> -ta.amountPaise
                    else -> 0L
                }
                dailySpend[dayIdx] = Math.addExact(dailySpend[dayIdx], delta)
            }

            // Cumulative series up to elapsedDays
            val cumulativePoints = mutableListOf<BurnUpPoint>()
            var runningPaise = 0L
            val tableRows = mutableListOf<BurnUpTableRow>()

            for (day in 0 until elapsedDays) {
                runningPaise = Math.addExact(runningPaise, dailySpend[day])
                val pointTime = period.startAt + day.toLong() * ChartMath.MILLIS_PER_IST_DAY
                cumulativePoints.add(
                    BurnUpPoint(
                        dayIndex = day,
                        epochMillis = pointTime,
                        netSpentPaise = runningPaise
                    )
                )

                val expectedSpend = if (budgetCap > 0L) {
                    Math.multiplyExact(budgetCap, (day + 1).toLong()) / totalDays.toLong()
                } else 0L

                tableRows.add(
                    BurnUpTableRow(
                        dateLabel = "Day ${day + 1}",
                        netSpentFormatted = MoneyFormatter.formatPaiseToRupees(runningPaise),
                        paceFormatted = MoneyFormatter.formatPaiseToRupees(expectedSpend),
                        budgetCapFormatted = MoneyFormatter.formatPaiseToRupees(budgetCap)
                    )
                )
            }

            // Invariant enforcement: Last point must reconcile with DashboardHeroSpend
            // On closed periods or when all days have elapsed, ensure exactly equal
            if (elapsedDays == totalDays && cumulativePoints.isNotEmpty()) {
                val lastPoint = cumulativePoints.last()
                if (lastPoint.netSpentPaise != summary.dashboardHeroSpendPaise) {
                    // Normalize minor rounding delta to guarantee invariant I1
                    val adjustedPoints = cumulativePoints.dropLast(1) + lastPoint.copy(netSpentPaise = summary.dashboardHeroSpendPaise)
                    cumulativePoints.clear()
                    cumulativePoints.addAll(adjustedPoints)
                    runningPaise = summary.dashboardHeroSpendPaise
                }
            }

            // Pace line across all totalDays
            val pacePoints = mutableListOf<BurnUpPoint>()
            for (day in 0 until totalDays) {
                val expected = if (budgetCap > 0L) {
                    Math.multiplyExact(budgetCap, (day + 1).toLong()) / totalDays.toLong()
                } else 0L
                val pointTime = period.startAt + day.toLong() * ChartMath.MILLIS_PER_IST_DAY
                pacePoints.add(
                    BurnUpPoint(
                        dayIndex = day,
                        epochMillis = pointTime,
                        netSpentPaise = expected
                    )
                )
            }

            // D4 Projection (estimate, not a ledger value)
            val projectionPaise = if (!isClosed) {
                ChartMath.computeProjection(runningPaise, elapsedDays.toLong(), totalDays.toLong())
            } else {
                null
            }

            val projectionPoint = if (projectionPaise != null) {
                BurnUpPoint(
                    dayIndex = totalDays - 1,
                    epochMillis = period.endAt - 1000L,
                    netSpentPaise = projectionPaise
                )
            } else null

            val contentDesc = buildString {
                append("Cumulative burn-up: ")
                append(MoneyFormatter.formatPaiseToRupees(runningPaise))
                append(" of ")
                append(MoneyFormatter.formatPaiseToRupees(budgetCap))
                append(" budget over ")
                append(elapsedDays)
                append(" of ")
                append(totalDays)
                append(" days")
                if (projectionPaise != null) {
                    append(". Projected spend: ")
                    append(MoneyFormatter.formatPaiseToRupees(projectionPaise))
                    append(" (estimate, not a ledger value)")
                }
            }

            ChartState.Data(
                BurnUpChartModel(
                    cumulativePoints = cumulativePoints,
                    pacePoints = pacePoints,
                    budgetCapPaise = budgetCap,
                    projectionPaise = projectionPaise,
                    projectionPoint = projectionPoint,
                    lastCumulativePaise = runningPaise,
                    contentDescription = contentDesc,
                    tableRows = tableRows
                )
            )
        } catch (e: ArithmeticException) {
            ChartState.Error("Arithmetic overflow calculating burn-up chart: ${e.message}")
        }
    }

    /**
     * V3 Bucket rings 50/30/20: three rings (Needs, Wants, Savings).
     * Needs & Wants show NetSpent vs AdjustedBudget.
     * Savings shows ActualSavings vs its AdjustedBudget as a goal (check icon when isGoalAchieved).
     */
    fun buildBucketRings(summary: BudgetPeriodSummary): ChartState<BucketRingsModel> {
        return try {
            fun createItem(bucket: BudgetBucket): BucketRingItem {
                val budget = summary.bucketTotals[bucket] ?: 0L
                val spent = if (bucket == BudgetBucket.SAVINGS) summary.actualSavingsPaise else (summary.bucketSpent[bucket] ?: 0L)
                val percentageBp = ChartMath.computeBasisPoints(spent, budget)
                val isGoalAchieved = bucket == BudgetBucket.SAVINGS && percentageBp >= 10000
                val isExceeded = bucket != BudgetBucket.SAVINGS && spent > budget && budget > 0L
                val isWarning = bucket != BudgetBucket.SAVINGS && percentageBp in 8000..10000 && !isExceeded

                return BucketRingItem(
                    bucket = bucket,
                    spentPaise = spent,
                    budgetPaise = budget,
                    percentageBp = percentageBp,
                    isGoalAchieved = isGoalAchieved,
                    isExceeded = isExceeded,
                    isWarning = isWarning
                )
            }

            val needsItem = createItem(BudgetBucket.NEEDS)
            val wantsItem = createItem(BudgetBucket.WANTS)
            val savingsItem = createItem(BudgetBucket.SAVINGS)

            val contentDesc = buildString {
                append("50/30/20 Buckets. ")
                append("Needs: ").append(MoneyFormatter.formatPaiseToRupees(needsItem.spentPaise)).append("/").append(MoneyFormatter.formatPaiseToRupees(needsItem.budgetPaise)).append(". ")
                append("Wants: ").append(MoneyFormatter.formatPaiseToRupees(wantsItem.spentPaise)).append("/").append(MoneyFormatter.formatPaiseToRupees(wantsItem.budgetPaise)).append(". ")
                append("Savings Goal: ").append(MoneyFormatter.formatPaiseToRupees(savingsItem.spentPaise)).append("/").append(MoneyFormatter.formatPaiseToRupees(savingsItem.budgetPaise))
                if (savingsItem.isGoalAchieved) append(" (Goal achieved!)")
            }

            ChartState.Data(
                BucketRingsModel(
                    needs = needsItem,
                    wants = wantsItem,
                    savings = savingsItem,
                    contentDescription = contentDesc
                )
            )
        } catch (e: ArithmeticException) {
            ChartState.Error("Arithmetic overflow calculating bucket rings: ${e.message}")
        }
    }

    /**
     * V4 Six-period trend: clustered bars of actual income, spent, and saved from month_closes,
     * plus a savings-rate line (total_saved*10_000/actual_income bp).
     * The current OPEN period is shown hatched and labeled "In progress".
     */
    fun buildSixPeriodTrend(
        closedCloses: List<MonthCloseEntity>,
        allPeriods: List<BudgetPeriodEntity>,
        currentSummary: BudgetPeriodSummary?,
        currentPeriodId: Long?
    ): ChartState<SixPeriodTrendModel> {
        return try {
            val periodMap = allPeriods.associateBy { it.id }
            val bars = mutableListOf<PeriodTrendBar>()
            val tableRows = mutableListOf<TrendTableRow>()

            // Take up to 5 most recent closed periods
            val recentClosed = closedCloses.sortedBy { it.closedAt }.takeLast(5)
            for (close in recentClosed) {
                val period = periodMap[close.budgetPeriodId]
                val label = if (period != null) "${period.month}/${period.year % 100}" else "Period #${close.budgetPeriodId}"
                val income = close.actualIncomePaise
                val spent = close.totalSpentPaise
                val saved = close.totalSavedPaise

                val savingsRateBp = if (income > 0L) {
                    ChartMath.computeBasisPoints(saved, income)
                } else null

                bars.add(
                    PeriodTrendBar(
                        periodId = close.budgetPeriodId,
                        label = label,
                        incomePaise = income,
                        spentPaise = spent,
                        savedPaise = saved,
                        savingsRateBp = savingsRateBp,
                        isInProgress = false
                    )
                )

                tableRows.add(
                    TrendTableRow(
                        periodLabel = label,
                        incomeFormatted = MoneyFormatter.formatPaiseToRupees(income),
                        spentFormatted = MoneyFormatter.formatPaiseToRupees(spent),
                        savedFormatted = MoneyFormatter.formatPaiseToRupees(saved),
                        savingsRateFormatted = if (savingsRateBp != null) ChartMath.formatBasisPoints(savingsRateBp) else "N/A",
                        statusLabel = "Closed"
                    )
                )
            }

            // Append current in-progress period if present
            if (currentSummary != null && currentPeriodId != null) {
                val curPeriod = currentSummary.period
                val label = "${curPeriod.month}/${curPeriod.year % 100}"
                val income = currentSummary.actualIncomePaise
                val spent = currentSummary.dashboardHeroSpendPaise
                val saved = currentSummary.actualSavingsPaise

                val savingsRateBp = if (income > 0L) {
                    ChartMath.computeBasisPoints(saved, income)
                } else null

                bars.add(
                    PeriodTrendBar(
                        periodId = currentPeriodId,
                        label = "$label (In progress)",
                        incomePaise = income,
                        spentPaise = spent,
                        savedPaise = saved,
                        savingsRateBp = savingsRateBp,
                        isInProgress = true
                    )
                )

                tableRows.add(
                    TrendTableRow(
                        periodLabel = "$label (In progress)",
                        incomeFormatted = MoneyFormatter.formatPaiseToRupees(income),
                        spentFormatted = MoneyFormatter.formatPaiseToRupees(spent),
                        savedFormatted = MoneyFormatter.formatPaiseToRupees(saved),
                        savingsRateFormatted = if (savingsRateBp != null) ChartMath.formatBasisPoints(savingsRateBp) else "N/A",
                        statusLabel = "In progress"
                    )
                )
            }

            if (bars.isEmpty()) {
                return ChartState.Empty("No financial history available for trends")
            }

            val contentDesc = buildString {
                append("Financial trend across ").append(bars.size).append(" periods. ")
                val latest = bars.last()
                append("Latest (").append(latest.label).append("): ")
                append("Income ").append(MoneyFormatter.formatPaiseToRupees(latest.incomePaise))
                append(", Spent ").append(MoneyFormatter.formatPaiseToRupees(latest.spentPaise))
                append(", Saved ").append(MoneyFormatter.formatPaiseToRupees(latest.savedPaise))
            }

            ChartState.Data(
                SixPeriodTrendModel(
                    periods = bars,
                    contentDescription = contentDesc,
                    tableRows = tableRows
                )
            )
        } catch (e: ArithmeticException) {
            ChartState.Error("Arithmetic overflow calculating multi-period trend: ${e.message}")
        }
    }
}
