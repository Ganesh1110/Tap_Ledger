package com.tapledger.domain.validator

import com.tapledger.core.database.entity.AccountEntity
import com.tapledger.core.database.entity.AdvanceLedgerEntity
import com.tapledger.core.database.entity.BudgetAdjustmentEntity
import com.tapledger.core.database.entity.BudgetAllocationEntity
import com.tapledger.core.database.entity.BudgetPeriodEntity
import com.tapledger.core.database.entity.CategoryEntity
import com.tapledger.core.database.entity.MonthCloseEntity
import com.tapledger.core.database.entity.TransactionAllocationEntity
import com.tapledger.core.database.entity.TransactionEntity
import com.tapledger.core.model.AccountType
import com.tapledger.core.model.AdvanceDirection
import com.tapledger.core.model.BudgetBucket
import com.tapledger.core.model.BudgetPeriodStatus
import com.tapledger.core.model.TransactionDirection
import com.tapledger.core.model.TransactionKind
import com.tapledger.core.model.TransactionStatus
import com.tapledger.domain.calculator.BudgetCalculator
import com.tapledger.domain.model.IntegrityIssue
import com.tapledger.domain.model.IntegritySeverity
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Data Integrity & Invariant Auditor (Flow.md §12).
 * Verifies all 18 formal database rules with exact codes and severities:
 * T1..T4, A1..A4, B1..B4, C1, P1, M1, M2, R1, R2.
 */
@Singleton
open class DataIntegrityChecker @Inject constructor() {

    /**
     * Executes all 18 invariant checks across database records.
     * Returns a list of actionable issues found.
     */
    open fun verifyIntegrity(
        accounts: List<AccountEntity>,
        categories: List<CategoryEntity>,
        periods: List<BudgetPeriodEntity>,
        allocations: List<BudgetAllocationEntity>,
        transactions: List<TransactionEntity>,
        transactionAllocations: List<TransactionAllocationEntity>,
        monthCloses: List<MonthCloseEntity>,
        adjustments: List<BudgetAdjustmentEntity> = emptyList(),
        advances: List<AdvanceLedgerEntity> = emptyList(),
        savingsGoals: List<com.tapledger.core.database.entity.SavingsGoalEntity> = emptyList(),
        savingsContributions: List<com.tapledger.core.database.entity.SavingsContributionEntity> = emptyList(),
        savingsTransferPlans: List<com.tapledger.core.database.entity.SavingsTransferPlanEntity> = emptyList(),
        nowEpochMs: Long = com.tapledger.core.util.DateTimeUtils.nowEpochMillis()
    ): List<IntegrityIssue> {
        val issues = mutableListOf<IntegrityIssue>()



        val accountIds = accounts.map { it.id }.toSet()
        val categoryIds = categories.map { it.id }.toSet()
        val periodIds = periods.map { it.id }.toSet()
        val txById = transactions.associateBy { it.id }
        val allocsByTxId = transactionAllocations.groupBy { it.transactionId }

        // =============================================================
        // 1. Transaction Rules (T1..T4)
        // =============================================================

        // Rule T1_INVALID_ACCOUNT (CRITICAL):
        // ∀ t with status ∉ {PENDING, IGNORED, VOIDED}, t.account_id ∈ Accounts.id
        for (tx in transactions) {
            val isExemptStatus = tx.status == TransactionStatus.PENDING ||
                tx.status == TransactionStatus.IGNORED ||
                tx.status == TransactionStatus.VOIDED

            if (!isExemptStatus) {
                if (tx.accountId == null || !accountIds.contains(tx.accountId)) {
                    issues.add(
                        IntegrityIssue(
                            ruleId = "T1_INVALID_ACCOUNT",
                            message = "Transaction #${tx.id} with status ${tx.status} has invalid/null account #${tx.accountId}",
                            entityType = "Transaction",
                            entityId = tx.id.toString(),
                            severity = IntegritySeverity.CRITICAL
                        )
                    )
                }
            }

            // Rule T2_NON_POSITIVE_AMOUNT (ERROR): ∀ t, t.amount_paise > 0
            if (tx.amountPaise <= 0L) {
                issues.add(
                    IntegrityIssue(
                        ruleId = "T2_NON_POSITIVE_AMOUNT",
                        message = "Transaction #${tx.id} has non-positive amount: ${tx.amountPaise} paise",
                        entityType = "Transaction",
                        entityId = tx.id.toString(),
                        severity = IntegritySeverity.ERROR
                    )
                )
            }
        }

        // Rule T3_UNPAIRED_TRANSFER (CRITICAL):
        // Every paired transfer has linked counterpart with equal amount, opposite direction, and reciprocal counterparty_account_id
        val transferGroups = transactions.filter { it.transferGroupId != null }.groupBy { it.transferGroupId!! }
        for ((groupId, legs) in transferGroups) {
            if (legs.size != 2) {
                issues.add(
                    IntegrityIssue(
                        ruleId = "T3_UNPAIRED_TRANSFER",
                        message = "Transfer group #$groupId has ${legs.size} legs instead of exactly 2",
                        entityType = "TransferGroup",
                        entityId = groupId,
                        severity = IntegritySeverity.CRITICAL
                    )
                )
            } else {
                val leg1 = legs[0]
                val leg2 = legs[1]
                val amountMatches = leg1.amountPaise == leg2.amountPaise
                val directionsOpposite = (leg1.direction == TransactionDirection.OUT && leg2.direction == TransactionDirection.IN) ||
                    (leg1.direction == TransactionDirection.IN && leg2.direction == TransactionDirection.OUT)
                val accountsReciprocal = leg1.counterpartyAccountId == leg2.accountId && leg2.counterpartyAccountId == leg1.accountId

                if (!amountMatches || !directionsOpposite || !accountsReciprocal) {
                    issues.add(
                        IntegrityIssue(
                            ruleId = "T3_UNPAIRED_TRANSFER",
                            message = "Transfer group #$groupId fails pairing invariants (amount=$amountMatches, directions=$directionsOpposite, reciprocal=$accountsReciprocal)",
                            entityType = "TransferGroup",
                            entityId = groupId,
                            severity = IntegritySeverity.CRITICAL
                        )
                    )
                }
            }
        }
        for (tx in transactions) {
            if (tx.kind == TransactionKind.TRANSFER && tx.transferGroupId == null) {
                issues.add(
                    IntegrityIssue(
                        ruleId = "T3_UNPAIRED_TRANSFER",
                        message = "Transfer #${tx.id} lacks transfer_group_id",
                        entityType = "Transaction",
                        entityId = tx.id.toString(),
                        severity = IntegritySeverity.CRITICAL
                    )
                )
            }
        }

        // Rule T4_DUPLICATE_UPI_REF (ERROR):
        // Unique non-null (account_id, upi_reference, direction, amount_paise); if account_id IS NULL: unique (upi_reference, direction, amount_paise)
        val nonVoidedTxsWithRef = transactions.filter {
            it.status != TransactionStatus.VOIDED && !it.upiReference.isNullOrBlank()
        }
        val groupedByUpi = nonVoidedTxsWithRef.groupBy {
            val accKey = it.accountId?.toString() ?: "NULL"
            val refKey = it.upiReference!!.trim().lowercase(Locale.ENGLISH)
            "$accKey:$refKey:${it.direction}:${it.amountPaise}"
        }
        for ((key, duplicates) in groupedByUpi) {
            if (duplicates.size > 1) {
                issues.add(
                    IntegrityIssue(
                        ruleId = "T4_DUPLICATE_UPI_REF",
                        message = "Duplicate transaction captured for key '$key' across transactions: ${duplicates.map { it.id }}",
                        entityType = "Transaction",
                        entityId = duplicates.first().id.toString(),
                        severity = IntegritySeverity.ERROR
                    )
                )
            }
        }

        // =============================================================
        // 2. Allocation Rules (A1..A4)
        // =============================================================

        // Rule A1_SPLIT_SUM_MISMATCH (CRITICAL):
        // For categorized t: Σ Allocations ≡ t.amount_paise
        for (tx in transactions) {
            if (tx.status == TransactionStatus.CATEGORIZED) {
                val txAllocs = allocsByTxId[tx.id].orEmpty()
                val isStandardCategorizedTx = tx.kind == TransactionKind.EXPENSE ||
                    tx.kind == TransactionKind.REFUND ||
                    tx.kind == TransactionKind.FEE ||
                    tx.kind == TransactionKind.INTEREST_CHARGED

                if (isStandardCategorizedTx || txAllocs.isNotEmpty()) {
                    val sumAllocs = txAllocs.sumOf { it.amountPaise }
                    if (sumAllocs != tx.amountPaise) {
                        issues.add(
                            IntegrityIssue(
                                ruleId = "A1_SPLIT_SUM_MISMATCH",
                                message = "Categorized transaction #${tx.id} amount (${tx.amountPaise}) does not match allocations sum ($sumAllocs)",
                                entityType = "Transaction",
                                entityId = tx.id.toString(),
                                severity = IntegritySeverity.CRITICAL
                            )
                        )
                    }
                }
            }
        }

        // Rule A2_INVALID_CATEGORY (ERROR): ∀ a, a.category_id ∈ Categories.id
        for (alloc in transactionAllocations) {
            if (!categoryIds.contains(alloc.categoryId)) {
                issues.add(
                    IntegrityIssue(
                        ruleId = "A2_INVALID_CATEGORY",
                        message = "Transaction allocation #${alloc.id} references non-existent category #${alloc.categoryId}",
                        entityType = "TransactionAllocation",
                        entityId = alloc.id.toString(),
                        severity = IntegritySeverity.ERROR
                    )
                )
            }
        }
        for (budgetAlloc in allocations) {
            if (!categoryIds.contains(budgetAlloc.categoryId)) {
                issues.add(
                    IntegrityIssue(
                        ruleId = "A2_INVALID_CATEGORY",
                        message = "Budget allocation #${budgetAlloc.id} references non-existent category #${budgetAlloc.categoryId}",
                        entityType = "BudgetAllocation",
                        entityId = budgetAlloc.id.toString(),
                        severity = IntegritySeverity.ERROR
                    )
                )
            }
        }

        // Rule A3_NON_POSITIVE_ALLOCATION (ERROR): ∀ a, a.amount_paise > 0
        for (alloc in transactionAllocations) {
            if (alloc.amountPaise <= 0L) {
                issues.add(
                    IntegrityIssue(
                        ruleId = "A3_NON_POSITIVE_ALLOCATION",
                        message = "Transaction allocation #${alloc.id} has non-positive amount: ${alloc.amountPaise}",
                        entityType = "TransactionAllocation",
                        entityId = alloc.id.toString(),
                        severity = IntegritySeverity.ERROR
                    )
                )
            }
        }

        // Rule A4_DUPLICATE_CATEGORY_IN_SPLIT (ERROR): Unique (transaction_id, category_id)
        for ((txId, allocList) in allocsByTxId) {
            val catCounts = allocList.groupBy { it.categoryId }
            for ((catId, duplicates) in catCounts) {
                if (duplicates.size > 1) {
                    issues.add(
                        IntegrityIssue(
                            ruleId = "A4_DUPLICATE_CATEGORY_IN_SPLIT",
                            message = "Transaction #$txId repeats category #$catId multiple times in split",
                            entityType = "TransactionAllocation",
                            entityId = duplicates.first().id.toString(),
                            severity = IntegritySeverity.ERROR
                        )
                    )
                }
            }
        }

        // =============================================================
        // 3. Budget Rules (B1..B4)
        // =============================================================

        // Rule B1_INVALID_ADJUSTMENT (ERROR): from_cat ∈ Cat ∧ to_cat ∈ Cat
        for (adj in adjustments) {
            if (!categoryIds.contains(adj.fromCategoryId) || !categoryIds.contains(adj.toCategoryId)) {
                issues.add(
                    IntegrityIssue(
                        ruleId = "B1_INVALID_ADJUSTMENT",
                        message = "Budget adjustment #${adj.id} has invalid category references (from=${adj.fromCategoryId}, to=${adj.toCategoryId})",
                        entityType = "BudgetAdjustment",
                        entityId = adj.id.toString(),
                        severity = IntegritySeverity.ERROR
                    )
                )
            }
        }

        // Rule B2_NEGATIVE_BUDGET (CRITICAL): ∀ b, AdjustedBudget(b) ≥ 0
        // Rule B3_BUCKET_OVERALLOCATION (WARNING): Σ BaseAllocations ≤ BucketTarget
        // Rule B4_BUDGET_EQUILIBRIUM (WARNING): Σ AdjustedBudget ≤ Period.income + NetAdvances(P)
        for (period in periods) {
            val periodAllocs = allocations.filter { it.budgetPeriodId == period.id }
            val periodAdjs = adjustments.filter { it.budgetPeriodId == period.id }
            val periodAdvs = advances.filter { it.appliedToPeriodId == period.id }

            val allPeriodCatIds = (periodAllocs.map { it.categoryId } +
                periodAdjs.map { it.fromCategoryId } +
                periodAdjs.map { it.toCategoryId } +
                periodAdvs.map { it.categoryId }).toSet()

            var totalAdjustedBudgetPeriod = 0L

            for (catId in allPeriodCatIds) {
                val base = periodAllocs.find { it.categoryId == catId }?.amountPaise ?: 0L
                val adjIn = periodAdjs.filter { it.toCategoryId == catId }.sumOf { it.amountPaise }
                val adjOut = periodAdjs.filter { it.fromCategoryId == catId }.sumOf { it.amountPaise }
                val advPos = periodAdvs.filter { it.categoryId == catId && it.direction == AdvanceDirection.POSITIVE }.sumOf { it.amountPaise }
                val advNeg = periodAdvs.filter { it.categoryId == catId && it.direction == AdvanceDirection.NEGATIVE }.sumOf { it.amountPaise }

                val adjustedBudget = base + adjIn - adjOut + advPos - advNeg
                if (adjustedBudget < 0L) {
                    issues.add(
                        IntegrityIssue(
                            ruleId = "B2_NEGATIVE_BUDGET",
                            message = "Period #${period.id} category #$catId adjusted budget is negative: $adjustedBudget paise",
                            entityType = "BudgetAllocation",
                            entityId = period.id.toString(),
                            severity = IntegritySeverity.CRITICAL
                        )
                    )
                }
                totalAdjustedBudgetPeriod += adjustedBudget
            }

            // B3 Bucket Over-allocation check
            val targetNeeds = (period.incomePaise * 50L) / 100L
            val targetWants = (period.incomePaise * 30L) / 100L
            val targetSavings = (period.incomePaise * 20L) / 100L

            val sumNeeds = periodAllocs.filter { it.bucketSnapshot == BudgetBucket.NEEDS }.sumOf { it.amountPaise }
            val sumWants = periodAllocs.filter { it.bucketSnapshot == BudgetBucket.WANTS }.sumOf { it.amountPaise }
            val sumSavings = periodAllocs.filter { it.bucketSnapshot == BudgetBucket.SAVINGS }.sumOf { it.amountPaise }

            if (sumNeeds > targetNeeds || sumWants > targetWants || sumSavings > targetSavings) {
                issues.add(
                    IntegrityIssue(
                        ruleId = "B3_BUCKET_OVERALLOCATION",
                        message = "Period #${period.id} base allocations exceed 50/30/20 bucket targets (Needs: $sumNeeds > $targetNeeds, Wants: $sumWants > $targetWants, Savings: $sumSavings > $targetSavings)",
                        entityType = "BudgetPeriod",
                        entityId = period.id.toString(),
                        severity = IntegritySeverity.WARNING
                    )
                )
            }

            // B4 Budget Equilibrium check
            val advPosTotal = periodAdvs.filter { it.direction == AdvanceDirection.POSITIVE }.sumOf { it.amountPaise }
            val advNegTotal = periodAdvs.filter { it.direction == AdvanceDirection.NEGATIVE }.sumOf { it.amountPaise }
            val netAdvances = advPosTotal - advNegTotal
            val allowableTotal = period.incomePaise + netAdvances

            if (totalAdjustedBudgetPeriod > allowableTotal) {
                issues.add(
                    IntegrityIssue(
                        ruleId = "B4_BUDGET_EQUILIBRIUM",
                        message = "Period #${period.id} total adjusted budget ($totalAdjustedBudgetPeriod) exceeds income + net advances ($allowableTotal)",
                        entityType = "BudgetPeriod",
                        entityId = period.id.toString(),
                        severity = IntegritySeverity.WARNING
                    )
                )
            }
        }

        // =============================================================
        // 4. Category Rules (C1)
        // =============================================================

        // Rule C1_DUPLICATE_CATEGORY (WARNING): Unique active category names
        val activeCategoriesByName = categories.filter { it.isActive }.groupBy { it.name.trim().lowercase(Locale.ENGLISH) }
        for ((name, duplicateCats) in activeCategoriesByName) {
            if (duplicateCats.size > 1) {
                issues.add(
                    IntegrityIssue(
                        ruleId = "C1_DUPLICATE_CATEGORY",
                        message = "Multiple active categories share name '$name': ${duplicateCats.map { it.id }}",
                        entityType = "Category",
                        entityId = duplicateCats.first().id.toString(),
                        severity = IntegritySeverity.WARNING
                    )
                )
            }
        }

        // =============================================================
        // 5. Period Boundary Rules (P1)
        // =============================================================

        // Rule P1_PERIOD_OVERLAP (CRITICAL): No overlapping [start_at, end_at) intervals
        val sortedPeriods = periods.sortedBy { it.startAt }
        for (i in 0 until sortedPeriods.size - 1) {
            val curr = sortedPeriods[i]
            val next = sortedPeriods[i + 1]
            if (curr.endAt > next.startAt) {
                issues.add(
                    IntegrityIssue(
                        ruleId = "P1_PERIOD_OVERLAP",
                        message = "Budget period #${curr.id} [${curr.startAt}, ${curr.endAt}) overlaps with period #${next.id} [${next.startAt}, ${next.endAt})",
                        entityType = "BudgetPeriod",
                        entityId = curr.id.toString(),
                        severity = IntegritySeverity.CRITICAL
                    )
                )
            }
        }

        // =============================================================
        // 6. Immutability & Month-Close Rules (M1, M2)
        // =============================================================

        // Rule M1_LOCKED_MODIFICATION (CRITICAL):
        // No row mutations targeting closed periods; enforced via SQLite BEFORE triggers
        for (period in periods) {
            if (period.status == BudgetPeriodStatus.CLOSED) {
                val periodClosedAt = period.closedAt
                val txsInPeriod = transactions.filter { it.occurredAt >= period.startAt && it.occurredAt < period.endAt }

                for (tx in txsInPeriod) {
                    if (tx.status == TransactionStatus.PENDING) {
                        issues.add(
                            IntegrityIssue(
                                ruleId = "M1_LOCKED_MODIFICATION",
                                message = "Closed period #${period.id} contains PENDING transaction #${tx.id}",
                                entityType = "Transaction",
                                entityId = tx.id.toString(),
                                severity = IntegritySeverity.CRITICAL
                            )
                        )
                    }
                    if (periodClosedAt != null && tx.createdAt > periodClosedAt) {
                        issues.add(
                            IntegrityIssue(
                                ruleId = "M1_LOCKED_MODIFICATION",
                                message = "Closed period #${period.id} contains transaction #${tx.id} created after close timestamp",
                                entityType = "Transaction",
                                entityId = tx.id.toString(),
                                severity = IntegritySeverity.CRITICAL
                            )
                        )
                    }
                }
            }
        }

        // Rule M2_CLOSE_TOTALS_MISMATCH (ERROR):
        // month_closes matches recomputed historical totals
        for (mc in monthCloses) {
            val period = periods.find { it.id == mc.budgetPeriodId }
            if (period == null) {
                issues.add(
                    IntegrityIssue(
                        ruleId = "M2_CLOSE_TOTALS_MISMATCH",
                        message = "MonthClose record #${mc.id} references non-existent period #${mc.budgetPeriodId}",
                        entityType = "MonthClose",
                        entityId = mc.id.toString(),
                        severity = IntegritySeverity.ERROR
                    )
                )
            } else {
                if (mc.plannedIncomePaise != period.incomePaise) {
                    issues.add(
                        IntegrityIssue(
                            ruleId = "M2_CLOSE_TOTALS_MISMATCH",
                            message = "MonthClose #${mc.id} planned income (${mc.plannedIncomePaise}) does not match period income (${period.incomePaise})",
                            entityType = "MonthClose",
                            entityId = mc.id.toString(),
                            severity = IntegritySeverity.ERROR
                        )
                    )
                }

                val periodAllocs = allocations.filter { it.budgetPeriodId == period.id }
                val periodAdjs = adjustments.filter { it.budgetPeriodId == period.id }
                val periodAdvs = advances.filter { it.appliedToPeriodId == period.id }
                val periodTxs = transactions.filter { it.occurredAt >= period.startAt && it.occurredAt < period.endAt }
                val periodTxAllocs = transactionAllocations.filter { alloc ->
                    periodTxs.any { it.id == alloc.transactionId }
                }

                if (periodAllocs.isNotEmpty() || periodTxs.isNotEmpty()) {
                    val summary = BudgetCalculator.calculateBudgetPeriodSummary(
                        period = period,
                        allocations = periodAllocs,
                        adjustments = periodAdjs,
                        transactions = periodTxs,
                        allocationsForTransactions = periodTxAllocs,
                        advances = periodAdvs
                    )

                    val spentMismatch = mc.totalSpentPaise != summary.dashboardHeroSpendPaise
                    val savedMismatch = mc.totalSavedPaise != summary.actualSavingsPaise
                    val budgetMismatch = mc.totalBudgetedPaise != summary.totalBudgetedPaise

                    if (spentMismatch || savedMismatch || budgetMismatch) {
                        issues.add(
                            IntegrityIssue(
                                ruleId = "M2_CLOSE_TOTALS_MISMATCH",
                                message = "MonthClose #${mc.id} totals mismatch recomputed values (Spent: ${mc.totalSpentPaise} vs ${summary.dashboardHeroSpendPaise}, Saved: ${mc.totalSavedPaise} vs ${summary.actualSavingsPaise}, Budget: ${mc.totalBudgetedPaise} vs ${summary.totalBudgetedPaise})",
                                entityType = "MonthClose",
                                entityId = mc.id.toString(),
                                severity = IntegritySeverity.ERROR
                            )
                        )
                    }
                }
            }
        }

        // =============================================================
        // 7. Refund Rules (R1, R2)
        // =============================================================

        // Rule R1_CUMULATIVE_REFUND_EXCEEDED (ERROR): Σ Refunds(T) ≤ T.amount_paise
        // Rule R2_REFUND_EXCEEDS_CATEGORY_CAP (ERROR): ∀ refund splits i, Refund_i ≤ cap_i
        val refundsByOriginal = transactions.filter { it.refundOfTransactionId != null }.groupBy { it.refundOfTransactionId!! }
        for ((origTxId, refunds) in refundsByOriginal) {
            val origTx = txById[origTxId]
            if (origTx == null) {
                issues.add(
                    IntegrityIssue(
                        ruleId = "R1_CUMULATIVE_REFUND_EXCEEDED",
                        message = "Refunds reference non-existent original transaction #$origTxId",
                        entityType = "Transaction",
                        entityId = origTxId.toString(),
                        severity = IntegritySeverity.ERROR
                    )
                )
            } else {
                val totalRefundPaise = refunds.sumOf { it.amountPaise }
                if (totalRefundPaise > origTx.amountPaise) {
                    issues.add(
                        IntegrityIssue(
                            ruleId = "R1_CUMULATIVE_REFUND_EXCEEDED",
                            message = "Cumulative refunds ($totalRefundPaise paise) exceed original transaction #${origTx.id} amount (${origTx.amountPaise} paise)",
                            entityType = "Transaction",
                            entityId = origTx.id.toString(),
                            severity = IntegritySeverity.ERROR
                        )
                    )
                }

                // Check R2 category cap
                val origAllocs = allocsByTxId[origTx.id].orEmpty()
                val refundTxIds = refunds.map { it.id }.toSet()
                val refundAllocs = transactionAllocations.filter { refundTxIds.contains(it.transactionId) }
                val refundedByCat = refundAllocs.groupBy { it.categoryId }.mapValues { it.value.sumOf { a -> a.amountPaise } }

                for (origAlloc in origAllocs) {
                    val refundedToCat = refundedByCat[origAlloc.categoryId] ?: 0L
                    if (refundedToCat > origAlloc.amountPaise) {
                        issues.add(
                            IntegrityIssue(
                                ruleId = "R2_REFUND_EXCEEDS_CATEGORY_CAP",
                                message = "Refund allocated to category #${origAlloc.categoryId} ($refundedToCat) exceeds original spend capacity (${origAlloc.amountPaise})",
                                entityType = "TransactionAllocation",
                                entityId = origAlloc.id.toString(),
                                severity = IntegritySeverity.ERROR
                            )
                        )
                    }
                }
            }
        }

        // =============================================================
        // 11. Savings Goals & Contributions Rules (Flow.md §18, D011)
        // =============================================================
        val goalIds = savingsGoals.map { it.id }.toSet()
        val contribsByGoal = savingsContributions.groupBy { it.goalId }

        for (goal in savingsGoals) {
            if (goal.targetPaise <= 0L) {
                issues.add(
                    IntegrityIssue(
                        ruleId = "SG1_INVALID_GOAL_TARGET",
                        message = "Goal #${goal.id} '${goal.name}' has non-positive target: ${goal.targetPaise} paise",
                        entityType = "SavingsGoal",
                        entityId = goal.id.toString(),
                        severity = IntegritySeverity.ERROR
                    )
                )
            }

            val goalContribs = contribsByGoal[goal.id].orEmpty()
            var balance = 0L
            for (c in goalContribs) {
                balance = Math.addExact(balance, c.amountPaise)
            }
            if (balance < 0L) {
                issues.add(
                    IntegrityIssue(
                        ruleId = "SG2_GOAL_NEGATIVE_BALANCE",
                        message = "Goal #${goal.id} '${goal.name}' has negative balance: $balance paise",
                        entityType = "SavingsGoal",
                        entityId = goal.id.toString(),
                        severity = IntegritySeverity.CRITICAL
                    )
                )
            }
        }

        for (contrib in savingsContributions) {
            if (!goalIds.contains(contrib.goalId)) {
                issues.add(
                    IntegrityIssue(
                        ruleId = "SG3_ORPHANED_SAVINGS_CONTRIBUTION",
                        message = "Savings contribution #${contrib.id} references non-existent goal #${contrib.goalId}",
                        entityType = "SavingsContribution",
                        entityId = contrib.id.toString(),
                        severity = IntegritySeverity.ERROR
                    )
                )
            }
        }

        // =============================================================
        // 12. Savings Transfer Plan Rules (Flow.md §18, D013, D014, Milestone M20)
        // =============================================================
        for (plan in savingsTransferPlans) {
            // Rule SP1_TRANSFER_LEGS_MISMATCH (CRITICAL):
            // A marked or verified plan's transfer legs must match moved_paise
            if (plan.status == com.tapledger.core.model.TransferPlanStatus.MARKED_MOVED ||
                plan.status == com.tapledger.core.model.TransferPlanStatus.VERIFIED
            ) {
                val movedPaise = plan.movedPaise
                if (movedPaise == null) {
                    issues.add(
                        IntegrityIssue(
                            ruleId = "SP1_TRANSFER_LEGS_MISMATCH",
                            message = "Savings transfer plan #${plan.id} in status ${plan.status} has no movedPaise",
                            entityType = "SavingsTransferPlan",
                            entityId = plan.id.toString(),
                            severity = IntegritySeverity.CRITICAL
                        )
                    )
                } else if (plan.transferGroupId == null) {
                    issues.add(
                        IntegrityIssue(
                            ruleId = "SP1_TRANSFER_LEGS_MISMATCH",
                            message = "Savings transfer plan #${plan.id} has movedPaise ($movedPaise) but no transferGroupId",
                            entityType = "SavingsTransferPlan",
                            entityId = plan.id.toString(),
                            severity = IntegritySeverity.CRITICAL
                        )
                    )
                } else {
                    val legs = transactions.filter { it.transferGroupId == plan.transferGroupId }
                    if (legs.isEmpty()) {
                        issues.add(
                            IntegrityIssue(
                                ruleId = "SP1_TRANSFER_LEGS_MISMATCH",
                                message = "Savings transfer plan #${plan.id} transfer legs not found for transferGroupId ${plan.transferGroupId}",
                                entityType = "SavingsTransferPlan",
                                entityId = plan.id.toString(),
                                severity = IntegritySeverity.CRITICAL
                            )
                        )
                    } else {
                        for (leg in legs) {
                            if (leg.amountPaise != movedPaise) {
                                issues.add(
                                    IntegrityIssue(
                                        ruleId = "SP1_TRANSFER_LEGS_MISMATCH",
                                        message = "Savings transfer plan #${plan.id} transfer leg #${leg.id} amount (${leg.amountPaise} paise) does not match movedPaise ($movedPaise paise)",
                                        entityType = "Transaction",
                                        entityId = leg.id.toString(),
                                        severity = IntegritySeverity.CRITICAL
                                    )
                                )
                            }
                        }
                    }
                }
            }

            // Rule SP2_INVALID_SAVINGS_TRANSFER_ACCOUNTS (ERROR):
            // source != destination, and destination must be a valid bank/savings account
            if (plan.sourceAccountId == plan.destAccountId) {
                issues.add(
                    IntegrityIssue(
                        ruleId = "SP2_INVALID_SAVINGS_TRANSFER_ACCOUNTS",
                        message = "Savings transfer plan #${plan.id} has identical source and destination account #${plan.sourceAccountId}",
                        entityType = "SavingsTransferPlan",
                        entityId = plan.id.toString(),
                        severity = IntegritySeverity.ERROR
                    )
                )
            }
            val destAccount = accounts.find { it.id == plan.destAccountId }
            if (destAccount == null) {
                issues.add(
                    IntegrityIssue(
                        ruleId = "SP2_INVALID_SAVINGS_TRANSFER_ACCOUNTS",
                        message = "Savings transfer plan #${plan.id} destination account #${plan.destAccountId} does not exist",
                        entityType = "SavingsTransferPlan",
                        entityId = plan.id.toString(),
                        severity = IntegritySeverity.ERROR
                    )
                )
            } else if (destAccount.type == AccountType.CREDIT_CARD || destAccount.type == AccountType.LOAN) {
                issues.add(
                    IntegrityIssue(
                        ruleId = "SP2_INVALID_SAVINGS_TRANSFER_ACCOUNTS",
                        message = "Savings transfer plan #${plan.id} destination account #${plan.destAccountId} is invalid type (${destAccount.type})",
                        entityType = "SavingsTransferPlan",
                        entityId = plan.id.toString(),
                        severity = IntegritySeverity.ERROR
                    )
                )
            }

            // Rule SP3_UNVERIFIED_PLAN_STALE (WARNING):
            // Plan unverified for more than 7 days
            if (plan.status == com.tapledger.core.model.TransferPlanStatus.MARKED_MOVED) {
                val timestamp = plan.movedAt ?: plan.createdAt
                if (nowEpochMs - timestamp > 7L * 86_400_000L) {
                    issues.add(
                        IntegrityIssue(
                            ruleId = "SP3_UNVERIFIED_PLAN_STALE",
                            message = "Savings transfer plan #${plan.id} marked moved over 7 days ago without bank verification",
                            entityType = "SavingsTransferPlan",
                            entityId = plan.id.toString(),
                            severity = IntegritySeverity.WARNING
                        )
                    )
                }
            }
        }

        return issues
    }
}

