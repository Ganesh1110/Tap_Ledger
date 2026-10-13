package com.tapledger.core.security

import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.time.temporal.IsoFields

/**
 * Pure policy logic for app lock evaluating grace periods, calendar boundary crossing
 * in IST, and periodic re-verification schedules. Flow.md §14.1 & Requirement 4.
 */
object AppLockPolicy {

    val IST_ZONE: ZoneId = ZoneId.of("Asia/Kolkata")

    /**
     * Evaluates whether background duration has exceeded the configured grace period.
     */
    fun isGracePeriodExpired(
        backgroundedAtElapsed: Long,
        currentElapsed: Long,
        gracePeriod: GracePeriod
    ): Boolean {
        if (backgroundedAtElapsed <= 0L) return true
        val elapsedDurationMs = currentElapsed - backgroundedAtElapsed
        val allowedDurationMs = gracePeriod.durationSeconds * 1000
        return elapsedDurationMs >= allowedDurationMs
    }

    /**
     * Evaluates whether mandatory periodic re-verification is due based on calendar
     * week/month boundary crossing or elapsed days in Asia/Kolkata (IST).
     */
    fun shouldTriggerPeriodicReverification(
        lastFullVerificationAt: Long,
        currentTimeMillis: Long,
        interval: PeriodicReverification
    ): Boolean {
        if (interval == PeriodicReverification.OFF || lastFullVerificationAt <= 0L) {
            return false
        }

        val lastDateTime = Instant.ofEpochMilli(lastFullVerificationAt).atZone(IST_ZONE)
        val currentDateTime = Instant.ofEpochMilli(currentTimeMillis).atZone(IST_ZONE)

        val lastDate = lastDateTime.toLocalDate()
        val currentDate = currentDateTime.toLocalDate()

        if (currentDate.isBefore(lastDate)) {
            // Conservative check if device clock moved backwards
            return true
        }

        return when (interval) {
            PeriodicReverification.OFF -> false
            PeriodicReverification.WEEKLY -> {
                val lastWeekYear = lastDate.get(IsoFields.WEEK_BASED_YEAR)
                val lastWeekNum = lastDate.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR)
                val currentWeekYear = currentDate.get(IsoFields.WEEK_BASED_YEAR)
                val currentWeekNum = currentDate.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR)

                val crossedWeekBoundary = currentWeekYear > lastWeekYear ||
                        (currentWeekYear == lastWeekYear && currentWeekNum > lastWeekNum)
                val daysElapsed = ChronoUnit.DAYS.between(lastDate, currentDate)

                crossedWeekBoundary || daysElapsed >= 7
            }
            PeriodicReverification.MONTHLY -> {
                val crossedMonthBoundary = currentDate.year > lastDate.year ||
                        (currentDate.year == lastDate.year && currentDate.monthValue > lastDate.monthValue)
                val daysElapsed = ChronoUnit.DAYS.between(lastDate, currentDate)

                crossedMonthBoundary || daysElapsed >= 30
            }
        }
    }
}
