package com.tapledger.domain.calculator

import com.google.common.truth.Truth.assertThat
import com.tapledger.core.database.entity.BudgetAdjustmentEntity
import com.tapledger.core.database.entity.BudgetAllocationEntity
import com.tapledger.core.database.entity.BudgetPeriodEntity
import com.tapledger.core.database.entity.TransactionAllocationEntity
import com.tapledger.core.database.entity.TransactionEntity
import com.tapledger.core.model.AdvancePolicy
import com.tapledger.core.model.BudgetBucket
import com.tapledger.core.model.BudgetPeriodStatus
import com.tapledger.core.model.TransactionKind
import com.tapledger.core.model.TransactionSource
import com.tapledger.core.model.TransactionStatus
import org.junit.Test

class BudgetCalculatorTest {

    private val period = BudgetPeriodEntity(
        id = 1,
        year = 2026,
        month = 10,
        startAt = 1000L,
        endAt = 2000L,
        incomePaise = 52_000_00L, // ₹52,000
        status = BudgetPeriodStatus.OPEN,
        createdAt = 1000L,
        updatedAt = 1000L
    )

    private val foodAllocation = BudgetAllocationEntity(
        id = 11,
        budgetPeriodId = 1,
        categoryId = 10,
        categoryNameSnapshot = "Food",
        bucketSnapshot = BudgetBucket.NEEDS,
        sortOrderSnapshot = 1,
        advancePolicySnapshot = AdvancePolicy.CARRY_TO_ADVANCE,
        amountPaise = 5_000_00L, // ₹5,000
        createdAt = 1000L,
        updatedAt = 1000L
    )

    private val entertainmentAllocation = BudgetAllocationEntity(
        id = 12,
        budgetPeriodId = 1,
        categoryId = 20,
        categoryNameSnapshot = "Entertainment",
        bucketSnapshot = BudgetBucket.WANTS,
        sortOrderSnapshot = 2,
        advancePolicySnapshot = AdvancePolicy.CARRY_TO_ADVANCE,
        amountPaise = 2_000_00L, // ₹2,000
        createdAt = 1000L,
        updatedAt = 1000L
    )

    @Test
    fun budgetSummary_tracksSpendingAndRemaining() {
        // Food expense ₹4,100 (82% -> warning threshold as per Section 25)
        val expenseTx = TransactionEntity(
            id = 101,
            accountId = 1,
            kind = TransactionKind.EXPENSE,
            direction = com.tapledger.core.model.TransactionDirection.OUT,
            amountPaise = 4_100_00L,
            occurredAt = 1500L,
            createdAt = 1500L,
            updatedAt = 1500L,
            status = TransactionStatus.CATEGORIZED,
            source = TransactionSource.NOTIFICATION
        )
        val expenseAlloc = TransactionAllocationEntity(
            id = 501,
            transactionId = 101,
            categoryId = 10,
            amountPaise = 4_100_00L
        )

        val summary = BudgetCalculator.calculateBudgetPeriodSummary(
            period = period,
            allocations = listOf(foodAllocation, entertainmentAllocation),
            adjustments = emptyList(),
            transactions = listOf(expenseTx),
            allocationsForTransactions = listOf(expenseAlloc)
        )

        val foodSummary = summary.categorySummaries.first { it.allocation.categoryId == 10L }
        assertThat(foodSummary.expensePaise).isEqualTo(4_100_00L)
        assertThat(foodSummary.netSpentPaise).isEqualTo(4_100_00L)
        assertThat(foodSummary.remainingPaise).isEqualTo(900_00L)
        assertThat(foodSummary.percentageUsed).isEqualTo(82)
        assertThat(foodSummary.isWarning).isTrue()
        assertThat(foodSummary.isExceeded).isFalse()
    }

    @Test
    fun budgetAdjustments_moveMoneyBetweenCategories() {
        // Entertainment -> Food ₹500 (Section 25)
        val adjustment = BudgetAdjustmentEntity(
            id = 1,
            budgetPeriodId = 1,
            fromCategoryId = 20,
            toCategoryId = 10,
            amountPaise = 500_00L,
            reason = "Cover grocery overspend",
            createdAt = 1600L
        )

        val summary = BudgetCalculator.calculateBudgetPeriodSummary(
            period = period,
            allocations = listOf(foodAllocation, entertainmentAllocation),
            adjustments = listOf(adjustment),
            transactions = emptyList(),
            allocationsForTransactions = emptyList()
        )

        val foodSummary = summary.categorySummaries.first { it.allocation.categoryId == 10L }
        val entSummary = summary.categorySummaries.first { it.allocation.categoryId == 20L }

        // Food was ₹5,000 + ₹500 = ₹5,500
        assertThat(foodSummary.adjustedBudgetPaise).isEqualTo(5_500_00L)
        // Entertainment was ₹2,000 - ₹500 = ₹1,500
        assertThat(entSummary.adjustedBudgetPaise).isEqualTo(1_500_00L)
    }

    @Test
    fun refund_reducesNetSpending() {
        val expenseTx = TransactionEntity(
            id = 101,
            accountId = 1,
            kind = TransactionKind.EXPENSE,
            direction = com.tapledger.core.model.TransactionDirection.OUT,
            amountPaise = 2_000_00L,
            occurredAt = 1200L,
            createdAt = 1200L,
            updatedAt = 1200L,
            status = TransactionStatus.CATEGORIZED,
            source = TransactionSource.NOTIFICATION
        )
        val expenseAlloc = TransactionAllocationEntity(
            id = 501,
            transactionId = 101,
            categoryId = 10,
            amountPaise = 2_000_00L
        )

        val refundTx = TransactionEntity(
            id = 102,
            accountId = 1,
            kind = TransactionKind.REFUND,
            direction = com.tapledger.core.model.TransactionDirection.IN,
            amountPaise = 500_00L,
            occurredAt = 1400L,
            createdAt = 1400L,
            updatedAt = 1400L,
            status = TransactionStatus.CATEGORIZED,
            source = TransactionSource.NOTIFICATION,
            refundOfTransactionId = 101
        )
        val refundAlloc = TransactionAllocationEntity(
            id = 502,
            transactionId = 102,
            categoryId = 10,
            amountPaise = 500_00L
        )

        val summary = BudgetCalculator.calculateBudgetPeriodSummary(
            period = period,
            allocations = listOf(foodAllocation),
            adjustments = emptyList(),
            transactions = listOf(expenseTx, refundTx),
            allocationsForTransactions = listOf(expenseAlloc, refundAlloc)
        )

        val foodSummary = summary.categorySummaries.first()
        assertThat(foodSummary.expensePaise).isEqualTo(2_000_00L)
        assertThat(foodSummary.refundPaise).isEqualTo(500_00L)
        assertThat(foodSummary.netSpentPaise).isEqualTo(1_500_00L) // 2000 - 500 = 1500
        assertThat(foodSummary.remainingPaise).isEqualTo(3_500_00L) // 5000 - 1500 = 3500
    }

    @Test
    fun budgetSummary_includesSpendForUnbudgetedCategories() {
        // Transaction categorized under category 99, which has NO allocation in this period
        val orphanTx = TransactionEntity(
            id = 201,
            accountId = 1,
            kind = TransactionKind.EXPENSE,
            direction = com.tapledger.core.model.TransactionDirection.OUT,
            amountPaise = 750_00L,
            occurredAt = 1200L,
            createdAt = 1200L,
            updatedAt = 1200L,
            status = TransactionStatus.CATEGORIZED,
            source = TransactionSource.MANUAL
        )
        val orphanAlloc = TransactionAllocationEntity(
            id = 601,
            transactionId = 201,
            categoryId = 99,
            amountPaise = 750_00L
        )

        val summary = BudgetCalculator.calculateBudgetPeriodSummary(
            period = period,
            allocations = listOf(foodAllocation), // category 10 only
            adjustments = emptyList(),
            transactions = listOf(orphanTx),
            allocationsForTransactions = listOf(orphanAlloc)
        )

        // The unbudgeted spend must NOT drop out
        assertThat(summary.totalSpentPaise).isEqualTo(750_00L)
        assertThat(summary.dashboardHeroSpendPaise).isEqualTo(750_00L)
        val orphanSummary = summary.categorySummaries.find { it.allocation.categoryId == 99L }
        assertThat(orphanSummary).isNotNull()
        assertThat(orphanSummary!!.baseBudgetPaise).isEqualTo(0L)
        assertThat(orphanSummary.netSpentPaise).isEqualTo(750_00L)
        assertThat(orphanSummary.isExceeded).isTrue()
    }
}
