package com.tapledger.domain.calculator

import com.tapledger.core.model.AdvancePolicy
import com.tapledger.core.model.BudgetBucket
import com.tapledger.domain.model.BudgetCategorySummary
import javax.inject.Inject
import javax.inject.Singleton

data class CardDueItem(
    val accountId: Long,
    val cardName: String,
    val balancePaise: Long // negative when dues are owed
) {
    val duePaise: Long get() = maxOf(0L, -balancePaise)
}

data class UnpaidCommitmentItem(
    val commitmentId: Long,
    val name: String,
    val amountPaise: Long,
    val accountId: Long?
)

data class SpareCalculationResult(
    val hdfcBalancePaise: Long,
    val cardDuesPaise: Long,
    val remainingNeedsWantsPaise: Long,
    val commitmentsPaise: Long,
    val bufferPaise: Long,
    val sparePaise: Long,
    val savingsGapPaise: Long,
    val paydaySuggestionPaise: Long,
    val sweepableLeftoverPaise: Long,
    val monthEndSweepPaise: Long,
    val isSafetyGateTriggered: Boolean,
    val safetyGateReason: String? = null
)

/**
 * Pure integer arithmetic calculator for Savings Transfer Guide spare funds and transfer suggestions.
 * Strictly adheres to Zero-Float invariant (Flow.md §1.1, D013, D014, D015).
 */
@Singleton
class SpareCalculator @Inject constructor() {

    /**
     * Computes the safe transfer spare money:
     * CardDues        = Σ max(−Balance(card), 0) for cards paid from HDFC
     * RemainingNW     = Σ over Needs+Wants categories of max(AdjustedBudget − NetSpent, 0)
     * Commitments     = unpaid SIP/RD/Chit due this period from HDFC
     * Spare           = max(0, Balance(HDFC) − RemainingNW − CardDues − Commitments − Buffer)
     * SavingsGap      = max(0, SavingsAdjustedBudget − ActualSavings)
     * PaydaySuggestion = min(SavingsGap, Spare)
     * MonthEndSweep   = min(SweepableLeftover, Spare)
     */
    fun calculateSpare(
        hdfcBalancePaise: Long,
        cardsPaidFromHdfc: List<CardDueItem>,
        categorySummaries: List<BudgetCategorySummary>,
        unpaidCommitmentsFromHdfc: List<UnpaidCommitmentItem>,
        bufferPaise: Long = 0L,
        savingsAdjustedBudgetPaise: Long = 0L,
        actualSavingsPaise: Long = 0L,
        isDriftBannerActive: Boolean = false,
        hasUnassignedPendingTransactions: Boolean = false
    ): SpareCalculationResult {
        // 1. Card dues = Σ max(-Balance(card), 0)
        var totalCardDues = 0L
        for (card in cardsPaidFromHdfc) {
            val due = maxOf(0L, -card.balancePaise)
            totalCardDues = Math.addExact(totalCardDues, due)
        }

        // 2. RemainingNW = Σ over Needs+Wants categories of max(AdjustedBudget - NetSpent, 0)
        var totalRemainingNW = 0L
        var totalSweepableLeftover = 0L

        for (cat in categorySummaries) {
            val leftover = maxOf(0L, cat.adjustedBudgetPaise - cat.netSpentPaise)
            val bucket = cat.allocation.bucketSnapshot
            val policy = cat.allocation.advancePolicySnapshot

            if (bucket == BudgetBucket.NEEDS || bucket == BudgetBucket.WANTS) {
                totalRemainingNW = Math.addExact(totalRemainingNW, leftover)

                // Sweepable leftover: only categories whose policy is EXPIRE or SWEEP_TO_SAVINGS
                // Categories with CARRY_TO_ADVANCE stay reserved for next month's advance pool
                if (policy == AdvancePolicy.EXPIRE || policy == AdvancePolicy.SWEEP_TO_SAVINGS) {
                    totalSweepableLeftover = Math.addExact(totalSweepableLeftover, leftover)
                }
            }
        }

        // 3. Commitments = unpaid SIP/RD/Chit due this period from HDFC
        var totalCommitments = 0L
        for (commitment in unpaidCommitmentsFromHdfc) {
            totalCommitments = Math.addExact(totalCommitments, commitment.amountPaise)
        }

        // 4. Spare = max(0, Balance(HDFC) - RemainingNW - CardDues - Commitments - Buffer)
        val nonNegativeBuffer = maxOf(0L, bufferPaise)
        val obligations = Math.addExact(
            totalRemainingNW,
            Math.addExact(totalCardDues, Math.addExact(totalCommitments, nonNegativeBuffer))
        )
        val rawSpare = hdfcBalancePaise - obligations
        val spare = maxOf(0L, rawSpare)

        // 5. SavingsGap = max(0, SavingsAdjustedBudget - ActualSavings)
        val savingsGap = maxOf(0L, savingsAdjustedBudgetPaise - actualSavingsPaise)

        // Safety gate checks
        val isSafetyGateTriggered = isDriftBannerActive || hasUnassignedPendingTransactions
        val safetyGateReason = if (isSafetyGateTriggered) {
            "Balance may be out of date, Correct Balance first"
        } else null

        val paydaySuggestion = if (isSafetyGateTriggered) {
            0L
        } else {
            minOf(savingsGap, spare)
        }

        val monthEndSweep = if (isSafetyGateTriggered) {
            0L
        } else {
            minOf(totalSweepableLeftover, spare)
        }

        return SpareCalculationResult(
            hdfcBalancePaise = hdfcBalancePaise,
            cardDuesPaise = totalCardDues,
            remainingNeedsWantsPaise = totalRemainingNW,
            commitmentsPaise = totalCommitments,
            bufferPaise = nonNegativeBuffer,
            sparePaise = spare,
            savingsGapPaise = savingsGap,
            paydaySuggestionPaise = paydaySuggestion,
            sweepableLeftoverPaise = totalSweepableLeftover,
            monthEndSweepPaise = monthEndSweep,
            isSafetyGateTriggered = isSafetyGateTriggered,
            safetyGateReason = safetyGateReason
        )
    }

    /**
     * Formats integer paise to a rupee decimal string suitable for UPI payment app intents or display.
     * E.g. 1200005L -> "12000.05", 5L -> "0.05", 50000L -> "500.00".
     * Executed strictly via 64-bit integer division and modulo (Zero Float).
     */
    fun formatUpiAmount(paise: Long): String {
        require(paise >= 0L) { "Amount cannot be negative" }
        val rupees = paise / 100L
        val remainder = paise % 100L
        val remainderStr = if (remainder < 10L) "0$remainder" else remainder.toString()
        return "$rupees.$remainderStr"
    }
}
