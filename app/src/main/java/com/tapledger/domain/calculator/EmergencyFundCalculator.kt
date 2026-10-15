package com.tapledger.domain.calculator

import com.tapledger.core.database.entity.MonthCloseEntity
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Calculates the Emergency Fund coverage metric (Flow.md §18, Phase 2).
 *
 * Rule: months covered = savings balance ÷ average monthly spend over the last 3 to 6 closed periods.
 * Stored strictly as tenths of a month in a 64-bit signed Long (e.g., 3.2 months → 32L).
 * Pure integer arithmetic, zero Double / Float.
 */
@Singleton
class EmergencyFundCalculator @Inject constructor() {

    /**
     * Calculates emergency fund coverage in tenths of a month (e.g., 32L = 3.2 months).
     *
     * @param totalSavingsPaise current liquid savings balance in paise.
     * @param monthlySpendList list of total spend in paise for recent closed periods.
     * @return tenths of a month covered (Long), rounded half-up.
     */
    fun calculateMonthsCoveredTenths(
        totalSavingsPaise: Long,
        monthlySpendList: List<Long>
    ): Long {
        if (totalSavingsPaise <= 0L || monthlySpendList.isEmpty()) return 0L

        val validSpends = monthlySpendList.filter { it > 0L }
        if (validSpends.isEmpty()) return 0L

        val n = validSpends.size.toLong()
        var totalSpendPaise = 0L
        for (spend in validSpends) {
            totalSpendPaise = Math.addExact(totalSpendPaise, spend)
        }
        if (totalSpendPaise <= 0L) return 0L

        // tenths = round((totalSavings * n * 10) / totalSpend)
        // With integer half-up rounding: ((totalSavings * n * 10) + (totalSpend / 2)) / totalSpend
        val scaledNumerator = Math.multiplyExact(Math.multiplyExact(totalSavingsPaise, n), 10L)
        val halfSpend = totalSpendPaise / 2L
        return Math.addExact(scaledNumerator, halfSpend) / totalSpendPaise
    }

    /**
     * Calculates coverage directly from the latest [monthCloses] records.
     * Takes up to [lookbackMonths] (typically 3 to 6) most recent closed periods.
     */
    fun calculateFromMonthCloses(
        totalSavingsPaise: Long,
        monthCloses: List<MonthCloseEntity>,
        lookbackMonths: Int = 3
    ): Long {
        if (monthCloses.isEmpty() || totalSavingsPaise <= 0L) return 0L
        val recentSpends = monthCloses
            .sortedByDescending { it.closedAt }
            .take(lookbackMonths)
            .map { it.totalSpentPaise }

        return calculateMonthsCoveredTenths(totalSavingsPaise, recentSpends)
    }

    /**
     * Formats tenths of a month to a display string (e.g., 32L -> "3.2 months", 10L -> "1.0 month").
     */
    fun formatDisplay(tenths: Long): String {
        val whole = tenths / 10L
        val decimal = tenths % 10L
        val unit = if (tenths == 10L) "month" else "months"
        return "$whole.$decimal $unit"
    }
}
