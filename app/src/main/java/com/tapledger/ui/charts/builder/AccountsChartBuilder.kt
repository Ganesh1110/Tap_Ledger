package com.tapledger.ui.charts.builder

import com.tapledger.core.database.entity.AccountEntity
import com.tapledger.core.database.entity.BudgetPeriodEntity
import com.tapledger.core.database.entity.MonthCloseEntity
import com.tapledger.core.database.entity.TransactionEntity
import com.tapledger.core.model.AccountType
import com.tapledger.core.model.MoneyFormatter
import com.tapledger.core.model.TransactionStatus
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
import org.json.JSONObject
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
                val bal = AccountBalanceCalculator.calculateAccountBalance(acc, accTxs, asOfEpochMillis = asOfNow).currentBalancePaise
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
            val sortedPeriods = periods.sortedBy { it.startAt }

            val filteredPeriods = when (range) {
                NetWorthRange.M3 -> sortedPeriods.takeLast(3)
                NetWorthRange.M6 -> sortedPeriods.takeLast(6)
                NetWorthRange.Y1 -> sortedPeriods.takeLast(12)
                NetWorthRange.ALL -> sortedPeriods
            }

            val points = mutableListOf<NetWorthPoint>()
            val tableRows = mutableListOf<NetWorthTableRow>()

            for (p in filteredPeriods) {
                val close = closeMap[p.id]
                val pointTime = p.endAt
                val label = "${p.month}/${p.year % 100}"

                val (assets, liabilities, nw) = if (close != null && close.closingBalancesJson.isNotBlank()) {
                    // Extract from closing_balances_json
                    var a = 0L
                    var l = 0L
                    try {
                        val json = JSONObject(close.closingBalancesJson)
                        for (acc in activeAccounts) {
                            val accBal = json.optLong(acc.id.toString(), 0L)
                            if (acc.type == AccountType.CREDIT_CARD || acc.type == AccountType.LOAN) {
                                l = Math.addExact(l, abs(accBal))
                            } else {
                                a = Math.addExact(a, accBal)
                            }
                        }
                    } catch (_: Exception) {
                        // Fallback to calculation
                        for (acc in activeAccounts) {
                            val accTxs = allTransactions.filter { it.accountId == acc.id }
                            val bal = AccountBalanceCalculator.calculateAccountBalance(acc, accTxs, asOfEpochMillis = pointTime).currentBalancePaise
                            if (acc.type == AccountType.CREDIT_CARD || acc.type == AccountType.LOAN) {
                                l = Math.addExact(l, abs(bal))
                            } else {
                                a = Math.addExact(a, bal)
                            }
                        }
                    }
                    Triple(a, l, Math.subtractExact(a, l))
                } else {
                    // Historical calculation via AccountBalanceCalculator
                    var a = 0L
                    var l = 0L
                    for (acc in activeAccounts) {
                        val accTxs = allTransactions.filter { it.accountId == acc.id }
                        val bal = AccountBalanceCalculator.calculateAccountBalance(acc, accTxs, asOfEpochMillis = pointTime).currentBalancePaise
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
                val bal = AccountBalanceCalculator.calculateAccountBalance(acc, accTxs, asOfEpochMillis = asOfNow).currentBalancePaise
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
}
