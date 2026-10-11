package com.tapledger.data.backup

import android.content.Context
import androidx.sqlite.db.SimpleSQLiteQuery
import com.tapledger.core.database.ReferenceCategories
import com.tapledger.core.database.TapLedgerDatabase
import com.tapledger.domain.validator.DataIntegrityChecker
import com.tapledger.service.FinancialIngestionPipeline
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BackupManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: TapLedgerDatabase,
    private val restoreNotificationBuffer: RestoreNotificationBuffer,
    private val financialIngestionPipeline: FinancialIngestionPipeline,
    private val dataIntegrityChecker: DataIntegrityChecker
) {

    /**
     * Executes atomic SQLite snapshot using VACUUM INTO and generates manifest per Flow.md §13.1.
     */
    suspend fun createSnapshotBundle(snapshotFile: File): Result<Pair<File, BackupManifest>> = withContext(Dispatchers.IO) {
        try {
            if (snapshotFile.exists()) {
                snapshotFile.delete()
            }
            val snapshotPath = snapshotFile.absolutePath

            // 1. VACUUM INTO atomic snapshot
            val vacuumQuery = SimpleSQLiteQuery("VACUUM INTO '$snapshotPath'")
            database.openHelper.writableDatabase.query(vacuumQuery).close()

            // 2. PRAGMA integrity_check on the snapshot
            val snapshotHelper = android.database.sqlite.SQLiteDatabase.openDatabase(
                snapshotPath,
                null,
                android.database.sqlite.SQLiteDatabase.OPEN_READONLY
            )
            val integrityCursor = snapshotHelper.rawQuery("PRAGMA integrity_check;", null)
            val integrityResult = if (integrityCursor.moveToFirst()) integrityCursor.getString(0) else "failed"
            integrityCursor.close()

            if (!integrityResult.equals("ok", ignoreCase = true)) {
                snapshotHelper.close()
                snapshotFile.delete()
                return@withContext Result.failure(IllegalStateException("Snapshot integrity check failed: $integrityResult"))
            }

            // 3. Count records from snapshot tables
            val recordCounts = mutableMapOf<String, Long>()
            val tables = listOf("transactions", "accounts", "categories", "budget_periods", "budget_allocations")
            for (table in tables) {
                val c = snapshotHelper.rawQuery("SELECT COUNT(*) FROM $table", null)
                val count = if (c.moveToFirst()) c.getLong(0) else 0L
                c.close()
                recordCounts[table] = count
            }
            snapshotHelper.close()

            // 4. SHA-256 & Manifest
            val sha256 = BackupManifest.computeFileSha256(snapshotFile)
            val manifest = BackupManifest(
                appVersion = "2.6",
                schemaVersion = database.openHelper.readableDatabase.version,
                createdAt = System.currentTimeMillis(),
                sha256 = sha256,
                recordCounts = recordCounts
            )

            Result.success(Pair(snapshotFile, manifest))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Creates an encrypted backup bundle (.tapledger) using Argon2id & AES-256-GCM per Flow.md §13.1.
     */
    suspend fun createEncryptedBackup(targetFile: File, secret: String): Result<BackupManifest> = withContext(Dispatchers.IO) {
        try {
            val tempDir = File(context.cacheDir, "backup_temp_${System.currentTimeMillis()}").apply { mkdirs() }
            val tempSnapshot = File(tempDir, "snapshot.db")

            val snapshotResult = createSnapshotBundle(tempSnapshot)
            if (snapshotResult.isFailure) {
                tempDir.deleteRecursively()
                return@withContext Result.failure(snapshotResult.exceptionOrNull() ?: Exception("Snapshot creation failed"))
            }

            val (snapshot, manifest) = snapshotResult.getOrThrow()
            val zipBytes = bundleToZipBytes(snapshot, manifest)
            val encryptedBytes = BackupCrypto.encrypt(zipBytes, secret)

            if (targetFile.exists()) targetFile.delete()
            FileOutputStream(targetFile).use { it.write(encryptedBytes) }
            tempDir.deleteRecursively()

            Result.success(manifest)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Creates a plaintext backup ZIP archive (opt-out) per Flow.md §13.1.
     */
    suspend fun createPlaintextBackup(targetFile: File): Result<BackupManifest> = withContext(Dispatchers.IO) {
        try {
            val tempDir = File(context.cacheDir, "backup_temp_${System.currentTimeMillis()}").apply { mkdirs() }
            val tempSnapshot = File(tempDir, "snapshot.db")

            val snapshotResult = createSnapshotBundle(tempSnapshot)
            if (snapshotResult.isFailure) {
                tempDir.deleteRecursively()
                return@withContext Result.failure(snapshotResult.exceptionOrNull() ?: Exception("Snapshot creation failed"))
            }

            val (snapshot, manifest) = snapshotResult.getOrThrow()
            val zipBytes = bundleToZipBytes(snapshot, manifest)

            if (targetFile.exists()) targetFile.delete()
            FileOutputStream(targetFile).use { it.write(zipBytes) }
            tempDir.deleteRecursively()

            Result.success(manifest)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Runs PRAGMA integrity_check on current active database.
     */
    suspend fun checkDatabaseIntegrity(): Result<String> = withContext(Dispatchers.IO) {
        try {
            val cursor = database.openHelper.readableDatabase.query(SimpleSQLiteQuery("PRAGMA integrity_check"))
            val result = if (cursor.moveToFirst()) cursor.getString(0) else "unknown"
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
     * Creates a pre-restore safety backup of active database per Flow.md §13.2.
     */
    suspend fun createSafetyBackup(): Result<File> = withContext(Dispatchers.IO) {
        try {
            val safetyFile = File(context.filesDir, "safety_backup_${System.currentTimeMillis()}.db")
            if (safetyFile.exists()) safetyFile.delete()

            val query = SimpleSQLiteQuery("VACUUM INTO '${safetyFile.absolutePath}'")
            database.openHelper.writableDatabase.query(query).close()
            Result.success(safetyFile)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Full Safe Restore Protocol with File-Backed Spool & Rollback Guard per Flow.md §13.2.
     */
    suspend fun restoreBackup(backupFile: File, secret: String? = null): Result<Unit> = withContext(Dispatchers.IO) {
        val tempRestoreDir = File(context.cacheDir, "restore_temp_${System.currentTimeMillis()}").apply { mkdirs() }
        val spoolFile = File(context.cacheDir, "restore_notification_spool.dat")
        var safetyBackupFile: File? = null

        try {
            if (!backupFile.exists() || backupFile.length() < 4) {
                return@withContext Result.failure(IllegalArgumentException("Invalid or non-existent backup file"))
            }

            // 1. Detect archive format by magic bytes
            val headerBytes = ByteArray(4)
            FileInputStream(backupFile).use { it.read(headerBytes) }
            val format = BackupCrypto.detectFormat(headerBytes)

            val zipBytes = when (format) {
                BackupFormat.ENCRYPTED_TPLF -> {
                    if (secret.isNullOrBlank()) {
                        return@withContext Result.failure(IllegalArgumentException("Passphrase or recovery key required for encrypted backup"))
                    }
                    val fileBytes = backupFile.readBytes()
                    BackupCrypto.decrypt(fileBytes, secret)
                }
                BackupFormat.PLAINTEXT_ZIP -> {
                    backupFile.readBytes()
                }
                BackupFormat.UNKNOWN -> {
                    return@withContext Result.failure(IllegalArgumentException("Invalid or corrupted backup format: unrecognized magic bytes"))
                }
            }

            // 2. Extract snapshot.db and manifest.json from ZIP
            val extractedSnapshot = File(tempRestoreDir, "snapshot.db")
            val extractedManifestFile = File(tempRestoreDir, "manifest.json")
            extractZipBytes(zipBytes, extractedSnapshot, extractedManifestFile)

            if (!extractedSnapshot.exists() || !extractedManifestFile.exists()) {
                return@withContext Result.failure(IllegalStateException("Backup archive missing snapshot.db or manifest.json"))
            }

            // 3. Verify SHA-256 and schema version
            val manifest = BackupManifest.fromJsonString(extractedManifestFile.readText(Charsets.UTF_8))
            val actualSha256 = BackupManifest.computeFileSha256(extractedSnapshot)
            if (!actualSha256.equals(manifest.sha256, ignoreCase = true)) {
                return@withContext Result.failure(IllegalStateException("SHA-256 checksum mismatch: corrupted backup snapshot"))
            }

            val currentSchemaVersion = database.openHelper.readableDatabase.version
            if (manifest.schemaVersion > currentSchemaVersion) {
                return@withContext Result.failure(IllegalStateException("Backup schema version (${manifest.schemaVersion}) exceeds app schema version ($currentSchemaVersion)"))
            }

            // 4. Run PRAGMA integrity_check on extracted snapshot
            val snapshotDb = android.database.sqlite.SQLiteDatabase.openDatabase(
                extractedSnapshot.absolutePath,
                null,
                android.database.sqlite.SQLiteDatabase.OPEN_READONLY
            )
            val checkCursor = snapshotDb.rawQuery("PRAGMA integrity_check;", null)
            val checkResult = if (checkCursor.moveToFirst()) checkCursor.getString(0) else "failed"
            checkCursor.close()
            snapshotDb.close()

            if (!checkResult.equals("ok", ignoreCase = true)) {
                return@withContext Result.failure(IllegalStateException("Snapshot integrity check failed: $checkResult"))
            }

            // 5. Pause Ingestion & Start Disk Spooling
            restoreNotificationBuffer.startSpooling(spoolFile)

            // 6. Create Pre-Restore Safety Backup of current DB
            val safetyResult = createSafetyBackup()
            if (safetyResult.isFailure) {
                restoreNotificationBuffer.cancelAndClear()
                return@withContext Result.failure(IllegalStateException("Failed to create pre-restore safety backup: ${safetyResult.exceptionOrNull()?.message}"))
            }
            safetyBackupFile = safetyResult.getOrThrow()

            // 7. Swap Database Files
            val dbFile = context.getDatabasePath(TapLedgerDatabase.DATABASE_NAME)
            val walFile = File(dbFile.parentFile, "${TapLedgerDatabase.DATABASE_NAME}-wal")
            val shmFile = File(dbFile.parentFile, "${TapLedgerDatabase.DATABASE_NAME}-shm")

            database.close()
            if (dbFile.exists()) dbFile.delete()
            if (walFile.exists()) walFile.delete()
            if (shmFile.exists()) shmFile.delete()

            extractedSnapshot.copyTo(dbFile, overwrite = true)

            // 8. Reopen Room Database & Validate Invariants
            val reopenedDb = try {
                database.openHelper.writableDatabase
            } catch (migrationEx: Exception) {
                // AUTOMATIC ROLLBACK on migration/open failure
                rollbackToSafetyBackup(safetyBackupFile, dbFile, walFile, shmFile)
                restoreNotificationBuffer.stopAndReplay { event -> financialIngestionPipeline.processEvent(event) }
                return@withContext Result.failure(IllegalStateException("Automatic rollback triggered: database open failed (${migrationEx.message})", migrationEx))
            }

            // 9. Run DataIntegrityChecker (19 Formal Invariant Rules)
            val accounts = database.accountDao().getAllAccountsSync()
            val categories = database.categoryDao().getAllCategoriesSync()
            val periods = database.budgetPeriodDao().getAllPeriodsSync()
            val budgetAllocations = database.budgetAllocationDao().getAllAllocationsSync()
            val transactions = database.transactionDao().getAllTransactionsSync()
            val transactionAllocations = database.transactionAllocationDao().getAllTransactionAllocationsSync()
            val monthCloses = database.monthCloseDao().getAllMonthClosesSync()
            val adjustments = database.budgetAdjustmentDao().getAllAdjustmentsSync()
            val advances = database.advanceLedgerDao().getAllAdvancesSync()
            val statementImports = database.statementImportDao().getAllImportsSync()
            val statementLines = database.statementLineDao().getAllLinesSync()

            val issues = dataIntegrityChecker.verifyIntegrity(
                accounts = accounts,
                categories = categories,
                periods = periods,
                allocations = budgetAllocations,
                transactions = transactions,
                transactionAllocations = transactionAllocations,
                monthCloses = monthCloses,
                adjustments = adjustments,
                advances = advances,
                statementImports = statementImports,
                statementLines = statementLines
            )

            val hasFailures = issues.any {
                it.severity == com.tapledger.domain.model.IntegritySeverity.CRITICAL ||
                it.severity == com.tapledger.domain.model.IntegritySeverity.ERROR
            }
            if (hasFailures) {
                // AUTOMATIC ROLLBACK on invariant failure
                rollbackToSafetyBackup(safetyBackupFile, dbFile, walFile, shmFile)
                restoreNotificationBuffer.stopAndReplay { event -> financialIngestionPipeline.processEvent(event) }
                return@withContext Result.failure(IllegalStateException("Automatic rollback triggered: restored database violated invariants (${issues.joinToString { it.ruleId }})"))
            }

            // 10. Restore Successful: Replay Spooled Alerts
            restoreNotificationBuffer.stopAndReplay { event ->
                financialIngestionPipeline.processEvent(event)
            }

            Result.success(Unit)
        } catch (e: Exception) {
            safetyBackupFile?.let { safety ->
                val dbFile = context.getDatabasePath(TapLedgerDatabase.DATABASE_NAME)
                val walFile = File(dbFile.parentFile, "${TapLedgerDatabase.DATABASE_NAME}-wal")
                val shmFile = File(dbFile.parentFile, "${TapLedgerDatabase.DATABASE_NAME}-shm")
                rollbackToSafetyBackup(safety, dbFile, walFile, shmFile)
            }
            restoreNotificationBuffer.stopAndReplay { event ->
                financialIngestionPipeline.processEvent(event)
            }
            Result.failure(e)
        } finally {
            tempRestoreDir.deleteRecursively()
        }
    }

    private fun rollbackToSafetyBackup(
        safetyFile: File,
        dbFile: File,
        walFile: File,
        shmFile: File
    ) {
        try {
            database.close()
            if (dbFile.exists()) dbFile.delete()
            if (walFile.exists()) walFile.delete()
            if (shmFile.exists()) shmFile.delete()
            if (safetyFile.exists()) {
                safetyFile.copyTo(dbFile, overwrite = true)
            }
            database.openHelper.writableDatabase
        } catch (_: Exception) {
            // Best-effort rollback
        }
    }

    private fun bundleToZipBytes(snapshot: File, manifest: BackupManifest): ByteArray {
        val bos = ByteArrayOutputStream()
        ZipOutputStream(bos).use { zos ->
            // Add manifest.json
            zos.putNextEntry(ZipEntry("manifest.json"))
            zos.write(manifest.toJsonString().toByteArray(Charsets.UTF_8))
            zos.closeEntry()

            // Add snapshot.db
            zos.putNextEntry(ZipEntry("snapshot.db"))
            FileInputStream(snapshot).use { fis ->
                fis.copyTo(zos)
            }
            zos.closeEntry()
        }
        return bos.toByteArray()
    }

    private fun extractZipBytes(zipBytes: ByteArray, targetSnapshot: File, targetManifest: File) {
        ZipInputStream(ByteArrayInputStream(zipBytes)).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                when (entry.name) {
                    "snapshot.db" -> {
                        FileOutputStream(targetSnapshot).use { fos ->
                            zis.copyTo(fos)
                        }
                    }
                    "manifest.json" -> {
                        FileOutputStream(targetManifest).use { fos ->
                            zis.copyTo(fos)
                        }
                    }
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
    }

    /**
     * Erases all user data and resets application to first-run state (Flow.md §15.1).
     * 1. Creates a safety backup of current data.
     * 2. Closes Room database.
     * 3. Deletes DB, WAL, and SHM files.
     * 4. Reopens fresh database and seeds reference categories.
     */
    suspend fun eraseAllDataAndReset(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            // 1. Create Safety Backup
            createSafetyBackup()

            // 2. Delete database files
            val dbFile = context.getDatabasePath(TapLedgerDatabase.DATABASE_NAME)
            val walFile = File(dbFile.parentFile, "${TapLedgerDatabase.DATABASE_NAME}-wal")
            val shmFile = File(dbFile.parentFile, "${TapLedgerDatabase.DATABASE_NAME}-shm")

            database.close()
            if (dbFile.exists()) dbFile.delete()
            if (walFile.exists()) walFile.delete()
            if (shmFile.exists()) shmFile.delete()

            // 3. Reopen fresh database
            database.openHelper.writableDatabase

            // 4. Seed reference categories
            ReferenceCategories.seedIfEmpty(database.categoryDao())

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
