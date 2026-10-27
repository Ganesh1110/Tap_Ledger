package com.tapledger.ui.screens.backup

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tapledger.core.database.dao.AccountDao
import com.tapledger.core.database.dao.CategoryDao
import com.tapledger.core.database.dao.TransactionAllocationDao
import com.tapledger.core.database.dao.TransactionDao
import com.tapledger.data.backup.BackupManager
import com.tapledger.data.export.CsvExporter
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

data class BackupFileInfo(
    val file: File,
    val name: String,
    val sizeBytes: Long,
    val modifiedAt: Long
)

data class BackupUiState(
    val isBackingUp: Boolean = false,
    val isChecking: Boolean = false,
    val isRestoring: Boolean = false,
    val lastBackupPath: String? = null,
    val lastBackupDate: Long? = null,
    val lastBackupSizeBytes: Long? = null,
    val availableBackups: List<BackupFileInfo> = emptyList(),
    val integrityVerified: Boolean? = null, // null = neutral, true = passed, false = failed
    val message: String? = null,
    val isError: Boolean = false,
    val restoreCompletedWithoutMpin: Boolean = false
)

@HiltViewModel
class BackupRestoreViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val backupManager: BackupManager,
    val mpinManager: com.tapledger.core.security.MpinManager,
    private val transactionDao: TransactionDao,
    private val accountDao: AccountDao,
    private val transactionAllocationDao: TransactionAllocationDao,
    private val categoryDao: CategoryDao
) : ViewModel() {

    private val _uiState = MutableStateFlow(BackupUiState())
    val uiState: StateFlow<BackupUiState> = _uiState.asStateFlow()

    init {
        refreshBackupList()
    }

    fun refreshBackupList() {
        val files = context.filesDir.listFiles { _, name ->
            (name.endsWith(".tapledger") || name.endsWith(".db")) &&
                    (name.startsWith("tapledger_backup_") || name.startsWith("safety_backup_"))
        } ?: emptyArray()

        val list = files.map { file ->
            BackupFileInfo(
                file = file,
                name = file.name,
                sizeBytes = file.length(),
                modifiedAt = file.lastModified()
            )
        }.sortedByDescending { it.modifiedAt }

        val latest = list.firstOrNull()
        _uiState.value = _uiState.value.copy(
            availableBackups = list,
            lastBackupPath = latest?.file?.absolutePath,
            lastBackupDate = latest?.modifiedAt,
            lastBackupSizeBytes = latest?.sizeBytes
        )
    }

    fun runIntegrityCheck() {
        _uiState.value = _uiState.value.copy(isChecking = true, message = null)
        viewModelScope.launch {
            val result = backupManager.checkDatabaseIntegrity()
            if (result.isSuccess) {
                _uiState.value = _uiState.value.copy(
                    isChecking = false,
                    integrityVerified = true,
                    message = "Database integrity check passed (PRAGMA: OK).",
                    isError = false
                )
            } else {
                _uiState.value = _uiState.value.copy(
                    isChecking = false,
                    integrityVerified = false,
                    message = result.exceptionOrNull()?.message ?: "Integrity check failed",
                    isError = true
                )
            }
        }
    }

    fun triggerBackup(passphrase: String? = null) {
        _uiState.value = _uiState.value.copy(isBackingUp = true, message = null)
        viewModelScope.launch {
            val isEncrypted = !passphrase.isNullOrBlank()
            val backupFile = File(context.filesDir, "tapledger_backup_${System.currentTimeMillis()}.tapledger")
            val result = if (isEncrypted) {
                backupManager.createEncryptedBackup(backupFile, passphrase!!)
            } else {
                backupManager.createPlaintextBackup(backupFile)
            }

            if (result.isSuccess) {
                refreshBackupList()
                _uiState.value = _uiState.value.copy(
                    isBackingUp = false,
                    message = if (isEncrypted) {
                        "Encrypted backup archive created (.tapledger with Argon2id + AES-256-GCM)!"
                    } else {
                        "Plaintext backup archive created safely (.tapledger ZIP bundle)!"
                    },
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

    fun restoreBackup(file: File, secret: String? = null) {
        _uiState.value = _uiState.value.copy(isRestoring = true, message = null)
        viewModelScope.launch {
            val result = backupManager.restoreBackup(file, secret)
            if (result.isSuccess) {
                refreshBackupList()
                val needsMpinSetup = !mpinManager.isMpinSet.value
                _uiState.value = _uiState.value.copy(
                    isRestoring = false,
                    message = "Backup restored successfully! All 19 invariants verified and spooled notifications replayed.",
                    isError = false,
                    restoreCompletedWithoutMpin = needsMpinSetup
                )
            } else {
                refreshBackupList()
                _uiState.value = _uiState.value.copy(
                    isRestoring = false,
                    message = "Restore failed: ${result.exceptionOrNull()?.message}",
                    isError = true
                )
            }
        }
    }

    fun eraseAllData() {
        _uiState.value = _uiState.value.copy(message = null)
        viewModelScope.launch {
            val result = backupManager.eraseAllDataAndReset()
            if (result.isSuccess) {
                refreshBackupList()
                _uiState.value = _uiState.value.copy(
                    message = "All data erased successfully. Resetting to first-run onboarding.",
                    isError = false
                )
            } else {
                _uiState.value = _uiState.value.copy(
                    message = "Failed to erase data: ${result.exceptionOrNull()?.message}",
                    isError = true
                )
            }
        }
    }

    fun exportCsv() {
        _uiState.value = _uiState.value.copy(message = null)
        viewModelScope.launch {
            try {
                val transactions = transactionDao.getAllTransactionsSync()
                val accounts = accountDao.getAllAccountsSync().associateBy { it.id }
                val allocations = transactionAllocationDao.getAllTransactionAllocationsSync().groupBy { it.transactionId }
                val categories = categoryDao.getAllCategoriesSync().associateBy { it.id }

                val csvContent = CsvExporter.exportTransactionsToCsv(
                    transactions = transactions,
                    accountsMap = accounts,
                    allocationsMap = allocations,
                    categoriesMap = categories
                )

                val exportDir = File(context.filesDir, "exports").apply { mkdirs() }
                val dateStr = java.time.LocalDate.now().toString()
                val exportFile = File(exportDir, "tapledger_transactions_$dateStr.csv")
                exportFile.writeText(csvContent)

                _uiState.value = _uiState.value.copy(
                    message = "Exported ${transactions.size} transactions to ${exportFile.name} (${exportFile.length()} bytes)",
                    isError = false
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    message = "CSV export failed: ${e.message}",
                    isError = true
                )
            }
        }
    }
}
