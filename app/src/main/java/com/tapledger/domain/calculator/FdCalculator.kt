package com.tapledger.domain.calculator

import com.tapledger.core.database.entity.FixedDepositEntity
import com.tapledger.core.model.CompoundingFrequency
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Pure financial calculator for Fixed Deposits (Flow.md §18, Phase 3).
 *
 * Strictly zero floating-point arithmetic (zero Double, BigDecimal, or Float).
 * All money is signed 64-bit Long paise, and percentages are basis points (Int, 10,000 = 100%).
 * Compounding follows Indian banking standard: quarterly/monthly/annual compounding
 * credited per period, with simple interest for broken remaining days.
 */
@Singleton
class FdCalculator @Inject constructor() {

    private val millisPerDay: Long = 86_400_000L
    private val daysPerYear: Long = 365L
    private val bpScale: Long = 10_000L

    /**
     * Calculates days to maturity as of [asOfMs].
     * Returns 0 if already matured.
     */
    fun calculateDaysToMaturity(maturityMs: Long, asOfMs: Long): Long {
        if (maturityMs <= asOfMs) return 0L
        return (maturityMs - asOfMs) / millisPerDay
    }

    /**
     * Calculates total maturity value in paise at the end of the term.
     */
    fun calculateMaturityValue(
        principalPaise: Long,
        rateBp: Int,
        startMs: Long,
        maturityMs: Long,
        compounding: CompoundingFrequency
    ): Long {
        if (principalPaise <= 0L) return 0L
        if (maturityMs <= startMs) return principalPaise

        val totalDays = (maturityMs - startMs) / millisPerDay
        if (totalDays <= 0L) return principalPaise

        return calculateMaturityForDays(principalPaise, rateBp, totalDays, compounding)
    }

    /**
     * Calculates accrued interest in paise as of [asOfMs].
     * Accrued interest = (value as of asOfMs) - principal.
     */
    fun calculateAccruedInterest(
        principalPaise: Long,
        rateBp: Int,
        startMs: Long,
        maturityMs: Long,
        asOfMs: Long,
        compounding: CompoundingFrequency
    ): Long {
        if (principalPaise <= 0L || asOfMs <= startMs) return 0L
        val effectiveEndMs = minOf(asOfMs, maturityMs)
        val elapsedDays = (effectiveEndMs - startMs) / millisPerDay
        if (elapsedDays <= 0L) return 0L

        val valueAsOf = calculateMaturityForDays(principalPaise, rateBp, elapsedDays, compounding)
        return maxOf(0L, valueAsOf - principalPaise)
    }

    /**
     * Calculates maturity value for [fd] entity as of maturity date.
     */
    fun calculateMaturityValue(fd: FixedDepositEntity): Long {
        return calculateMaturityValue(
            principalPaise = fd.principalPaise,
            rateBp = fd.rateBp,
            startMs = fd.startMs,
            maturityMs = fd.maturityMs,
            compounding = fd.compounding
        )
    }

    /**
     * Calculates accrued interest for [fd] entity as of [asOfMs].
     */
    fun calculateAccruedInterest(fd: FixedDepositEntity, asOfMs: Long): Long {
        return calculateAccruedInterest(
            principalPaise = fd.principalPaise,
            rateBp = fd.rateBp,
            startMs = fd.startMs,
            maturityMs = fd.maturityMs,
            asOfMs = asOfMs,
            compounding = fd.compounding
        )
    }

    /**
     * Pure calculation given principal, rateBp, totalDays, and compounding frequency.
     */
    fun calculateMaturityForDays(
        principalPaise: Long,
        rateBp: Int,
        totalDays: Long,
        compounding: CompoundingFrequency
    ): Long {
        if (principalPaise <= 0L || totalDays <= 0L || rateBp <= 0) return principalPaise

        return when (compounding) {
            CompoundingFrequency.SIMPLE -> calculateSimpleInterestMaturity(principalPaise, rateBp, totalDays)
            CompoundingFrequency.QUARTERLY -> calculateCompoundedMaturity(principalPaise, rateBp, totalDays, periodsPerYear = 4)
            CompoundingFrequency.MONTHLY -> calculateCompoundedMaturity(principalPaise, rateBp, totalDays, periodsPerYear = 12)
            CompoundingFrequency.ANNUAL -> calculateCompoundedMaturity(principalPaise, rateBp, totalDays, periodsPerYear = 1)
        }
    }

    private fun calculateSimpleInterestMaturity(
        principalPaise: Long,
        rateBp: Int,
        totalDays: Long
    ): Long {
        val denominator = bpScale * daysPerYear // 3,650,000
        val numerator = Math.multiplyExact(Math.multiplyExact(principalPaise, rateBp.toLong()), totalDays)
        val interest = (numerator + (denominator / 2L)) / denominator
        return Math.addExact(principalPaise, interest)
    }

    private fun calculateCompoundedMaturity(
        principalPaise: Long,
        rateBp: Int,
        totalDays: Long,
        periodsPerYear: Int
    ): Long {
        val periodsPerYearLong = periodsPerYear.toLong()
        val periodDenominator = bpScale * periodsPerYearLong // e.g. 40,000 for quarterly
        val daysPerPeriodNumerator = daysPerYear // 365
        val daysPerPeriodDenominator = periodsPerYearLong // 4 -> 91.25 days per period

        // Number of completed periods: (totalDays * periodsPerYear) / 365
        val completedPeriods = ((totalDays * periodsPerYearLong) / daysPerPeriodNumerator).toInt()

        var currentBalance = principalPaise
        for (i in 0 until completedPeriods) {
            val periodInterestNum = currentBalance * rateBp.toLong()
            val periodInterest = (periodInterestNum + (periodDenominator / 2L)) / periodDenominator
            currentBalance += periodInterest
        }

        // Remaining broken days
        val completedDays = (completedPeriods.toLong() * daysPerPeriodNumerator) / daysPerPeriodDenominator
        val remainingDays = maxOf(0L, totalDays - completedDays)

        if (remainingDays > 0L) {
            val brokenDenominator = bpScale * daysPerYear // 3,650,000
            val brokenNumerator = currentBalance * rateBp.toLong() * remainingDays
            val brokenInterest = (brokenNumerator + (brokenDenominator / 2L)) / brokenDenominator
            currentBalance += brokenInterest
        }

        return currentBalance
    }
}
