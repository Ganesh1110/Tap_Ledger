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

            when (tx.direction) {
                com.tapledger.core.model.TransactionDirection.IN -> totalIncomingPaise += tx.amountPaise
                com.tapledger.core.model.TransactionDirection.OUT -> totalOutgoingPaise += tx.amountPaise
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
     * Computes Net Worth across all active accounts (Flow.md §6.2):
     * Assets      = Σ max( Balance(A), 0)
     * Liabilities = Σ max(−Balance(A), 0)      // type-agnostic
     * NetWorth    = Assets − Liabilities = Σ Balance(A)
     */
    fun calculateNetWorth(summaries: List<AccountBalanceSummary>): NetWorthSummary {
        var totalAssets = 0L
        var totalLiabilities = 0L

        for (summary in summaries) {
            if (!summary.account.isActive) continue
            val balance = summary.currentBalancePaise
            if (balance >= 0) {
                totalAssets += balance
            } else {
                totalLiabilities += -balance
            }
        }

        return NetWorthSummary(
            totalAssetsPaise = totalAssets,
            totalLiabilitiesPaise = totalLiabilities,
            netWorthPaise = totalAssets - totalLiabilities
        )
    }
}
