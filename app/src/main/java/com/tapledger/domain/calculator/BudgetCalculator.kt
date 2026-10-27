package com.tapledger.domain.calculator

import com.tapledger.core.database.entity.AdvanceLedgerEntity
import com.tapledger.core.database.entity.BudgetAdjustmentEntity
import com.tapledger.core.database.entity.BudgetAllocationEntity
import com.tapledger.core.database.entity.BudgetPeriodEntity
import com.tapledger.core.database.entity.TransactionAllocationEntity
import com.tapledger.core.database.entity.TransactionEntity
import com.tapledger.core.model.AdvanceDirection
import com.tapledger.core.model.AdvancePolicy
import com.tapledger.core.model.BudgetBucket
import com.tapledger.core.model.TransactionDirection
import com.tapledger.core.model.TransactionKind
import com.tapledger.core.model.TransactionStatus
import com.tapledger.domain.model.BudgetCategorySummary
import com.tapledger.domain.model.BudgetPeriodSummary
import com.tapledger.domain.model.Target503020

object BudgetCalculator {

    /**
     * Flow.md §8.1: Zero-Remainder Target Math:
     * NeedsTarget   = floor(I * 50L / 100L)
     * WantsTarget   = floor(I * 30L / 100L)
     * SavingsTarget = I - NeedsTarget - WantsTarget
     * Invariant: NeedsTarget + WantsTarget + SavingsTarget == I
     */
    fun calculate503020Targets(plannedIncomePaise: Long): Target503020 {
        require(plannedIncomePaise >= 0L) { "Planned income cannot be negative" }
        val needs = (plannedIncomePaise * 50L) / 100L
        val wants = (plannedIncomePaise * 30L) / 100L
        val savings = plannedIncomePaise - needs - wants
        return Target503020(
            needsPaise = needs,
            wantsPaise = wants,
            savingsPaise = savings
        )
    }

    /**
     * Flow.md §8.1: Dynamic Verified Actual Income:
     * ActualIncome = Σ t.amount_paise where:
     * t.kind in (INCOME, INTEREST_EARNED) and t.status in (CONFIRMED, CATEGORIZED) and t.direction == IN
     */
    fun calculateActualIncome(transactions: List<TransactionEntity>): Long {
        return transactions.filter { tx ->
            (tx.kind == TransactionKind.INCOME || tx.kind == TransactionKind.INTEREST_EARNED) &&
            (tx.status == TransactionStatus.CONFIRMED || tx.status == TransactionStatus.CATEGORIZED) &&
            tx.direction == TransactionDirection.IN
        }.sumOf { it.amountPaise }
    }

    /**
     * Calculates the complete budget summary for a given budget period (Flow.md §8.2, §8.3, §8.4, §8.5).
     * - Adjusted Budget: Base + AdjIn - AdjOut + AdvPos - AdvNeg
     * - Net Spent: Expense - Refund + SavingsContrib - SavingsWithdraw
     * - Savings Goal Semantics: Savings envelopes are goals, never exceeded
     * - Dashboard Hero Spend & Budget (Needs + Wants only) vs Actual Savings
     */
    fun calculateBudgetPeriodSummary(
        period: BudgetPeriodEntity,
        allocations: List<BudgetAllocationEntity>,
        adjustments: List<BudgetAdjustmentEntity>,
        transactions: List<TransactionEntity>,
        allocationsForTransactions: List<TransactionAllocationEntity>,
        advances: List<AdvanceLedgerEntity> = emptyList(),
        categoryNames: Map<Long, String> = emptyMap()
    ): BudgetPeriodSummary {
        val txMap = transactions.associateBy { it.id }

        // Compute adjustments per category (§8.2)
        val adjInPerCategory = mutableMapOf<Long, Long>()
        val adjOutPerCategory = mutableMapOf<Long, Long>()
        for (adj in adjustments) {
            adjOutPerCategory[adj.fromCategoryId] = (adjOutPerCategory[adj.fromCategoryId] ?: 0L) + adj.amountPaise
            adjInPerCategory[adj.toCategoryId] = (adjInPerCategory[adj.toCategoryId] ?: 0L) + adj.amountPaise
        }

        // Compute advance ledger per category (§8.2)
        val advPosPerCategory = mutableMapOf<Long, Long>()
        val advNegPerCategory = mutableMapOf<Long, Long>()
        for (adv in advances) {
            if (adv.appliedToPeriodId == period.id) {
                when (adv.direction) {
                    AdvanceDirection.POSITIVE -> {
                        advPosPerCategory[adv.categoryId] = (advPosPerCategory[adv.categoryId] ?: 0L) + adv.amountPaise
                    }
                    AdvanceDirection.NEGATIVE -> {
                        advNegPerCategory[adv.categoryId] = (advNegPerCategory[adv.categoryId] ?: 0L) + adv.amountPaise
                    }
                }
            }
        }

        // Compute spending, refunds, and savings transfers per category (§8.3)
        val expensePerCategory = mutableMapOf<Long, Long>()
        val refundPerCategory = mutableMapOf<Long, Long>()
        val savingsContribPerCategory = mutableMapOf<Long, Long>()
        val savingsWithdrawPerCategory = mutableMapOf<Long, Long>()

        for (txAlloc in allocationsForTransactions) {
            val tx = txMap[txAlloc.transactionId] ?: continue
            // Only CATEGORIZED transactions hit budget spending & envelopes (Flow.md §4.2, §8.3)
            if (tx.status != TransactionStatus.CATEGORIZED) continue

            // Must fall within half-open interval [startAt, endAt) (§8.3)
            if (tx.occurredAt < period.startAt || tx.occurredAt >= period.endAt) continue

            when (tx.kind) {
                TransactionKind.EXPENSE,
                TransactionKind.FEE,
                TransactionKind.INTEREST_CHARGED -> {
                    expensePerCategory[txAlloc.categoryId] =
                        (expensePerCategory[txAlloc.categoryId] ?: 0L) + txAlloc.amountPaise
                }
                TransactionKind.REFUND -> {
                    refundPerCategory[txAlloc.categoryId] =
                        (refundPerCategory[txAlloc.categoryId] ?: 0L) + txAlloc.amountPaise
                }
                TransactionKind.TRANSFER -> {
                    if (tx.direction == TransactionDirection.OUT) {
                        // T_sav_contrib (§8.3)
                        savingsContribPerCategory[txAlloc.categoryId] =
                            (savingsContribPerCategory[txAlloc.categoryId] ?: 0L) + txAlloc.amountPaise
                    } else if (tx.direction == TransactionDirection.IN) {
                        // T_sav_withdraw (§8.3)
                        savingsWithdrawPerCategory[txAlloc.categoryId] =
                            (savingsWithdrawPerCategory[txAlloc.categoryId] ?: 0L) + txAlloc.amountPaise
                    }
                }
                else -> {
                    // Income or other types do not affect category envelope spending
                }
            }
        }

        val categorySummaries = mutableListOf<BudgetCategorySummary>()
        var totalBudgeted = 0L
        var totalNetSpent = 0L

        val bucketTotals = mutableMapOf<BudgetBucket, Long>()
        val bucketSpent = mutableMapOf<BudgetBucket, Long>()

        for (bucket in BudgetBucket.values()) {
            bucketTotals[bucket] = 0L
            bucketSpent[bucket] = 0L
        }

        // Guard against orphan categories: categories that have recorded activity (expenses,
        // refunds, adjustments, advances) but lack an explicit allocation row for this period.
        val allocatedCategoryIds = allocations.map { it.categoryId }.toSet()
        val orphanCategoryIds = (
            expensePerCategory.keys +
            refundPerCategory.keys +
            savingsContribPerCategory.keys +
            savingsWithdrawPerCategory.keys +
            adjInPerCategory.keys +
            adjOutPerCategory.keys +
            advPosPerCategory.keys +
            advNegPerCategory.keys
        ) - allocatedCategoryIds

        val syntheticAllocations = orphanCategoryIds.sorted().map { orphanCatId ->
            val isSavingsCategory = savingsContribPerCategory.containsKey(orphanCatId) ||
                savingsWithdrawPerCategory.containsKey(orphanCatId)
            val bucket = if (isSavingsCategory) BudgetBucket.SAVINGS else BudgetBucket.WANTS
            val name = categoryNames[orphanCatId] ?: "Unbudgeted Category #$orphanCatId"
            BudgetAllocationEntity(
                id = -orphanCatId,
                budgetPeriodId = period.id,
                categoryId = orphanCatId,
                categoryNameSnapshot = name,
                bucketSnapshot = bucket,
                sortOrderSnapshot = Int.MAX_VALUE,
                advancePolicySnapshot = AdvancePolicy.CARRY_TO_ADVANCE,
                amountPaise = 0L,
                createdAt = period.startAt,
                updatedAt = period.startAt
            )
        }

        val allAllocations = allocations + syntheticAllocations

        for (alloc in allAllocations) {
            val catId = alloc.categoryId
            val baseBudget = alloc.amountPaise
            val adjIn = adjInPerCategory[catId] ?: 0L
            val adjOut = adjOutPerCategory[catId] ?: 0L
            val advPos = advPosPerCategory[catId] ?: 0L
            val advNeg = advNegPerCategory[catId] ?: 0L

            // AdjustedBudget(C) = Base + AdjIn - AdjOut + AdvPos - AdvNeg (§8.2)
            val adjustedBudget = baseBudget + adjIn - adjOut + advPos - advNeg

            val expense = expensePerCategory[catId] ?: 0L
            val refund = refundPerCategory[catId] ?: 0L
            val savContrib = savingsContribPerCategory[catId] ?: 0L
            val savWithdraw = savingsWithdrawPerCategory[catId] ?: 0L

            // NetSpent(C) = Expense - Refund + SavContrib - SavWithdraw (§8.3)
            val netSpent = expense - refund + savContrib - savWithdraw
            val remaining = adjustedBudget - netSpent

            val isSavings = alloc.bucketSnapshot == BudgetBucket.SAVINGS

            // Flow.md §8.4: Savings Envelopes are Goals (Surplus is Celebrated, never Exceeded)
            val isExceeded = if (isSavings) false else netSpent > adjustedBudget

            val percentageUsed = if (adjustedBudget > 0L) {
                ((netSpent * 100L) / adjustedBudget).coerceIn(0L, 999L).toInt()
            } else if (netSpent > 0L) {
                100
            } else {
                0
            }

            val isWarning = !isSavings && !isExceeded && percentageUsed in 80..100
            val isGoalAchieved = isSavings && percentageUsed >= 100

            val categorySummary = BudgetCategorySummary(
                allocation = alloc,
                baseBudgetPaise = baseBudget,
                adjustedBudgetPaise = adjustedBudget,
                expensePaise = expense,
                refundPaise = refund,
                netSpentPaise = netSpent,
                remainingPaise = remaining,
                percentageUsed = percentageUsed,
                isWarning = isWarning,
                isExceeded = isExceeded,
                isGoalAchieved = isGoalAchieved
            )
            categorySummaries.add(categorySummary)

            totalBudgeted += adjustedBudget
            totalNetSpent += netSpent

            val bucket = alloc.bucketSnapshot
            bucketTotals[bucket] = (bucketTotals[bucket] ?: 0L) + adjustedBudget
            bucketSpent[bucket] = (bucketSpent[bucket] ?: 0L) + netSpent
        }

        val unallocatedBudget = period.incomePaise - totalBudgeted

        // Flow.md §8.5: Dashboard Hero vs. Actual Savings Metrics
        var dashboardHeroSpend = 0L
        var dashboardHeroBudget = 0L
        var actualSavings = 0L

        for (cat in categorySummaries) {
            if (cat.allocation.bucketSnapshot == BudgetBucket.SAVINGS) {
                actualSavings += cat.netSpentPaise
            } else {
                dashboardHeroSpend += cat.netSpentPaise
                dashboardHeroBudget += cat.adjustedBudgetPaise
            }
        }

        val actualIncome = calculateActualIncome(transactions)

        return BudgetPeriodSummary(
            period = period,
            totalIncomePaise = period.incomePaise,
            totalBudgetedPaise = totalBudgeted,
            unallocatedBudgetPaise = unallocatedBudget,
            totalSpentPaise = totalNetSpent,
            totalRemainingPaise = totalBudgeted - totalNetSpent,
            bucketTotals = bucketTotals,
            bucketSpent = bucketSpent,
            categorySummaries = categorySummaries,
            dashboardHeroSpendPaise = dashboardHeroSpend,
            dashboardHeroBudgetPaise = dashboardHeroBudget,
            actualSavingsPaise = actualSavings,
            actualIncomePaise = actualIncome
        )
    }
}
