package com.tapledger.ui.screens.diagnostics

import com.tapledger.core.database.entity.AuditLogEntity
import com.tapledger.domain.model.IntegrityIssue
import com.tapledger.ui.charts.model.ChartState
import com.tapledger.ui.charts.model.DiagnosticsSeverityBarModel

data class DataDiagnosticsUiState(
    val isLoading: Boolean = true,
    val issues: List<IntegrityIssue> = emptyList(),
    val auditLogs: List<AuditLogEntity> = emptyList(),
    val isAuditRunning: Boolean = false,
    val lastScannedAt: Long? = null,
    val severityBarState: ChartState<DiagnosticsSeverityBarModel> = ChartState.Loading,
    val errorMessage: String? = null
)
