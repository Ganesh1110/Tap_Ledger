package com.tapledger.core.security

data class EncryptedData(
    val iv: ByteArray,
    val ciphertext: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as EncryptedData
        if (!iv.contentEquals(other.iv)) return false
        if (!ciphertext.contentEquals(other.ciphertext)) return false
        return true
    }

    override fun hashCode(): Int {
        var result = iv.contentHashCode()
        result = 31 * result + ciphertext.contentHashCode()
        return result
    }
}

/**
 * Keystore interface abstracting Android Keystore AES-GCM encryption/decryption
 * so tests can use in-memory fakes without AndroidKeyStore dependency.
 */
interface MpinKeystore {
    fun encrypt(plaintext: ByteArray): EncryptedData
    fun decrypt(encryptedData: EncryptedData): ByteArray
    fun clearKey()
}
