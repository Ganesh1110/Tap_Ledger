package com.tapledger.ui.screens.specialized

import com.google.common.truth.Truth.assertThat
import com.tapledger.core.database.entity.BudgetPeriodEntity
import com.tapledger.core.database.entity.TransactionAllocationEntity
import com.tapledger.core.database.entity.TransactionEntity
import com.tapledger.core.model.BudgetBucket
import com.tapledger.core.model.BudgetPeriodStatus
import com.tapledger.core.model.TransactionDirection
import com.tapledger.core.model.TransactionKind
import com.tapledger.core.model.TransactionSource
import com.tapledger.core.model.TransactionStatus
import com.tapledger.data.statement.DiscrepancyPair
import com.tapledger.data.statement.ReconciliationProof
import com.tapledger.data.statement.TimingDifferencePair
import com.tapledger.domain.model.IntegrityIssue
import com.tapledger.domain.model.IntegritySeverity
import com.tapledger.domain.monthclose.MonthCloseEngine
import com.tapledger.ui.charts.builder.DiagnosticsChartBuilder
import com.tapledger.ui.charts.builder.InsightsChartBuilder
import com.tapledger.ui.charts.builder.MonthCloseChartBuilder
import com.tapledger.ui.charts.builder.ReconciliationChartBuilder
import com.tapledger.ui.charts.model.ChartState
import com.tapledger.ui.charts.util.ChartMath
import org.junit.Test

/**
 * Named test suite proving V7, V11, V12, and V13 visual requirements per Flow.md §17.2 & §17.7.
 */
class SpecializedVisualsTest {

    private val startAt = 1_700_000_000_000L
    private val dayMs = ChartMath.MILLIS_PER_IST_DAY // 86_400_000L

    // -------------------------------------------------------------
    // V7: SPENDING CALENDAR HEATMAP TESTS
    // -------------------------------------------------------------

    @Test
    fun testV7_IstDayDurationIsExactly86400000Ms() {
        assertThat(ChartMath.MILLIS_PER_IST_DAY).isEqualTo(86_400_000L)
    }

    @Test
    fun testV7_SpendingHeatmap_IntegerQuantileDistribution5Levels() {
        val period = BudgetPeriodEntity(
            id = 1L,
            year = 2026,
            month = 10,
            startAt = startAt,
            endAt = startAt + 10 * dayMs,
            incomePaise = 50_000_00L,
            status = BudgetPeriodStatus.OPEN,
            createdAt = startAt,
            updatedAt = startAt
        )

        // Create transactions on different days with increasing amounts
        val txs = listOf(
            TransactionEntity(id = 1L, accountId = 1L, amountPaise = 100_00L, direction = TransactionDirection.OUT, kind = TransactionKind.EXPENSE, status = TransactionStatus.CATEGORIZED, source = TransactionSource.MANUAL, occurredAt = startAt, createdAt = startAt, updatedAt = startAt),
            TransactionEntity(id = 2L, accountId = 1L, amountPaise = 200_00L, direction = TransactionDirection.OUT, kind = TransactionKind.EXPENSE, status = TransactionStatus.CATEGORIZED, source = TransactionSource.MANUAL, occurredAt = startAt + dayMs, createdAt = startAt, updatedAt = startAt),
            TransactionEntity(id = 3L, accountId = 1L, amountPaise = 500_00L, direction = TransactionDirection.OUT, kind = TransactionKind.EXPENSE, status = TransactionStatus.CATEGORIZED, source = TransactionSource.MANUAL, occurredAt = startAt + 2 * dayMs, createdAt = startAt, updatedAt = startAt),
            TransactionEntity(id = 4L, accountId = 1L, amountPaise = 1000_00L, direction = TransactionDirection.OUT, kind = TransactionKind.EXPENSE, status = TransactionStatus.CATEGORIZED, source = TransactionSource.MANUAL, occurredAt = startAt + 3 * dayMs, createdAt = startAt, updatedAt = startAt),
            TransactionEntity(id = 5L, accountId = 1L, amountPaise = 2000_00L, direction = TransactionDirection.OUT, kind = TransactionKind.EXPENSE, status = TransactionStatus.CATEGORIZED, source = TransactionSource.MANUAL, occurredAt = startAt + 4 * dayMs, createdAt = startAt, updatedAt = startAt)
        )

        val allocs = txs.mapIndexed { idx, tx ->
            TransactionAllocationEntity(
                id = (idx + 1).toLong(),
                transactionId = tx.id,
                categoryId = 10L,
                amountPaise = tx.amountPaise
            )
        }

        val state = InsightsChartBuilder.buildSpendingHeatmap(period, txs, allocs, mapOf(10L to BudgetBucket.NEEDS))
        assertThat(state).isInstanceOf(ChartState.Data::class.java)
        val data = (state as ChartState.Data).data

        assertThat(data.cells).hasSize(10)
        // Days with spend > 0 have quantile levels in 1..4
        val spendDays = data.cells.filter { it.netSpentPaise > 0L }
        assertThat(spendDays).hasSize(5)
        spendDays.forEach {
            assertThat(it.intensityLevel).isAtLeast(1)
            assertThat(it.intensityLevel).isAtMost(4)
        }

        // Days with 0 spend have intensity level 0
        val zeroDays = data.cells.filter { it.netSpentPaise == 0L }
        assertThat(zeroDays).hasSize(5)
        zeroDays.forEach {
            assertThat(it.intensityLevel).isEqualTo(0)
        }

        // Monotonic intensity: higher spend gets higher or equal intensity level
        val sortedBySpend = spendDays.sortedBy { it.netSpentPaise }
        for (i in 0 until sortedBySpend.size - 1) {
            assertThat(sortedBySpend[i].intensityLevel).isAtMost(sortedBySpend[i + 1].intensityLevel)
        }
    }

    @Test
    fun testV7_SpendingHeatmap_ExcludesIgnoredAndVoided() {
        val period = BudgetPeriodEntity(
            id = 1L,
            year = 2026,
            month = 10,
            startAt = startAt,
            endAt = startAt + 3 * dayMs,
            incomePaise = 50_000_00L,
            status = BudgetPeriodStatus.OPEN,
            createdAt = startAt,
            updatedAt = startAt
        )

        val txs = listOf(
            TransactionEntity(id = 1L, accountId = 1L, amountPaise = 500_00L, direction = TransactionDirection.OUT, kind = TransactionKind.EXPENSE, status = TransactionStatus.VOIDED, source = TransactionSource.MANUAL, occurredAt = startAt, createdAt = startAt, updatedAt = startAt),
            TransactionEntity(id = 2L, accountId = 1L, amountPaise = 500_00L, direction = TransactionDirection.OUT, kind = TransactionKind.EXPENSE, status = TransactionStatus.IGNORED, source = TransactionSource.MANUAL, occurredAt = startAt + dayMs, createdAt = startAt, updatedAt = startAt)
        )

        val allocs = listOf(
            TransactionAllocationEntity(id = 1L, transactionId = 1L, categoryId = 10L, amountPaise = 500_00L),
            TransactionAllocationEntity(id = 2L, transactionId = 2L, categoryId = 10L, amountPaise = 500_00L)
        )

        val state = InsightsChartBuilder.buildSpendingHeatmap(period, txs, allocs, mapOf(10L to BudgetBucket.NEEDS))
        val data = (state as ChartState.Data).data
        // Both days should be 0 spend because VOIDED and IGNORED are excluded per §4.2
        assertThat(data.cells.all { it.netSpentPaise == 0L }).isTrue()
    }

    // -------------------------------------------------------------
    // V11: ADVANCE ROLLOVER BARS TESTS
    // -------------------------------------------------------------

    @Test
    fun testV11_AdvanceRolloverBars_BuildsDivergingSegmentsAndTableRows() {
        val previewRows = listOf(
            MonthCloseEngine.MonthCloseAdvancePreviewRow(
                categoryId = 10L,
                categoryName = "Groceries",
                carryPaise = 5_000_00L,
                sweepPaise = 0L,
                appliedDeficitPaise = 0L,
                uncoveredPoolPaise = 0L
            ),
            MonthCloseEngine.MonthCloseAdvancePreviewRow(
                categoryId = 20L,
                categoryName = "Dining Out",
                carryPaise = 0L,
                sweepPaise = 0L,
                appliedDeficitPaise = 3_000_00L,
                uncoveredPoolPaise = 1_000_00L
            )
        )

        val preview = MonthCloseEngine.MonthClosePreviewResult(
            periodId = 1L,
            rows = previewRows,
            advanceEntries = emptyList()
        )

        val state = MonthCloseChartBuilder.buildAdvanceRolloverBars(preview)
        assertThat(state).isInstanceOf(ChartState.Data::class.java)
        val data = (state as ChartState.Data).data

        assertThat(data.rows).hasSize(2)
        assertThat(data.tableRows).hasSize(2)

        val carryRow = data.rows.first { it.categoryId == 10L }
        assertThat(carryRow.carryPaise).isEqualTo(5_000_00L)
        assertThat(carryRow.appliedDeficitPaise).isEqualTo(0L)

        val deficitRow = data.rows.first { it.categoryId == 20L }
        assertThat(deficitRow.appliedDeficitPaise).isEqualTo(3_000_00L)
        assertThat(deficitRow.uncoveredPoolPaise).isEqualTo(1_000_00L)

        // Accessibility content description includes amounts
        assertThat(data.contentDescription).contains("Carrying forward: ₹5,000.00")
        assertThat(data.contentDescription).contains("Applied deficits: ₹3,000.00")
        assertThat(data.contentDescription).contains("Uncovered pool: ₹1,000.00")
    }

    @Test
    fun testV11_AdvanceRolloverBars_EmptyRowsYieldsEmptyState() {
        val preview = MonthCloseEngine.MonthClosePreviewResult(
            periodId = 1L,
            rows = emptyList(),
            advanceEntries = emptyList()
        )

        val state = MonthCloseChartBuilder.buildAdvanceRolloverBars(preview)
        assertThat(state).isInstanceOf(ChartState.Empty::class.java)
        assertThat((state as ChartState.Empty).message).contains("No advance adjustments")
    }

    // -------------------------------------------------------------
    // V12: RECONCILIATION BRIDGE TESTS
    // -------------------------------------------------------------

    @Test
    fun testV12_ReconciliationBridge_FullyReconciledProof() {
        val proof = ReconciliationProof(
            stmtClosingPaise = 65_000_00L,
            appBalanceEndPaise = 65_000_00L,
            lhsPaise = 65_000_00L,
            stmtOpeningPaise = 50_000_00L,
            appBalanceStartMinusPaise = 50_000_00L,
            openingDifferentialPaise = 0L,
            missingInAppSumPaise = 0L,
            missingInStmtSumPaise = 0L,
            discrepancySumPaise = 0L,
            timingDifferenceSumPaise = 0L,
            rhsPaise = 65_000_00L,
            isBalanced = true,
            missingInAppLines = emptyList(),
            missingInStmtTransactions = emptyList(),
            discrepancies = emptyList<DiscrepancyPair>(),
            timingDifferences = emptyList<TimingDifferencePair>()
        )

        val state = ReconciliationChartBuilder.buildReconciliationBridge(proof)
        assertThat(state).isInstanceOf(ChartState.Data::class.java)
        val data = (state as ChartState.Data).data

        assertThat(data.isReconciled).isTrue()
        assertThat(data.residualPaise).isEqualTo(0L)
        assertThat(data.steps.any { it.isResidual }).isFalse()
        assertThat(data.contentDescription).contains("Reconciliation balanced")
    }

    @Test
    fun testV12_ReconciliationBridge_NonZeroResidualDrawnAsResidual() {
        val proof = ReconciliationProof(
            stmtClosingPaise = 60_000_00L,
            appBalanceEndPaise = 55_000_00L,
            lhsPaise = 60_000_00L,
            stmtOpeningPaise = 50_000_00L,
            appBalanceStartMinusPaise = 50_000_00L,
            openingDifferentialPaise = 0L,
            missingInAppSumPaise = 0L,
            missingInStmtSumPaise = 0L,
            discrepancySumPaise = 0L,
            timingDifferenceSumPaise = 0L,
            rhsPaise = 55_000_00L,
            isBalanced = false,
            missingInAppLines = emptyList(),
            missingInStmtTransactions = emptyList(),
            discrepancies = emptyList<DiscrepancyPair>(),
            timingDifferences = emptyList<TimingDifferencePair>()
        )

        val state = ReconciliationChartBuilder.buildReconciliationBridge(proof)
        assertThat(state).isInstanceOf(ChartState.Data::class.java)
        val data = (state as ChartState.Data).data

        assertThat(data.isReconciled).isFalse()
        assertThat(data.residualPaise).isEqualTo(5_000_00L)

        // Residual step exists and is marked isResidual = true (drawn in red per §17.2 V12)
        val residualStep = data.steps.firstOrNull { it.isResidual }
        assertThat(residualStep).isNotNull()
        assertThat(residualStep?.deltaPaise).isEqualTo(5_000_00L)
        assertThat(data.contentDescription).contains("Unreconciled: residual of")
    }

    // -------------------------------------------------------------
    // V13: DIAGNOSTICS SEVERITY BAR TESTS
    // -------------------------------------------------------------

    @Test
    fun testV13_DiagnosticsSeverityBar_AggregatesCountsAccurately() {
        val issues = listOf(
            IntegrityIssue(ruleId = "INV-01", message = "Imbalance", entityType = "Transaction", entityId = "1", severity = IntegritySeverity.CRITICAL),
            IntegrityIssue(ruleId = "INV-02", message = "Negative cash", entityType = "Account", entityId = "2", severity = IntegritySeverity.CRITICAL),
            IntegrityIssue(ruleId = "INV-03", message = "Orphaned", entityType = "Allocation", entityId = "3", severity = IntegritySeverity.ERROR),
            IntegrityIssue(ruleId = "INV-04", message = "Overlap", entityType = "Period", entityId = "4", severity = IntegritySeverity.WARNING)
        )

        val state = DiagnosticsChartBuilder.buildDiagnosticsSeverityBar(issues)
        assertThat(state).isInstanceOf(ChartState.Data::class.java)
        val data = (state as ChartState.Data).data

        assertThat(data.criticalCount).isEqualTo(2)
        assertThat(data.errorCount).isEqualTo(1)
        assertThat(data.warningCount).isEqualTo(1)
        assertThat(data.totalIssues).isEqualTo(4)
        assertThat(data.contentDescription).contains("Critical: 2, Error: 1, Warning: 1")
    }

    @Test
    fun testV13_DiagnosticsSeverityBar_ZeroIssuesHandledCleanly() {
        val state = DiagnosticsChartBuilder.buildDiagnosticsSeverityBar(emptyList())
        assertThat(state).isInstanceOf(ChartState.Data::class.java)
        val data = (state as ChartState.Data).data

        assertThat(data.totalIssues).isEqualTo(0)
        assertThat(data.criticalCount).isEqualTo(0)
        assertThat(data.errorCount).isEqualTo(0)
        assertThat(data.warningCount).isEqualTo(0)
        assertThat(data.contentDescription).contains("0 total issues found")
    }
}
