package com.tapledger.domain.calculator

import com.tapledger.core.database.entity.FixedDepositEntity
import com.tapledger.core.model.CompoundingFrequency
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Pure financial calculator for Fixed Deposits (Flow.md §18, Phase 3).
 *
 * Strictly follows guiding rules:
 * - All money in Long paise, rates in basis points (10,000 = 100.00%).
 * - Uses BigDecimal with a single HALF_UP rounding at the end to Long paise.
 * - Zero Double / Float.
 * - Compounding follows Indian banking standard: quarterly/monthly/annual compounding
 *   for full periods, plus simple interest for remaining days.
 */
@Singleton
class FdCalculator @Inject constructor() {

    private val mathContext = MathContext(34, RoundingMode.HALF_UP)
    private val daysPerYear = BigDecimal("365")
    private val millisPerDay = 86_400_000L

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

        val p = BigDecimal(principalPaise)
        val annualRate = BigDecimal(rateBp).divide(BigDecimal(10000), mathContext)

        val multiplier: BigDecimal = when (compounding) {
            CompoundingFrequency.SIMPLE -> {
                val timeFraction = BigDecimal(totalDays).divide(daysPerYear, mathContext)
                BigDecimal.ONE.add(annualRate.multiply(timeFraction, mathContext))
            }
            CompoundingFrequency.QUARTERLY -> calculateCompoundedMultiplier(annualRate, totalDays, periodsPerYear = 4)
            CompoundingFrequency.MONTHLY -> calculateCompoundedMultiplier(annualRate, totalDays, periodsPerYear = 12)
            CompoundingFrequency.ANNUAL -> calculateCompoundedMultiplier(annualRate, totalDays, periodsPerYear = 1)
        }

        val maturityPaiseBd = p.multiply(multiplier, mathContext)
        return maturityPaiseBd.setScale(0, RoundingMode.HALF_UP).longValueExact()
    }

    private fun calculateCompoundedMultiplier(
        annualRate: BigDecimal,
        totalDays: Long,
        periodsPerYear: Int
    ): BigDecimal {
        val nBd = BigDecimal(periodsPerYear)
        val daysPerPeriodBd = daysPerYear.divide(nBd, mathContext)

        // Number of completed compounding periods
        val completedPeriods = (BigDecimal(totalDays).divide(daysPerPeriodBd, 0, RoundingMode.DOWN)).toInt()
        val ratePerPeriod = annualRate.divide(nBd, mathContext)
        val periodBase = BigDecimal.ONE.add(ratePerPeriod)
        val compoundedPart = periodBase.pow(completedPeriods, mathContext)

        // Remaining days treated with simple interest (Indian banking standard)
        val completedDays = daysPerPeriodBd.multiply(BigDecimal(completedPeriods), mathContext)
        val remainingDays = BigDecimal(totalDays).subtract(completedDays)

        val remainingFraction = if (remainingDays.signum() > 0) {
            val simplePart = annualRate.multiply(remainingDays.divide(daysPerYear, mathContext), mathContext)
            BigDecimal.ONE.add(simplePart)
        } else {
            BigDecimal.ONE
        }

        return compoundedPart.multiply(remainingFraction, mathContext)
    }
}
