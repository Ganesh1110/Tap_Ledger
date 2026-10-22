package com.tapledger.core.util

import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

object DateTimeUtils {
    val IST_ZONE: ZoneId = ZoneId.of("Asia/Kolkata")
    private val MONTH_YEAR_FORMATTER = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH)
    private val DATE_FORMATTER = DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH)
    private val TIME_FORMATTER = DateTimeFormatter.ofPattern("hh:mm a", Locale.ENGLISH)
    private val DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a", Locale.ENGLISH)

    fun nowEpochMillis(): Long = System.currentTimeMillis()

    fun toZonedDateTime(epochMillis: Long): ZonedDateTime {
        return Instant.ofEpochMilli(epochMillis).atZone(IST_ZONE)
    }

    fun toLocalDate(epochMillis: Long): LocalDate {
        return toZonedDateTime(epochMillis).toLocalDate()
    }

    fun toYearMonth(epochMillis: Long): YearMonth {
        val zdt = toZonedDateTime(epochMillis)
        return YearMonth.of(zdt.year, zdt.monthValue)
    }

    /**
     * Calculates the start epoch millis (00:00:00.000) for a given year and month in Asia/Kolkata.
     */
    fun getStartOfMonthEpochMillis(year: Int, month: Int): Long {
        val ym = YearMonth.of(year, month)
        val startDateTime = ym.atDay(1).atStartOfDay(IST_ZONE)
        return startDateTime.toInstant().toEpochMilli()
    }

    /**
     * Calculates the half-open interval upper bound end_at epoch millis (00:00:00.000 IST of next month)
     * strictly conforming to Flow.md half-open intervals [start_at, end_at) in Asia/Kolkata.
     */
    fun getEndOfMonthEpochMillis(year: Int, month: Int): Long {
        val ym = YearMonth.of(year, month)
        val endDateTime = ym.plusMonths(1).atDay(1).atStartOfDay(IST_ZONE)
        return endDateTime.toInstant().toEpochMilli()
    }

    fun formatMonthYear(year: Int, month: Int): String {
        val ym = YearMonth.of(year, month)
        return ym.format(MONTH_YEAR_FORMATTER)
    }

    fun formatDate(epochMillis: Long): String {
        return toZonedDateTime(epochMillis).format(DATE_FORMATTER)
    }

    /**
     * Calculates the start epoch millis (00:00:00.000) for a given timestamp in Asia/Kolkata (Flow.md §6.1).
     */
    fun getStartOfDayEpochMillis(epochMillis: Long): Long {
        val zdt = toZonedDateTime(epochMillis)
        return zdt.toLocalDate().atStartOfDay(IST_ZONE).toInstant().toEpochMilli()
    }

    /**
     * Calculates the end of day epoch millis (23:59:59.999) for a given timestamp in Asia/Kolkata (Flow.md §11.2, §11.3).
     */
    fun getEndOfDayEpochMillis(epochMillis: Long): Long {
        val zdt = toZonedDateTime(epochMillis)
        return zdt.toLocalDate().plusDays(1).atStartOfDay(IST_ZONE).toInstant().toEpochMilli() - 1L
    }

    fun formatTime(epochMillis: Long): String {
        return toZonedDateTime(epochMillis).format(TIME_FORMATTER)
    }

    fun formatDateTime(epochMillis: Long): String {
        return toZonedDateTime(epochMillis).format(DATE_TIME_FORMATTER)
    }

    /**
     * Converts a UTC midnight epoch millis (e.g. from Material 3 DatePicker)
     * to Asia/Kolkata epoch millis. If the selected date is today in IST, uses current
     * time-of-day; otherwise, combines the selected date with the current time-of-day in IST.
     */
    fun utcMidnightToIstEpochMillis(utcMillis: Long): Long {
        val pickedDate = Instant.ofEpochMilli(utcMillis).atZone(ZoneId.of("UTC")).toLocalDate()
        val todayIst = LocalDate.now(IST_ZONE)
        return if (pickedDate == todayIst) {
            nowEpochMillis()
        } else {
            val nowTime = ZonedDateTime.now(IST_ZONE).toLocalTime()
            pickedDate.atTime(nowTime).atZone(IST_ZONE).toInstant().toEpochMilli()
        }
    }

    /**
     * Converts an Asia/Kolkata epoch millis to UTC midnight epoch millis
     * for feeding into Material 3 DatePicker (which operates in UTC).
     */
    fun istEpochMillisToUtcMidnightMillis(epochMillis: Long): Long {
        val istDate = toLocalDate(epochMillis)
        return istDate.atStartOfDay(ZoneId.of("UTC")).toInstant().toEpochMilli()
    }

    /**
     * Returns the epoch millis for yesterday at the current time-of-day in Asia/Kolkata.
     */
    fun getYesterdayEpochMillis(): Long {
        val yesterday = LocalDate.now(IST_ZONE).minusDays(1)
        val nowTime = ZonedDateTime.now(IST_ZONE).toLocalTime()
        return yesterday.atTime(nowTime).atZone(IST_ZONE).toInstant().toEpochMilli()
    }
}
