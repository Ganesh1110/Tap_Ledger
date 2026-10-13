package com.tapledger.core.security

import android.content.Context
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import java.lang.reflect.Proxy

class MpinLockoutTest {

    private lateinit var clock: FakeMpinClock
    private lateinit var keystore: FakeMpinKeystore
    private lateinit var prefs: FakeSharedPreferences
    private lateinit var context: Context
    private lateinit var mpinManager: MpinManagerImpl

    @Before
    fun setUp() = runTest {
        clock = FakeMpinClock(elapsed = 10_000L, wallClock = 1_700_000_000_000L)
        keystore = FakeMpinKeystore()
        prefs = FakeSharedPreferences()

        mpinManager = MpinManagerImpl(keystore, clock, prefs)
        mpinManager.setMpin("4829", "4829")
    }

    @Test
    fun escalatingLockoutSchedule() = runTest {
        // Attempts 1 to 4: No lockout
        for (i in 1..4) {
            val res = mpinManager.verify("0000")
            assertThat(res).isInstanceOf(MpinVerifyResult.WrongMpin::class.java)
            val wrong = res as MpinVerifyResult.WrongMpin
            assertThat(wrong.lockoutSeconds).isEqualTo(0L)
            if (i in 3..4) {
                assertThat(wrong.attemptsRemainingBeforeLockout).isEqualTo(5 - i)
            }
        }

        // 5th attempt: 30 seconds lockout
        val res5 = mpinManager.verify("0000")
        assertThat(res5).isInstanceOf(MpinVerifyResult.WrongMpin::class.java)
        assertThat((res5 as MpinVerifyResult.WrongMpin).lockoutSeconds).isEqualTo(30L)
        assertThat(mpinManager.getLockoutCountdownSeconds()).isEqualTo(30L)

        // Cannot verify while locked out
        val lockedAttempt = mpinManager.verify("4829")
        assertThat(lockedAttempt).isInstanceOf(MpinVerifyResult.LockedOut::class.java)

        // Advance clock by 30 seconds
        clock.advanceSeconds(30)
        assertThat(mpinManager.getLockoutCountdownSeconds()).isEqualTo(0L)

        // 6th attempt: 60s lockout
        val res6 = mpinManager.verify("0000")
        assertThat((res6 as MpinVerifyResult.WrongMpin).lockoutSeconds).isEqualTo(60L)
        clock.advanceSeconds(60)

        // 7th attempt: 300s (5m) lockout
        val res7 = mpinManager.verify("0000")
        assertThat((res7 as MpinVerifyResult.WrongMpin).lockoutSeconds).isEqualTo(300L)
        clock.advanceSeconds(300)

        // 8th attempt: 900s (15m) lockout
        val res8 = mpinManager.verify("0000")
        assertThat((res8 as MpinVerifyResult.WrongMpin).lockoutSeconds).isEqualTo(900L)
        clock.advanceSeconds(900)

        // 9th attempt: 3600s (1 hr) lockout
        val res9 = mpinManager.verify("0000")
        assertThat((res9 as MpinVerifyResult.WrongMpin).lockoutSeconds).isEqualTo(3600L)
    }

    @Test
    fun lockoutPersistsAcrossManagerRecreation() = runTest {
        // Fail 5 times to trigger 30s lockout
        repeat(5) { mpinManager.verify("0000") }
        assertThat(mpinManager.getLockoutCountdownSeconds()).isEqualTo(30L)

        // Advance clock by 10s -> 20s remaining
        clock.advanceSeconds(10)
        assertThat(mpinManager.getLockoutCountdownSeconds()).isEqualTo(20L)

        // Simulate killing app and creating fresh manager instance sharing prefs
        val recreatedManager = MpinManagerImpl(keystore, clock, prefs)
        assertThat(recreatedManager.getLockoutCountdownSeconds()).isEqualTo(20L)

        val lockedResult = recreatedManager.verify("4829")
        assertThat(lockedResult).isInstanceOf(MpinVerifyResult.LockedOut::class.java)

        // After remaining 20s expires, unlock succeeds
        clock.advanceSeconds(20)
        assertThat(recreatedManager.getLockoutCountdownSeconds()).isEqualTo(0L)
        val successResult = recreatedManager.verify("4829")
        assertThat(successResult).isEqualTo(MpinVerifyResult.Success)
    }

    @Test
    fun successfulVerifyResetsFailureCount() = runTest {
        // Fail 4 times (1 attempt remaining before lockout)
        repeat(4) { mpinManager.verify("0000") }
        assertThat(mpinManager.lockoutState.value.failedAttempts).isEqualTo(4)

        // Correct verification resets failure count to 0
        val success = mpinManager.verify("4829")
        assertThat(success).isEqualTo(MpinVerifyResult.Success)
        assertThat(mpinManager.lockoutState.value.failedAttempts).isEqualTo(0)

        // Now fails again: starts from attempt 1 without locking
        val failAfterReset = mpinManager.verify("0000")
        assertThat(failAfterReset).isInstanceOf(MpinVerifyResult.WrongMpin::class.java)
        assertThat((failAfterReset as MpinVerifyResult.WrongMpin).lockoutSeconds).isEqualTo(0L)
        assertThat(mpinManager.lockoutState.value.failedAttempts).isEqualTo(1)
    }

    @Test
    fun rebootDoesNotBypassLockout() = runTest {
        // Trigger 30s lockout
        repeat(5) { mpinManager.verify("0000") }
        assertThat(mpinManager.getLockoutCountdownSeconds()).isEqualTo(30L)

        // Device reboots: elapsedRealtime resets back to 1_000ms
        clock.reboot(newElapsed = 1_000L)

        // Check remaining lockout: reboot handled conservatively, lockout is not bypassed!
        val remainingAfterReboot = mpinManager.getLockoutCountdownSeconds()
        assertThat(remainingAfterReboot).isAtLeast(30L)
    }
}
