package com.tapledger.domain.calculator

import com.tapledger.core.database.entity.FixedDepositEntity
import com.tapledger.core.model.CompoundingFrequency
import org.junit.Assert.assertEquals
import org.junit.Test

class FdCalculatorTest {

    private val calculator = FdCalculator()

    @Test
    fun testSimpleInterestOneYearGoldenValue() {
        // Principal = ₹100,000 (10,000,000 paise)
        // Rate = 7.50% (750 bp)
        // Days = 365
        // Expected Interest = ₹7,500 (750,000 paise)
        // Expected Maturity = ₹107,500 (10,750,000 paise)
        val maturity = calculator.calculateMaturityForDays(
            principalPaise = 10_000_000L,
            rateBp = 750,
            totalDays = 365L,
            compounding = CompoundingFrequency.SIMPLE
        )
        assertEquals(10_750_000L, maturity)
    }

    @Test
    fun testQuarterlyCompoundingOneYearGoldenValue() {
        // Principal = ₹100,000 (10,000,000 paise)
        // Rate = 7.50% (750 bp)
        // 4 quarters: (1 + 0.075/4)^4 = 1.01875^4 = 1.077135866...
        // Expected Maturity = 10,771,359 paise (₹107,713.59)
        val maturity = calculator.calculateMaturityForDays(
            principalPaise = 10_000_000L,
            rateBp = 750,
            totalDays = 365L,
            compounding = CompoundingFrequency.QUARTERLY
        )
        assertEquals(10_771_359L, maturity)
    }

    @Test
    fun testQuarterlyCompoundingTwoYearsGoldenValue() {
        // Principal = ₹50,000 (5,000,000 paise)
        // Rate = 6.00% (600 bp)
        // 8 quarters: (1 + 0.06/4)^8 = 1.015^8 = 1.126492586...
        // Expected Maturity = 5,632,463 paise (₹56,324.63)
        val maturity = calculator.calculateMaturityForDays(
            principalPaise = 5_000_000L,
            rateBp = 600,
            totalDays = 730L,
            compounding = CompoundingFrequency.QUARTERLY
        )
        assertEquals(5_632_463L, maturity)
    }

    @Test
    fun testAnnualCompoundingGoldenValue() {
        // Principal = ₹200,000 (20,000,000 paise)
        // Rate = 8.00% (800 bp)
        // 1 year: 20,000,000 * 1.08 = 21,600,000 paise
        val maturity = calculator.calculateMaturityForDays(
            principalPaise = 20_000_000L,
            rateBp = 800,
            totalDays = 365L,
            compounding = CompoundingFrequency.ANNUAL
        )
        assertEquals(21_600_000L, maturity)
    }

    @Test
    fun testAccruedInterestCalculation() {
        val startMs = 1_000_000_000L
        val maturityMs = startMs + (365L * 86_400_000L) // 1 year
        val halfYearMs = startMs + (182L * 86_400_000L) // ~half year

        val fd = FixedDepositEntity(
            id = 1L,
            name = "SBI FD",
            principalPaise = 10_000_000L, // ₹100,000
            rateBp = 750,
            startMs = startMs,
            maturityMs = maturityMs,
            compounding = CompoundingFrequency.SIMPLE,
            goalId = null,
            createdAt = startMs
        )

        // At full maturity: accrued interest should be 750,000 paise
        val fullAccrued = calculator.calculateAccruedInterest(fd, asOfMs = maturityMs)
        assertEquals(750_000L, fullAccrued)

        // At start: accrued interest is 0
        val zeroAccrued = calculator.calculateAccruedInterest(fd, asOfMs = startMs)
        assertEquals(0L, zeroAccrued)

        // After maturity: capped at full maturity interest
        val afterMaturityAccrued = calculator.calculateAccruedInterest(fd, asOfMs = maturityMs + 10_000_000L)
        assertEquals(750_000L, afterMaturityAccrued)
    }

    @Test
    fun testDaysToMaturity() {
        val now = 10_000_000_000L
        val tenDaysLater = now + (10L * 86_400_000L)
        assertEquals(10L, calculator.calculateDaysToMaturity(tenDaysLater, now))
        assertEquals(0L, calculator.calculateDaysToMaturity(now - 1000L, now))
    }

    @Test
    fun testEdgeCasesZeroOrNegative() {
        assertEquals(0L, calculator.calculateMaturityForDays(0L, 750, 365L, CompoundingFrequency.QUARTERLY))
        assertEquals(100_000L, calculator.calculateMaturityForDays(100_000L, 0, 365L, CompoundingFrequency.QUARTERLY))
        assertEquals(100_000L, calculator.calculateMaturityForDays(100_000L, 750, 0L, CompoundingFrequency.QUARTERLY))
    }
}
