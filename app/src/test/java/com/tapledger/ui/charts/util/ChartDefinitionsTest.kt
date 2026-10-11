package com.tapledger.ui.charts.util

import com.google.common.truth.Truth.assertThat
import com.tapledger.core.model.MoneyFormatter
import org.junit.Assert.assertThrows
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Unit tests verifying Shared Definitions D1 through D7 and Invariants I11, I12.
 */
class ChartDefinitionsTest {

    private val kolkataZone = ZoneId.of("Asia/Kolkata")

    // --- D1: Basis Points ---
    @Test
    fun testD1_BasisPoints_normalAndEdgeCases() {
        // 50% = 5,000 bp
        assertThat(ChartMath.computeBasisPoints(50L, 100L)).isEqualTo(5_000)
        // 100% = 10,000 bp
        assertThat(ChartMath.computeBasisPoints(100L, 100L)).isEqualTo(10_000)
        // 1/3 = 3,333 bp (floor)
        assertThat(ChartMath.computeBasisPoints(1L, 3L)).isEqualTo(3_333)
        // whole <= 0 gives 0
        assertThat(ChartMath.computeBasisPoints(50L, 0L)).isEqualTo(0)
        assertThat(ChartMath.computeBasisPoints(50L, -100L)).isEqualTo(0)
        // part <= 0 gives 0
        assertThat(ChartMath.computeBasisPoints(0L, 100L)).isEqualTo(0)
        assertThat(ChartMath.computeBasisPoints(-10L, 100L)).isEqualTo(0)
    }

    @Test
    fun testD1_BasisPoints_overflowProtection() {
        // ₹100 Cr = 100 * 10^7 * 100 = 10_000_000_000 paise
        val hundredCrPaise = 10_000_000_000L
        assertThat(ChartMath.computeBasisPoints(hundredCrPaise, hundredCrPaise * 2)).isEqualTo(5_000)

        // Extremely large number that overflows Long on multiply by 10_000 triggers ArithmeticException
        assertThrows(ArithmeticException::class.java) {
            ChartMath.computeBasisPoints(Long.MAX_VALUE, Long.MAX_VALUE)
        }
    }

    // --- D2: Percent Labels (Largest Remainder Apportionment) ---
    @Test
    fun testD2_LargestRemainderApportionment_sumsToExactly10000Bp() {
        // Three equal slices of 100
        val items = listOf(
            Pair(1L, 100L),
            Pair(2L, 100L),
            Pair(3L, 100L)
        )
        val result = ChartMath.apportionBasisPoints(items)
        assertThat(result.values.sum()).isEqualTo(10_000)
        // Floor of 1/3 is 3333; remainders are 1, 1, 1; two get +1 by tie-break
        assertThat(result.values).containsExactly(3_334, 3_333, 3_333)
    }

    @Test
    fun testD2_LargestRemainderApportionment_formatting() {
        assertThat(ChartMath.formatBasisPoints(3_334)).isEqualTo("33.34%")
        assertThat(ChartMath.formatBasisPoints(3_333)).isEqualTo("33.33%")
        assertThat(ChartMath.formatBasisPoints(5_000)).isEqualTo("50.00%")
        assertThat(ChartMath.formatBasisPoints(10_000)).isEqualTo("100.00%")
        assertThat(ChartMath.formatBasisPoints(0)).isEqualTo("0.00%")
        assertThat(ChartMath.formatBasisPoints(50)).isEqualTo("0.50%")
    }

    @Test
    fun testD2_LargestRemainderApportionment_zeroTotal() {
        val items = listOf(
            Pair(1L, 0L),
            Pair(2L, 0L)
        )
        val result = ChartMath.apportionBasisPoints(items)
        assertThat(result[1L]).isEqualTo(0)
        assertThat(result[2L]).isEqualTo(0)
    }

    // --- D3: Pace ---
    @Test
    fun testD3_Pace_calculation() {
        val startAt = 1_000_000_000L
        val totalDays = 30L
        val endAt = startAt + totalDays * ChartMath.MILLIS_PER_IST_DAY
        val budget = 30_000_00L // ₹30,000

        // Day 1
        val nowDay1 = startAt + 1000L // early day 1
        val (elapsed1, expectedSpend1) = ChartMath.computePace(startAt, endAt, nowDay1, budget)
        assertThat(elapsed1).isEqualTo(1L)
        assertThat(expectedSpend1).isEqualTo(1_000_00L) // 30,000 * 1 / 30 = 1,000

        // Day 15
        val nowDay15 = startAt + 14L * ChartMath.MILLIS_PER_IST_DAY + 5000L
        val (elapsed15, expectedSpend15) = ChartMath.computePace(startAt, endAt, nowDay15, budget)
        assertThat(elapsed15).isEqualTo(15L)
        assertThat(expectedSpend15).isEqualTo(15_000_00L)

        // Clamp at end
        val nowAfterEnd = endAt + 10_000L
        val (elapsedEnd, expectedSpendEnd) = ChartMath.computePace(startAt, endAt, nowAfterEnd, budget)
        assertThat(elapsedEnd).isEqualTo(30L)
        assertThat(expectedSpendEnd).isEqualTo(30_000_00L)
    }

    // --- D4: Projection ---
    @Test
    fun testD4_Projection_rules() {
        val totalDays = 30L

        // Less than 5 elapsed days -> null
        assertThat(ChartMath.computeProjection(spentPaise = 5_000_00L, elapsedDays = 4L, totalDays = totalDays)).isNull()

        // 5 days: spent ₹5,000 -> projected = floor(5000 * 30 / 5) = ₹30,000
        val proj5 = ChartMath.computeProjection(spentPaise = 5_000_00L, elapsedDays = 5L, totalDays = totalDays)
        assertThat(proj5).isNotNull()
        assertThat(proj5).isEqualTo(30_000_00L)

        // 10 days: spent ₹12,000 -> projected = floor(12000 * 30 / 10) = ₹36,000
        val proj10 = ChartMath.computeProjection(spentPaise = 12_000_00L, elapsedDays = 10L, totalDays = totalDays)
        assertThat(proj10).isEqualTo(36_000_00L)
    }

    // --- D5: Nice Axis Ticks ---
    @Test
    fun testD5_NiceAxisTicks_zeroBaselineAndMax() {
        val ticks = ChartMath.computeNiceTicks(minPaise = 0L, maxPaise = 85_000_00L)
        // 85,000: step should yield <= 5 intervals.
        assertThat(ticks.ticks).contains(0L)
        assertThat(ticks.maxPaise).isAtLeast(85_000_00L)
        val intervals = (ticks.maxPaise - ticks.minPaise) / ticks.stepPaise
        assertThat(intervals).isAtMost(5L)
        assertThat(ticks.stepPaise).isGreaterThan(0L)
    }

    @Test
    fun testD5_NiceAxisTicks_negativeValues() {
        val ticks = ChartMath.computeNiceTicks(minPaise = -40_000_00L, maxPaise = 80_000_00L)
        assertThat(ticks.ticks).contains(0L)
        assertThat(ticks.minPaise).isAtMost(-40_000_00L)
        assertThat(ticks.maxPaise).isAtLeast(80_000_00L)
    }

    // --- D6: Compact Indian Labels ---
    @Test
    fun testD6_CompactIndianLabels_allRanges() {
        // Under ₹1,000
        assertThat(MoneyFormatter.formatCompactIndian(0L)).isEqualTo("₹0")
        assertThat(MoneyFormatter.formatCompactIndian(50_00L)).isEqualTo("₹50")
        assertThat(MoneyFormatter.formatCompactIndian(999_00L)).isEqualTo("₹999")

        // Under ₹1,00,000 (K)
        assertThat(MoneyFormatter.formatCompactIndian(1_000_00L)).isEqualTo("₹1K")
        assertThat(MoneyFormatter.formatCompactIndian(1_500_00L)).isEqualTo("₹1.5K")
        assertThat(MoneyFormatter.formatCompactIndian(12_000_00L)).isEqualTo("₹12K")
        assertThat(MoneyFormatter.formatCompactIndian(12_340_00L)).isEqualTo("₹12.3K")
        assertThat(MoneyFormatter.formatCompactIndian(99_900_00L)).isEqualTo("₹99.9K")

        // Under ₹1,00,00,000 (L)
        assertThat(MoneyFormatter.formatCompactIndian(1_00_000_00L)).isEqualTo("₹1L")
        assertThat(MoneyFormatter.formatCompactIndian(1_50_000_00L)).isEqualTo("₹1.5L")
        assertThat(MoneyFormatter.formatCompactIndian(25_00_000_00L)).isEqualTo("₹25L")
        assertThat(MoneyFormatter.formatCompactIndian(99_90_000_00L)).isEqualTo("₹99.9L")

        // ₹1 Cr and above (Cr)
        assertThat(MoneyFormatter.formatCompactIndian(1_00_00_000_00L)).isEqualTo("₹1Cr")
        assertThat(MoneyFormatter.formatCompactIndian(2_50_00_000_00L)).isEqualTo("₹2.5Cr")
        assertThat(MoneyFormatter.formatCompactIndian(100_00_00_000_00L)).isEqualTo("₹100Cr")

        // Negatives
        assertThat(MoneyFormatter.formatCompactIndian(-500_00L)).isEqualTo("-₹500")
        assertThat(MoneyFormatter.formatCompactIndian(-15_000_00L)).isEqualTo("-₹15K")
        assertThat(MoneyFormatter.formatCompactIndian(-2_50_000_00L)).isEqualTo("-₹2.5L")
        assertThat(MoneyFormatter.formatCompactIndian(-1_00_00_000_00L)).isEqualTo("-₹1Cr")
    }

    // --- D7: Money Animation Interpolation ---
    @Test
    fun testD7_MoneyAnimation_permilleInterpolation() {
        val start = 1_000_00L
        val end = 2_000_00L

        assertThat(ChartMath.interpolateMoney(start, end, 0)).isEqualTo(start)
        assertThat(ChartMath.interpolateMoney(start, end, 500)).isEqualTo(1_500_00L)
        assertThat(ChartMath.interpolateMoney(start, end, 1000)).isEqualTo(end)

        // Decreasing
        assertThat(ChartMath.interpolateMoney(end, start, 250)).isEqualTo(1_750_00L)
    }

    // --- I11: IST Day Duration Invariant ---
    @Test
    fun testI11_EveryIstDayIsExactly86400000Ms() {
        assertThat(ChartMath.MILLIS_PER_IST_DAY).isEqualTo(86_400_000L)

        // Assert across 365 days of 2026, 366 days of leap year 2028
        val testDates = listOf(
            LocalDate.of(2026, 1, 1),
            LocalDate.of(2026, 2, 28),
            LocalDate.of(2026, 3, 1),
            LocalDate.of(2028, 2, 28), // Leap year 2028
            LocalDate.of(2028, 2, 29),
            LocalDate.of(2028, 3, 1),
            LocalDate.of(2028, 12, 31)
        )

        for (date in testDates) {
            val startOfDay = ZonedDateTime.of(date.atStartOfDay(), kolkataZone).toInstant().toEpochMilli()
            val nextDay = ZonedDateTime.of(date.plusDays(1).atStartOfDay(), kolkataZone).toInstant().toEpochMilli()
            val diffMs = nextDay - startOfDay
            assertThat(diffMs).isEqualTo(86_400_000L)
        }
    }
}
