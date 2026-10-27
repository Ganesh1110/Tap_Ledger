package com.tapledger.domain.calculator

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LoanCalculatorTest {

    private val calculator = LoanCalculator()

    @Test
    fun testEmiCalculationGoldenValue() {
        // Principal: ₹100,000 (10,000,000 paise)
        // Rate: 12% p.a. (1200 bp)
        // Tenure: 12 months
        // Standard mathematical formula: EMI = 100,000 * 0.01 * 1.01^12 / (1.01^12 - 1) = ₹8,884.88 -> 888,488 paise
        val emi = calculator.calculateEmi(
            principalPaise = 10_000_000L,
            annualRateBp = 1200,
            tenureMonths = 12
        )
        // Check within +/- 2 paise of analytical value (888,488 paise)
        assertTrue("EMI $emi should be near 888488", emi in 888_480L..888_500L)
    }

    @Test
    fun testAmortizationScheduleReconciliation() {
        val principal = 5_000_000L // ₹50,000
        val rateBp = 1000 // 10%
        val tenure = 6
        val schedule = calculator.generateAmortizationSchedule(
            principalPaise = principal,
            annualRateBp = rateBp,
            tenureMonths = tenure,
            startDateMs = 1700000000000L
        )

        assertEquals(6, schedule.size)
        // Verify final closing balance is exactly 0
        assertEquals(0L, schedule.last().closingPrincipalPaise)

        // Verify sum of principal paid equals original principal
        val totalPrincipalPaid = schedule.sumOf { it.principalPaidPaise }
        assertEquals(principal, totalPrincipalPaid)
    }

    @Test
    fun testAffordabilitySafe() {
        val income = 10_000_000L // ₹100,000
        val existingDebt = 500_000L // ₹5,000
        val result = calculator.checkAffordability(
            monthlyIncomePaise = income,
            existingMonthlyDebtPaise = existingDebt,
            newPrincipalPaise = 10_000_000L, // ₹100,000
            annualRateBp = 1000,
            tenureMonths = 12
        )
        // New EMI approx ₹8,790. Total debt approx ₹13,790.
        // DTI approx 13.8% -> Safe (<30%)
        assertEquals(AffordabilityStatus.SAFE, result.status)
        assertTrue(result.explanation.contains("leaves"))
    }

    @Test
    fun testAffordabilityHighRisk() {
        val income = 5_000_000L // ₹50,000
        val existingDebt = 2_000_000L // ₹20,000
        val result = calculator.checkAffordability(
            monthlyIncomePaise = income,
            existingMonthlyDebtPaise = existingDebt,
            newPrincipalPaise = 20_000_000L, // ₹200,000
            annualRateBp = 1400,
            tenureMonths = 24
        )
        // Total debt exceeds 45% DTI
        assertEquals(AffordabilityStatus.HIGH_RISK, result.status)
    }

    @Test
    fun testPartPrepaymentSimulator() {
        val outstanding = 10_000_000L // ₹100,000
        val rateBp = 1200
        val emi = 888_488L
        val remainingTenure = 12
        val prepayment = 3_000_000L // ₹30,000

        val result = calculator.simulatePartPrepayment(
            currentOutstandingPaise = outstanding,
            annualRateBp = rateBp,
            currentEmiPaise = emi,
            remainingTenureMonths = remainingTenure,
            prepaymentPaise = prepayment
        )

        // Prepayment must save months and interest
        assertTrue(result.tenureOptionMonthsSaved > 0)
        assertTrue(result.tenureOptionInterestSavedPaise > 0L)
        assertTrue(result.emiOptionMonthlySavingsPaise > 0L)
        assertTrue(result.emiOptionInterestSavedPaise > 0L)
    }
}
