package com.tapledger.domain.calculator

import com.tapledger.core.database.entity.SavingsGoalEntity
import com.tapledger.core.model.SavingsGoalStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SavingsPlannerTest {

    private val planner = SavingsPlanner()

    @Test
    fun testEmptyGoalsOrZeroSavingsReturnsEmptyList() {
        val emptyPlan = planner.planForPeriod(
            goals = emptyList(),
            goalBalances = emptyMap(),
            periodId = 1L,
            totalSavingsPaise = 100_000L
        )
        assertTrue(emptyPlan.isEmpty())

        val goal = SavingsGoalEntity(
            id = 1L,
            name = "Emergency",
            targetPaise = 500_000L,
            priority = 1,
            createdAt = 1000L,
            updatedAt = 1000L
        )
        val zeroPlan = planner.planForPeriod(
            goals = listOf(goal),
            goalBalances = emptyMap(),
            periodId = 1L,
            totalSavingsPaise = 0L
        )
        assertTrue(zeroPlan.isEmpty())
    }

    @Test
    fun testOnlyActiveUnderTargetGoalsAreAllocated() {
        val activeGoal = SavingsGoalEntity(
            id = 1L,
            name = "Active Under Target",
            targetPaise = 100_000L,
            status = SavingsGoalStatus.ACTIVE,
            priority = 1,
            createdAt = 1000L,
            updatedAt = 1000L
        )
        val completedGoal = SavingsGoalEntity(
            id = 2L,
            name = "Already Completed",
            targetPaise = 50_000L,
            status = SavingsGoalStatus.ACTIVE,
            priority = 1,
            createdAt = 1000L,
            updatedAt = 1000L
        )
        val pausedGoal = SavingsGoalEntity(
            id = 3L,
            name = "Paused Goal",
            targetPaise = 200_000L,
            status = SavingsGoalStatus.PAUSED,
            priority = 1,
            createdAt = 1000L,
            updatedAt = 1000L
        )

        val balances = mapOf(
            1L to 20_000L,
            2L to 50_000L, // balance >= target
            3L to 10_000L
        )

        val plan = planner.planForPeriod(
            goals = listOf(activeGoal, completedGoal, pausedGoal),
            goalBalances = balances,
            periodId = 101L,
            totalSavingsPaise = 10_000L
        )

        assertEquals(1, plan.size)
        assertEquals(1L, plan[0].goalId)
        assertEquals(10_000L, plan[0].plannedPaise)
        assertEquals(101L, plan[0].periodId)
    }

    @Test
    fun testPriorityWeightedProportionalAllocationAndSumInvariant() {
        // P1: weight 4, P2: weight 3, P3: weight 2
        // Total weight = 9
        // Total savings = 10,000 paise (₹100)
        // Goal 1: ceil(10,000 * 4 / 9) = ceil(40,000 / 9) = 4445 paise
        // Goal 2: ceil(10,000 * 3 / 9) = ceil(30,000 / 9) = 3334 paise
        // Goal 3 (last): remaining = 10,000 - (4445 + 3334) = 2221 paise
        // Total sum = 4445 + 3334 + 2221 = 10,000 paise
        val g1 = SavingsGoalEntity(id = 1L, name = "P1 Goal", targetPaise = 1_000_000L, priority = 1, createdAt = 1000L, updatedAt = 1000L)
        val g2 = SavingsGoalEntity(id = 2L, name = "P2 Goal", targetPaise = 1_000_000L, priority = 2, createdAt = 1000L, updatedAt = 1000L)
        val g3 = SavingsGoalEntity(id = 3L, name = "P3 Goal", targetPaise = 1_000_000L, priority = 3, createdAt = 1000L, updatedAt = 1000L)

        val totalSavings = 10_000L
        val plan = planner.planForPeriod(
            goals = listOf(g1, g2, g3),
            goalBalances = emptyMap(),
            periodId = 55L,
            totalSavingsPaise = totalSavings
        )

        assertEquals(3, plan.size)
        val sumPlanned = plan.sumOf { it.plannedPaise }
        assertEquals("Total planned paise must equal totalSavings exactly", totalSavings, sumPlanned)

        val p1Entry = plan.first { it.goalId == 1L }
        val p2Entry = plan.first { it.goalId == 2L }
        val p3Entry = plan.first { it.goalId == 3L }

        assertTrue(p1Entry.plannedPaise > p2Entry.plannedPaise)
        assertTrue(p2Entry.plannedPaise > p3Entry.plannedPaise)
        assertEquals(4445L, p1Entry.plannedPaise)
        assertEquals(3334L, p2Entry.plannedPaise)
        assertEquals(2221L, p3Entry.plannedPaise)
    }
}
