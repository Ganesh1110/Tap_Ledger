package com.tapledger.ui.screens.backup

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tapledger.data.backup.BackupManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

data class BackupUiState(
    val isBackingUp: Boolean = false,
    val isChecking: Boolean = false,
    val lastBackupPath: String? = null,
    val integrityStatus: String? = null,
    val message: String? = null,
    val isError: Boolean = false
)

@HiltViewModel
class BackupRestoreViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val backupManager: BackupManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(BackupUiState())
    val uiState: StateFlow<BackupUiState> = _uiState.asStateFlow()

    fun runIntegrityCheck() {
        _uiState.value = _uiState.value.copy(isChecking = true, message = null)
        viewModelScope.launch {
            val result = backupManager.checkDatabaseIntegrity()
            if (result.isSuccess) {
                _uiState.value = _uiState.value.copy(
                    isChecking = false,
                    integrityStatus = "PRAGMA integrity_check: OK",
                    message = "Database is healthy and sound.",
                    isError = false
                )
            } else {
                _uiState.value = _uiState.value.copy(
                    isChecking = false,
                    integrityStatus = "Failed",
                    message = result.exceptionOrNull()?.message ?: "Integrity check failed",
                    isError = true
                )
            }
        }
    }

    fun triggerBackup() {
        _uiState.value = _uiState.value.copy(isBackingUp = true, message = null)
        viewModelScope.launch {
            val backupFile = File(context.filesDir, "tapledger_backup_${System.currentTimeMillis()}.db")
            val result = backupManager.createBackup(backupFile)
            if (result.isSuccess) {
                _uiState.value = _uiState.value.copy(
                    isBackingUp = false,
                    lastBackupPath = backupFile.absolutePath,
                    message = "Backup created successfully via VACUUM INTO!",
                    isError = false
                )
            } else {
                _uiState.value = _uiState.value.copy(
                    isBackingUp = false,
                    message = "Backup failed: ${result.exceptionOrNull()?.message}",
                    isError = true
                )
            }
        }
    }
}
