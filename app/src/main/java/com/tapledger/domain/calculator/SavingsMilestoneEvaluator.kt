package com.tapledger.domain.calculator

import com.tapledger.domain.model.MilestoneType
import com.tapledger.domain.model.SavingsMilestoneBadge
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Pure evaluator for savings milestones and badges (Flow.md §18, Phase 4).
 * Strictly zero floating-point arithmetic.
 */
@Singleton
class SavingsMilestoneEvaluator @Inject constructor() {

    private val maxBp: Int = 10_000

    private fun progressBp(current: Long, target: Long): Int {
        if (target <= 0L) return if (current >= 0L) maxBp else 0
        if (current >= target) return maxBp
        if (current <= 0L) return 0
        val scaled = current * maxBp.toLong()
        return (scaled / target).coerceIn(0L, maxBp.toLong()).toInt()
    }

    fun evaluateMilestones(
        totalSavedPaise: Long,
        emergencyFundTenths: Long,
        totalGoalsCount: Int,
        completedGoalsCount: Int,
        totalContributionsCount: Int
    ): List<SavingsMilestoneBadge> {
        val badges = mutableListOf<SavingsMilestoneBadge>()

        // 1. First Goal
        badges.add(
            SavingsMilestoneBadge(
                type = MilestoneType.FIRST_GOAL,
                title = "First Step",
                description = "Created your first savings goal",
                isUnlocked = totalGoalsCount > 0,
                progressBp = if (totalGoalsCount > 0) maxBp else 0
            )
        )

        // 2. First Deposit
        badges.add(
            SavingsMilestoneBadge(
                type = MilestoneType.FIRST_DEPOSIT,
                title = "Saver Debut",
                description = "Made your first contribution towards a goal",
                isUnlocked = totalContributionsCount > 0,
                progressBp = if (totalContributionsCount > 0) maxBp else 0
            )
        )

        // 3. Emergency Fund Starter (3.0 months -> 30 tenths)
        badges.add(
            SavingsMilestoneBadge(
                type = MilestoneType.EMERGENCY_FUND_STARTER,
                title = "Safety Net",
                description = "Built a 3-month emergency fund",
                isUnlocked = emergencyFundTenths >= 30L,
                progressBp = progressBp(emergencyFundTenths, 30L)
            )
        )

        // 4. Emergency Fund Hero (6.0 months -> 60 tenths)
        badges.add(
            SavingsMilestoneBadge(
                type = MilestoneType.EMERGENCY_FUND_HERO,
                title = "Financial Fortress",
                description = "Built a 6-month emergency fund",
                isUnlocked = emergencyFundTenths >= 60L,
                progressBp = progressBp(emergencyFundTenths, 60L)
            )
        )

        // 5. Saved ₹10,000
        val tenKPaise = 10_000L * 100L
        badges.add(
            SavingsMilestoneBadge(
                type = MilestoneType.SAVED_10K,
                title = "Seed Saver",
                description = "Accumulated ₹10,000 across savings goals",
                isUnlocked = totalSavedPaise >= tenKPaise,
                progressBp = progressBp(totalSavedPaise, tenKPaise)
            )
        )

        // 6. Saved ₹50,000
        val fiftyKPaise = 50_000L * 100L
        badges.add(
            SavingsMilestoneBadge(
                type = MilestoneType.SAVED_50K,
                title = "Bronze Saver",
                description = "Accumulated ₹50,000 across savings goals",
                isUnlocked = totalSavedPaise >= fiftyKPaise,
                progressBp = progressBp(totalSavedPaise, fiftyKPaise)
            )
        )

        // 7. Saved ₹1,00,000
        val oneLakhPaise = 100_000L * 100L
        badges.add(
            SavingsMilestoneBadge(
                type = MilestoneType.SAVED_100K,
                title = "Silver Saver",
                description = "Accumulated ₹1,00,000 across savings goals",
                isUnlocked = totalSavedPaise >= oneLakhPaise,
                progressBp = progressBp(totalSavedPaise, oneLakhPaise)
            )
        )

        // 8. Goal Achiever
        badges.add(
            SavingsMilestoneBadge(
                type = MilestoneType.GOAL_ACHIEVER,
                title = "Goal Crusher",
                description = "Fully completed at least one savings goal",
                isUnlocked = completedGoalsCount > 0,
                progressBp = if (completedGoalsCount > 0) maxBp else 0
            )
        )

        return badges
    }
}
