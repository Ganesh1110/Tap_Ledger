package com.tapledger.ui.charts.builder

import com.tapledger.core.model.MoneyFormatter
import com.tapledger.data.statement.ReconciliationProof
import com.tapledger.ui.charts.model.ChartState
import com.tapledger.ui.charts.model.ReconciliationBridgeModel
import com.tapledger.ui.charts.model.ReconciliationBridgeStep
import com.tapledger.ui.charts.model.ReconciliationTableRow

/**
 * Pure builder for Statement Reconciliation visual analytics (V12 Reconciliation Bridge) per Flow.md §17.2.
 */
object ReconciliationChartBuilder {

    /**
     * V12 Reconciliation bridge: waterfall
     * AppBalance(end) -> opening diff -> MISSING_IN_APP -> MISSING_IN_STMT -> DISCREPANCY -> TIMING_DIFFERENCE -> StmtClosing.
     * Driven by existing computeReconciliationProof output.
     * Non-zero residual drawn as a red bar and screen must not show "Reconciled".
     */
    fun buildReconciliationBridge(proof: ReconciliationProof): ChartState<ReconciliationBridgeModel> {
        return try {
            val steps = mutableListOf<ReconciliationBridgeStep>()
            val tableRows = mutableListOf<ReconciliationTableRow>()

            var runningPaise = proof.appBalanceEndPaise

            // Step 1: Base App Balance (End)
            steps.add(
                ReconciliationBridgeStep(
                    label = "App Balance",
                    deltaPaise = runningPaise,
                    runningTotalPaise = runningPaise,
                    isResidual = false
                )
            )
            tableRows.add(
                ReconciliationTableRow(
                    stepLabel = "App Balance (Period End)",
                    deltaFormatted = MoneyFormatter.formatPaiseToRupees(runningPaise),
                    runningTotalFormatted = MoneyFormatter.formatPaiseToRupees(runningPaise),
                    note = "Starting baseline"
                )
            )

            // Step 2: Opening Differential
            runningPaise = Math.addExact(runningPaise, proof.openingDifferentialPaise)
            steps.add(
                ReconciliationBridgeStep(
                    label = "Opening Diff",
                    deltaPaise = proof.openingDifferentialPaise,
                    runningTotalPaise = runningPaise,
                    isResidual = false
                )
            )
            tableRows.add(
                ReconciliationTableRow(
                    stepLabel = "Opening Differential",
                    deltaFormatted = MoneyFormatter.formatPaiseToRupees(proof.openingDifferentialPaise),
                    runningTotalFormatted = MoneyFormatter.formatPaiseToRupees(runningPaise),
                    note = "Statement Opening - App Opening"
                )
            )

            // Step 3: Missing In App (+)
            runningPaise = Math.addExact(runningPaise, proof.missingInAppSumPaise)
            steps.add(
                ReconciliationBridgeStep(
                    label = "Missing In App",
                    deltaPaise = proof.missingInAppSumPaise,
                    runningTotalPaise = runningPaise,
                    isResidual = false
                )
            )
            tableRows.add(
                ReconciliationTableRow(
                    stepLabel = "Missing In App",
                    deltaFormatted = MoneyFormatter.formatPaiseToRupees(proof.missingInAppSumPaise),
                    runningTotalFormatted = MoneyFormatter.formatPaiseToRupees(runningPaise),
                    note = "${proof.missingInAppLines.size} statement lines not in app"
                )
            )

            // Step 4: Missing In Stmt (-)
            val missingInStmtDelta = -proof.missingInStmtSumPaise
            runningPaise = Math.addExact(runningPaise, missingInStmtDelta)
            steps.add(
                ReconciliationBridgeStep(
                    label = "Missing In Stmt",
                    deltaPaise = missingInStmtDelta,
                    runningTotalPaise = runningPaise,
                    isResidual = false
                )
            )
            tableRows.add(
                ReconciliationTableRow(
                    stepLabel = "Missing In Stmt",
                    deltaFormatted = MoneyFormatter.formatPaiseToRupees(missingInStmtDelta),
                    runningTotalFormatted = MoneyFormatter.formatPaiseToRupees(runningPaise),
                    note = "${proof.missingInStmtTransactions.size} app transactions not in statement"
                )
            )

            // Step 5: Discrepancy
            runningPaise = Math.addExact(runningPaise, proof.discrepancySumPaise)
            steps.add(
                ReconciliationBridgeStep(
                    label = "Discrepancy",
                    deltaPaise = proof.discrepancySumPaise,
                    runningTotalPaise = runningPaise,
                    isResidual = false
                )
            )
            tableRows.add(
                ReconciliationTableRow(
                    stepLabel = "Discrepancies",
                    deltaFormatted = MoneyFormatter.formatPaiseToRupees(proof.discrepancySumPaise),
                    runningTotalFormatted = MoneyFormatter.formatPaiseToRupees(runningPaise),
                    note = "${proof.discrepancies.size} amount mismatches"
                )
            )

            // Step 6: Timing Difference
            runningPaise = Math.addExact(runningPaise, proof.timingDifferenceSumPaise)
            steps.add(
                ReconciliationBridgeStep(
                    label = "Timing Diff",
                    deltaPaise = proof.timingDifferenceSumPaise,
                    runningTotalPaise = runningPaise,
                    isResidual = false
                )
            )
            tableRows.add(
                ReconciliationTableRow(
                    stepLabel = "Timing Differences",
                    deltaFormatted = MoneyFormatter.formatPaiseToRupees(proof.timingDifferenceSumPaise),
                    runningTotalFormatted = MoneyFormatter.formatPaiseToRupees(runningPaise),
                    note = "${proof.timingDifferences.size} cross-period clearing items"
                )
            )

            // Check residual against Statement Closing
            val residual = Math.subtractExact(proof.stmtClosingPaise, runningPaise)
            if (residual != 0L) {
                runningPaise = Math.addExact(runningPaise, residual)
                steps.add(
                    ReconciliationBridgeStep(
                        label = "Unreconciled Residual",
                        deltaPaise = residual,
                        runningTotalPaise = runningPaise,
                        isResidual = true
                    )
                )
                tableRows.add(
                    ReconciliationTableRow(
                        stepLabel = "Unreconciled Residual",
                        deltaFormatted = MoneyFormatter.formatPaiseToRupees(residual),
                        runningTotalFormatted = MoneyFormatter.formatPaiseToRupees(runningPaise),
                        note = "Discrepancy between proof and statement closing"
                    )
                )
            }

            // Final Statement Closing Step
            steps.add(
                ReconciliationBridgeStep(
                    label = "Stmt Closing",
                    deltaPaise = proof.stmtClosingPaise,
                    runningTotalPaise = proof.stmtClosingPaise,
                    isResidual = false
                )
            )
            tableRows.add(
                ReconciliationTableRow(
                    stepLabel = "Statement Closing",
                    deltaFormatted = MoneyFormatter.formatPaiseToRupees(proof.stmtClosingPaise),
                    runningTotalFormatted = MoneyFormatter.formatPaiseToRupees(proof.stmtClosingPaise),
                    note = "Final target balance"
                )
            )

            val isReconciled = proof.isBalanced && residual == 0L

            val contentDesc = buildString {
                append("Statement reconciliation waterfall: ")
                append("App Balance ").append(MoneyFormatter.formatPaiseToRupees(proof.appBalanceEndPaise))
                append(" to Statement Closing ").append(MoneyFormatter.formatPaiseToRupees(proof.stmtClosingPaise)).append(". ")
                if (isReconciled) {
                    append("Reconciliation balanced (residual is ₹0).")
                } else {
                    append("Unreconciled: residual of ").append(MoneyFormatter.formatPaiseToRupees(residual)).append(".")
                }
            }

            ChartState.Data(
                ReconciliationBridgeModel(
                    steps = steps,
                    appClosingPaise = proof.appBalanceEndPaise,
                    stmtClosingPaise = proof.stmtClosingPaise,
                    residualPaise = residual,
                    isReconciled = isReconciled,
                    contentDescription = contentDesc,
                    tableRows = tableRows
                )
            )
        } catch (e: ArithmeticException) {
            ChartState.Error("Arithmetic overflow calculating reconciliation bridge: ${e.message}")
        }
    }
}
