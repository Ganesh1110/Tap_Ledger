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
        val utilizationPercent = calculateCreditCardUtilization(account, currentBalance)

        return AccountBalanceSummary(
            account = account,
            openingBalancePaise = account.openingBalancePaise,
            totalIncomingPaise = totalIncomingPaise,
            totalOutgoingPaise = totalOutgoingPaise,
            currentBalancePaise = currentBalance,
            creditCardUtilizationPercent = utilizationPercent
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
     * Flow.md §6.2: Computes credit card utilization percentage:
     * Utilization% = min(999, max(0, floor(|Balance(A)| * 100L / credit_limit_paise)))
     * when credit_limit_paise > 0 and Balance(A) < 0. Otherwise 0.
     */
    fun calculateCreditCardUtilization(
        account: AccountEntity,
        balancePaise: Long
    ): Int {
        val limit = account.creditLimitPaise ?: 0L
        if (limit <= 0L || balancePaise >= 0L) return 0
        val rawPercent = ((-balancePaise) * 100L) / limit
        return rawPercent.coerceIn(0L, 999L).toInt()
    }

    /**
     * Flow.md §6.2: Display Grouping & Warning:
     * Returns true if a BANK, CASH, or WALLET account drops negative (amber Data Inconsistency Warning).
     */
    fun hasDataInconsistencyWarning(account: AccountEntity, balancePaise: Long): Boolean {
        return (account.type == AccountType.BANK || account.type == AccountType.CASH || account.type == AccountType.WALLET) &&
                balancePaise < 0L
    }

    /**
     * PURE function computing Net Worth from a list of signed balances (Flow.md §6.2):
     * Assets      = Σ max( Balance, 0 )
     * Liabilities = Σ max( -Balance, 0 )
     * NetWorth    = Assets - Liabilities = Σ Balance
     */
    fun calculateNetWorthFromBalances(signedBalancesPaise: List<Long>): NetWorthSummary {
        var totalAssets = 0L
        var totalLiabilities = 0L

        for (balance in signedBalancesPaise) {
            if (balance >= 0) {
                totalAssets = Math.addExact(totalAssets, balance)
            } else {
                totalLiabilities = Math.addExact(totalLiabilities, -balance)
            }
        }

        return NetWorthSummary(
            totalAssetsPaise = totalAssets,
            totalLiabilitiesPaise = totalLiabilities,
            netWorthPaise = totalAssets - totalLiabilities
        )
    }

    /**
     * Computes Net Worth across all active accounts (Flow.md §6.2):
     * Assets      = Σ max( Balance(A), 0)
     * Liabilities = Σ max(−Balance(A), 0)      // type-agnostic
     * NetWorth    = Assets − Liabilities = Σ Balance(A)
     */
    fun calculateNetWorth(summaries: List<AccountBalanceSummary>): NetWorthSummary {
        val activeBalances = summaries
            .filter { it.account.isActive }
            .map { it.currentBalancePaise }
        return calculateNetWorthFromBalances(activeBalances)
    }

    /**
     * Flow.md §6.3: Expected Bank Balance based on Checkpoint Type:
     * - AVAILABLE_BALANCE: Balance(A, occurred_at)
     * - AVAILABLE_CREDIT_LIMIT: credit_limit_paise + Balance(A, occurred_at)
     * - TOTAL_OUTSTANDING: -Balance(A, occurred_at)
     */
    fun calculateExpectedBankBalance(
        account: AccountEntity,
        balanceAtOccurredAtPaise: Long,
        checkpointType: com.tapledger.core.model.CheckpointType
    ): Long {
        return when (checkpointType) {
            com.tapledger.core.model.CheckpointType.AVAILABLE_BALANCE -> balanceAtOccurredAtPaise
            com.tapledger.core.model.CheckpointType.AVAILABLE_CREDIT_LIMIT -> (account.creditLimitPaise ?: 0L) + balanceAtOccurredAtPaise
            com.tapledger.core.model.CheckpointType.TOTAL_OUTSTANDING -> -balanceAtOccurredAtPaise
        }
    }

    /**
     * Flow.md §6.3: Real-Time Balance Drift Formula:
     * BalanceDrift = checkpoint_balance_paise - ExpectedBankBalance
     */
    fun calculateBalanceDrift(
        account: AccountEntity,
        checkpointBalancePaise: Long,
        checkpointType: com.tapledger.core.model.CheckpointType,
        balanceAtOccurredAtPaise: Long
    ): Long {
        val expected = calculateExpectedBankBalance(account, balanceAtOccurredAtPaise, checkpointType)
        return checkpointBalancePaise - expected
    }

    /**
     * Flow.md §6.3: Real-Time Drift Stability Protocol:
     * Drift banner is displayed only if:
     * 1. t is the latest ingested checkpoint for account A
     * 2. Account A has zero unassigned PENDING transactions
     * 3. A 5-minute (300,000 ms) stability window has elapsed since notification ingestion (now - t.createdAt >= 300_000L)
     */
    fun shouldDisplayDriftBanner(
        isLatestCheckpointForAccount: Boolean,
        hasUnassignedPendingTransactions: Boolean,
        checkpointCreatedAtEpochMillis: Long,
        nowEpochMillis: Long
    ): Boolean {
        if (!isLatestCheckpointForAccount) return false
        if (hasUnassignedPendingTransactions) return false
        return (nowEpochMillis - checkpointCreatedAtEpochMillis) >= 300_000L
    }
}
