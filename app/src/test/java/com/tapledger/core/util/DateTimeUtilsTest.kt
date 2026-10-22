package com.tapledger.core.util

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

class DateTimeUtilsTest {

    @Test
    fun budgetMonth_calculatedInAsiaKolkata_notUTC() {
        // 2026-10-01 00:30:00 IST is 2026-09-30 19:00:00 UTC
        // Must belong to October 2026!
        val istDateTime = ZonedDateTime.of(2026, 10, 1, 0, 30, 0, 0, ZoneId.of("Asia/Kolkata"))
        val epochMillis = istDateTime.toInstant().toEpochMilli()

        val yearMonth = DateTimeUtils.toYearMonth(epochMillis)
        assertThat(yearMonth.year).isEqualTo(2026)
        assertThat(yearMonth.monthValue).isEqualTo(10)
    }

    @Test
    fun startAndEndOfMonth_boundariesMatchIST() {
        val startMillis = DateTimeUtils.getStartOfMonthEpochMillis(2026, 10)
        val endMillis = DateTimeUtils.getEndOfMonthEpochMillis(2026, 10)

        val startDate = DateTimeUtils.toZonedDateTime(startMillis)
        val endDate = DateTimeUtils.toZonedDateTime(endMillis)

        assertThat(startDate.year).isEqualTo(2026)
        assertThat(startDate.monthValue).isEqualTo(10)
        assertThat(startDate.dayOfMonth).isEqualTo(1)
        assertThat(startDate.hour).isEqualTo(0)
        assertThat(startDate.minute).isEqualTo(0)

        // Half-open interval [start_at, end_at): end_at is 00:00:00.000 IST of 2026-11-01
        assertThat(endDate.year).isEqualTo(2026)
        assertThat(endDate.monthValue).isEqualTo(11)
        assertThat(endDate.dayOfMonth).isEqualTo(1)
        assertThat(endDate.hour).isEqualTo(0)
        assertThat(endDate.minute).isEqualTo(0)
    }

    @Test
    fun testI11_EveryIstDayIsExactly86400000msAndMonthBoundariesPrecision() {
        val millisPerDay = 86_400_000L

        // Invariant I11: Asia/Kolkata has no DST transitions; every single day is exactly 86,400,000 ms
        val testYearMonths = listOf(
            Triple(2025, 2, 28), // 28-day Feb
            Triple(2028, 2, 29), // 29-day Feb (leap year 2028)
            Triple(2026, 4, 30), // 30-day April
            Triple(2026, 10, 31) // 31-day October
        )

        for ((year, month, expectedDays) in testYearMonths) {
            val startMillis = DateTimeUtils.getStartOfMonthEpochMillis(year, month)
            val endMillis = DateTimeUtils.getEndOfMonthEpochMillis(year, month)

            // Assert exact millisecond duration of the month
            val durationMillis = endMillis - startMillis
            val expectedDurationMillis = expectedDays.toLong() * millisPerDay
            assertThat(durationMillis).isEqualTo(expectedDurationMillis)

            // Assert every day in this month spans exactly 86,400,000 ms starting at 00:00:00 IST
            for (day in 1..expectedDays) {
                val dayStart = startMillis + (day - 1).toLong() * millisPerDay
                val zdt = DateTimeUtils.toZonedDateTime(dayStart)

                assertThat(zdt.year).isEqualTo(year)
                assertThat(zdt.monthValue).isEqualTo(month)
                assertThat(zdt.dayOfMonth).isEqualTo(day)
                assertThat(zdt.hour).isEqualTo(0)
                assertThat(zdt.minute).isEqualTo(0)
                assertThat(zdt.second).isEqualTo(0)
                assertThat(zdt.nano).isEqualTo(0)
                assertThat(zdt.zone).isEqualTo(DateTimeUtils.IST_ZONE)
            }
        }
    }

    @Test
    fun testDatePickerTimezoneConversion_roundTripAndDatePreservation() {
        // An IST timestamp on 2026-10-15 at 14:30:00
        val istZdt = ZonedDateTime.of(2026, 10, 15, 14, 30, 0, 0, DateTimeUtils.IST_ZONE)
        val epochMillis = istZdt.toInstant().toEpochMilli()

        // Convert to UTC midnight for M3 DatePicker
        val utcMidnight = DateTimeUtils.istEpochMillisToUtcMidnightMillis(epochMillis)
        val utcZdt = Instant.ofEpochMilli(utcMidnight).atZone(ZoneId.of("UTC"))
        assertThat(utcZdt.year).isEqualTo(2026)
        assertThat(utcZdt.monthValue).isEqualTo(10)
        assertThat(utcZdt.dayOfMonth).isEqualTo(15)
        assertThat(utcZdt.hour).isEqualTo(0)
        assertThat(utcZdt.minute).isEqualTo(0)

        // Convert back from UTC midnight to IST
        val convertedIstMillis = DateTimeUtils.utcMidnightToIstEpochMillis(utcMidnight)
        val convertedDate = DateTimeUtils.toLocalDate(convertedIstMillis)
        assertThat(convertedDate.year).isEqualTo(2026)
        assertThat(convertedDate.monthValue).isEqualTo(10)
        assertThat(convertedDate.dayOfMonth).isEqualTo(15)
        assertThat(DateTimeUtils.formatDate(convertedIstMillis)).isEqualTo("15 Oct 2026")
    }
}
