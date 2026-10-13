package com.tapledger.core.security

import android.content.Context
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import java.lang.reflect.Proxy
import java.time.LocalDateTime
import java.time.ZoneId

class AppLockPolicyTest {

    private val istZone = ZoneId.of("Asia/Kolkata")

    @Test
    fun testGracePeriodBoundaries() {
        val backgroundedAt = 10_000L

        // IMMEDIATELY: Any non-zero elapsed or exact boundary is expired
        assertThat(AppLockPolicy.isGracePeriodExpired(backgroundedAt, 10_000L, GracePeriod.IMMEDIATELY)).isTrue()
        assertThat(AppLockPolicy.isGracePeriodExpired(backgroundedAt, 10_001L, GracePeriod.IMMEDIATELY)).isTrue()

        // SECONDS_30: (30s = 30_000ms)
        // 29s elapsed -> not expired
        assertThat(AppLockPolicy.isGracePeriodExpired(backgroundedAt, 39_000L, GracePeriod.SECONDS_30)).isFalse()
        // 30s elapsed -> expired
        assertThat(AppLockPolicy.isGracePeriodExpired(backgroundedAt, 40_000L, GracePeriod.SECONDS_30)).isTrue()
        // 35s elapsed -> expired
        assertThat(AppLockPolicy.isGracePeriodExpired(backgroundedAt, 45_000L, GracePeriod.SECONDS_30)).isTrue()

        // MINUTES_1: (60s)
        assertThat(AppLockPolicy.isGracePeriodExpired(backgroundedAt, 69_000L, GracePeriod.MINUTES_1)).isFalse()
        assertThat(AppLockPolicy.isGracePeriodExpired(backgroundedAt, 70_000L, GracePeriod.MINUTES_1)).isTrue()

        // MINUTES_5: (300s)
        assertThat(AppLockPolicy.isGracePeriodExpired(backgroundedAt, 309_000L, GracePeriod.MINUTES_5)).isFalse()
        assertThat(AppLockPolicy.isGracePeriodExpired(backgroundedAt, 310_000L, GracePeriod.MINUTES_5)).isTrue()
    }

    @Test
    fun testPeriodicReverificationOffMode() {
        val lastVerified = LocalDateTime.of(2026, 1, 1, 10, 0)
            .atZone(istZone).toInstant().toEpochMilli()
        val now = LocalDateTime.of(2026, 6, 1, 10, 0)
            .atZone(istZone).toInstant().toEpochMilli()

        val shouldForce = AppLockPolicy.shouldTriggerPeriodicReverification(
            lastFullVerificationAt = lastVerified,
            currentTimeMillis = now,
            interval = PeriodicReverification.OFF
        )
        assertThat(shouldForce).isFalse()
    }

    @Test
    fun testPeriodicReverificationWeeklyCrossingBoundary() {
        // Sunday 2026-10-11 23:59:00 IST
        val sundayNight = LocalDateTime.of(2026, 10, 11, 23, 59, 0)
            .atZone(istZone).toInstant().toEpochMilli()

        // Monday 2026-10-12 00:01:00 IST (crossed calendar week boundary into next week)
        val mondayMorning = LocalDateTime.of(2026, 10, 12, 0, 1, 0)
            .atZone(istZone).toInstant().toEpochMilli()

        val shouldForce = AppLockPolicy.shouldTriggerPeriodicReverification(
            lastFullVerificationAt = sundayNight,
            currentTimeMillis = mondayMorning,
            interval = PeriodicReverification.WEEKLY
        )
        assertThat(shouldForce).isTrue()
    }

    @Test
    fun testPeriodicReverificationWeeklyWithinSameWeek() {
        // Tuesday 2026-10-13 10:00 IST
        val tuesday = LocalDateTime.of(2026, 10, 13, 10, 0)
            .atZone(istZone).toInstant().toEpochMilli()

        // Wednesday 2026-10-14 10:00 IST (same calendar week, < 7 days)
        val wednesday = LocalDateTime.of(2026, 10, 14, 10, 0)
            .atZone(istZone).toInstant().toEpochMilli()

        val shouldForce = AppLockPolicy.shouldTriggerPeriodicReverification(
            lastFullVerificationAt = tuesday,
            currentTimeMillis = wednesday,
            interval = PeriodicReverification.WEEKLY
        )
        assertThat(shouldForce).isFalse()
    }

    @Test
    fun testPeriodicReverificationMonthlyCrossingBoundary() {
        // October 31, 2026 23:59 IST
        val endOfOct = LocalDateTime.of(2026, 10, 31, 23, 59)
            .atZone(istZone).toInstant().toEpochMilli()

        // November 1, 2026 00:01 IST
        val startOfNov = LocalDateTime.of(2026, 11, 1, 0, 1)
            .atZone(istZone).toInstant().toEpochMilli()

        val shouldForce = AppLockPolicy.shouldTriggerPeriodicReverification(
            lastFullVerificationAt = endOfOct,
            currentTimeMillis = startOfNov,
            interval = PeriodicReverification.MONTHLY
        )
        assertThat(shouldForce).isTrue()
    }

    @Test
    fun testPeriodicReverificationMonthlyWithinSameMonth() {
        val oct5 = LocalDateTime.of(2026, 10, 5, 10, 0)
            .atZone(istZone).toInstant().toEpochMilli()
        val oct20 = LocalDateTime.of(2026, 10, 20, 10, 0)
            .atZone(istZone).toInstant().toEpochMilli()

        val shouldForce = AppLockPolicy.shouldTriggerPeriodicReverification(
            lastFullVerificationAt = oct5,
            currentTimeMillis = oct20,
            interval = PeriodicReverification.MONTHLY
        )
        assertThat(shouldForce).isFalse()
    }

    @Test
    fun testColdStartLocksAndCoordinatorLifecycle() = runTest {
        val clock = FakeMpinClock(elapsed = 10_000L, wallClock = 1_700_000_000_000L)
        val keystore = FakeMpinKeystore()
        val prefs = FakeSharedPreferences()
        val mpinManager = MpinManagerImpl(keystore, clock, prefs)
        mpinManager.setMpin("4829", "4829")

        val coordinator = AppLockCoordinator(mpinManager, clock, kotlinx.coroutines.Dispatchers.Unconfined)
        // Cold start with MPIN set -> app is locked
        assertThat(coordinator.isAppLocked.value).isTrue()

        // Unlock
        coordinator.unlock()
        assertThat(coordinator.isAppLocked.value).isFalse()

        // Background app for 10 seconds (grace period is 30s)
        coordinator.onAppBackgrounded()
        clock.advanceSeconds(10)
        coordinator.onAppForegrounded()
        // Not locked because 10s < 30s grace period
        assertThat(coordinator.isAppLocked.value).isFalse()

        // Background app for 35 seconds (grace period is 30s)
        coordinator.onAppBackgrounded()
        clock.advanceSeconds(35)
        coordinator.onAppForegrounded()
        // Grace period expired -> locked!
        assertThat(coordinator.isAppLocked.value).isTrue()
    }
}
