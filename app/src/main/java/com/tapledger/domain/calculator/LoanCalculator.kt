package com.tapledger.domain.calculator

import com.tapledger.core.util.DateTimeUtils
import javax.inject.Inject
import javax.inject.Singleton

enum class AffordabilityStatus {
    SAFE,
    MODERATE,
    HIGH_RISK
}

data class AmortizationInstallment(
    val monthIndex: Int,
    val dueDateMs: Long,
    val openingPrincipalPaise: Long,
    val emiPaise: Long,
    val interestPaise: Long,
    val principalPaidPaise: Long,
    val closingPrincipalPaise: Long
)

data class LoanAffordabilityResult(
    val monthlyIncomePaise: Long,
    val newEmiPaise: Long,
    val totalMonthlyDebtPaise: Long,
    val dtiBasisPoints: Int,
    val needs50PoolPaise: Long,
    val wants30PoolPaise: Long,
    val savings20PoolPaise: Long,
    val remainingNeedsPoolPaise: Long,
    val status: AffordabilityStatus,
    val explanation: String
)

data class PrepaymentSimulationResult(
    val prepaymentPaise: Long,
    val originalOutstandingPaise: Long,
    val originalEmiPaise: Long,
    val originalRemainingTenureMonths: Int,
    // Option 1: Reduce Tenure (Keep EMI same)
    val tenureOptionNewTenureMonths: Int,
    val tenureOptionMonthsSaved: Int,
    val tenureOptionInterestSavedPaise: Long,
    // Option 2: Reduce EMI (Keep tenure same)
    val emiOptionNewEmiPaise: Long,
    val emiOptionMonthlySavingsPaise: Long,
    val emiOptionInterestSavedPaise: Long
)

/**
 * Pure financial domain calculator for loans, amortization schedules,
 * 50/30/20 affordability checks, and part-prepayment simulations.
 *
 * Hard Invariant: Strictly zero floating-point arithmetic (zero Double, Float, BigDecimal).
 * All money is signed 64-bit Long paise, and percentages are basis points (Int, 10,000 = 100%).
 */
@Singleton
class LoanCalculator @Inject constructor() {

    private val scale: Long = 1_000_000_000L // 10^9 fixed-point scale
    private val monthsPerYear: Long = 12L
    private val bpScale: Long = 10_000L
    private val annualRateDivisor: Long = bpScale * monthsPerYear // 120,000

    /**
     * Calculates monthly Equated Monthly Installment (EMI) in paise.
     * Uses zero-float scaled fixed-point recurrence for (1 + r)^n.
     */
    fun calculateEmi(principalPaise: Long, annualRateBp: Int, tenureMonths: Int): Long {
        if (principalPaise <= 0L || tenureMonths <= 0) return 0L
        if (annualRateBp <= 0) {
            return (principalPaise + (tenureMonths / 2L)) / tenureMonths.toLong()
        }

        val rateScaled = (annualRateBp.toLong() * scale) / annualRateDivisor
        var factor = scale
        for (i in 0 until tenureMonths) {
            val delta = (factor * rateScaled) / scale
            factor += delta
        }

        val factorMinusScale = factor - scale
        if (factorMinusScale <= 0L) {
            return (principalPaise + (tenureMonths / 2L)) / tenureMonths.toLong()
        }

        val termPaise = (principalPaise * rateScaled) / scale
        val numerator = termPaise * factor
        val emi = (numerator + (factorMinusScale / 2L)) / factorMinusScale
        return maxOf(1L, emi)
    }

    /**
     * Generates a month-by-month amortization schedule splitting principal from interest.
     * Guarantees zero balance drift: final installment reconciles to exact 0 paise.
     */
    fun generateAmortizationSchedule(
        principalPaise: Long,
        annualRateBp: Int,
        tenureMonths: Int,
        startDateMs: Long
    ): List<AmortizationInstallment> {
        if (principalPaise <= 0L || tenureMonths <= 0) return emptyList()

        val emi = calculateEmi(principalPaise, annualRateBp, tenureMonths)
        val schedule = mutableListOf<AmortizationInstallment>()

        var currentBalance = principalPaise
        val approxMillisPerMonth = 30L * 86_400_000L

        for (m in 1..tenureMonths) {
            if (currentBalance <= 0L) break

            val interestNum = currentBalance * annualRateBp.toLong()
            val interestPaise = (interestNum + (annualRateDivisor / 2L)) / annualRateDivisor

            val isLastMonth = (m == tenureMonths) || (currentBalance <= (emi - interestPaise))
            val principalPaid = if (isLastMonth) currentBalance else minOf(currentBalance, maxOf(0L, emi - interestPaise))
            val actualEmi = principalPaid + interestPaise
            val closingBalance = maxOf(0L, currentBalance - principalPaid)
            val dueDateMs = startDateMs + (m.toLong() * approxMillisPerMonth)

            schedule.add(
                AmortizationInstallment(
                    monthIndex = m,
                    dueDateMs = dueDateMs,
                    openingPrincipalPaise = currentBalance,
                    emiPaise = actualEmi,
                    interestPaise = interestPaise,
                    principalPaidPaise = principalPaid,
                    closingPrincipalPaise = closingBalance
                )
            )

            currentBalance = closingBalance
        }

        return schedule
    }

    /**
     * Pre-loan affordability check tied to the 50/30/20 budget framework.
     */
    fun checkAffordability(
        monthlyIncomePaise: Long,
        existingMonthlyDebtPaise: Long,
        newPrincipalPaise: Long,
        annualRateBp: Int,
        tenureMonths: Int
    ): LoanAffordabilityResult {
        val newEmi = calculateEmi(newPrincipalPaise, annualRateBp, tenureMonths)
        val totalMonthlyDebt = existingMonthlyDebtPaise + newEmi

        val needsPool = (monthlyIncomePaise * 50L) / 100L
        val wantsPool = (monthlyIncomePaise * 30L) / 100L
        val savingsPool = (monthlyIncomePaise * 20L) / 100L

        val dtiBp = if (monthlyIncomePaise > 0L) {
            ((totalMonthlyDebt * bpScale) / monthlyIncomePaise).toInt()
        } else {
            10_000
        }

        val remainingNeeds = needsPool - totalMonthlyDebt

        val status = when {
            dtiBp <= 3000 -> AffordabilityStatus.SAFE
            dtiBp <= 4500 -> AffordabilityStatus.MODERATE
            else -> AffordabilityStatus.HIGH_RISK
        }

        val dtiPercentWhole = dtiBp / 100
        val dtiPercentFraction = dtiBp % 100
        val dtiFormatted = if (dtiPercentFraction == 0) "$dtiPercentWhole%" else String.format(java.util.Locale.ROOT, "%d.%02d%%", dtiPercentWhole, dtiPercentFraction)

        val emiRupees = newEmi / 100L
        val remainingNeedsRupees = maxOf(0L, remainingNeeds) / 100L
        val wantsRupees = wantsPool / 100L

        val explanation = when (status) {
            AffordabilityStatus.SAFE ->
                "EMI of ₹$emiRupees is $dtiFormatted of your income and leaves ₹$remainingNeedsRupees for other Needs and ₹$wantsRupees for Wants."
            AffordabilityStatus.MODERATE ->
                "EMI of ₹$emiRupees consumes $dtiFormatted of your monthly income. Remaining Needs pool is tight at ₹$remainingNeedsRupees."
            AffordabilityStatus.HIGH_RISK ->
                "High Debt Alert! Total debt obligations will take $dtiFormatted of your monthly income, exceeding safe 50% Needs allocations."
        }

        return LoanAffordabilityResult(
            monthlyIncomePaise = monthlyIncomePaise,
            newEmiPaise = newEmi,
            totalMonthlyDebtPaise = totalMonthlyDebt,
            dtiBasisPoints = dtiBp,
            needs50PoolPaise = needsPool,
            wants30PoolPaise = wantsPool,
            savings20PoolPaise = savingsPool,
            remainingNeedsPoolPaise = remainingNeeds,
            status = status,
            explanation = explanation
        )
    }

    /**
     * Part-prepayment simulator showing interest saved and months saved.
     */
    fun simulatePartPrepayment(
        currentOutstandingPaise: Long,
        annualRateBp: Int,
        currentEmiPaise: Long,
        remainingTenureMonths: Int,
        prepaymentPaise: Long
    ): PrepaymentSimulationResult {
        val effectivePrepayment = minOf(prepaymentPaise, currentOutstandingPaise)
        val newPrincipal = maxOf(0L, currentOutstandingPaise - effectivePrepayment)

        // Baseline total interest remaining without prepayment
        var baselineInterestTotal = 0L
        var balanceCursor = currentOutstandingPaise
        for (m in 1..remainingTenureMonths) {
            if (balanceCursor <= 0L) break
            val interest = (balanceCursor * annualRateBp.toLong() + (annualRateDivisor / 2L)) / annualRateDivisor
            baselineInterestTotal += interest
            val principalPaid = minOf(balanceCursor, maxOf(0L, currentEmiPaise - interest))
            balanceCursor -= principalPaid
        }

        // Option 1: Reduce Tenure (Keep EMI constant)
        var tenureOptionInterestTotal = 0L
        var tenureCursor = newPrincipal
        var newTenureMonths = 0
        if (newPrincipal > 0L) {
            while (tenureCursor > 0L && newTenureMonths < 600) {
                newTenureMonths++
                val interest = (tenureCursor * annualRateBp.toLong() + (annualRateDivisor / 2L)) / annualRateDivisor
                tenureOptionInterestTotal += interest
                val principalPaid = minOf(tenureCursor, maxOf(0L, currentEmiPaise - interest))
                if (principalPaid <= 0L) break // avoid infinite loop if EMI <= interest
                tenureCursor -= principalPaid
            }
        }
        val monthsSaved = maxOf(0, remainingTenureMonths - newTenureMonths)
        val tenureInterestSaved = maxOf(0L, baselineInterestTotal - tenureOptionInterestTotal)

        // Option 2: Reduce EMI (Keep tenure constant)
        val newEmi = if (newPrincipal > 0L && remainingTenureMonths > 0) {
            calculateEmi(newPrincipal, annualRateBp, remainingTenureMonths)
        } else 0L

        var emiOptionInterestTotal = 0L
        var emiCursor = newPrincipal
        for (m in 1..remainingTenureMonths) {
            if (emiCursor <= 0L) break
            val interest = (emiCursor * annualRateBp.toLong() + (annualRateDivisor / 2L)) / annualRateDivisor
            emiOptionInterestTotal += interest
            val principalPaid = minOf(emiCursor, maxOf(0L, newEmi - interest))
            emiCursor -= principalPaid
        }
        val monthlySavings = maxOf(0L, currentEmiPaise - newEmi)
        val emiInterestSaved = maxOf(0L, baselineInterestTotal - emiOptionInterestTotal)

        return PrepaymentSimulationResult(
            prepaymentPaise = effectivePrepayment,
            originalOutstandingPaise = currentOutstandingPaise,
            originalEmiPaise = currentEmiPaise,
            originalRemainingTenureMonths = remainingTenureMonths,
            tenureOptionNewTenureMonths = newTenureMonths,
            tenureOptionMonthsSaved = monthsSaved,
            tenureOptionInterestSavedPaise = tenureInterestSaved,
            emiOptionNewEmiPaise = newEmi,
            emiOptionMonthlySavingsPaise = monthlySavings,
            emiOptionInterestSavedPaise = emiInterestSaved
        )
    }
}
