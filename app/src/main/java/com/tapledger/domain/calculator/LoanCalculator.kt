package com.tapledger.domain.calculator

import com.tapledger.core.util.DateTimeUtils
import javax.inject.Inject
import javax.inject.Singleton

enum class AffordabilityStatus {
    SAFE,
    MODERATE,
    HIGH_RISK
}

enum class InterestCalculationMethod {
    MONTHLY_REDUCING,
    DAILY_REDUCING
}

enum class PrepaymentStrategy {
    REDUCE_TENURE,
    REDUCE_EMI
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

data class PrepaymentAllocationBreakdown(
    val totalCashPaidPaise: Long,
    val feeChargesPaise: Long,
    val accruedInterestPaise: Long,
    val principalReductionPaise: Long,
    val newOutstandingPrincipalPaise: Long
)

data class PrepaymentSimulationResult(
    val prepaymentPaise: Long,
    val originalOutstandingPaise: Long,
    val originalEmiPaise: Long,
    val originalRemainingTenureMonths: Int,
    val feeChargesPaise: Long = 0L,
    val accruedInterestPaise: Long = 0L,
    val netPrincipalReductionPaise: Long = 0L,
    // Option 1: Reduce Tenure (Keep EMI same)
    val tenureOptionNewTenureMonths: Int,
    val tenureOptionMonthsSaved: Int,
    val tenureOptionInterestSavedPaise: Long,
    val tenureOptionNetSavingsPaise: Long = 0L,
    // Option 2: Reduce EMI (Keep tenure same)
    val emiOptionNewEmiPaise: Long,
    val emiOptionMonthlySavingsPaise: Long,
    val emiOptionInterestSavedPaise: Long,
    val emiOptionNetSavingsPaise: Long = 0L
)

data class ForeclosureSettlement(
    val outstandingPrincipalPaise: Long,
    val accruedInterestPaise: Long,
    val foreclosureFeePaise: Long,
    val totalSettlementAmountPaise: Long
)

/**
 * Pure financial domain calculator for loans, amortization schedules,
 * 50/30/20 affordability checks, part-prepayment simulations, and foreclosure settlements.
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
    private val daysPerYear: Long = 365L
    private val dailyRateDivisor: Long = bpScale * daysPerYear // 3,650,000

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
     * Calculates accrued interest between installment dates or for an arbitrary number of elapsed days.
     */
    fun calculateAccruedInterest(
        principalPaise: Long,
        annualRateBp: Int,
        daysElapsed: Int,
        method: InterestCalculationMethod = InterestCalculationMethod.MONTHLY_REDUCING
    ): Long {
        if (principalPaise <= 0L || annualRateBp <= 0 || daysElapsed <= 0) return 0L

        return when (method) {
            InterestCalculationMethod.DAILY_REDUCING -> {
                val num = principalPaise * annualRateBp.toLong() * daysElapsed.toLong()
                (num + (dailyRateDivisor / 2L)) / dailyRateDivisor
            }
            InterestCalculationMethod.MONTHLY_REDUCING -> {
                val num = principalPaise * annualRateBp.toLong() * daysElapsed.toLong()
                val divisor = annualRateDivisor * 30L // 3,600,000 (30-day month convention)
                (num + (divisor / 2L)) / divisor
            }
        }
    }

    /**
     * Allocates a part-payment according to financial priority rules:
     * 1. Applicable Charges & Fees (Fixed + Percentage)
     * 2. Accrued Interest (if paying between installment dates)
     * 3. Principal Reduction
     */
    fun calculatePrepaymentAllocation(
        totalPaymentPaise: Long,
        currentOutstandingPaise: Long,
        annualRateBp: Int,
        daysElapsedSinceLastPayment: Int = 0,
        fixedFeePaise: Long = 0L,
        percentageFeeBp: Int = 0,
        method: InterestCalculationMethod = InterestCalculationMethod.MONTHLY_REDUCING
    ): PrepaymentAllocationBreakdown {
        if (totalPaymentPaise <= 0L || currentOutstandingPaise <= 0L) {
            return PrepaymentAllocationBreakdown(
                totalCashPaidPaise = 0L,
                feeChargesPaise = 0L,
                accruedInterestPaise = 0L,
                principalReductionPaise = 0L,
                newOutstandingPrincipalPaise = currentOutstandingPaise
            )
        }

        // 1. Calculate applicable fees
        val feeFromPercent = if (percentageFeeBp > 0) {
            (totalPaymentPaise * percentageFeeBp.toLong() + 5000L) / bpScale
        } else 0L
        val totalApplicableFee = fixedFeePaise + feeFromPercent
        val feeAllocated = minOf(totalPaymentPaise, totalApplicableFee)
        val remainingAfterFee = totalPaymentPaise - feeAllocated

        // 2. Calculate accrued interest
        val accruedInterest = calculateAccruedInterest(
            principalPaise = currentOutstandingPaise,
            annualRateBp = annualRateBp,
            daysElapsed = daysElapsedSinceLastPayment,
            method = method
        )
        val interestAllocated = minOf(remainingAfterFee, accruedInterest)
        val remainingAfterInterest = remainingAfterFee - interestAllocated

        // 3. Remainder reduces principal
        val principalReduction = minOf(currentOutstandingPaise, remainingAfterInterest)
        val newPrincipal = maxOf(0L, currentOutstandingPaise - principalReduction)

        return PrepaymentAllocationBreakdown(
            totalCashPaidPaise = totalPaymentPaise,
            feeChargesPaise = feeAllocated,
            accruedInterestPaise = interestAllocated,
            principalReductionPaise = principalReduction,
            newOutstandingPrincipalPaise = newPrincipal
        )
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

            val interestPaise = if (annualRateBp > 0) {
                val interestNum = currentBalance * annualRateBp.toLong()
                (interestNum + (annualRateDivisor / 2L)) / annualRateDivisor
            } else 0L

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
     * DTI thresholds are configurable (default 30% safe, 45% moderate).
     */
    fun checkAffordability(
        monthlyIncomePaise: Long,
        existingMonthlyDebtPaise: Long,
        newPrincipalPaise: Long,
        annualRateBp: Int,
        tenureMonths: Int,
        safeDtiThresholdBp: Int = 3000,
        moderateDtiThresholdBp: Int = 4500
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
            dtiBp <= safeDtiThresholdBp -> AffordabilityStatus.SAFE
            dtiBp <= moderateDtiThresholdBp -> AffordabilityStatus.MODERATE
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
     * Part-prepayment simulator showing interest saved, months saved, and net savings after fees.
     */
    fun simulatePartPrepayment(
        currentOutstandingPaise: Long,
        annualRateBp: Int,
        currentEmiPaise: Long,
        remainingTenureMonths: Int,
        prepaymentPaise: Long,
        daysElapsedSinceLastPayment: Int = 0,
        fixedFeePaise: Long = 0L,
        percentageFeeBp: Int = 0,
        method: InterestCalculationMethod = InterestCalculationMethod.MONTHLY_REDUCING
    ): PrepaymentSimulationResult {
        val allocation = calculatePrepaymentAllocation(
            totalPaymentPaise = prepaymentPaise,
            currentOutstandingPaise = currentOutstandingPaise,
            annualRateBp = annualRateBp,
            daysElapsedSinceLastPayment = daysElapsedSinceLastPayment,
            fixedFeePaise = fixedFeePaise,
            percentageFeeBp = percentageFeeBp,
            method = method
        )

        val newPrincipal = allocation.newOutstandingPrincipalPaise

        // Baseline total interest remaining without prepayment
        var baselineInterestTotal = 0L
        var balanceCursor = currentOutstandingPaise
        for (m in 1..remainingTenureMonths) {
            if (balanceCursor <= 0L) break
            val interest = if (annualRateBp > 0) {
                (balanceCursor * annualRateBp.toLong() + (annualRateDivisor / 2L)) / annualRateDivisor
            } else 0L
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
                val interest = if (annualRateBp > 0) {
                    (tenureCursor * annualRateBp.toLong() + (annualRateDivisor / 2L)) / annualRateDivisor
                } else 0L
                tenureOptionInterestTotal += interest
                val isLast = tenureCursor <= (currentEmiPaise - interest)
                val principalPaid = if (isLast) tenureCursor else minOf(tenureCursor, maxOf(0L, currentEmiPaise - interest))
                if (principalPaid <= 0L) break // avoid infinite loop if EMI <= interest
                tenureCursor -= principalPaid
            }
        }
        val monthsSaved = maxOf(0, remainingTenureMonths - newTenureMonths)
        val tenureInterestSaved = maxOf(0L, baselineInterestTotal - tenureOptionInterestTotal)
        val tenureNetSaved = maxOf(0L, tenureInterestSaved - allocation.feeChargesPaise)

        // Option 2: Reduce EMI (Keep tenure constant)
        val newEmi = if (newPrincipal > 0L && remainingTenureMonths > 0) {
            calculateEmi(newPrincipal, annualRateBp, remainingTenureMonths)
        } else 0L

        var emiOptionInterestTotal = 0L
        var emiCursor = newPrincipal
        for (m in 1..remainingTenureMonths) {
            if (emiCursor <= 0L) break
            val interest = if (annualRateBp > 0) {
                (emiCursor * annualRateBp.toLong() + (annualRateDivisor / 2L)) / annualRateDivisor
            } else 0L
            emiOptionInterestTotal += interest
            val isLast = (m == remainingTenureMonths) || (emiCursor <= (newEmi - interest))
            val principalPaid = if (isLast) emiCursor else minOf(emiCursor, maxOf(0L, newEmi - interest))
            emiCursor -= principalPaid
        }
        val monthlySavings = maxOf(0L, currentEmiPaise - newEmi)
        val emiInterestSaved = maxOf(0L, baselineInterestTotal - emiOptionInterestTotal)
        val emiNetSaved = maxOf(0L, emiInterestSaved - allocation.feeChargesPaise)

        return PrepaymentSimulationResult(
            prepaymentPaise = prepaymentPaise,
            originalOutstandingPaise = currentOutstandingPaise,
            originalEmiPaise = currentEmiPaise,
            originalRemainingTenureMonths = remainingTenureMonths,
            feeChargesPaise = allocation.feeChargesPaise,
            accruedInterestPaise = allocation.accruedInterestPaise,
            netPrincipalReductionPaise = allocation.principalReductionPaise,
            tenureOptionNewTenureMonths = newTenureMonths,
            tenureOptionMonthsSaved = monthsSaved,
            tenureOptionInterestSavedPaise = tenureInterestSaved,
            tenureOptionNetSavingsPaise = tenureNetSaved,
            emiOptionNewEmiPaise = newEmi,
            emiOptionMonthlySavingsPaise = monthlySavings,
            emiOptionInterestSavedPaise = emiInterestSaved,
            emiOptionNetSavingsPaise = emiNetSaved
        )
    }

    /**
     * Calculates full early foreclosure settlement amount.
     */
    fun calculateForeclosureSettlement(
        outstandingPrincipalPaise: Long,
        annualRateBp: Int,
        daysElapsedSinceLastPayment: Int,
        fixedFeePaise: Long = 0L,
        percentageFeeBp: Int = 0,
        method: InterestCalculationMethod = InterestCalculationMethod.MONTHLY_REDUCING
    ): ForeclosureSettlement {
        if (outstandingPrincipalPaise <= 0L) {
            return ForeclosureSettlement(0L, 0L, 0L, 0L)
        }

        val accruedInterest = calculateAccruedInterest(
            principalPaise = outstandingPrincipalPaise,
            annualRateBp = annualRateBp,
            daysElapsed = daysElapsedSinceLastPayment,
            method = method
        )

        val feeFromPercent = if (percentageFeeBp > 0) {
            (outstandingPrincipalPaise * percentageFeeBp.toLong() + 5000L) / bpScale
        } else 0L
        val totalFee = fixedFeePaise + feeFromPercent
        val totalSettlement = outstandingPrincipalPaise + accruedInterest + totalFee

        return ForeclosureSettlement(
            outstandingPrincipalPaise = outstandingPrincipalPaise,
            accruedInterestPaise = accruedInterest,
            foreclosureFeePaise = totalFee,
            totalSettlementAmountPaise = totalSettlement
        )
    }
}
