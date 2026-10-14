package com.tapledger.ui.charts.builder

import com.tapledger.core.database.entity.AccountEntity
import com.tapledger.core.database.entity.BudgetPeriodEntity
import com.tapledger.core.database.entity.MonthCloseEntity
import com.tapledger.core.database.entity.TransactionEntity
import com.tapledger.core.model.AccountType
import com.tapledger.core.model.BudgetPeriodStatus
import com.tapledger.core.model.MoneyFormatter
import com.tapledger.core.model.TransactionStatus
import com.tapledger.core.util.DateTimeUtils
import com.tapledger.domain.calculator.AccountBalanceCalculator
import com.tapledger.ui.charts.model.AccountCompositionBarItem
import com.tapledger.ui.charts.model.AccountCompositionModel
import com.tapledger.ui.charts.model.ChartState
import com.tapledger.ui.charts.model.CreditCardUtilizationGaugeItem
import com.tapledger.ui.charts.model.NetWorthChartModel
import com.tapledger.ui.charts.model.NetWorthPoint
import com.tapledger.ui.charts.model.NetWorthTableRow
import com.tapledger.ui.charts.model.UtilizationZone
import com.tapledger.ui.charts.util.ChartMath
import java.time.YearMonth
import kotlin.math.abs

enum class NetWorthRange(val monthsCount: Int?) {
    M3(3),
    M6(6),
    Y1(12),
    ALL(null)
}

/**
 * Pure builders for Accounts visual analytics (V9 Net Worth, V10 Composition) per Flow.md §17.2.
 */
object AccountsChartBuilder {

    /**
     * V9 Net worth line/area: ranges 3M / 6M / 1Y / All.
     * Points are month-ends via existing Balance(A, asOf) plus live point for now.
     * INVARIANTS: final point == current NetWorth; closed period points == closing_balances_json totals.
     * Rows with account_id NULL are excluded and screen shows "N unassigned transactions excluded" when N > 0.
     */
    fun buildNetWorthChart(
        range: NetWorthRange,
        accounts: List<AccountEntity>,
        allTransactions: List<TransactionEntity>,
        monthCloses: List<MonthCloseEntity>,
        periods: List<BudgetPeriodEntity>,
        asOfNow: Long
    ): ChartState<NetWorthChartModel> {
        return try {
            val activeAccounts = accounts.filter { it.isActive }
            val unassignedCount = allTransactions.count {
                it.accountId == null && it.status != TransactionStatus.VOIDED && it.status != TransactionStatus.IGNORED
            }
            val unassignedMessage = if (unassignedCount > 0) "$unassignedCount unassigned transactions excluded" else null

            // Calculate live point for now
            val liveMap = mutableMapOf<Long, Long>()
            var currentAssets = 0L
            var currentLiabilities = 0L

            for (acc in activeAccounts) {
                val accTxs = allTransactions.filter { it.accountId == acc.id }
                val earliestTx = accTxs.filter { it.status != TransactionStatus.IGNORED && it.status != TransactionStatus.VOIDED }.minOfOrNull { it.occurredAt }
                val effectiveOpeningDate = if (earliestTx != null && earliestTx < acc.openingBalanceDate) {
                    DateTimeUtils.getStartOfDayEpochMillis(earliestTx)
                } else {
                    acc.openingBalanceDate
                }
                val effectiveAcc = acc.copy(openingBalanceDate = effectiveOpeningDate)
                val bal = AccountBalanceCalculator.calculateAccountBalance(effectiveAcc, accTxs, asOfEpochMillis = asOfNow).currentBalancePaise
                liveMap[acc.id] = bal
                if (acc.type == AccountType.CREDIT_CARD || acc.type == AccountType.LOAN) {
                    currentLiabilities = Math.addExact(currentLiabilities, abs(bal))
                } else {
                    currentAssets = Math.addExact(currentAssets, bal)
                }
            }
            val currentNetWorth = Math.subtractExact(currentAssets, currentLiabilities)

            // Map closed period points from month_closes closing_balances_json totals
            val closeMap = monthCloses.associateBy { it.budgetPeriodId }
            val currentYm = DateTimeUtils.toYearMonth(asOfNow)
            val closedPeriods = periods.filter { (it.status == BudgetPeriodStatus.CLOSED || it.endAt <= asOfNow) && it.endAt > 0L }
            val periodYms = closedPeriods.map { YearMonth.of(it.year, it.month) }

            val pastYms: List<YearMonth> = when (range) {
                NetWorthRange.M3 -> listOf(currentYm.minusMonths(2), currentYm.minusMonths(1))
                NetWorthRange.M6 -> (5 downTo 1).map { currentYm.minusMonths(it.toLong()) }
                NetWorthRange.Y1 -> (11 downTo 1).map { currentYm.minusMonths(it.toLong()) }
                NetWorthRange.ALL -> {
                    val earliestTx = allTransactions
                        .filter { it.status != TransactionStatus.IGNORED && it.status != TransactionStatus.VOIDED }
                        .minOfOrNull { it.occurredAt }
                    val earliestAcc = activeAccounts.minOfOrNull { it.openingBalanceDate }
                    val earliestPeriod = periods.minOfOrNull { it.startAt }
                    val earliestEpoch = listOfNotNull(earliestTx, earliestAcc, earliestPeriod).minOrNull() ?: asOfNow
                    val earliestYm = DateTimeUtils.toYearMonth(earliestEpoch)

                    val startYm = if (earliestYm.isBefore(currentYm)) earliestYm else currentYm.minusMonths(1)
                    val yms = mutableListOf<YearMonth>()
                    var curr = startYm
                    while (!curr.isAfter(currentYm.minusMonths(1))) {
                        yms.add(curr)
                        curr = curr.plusMonths(1)
                    }
                    (yms + periodYms).distinct().sorted()
                }
            }

            val points = mutableListOf<NetWorthPoint>()
            val tableRows = mutableListOf<NetWorthTableRow>()

            for (ym in pastYms) {
                val periodForYm = periods.find { it.year == ym.year && it.month == ym.monthValue }
                val pointTime = periodForYm?.endAt ?: (DateTimeUtils.getEndOfMonthEpochMillis(ym.year, ym.monthValue) - 1L)
                val label = "${ym.monthValue}/${ym.year % 100}"

                val close = periodForYm?.let { closeMap[it.id] }
                val (assets, liabilities, nw) = if (close != null && close.closingBalancesJson.isNotBlank()) {
                    val balanceMap = parseClosingBalancesJson(close.closingBalancesJson)
                    var a = 0L
                    var l = 0L
                    for (acc in activeAccounts) {
                        val accBal = balanceMap[acc.id] ?: 0L
                        if (acc.type == AccountType.CREDIT_CARD || acc.type == AccountType.LOAN) {
                            l = Math.addExact(l, abs(accBal))
                        } else {
                            a = Math.addExact(a, accBal)
                        }
                    }
                    Triple(a, l, Math.subtractExact(a, l))
                } else {
                    // Historical calculation via AccountBalanceCalculator
                    var a = 0L
                    var l = 0L
                    for (acc in activeAccounts) {
                        val accTxs = allTransactions.filter { it.accountId == acc.id }
                        val earliestTx = accTxs.filter { it.status != TransactionStatus.IGNORED && it.status != TransactionStatus.VOIDED }.minOfOrNull { it.occurredAt }
                        val effectiveOpeningDate = if (earliestTx != null && earliestTx < acc.openingBalanceDate) {
                            DateTimeUtils.getStartOfDayEpochMillis(earliestTx)
                        } else {
                            acc.openingBalanceDate
                        }
                        val effectiveAcc = acc.copy(openingBalanceDate = effectiveOpeningDate)
                        val openStartOfDay = DateTimeUtils.getStartOfDayEpochMillis(effectiveAcc.openingBalanceDate)
                        val bal = if (pointTime < openStartOfDay) {
                            0L
                        } else {
                            AccountBalanceCalculator.calculateAccountBalance(effectiveAcc, accTxs, asOfEpochMillis = pointTime).currentBalancePaise
                        }
                        if (acc.type == AccountType.CREDIT_CARD || acc.type == AccountType.LOAN) {
                            l = Math.addExact(l, abs(bal))
                        } else {
                            a = Math.addExact(a, bal)
                        }
                    }
                    Triple(a, l, Math.subtractExact(a, l))
                }

                points.add(
                    NetWorthPoint(
                        timestamp = pointTime,
                        label = label,
                        netWorthPaise = nw,
                        assetsPaise = assets,
                        liabilitiesPaise = liabilities
                    )
                )

                tableRows.add(
                    NetWorthTableRow(
                        dateLabel = label,
                        netWorthFormatted = MoneyFormatter.formatPaiseToRupees(nw),
                        assetsFormatted = MoneyFormatter.formatPaiseToRupees(assets),
                        liabilitiesFormatted = MoneyFormatter.formatPaiseToRupees(liabilities)
                    )
                )
            }

            // Always append live point for now as final point
            points.add(
                NetWorthPoint(
                    timestamp = asOfNow,
                    label = "Now",
                    netWorthPaise = currentNetWorth,
                    assetsPaise = currentAssets,
                    liabilitiesPaise = currentLiabilities
                )
            )

            tableRows.add(
                NetWorthTableRow(
                    dateLabel = "Now",
                    netWorthFormatted = MoneyFormatter.formatPaiseToRupees(currentNetWorth),
                    assetsFormatted = MoneyFormatter.formatPaiseToRupees(currentAssets),
                    liabilitiesFormatted = MoneyFormatter.formatPaiseToRupees(currentLiabilities)
                )
            )

            val contentDesc = buildString {
                append("Net worth history: ").append(points.size).append(" data points. ")
                append("Current Net Worth: ").append(MoneyFormatter.formatPaiseToRupees(currentNetWorth)).append(" (")
                append("Assets: ").append(MoneyFormatter.formatPaiseToRupees(currentAssets)).append(", ")
                append("Liabilities: ").append(MoneyFormatter.formatPaiseToRupees(currentLiabilities)).append(").")
                if (unassignedMessage != null) append(" ").append(unassignedMessage).append(".")
            }

            ChartState.Data(
                NetWorthChartModel(
                    points = points,
                    currentNetWorthPaise = currentNetWorth,
                    unassignedCount = unassignedCount,
                    unassignedExcludedMessage = unassignedMessage,
                    contentDescription = contentDesc,
                    tableRows = tableRows
                )
            )
        } catch (e: ArithmeticException) {
            ChartState.Error("Arithmetic overflow calculating net worth chart: ${e.message}")
        }
    }

    /**
     * V10 Account composition: diverging bars per account (assets right, liabilities left, sorted by |balance|)
     * and credit-card utilization gauge using §6.2 Utilization% formula.
     * Invariant I6: sum(assets) - sum(liabilities) == NetWorth.
     */
    fun buildAccountComposition(
        accounts: List<AccountEntity>,
        allTransactions: List<TransactionEntity>,
        asOfNow: Long
    ): ChartState<AccountCompositionModel> {
        return try {
            val activeAccounts = accounts.filter { it.isActive }
            val unassignedCount = allTransactions.count {
                it.accountId == null && it.status != TransactionStatus.VOIDED && it.status != TransactionStatus.IGNORED
            }
            val unassignedMessage = if (unassignedCount > 0) "$unassignedCount unassigned transactions excluded" else null

            var totalAssets = 0L
            var totalLiabilities = 0L

            val barItems = mutableListOf<AccountCompositionBarItem>()
            val ccGauges = mutableListOf<CreditCardUtilizationGaugeItem>()

            for (acc in activeAccounts) {
                val accTxs = allTransactions.filter { it.accountId == acc.id }
                val earliestTx = accTxs.filter { it.status != TransactionStatus.IGNORED && it.status != TransactionStatus.VOIDED }.minOfOrNull { it.occurredAt }
                val effectiveOpeningDate = if (earliestTx != null && earliestTx < acc.openingBalanceDate) {
                    DateTimeUtils.getStartOfDayEpochMillis(earliestTx)
                } else {
                    acc.openingBalanceDate
                }
                val effectiveAcc = acc.copy(openingBalanceDate = effectiveOpeningDate)
                val bal = AccountBalanceCalculator.calculateAccountBalance(effectiveAcc, accTxs, asOfEpochMillis = asOfNow).currentBalancePaise
                val isLiability = acc.type == AccountType.CREDIT_CARD || acc.type == AccountType.LOAN
                val isAsset = !isLiability

                if (isAsset) {
                    totalAssets = Math.addExact(totalAssets, bal)
                } else {
                    totalLiabilities = Math.addExact(totalLiabilities, abs(bal))
                }

                barItems.add(
                    AccountCompositionBarItem(
                        accountId = acc.id,
                        accountName = acc.name,
                        balancePaise = bal,
                        isAsset = isAsset,
                        absBalancePaise = abs(bal)
                    )
                )

                // CC Utilization Gauge (§6.2)
                if (acc.type == AccountType.CREDIT_CARD) {
                    val limit = acc.creditLimitPaise ?: 0L
                    val utilBp = if (limit > 0L) {
                        ChartMath.computeBasisPoints(abs(bal), limit)
                    } else 0

                    val zone = when {
                        utilBp < 3000 -> UtilizationZone.NORMAL
                        utilBp <= 7000 -> UtilizationZone.CAUTION
                        else -> UtilizationZone.HIGH
                    }

                    ccGauges.add(
                        CreditCardUtilizationGaugeItem(
                            accountId = acc.id,
                            accountName = acc.name,
                            balancePaise = bal,
                            creditLimitPaise = limit,
                            utilizationBp = utilBp,
                            zone = zone
                        )
                    )
                }
            }

            val netWorth = Math.subtractExact(totalAssets, totalLiabilities)
            // Sort by abs(balance) descending
            val sortedBarItems = barItems.sortedByDescending { it.absBalancePaise }

            val contentDesc = buildString {
                append("Account Composition: ").append(sortedBarItems.size).append(" accounts. ")
                append("Total Assets: ").append(MoneyFormatter.formatPaiseToRupees(totalAssets)).append(", ")
                append("Total Liabilities: ").append(MoneyFormatter.formatPaiseToRupees(totalLiabilities)).append(", ")
                append("Net Worth: ").append(MoneyFormatter.formatPaiseToRupees(netWorth)).append(".")
                if (unassignedMessage != null) append(" ").append(unassignedMessage).append(".")
            }

            ChartState.Data(
                AccountCompositionModel(
                    accounts = sortedBarItems,
                    totalAssetsPaise = totalAssets,
                    totalLiabilitiesPaise = totalLiabilities,
                    netWorthPaise = netWorth,
                    creditCardGauges = ccGauges,
                    unassignedCount = unassignedCount,
                    unassignedExcludedMessage = unassignedMessage,
                    contentDescription = contentDesc
                )
            )
        } catch (e: ArithmeticException) {
            ChartState.Error("Arithmetic overflow calculating account composition: ${e.message}")
        }
    }

    private fun parseClosingBalancesJson(jsonStr: String): Map<Long, Long> {
        val map = mutableMapOf<Long, Long>()
        val clean = jsonStr.trim().removePrefix("{").removeSuffix("}").trim()
        if (clean.isEmpty()) return map
        val pairs = clean.split(",")
        for (pair in pairs) {
            val parts = pair.split(":")
            if (parts.size == 2) {
                val key = parts[0].trim().replace("\"", "").toLongOrNull()
                val value = parts[1].trim().toLongOrNull()
                if (key != null && value != null) {
                    map[key] = value
                }
            }
        }
        return map
    }
}
