package com.tapledger.domain.calculator

import com.tapledger.core.database.entity.AccountEntity
import com.tapledger.core.database.entity.TransactionEntity
import com.tapledger.core.model.AccountType
import com.tapledger.core.model.TransactionKind
import com.tapledger.core.model.TransactionStatus
import com.tapledger.core.model.TransferDirection
import com.tapledger.domain.model.AccountBalanceSummary
import com.tapledger.domain.model.NetWorthSummary

object AccountBalanceCalculator {

    /**
     * Calculates account balance strictly according to Section 14, 16, 23:
     * current_balance = opening_balance + sum(incoming) - sum(outgoing)
     * Includes PENDING and CATEGORIZED transactions.
     * Excludes IGNORED and VOIDED transactions.
     */
    fun calculateAccountBalance(
        account: AccountEntity,
        transactions: List<TransactionEntity>
    ): AccountBalanceSummary {
        var totalIncomingPaise = 0L
        var totalOutgoingPaise = 0L

        for (tx in transactions) {
            // Must belong to this account and occur at or after opening balance date
            if (tx.accountId != account.id) continue
            if (tx.occurredAt < account.openingBalanceDate) continue

            // Exclude IGNORED and VOIDED
            if (tx.status == TransactionStatus.IGNORED || tx.status == TransactionStatus.VOIDED) {
                continue
            }

            when (tx.kind) {
                TransactionKind.INCOME -> {
                    totalIncomingPaise += tx.amountPaise
                }
                TransactionKind.REFUND -> {
                    totalIncomingPaise += tx.amountPaise
                }
                TransactionKind.EXPENSE,
                TransactionKind.FEE,
                TransactionKind.INTEREST -> {
                    totalOutgoingPaise += tx.amountPaise
                }
                TransactionKind.TRANSFER -> {
                    when (tx.transferDirection) {
                        TransferDirection.IN -> totalIncomingPaise += tx.amountPaise
                        TransferDirection.OUT -> totalOutgoingPaise += tx.amountPaise
                        null -> {
                            // Default fallback if direction was not recorded
                            totalOutgoingPaise += tx.amountPaise
                        }
                    }
                }
                TransactionKind.ADJUSTMENT -> {
                    // Adjustments can be positive or negative; if stored as positive magnitude
                    if (tx.amountPaise >= 0) {
                        totalIncomingPaise += tx.amountPaise
                    } else {
                        totalOutgoingPaise += -tx.amountPaise
                    }
                }
            }
        }

        val currentBalance = account.openingBalancePaise + totalIncomingPaise - totalOutgoingPaise

        return AccountBalanceSummary(
            account = account,
            openingBalancePaise = account.openingBalancePaise,
            totalIncomingPaise = totalIncomingPaise,
            totalOutgoingPaise = totalOutgoingPaise,
            currentBalancePaise = currentBalance
        )
    }

    /**
     * Computes Net Worth across all active accounts:
     * Asset accounts (BANK, CASH) contribute to total assets (positive balance).
     * Credit Card accounts contribute to liabilities (negative balance).
     */
    fun calculateNetWorth(summaries: List<AccountBalanceSummary>): NetWorthSummary {
        var totalAssets = 0L
        var totalLiabilities = 0L

        for (summary in summaries) {
            if (!summary.account.isActive) continue
            val balance = summary.currentBalancePaise
            when (summary.account.type) {
                AccountType.BANK,
                AccountType.CASH -> {
                    if (balance >= 0) {
                        totalAssets += balance
                    } else {
                        // In case of bank overdraft
                        totalLiabilities += -balance
                    }
                }
                AccountType.CREDIT_CARD -> {
                    // Credit card liability: balance is negative (e.g. -₹8,000 means ₹8,000 liability)
                    if (balance < 0) {
                        totalLiabilities += -balance
                    } else {
                        // Credit balance on card
                        totalAssets += balance
                    }
                }
            }
        }

        return NetWorthSummary(
            totalAssetsPaise = totalAssets,
            totalLiabilitiesPaise = totalLiabilities,
            netWorthPaise = totalAssets - totalLiabilities
        )
    }
}
