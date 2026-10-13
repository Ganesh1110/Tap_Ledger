package com.tapledger.core.security

import android.content.Context
import android.content.SharedPreferences

class FakeMpinClock(
    var elapsed: Long = 100_000L,
    var wallClock: Long = 1_700_000_000_000L
) : MpinClock {
    override fun elapsedRealtime(): Long = elapsed
    override fun currentTimeMillis(): Long = wallClock

    fun advanceSeconds(seconds: Long) {
        elapsed += seconds * 1000
        wallClock += seconds * 1000
    }

    fun reboot(newElapsed: Long = 1_000L) {
        elapsed = newElapsed
    }
}

class FakeMpinKeystore : MpinKeystore {
    var isKeyCleared: Boolean = false
        private set

    private val key = byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16)

    override fun encrypt(plaintext: ByteArray): EncryptedData {
        isKeyCleared = false
        val iv = ByteArray(12) { 0x42.toByte() }
        val ciphertext = plaintext.mapIndexed { idx, b -> (b.toInt() xor key[idx % key.size].toInt()).toByte() }.toByteArray()
        return EncryptedData(iv, ciphertext)
    }

    override fun decrypt(encryptedData: EncryptedData): ByteArray {
        if (isKeyCleared) throw IllegalStateException("Key is cleared")
        return encryptedData.ciphertext.mapIndexed { idx, b -> (b.toInt() xor key[idx % key.size].toInt()).toByte() }.toByteArray()
    }

    override fun clearKey() {
        isKeyCleared = true
    }
}

class FakeSharedPreferences : SharedPreferences {
    val storage = mutableMapOf<String, Any>()

    override fun getAll(): MutableMap<String, *> = storage.toMutableMap()
    override fun getString(key: String?, defValue: String?): String? = storage[key] as? String ?: defValue
    override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? = defValues
    override fun getInt(key: String?, defValue: Int): Int = storage[key] as? Int ?: defValue
    override fun getLong(key: String?, defValue: Long): Long = storage[key] as? Long ?: defValue
    override fun getFloat(key: String?, defValue: Float): Float = storage[key] as? Float ?: defValue
    override fun getBoolean(key: String?, defValue: Boolean): Boolean = storage[key] as? Boolean ?: defValue
    override fun contains(key: String?): Boolean = storage.containsKey(key)
    override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}
    override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}

    override fun edit(): SharedPreferences.Editor = FakeEditor()

    inner class FakeEditor : SharedPreferences.Editor {
        private val temp = mutableMapOf<String, Any?>()
        private var clearRequested = false

        override fun putString(key: String?, value: String?): SharedPreferences.Editor {
            key?.let { if (value != null) temp[it] = value else temp.remove(it) }
            return this
        }

        override fun putStringSet(key: String?, values: MutableSet<String>?): SharedPreferences.Editor = this

        override fun putInt(key: String?, value: Int): SharedPreferences.Editor {
            key?.let { temp[it] = value }
            return this
        }

        override fun putLong(key: String?, value: Long): SharedPreferences.Editor {
            key?.let { temp[it] = value }
            return this
        }

        override fun putFloat(key: String?, value: Float): SharedPreferences.Editor {
            key?.let { temp[it] = value }
            return this
        }

        override fun putBoolean(key: String?, value: Boolean): SharedPreferences.Editor {
            key?.let { temp[it] = value }
            return this
        }

        override fun remove(key: String?): SharedPreferences.Editor {
            key?.let { temp[it] = null }
            return this
        }

        override fun clear(): SharedPreferences.Editor {
            clearRequested = true
            return this
        }

        override fun commit(): Boolean {
            apply()
            return true
        }

        override fun apply() {
            if (clearRequested) {
                storage.clear()
            }
            temp.forEach { (k, v) ->
                if (v == null) storage.remove(k) else storage[k] = v
            }
            temp.clear()
            clearRequested = false
        }
    }
}
