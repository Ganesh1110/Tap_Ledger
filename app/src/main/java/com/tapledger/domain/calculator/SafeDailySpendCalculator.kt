package com.tapledger.domain.calculator

data class SafeDailySpendResult(
    val safeDailyPaise: Long,
    val remainingBudgetPaise: Long,
    val totalHeroBudgetPaise: Long,
    val totalHeroSpentPaise: Long,
    val daysRemaining: Int,
    val totalDaysInPeriod: Int,
    val elapsedDays: Int,
    val isPeriodActive: Boolean,
    val isPeriodClosed: Boolean,
    val isOverBudget: Boolean,
    val todaySpentPaise: Long = 0L,
    val todayRemainingPaise: Long = 0L
)

/**
 * Pure zero-float calculator for daily discretionary spending guidance.
 * Discretionary budget covers Needs + Wants (excluding Savings, per Flow.md §8.4/§8.5).
 */
object SafeDailySpendCalculator {
    const val MILLIS_PER_IST_DAY = 86_400_000L

    fun calculate(
        heroBudgetPaise: Long,
        heroSpentPaise: Long,
        periodStartAt: Long,
        periodEndAt: Long,
        nowIST: Long,
        isClosed: Boolean,
        todaySpentPaise: Long = 0L
    ): SafeDailySpendResult {
        val totalDays = ((periodEndAt - periodStartAt) / MILLIS_PER_IST_DAY).toInt().coerceAtLeast(1)
        val isPeriodActive = !isClosed && nowIST in periodStartAt until periodEndAt

        val elapsedDays = if (nowIST < periodStartAt) {
            0
        } else if (nowIST >= periodEndAt) {
            totalDays
        } else {
            ((nowIST - periodStartAt) / MILLIS_PER_IST_DAY).toInt() + 1
        }.coerceIn(0, totalDays)

        val daysRemaining = if (nowIST < periodStartAt) {
            totalDays
        } else if (nowIST >= periodEndAt || isClosed) {
            0
        } else {
            (totalDays - elapsedDays + 1).coerceAtLeast(1)
        }

        val remainingBudgetPaise = heroBudgetPaise - heroSpentPaise
        val isOverBudget = remainingBudgetPaise <= 0L

        val safeDailyPaise = if (isOverBudget || daysRemaining <= 0) {
            0L
        } else {
            remainingBudgetPaise / daysRemaining
        }

        val clampedTodaySpent = todaySpentPaise.coerceAtLeast(0L)
        val todayRemainingPaise = if (safeDailyPaise > clampedTodaySpent) {
            safeDailyPaise - clampedTodaySpent
        } else {
            0L
        }

        return SafeDailySpendResult(
            safeDailyPaise = safeDailyPaise,
            remainingBudgetPaise = remainingBudgetPaise,
            totalHeroBudgetPaise = heroBudgetPaise,
            totalHeroSpentPaise = heroSpentPaise,
            daysRemaining = daysRemaining,
            totalDaysInPeriod = totalDays,
            elapsedDays = elapsedDays,
            isPeriodActive = isPeriodActive,
            isPeriodClosed = isClosed,
            isOverBudget = isOverBudget,
            todaySpentPaise = clampedTodaySpent,
            todayRemainingPaise = todayRemainingPaise
        )
    }
}
