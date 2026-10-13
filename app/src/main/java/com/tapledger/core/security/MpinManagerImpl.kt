package com.tapledger.core.security

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Base64
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MpinManagerImpl(
    private val keystore: MpinKeystore,
    private val clock: MpinClock,
    private val context: Context? = null,
    private val customPrefs: SharedPreferences? = null
) : MpinManager {

    constructor(
        context: Context,
        keystore: MpinKeystore,
        clock: MpinClock
    ) : this(keystore = keystore, clock = clock, context = context, customPrefs = null)

    // Constructor for testing with FakeSharedPreferences
    constructor(
        keystore: MpinKeystore,
        clock: MpinClock,
        prefs: SharedPreferences
    ) : this(keystore = keystore, clock = clock, context = null, customPrefs = prefs)

    companion object {
        const val PREFS_NAME = "tapledger_mpin_secure_prefs"

        private const val KEY_IS_SET = "is_mpin_set"
        private const val KEY_SALT_B64 = "mpin_salt_b64"
        private const val KEY_IV_B64 = "mpin_iv_b64"
        private const val KEY_CIPHERTEXT_B64 = "mpin_ciphertext_b64"
        private const val KEY_MPIN_LENGTH = "mpin_length"
        private const val KEY_GRACE_PERIOD = "auto_lock_grace_period"
        private const val KEY_PERIODIC_REVERIFICATION = "periodic_reverification"
        private const val KEY_LAST_FULL_VERIFICATION = "last_full_verification_at"

        private const val KEY_FAILED_ATTEMPTS = "failed_attempts"
        private const val KEY_LOCKOUT_UNTIL_ELAPSED = "lockout_until_elapsed"
        private const val KEY_LOCKOUT_UNTIL_WALL = "lockout_until_wall"
        private const val KEY_LAST_KNOWN_ELAPSED = "last_known_elapsed"
        private const val KEY_LOCKOUT_DURATION_SEC = "lockout_duration_sec"

        fun getLockoutDurationSeconds(consecutiveFailures: Int): Long = when {
            consecutiveFailures < 5 -> 0L
            consecutiveFailures == 5 -> 30L
            consecutiveFailures == 6 -> 60L
            consecutiveFailures == 7 -> 300L
            consecutiveFailures == 8 -> 900L
            else -> 3600L
        }
    }

    private val prefs: SharedPreferences by lazy {
        customPrefs ?: context?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            ?: throw IllegalStateException("SharedPreferences not available")
    }

    private val _isMpinSet = MutableStateFlow(false)
    override val isMpinSet: StateFlow<Boolean> = _isMpinSet.asStateFlow()

    private val _mpinLength = MutableStateFlow(4)
    override val mpinLength: StateFlow<Int> = _mpinLength.asStateFlow()

    private val _gracePeriod = MutableStateFlow(GracePeriod.SECONDS_30)
    override val gracePeriod: StateFlow<GracePeriod> = _gracePeriod.asStateFlow()

    private val _periodicReverification = MutableStateFlow(PeriodicReverification.WEEKLY)
    override val periodicReverification: StateFlow<PeriodicReverification> = _periodicReverification.asStateFlow()

    private val _lockoutState = MutableStateFlow(MpinLockoutState())
    override val lockoutState: StateFlow<MpinLockoutState> = _lockoutState.asStateFlow()

    init {
        refreshState()
    }

    @Synchronized
    private fun refreshState() {
        val isSet = prefs.getBoolean(KEY_IS_SET, false)
        _isMpinSet.value = isSet
        _mpinLength.value = prefs.getInt(KEY_MPIN_LENGTH, 4)
        _gracePeriod.value = GracePeriod.fromNameOrDefault(prefs.getString(KEY_GRACE_PERIOD, GracePeriod.SECONDS_30.name))
        _periodicReverification.value = PeriodicReverification.fromNameOrDefault(
            prefs.getString(KEY_PERIODIC_REVERIFICATION, PeriodicReverification.WEEKLY.name)
        )
        refreshLockoutState()
    }

    @Synchronized
    private fun refreshLockoutState() {
        val attempts = prefs.getInt(KEY_FAILED_ATTEMPTS, 0)
        val remaining = computeRemainingLockoutSecondsInternal()
        val attemptsRemaining = if (attempts in 3..4) 5 - attempts else null

        _lockoutState.value = MpinLockoutState(
            isLockedOut = remaining > 0,
            remainingLockoutSeconds = remaining,
            failedAttempts = attempts,
            attemptsRemainingBeforeLockout = attemptsRemaining
        )
    }

    @Synchronized
    private fun computeRemainingLockoutSecondsInternal(): Long {
        var lockoutUntilElapsed = prefs.getLong(KEY_LOCKOUT_UNTIL_ELAPSED, 0L)
        if (lockoutUntilElapsed <= 0L) return 0L

        val currentElapsed = clock.elapsedRealtime()
        val currentWall = clock.currentTimeMillis()
        val lastKnownElapsed = prefs.getLong(KEY_LAST_KNOWN_ELAPSED, 0L)
        val lockoutUntilWall = prefs.getLong(KEY_LOCKOUT_UNTIL_WALL, 0L)
        val lockoutDurationSec = prefs.getLong(KEY_LOCKOUT_DURATION_SEC, 0L)

        // Detect device reboot if currentElapsed < lastKnownElapsed
        if (currentElapsed < lastKnownElapsed) {
            val wallRemainingSec = (lockoutUntilWall - currentWall) / 1000
            // Conservative remaining lockout on reboot: never less than remaining duration or the full duration if tampered
            val conservativeRemainingSec = maxOf(wallRemainingSec, lockoutDurationSec).coerceAtLeast(1L)
            lockoutUntilElapsed = currentElapsed + (conservativeRemainingSec * 1000)

            prefs.edit()
                .putLong(KEY_LOCKOUT_UNTIL_ELAPSED, lockoutUntilElapsed)
                .putLong(KEY_LOCKOUT_UNTIL_WALL, currentWall + (conservativeRemainingSec * 1000))
                .putLong(KEY_LAST_KNOWN_ELAPSED, currentElapsed)
                .apply()
        } else {
            prefs.edit().putLong(KEY_LAST_KNOWN_ELAPSED, currentElapsed).apply()
        }

        val remainingMillis = lockoutUntilElapsed - currentElapsed
        return if (remainingMillis > 0) (remainingMillis + 999) / 1000 else 0L
    }

    override fun getLockoutCountdownSeconds(): Long {
        val remaining = computeRemainingLockoutSecondsInternal()
        refreshLockoutState()
        return remaining
    }

    override suspend fun setMpin(mpin: String, confirmMpin: String): Result<Unit> {
        if (mpin != confirmMpin) {
            return Result.failure(IllegalArgumentException("MPIN entries do not match"))
        }

        when (val validation = MpinValidator.validate(mpin)) {
            is MpinValidationResult.Invalid -> return Result.failure(IllegalArgumentException(validation.message))
            is MpinValidationResult.Valid -> {}
        }

        val salt = MpinHasher.generateSalt()
        val hash = MpinHasher.hash(mpin = mpin, salt = salt)
        val encrypted = keystore.encrypt(hash)

        val b64Encoder = Base64.getEncoder()
        prefs.edit()
            .putBoolean(KEY_IS_SET, true)
            .putString(KEY_SALT_B64, b64Encoder.encodeToString(salt))
            .putString(KEY_IV_B64, b64Encoder.encodeToString(encrypted.iv))
            .putString(KEY_CIPHERTEXT_B64, b64Encoder.encodeToString(encrypted.ciphertext))
            .putInt(KEY_MPIN_LENGTH, mpin.length)
            .putInt(KEY_FAILED_ATTEMPTS, 0)
            .putLong(KEY_LOCKOUT_UNTIL_ELAPSED, 0L)
            .putLong(KEY_LOCKOUT_UNTIL_WALL, 0L)
            .putLong(KEY_LOCKOUT_DURATION_SEC, 0L)
            .putLong(KEY_LAST_FULL_VERIFICATION, clock.currentTimeMillis())
            .apply()

        refreshState()
        return Result.success(Unit)
    }

    override suspend fun verify(mpin: String): MpinVerifyResult {
        val remainingLockout = getLockoutCountdownSeconds()
        if (remainingLockout > 0) {
            return MpinVerifyResult.LockedOut(remainingLockout)
        }

        val isSet = prefs.getBoolean(KEY_IS_SET, false)
        val saltB64 = prefs.getString(KEY_SALT_B64, null)
        val ivB64 = prefs.getString(KEY_IV_B64, null)
        val cipherB64 = prefs.getString(KEY_CIPHERTEXT_B64, null)

        if (!isSet || saltB64 == null || ivB64 == null || cipherB64 == null) {
            return MpinVerifyResult.WrongMpin(null, 0L)
        }

        val b64Decoder = Base64.getDecoder()
        val salt = b64Decoder.decode(saltB64)
        val iv = b64Decoder.decode(ivB64)
        val ciphertext = b64Decoder.decode(cipherB64)

        val decryptedHash = try {
            keystore.decrypt(EncryptedData(iv = iv, ciphertext = ciphertext))
        } catch (_: Exception) {
            return MpinVerifyResult.WrongMpin(null, 0L)
        }

        val isMatch = MpinHasher.verify(
            mpin = mpin,
            salt = salt,
            expectedHash = decryptedHash
        )

        if (isMatch) {
            // Reset failure count & update last verification timestamp
            prefs.edit()
                .putInt(KEY_FAILED_ATTEMPTS, 0)
                .putLong(KEY_LOCKOUT_UNTIL_ELAPSED, 0L)
                .putLong(KEY_LOCKOUT_UNTIL_WALL, 0L)
                .putLong(KEY_LOCKOUT_DURATION_SEC, 0L)
                .putLong(KEY_LAST_FULL_VERIFICATION, clock.currentTimeMillis())
                .apply()

            refreshLockoutState()
            return MpinVerifyResult.Success
        } else {
            val currentAttempts = prefs.getInt(KEY_FAILED_ATTEMPTS, 0) + 1
            val lockoutSec = getLockoutDurationSeconds(currentAttempts)
            val currentElapsed = clock.elapsedRealtime()
            val currentWall = clock.currentTimeMillis()

            val editor = prefs.edit().putInt(KEY_FAILED_ATTEMPTS, currentAttempts)
            if (lockoutSec > 0) {
                editor.putLong(KEY_LOCKOUT_UNTIL_ELAPSED, currentElapsed + (lockoutSec * 1000))
                editor.putLong(KEY_LOCKOUT_UNTIL_WALL, currentWall + (lockoutSec * 1000))
                editor.putLong(KEY_LOCKOUT_DURATION_SEC, lockoutSec)
            }
            editor.putLong(KEY_LAST_KNOWN_ELAPSED, currentElapsed)
            editor.apply()

            refreshLockoutState()

            return if (lockoutSec > 0) {
                MpinVerifyResult.WrongMpin(attemptsRemainingBeforeLockout = null, lockoutSeconds = lockoutSec)
            } else {
                val attemptsRemaining = if (currentAttempts in 3..4) 5 - currentAttempts else null
                MpinVerifyResult.WrongMpin(attemptsRemainingBeforeLockout = attemptsRemaining, lockoutSeconds = 0L)
            }
        }
    }

    override suspend fun changeMpin(
        currentMpin: String,
        newMpin: String,
        confirmNewMpin: String
    ): Result<Unit> {
        val verifyCurrent = verify(currentMpin)
        if (verifyCurrent !is MpinVerifyResult.Success) {
            return Result.failure(IllegalStateException("Current MPIN verification failed"))
        }
        return setMpin(newMpin, confirmNewMpin)
    }

    override suspend fun disable(currentMpin: String): Result<Unit> {
        val verifyCurrent = verify(currentMpin)
        if (verifyCurrent !is MpinVerifyResult.Success) {
            return Result.failure(IllegalStateException("Current MPIN verification failed"))
        }

        prefs.edit()
            .putBoolean(KEY_IS_SET, false)
            .remove(KEY_SALT_B64)
            .remove(KEY_IV_B64)
            .remove(KEY_CIPHERTEXT_B64)
            .putInt(KEY_FAILED_ATTEMPTS, 0)
            .putLong(KEY_LOCKOUT_UNTIL_ELAPSED, 0L)
            .putLong(KEY_LOCKOUT_UNTIL_WALL, 0L)
            .putLong(KEY_LOCKOUT_DURATION_SEC, 0L)
            .apply()

        keystore.clearKey()
        refreshState()
        return Result.success(Unit)
    }

    override suspend fun updateGracePeriod(newPeriod: GracePeriod) {
        prefs.edit().putString(KEY_GRACE_PERIOD, newPeriod.name).apply()
        _gracePeriod.value = newPeriod
    }

    override suspend fun updatePeriodicReverification(newInterval: PeriodicReverification) {
        prefs.edit().putString(KEY_PERIODIC_REVERIFICATION, newInterval.name).apply()
        _periodicReverification.value = newInterval
    }

    override fun recordSuccessfulVerification() {
        prefs.edit().putLong(KEY_LAST_FULL_VERIFICATION, clock.currentTimeMillis()).apply()
    }

    override fun getLastFullVerificationAt(): Long {
        return prefs.getLong(KEY_LAST_FULL_VERIFICATION, 0L)
    }

    override suspend fun clearAllSecurityData() {
        prefs.edit().clear().apply()
        try {
            keystore.clearKey()
        } catch (_: Exception) {
            // Best effort
        }
        refreshState()
    }
}
