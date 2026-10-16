package com.tapledger.domain.calculator

import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Test

class GoalApportionmentCalculatorTest {

    private val calculator = GoalApportionmentCalculator()

    @Test
    fun apportionByPriority_distributesInOrderOfPriority() {
        val goals = listOf(
            GoalApportionmentItem(goalId = 1L, name = "Emergency Fund", priority = 1, targetPaise = 50_000_00L, currentBalancePaise = 20_000_00L), // needed 30,000
            GoalApportionmentItem(goalId = 2L, name = "Vacation", priority = 2, targetPaise = 20_000_00L, currentBalancePaise = 5_000_00L),   // needed 15,000
            GoalApportionmentItem(goalId = 3L, name = "Gadget", priority = 3, targetPaise = 10_000_00L, currentBalancePaise = 0L)            // needed 10,000
        )

        // Moved ₹35,000 (35_000_00L)
        // P1 takes 30,000, leaving 5,000. P2 takes 5,000. P3 gets 0. Unallocated = 0.
        val result = calculator.apportionByPriority(35_000_00L, goals)

        assertThat(result.totalMovedPaise).isEqualTo(35_000_00L)
        assertThat(result.allocations[1L]).isEqualTo(30_000_00L)
        assertThat(result.allocations[2L]).isEqualTo(5_000_00L)
        assertThat(result.allocations[3L]).isEqualTo(0L)
        assertThat(result.unallocatedPaise).isEqualTo(0L)
        assertThat(result.allocations.values.sum() + result.unallocatedPaise).isEqualTo(35_000_00L)
    }

    @Test
    fun apportionByPriority_putsSurplusIntoUnallocated() {
        val goals = listOf(
            GoalApportionmentItem(goalId = 1L, name = "Goal 1", priority = 1, targetPaise = 10_000_00L, currentBalancePaise = 5_000_00L) // needed 5,000
        )

        // Move ₹12,000. Needed is ₹5,000. Surplus ₹7,000 is unallocated.
        val result = calculator.apportionByPriority(12_000_00L, goals)

        assertThat(result.allocations[1L]).isEqualTo(5_000_00L)
        assertThat(result.unallocatedPaise).isEqualTo(7_000_00L)
        assertThat(result.allocations.values.sum() + result.unallocatedPaise).isEqualTo(12_000_00L)
    }

    @Test
    fun apportionByPriority_zeroMoved_allocatesZero() {
        val goals = listOf(
            GoalApportionmentItem(goalId = 1L, name = "Goal 1", priority = 1, targetPaise = 10_000_00L, currentBalancePaise = 0L)
        )

        val result = calculator.apportionByPriority(0L, goals)

        assertThat(result.totalMovedPaise).isEqualTo(0L)
        assertThat(result.allocations[1L]).isEqualTo(0L)
        assertThat(result.unallocatedPaise).isEqualTo(0L)
    }

    @Test
    fun apportionByPriority_emptyGoals_keepsAllUnallocated() {
        val result = calculator.apportionByPriority(50_000_00L, emptyList())

        assertThat(result.totalMovedPaise).isEqualTo(50_000_00L)
        assertThat(result.allocations).isEmpty()
        assertThat(result.unallocatedPaise).isEqualTo(50_000_00L)
    }

    @Test
    fun apportionProportionally_distributesUsingLargestRemainder_sumMatchesExactly() {
        // 3 goals with huge remaining capacity, differing priorities
        // P1 weight = max(1, 5 - 1) = 4
        // P2 weight = max(1, 5 - 2) = 3
        // P3 weight = max(1, 5 - 3) = 2
        // Total weight = 9
        val goals = listOf(
            GoalApportionmentItem(goalId = 1L, name = "P1", priority = 1, targetPaise = 100_000_00L, currentBalancePaise = 0L),
            GoalApportionmentItem(goalId = 2L, name = "P2", priority = 2, targetPaise = 100_000_00L, currentBalancePaise = 0L),
            GoalApportionmentItem(goalId = 3L, name = "P3", priority = 3, targetPaise = 100_000_00L, currentBalancePaise = 0L)
        )

        // ₹100.00 = 10,000 paise.
        // 10,000 * 4 / 9 = 4444.44... -> base 4444, rem 4
        // 10,000 * 3 / 9 = 3333.33... -> base 3333, rem 3
        // 10,000 * 2 / 9 = 2222.22... -> base 2222, rem 2
        // Base sum = 9999. Remaining = 1.
        // Highest remainder is P1 (rem 4), so P1 gets +1 paisa -> 4445.
        // Total = 4445 + 3333 + 2222 = 10000.
        val result = calculator.apportionProportionally(10_000L, goals)

        assertThat(result.totalMovedPaise).isEqualTo(10_000L)
        assertThat(result.allocations[1L]).isEqualTo(4_445L)
        assertThat(result.allocations[2L]).isEqualTo(3_333L)
        assertThat(result.allocations[3L]).isEqualTo(2_222L)
        assertThat(result.unallocatedPaise).isEqualTo(0L)
        assertThat(result.allocations.values.sum() + result.unallocatedPaise).isEqualTo(10_000L)
    }

    @Test
    fun apportionProportionally_capsAtRemainingCapacity() {
        // P1 needs only 1,000 paise
        val goals = listOf(
            GoalApportionmentItem(goalId = 1L, name = "P1 Small", priority = 1, targetPaise = 1_000L, currentBalancePaise = 0L),
            GoalApportionmentItem(goalId = 2L, name = "P2 Big", priority = 2, targetPaise = 100_000_00L, currentBalancePaise = 0L)
        )

        // Move 20,000 paise
        val result = calculator.apportionProportionally(20_000L, goals)

        assertThat(result.allocations[1L]).isAtMost(1_000L)
        assertThat(result.allocations.values.sum() + result.unallocatedPaise).isEqualTo(20_000L)
    }

    @Test
    fun apportionProportionally_allGoalsSatisfied_keepsAllUnallocated() {
        val goals = listOf(
            GoalApportionmentItem(goalId = 1L, name = "Done Goal", priority = 1, targetPaise = 5_000_00L, currentBalancePaise = 5_000_00L)
        )

        val result = calculator.apportionProportionally(10_000_00L, goals)

        assertThat(result.allocations[1L]).isEqualTo(0L)
        assertThat(result.unallocatedPaise).isEqualTo(10_000_00L)
        assertThat(result.allocations.values.sum() + result.unallocatedPaise).isEqualTo(10_000_00L)
    }

    @Test
    fun goalApportionmentResult_throwsIfSumMismatch() {
        assertThrows(IllegalArgumentException::class.java) {
            GoalApportionmentResult(
                totalMovedPaise = 10_000L,
                allocations = mapOf(1L to 5_000L),
                unallocatedPaise = 4_999L // sum is 9999 != 10000
            )
        }
    }
}
