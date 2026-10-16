package com.tapledger.domain.calculator

import com.tapledger.domain.model.MilestoneType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SavingsMilestoneEvaluatorTest {

    private val evaluator = SavingsMilestoneEvaluator()

    @Test
    fun testEmptyStateBadgesLocked() {
        val badges = evaluator.evaluateMilestones(
            totalSavedPaise = 0L,
            emergencyFundTenths = 0L,
            totalGoalsCount = 0,
            completedGoalsCount = 0,
            totalContributionsCount = 0
        )

        assertEquals(8, badges.size)
        assertTrue(badges.all { !it.isUnlocked })
        assertTrue(badges.all { it.progressBp == 0 })
    }

    @Test
    fun testFirstGoalAndDepositUnlocked() {
        val badges = evaluator.evaluateMilestones(
            totalSavedPaise = 500_000L, // ₹5,000
            emergencyFundTenths = 15L,   // 1.5 months
            totalGoalsCount = 1,
            completedGoalsCount = 0,
            totalContributionsCount = 2
        )

        val firstGoalBadge = badges.first { it.type == MilestoneType.FIRST_GOAL }
        assertTrue(firstGoalBadge.isUnlocked)
        assertEquals(10_000, firstGoalBadge.progressBp)

        val firstDepositBadge = badges.first { it.type == MilestoneType.FIRST_DEPOSIT }
        assertTrue(firstDepositBadge.isUnlocked)
        assertEquals(10_000, firstDepositBadge.progressBp)

        val efStarter = badges.first { it.type == MilestoneType.EMERGENCY_FUND_STARTER }
        assertFalse(efStarter.isUnlocked)
        assertEquals(5_000, efStarter.progressBp) // 15/30 = 50% = 5000 bp

        val saved10k = badges.first { it.type == MilestoneType.SAVED_10K }
        assertFalse(saved10k.isUnlocked)
        assertEquals(5_000, saved10k.progressBp) // 5,000 / 10,000 = 5000 bp
    }

    @Test
    fun testEmergencyFundAndHighMilestonesUnlocked() {
        val badges = evaluator.evaluateMilestones(
            totalSavedPaise = 15_000_000L, // ₹1,50,000
            emergencyFundTenths = 65L,     // 6.5 months
            totalGoalsCount = 3,
            completedGoalsCount = 2,
            totalContributionsCount = 10
        )

        val efHero = badges.first { it.type == MilestoneType.EMERGENCY_FUND_HERO }
        assertTrue(efHero.isUnlocked)
        assertEquals(10_000, efHero.progressBp)

        val saved100k = badges.first { it.type == MilestoneType.SAVED_100K }
        assertTrue(saved100k.isUnlocked)
        assertEquals(10_000, saved100k.progressBp)

        val goalAchiever = badges.first { it.type == MilestoneType.GOAL_ACHIEVER }
        assertTrue(goalAchiever.isUnlocked)
        assertEquals(10_000, goalAchiever.progressBp)
    }
}
