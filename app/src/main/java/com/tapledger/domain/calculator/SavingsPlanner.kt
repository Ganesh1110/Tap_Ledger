package com.tapledger.domain.calculator

import com.tapledger.core.database.entity.SavingsGoalEntity
import com.tapledger.core.database.entity.SavingsPlanEntryEntity
import com.tapledger.core.model.SavingsGoalStatus
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Calculates a monthly savings plan for active goals (Flow.md §18, Phase 2).
 *
 * Distributes the planned/actual savings amount across active goals proportionally
 * according to priority weight (P1 > P2 > P3 > P4) using integer arithmetic.
 */
@Singleton
class SavingsPlanner @Inject constructor() {

    /**
     * Returns a list of [SavingsPlanEntryEntity] for [periodId] based on the supplied goals.
     *
     * @param goals all savings goals to consider.
     * @param goalBalances current accumulated balance per goal ID.
     * @param periodId the target budget period ID.
     * @param totalSavingsPaise amount in paise to distribute across goals.
     * @param createdAt timestamp for entity creation.
     */
    fun planForPeriod(
        goals: List<SavingsGoalEntity>,
        goalBalances: Map<Long, Long>,
        periodId: Long,
        totalSavingsPaise: Long,
        createdAt: Long = System.currentTimeMillis()
    ): List<SavingsPlanEntryEntity> {
        val eligibleGoals = goals.filter { goal ->
            goal.status == SavingsGoalStatus.ACTIVE &&
                (goalBalances[goal.id] ?: 0L) < goal.targetPaise
        }
        if (eligibleGoals.isEmpty() || totalSavingsPaise <= 0L) return emptyList()

        fun getWeight(priority: Int): Int = maxOf(1, 5 - priority)

        val totalWeight = eligibleGoals.sumOf { getWeight(it.priority) }
        val plan = mutableListOf<SavingsPlanEntryEntity>()
        var allocated = 0L

        for ((index, goal) in eligibleGoals.withIndex()) {
            val weight = getWeight(goal.priority)
            val amount = if (index == eligibleGoals.lastIndex) {
                totalSavingsPaise - allocated
            } else {
                ((totalSavingsPaise * weight.toLong() + totalWeight.toLong() - 1L) / totalWeight.toLong())
            }
            allocated += amount

            plan.add(
                SavingsPlanEntryEntity(
                    id = 0L,
                    periodId = periodId,
                    goalId = goal.id,
                    plannedPaise = amount,
                    createdAt = createdAt
                )
            )
        }
        return plan
    }
}
