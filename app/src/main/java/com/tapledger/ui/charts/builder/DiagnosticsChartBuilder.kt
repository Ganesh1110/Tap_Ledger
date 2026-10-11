package com.tapledger.ui.charts.builder

import com.tapledger.domain.model.IntegrityIssue
import com.tapledger.domain.model.IntegritySeverity
import com.tapledger.ui.charts.model.ChartState
import com.tapledger.ui.charts.model.DiagnosticsSeverityBarModel

/**
 * Pure builder for Diagnostics visual analytics (V13 Severity Bar) per Flow.md §17.2.
 */
object DiagnosticsChartBuilder {

    /**
     * V13 Diagnostics severity bar: count of violations by CRITICAL / ERROR / WARNING from DataIntegrityChecker.
     */
    fun buildDiagnosticsSeverityBar(issues: List<IntegrityIssue>): ChartState<DiagnosticsSeverityBarModel> {
        val critical = issues.count { it.severity == IntegritySeverity.CRITICAL }
        val error = issues.count { it.severity == IntegritySeverity.ERROR }
        val warning = issues.count { it.severity == IntegritySeverity.WARNING }
        val total = critical + error + warning

        val contentDesc = buildString {
            append("Diagnostics: ").append(total).append(" total issues found. ")
            append("Critical: ").append(critical).append(", ")
            append("Error: ").append(error).append(", ")
            append("Warning: ").append(warning).append(".")
        }

        return ChartState.Data(
            DiagnosticsSeverityBarModel(
                criticalCount = critical,
                errorCount = error,
                warningCount = warning,
                totalIssues = total,
                contentDescription = contentDesc
            )
        )
    }
}
