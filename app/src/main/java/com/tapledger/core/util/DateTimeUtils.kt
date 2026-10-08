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
     * Calculates the end epoch millis (23:59:59.999) for a given year and month in Asia/Kolkata.
     */
    fun getEndOfMonthEpochMillis(year: Int, month: Int): Long {
        val ym = YearMonth.of(year, month)
        val endDateTime = ym.atEndOfMonth().atTime(23, 59, 59, 999_000_000).atZone(IST_ZONE)
        return endDateTime.toInstant().toEpochMilli()
    }

    fun formatMonthYear(year: Int, month: Int): String {
        val ym = YearMonth.of(year, month)
        return ym.format(MONTH_YEAR_FORMATTER)
    }

    fun formatDate(epochMillis: Long): String {
        return toZonedDateTime(epochMillis).format(DATE_FORMATTER)
    }

    fun formatTime(epochMillis: Long): String {
        return toZonedDateTime(epochMillis).format(TIME_FORMATTER)
    }

    fun formatDateTime(epochMillis: Long): String {
        return toZonedDateTime(epochMillis).format(DATE_TIME_FORMATTER)
    }
}
