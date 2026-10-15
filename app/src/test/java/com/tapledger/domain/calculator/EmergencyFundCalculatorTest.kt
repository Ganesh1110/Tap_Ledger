package com.tapledger.domain.calculator

import com.tapledger.core.database.entity.MonthCloseEntity
import com.tapledger.core.model.MonthCloseReconciliationStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class EmergencyFundCalculatorTest {

    private val calculator = EmergencyFundCalculator()

    @Test
    fun testZeroSavingsOrEmptySpends() {
        assertEquals(0L, calculator.calculateMonthsCoveredTenths(0L, listOf(100_000L, 200_000L)))
        assertEquals(0L, calculator.calculateMonthsCoveredTenths(500_000L, emptyList()))
        assertEquals(0L, calculator.calculateMonthsCoveredTenths(500_000L, listOf(0L, 0L)))
    }

    @Test
    fun testExactMonthsCoveredCalculation() {
        // Average spend per month: (1,000,000 + 1,000,000 + 1,000,000) / 3 = 1,000,000 paise (₹10,000)
        // Total savings: 3,200,000 paise (₹32,000)
        // Months covered: 3.2 months -> 32 tenths
        val monthlySpends = listOf(1_000_000L, 1_000_000L, 1_000_000L)
        val tenths = calculator.calculateMonthsCoveredTenths(3_200_000L, monthlySpends)
        assertEquals(32L, tenths)
        assertEquals("3.2 months", calculator.formatDisplay(tenths))
    }

    @Test
    fun testRoundingHalfUp() {
        // Average spend: 300_000 paise over 3 months = 100_000 paise/month
        // Total savings: 155_000 paise -> 1.55 months -> rounds to 1.6 months (16 tenths)
        val monthlySpends = listOf(100_000L, 100_000L, 100_000L)
        val tenths = calculator.calculateMonthsCoveredTenths(155_000L, monthlySpends)
        assertEquals(16L, tenths)
    }

    @Test
    fun testFormatSingularAndPlural() {
        assertEquals("1.0 month", calculator.formatDisplay(10L))
        assertEquals("6.5 months", calculator.formatDisplay(65L))
        assertEquals("0.0 months", calculator.formatDisplay(0L))
    }

    @Test
    fun testCalculateFromMonthClosesTakesLookback() {
        val c1 = MonthCloseEntity(
            budgetPeriodId = 1L,
            closedAt = 1000L,
            plannedIncomePaise = 0L,
            actualIncomePaise = 0L,
            totalBudgetedPaise = 0L,
            totalSpentPaise = 2_000_000L,
            totalSavedPaise = 0L,
            reconciliationStatus = MonthCloseReconciliationStatus.RECONCILED,
            overrideReason = null,
            closingBalancesJson = "{}"
        )
        val c2 = MonthCloseEntity(
            budgetPeriodId = 2L,
            closedAt = 2000L,
            plannedIncomePaise = 0L,
            actualIncomePaise = 0L,
            totalBudgetedPaise = 0L,
            totalSpentPaise = 1_000_000L,
            totalSavedPaise = 0L,
            reconciliationStatus = MonthCloseReconciliationStatus.RECONCILED,
            overrideReason = null,
            closingBalancesJson = "{}"
        )
        val c3 = MonthCloseEntity(
            budgetPeriodId = 3L,
            closedAt = 3000L,
            plannedIncomePaise = 0L,
            actualIncomePaise = 0L,
            totalBudgetedPaise = 0L,
            totalSpentPaise = 1_000_000L,
            totalSavedPaise = 0L,
            reconciliationStatus = MonthCloseReconciliationStatus.RECONCILED,
            overrideReason = null,
            closingBalancesJson = "{}"
        )

        // Lookback 2: takes c3 (3000L) and c2 (2000L), spends = 1,000,000 + 1,000,000 = 2,000,000. Avg = 1,000,000.
        // Savings = 3,000,000 -> 3.0 months = 30 tenths
        val tenths = calculator.calculateFromMonthCloses(
            totalSavingsPaise = 3_000_000L,
            monthCloses = listOf(c1, c2, c3),
            lookbackMonths = 2
        )
        assertEquals(30L, tenths)
    }
}
