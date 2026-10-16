package com.tapledger.domain.calculator

import com.google.common.truth.Truth.assertThat
import com.tapledger.core.database.entity.BudgetAllocationEntity
import com.tapledger.core.model.AdvancePolicy
import com.tapledger.core.model.BudgetBucket
import com.tapledger.domain.model.BudgetCategorySummary
import org.junit.Test

class SpareCalculatorTest {

    private val calculator = SpareCalculator()

    private fun createCategorySummary(
        categoryId: Long,
        name: String,
        bucket: BudgetBucket,
        advancePolicy: AdvancePolicy = AdvancePolicy.CARRY_TO_ADVANCE,
        adjustedBudgetPaise: Long,
        netSpentPaise: Long
    ): BudgetCategorySummary {
        val allocation = BudgetAllocationEntity(
            id = categoryId,
            budgetPeriodId = 1L,
            categoryId = categoryId,
            categoryNameSnapshot = name,
            bucketSnapshot = bucket,
            sortOrderSnapshot = 1,
            advancePolicySnapshot = advancePolicy,
            amountPaise = adjustedBudgetPaise,
            createdAt = 0L,
            updatedAt = 0L
        )
        val remaining = adjustedBudgetPaise - netSpentPaise
        return BudgetCategorySummary(
            allocation = allocation,
            baseBudgetPaise = adjustedBudgetPaise,
            adjustedBudgetPaise = adjustedBudgetPaise,
            expensePaise = netSpentPaise,
            refundPaise = 0L,
            netSpentPaise = netSpentPaise,
            remainingPaise = remaining,
            percentageUsed = 0,
            isWarning = false,
            isExceeded = remaining < 0L
        )
    }

    @Test
    fun testCardDuesCalculatedCorrectly() {
        val cards = listOf(
            CardDueItem(accountId = 10, cardName = "HDFC Regalia", balancePaise = -8_000_00L), // ₹8,000 due
            CardDueItem(accountId = 11, cardName = "SBI SimplyClick", balancePaise = -2_500_00L), // ₹2,500 due
            CardDueItem(accountId = 12, cardName = "ICICI Amazon", balancePaise = 1_000_00L) // +₹1,000 credit/overpaid
        )

        val result = calculator.calculateSpare(
            hdfcBalancePaise = 50_000_00L,
            cardsPaidFromHdfc = cards,
            categorySummaries = emptyList(),
            unpaidCommitmentsFromHdfc = emptyList()
        )

        // Card dues: 8000 + 2500 + 0 = 10,500
        assertThat(result.cardDuesPaise).isEqualTo(10_500_00L)
    }

    @Test
    fun testRemainingNeedsWantsCalculatedWithZeroFloor() {
        // Envelopes:
        // Needs 1: Adjusted 20,000, Spent 12,000 -> Remaining 8,000
        // Needs 2: Adjusted 10,000, Spent 15,000 -> Remaining -5,000 (overspent, floored to 0!)
        // Wants 1: Adjusted 10,000, Spent 6,000  -> Remaining 4,000
        // Savings 1: Adjusted 10,000, Spent 0 -> Should NOT count in RemainingNW!
        val categories = listOf(
            createCategorySummary(1, "Groceries", BudgetBucket.NEEDS, AdvancePolicy.CARRY_TO_ADVANCE, 20_000_00L, 12_000_00L),
            createCategorySummary(2, "Utilities", BudgetBucket.NEEDS, AdvancePolicy.CARRY_TO_ADVANCE, 10_000_00L, 15_000_00L),
            createCategorySummary(3, "Dining", BudgetBucket.WANTS, AdvancePolicy.EXPIRE, 10_000_00L, 6_000_00L),
            createCategorySummary(4, "Emergency Fund", BudgetBucket.SAVINGS, AdvancePolicy.SWEEP_TO_SAVINGS, 10_000_00L, 0L)
        )

        val result = calculator.calculateSpare(
            hdfcBalancePaise = 50_000_00L,
            cardsPaidFromHdfc = emptyList(),
            categorySummaries = categories,
            unpaidCommitmentsFromHdfc = emptyList()
        )

        // RemainingNW: max(8000,0) + max(-5000,0) + max(4000,0) = 8000 + 0 + 4000 = 12,000
        assertThat(result.remainingNeedsWantsPaise).isEqualTo(12_000_00L)
    }

    @Test
    fun testCommitmentsAndBufferDeducted() {
        val commitments = listOf(
            UnpaidCommitmentItem(1, "HDFC Nifty 50 SIP", 5_000_00L, 1L),
            UnpaidCommitmentItem(2, "Parag Parikh Flexi SIP", 2_500_00L, 1L)
        )

        val result = calculator.calculateSpare(
            hdfcBalancePaise = 50_000_00L,
            cardsPaidFromHdfc = emptyList(),
            categorySummaries = emptyList(),
            unpaidCommitmentsFromHdfc = commitments,
            bufferPaise = 3_000_00L
        )

        assertThat(result.commitmentsPaise).isEqualTo(7_500_00L)
        assertThat(result.bufferPaise).isEqualTo(3_000_00L)
        // Spare: 50,000 - 7,500 - 3,000 = 39,500
        assertThat(result.sparePaise).isEqualTo(39_500_00L)
    }

    @Test
    fun testExactSpecificationExample() {
        // From Plan §3:
        // HDFC ₹70,000 − remaining ₹40,000 − card ₹8,000 − SIP ₹5,000 − buffer ₹2,000 = spare ₹15,000.
        // Target gap ₹12,000, so the card says Move ₹12,000.
        val cards = listOf(
            CardDueItem(accountId = 10, cardName = "Credit Card", balancePaise = -8_000_00L)
        )
        val categories = listOf(
            createCategorySummary(1, "Needs", BudgetBucket.NEEDS, AdvancePolicy.CARRY_TO_ADVANCE, 50_000_00L, 25_000_00L), // 25k
            createCategorySummary(2, "Wants", BudgetBucket.WANTS, AdvancePolicy.EXPIRE, 30_000_00L, 15_000_00L) // 15k
            // Total remaining NW = 40,000
        )
        val commitments = listOf(
            UnpaidCommitmentItem(1, "SIP", 5_000_00L, 1L)
        )

        val result = calculator.calculateSpare(
            hdfcBalancePaise = 70_000_00L,
            cardsPaidFromHdfc = cards,
            categorySummaries = categories,
            unpaidCommitmentsFromHdfc = commitments,
            bufferPaise = 2_000_00L,
            savingsAdjustedBudgetPaise = 20_000_00L,
            actualSavingsPaise = 8_000_00L // gap = 12,000
        )

        assertThat(result.sparePaise).isEqualTo(15_000_00L)
        assertThat(result.savingsGapPaise).isEqualTo(12_000_00L)
        assertThat(result.paydaySuggestionPaise).isEqualTo(12_000_00L)
    }

    @Test
    fun testPaydaySuggestionCappedBySpare() {
        // When SavingsGap (15,000) > Spare (10,000), suggestion is capped at Spare (10,000)
        val result = calculator.calculateSpare(
            hdfcBalancePaise = 30_000_00L,
            cardsPaidFromHdfc = emptyList(),
            categorySummaries = listOf(
                createCategorySummary(1, "Needs", BudgetBucket.NEEDS, AdvancePolicy.CARRY_TO_ADVANCE, 20_000_00L, 0L)
            ),
            unpaidCommitmentsFromHdfc = emptyList(),
            bufferPaise = 0L,
            savingsAdjustedBudgetPaise = 20_000_00L,
            actualSavingsPaise = 5_000_00L // gap = 15,000
        )

        assertThat(result.sparePaise).isEqualTo(10_000_00L)
        assertThat(result.savingsGapPaise).isEqualTo(15_000_00L)
        assertThat(result.paydaySuggestionPaise).isEqualTo(10_000_00L)
    }

    @Test
    fun testMonthEndSweepPolicies() {
        // Categories:
        // 1. Groceries: CARRY_TO_ADVANCE, Leftover 5,000 (Reserved! Should NOT sweep)
        // 2. Dining: EXPIRE, Leftover 3,000 (Sweepable!)
        // 3. Entertainment: SWEEP_TO_SAVINGS, Leftover 4,000 (Sweepable!)
        val categories = listOf(
            createCategorySummary(1, "Groceries", BudgetBucket.NEEDS, AdvancePolicy.CARRY_TO_ADVANCE, 10_000_00L, 5_000_00L),
            createCategorySummary(2, "Dining", BudgetBucket.WANTS, AdvancePolicy.EXPIRE, 6_000_00L, 3_000_00L),
            createCategorySummary(3, "Entertainment", BudgetBucket.WANTS, AdvancePolicy.SWEEP_TO_SAVINGS, 5_000_00L, 1_000_00L)
        )

        val result = calculator.calculateSpare(
            hdfcBalancePaise = 20_000_00L,
            cardsPaidFromHdfc = emptyList(),
            categorySummaries = categories,
            unpaidCommitmentsFromHdfc = emptyList(),
            bufferPaise = 0L
        )

        // RemainingNW = 5,000 + 3,000 + 4,000 = 12,000
        // Spare = 20,000 - 12,000 = 8,000
        // Sweepable = 3,000 (EXPIRE) + 4,000 (SWEEP_TO_SAVINGS) = 7,000
        // Month-end sweep = min(7,000, 8,000) = 7,000
        assertThat(result.sweepableLeftoverPaise).isEqualTo(7_000_00L)
        assertThat(result.monthEndSweepPaise).isEqualTo(7_000_00L)
    }

    @Test
    fun testMonthEndSweepBoundedBySpare() {
        // Sweepable leftover 10,000, but Spare is only 4,000 -> Sweep capped at 4,000
        val categories = listOf(
            createCategorySummary(1, "Dining", BudgetBucket.WANTS, AdvancePolicy.EXPIRE, 15_000_00L, 5_000_00L) // 10k leftover
        )

        val result = calculator.calculateSpare(
            hdfcBalancePaise = 14_000_00L,
            cardsPaidFromHdfc = emptyList(),
            categorySummaries = categories,
            unpaidCommitmentsFromHdfc = emptyList(),
            bufferPaise = 0L
        )

        // RemainingNW = 10,000
        // Spare = 14,000 - 10,000 = 4,000
        // Sweepable = 10,000
        // Sweep = min(10,000, 4,000) = 4,000
        assertThat(result.sparePaise).isEqualTo(4_000_00L)
        assertThat(result.sweepableLeftoverPaise).isEqualTo(10_000_00L)
        assertThat(result.monthEndSweepPaise).isEqualTo(4_000_00L)
    }

    @Test
    fun testZeroOrNegativeSpareYieldsZeroSuggestion() {
        val result = calculator.calculateSpare(
            hdfcBalancePaise = 10_000_00L,
            cardsPaidFromHdfc = listOf(CardDueItem(1, "Card", -15_000_00L)),
            categorySummaries = emptyList(),
            unpaidCommitmentsFromHdfc = emptyList(),
            bufferPaise = 2_000_00L,
            savingsAdjustedBudgetPaise = 10_000_00L,
            actualSavingsPaise = 0L
        )

        assertThat(result.sparePaise).isEqualTo(0L)
        assertThat(result.paydaySuggestionPaise).isEqualTo(0L)
        assertThat(result.monthEndSweepPaise).isEqualTo(0L)
    }

    @Test
    fun testSafetyGateSuppression() {
        // Drift banner active
        val driftResult = calculator.calculateSpare(
            hdfcBalancePaise = 50_000_00L,
            cardsPaidFromHdfc = emptyList(),
            categorySummaries = emptyList(),
            unpaidCommitmentsFromHdfc = emptyList(),
            savingsAdjustedBudgetPaise = 20_000_00L,
            actualSavingsPaise = 0L,
            isDriftBannerActive = true
        )

        assertThat(driftResult.isSafetyGateTriggered).isTrue()
        assertThat(driftResult.paydaySuggestionPaise).isEqualTo(0L)
        assertThat(driftResult.safetyGateReason).isEqualTo("Balance may be out of date, Correct Balance first")

        // Unassigned pending transactions
        val pendingResult = calculator.calculateSpare(
            hdfcBalancePaise = 50_000_00L,
            cardsPaidFromHdfc = emptyList(),
            categorySummaries = emptyList(),
            unpaidCommitmentsFromHdfc = emptyList(),
            savingsAdjustedBudgetPaise = 20_000_00L,
            actualSavingsPaise = 0L,
            hasUnassignedPendingTransactions = true
        )

        assertThat(pendingResult.isSafetyGateTriggered).isTrue()
        assertThat(pendingResult.paydaySuggestionPaise).isEqualTo(0L)
        assertThat(pendingResult.safetyGateReason).isEqualTo("Balance may be out of date, Correct Balance first")
    }

    @Test
    fun testUpiAmountFormatting() {
        assertThat(calculator.formatUpiAmount(0L)).isEqualTo("0.00")
        assertThat(calculator.formatUpiAmount(5L)).isEqualTo("0.05")
        assertThat(calculator.formatUpiAmount(50L)).isEqualTo("0.50")
        assertThat(calculator.formatUpiAmount(100L)).isEqualTo("1.00")
        assertThat(calculator.formatUpiAmount(1200005L)).isEqualTo("12000.05")
        assertThat(calculator.formatUpiAmount(50000_00L)).isEqualTo("50000.00")
        assertThat(calculator.formatUpiAmount(1000000_00L)).isEqualTo("1000000.00")
    }
}
