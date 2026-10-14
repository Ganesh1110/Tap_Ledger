package com.tapledger.ui.screens.accounts

import com.google.common.truth.Truth.assertThat
import com.tapledger.core.database.entity.AccountEntity
import com.tapledger.core.database.entity.BudgetPeriodEntity
import com.tapledger.core.database.entity.MonthCloseEntity
import com.tapledger.core.database.entity.TransactionEntity
import com.tapledger.core.model.AccountType
import com.tapledger.core.model.BudgetPeriodStatus
import com.tapledger.core.model.MonthCloseReconciliationStatus
import com.tapledger.core.model.TransactionDirection
import com.tapledger.core.model.TransactionKind
import com.tapledger.core.model.TransactionSource
import com.tapledger.core.model.TransactionStatus
import com.tapledger.ui.charts.builder.AccountsChartBuilder
import com.tapledger.ui.charts.builder.NetWorthRange
import com.tapledger.ui.charts.model.ChartState
import com.tapledger.ui.charts.model.UtilizationZone
import org.junit.Test

/**
 * Named test suite proving V9 (Net Worth line/area) and V10 (Account composition & CC gauge)
 * visual requirements on the Accounts screen per Flow.md §17.2 and §17.7.
 */
class AccountsVisualsTest {

    private val startAt = 1_700_000_000_000L
    private val dayMs = 86_400_000L

    private val bankAccount = AccountEntity(
        id = 1L,
        name = "HDFC Bank",
        type = AccountType.BANK,
        identifierLast4 = null,
        openingBalancePaise = 50_000_00L,
        openingBalanceDate = startAt - 100 * dayMs,
        creditLimitPaise = null,
        isActive = true,
        createdAt = startAt,
        updatedAt = startAt
    )

    private val creditCard = AccountEntity(
        id = 2L,
        name = "ICICI Credit Card",
        type = AccountType.CREDIT_CARD,
        identifierLast4 = null,
        openingBalancePaise = 0L,
        openingBalanceDate = startAt - 100 * dayMs,
        creditLimitPaise = 100_000_00L, // 1 Lakh limit
        isActive = true,
        createdAt = startAt,
        updatedAt = startAt
    )

    private val cashWallet = AccountEntity(
        id = 3L,
        name = "Cash",
        type = AccountType.CASH,
        identifierLast4 = null,
        openingBalancePaise = 5_000_00L,
        openingBalanceDate = startAt - 100 * dayMs,
        creditLimitPaise = null,
        isActive = true,
        createdAt = startAt,
        updatedAt = startAt
    )

    // =========================================================================
    // V9: Net Worth Line / Area (S_Acct)
    // =========================================================================

    @Test
    fun testV9_NetWorthChart_FinalPointMatchesCurrentNetWorthInvariant() {
        val accounts = listOf(bankAccount, creditCard, cashWallet)
        // Bank = 50,000 + 10,000 = 60,000. Cash = 5,000. CC = -25,000.
        // Total Assets = 65,000. Total Liabilities = 25,000. Net Worth = 40,000.
        val txBank = TransactionEntity(
            id = 1L,
            accountId = 1L,
            kind = TransactionKind.INCOME,
            direction = TransactionDirection.IN,
            amountPaise = 10_000_00L,
            status = TransactionStatus.CONFIRMED,
            source = TransactionSource.MANUAL,
            occurredAt = startAt + 1000L,
            createdAt = startAt,
            updatedAt = startAt
        )
        val txCard = TransactionEntity(
            id = 2L,
            accountId = 2L,
            kind = TransactionKind.EXPENSE,
            direction = TransactionDirection.OUT,
            amountPaise = 25_000_00L,
            status = TransactionStatus.CONFIRMED,
            source = TransactionSource.MANUAL,
            occurredAt = startAt + 2000L,
            createdAt = startAt,
            updatedAt = startAt
        )
        val txs = listOf(txBank, txCard)

        val period = BudgetPeriodEntity(
            id = 1L,
            year = 2026,
            month = 10,
            startAt = startAt,
            endAt = startAt + 30 * dayMs,
            incomePaise = 10_000_00L,
            status = BudgetPeriodStatus.OPEN,
            isReviewed = true,
            createdAt = startAt,
            updatedAt = startAt
        )

        val nowIST = startAt + 15 * dayMs
        val result = AccountsChartBuilder.buildNetWorthChart(
            range = NetWorthRange.ALL,
            accounts = accounts,
            allTransactions = txs,
            monthCloses = emptyList(),
            periods = listOf(period),
            asOfNow = nowIST
        )

        assertThat(result).isInstanceOf(ChartState.Data::class.java)
        val data = (result as ChartState.Data).data

        // Invariant I5: Final point timestamp == asOfNow, netWorthPaise == currentNetWorth
        val finalPoint = data.points.last()
        assertThat(finalPoint.label).isEqualTo("Now")
        assertThat(finalPoint.timestamp).isEqualTo(nowIST)
        assertThat(finalPoint.netWorthPaise).isEqualTo(40_000_00L)
        assertThat(finalPoint.assetsPaise).isEqualTo(65_000_00L)
        assertThat(finalPoint.liabilitiesPaise).isEqualTo(25_000_00L)
        assertThat(data.currentNetWorthPaise).isEqualTo(40_000_00L)
    }

    @Test
    fun testV9_NetWorthChart_ClosedPeriodPointMatchesClosingBalancesJsonTotals() {
        val accounts = listOf(bankAccount, creditCard)
        val period1 = BudgetPeriodEntity(
            id = 1L,
            year = 2026,
            month = 9,
            startAt = startAt - 30 * dayMs,
            endAt = startAt,
            incomePaise = 50_000_00L,
            status = BudgetPeriodStatus.CLOSED,
            isReviewed = true,
            createdAt = startAt - 30 * dayMs,
            updatedAt = startAt
        )

        // Month close snapshot: Bank = 55,000, Card = -10,000. Total Assets = 55,000, Liabilities = 10,000, NW = 45,000.
        val closingJson = """{"1":5500000,"2":-1000000}"""

        val monthClose = MonthCloseEntity(
            id = 1L,
            budgetPeriodId = 1L,
            closedAt = startAt,
            plannedIncomePaise = 50_000_00L,
            actualIncomePaise = 50_000_00L,
            totalBudgetedPaise = 50_000_00L,
            totalSpentPaise = 30_000_00L,
            totalSavedPaise = 20_000_00L,
            closingBalancesJson = closingJson,
            reconciliationStatus = MonthCloseReconciliationStatus.RECONCILED
        )

        val result = AccountsChartBuilder.buildNetWorthChart(
            range = NetWorthRange.ALL,
            accounts = accounts,
            allTransactions = emptyList(),
            monthCloses = listOf(monthClose),
            periods = listOf(period1),
            asOfNow = startAt + 5 * dayMs
        )

        assertThat(result).isInstanceOf(ChartState.Data::class.java)
        val data = (result as ChartState.Data).data

        // Closed period point must equal closingBalancesJson totals exactly (Flow.md §17.2 V9 Invariant)
        val closedPoint = data.points.first { it.label == "9/26" }
        assertThat(closedPoint.assetsPaise).isEqualTo(55_000_00L)
        assertThat(closedPoint.liabilitiesPaise).isEqualTo(10_000_00L)
        assertThat(closedPoint.netWorthPaise).isEqualTo(45_000_00L)

        // Table rows representation must match
        val tableRow = data.tableRows.first { it.dateLabel == "9/26" }
        assertThat(tableRow.netWorthFormatted).isEqualTo("₹45,000.00")
        assertThat(tableRow.assetsFormatted).isEqualTo("₹55,000.00")
        assertThat(tableRow.liabilitiesFormatted).isEqualTo("₹10,000.00")
    }

    @Test
    fun testV9_NetWorthChart_ExcludesUnassignedTransactionsWithMessage() {
        val accounts = listOf(bankAccount)
        // One unassigned transaction (account_id == null)
        val unassignedTx = TransactionEntity(
            id = 99L,
            accountId = null,
            kind = TransactionKind.EXPENSE,
            direction = TransactionDirection.OUT,
            amountPaise = 5_000_00L,
            status = TransactionStatus.CATEGORIZED,
            source = TransactionSource.SMS,
            occurredAt = startAt + 1000L,
            createdAt = startAt,
            updatedAt = startAt
        )

        val result = AccountsChartBuilder.buildNetWorthChart(
            range = NetWorthRange.M3,
            accounts = accounts,
            allTransactions = listOf(unassignedTx),
            monthCloses = emptyList(),
            periods = emptyList(),
            asOfNow = startAt + 5000L
        )

        assertThat(result).isInstanceOf(ChartState.Data::class.java)
        val data = (result as ChartState.Data).data

        assertThat(data.unassignedCount).isEqualTo(1)
        assertThat(data.unassignedExcludedMessage).isEqualTo("1 unassigned transactions excluded")
    }

    @Test
    fun testV9_NetWorthChart_RangesGenerateDistinctPointCounts() {
        val accounts = listOf(bankAccount)
        val nowIST = startAt + 15 * dayMs

        val r3m = AccountsChartBuilder.buildNetWorthChart(NetWorthRange.M3, accounts, emptyList(), emptyList(), emptyList(), nowIST)
        val r6m = AccountsChartBuilder.buildNetWorthChart(NetWorthRange.M6, accounts, emptyList(), emptyList(), emptyList(), nowIST)
        val r1y = AccountsChartBuilder.buildNetWorthChart(NetWorthRange.Y1, accounts, emptyList(), emptyList(), emptyList(), nowIST)

        val d3m = (r3m as ChartState.Data).data
        val d6m = (r6m as ChartState.Data).data
        val d1y = (r1y as ChartState.Data).data

        // 3M = 2 past + Now = 3 points
        assertThat(d3m.points).hasSize(3)
        assertThat(d3m.points.last().label).isEqualTo("Now")

        // 6M = 5 past + Now = 6 points
        assertThat(d6m.points).hasSize(6)
        assertThat(d6m.points.last().label).isEqualTo("Now")

        // 1Y = 11 past + Now = 12 points
        assertThat(d1y.points).hasSize(12)
        assertThat(d1y.points.last().label).isEqualTo("Now")

        // Strictly monotonic timestamps in all ranges
        for (points in listOf(d3m.points, d6m.points, d1y.points)) {
            for (i in 0 until points.size - 1) {
                assertThat(points[i].timestamp).isLessThan(points[i + 1].timestamp)
            }
        }
    }

    @Test
    fun testV9_NetWorthChart_IncorporatesBackdatedTransactionsWithEffectiveAccount() {
        // Account opening balance date is startAt (e.g. today)
        val acc = bankAccount.copy(openingBalancePaise = 2_432_72L, openingBalanceDate = startAt)
        // Backdated transaction 20 days before startAt
        val backdatedTx = TransactionEntity(
            id = 10L,
            accountId = acc.id,
            kind = TransactionKind.INCOME,
            direction = TransactionDirection.IN,
            amountPaise = 10_000_00L,
            status = TransactionStatus.CONFIRMED,
            source = TransactionSource.MANUAL,
            occurredAt = startAt - 20 * dayMs,
            createdAt = startAt,
            updatedAt = startAt
        )

        val result = AccountsChartBuilder.buildNetWorthChart(
            range = NetWorthRange.M3,
            accounts = listOf(acc),
            allTransactions = listOf(backdatedTx),
            monthCloses = emptyList(),
            periods = emptyList(),
            asOfNow = startAt + 2 * dayMs
        )

        assertThat(result).isInstanceOf(ChartState.Data::class.java)
        val data = (result as ChartState.Data).data
        val nowPoint = data.points.last()

        // 2,432.72 + 10,000.00 = 12,432.72
        assertThat(nowPoint.netWorthPaise).isEqualTo(12_432_72L)
        assertThat(nowPoint.assetsPaise).isEqualTo(12_432_72L)
    }

    // =========================================================================
    // V10: Account Composition & Credit Card Utilization (S_Acct)
    // =========================================================================

    @Test
    fun testV10_AccountComposition_DivergingBarsAndInvariantI6() {
        val accounts = listOf(bankAccount, creditCard, cashWallet)
        // Bank = 50,000; Cash = 5,000; CC = -35,000
        val txCard = TransactionEntity(
            id = 1L,
            accountId = 2L,
            kind = TransactionKind.EXPENSE,
            direction = TransactionDirection.OUT,
            amountPaise = 35_000_00L,
            status = TransactionStatus.CONFIRMED,
            source = TransactionSource.MANUAL,
            occurredAt = startAt + 1000L,
            createdAt = startAt,
            updatedAt = startAt
        )

        val result = AccountsChartBuilder.buildAccountComposition(
            accounts = accounts,
            allTransactions = listOf(txCard),
            asOfNow = startAt + 5000L
        )

        assertThat(result).isInstanceOf(ChartState.Data::class.java)
        val data = (result as ChartState.Data).data

        // Invariant I6: sum(assets) - sum(liabilities) == NetWorth
        assertThat(data.totalAssetsPaise).isEqualTo(55_000_00L) // 50,000 + 5,000
        assertThat(data.totalLiabilitiesPaise).isEqualTo(35_000_00L)
        assertThat(data.netWorthPaise).isEqualTo(20_000_00L)
        assertThat(data.totalAssetsPaise - data.totalLiabilitiesPaise).isEqualTo(data.netWorthPaise)

        // Sorted by abs(balance) descending: Bank (50,000), CC (35,000), Cash (5,000)
        assertThat(data.accounts).hasSize(3)
        assertThat(data.accounts[0].accountName).isEqualTo("HDFC Bank")
        assertThat(data.accounts[0].isAsset).isTrue()
        assertThat(data.accounts[1].accountName).isEqualTo("ICICI Credit Card")
        assertThat(data.accounts[1].isAsset).isFalse()
        assertThat(data.accounts[2].accountName).isEqualTo("Cash")
        assertThat(data.accounts[2].isAsset).isTrue()
    }

    @Test
    fun testV10_CreditCardGauge_ZonesClassification() {
        val accounts = listOf(creditCard)

        // Case A: 25% debt (25,000 / 100,000 = 2500 bp) -> NORMAL (< 30%)
        val txA = TransactionEntity(id = 1L, accountId = 2L, kind = TransactionKind.EXPENSE, direction = TransactionDirection.OUT, amountPaise = 25_000_00L, status = TransactionStatus.CONFIRMED, source = TransactionSource.MANUAL, occurredAt = startAt + 1000L, createdAt = startAt, updatedAt = startAt)
        val resA = AccountsChartBuilder.buildAccountComposition(accounts, listOf(txA), startAt + 2000L)
        val gaugeA = (resA as ChartState.Data).data.creditCardGauges.first()
        assertThat(gaugeA.utilizationBp).isEqualTo(2500)
        assertThat(gaugeA.zone).isEqualTo(UtilizationZone.NORMAL)

        // Case B: 50% debt (50,000 / 100,000 = 5000 bp) -> CAUTION (30% - 70%)
        val txB = TransactionEntity(id = 2L, accountId = 2L, kind = TransactionKind.EXPENSE, direction = TransactionDirection.OUT, amountPaise = 50_000_00L, status = TransactionStatus.CONFIRMED, source = TransactionSource.MANUAL, occurredAt = startAt + 1000L, createdAt = startAt, updatedAt = startAt)
        val resB = AccountsChartBuilder.buildAccountComposition(accounts, listOf(txB), startAt + 2000L)
        val gaugeB = (resB as ChartState.Data).data.creditCardGauges.first()
        assertThat(gaugeB.utilizationBp).isEqualTo(5000)
        assertThat(gaugeB.zone).isEqualTo(UtilizationZone.CAUTION)

        // Case C: 85% debt (85,000 / 100,000 = 8500 bp) -> HIGH (> 70%)
        val txC = TransactionEntity(id = 3L, accountId = 2L, kind = TransactionKind.EXPENSE, direction = TransactionDirection.OUT, amountPaise = 85_000_00L, status = TransactionStatus.CONFIRMED, source = TransactionSource.MANUAL, occurredAt = startAt + 1000L, createdAt = startAt, updatedAt = startAt)
        val resC = AccountsChartBuilder.buildAccountComposition(accounts, listOf(txC), startAt + 2000L)
        val gaugeC = (resC as ChartState.Data).data.creditCardGauges.first()
        assertThat(gaugeC.utilizationBp).isEqualTo(8500)
        assertThat(gaugeC.zone).isEqualTo(UtilizationZone.HIGH)
    }

    @Test
    fun testV10_CreditCardGauge_OverLimitUtilizationHandledSafely() {
        val accounts = listOf(creditCard)
        // 120% debt (120,000 / 100,000 = 12000 bp)
        val txOver = TransactionEntity(id = 1L, accountId = 2L, kind = TransactionKind.EXPENSE, direction = TransactionDirection.OUT, amountPaise = 120_000_00L, status = TransactionStatus.CONFIRMED, source = TransactionSource.MANUAL, occurredAt = startAt + 1000L, createdAt = startAt, updatedAt = startAt)

        val result = AccountsChartBuilder.buildAccountComposition(accounts, listOf(txOver), startAt + 2000L)
        assertThat(result).isInstanceOf(ChartState.Data::class.java)
        val gauge = (result as ChartState.Data).data.creditCardGauges.first()

        assertThat(gauge.utilizationBp).isEqualTo(12000)
        assertThat(gauge.zone).isEqualTo(UtilizationZone.HIGH)
    }
}
