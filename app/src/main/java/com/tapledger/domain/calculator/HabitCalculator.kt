package com.tapledger.domain.calculator

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Pure Kotlin calculator for savings habits, round-ups, and income rules (Flow.md §18, Phase 4).
 * Strictly zero floating-point arithmetic. All money is Long paise, and percentages are basis points.
 */
@Singleton
class HabitCalculator @Inject constructor() {

    private val bpScale: Long = 10_000L

    /**
     * Calculates virtual round-up amount in paise for an expense transaction.
     *
     * Example: For an expense of ₹142 (14,200 paise) with a step of ₹10 (1,000 paise),
     * round-up is 800 paise (₹8), bringing total to ₹150 (15,000 paise).
     *
     * @param amountPaise transaction amount in paise.
     * @param stepPaise round-up step interval in paise (e.g., 1000L for ₹10, 5000L for ₹50, 10000L for ₹100).
     * @return additional paise to reach the next multiple of [stepPaise], or 0L if already a multiple.
     */
    fun calculateRoundUpPaise(amountPaise: Long, stepPaise: Long = 1_000L): Long {
        if (amountPaise <= 0L || stepPaise <= 0L) return 0L
        val remainder = amountPaise % stepPaise
        return if (remainder == 0L) 0L else stepPaise - remainder
    }

    /**
     * Calculates savings contribution from an income transaction using a rule.
     * Rule: Save [rateBp] percent of income above [thresholdPaise].
     *
     * Example: Income ₹60,000 (6,000,000 paise), threshold ₹50,000 (5,000,000 paise), rate 20% (2,000 bp).
     * Surplus = ₹10,000 -> 20% = ₹2,000 (200,000 paise).
     *
     * @param incomePaise total income amount in paise.
     * @param thresholdPaise baseline threshold in paise below which no rule applies.
     * @param rateBp allocation percentage in basis points (10,000 = 100%, 2,000 = 20%).
     * @return paise to allocate to savings.
     */
    fun calculateIncomeRulePaise(
        incomePaise: Long,
        thresholdPaise: Long,
        rateBp: Int
    ): Long {
        if (incomePaise <= thresholdPaise || rateBp <= 0) return 0L
        val surplusPaise = incomePaise - thresholdPaise
        val numerator = surplusPaise * rateBp.toLong()
        return (numerator + (bpScale / 2L)) / bpScale
    }
}
