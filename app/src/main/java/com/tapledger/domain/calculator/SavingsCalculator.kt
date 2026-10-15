package com.tapledger.domain.calculator

import com.tapledger.core.database.entity.AccountEntity
import com.tapledger.core.database.entity.SavingsContributionEntity
import com.tapledger.core.database.entity.SavingsGoalEntity
import com.tapledger.core.model.GoalTrackStatus
import com.tapledger.core.model.MoneyFormatter
import com.tapledger.core.model.SavingsGoalStatus
import com.tapledger.core.util.DateTimeUtils
import com.tapledger.domain.model.AccountEarmarkWarning
import com.tapledger.domain.model.SavingsGoalSummary

/**
 * Pure Kotlin financial calculator for the Savings Module (Flow.md §18, D011).
 * Strictly zero floating-point arithmetic (zero Double, BigDecimal, or Float outside render).
 * All money is signed 64-bit Long paise, and percentages are basis points (Int, 10,000 = 100%).
 */
object SavingsCalculator {

    const val MAX_BP: Int = 10000

    /**
     * Calculates current balance of a savings goal as the sum of its contributions.
     */
    fun calculateGoalBalance(contributions: List<SavingsContributionEntity>): Long {
        var sum = 0L
        for (c in contributions) {
            sum = Math.addExact(sum, c.amountPaise)
        }
        return sum
    }

    /**
     * Calculates goal completion progress in basis points (10,000 = 100.00%).
     */
    fun calculateProgressBp(currentBalancePaise: Long, targetPaise: Long): Int {
        if (targetPaise <= 0L) return if (currentBalancePaise >= 0L) MAX_BP else 0
        if (currentBalancePaise >= targetPaise) return MAX_BP
        if (currentBalancePaise <= 0L) return 0

        val scaled = Math.multiplyExact(currentBalancePaise, MAX_BP.toLong())
        val bp = scaled / targetPaise
        return bp.coerceIn(0L, MAX_BP.toLong()).toInt()
    }

    /**
     * Calculates remaining amount in paise to reach target.
     */
    fun calculateRemainingPaise(currentBalancePaise: Long, targetPaise: Long): Long {
        return maxOf(0L, targetPaise - currentBalancePaise)
    }

    /**
     * Calculates the number of months left from asOf epoch millis until the deadline.
     * Inclusive of the current active month.
     * Returns 0 if deadline is in the past.
     */
    fun calculateMonthsLeft(deadlineEpochMs: Long?, asOfEpochMs: Long): Int {
        if (deadlineEpochMs == null) return 0
        if (deadlineEpochMs < asOfEpochMs) return 0

        val currentYm = DateTimeUtils.toYearMonth(asOfEpochMs)
        val deadlineYm = DateTimeUtils.toYearMonth(deadlineEpochMs)

        val months = (deadlineYm.year - currentYm.year) * 12 + (deadlineYm.monthValue - currentYm.monthValue) + 1
        return maxOf(0, months)
    }

    /**
     * Calculates the required monthly savings amount in paise using integer ceiling division.
     * (remainingPaise + monthsLeft - 1) / monthsLeft
     */
    fun calculateRequiredMonthlyPaise(remainingPaise: Long, monthsLeft: Int): Long {
        if (remainingPaise <= 0L) return 0L
        if (monthsLeft <= 0) return remainingPaise
        return (remainingPaise + monthsLeft.toLong() - 1L) / monthsLeft.toLong()
    }

    /**
     * Determines whether a goal is ON_TRACK, BEHIND, DONE, or OVERDUE.
     */
    fun calculateTrackStatus(
        goal: SavingsGoalEntity,
        currentBalancePaise: Long,
        asOfEpochMs: Long
    ): GoalTrackStatus {
        if (currentBalancePaise >= goal.targetPaise) {
            return GoalTrackStatus.DONE
        }

        val deadline = goal.deadlineEpochMs
        if (deadline != null && deadline < asOfEpochMs) {
            return GoalTrackStatus.OVERDUE
        }

        if (deadline == null) {
            return GoalTrackStatus.ON_TRACK
        }

        val startYm = DateTimeUtils.toYearMonth(goal.createdAt)
        val currentYm = DateTimeUtils.toYearMonth(asOfEpochMs)
        val deadlineYm = DateTimeUtils.toYearMonth(deadline)

        val totalMonths = maxOf(1, (deadlineYm.year - startYm.year) * 12 + (deadlineYm.monthValue - startYm.monthValue) + 1)
        val elapsedMonths = maxOf(0, (currentYm.year - startYm.year) * 12 + (currentYm.monthValue - startYm.monthValue))

        val expectedPaise = if (totalMonths > 0) {
            (goal.targetPaise * elapsedMonths.toLong()) / totalMonths.toLong()
        } else {
            0L
        }

        return if (currentBalancePaise >= expectedPaise) {
            GoalTrackStatus.ON_TRACK
        } else {
            GoalTrackStatus.BEHIND
        }
    }

    /**
     * Generates a plain-language summary line for a goal (e.g., "₹4,200 to go, on track for March").
     */
    fun buildPaceSummary(
        goal: SavingsGoalEntity,
        currentBalancePaise: Long,
        asOfEpochMs: Long
    ): String {
        if (currentBalancePaise >= goal.targetPaise) {
            return "Goal achieved!"
        }

        val remaining = calculateRemainingPaise(currentBalancePaise, goal.targetPaise)
        val formattedRemaining = MoneyFormatter.formatPaiseToRupees(remaining)

        val deadline = goal.deadlineEpochMs ?: return "$formattedRemaining to go"
        if (deadline < asOfEpochMs) {
            return "$formattedRemaining to go, overdue"
        }

        val deadlineZdt = DateTimeUtils.toZonedDateTime(deadline)
        val monthName = deadlineZdt.month.name.lowercase().replaceFirstChar { it.uppercase() }
        val trackStatus = calculateTrackStatus(goal, currentBalancePaise, asOfEpochMs)

        return if (trackStatus == GoalTrackStatus.ON_TRACK) {
            "$formattedRemaining to go, on track for $monthName"
        } else {
            "$formattedRemaining to go, behind schedule for $monthName"
        }
    }

    /**
     * Builds a comprehensive SavingsGoalSummary model for a single goal.
     */
    fun calculateGoalSummary(
        goal: SavingsGoalEntity,
        balancePaise: Long,
        asOfEpochMs: Long = DateTimeUtils.nowEpochMillis(),
        isOverEarmarked: Boolean = false
    ): SavingsGoalSummary {
        val progressBp = calculateProgressBp(balancePaise, goal.targetPaise)
        val remainingPaise = calculateRemainingPaise(balancePaise, goal.targetPaise)
        val monthsLeft = calculateMonthsLeft(goal.deadlineEpochMs, asOfEpochMs)
        val requiredMonthlyPaise = calculateRequiredMonthlyPaise(remainingPaise, monthsLeft)
        val trackStatus = calculateTrackStatus(goal, balancePaise, asOfEpochMs)
        val paceSummary = buildPaceSummary(goal, balancePaise, asOfEpochMs)

        return SavingsGoalSummary(
            goal = goal,
            balancePaise = balancePaise,
            progressBp = progressBp,
            remainingPaise = remainingPaise,
            monthsLeft = monthsLeft,
            requiredMonthlyPaise = requiredMonthlyPaise,
            trackStatus = trackStatus,
            paceSummary = paceSummary,
            isOverEarmarked = isOverEarmarked
        )
    }

    /**
     * Evaluates account earmarking invariant (D011):
     * Total earmarked to goals linked to an account may not exceed that account's live balance.
     * Returns soft warnings for each account that is over-earmarked.
     */
    fun evaluateAccountEarmarks(
        activeGoals: List<SavingsGoalEntity>,
        goalBalances: Map<Long, Long>,
        accountsMap: Map<Long, AccountEntity>,
        accountBalances: Map<Long, Long>
    ): List<AccountEarmarkWarning> {
        val warnings = mutableListOf<AccountEarmarkWarning>()

        val goalsByAccount = activeGoals
            .filter { it.status == SavingsGoalStatus.ACTIVE && it.linkedAccountId != null }
            .groupBy { it.linkedAccountId!! }

        for ((accountId, goals) in goalsByAccount) {
            val account = accountsMap[accountId] ?: continue
            val accountBalance = accountBalances[accountId] ?: 0L
            val totalEarmarked = goals.sumOf { goalBalances[it.id] ?: 0L }

            if (totalEarmarked > accountBalance) {
                warnings.add(
                    AccountEarmarkWarning(
                        accountId = accountId,
                        accountName = account.name,
                        accountBalancePaise = accountBalance,
                        totalEarmarkedPaise = totalEarmarked,
                        deficitPaise = totalEarmarked - accountBalance
                    )
                )
            }
        }

        return warnings
    }
}
