package com.tapledger.domain.calculator

import org.junit.Assert.assertEquals
import org.junit.Test

class HabitCalculatorTest {

    private val calculator = HabitCalculator()

    @Test
    fun testRoundUpToNearest10() {
        // Step = 1000 paise (₹10)
        // Expense = 14,200 paise (₹142) -> remainder 200 -> round-up 800 paise (₹8)
        assertEquals(800L, calculator.calculateRoundUpPaise(14_200L, 1_000L))

        // Already multiple: 15,000 paise (₹150) -> 0 paise
        assertEquals(0L, calculator.calculateRoundUpPaise(15_000L, 1_000L))

        // Zero or negative
        assertEquals(0L, calculator.calculateRoundUpPaise(0L, 1_000L))
        assertEquals(0L, calculator.calculateRoundUpPaise(-500L, 1_000L))
    }

    @Test
    fun testRoundUpToNearest50And100() {
        // Step = 5000 paise (₹50)
        // Expense = 3,400 paise (₹34) -> remainder 3400 -> round-up 1,600 paise (₹16)
        assertEquals(1_600L, calculator.calculateRoundUpPaise(3_400L, 5_000L))

        // Step = 10000 paise (₹100)
        // Expense = 24,000 paise (₹240) -> remainder 4000 -> round-up 6,000 paise (₹60)
        assertEquals(6_000L, calculator.calculateRoundUpPaise(24_000L, 10_000L))
    }

    @Test
    fun testIncomeRuleCalculation() {
        // Income = ₹60,000 (6,000,000 paise), threshold = ₹50,000 (5,000,000 paise), rate = 20% (2000 bp)
        // Surplus = 1,000,000 paise -> 20% = 200,000 paise (₹2,000)
        val result = calculator.calculateIncomeRulePaise(
            incomePaise = 6_000_000L,
            thresholdPaise = 5_000_000L,
            rateBp = 2_000
        )
        assertEquals(200_000L, result)

        // Below or equal threshold: zero
        assertEquals(0L, calculator.calculateIncomeRulePaise(5_000_000L, 5_000_000L, 2_000))
        assertEquals(0L, calculator.calculateIncomeRulePaise(4_000_000L, 5_000_000L, 2_000))
    }
}
