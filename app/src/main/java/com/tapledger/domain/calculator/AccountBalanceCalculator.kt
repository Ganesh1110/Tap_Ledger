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
     * Calculates account balance strictly according to Flow.md §4.2, §6.1:
     * Balance(A, T_asOf) = opening_balance_paise + Σ(IN) - Σ(OUT)
     * Eligibility rules:
     * 1. tx.occurred_at >= StartOfDayIST(account.opening_balance_date)
     * 2. tx.occurred_at <= asOfEpochMillis
     * 3. tx.status in (PENDING, CONFIRMED, CATEGORIZED)
     * 4. tx.account_id == account.id
     */
    fun calculateAccountBalance(
        account: AccountEntity,
        transactions: List<TransactionEntity>,
        asOfEpochMillis: Long = Long.MAX_VALUE
    ): AccountBalanceSummary {
        val openingStartOfDay = com.tapledger.core.util.DateTimeUtils.getStartOfDayEpochMillis(account.openingBalanceDate)
        var totalIncomingPaise = 0L
        var totalOutgoingPaise = 0L

        for (tx in transactions) {
            // Must belong to this account and occur at or after opening balance date (StartOfDayIST)
            if (tx.accountId != account.id) continue
            if (tx.occurredAt < openingStartOfDay) continue
            // As-Of boundary: caps future transactions from asOf timestamp
            if (tx.occurredAt > asOfEpochMillis) continue

            // Include PENDING, CONFIRMED, CATEGORIZED; Exclude IGNORED and VOIDED
            when (tx.status) {
                TransactionStatus.PENDING,
                TransactionStatus.CONFIRMED,
                TransactionStatus.CATEGORIZED -> {
                    when (tx.direction) {
                        com.tapledger.core.model.TransactionDirection.IN -> totalIncomingPaise += tx.amountPaise
                        com.tapledger.core.model.TransactionDirection.OUT -> totalOutgoingPaise += tx.amountPaise
                    }
                }
                TransactionStatus.IGNORED,
                TransactionStatus.VOIDED -> {
                    continue
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
     * Calculates as-of balance for an account at an arbitrary historical timestamp (Flow.md §6.1).
     */
    fun calculateAsOfBalance(
        account: AccountEntity,
        transactions: List<TransactionEntity>,
        asOfEpochMillis: Long
    ): Long {
        return calculateAccountBalance(account, transactions, asOfEpochMillis).currentBalancePaise
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
