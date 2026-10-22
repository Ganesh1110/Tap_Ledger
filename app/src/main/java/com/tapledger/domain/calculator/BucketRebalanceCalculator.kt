package com.tapledger.domain.calculator

data class CategoryRebalanceInput(
    val categoryId: Long,
    val currentAllocationPaise: Long,
    val spentPaise: Long = 0L
)

data class RebalanceResult(
    val adjustments: Map<Long, Long>, // categoryId to newAllocationPaise
    val success: Boolean,
    val errorMessage: String? = null
)

/**
 * Calculates proportional rebalancing of existing category allocations within a budget bucket
 * when a new category is added or an existing category is updated, ensuring the total bucket
 * allocation exactly matches the target 50/30/20 limit without rounding drift.
 */
object BucketRebalanceCalculator {

    /**
     * Rebalances allocations of existing categories in a bucket.
     *
     * @param targetBucketLimitPaise Total target pool for this bucket (e.g. 50% of monthly planned income).
     * @param targetCategoryAllocationPaise The desired allocation for the new/edited category.
     * @param existingCategories Other categories in this bucket to be adjusted proportionally.
     * @param roundToRupees When true (default), apportionments are computed in units of 1 Rupee (100 paise),
     *                      yielding clean integer rupee budgets with zero drift.
     */
    fun calculateRebalance(
        targetBucketLimitPaise: Long,
        targetCategoryAllocationPaise: Long,
        existingCategories: List<CategoryRebalanceInput>,
        roundToRupees: Boolean = true
    ): RebalanceResult {
        require(targetBucketLimitPaise >= 0L) { "Bucket limit cannot be negative" }
        require(targetCategoryAllocationPaise >= 0L) { "Category allocation cannot be negative" }

        if (existingCategories.isEmpty()) {
            return RebalanceResult(adjustments = emptyMap(), success = true)
        }

        if (targetCategoryAllocationPaise > targetBucketLimitPaise) {
            val limitRupees = targetBucketLimitPaise / 100L
            val catRupees = targetCategoryAllocationPaise / 100L
            return RebalanceResult(
                adjustments = emptyMap(),
                success = false,
                errorMessage = "Category budget (₹$catRupees) exceeds the entire bucket limit (₹$limitRupees)."
            )
        }

        val availableForExistingPaise = targetBucketLimitPaise - targetCategoryAllocationPaise

        // Safety invariant: Allocation cannot drop below spent paise
        val totalCommittedSpentPaise = existingCategories.sumOf { maxOf(0L, it.spentPaise) }
        if (availableForExistingPaise < totalCommittedSpentPaise) {
            val spentRupees = (totalCommittedSpentPaise + 99L) / 100L
            val availRupees = availableForExistingPaise / 100L
            return RebalanceResult(
                adjustments = emptyMap(),
                success = false,
                errorMessage = "Cannot auto-align: categories have already spent ₹$spentRupees, exceeding available ₹$availRupees."
            )
        }

        // Apportionment unit: 1 Rupee (100 paise) or 1 Paisa
        val unit = if (roundToRupees) 100L else 1L
        val totalUnits = availableForExistingPaise / unit

        // Minimum floor in units per category (guaranteeing newAllocation >= spentPaise)
        val floorUnits = existingCategories.associate { cat ->
            val floorPaise = maxOf(0L, cat.spentPaise)
            val units = (floorPaise + (unit - 1)) / unit // ceil to unit
            cat.categoryId to units
        }
        val totalFloorUnits = floorUnits.values.sum()

        if (totalUnits < totalFloorUnits) {
            // If whole-rupee rounding pushes floor over available, fall back to exact paise
            return calculateRebalance(
                targetBucketLimitPaise = targetBucketLimitPaise,
                targetCategoryAllocationPaise = targetCategoryAllocationPaise,
                existingCategories = existingCategories,
                roundToRupees = false
            )
        }

        val flexibleUnitsToDistribute = totalUnits - totalFloorUnits

        // Proportional weights: based on current allocation headroom or current allocation
        val weights = existingCategories.associate { cat ->
            val curUnits = cat.currentAllocationPaise / unit
            val floor = floorUnits[cat.categoryId] ?: 0L
            val headroom = maxOf(0L, curUnits - floor)
            cat.categoryId to if (headroom > 0L) headroom else maxOf(1L, curUnits)
        }
        val totalWeight = weights.values.sum().coerceAtLeast(1L)

        // Largest-Remainder Distribution
        data class Quota(
            val categoryId: Long,
            val integerPart: Long,
            val remainder: Long
        )

        val quotas = existingCategories.map { cat ->
            val w = weights[cat.categoryId] ?: 1L
            val intPart = (flexibleUnitsToDistribute * w) / totalWeight
            val rem = (flexibleUnitsToDistribute * w) % totalWeight
            Quota(cat.categoryId, intPart, rem)
        }

        val baseAllocated = quotas.sumOf { it.integerPart }
        val leftoverUnits = (flexibleUnitsToDistribute - baseAllocated).toInt()

        // Distribute 1 unit to highest remainders
        val sortedByRemainder = quotas.sortedWith(
            compareByDescending<Quota> { it.remainder }
                .thenBy { it.categoryId }
        )
        val bonusRecipients = sortedByRemainder.take(leftoverUnits).map { it.categoryId }.toSet()

        val finalAdjustments = mutableMapOf<Long, Long>()
        for (q in quotas) {
            val floor = floorUnits[q.categoryId] ?: 0L
            val bonus = if (q.categoryId in bonusRecipients) 1L else 0L
            val totalUnitsForCat = floor + q.integerPart + bonus
            finalAdjustments[q.categoryId] = totalUnitsForCat * unit
        }

        // Distribute any leftover sub-unit paise to top category if roundToRupees was used
        val totalAdjustedPaise = finalAdjustments.values.sum()
        val remainderPaise = availableForExistingPaise - totalAdjustedPaise
        if (remainderPaise > 0L && existingCategories.isNotEmpty()) {
            val topCatId = existingCategories.first().categoryId
            finalAdjustments[topCatId] = (finalAdjustments[topCatId] ?: 0L) + remainderPaise
        }

        return RebalanceResult(
            adjustments = finalAdjustments,
            success = true
        )
    }
}
