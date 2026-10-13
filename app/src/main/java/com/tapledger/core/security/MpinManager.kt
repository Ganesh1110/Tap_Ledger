package com.tapledger.core.security

import kotlinx.coroutines.flow.StateFlow

enum class GracePeriod(val durationSeconds: Long, val label: String) {
    IMMEDIATELY(0L, "Immediately"),
    SECONDS_30(30L, "30 seconds"),
    MINUTES_1(60L, "1 minute"),
    MINUTES_5(300L, "5 minutes");

    companion object {
        fun fromNameOrDefault(name: String?): GracePeriod {
            return entries.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: SECONDS_30
        }
    }
}

enum class PeriodicReverification(val label: String) {
    WEEKLY("Weekly"),
    MONTHLY("Monthly"),
    OFF("Off");

    companion object {
        fun fromNameOrDefault(name: String?): PeriodicReverification {
            return entries.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: WEEKLY
        }
    }
}

data class MpinLockoutState(
    val isLockedOut: Boolean = false,
    val remainingLockoutSeconds: Long = 0L,
    val failedAttempts: Int = 0,
    val attemptsRemainingBeforeLockout: Int? = null
)

sealed interface MpinVerifyResult {
    data object Success : MpinVerifyResult
    data class WrongMpin(
        val attemptsRemainingBeforeLockout: Int?,
        val lockoutSeconds: Long
    ) : MpinVerifyResult
    data class LockedOut(val lockoutSeconds: Long) : MpinVerifyResult
}

/**
 * Bank-style MPIN management interface providing Argon2id verification,
 * escalating lockout brute-force defense, Keystore wrapping, and configuration state.
 */
interface MpinManager {
    val isMpinSet: StateFlow<Boolean>
    val mpinLength: StateFlow<Int>
    val gracePeriod: StateFlow<GracePeriod>
    val periodicReverification: StateFlow<PeriodicReverification>
    val lockoutState: StateFlow<MpinLockoutState>

    suspend fun setMpin(mpin: String, confirmMpin: String): Result<Unit>
    suspend fun verify(mpin: String): MpinVerifyResult
    suspend fun changeMpin(currentMpin: String, newMpin: String, confirmNewMpin: String): Result<Unit>
    suspend fun disable(currentMpin: String): Result<Unit>

    suspend fun updateGracePeriod(newPeriod: GracePeriod)
    suspend fun updatePeriodicReverification(newInterval: PeriodicReverification)

    fun recordSuccessfulVerification()
    fun getLastFullVerificationAt(): Long
    fun getLockoutCountdownSeconds(): Long

    suspend fun clearAllSecurityData()
}
