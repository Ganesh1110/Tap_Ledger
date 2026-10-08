package com.tapledger.domain.validator

import com.tapledger.core.database.entity.AccountEntity
import com.tapledger.core.database.entity.BudgetAllocationEntity
import com.tapledger.core.database.entity.BudgetPeriodEntity
import com.tapledger.core.database.entity.CategoryEntity
import com.tapledger.core.database.entity.MonthCloseEntity
import com.tapledger.core.database.entity.TransactionAllocationEntity
import com.tapledger.core.database.entity.TransactionEntity
import com.tapledger.core.model.BudgetPeriodStatus
import com.tapledger.core.model.TransactionKind
import com.tapledger.core.model.TransactionStatus
import com.tapledger.core.model.TransferDirection
import com.tapledger.domain.model.IntegrityIssue
import com.tapledger.domain.model.IntegritySeverity

class DataIntegrityChecker {

    /**
     * Executes all integrity checks across database records.
     * Returns a list of actionable issues found.
     */
    fun verifyIntegrity(
        accounts: List<AccountEntity>,
        categories: List<CategoryEntity>,
        periods: List<BudgetPeriodEntity>,
        allocations: List<BudgetAllocationEntity>,
        transactions: List<TransactionEntity>,
        transactionAllocations: List<TransactionAllocationEntity>,
        monthCloses: List<MonthCloseEntity>
    ): List<IntegrityIssue> {
        val issues = mutableListOf<IntegrityIssue>()

        val accountIds = accounts.map { it.id }.toSet()
        val categoryIds = categories.map { it.id }.toSet()
        val periodIds = periods.map { it.id }.toSet()
        val txById = transactions.associateBy { it.id }

        // 1. Transaction checks
        for (tx in transactions) {
            // Rule T1: Invalid account reference
            if (!accountIds.contains(tx.accountId)) {
                issues.add(
                    IntegrityIssue(
                        ruleId = "T1_INVALID_ACCOUNT",
                        message = "Transaction #${tx.id} references non-existent account #${tx.accountId}",
                        entityType = "Transaction",
                        entityId = tx.id,
                        severity = IntegritySeverity.CRITICAL
                    )
                )
            }

            // Rule T2: Non-positive transaction amount
            if (tx.amountPaise <= 0L) {
                issues.add(
                    IntegrityIssue(
                        ruleId = "T2_INVALID_AMOUNT",
                        message = "Transaction #${tx.id} has non-positive amount: ${tx.amountPaise} paise",
                        entityType = "Transaction",
                        entityId = tx.id,
                        severity = IntegritySeverity.ERROR
                    )
                )
            }
        }

        // 2. Allocation sum rule for Categorized transactions (Section 12)
        val allocationsByTxId = transactionAllocations.groupBy { it.transactionId }
        for (tx in transactions) {
            if (tx.status == TransactionStatus.CATEGORIZED &&
                (tx.kind == TransactionKind.EXPENSE || tx.kind == TransactionKind.REFUND)
            ) {
                val txAllocs = allocationsByTxId[tx.id].orEmpty()
                val sumAllocs = txAllocs.sumOf { it.amountPaise }
                if (sumAllocs != tx.amountPaise) {
                    issues.add(
                        IntegrityIssue(
                            ruleId = "A1_SPLIT_SUM_MISMATCH",
                            message = "Categorized transaction #${tx.id} amount (${tx.amountPaise} paise) does not match allocations sum ($sumAllocs paise)",
                            entityType = "Transaction",
                            entityId = tx.id,
                            severity = IntegritySeverity.CRITICAL
                        )
                    )
                }

                // Check category references in allocations
                for (alloc in txAllocs) {
                    if (!categoryIds.contains(alloc.categoryId)) {
                        issues.add(
                            IntegrityIssue(
                                ruleId = "A2_INVALID_CATEGORY_REF",
                                message = "Allocation #${alloc.id} references non-existent category #${alloc.categoryId}",
                                entityType = "TransactionAllocation",
                                entityId = alloc.id,
                                severity = IntegritySeverity.CRITICAL
                            )
                        )
                    }
                }
            }
        }

        // 3. Orphaned Allocations
        for (alloc in transactionAllocations) {
            if (!txById.containsKey(alloc.transactionId)) {
                issues.add(
                    IntegrityIssue(
                        ruleId = "A3_ORPHANED_ALLOCATION",
                        message = "Allocation #${alloc.id} references non-existent transaction #${alloc.transactionId}",
                        entityType = "TransactionAllocation",
                        entityId = alloc.id,
                        severity = IntegritySeverity.CRITICAL
                    )
                )
            }
        }

        // 4. Transfer Pair Consistency (Section 16)
        val transferGroups = transactions.filter { it.transferGroupId != null }.groupBy { it.transferGroupId!! }
        for ((groupId, legs) in transferGroups) {
            if (legs.size != 2) {
                issues.add(
                    IntegrityIssue(
                        ruleId = "TR1_UNBALANCED_TRANSFER",
                        message = "Transfer group #$groupId has ${legs.size} legs instead of exactly 2",
                        entityType = "TransferGroup",
                        entityId = groupId,
                        severity = IntegritySeverity.ERROR
                    )
                )
            } else {
                val leg1 = legs[0]
                val leg2 = legs[1]
                if (leg1.amountPaise != leg2.amountPaise) {
                    issues.add(
                        IntegrityIssue(
                            ruleId = "TR2_AMOUNT_MISMATCH",
                            message = "Transfer group #$groupId has mismatched leg amounts: ${leg1.amountPaise} vs ${leg2.amountPaise}",
                            entityType = "TransferGroup",
                            entityId = groupId,
                            severity = IntegritySeverity.ERROR
                        )
                    )
                }
                if (leg1.transferDirection == leg2.transferDirection ||
                    leg1.transferDirection == null || leg2.transferDirection == null
                ) {
                    issues.add(
                        IntegrityIssue(
                            ruleId = "TR3_INVALID_DIRECTIONS",
                            message = "Transfer group #$groupId legs must have opposing directions (OUT and IN)",
                            entityType = "TransferGroup",
                            entityId = groupId,
                            severity = IntegritySeverity.ERROR
                        )
                    )
                }
                if (leg1.accountId == leg2.accountId) {
                    issues.add(
                        IntegrityIssue(
                            ruleId = "TR4_IDENTICAL_ACCOUNTS",
                            message = "Transfer group #$groupId legs are on the exact same account #${leg1.accountId}",
                            entityType = "TransferGroup",
                            entityId = groupId,
                            severity = IntegritySeverity.WARNING
                        )
                    )
                }
            }
        }

        // 5. Refund reference validity (Section 18)
        for (tx in transactions) {
            if (tx.refundOfTransactionId != null) {
                val originalTx = txById[tx.refundOfTransactionId]
                if (originalTx == null) {
                    issues.add(
                        IntegrityIssue(
                            ruleId = "R1_MISSING_ORIGINAL_TX",
                            message = "Refund #${tx.id} references non-existent original transaction #${tx.refundOfTransactionId}",
                            entityType = "Transaction",
                            entityId = tx.id,
                            severity = IntegritySeverity.ERROR
                        )
                    )
                } else if (tx.amountPaise > originalTx.amountPaise) {
                    issues.add(
                        IntegrityIssue(
                            ruleId = "R2_REFUND_EXCEEDS_ORIGINAL",
                            message = "Refund #${tx.id} amount (${tx.amountPaise}) exceeds original transaction amount (${originalTx.amountPaise})",
                            entityType = "Transaction",
                            entityId = tx.id,
                            severity = IntegritySeverity.WARNING
                        )
                    )
                }
            }
        }

        // 6. Closed-Month Integrity (Section 57, 58)
        val closedPeriodIds = monthCloses.map { it.budgetPeriodId }.toSet()
        for (period in periods) {
            if (period.status == BudgetPeriodStatus.CLOSED && !closedPeriodIds.contains(period.id)) {
                issues.add(
                    IntegrityIssue(
                        ruleId = "MC1_MISSING_CLOSE_RECORD",
                        message = "Budget period #${period.id} (${period.year}-${period.month}) is CLOSED but lacks a MonthClose record",
                        entityType = "BudgetPeriod",
                        entityId = period.id,
                        severity = IntegritySeverity.ERROR
                    )
                )
            }
        }

        return issues
    }
}
