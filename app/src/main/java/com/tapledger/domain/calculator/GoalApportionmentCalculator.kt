package com.tapledger.domain.calculator

import javax.inject.Inject
import javax.inject.Singleton

data class GoalApportionmentItem(
    val goalId: Long,
    val name: String,
    val priority: Int,
    val targetPaise: Long,
    val currentBalancePaise: Long
) {
    val remainingNeededPaise: Long get() = maxOf(0L, targetPaise - currentBalancePaise)
}

data class GoalApportionmentResult(
    val totalMovedPaise: Long,
    val allocations: Map<Long, Long>,
    val unallocatedPaise: Long
) {
    init {
        val allocatedSum = allocations.values.sum()
        require(allocatedSum + unallocatedPaise == totalMovedPaise) {
            "Invariant violation: allocated ($allocatedSum) + unallocated ($unallocatedPaise) != total ($totalMovedPaise)"
        }
    }
}

/**
 * Calculates goal apportionments for funds moved into the savings account (Flow.md §18, D013, D2 Invariant).
 * Guarantees that the sum of allocated amounts plus unallocated leftover strictly equals moved_paise down to 1 paisa.
 * Strictly adheres to Zero-Float invariant.
 */
@Singleton
class GoalApportionmentCalculator @Inject constructor() {

    /**
     * Priority-first apportionment:
     * Fulfills highest priority goals (Priority 1 > Priority 2 > ...) up to their remainingNeeded capacity.
     * Any surplus after satisfying active goals is assigned to unallocated savings.
     */
    fun apportionByPriority(
        movedPaise: Long,
        goals: List<GoalApportionmentItem>
    ): GoalApportionmentResult {
        require(movedPaise >= 0L) { "Moved paise cannot be negative" }
        if (movedPaise == 0L || goals.isEmpty()) {
            return GoalApportionmentResult(
                totalMovedPaise = movedPaise,
                allocations = goals.associate { it.goalId to 0L },
                unallocatedPaise = movedPaise
            )
        }

        val sortedGoals = goals.sortedWith(compareBy({ it.priority }, { it.goalId }))
        var remainingPool = movedPaise
        val allocations = mutableMapOf<Long, Long>()

        for (goal in sortedGoals) {
            val needed = goal.remainingNeededPaise
            val grant = minOf(remainingPool, needed)
            allocations[goal.goalId] = grant
            remainingPool -= grant
        }

        return GoalApportionmentResult(
            totalMovedPaise = movedPaise,
            allocations = allocations,
            unallocatedPaise = remainingPool
        )
    }

    /**
     * Proportional apportionment using D2 Largest-Remainder algorithm:
     * Weights active goals by priority weight: weight(p) = max(1, 5 - p).
     * Distributes movedPaise across goals proportionally, bounded by remainingNeeded capacity.
     */
    fun apportionProportionally(
        movedPaise: Long,
        goals: List<GoalApportionmentItem>
    ): GoalApportionmentResult {
        require(movedPaise >= 0L) { "Moved paise cannot be negative" }
        if (movedPaise == 0L || goals.isEmpty()) {
            return GoalApportionmentResult(
                totalMovedPaise = movedPaise,
                allocations = goals.associate { it.goalId to 0L },
                unallocatedPaise = movedPaise
            )
        }

        val eligibleGoals = goals.filter { it.remainingNeededPaise > 0L }
        if (eligibleGoals.isEmpty()) {
            return GoalApportionmentResult(
                totalMovedPaise = movedPaise,
                allocations = goals.associate { it.goalId to 0L },
                unallocatedPaise = movedPaise
            )
        }

        fun getWeight(priority: Int): Long = maxOf(1L, 5L - priority.toLong())
        val totalWeight = eligibleGoals.sumOf { getWeight(it.priority) }

        val allocations = mutableMapOf<Long, Long>()
        for (g in goals) {
            allocations[g.goalId] = 0L
        }

        var unallocated = movedPaise
        data class GoalQuota(
            val goal: GoalApportionmentItem,
            val integerPart: Long,
            val remainder: Long
        )

        val quotas = eligibleGoals.map { goal ->
            val w = getWeight(goal.priority)
            val integerPart = (movedPaise * w) / totalWeight
            val remainder = (movedPaise * w) % totalWeight
            GoalQuota(goal, integerPart, remainder)
        }

        var totalBaseAllocated = 0L
        val provisionalAllocations = mutableMapOf<Long, Long>()

        for (q in quotas) {
            val capped = minOf(q.integerPart, q.goal.remainingNeededPaise)
            provisionalAllocations[q.goal.goalId] = capped
            totalBaseAllocated += capped
        }

        var surplusToDistribute = movedPaise - totalBaseAllocated

        // Distribute remaining paise to goals with the largest remainder that still have capacity
        val sortedByRemainder = quotas.sortedWith(
            compareByDescending<GoalQuota> { it.remainder }
                .thenBy { it.goal.priority }
                .thenBy { it.goal.goalId }
        )

        for (q in sortedByRemainder) {
            if (surplusToDistribute <= 0L) break
            val current = provisionalAllocations[q.goal.goalId] ?: 0L
            if (current < q.goal.remainingNeededPaise) {
                provisionalAllocations[q.goal.goalId] = current + 1L
                surplusToDistribute -= 1L
            }
        }

        provisionalAllocations.forEach { (id, amt) ->
            allocations[id] = amt
        }

        return GoalApportionmentResult(
            totalMovedPaise = movedPaise,
            allocations = allocations,
            unallocatedPaise = surplusToDistribute
        )
    }
}
