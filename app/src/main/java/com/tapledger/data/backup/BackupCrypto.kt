package com.tapledger.data.backup

import org.bouncycastle.crypto.generators.Argon2BytesGenerator
import org.bouncycastle.crypto.params.Argon2Parameters
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

enum class BackupFormat {
    ENCRYPTED_TPLF,
    PLAINTEXT_ZIP,
    UNKNOWN
}

/**
 * Portable Key Derivation & AES-256-GCM encryption architecture per Flow.md §13.1.
 * MasterKey = Argon2id(Secret, salt, iterations = 3, memory = 65536 KiB, parallelism = 1)
 * Fresh 12-byte IV/nonce generated per archive. Nonce reuse strictly prevented.
 * Header format: TPLF (4B) | Version (1B) | Salt (16B) | IV (12B) | Iterations (4B) | Memory (4B) | Parallelism (4B) | Tag (16B)
 */
object BackupCrypto {

    val MAGIC_TPLF = byteArrayOf(0x54, 0x50, 0x4C, 0x46) // "TPLF"
    val MAGIC_ZIP = byteArrayOf(0x50, 0x4B, 0x03, 0x04)  // "PK\x03\x04"

    const val FORMAT_VERSION: Byte = 1
    const val DEFAULT_ITERATIONS = 3
    const val DEFAULT_MEMORY_KIB = 65536
    const val DEFAULT_PARALLELISM = 1
    const val SALT_LENGTH_BYTES = 16
    const val IV_LENGTH_BYTES = 12
    const val KEY_LENGTH_BYTES = 32 // 256-bit AES
    const val KEY_VERIFY_TAG_LENGTH = 16
    const val HEADER_LENGTH_BYTES = 61 // 4 + 1 + 16 + 12 + 4 + 4 + 4 + 16

    private const val KEY_VERIFICATION_CONSTANT = "TAPLEDGER_MASTER_KEY_VERIFICATION_V1"

    fun detectFormat(bytes: ByteArray): BackupFormat {
        if (bytes.size < 4) return BackupFormat.UNKNOWN
        if (bytes[0] == MAGIC_TPLF[0] && bytes[1] == MAGIC_TPLF[1] && bytes[2] == MAGIC_TPLF[2] && bytes[3] == MAGIC_TPLF[3]) {
            return BackupFormat.ENCRYPTED_TPLF
        }
        if (bytes[0] == MAGIC_ZIP[0] && bytes[1] == MAGIC_ZIP[1] && bytes[2] == MAGIC_ZIP[2] && bytes[3] == MAGIC_ZIP[3]) {
            return BackupFormat.PLAINTEXT_ZIP
        }
        return BackupFormat.UNKNOWN
    }

    /**
     * Derives a 256-bit (32-byte) AES master key via Argon2id using BouncyCastle.
     */
    fun deriveMasterKey(
        secret: String,
        salt: ByteArray,
        iterations: Int = DEFAULT_ITERATIONS,
        memoryKiB: Int = DEFAULT_MEMORY_KIB,
        parallelism: Int = DEFAULT_PARALLELISM
    ): ByteArray {
        val builder = Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
            .withVersion(Argon2Parameters.ARGON2_VERSION_13)
            .withIterations(iterations)
            .withMemoryAsKB(memoryKiB)
            .withParallelism(parallelism)
            .withSalt(salt)
        val params = builder.build()
        val generator = Argon2BytesGenerator()
        generator.init(params)
        val masterKey = ByteArray(KEY_LENGTH_BYTES)
        generator.generateBytes(secret.toByteArray(Charsets.UTF_8), masterKey)
        return masterKey
    }

    /**
     * Computes 16-byte verification tag: HMAC-SHA256(key, constant)[0..15].
     */
    fun computeKeyVerificationTag(masterKey: ByteArray): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(masterKey, "HmacSHA256"))
        val fullTag = mac.doFinal(KEY_VERIFICATION_CONSTANT.toByteArray(Charsets.UTF_8))
        return fullTag.copyOfRange(0, KEY_VERIFY_TAG_LENGTH)
    }

    /**
     * Encrypts plaintext bytes using AES-256-GCM with fresh salt and fresh IV per archive.
     */
    fun encrypt(
        plaintextBytes: ByteArray,
        secret: String,
        secureRandom: SecureRandom = SecureRandom()
    ): ByteArray {
        val salt = ByteArray(SALT_LENGTH_BYTES)
        secureRandom.nextBytes(salt)

        val iv = ByteArray(IV_LENGTH_BYTES)
        secureRandom.nextBytes(iv) // Fresh Nonce Invariant per Flow.md §13.1

        val masterKey = deriveMasterKey(secret, salt)
        val keyTag = computeKeyVerificationTag(masterKey)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val keySpec = SecretKeySpec(masterKey, "AES")
        val gcmSpec = GCMParameterSpec(128, iv)
        cipher.init(Cipher.ENCRYPT_MODE, keySpec, gcmSpec)
        val ciphertext = cipher.doFinal(plaintextBytes)

        val buffer = ByteBuffer.allocate(HEADER_LENGTH_BYTES + ciphertext.size)
        buffer.order(ByteOrder.BIG_ENDIAN)
        buffer.put(MAGIC_TPLF)
        buffer.put(FORMAT_VERSION)
        buffer.put(salt)
        buffer.put(iv)
        buffer.putInt(DEFAULT_ITERATIONS)
        buffer.putInt(DEFAULT_MEMORY_KIB)
        buffer.putInt(DEFAULT_PARALLELISM)
        buffer.put(keyTag)
        buffer.put(ciphertext)

        return buffer.array()
    }

    /**
     * Verifies header and decrypts AES-256-GCM ciphertext stream.
     */
    fun decrypt(
        encryptedBytes: ByteArray,
        secret: String
    ): ByteArray {
        if (encryptedBytes.size < HEADER_LENGTH_BYTES) {
            throw IllegalArgumentException("Corrupted backup: file too small for TPLF header")
        }

        val buffer = ByteBuffer.wrap(encryptedBytes).order(ByteOrder.BIG_ENDIAN)
        val magic = ByteArray(4)
        buffer.get(magic)
        if (!magic.contentEquals(MAGIC_TPLF)) {
            throw IllegalArgumentException("Invalid magic bytes: expected TPLF")
        }

        val version = buffer.get()
        if (version != FORMAT_VERSION) {
            throw IllegalArgumentException("Unsupported backup format version: $version")
        }

        val salt = ByteArray(SALT_LENGTH_BYTES)
        buffer.get(salt)

        val iv = ByteArray(IV_LENGTH_BYTES)
        buffer.get(iv)

        val iterations = buffer.int
        val memoryKiB = buffer.int
        val parallelism = buffer.int

        val expectedKeyTag = ByteArray(KEY_VERIFY_TAG_LENGTH)
        buffer.get(expectedKeyTag)

        val masterKey = deriveMasterKey(secret, salt, iterations, memoryKiB, parallelism)
        val actualKeyTag = computeKeyVerificationTag(masterKey)

        if (!actualKeyTag.contentEquals(expectedKeyTag)) {
            throw IllegalArgumentException("Incorrect passphrase or recovery key")
        }

        val ciphertext = ByteArray(buffer.remaining())
        buffer.get(ciphertext)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val keySpec = SecretKeySpec(masterKey, "AES")
        val gcmSpec = GCMParameterSpec(128, iv)
        cipher.init(Cipher.DECRYPT_MODE, keySpec, gcmSpec)
        return cipher.doFinal(ciphertext)
    }
}
