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

        assertThat(endDate.year).isEqualTo(2026)
        assertThat(endDate.monthValue).isEqualTo(10)
        assertThat(endDate.dayOfMonth).isEqualTo(31)
        assertThat(endDate.hour).isEqualTo(23)
        assertThat(endDate.minute).isEqualTo(59)
    }
}
