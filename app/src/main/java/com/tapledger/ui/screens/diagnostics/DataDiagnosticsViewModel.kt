package com.tapledger.ui.screens.diagnostics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tapledger.data.repository.DiagnosticsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DataDiagnosticsViewModel @Inject constructor(
    private val diagnosticsRepository: DiagnosticsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(DataDiagnosticsUiState())
    val uiState: StateFlow<DataDiagnosticsUiState> = _uiState.asStateFlow()

    init {
        loadData()
    }

    fun loadData() {
        viewModelScope.launch {
            try {
                diagnosticsRepository.observeAuditLogs().collect { logs ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            auditLogs = logs,
                            errorMessage = null
                        )
                    }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = e.message ?: "Failed to load audit log records"
                    )
                }
            }
        }
        runIntegrityAudit()
    }

    fun runIntegrityAudit() {
        viewModelScope.launch {
            try {
                _uiState.update { it.copy(isAuditRunning = true) }
                val issues = diagnosticsRepository.runIntegrityAudit()
                _uiState.update {
                    it.copy(
                        isAuditRunning = false,
                        issues = issues,
                        lastScannedAt = com.tapledger.core.util.DateTimeUtils.nowEpochMillis(),
                        errorMessage = null
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isAuditRunning = false,
                        errorMessage = e.message ?: "Failed to run integrity audit scanner"
                    )
                }
            }
        }
    }

    fun clearErrorMessage() {
        _uiState.update { it.copy(errorMessage = null) }
    }
}
