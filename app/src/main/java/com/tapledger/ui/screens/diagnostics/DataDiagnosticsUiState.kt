package com.tapledger.ui.screens.diagnostics

import com.tapledger.core.database.entity.AuditLogEntity
import com.tapledger.domain.model.IntegrityIssue

data class DataDiagnosticsUiState(
    val isLoading: Boolean = true,
    val issues: List<IntegrityIssue> = emptyList(),
    val auditLogs: List<AuditLogEntity> = emptyList(),
    val isAuditRunning: Boolean = false,
    val lastScannedAt: Long? = null,
    val errorMessage: String? = null
)
