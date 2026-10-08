package com.tapledger.data.backup

import android.content.Context
import androidx.sqlite.db.SimpleSQLiteQuery
import com.tapledger.core.database.TapLedgerDatabase
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BackupManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: TapLedgerDatabase
) {

    /**
     * Executes a safe SQLite backup using VACUUM INTO to avoid WAL concurrency issues (Section 62).
     */
    suspend fun createBackup(targetFile: File): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            if (targetFile.exists()) {
                targetFile.delete()
            }
            val targetPath = targetFile.absolutePath
            // VACUUM INTO safely checkpoints WAL and creates an atomic standalone DB snapshot
            val query = SimpleSQLiteQuery("VACUUM INTO '$targetPath'")
            database.openHelper.writableDatabase.query(query).close()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Runs PRAGMA integrity_check on the current database (Section 65).
     */
    suspend fun checkDatabaseIntegrity(): Result<String> = withContext(Dispatchers.IO) {
        try {
            val cursor = database.openHelper.readableDatabase.query(SimpleSQLiteQuery("PRAGMA integrity_check"))
            val result = if (cursor.moveToFirst()) {
                cursor.getString(0)
            } else {
                "unknown"
            }
            cursor.close()
            if (result.equals("ok", ignoreCase = true)) {
                Result.success("ok")
            } else {
                Result.failure(IllegalStateException("Integrity check failed: $result"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Creates a pre-restore safety backup (Section 65).
     */
    suspend fun createSafetyBackup(): Result<File> = withContext(Dispatchers.IO) {
        try {
            val safetyFile = File(context.filesDir, "safety_backup_${System.currentTimeMillis()}.db")
            val result = createBackup(safetyFile)
            if (result.isSuccess) {
                Result.success(safetyFile)
            } else {
                Result.failure(result.exceptionOrNull() ?: Exception("Unknown error creating safety backup"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
