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

    @Test
    fun testZeroInterestLoan() {
        val principal = 12_000_000L // ₹120,000
        val tenure = 12
        val emi = calculator.calculateEmi(principal, annualRateBp = 0, tenureMonths = tenure)
        assertEquals(1_000_000L, emi) // Exactly ₹10,000 / month

        val schedule = calculator.generateAmortizationSchedule(
            principalPaise = principal,
            annualRateBp = 0,
            tenureMonths = tenure,
            startDateMs = 1700000000000L
        )
        assertEquals(12, schedule.size)
        schedule.forEach { inst ->
            assertEquals(0L, inst.interestPaise)
            assertEquals(1_000_000L, inst.principalPaidPaise)
        }
        assertEquals(0L, schedule.last().closingPrincipalPaise)
    }

    @Test
    fun testAccruedInterestDailyAndMonthlyProRata() {
        val principal = 10_000_000L // ₹100,000
        val rateBp = 1200 // 12% p.a.
        val days = 15

        // Daily reducing: (10,000,000 * 1200 * 15 + 1,825,000) / 3,650,000 = 49,315 paise (~₹493.15)
        val dailyAccrued = calculator.calculateAccruedInterest(
            principalPaise = principal,
            annualRateBp = rateBp,
            daysElapsed = days,
            method = InterestCalculationMethod.DAILY_REDUCING
        )
        assertTrue("Daily interest should be near 49,315 paise", dailyAccrued in 49_300L..49_350L)

        // Monthly reducing (30-day month convention): (10,000,000 * 1200 * 15 + 1,800,000) / 3,600,000 = 50,000 paise (₹500.00)
        val monthlyAccrued = calculator.calculateAccruedInterest(
            principalPaise = principal,
            annualRateBp = rateBp,
            daysElapsed = days,
            method = InterestCalculationMethod.MONTHLY_REDUCING
        )
        assertEquals(50_000L, monthlyAccrued)
    }

    @Test
    fun testPrepaymentAllocationWaterfall() {
        // Outstanding: ₹100,000 (10,000,000 paise)
        // Rate: 12% p.a.
        // Payment: ₹50,000 (5,000,000 paise)
        // Days elapsed: 15 days -> Accrued Interest: 50,000 paise (₹500)
        // Fixed fee: ₹200 (20,000 paise)
        // Percentage fee: 1% (100 bp) -> 1% of ₹50,000 = ₹500 (50,000 paise)
        // Total fees: 70,000 paise (₹700)
        // Waterfall:
        // 1. Fee: ₹700 (70,000 paise)
        // 2. Accrued Interest: ₹500 (50,000 paise)
        // 3. Principal Reduction: ₹50,000 - ₹700 - ₹500 = ₹48,800 (4,880,000 paise)
        // New Principal: ₹100,000 - ₹48,800 = ₹51,200 (5,120,000 paise)
        val allocation = calculator.calculatePrepaymentAllocation(
            totalPaymentPaise = 5_000_000L,
            currentOutstandingPaise = 10_000_000L,
            annualRateBp = 1200,
            daysElapsedSinceLastPayment = 15,
            fixedFeePaise = 20_000L,
            percentageFeeBp = 100,
            method = InterestCalculationMethod.MONTHLY_REDUCING
        )

        assertEquals(70_000L, allocation.feeChargesPaise)
        assertEquals(50_000L, allocation.accruedInterestPaise)
        assertEquals(4_880_000L, allocation.principalReductionPaise)
        assertEquals(5_120_000L, allocation.newOutstandingPrincipalPaise)
    }

    @Test
    fun testForeclosureSettlementCalculation() {
        val outstanding = 10_000_000L // ₹100,000
        val rateBp = 1200 // 12%
        val days = 15 // Accrued interest = ₹500 (50,000 paise)
        val feePercentBp = 200 // 2% foreclosure fee = ₹2,000 (200,000 paise)
        val fixedFee = 50_000L // ₹500

        val settlement = calculator.calculateForeclosureSettlement(
            outstandingPrincipalPaise = outstanding,
            annualRateBp = rateBp,
            daysElapsedSinceLastPayment = days,
            fixedFeePaise = fixedFee,
            percentageFeeBp = feePercentBp,
            method = InterestCalculationMethod.MONTHLY_REDUCING
        )

        assertEquals(outstanding, settlement.outstandingPrincipalPaise)
        assertEquals(50_000L, settlement.accruedInterestPaise)
        assertEquals(250_000L, settlement.foreclosureFeePaise) // ₹2,000 + ₹500
        assertEquals(10_300_000L, settlement.totalSettlementAmountPaise) // ₹103,000
    }

    @Test
    fun testConfigurableDtiThresholds() {
        val income = 10_000_000L // ₹100,000
        val existingDebt = 2_000_000L // ₹20,000
        // New loan EMI approx ₹8,790. Total debt approx ₹28,790 (28.79% DTI)

        // With default 30% threshold: 28.79% <= 30% -> SAFE
        val defaultResult = calculator.checkAffordability(
            monthlyIncomePaise = income,
            existingMonthlyDebtPaise = existingDebt,
            newPrincipalPaise = 10_000_000L,
            annualRateBp = 1000,
            tenureMonths = 12,
            safeDtiThresholdBp = 3000,
            moderateDtiThresholdBp = 4500
        )
        assertEquals(AffordabilityStatus.SAFE, defaultResult.status)

        // With stricter custom safe threshold 25%: 28.79% > 25% -> MODERATE
        val strictResult = calculator.checkAffordability(
            monthlyIncomePaise = income,
            existingMonthlyDebtPaise = existingDebt,
            newPrincipalPaise = 10_000_000L,
            annualRateBp = 1000,
            tenureMonths = 12,
            safeDtiThresholdBp = 2500,
            moderateDtiThresholdBp = 4500
        )
        assertEquals(AffordabilityStatus.MODERATE, strictResult.status)
    }
}
