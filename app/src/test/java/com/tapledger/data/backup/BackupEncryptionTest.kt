package com.tapledger.data.backup

import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.security.SecureRandom

class BackupEncryptionTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun testBackupEncryptionWithArgon2id() {
        val testData = "TAPLEDGER_SQLITE_SNAPSHOT_DATABASE_CONTENT_TEST_PAYLOAD".toByteArray(Charsets.UTF_8)
        val passphrase = "correct-horse-battery-staple"

        // 1. Encrypt archive
        val encryptedBytes = BackupCrypto.encrypt(testData, passphrase)

        // 2. Verify Header format per Flow.md §13.1
        // Total header size: 61 bytes
        assertThat(encryptedBytes.size).isAtLeast(BackupCrypto.HEADER_LENGTH_BYTES + testData.size)

        // Bytes 0-3: TPLF magic bytes (0x54 0x50 0x4C 0x46)
        val magic = encryptedBytes.copyOfRange(0, 4)
        assertThat(magic).isEqualTo(BackupCrypto.MAGIC_TPLF)

        // Byte 4: Format Version = 1
        assertThat(encryptedBytes[4]).isEqualTo(BackupCrypto.FORMAT_VERSION)

        // 3. Format Auto-Detection
        val detected = BackupCrypto.detectFormat(encryptedBytes)
        assertThat(detected).isEqualTo(BackupFormat.ENCRYPTED_TPLF)

        // 4. Fresh Nonce Invariant per Flow.md §13.1
        // Encrypting the exact same plaintext and password a second time must produce distinct salt and IV
        val secondEncryptedBytes = BackupCrypto.encrypt(testData, passphrase)
        val salt1 = encryptedBytes.copyOfRange(5, 21)
        val salt2 = secondEncryptedBytes.copyOfRange(5, 21)
        val iv1 = encryptedBytes.copyOfRange(21, 33)
        val iv2 = secondEncryptedBytes.copyOfRange(21, 33)

        assertThat(salt1).isNotEqualTo(salt2)
        assertThat(iv1).isNotEqualTo(iv2)

        // 5. Successful Decryption with Correct Passphrase
        val decrypted = BackupCrypto.decrypt(encryptedBytes, passphrase)
        assertThat(decrypted).isEqualTo(testData)

        // 6. Failed Decryption with Incorrect Passphrase
        val ex = assertThrows(IllegalArgumentException::class.java) {
            BackupCrypto.decrypt(encryptedBytes, "wrong-passphrase")
        }
        assertThat(ex.message).contains("Incorrect passphrase or recovery key")
    }

    @Test
    fun testMagicByteFormatDetection() {
        val tplfBytes = byteArrayOf(0x54, 0x50, 0x4C, 0x46, 0x01, 0x00)
        val zipBytes = byteArrayOf(0x50, 0x4B, 0x03, 0x04, 0x00, 0x00)
        val corruptBytes = byteArrayOf(0x7F, 0x45, 0x4C, 0x46)

        assertThat(BackupCrypto.detectFormat(tplfBytes)).isEqualTo(BackupFormat.ENCRYPTED_TPLF)
        assertThat(BackupCrypto.detectFormat(zipBytes)).isEqualTo(BackupFormat.PLAINTEXT_ZIP)
        assertThat(BackupCrypto.detectFormat(corruptBytes)).isEqualTo(BackupFormat.UNKNOWN)
    }

    @Test
    fun testManifestBundlingAndSha256() {
        val dummyFile = tempFolder.newFile("test_snapshot.db").apply {
            writeText("SNAPSHOT_SQLITE_CONTENT_FOR_HASHING")
        }

        val sha256 = BackupManifest.computeFileSha256(dummyFile)
        assertThat(sha256).isNotEmpty()
        assertThat(sha256).hasLength(64) // 256 bits = 64 hex chars

        val manifest = BackupManifest(
            appVersion = "2.6",
            schemaVersion = 2,
            createdAt = 1728643200000L,
            sha256 = sha256,
            recordCounts = mapOf(
                "transactions" to 150L,
                "accounts" to 4L,
                "categories" to 12L
            )
        )

        val jsonString = manifest.toJsonString()
        val parsed = BackupManifest.fromJsonString(jsonString)

        assertThat(parsed.appVersion).isEqualTo("2.6")
        assertThat(parsed.schemaVersion).isEqualTo(2)
        assertThat(parsed.sha256).isEqualTo(sha256)
        assertThat(parsed.recordCounts["transactions"]).isEqualTo(150L)
        assertThat(parsed.recordCounts["accounts"]).isEqualTo(4L)
    }
}
