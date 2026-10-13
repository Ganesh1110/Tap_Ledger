package com.tapledger.core.security

import org.bouncycastle.crypto.generators.Argon2BytesGenerator
import org.bouncycastle.crypto.params.Argon2Parameters
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * Argon2id hasher using BouncyCastle for bank-style MPIN storage.
 * Performs constant-time hash comparisons via MessageDigest.isEqual.
 * Never logs the MPIN, salt, or hash.
 */
object MpinHasher {

    const val SALT_LENGTH_BYTES = 16
    const val HASH_LENGTH_BYTES = 32
    const val DEFAULT_ITERATIONS = 3
    const val DEFAULT_MEMORY_KIB = 16384 // 16 MB memory-hard parameter
    const val DEFAULT_PARALLELISM = 1

    fun generateSalt(): ByteArray {
        val salt = ByteArray(SALT_LENGTH_BYTES)
        SecureRandom().nextBytes(salt)
        return salt
    }

    fun hash(
        mpin: String,
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

        val generator = Argon2BytesGenerator()
        generator.init(builder.build())
        val hash = ByteArray(HASH_LENGTH_BYTES)
        val mpinBytes = mpin.toByteArray(Charsets.UTF_8)
        try {
            generator.generateBytes(mpinBytes, hash, 0, hash.size)
        } finally {
            // Overwrite temporary byte array in memory
            mpinBytes.fill(0)
        }
        return hash
    }

    fun verify(
        mpin: String,
        salt: ByteArray,
        expectedHash: ByteArray,
        iterations: Int = DEFAULT_ITERATIONS,
        memoryKiB: Int = DEFAULT_MEMORY_KIB,
        parallelism: Int = DEFAULT_PARALLELISM
    ): Boolean {
        val computedHash = hash(mpin, salt, iterations, memoryKiB, parallelism)
        return MessageDigest.isEqual(computedHash, expectedHash)
    }
}
