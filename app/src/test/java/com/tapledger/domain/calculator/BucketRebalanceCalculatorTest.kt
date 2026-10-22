package com.tapledger.domain.calculator

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class BucketRebalanceCalculatorTest {

    @Test
    fun rebalance_zeroRemainderAndExactMatch() {
        // Needs 50% = ₹25,500. Rent ₹12,000, Groceries ₹8,000, Utilities ₹5,500
        val targetBucketLimitPaise = 25_500_00L
        val newCategoryBudgetPaise = 3_000_00L // ₹3,000

        val existing = listOf(
            CategoryRebalanceInput(categoryId = 1, currentAllocationPaise = 12_000_00L, spentPaise = 0L),
            CategoryRebalanceInput(categoryId = 2, currentAllocationPaise = 8_000_00L, spentPaise = 0L),
            CategoryRebalanceInput(categoryId = 3, currentAllocationPaise = 5_500_00L, spentPaise = 0L)
        )

        val result = BucketRebalanceCalculator.calculateRebalance(
            targetBucketLimitPaise = targetBucketLimitPaise,
            targetCategoryAllocationPaise = newCategoryBudgetPaise,
            existingCategories = existing
        )

        assertThat(result.success).isTrue()
        assertThat(result.adjustments).hasSize(3)

        val sumExisting = result.adjustments.values.sum()
        // Available for existing was 25,500 - 3,000 = 22,500
        assertThat(sumExisting).isEqualTo(22_500_00L)
        // Total bucket sum: new category + existing == exact target limit
        assertThat(sumExisting + newCategoryBudgetPaise).isEqualTo(targetBucketLimitPaise)

        // Verify each category was reduced proportionally
        val rent = result.adjustments[1]!!
        val groceries = result.adjustments[2]!!
        val utilities = result.adjustments[3]!!

        assertThat(rent).isEqualTo(10_588_00L) // ₹10,588
        assertThat(groceries).isEqualTo(7_059_00L) // ₹7,059
        assertThat(utilities).isEqualTo(4_853_00L) // ₹4,853
        assertThat(rent + groceries + utilities).isEqualTo(22_500_00L)
    }

    @Test
    fun rebalance_respectsSpentFloor() {
        // Target: ₹10,000. New category: ₹2,000. Available: ₹8,000.
        // Cat 1 has spent ₹5,000. Cat 2 has spent ₹0.
        val targetLimit = 10_000_00L
        val newBudget = 2_000_00L

        val existing = listOf(
            CategoryRebalanceInput(categoryId = 1, currentAllocationPaise = 6_000_00L, spentPaise = 5_000_00L),
            CategoryRebalanceInput(categoryId = 2, currentAllocationPaise = 4_000_00L, spentPaise = 0L)
        )

        val result = BucketRebalanceCalculator.calculateRebalance(
            targetBucketLimitPaise = targetLimit,
            targetCategoryAllocationPaise = newBudget,
            existingCategories = existing
        )

        assertThat(result.success).isTrue()
        // Cat 1 must not be less than spent ₹5,000
        assertThat(result.adjustments[1]!!).isAtLeast(5_000_00L)
        // Total must equal ₹8,000
        assertThat(result.adjustments.values.sum()).isEqualTo(8_000_00L)
    }

    @Test
    fun rebalance_rejectsWhenCategoryExceedsBucketLimit() {
        val targetLimit = 10_000_00L
        val newBudget = 12_000_00L // Exceeds bucket limit

        val existing = listOf(
            CategoryRebalanceInput(categoryId = 1, currentAllocationPaise = 5_000_00L, spentPaise = 0L)
        )

        val result = BucketRebalanceCalculator.calculateRebalance(
            targetBucketLimitPaise = targetLimit,
            targetCategoryAllocationPaise = newBudget,
            existingCategories = existing
        )

        assertThat(result.success).isFalse()
        assertThat(result.errorMessage).contains("exceeds the entire bucket limit")
    }

    @Test
    fun rebalance_rejectsWhenSpentExceedsAvailable() {
        val targetLimit = 10_000_00L
        val newBudget = 6_000_00L // Leaves ₹4,000 available

        val existing = listOf(
            CategoryRebalanceInput(categoryId = 1, currentAllocationPaise = 6_000_00L, spentPaise = 5_000_00L) // Spent ₹5,000 > available ₹4,000
        )

        val result = BucketRebalanceCalculator.calculateRebalance(
            targetBucketLimitPaise = targetLimit,
            targetCategoryAllocationPaise = newBudget,
            existingCategories = existing
        )

        assertThat(result.success).isFalse()
        assertThat(result.errorMessage).contains("already spent")
    }

    @Test
    fun rebalance_emptyExistingCategories_returnsSuccess() {
        val result = BucketRebalanceCalculator.calculateRebalance(
            targetBucketLimitPaise = 25_500_00L,
            targetCategoryAllocationPaise = 3_000_00L,
            existingCategories = emptyList()
        )

        assertThat(result.success).isTrue()
        assertThat(result.adjustments).isEmpty()
    }
}
