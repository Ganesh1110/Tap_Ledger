package com.tapledger.domain.calculator

import com.tapledger.core.database.entity.BudgetAdjustmentEntity
import com.tapledger.core.database.entity.BudgetAllocationEntity
import com.tapledger.core.database.entity.BudgetPeriodEntity
import com.tapledger.core.database.entity.TransactionAllocationEntity
import com.tapledger.core.database.entity.TransactionEntity
import com.tapledger.core.model.BudgetBucket
import com.tapledger.core.model.TransactionKind
import com.tapledger.core.model.TransactionStatus
import com.tapledger.domain.model.BudgetCategorySummary
import com.tapledger.domain.model.BudgetPeriodSummary

object BudgetCalculator {

    /**
     * Calculates the complete budget summary for a given budget period.
     * Respects monthly snapshots, budget adjustments, refunds, and overspending thresholds.
     */
    fun calculateBudgetPeriodSummary(
        period: BudgetPeriodEntity,
        allocations: List<BudgetAllocationEntity>,
        adjustments: List<BudgetAdjustmentEntity>,
        transactions: List<TransactionEntity>,
        allocationsForTransactions: List<TransactionAllocationEntity>
    ): BudgetPeriodSummary {
        // Map transaction id -> transaction kind/status for lookup
        val txMap = transactions.associateBy { it.id }

        // Compute adjustments per category
        val netAdjustmentPerCategory = mutableMapOf<Long, Long>()
        for (adj in adjustments) {
            netAdjustmentPerCategory[adj.fromCategoryId] =
                (netAdjustmentPerCategory[adj.fromCategoryId] ?: 0L) - adj.amountPaise
            netAdjustmentPerCategory[adj.toCategoryId] =
                (netAdjustmentPerCategory[adj.toCategoryId] ?: 0L) + adj.amountPaise
        }

        // Compute spending and refunds per category
        val expensePerCategory = mutableMapOf<Long, Long>()
        val refundPerCategory = mutableMapOf<Long, Long>()

        for (txAlloc in allocationsForTransactions) {
            val tx = txMap[txAlloc.transactionId] ?: continue
            // Skip VOIDED and IGNORED transactions
            if (tx.status == TransactionStatus.VOIDED || tx.status == TransactionStatus.IGNORED) {
                continue
            }

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
                else -> {
                    // Transfers and general income do not affect category spending budgets
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

        for (alloc in allocations) {
            val baseBudget = alloc.amountPaise
            val adjustment = netAdjustmentPerCategory[alloc.categoryId] ?: 0L
            val adjustedBudget = baseBudget + adjustment

            val expense = expensePerCategory[alloc.categoryId] ?: 0L
            val refund = refundPerCategory[alloc.categoryId] ?: 0L
            val netSpent = expense - refund
            val remaining = adjustedBudget - netSpent

            val percentageUsed = if (adjustedBudget > 0L) {
                ((netSpent * 100L) / adjustedBudget).toInt()
            } else if (netSpent > 0L) {
                100
            } else {
                0
            }

            val isWarning = percentageUsed in 80..100
            val isExceeded = percentageUsed > 100 || netSpent > adjustedBudget

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
                isExceeded = isExceeded
            )
            categorySummaries.add(categorySummary)

            totalBudgeted += adjustedBudget
            totalNetSpent += netSpent

            val bucket = alloc.bucketSnapshot
            bucketTotals[bucket] = (bucketTotals[bucket] ?: 0L) + adjustedBudget
            bucketSpent[bucket] = (bucketSpent[bucket] ?: 0L) + netSpent
        }

        val unallocatedBudget = period.incomePaise - totalBudgeted

        return BudgetPeriodSummary(
            period = period,
            totalIncomePaise = period.incomePaise,
            totalBudgetedPaise = totalBudgeted,
            unallocatedBudgetPaise = unallocatedBudget,
            totalSpentPaise = totalNetSpent,
            totalRemainingPaise = totalBudgeted - totalNetSpent,
            bucketTotals = bucketTotals,
            bucketSpent = bucketSpent,
            categorySummaries = categorySummaries
        )
    }
}
