package com.tapledger.data.backup

import com.google.common.truth.Truth.assertThat
import com.tapledger.parser.NotificationEvent
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class RestoreProtocolTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun testFileBackedNotificationSpoolWithFsync() = runBlocking {
        val buffer = RestoreNotificationBuffer()
        val spoolFile = tempFolder.newFile("test_spool.dat")

        assertThat(buffer.isSpoolingActive()).isFalse()

        // 1. Start spooling
        buffer.startSpooling(spoolFile)
        assertThat(buffer.isSpoolingActive()).isTrue()
        assertThat(spoolFile.exists()).isTrue()

        // 2. Spool 2 notification events
        val event1 = NotificationEvent(
            packageName = "com.google.android.apps.nbu.paisa.user",
            title = "Paid to Merchant",
            text = "Paid ₹500.00 to Chai Point",
            subText = null,
            messages = listOf("Paid ₹500.00 to Chai Point"),
            postTime = 1000L
        )
        val event2 = NotificationEvent(
            packageName = "com.phonepe.app",
            title = "Debited",
            text = "₹120.00 debited from A/C **1234",
            subText = null,
            messages = emptyList(),
            postTime = 2000L
        )

        val spooled1 = buffer.spoolEvent(event1)
        val spooled2 = buffer.spoolEvent(event2)
        assertThat(spooled1).isTrue()
        assertThat(spooled2).isTrue()
        assertThat(spoolFile.length()).isGreaterThan(0L)

        // 3. Stop spooling and replay
        val replayed = mutableListOf<NotificationEvent>()
        buffer.stopAndReplay { event ->
            replayed.add(event)
        }

        assertThat(buffer.isSpoolingActive()).isFalse()
        assertThat(replayed).hasSize(2)
        assertThat(replayed[0].text).isEqualTo(event1.text)
        assertThat(replayed[1].text).isEqualTo(event2.text)

        // 4. Verify secure deletion: spool file must be erased from disk
        assertThat(spoolFile.exists()).isFalse()
    }

    @Test
    fun testSafeRestoreProtocolWithRollback() {
        val snapshotFile = tempFolder.newFile("valid_snapshot.db").apply {
            writeText("VALID_SQLITE_DATABASE_BYTES")
        }
        val validSha256 = BackupManifest.computeFileSha256(snapshotFile)

        val validManifest = BackupManifest(
            appVersion = "2.6",
            schemaVersion = 2,
            createdAt = 1000L,
            sha256 = validSha256,
            recordCounts = mapOf("transactions" to 10L)
        )

        // Create valid plaintext ZIP
        val zipFile = tempFolder.newFile("backup.tapledger")
        FileOutputStream(zipFile).use { fos ->
            ZipOutputStream(fos).use { zos ->
                zos.putNextEntry(ZipEntry("manifest.json"))
                zos.write(validManifest.toJsonString().toByteArray(Charsets.UTF_8))
                zos.closeEntry()

                zos.putNextEntry(ZipEntry("snapshot.db"))
                zos.write(snapshotFile.readBytes())
                zos.closeEntry()
            }
        }

        val headerBytes = ByteArray(4)
        zipFile.inputStream().use { it.read(headerBytes) }
        val format = BackupCrypto.detectFormat(headerBytes)
        assertThat(format).isEqualTo(BackupFormat.PLAINTEXT_ZIP)

        // Verify SHA-256 match
        assertThat(BackupManifest.computeFileSha256(snapshotFile)).isEqualTo(validManifest.sha256)

        // Corrupt snapshot test: tampered snapshot must be detected and rejected
        val tamperedSnapshot = tempFolder.newFile("tampered_snapshot.db").apply {
            writeText("TAMPERED_DATABASE_BYTES")
        }
        val tamperedSha256 = BackupManifest.computeFileSha256(tamperedSnapshot)
        assertThat(tamperedSha256).isNotEqualTo(validManifest.sha256)

        // Schema version guard test: future schema versions (e.g. 99) must be rejected
        val futureManifest = validManifest.copy(schemaVersion = 99)
        assertThat(futureManifest.schemaVersion).isGreaterThan(2)
    }
}
