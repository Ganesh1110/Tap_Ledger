package com.tapledger.data.statement

import com.tapledger.core.database.entity.AccountEntity
import com.tapledger.core.database.entity.StatementImportEntity
import com.tapledger.core.database.entity.StatementLineEntity
import com.tapledger.core.database.entity.TransactionEntity
import com.tapledger.core.model.StatementMatchStatus
import com.tapledger.core.model.TransactionDirection
import com.tapledger.core.model.TransactionKind
import com.tapledger.core.model.TransactionStatus
import com.tapledger.core.util.DateTimeUtils
import com.tapledger.domain.calculator.AccountBalanceCalculator
import kotlin.math.abs

data class DiscrepancyPair(
    val line: StatementLineEntity,
    val tx: TransactionEntity
)

data class TimingDifferencePair(
    val line: StatementLineEntity,
    val tx: TransactionEntity
)

/**
 * Encapsulates the 5 terms of the exact algebraic reconciliation identity (Flow.md §11.3).
 */
data class ReconciliationProof(
    val stmtClosingPaise: Long,
    val appBalanceEndPaise: Long,
    val lhsPaise: Long,
    val stmtOpeningPaise: Long,
    val appBalanceStartMinusPaise: Long,
    val openingDifferentialPaise: Long,
    val missingInAppSumPaise: Long,
    val missingInStmtSumPaise: Long,
    val discrepancySumPaise: Long,
    val timingDifferenceSumPaise: Long,
    val rhsPaise: Long,
    val isBalanced: Boolean,
    val missingInAppLines: List<StatementLineEntity>,
    val missingInStmtTransactions: List<TransactionEntity>,
    val discrepancies: List<DiscrepancyPair>,
    val timingDifferences: List<TimingDifferencePair>
)

object StatementReconciliationEngine {

    /**
     * Verifies internal arithmetic of bank and credit card statement (Flow.md §11.1):
     * ExpectedClosing = OpeningBalance + Σ Credits - Σ Debits
     */
    fun verifyStatementArithmetic(
        openingBalancePaise: Long,
        lines: List<StatementLineEntity>,
        reportedClosingBalancePaise: Long
    ): Boolean {
        val totalDebits = lines.sumOf { it.debitPaise }
        val totalCredits = lines.sumOf { it.creditPaise }
        val expectedClosing = openingBalancePaise + totalCredits - totalDebits
        return expectedClosing == reportedClosingBalancePaise
    }

    /**
     * Verifies statement chain invariant (Flow.md §11.1):
     * Opening(S_n) == Closing(S_{n-1})
     */
    fun verifyStatementChain(
        previousClosingBalancePaise: Long?,
        currentOpeningBalancePaise: Long
    ): Boolean {
        if (previousClosingBalancePaise == null) return true
        return previousClosingBalancePaise == currentOpeningBalancePaise
    }

    /**
     * Executes two-phase matching between statement lines and app transactions (Flow.md §11.2):
     * Phase 1: UPI / Reference matching with discrepancy classification.
     * Phase 2: Global minimal delta proximity matching by effective date (t_effective)
     *          with timing difference classification strictly by stored occurred_at.
     */
    fun matchStatementLines(
        lines: List<StatementLineEntity>,
        transactions: List<TransactionEntity>,
        periodStart: Long,
        periodEnd: Long,
        dateToleranceMillis: Long = 172_800_000L // ±2 days (172,800,000 ms) (§11.2)
    ): List<StatementLineEntity> {
        val windowStartOfDay = DateTimeUtils.getStartOfDayEpochMillis(periodStart)
        val windowEndOfDay = DateTimeUtils.getEndOfDayEpochMillis(periodEnd)

        val activeTxs = transactions.filter { tx ->
            tx.status in setOf(
                TransactionStatus.PENDING,
                TransactionStatus.CONFIRMED,
                TransactionStatus.CATEGORIZED
            )
        }

        val matchedTxIds = mutableSetOf<Long>()
        val matchedLineIndices = mutableSetOf<Int>()
        val result = lines.map { it.copy() }.toMutableList()

        // -------------------------------------------------------------
        // Phase 1: UPI / Reference Matching (§11.2)
        // -------------------------------------------------------------
        for (i in result.indices) {
            val line = result[i]
            val ref = line.reference?.trim()
            if (ref.isNullOrBlank()) continue

            val candidateTx = activeTxs.firstOrNull { tx ->
                !matchedTxIds.contains(tx.id) &&
                !tx.upiReference.isNullOrBlank() &&
                tx.upiReference.trim().equals(ref, ignoreCase = true) &&
                isDirectionCompatible(line.debitPaise > 0L, tx)
            }

            if (candidateTx != null) {
                matchedTxIds.add(candidateTx.id)
                matchedLineIndices.add(i)

                val lineAmount = if (line.debitPaise > 0L) line.debitPaise else line.creditPaise
                if (candidateTx.amountPaise != lineAmount) {
                    result[i] = line.copy(
                        matchedTransactionId = candidateTx.id,
                        matchStatus = StatementMatchStatus.MATCHED_WITH_DISCREPANCY
                    )
                } else {
                    val isTiming = candidateTx.occurredAt < windowStartOfDay || candidateTx.occurredAt > windowEndOfDay
                    result[i] = line.copy(
                        matchedTransactionId = candidateTx.id,
                        matchStatus = if (isTiming) StatementMatchStatus.TIMING_DIFFERENCE else StatementMatchStatus.MATCHED
                    )
                }
            }
        }

        // -------------------------------------------------------------
        // Phase 2: Global Minimal Delta Matching (§11.2)
        // -------------------------------------------------------------
        data class ScoredPair(
            val lineIndex: Int,
            val tx: TransactionEntity,
            val deltaMillis: Long
        )

        val candidatePairs = mutableListOf<ScoredPair>()

        for (i in result.indices) {
            if (matchedLineIndices.contains(i)) continue
            val line = result[i]
            val lineAmount = if (line.debitPaise > 0L) line.debitPaise else line.creditPaise
            val isDebit = line.debitPaise > 0L

            for (tx in activeTxs) {
                if (matchedTxIds.contains(tx.id)) continue
                if (tx.amountPaise != lineAmount) continue
                if (!isDirectionCompatible(isDebit, tx)) continue

                val tEffective = tx.originalOccurredAt ?: tx.occurredAt
                val delta = abs(tEffective - line.transactionDate)

                if (delta <= dateToleranceMillis) {
                    candidatePairs.add(ScoredPair(i, tx, delta))
                }
            }
        }

        // Sort globally by delta ascending, tie-breaking by line index then tx ID
        candidatePairs.sortWith(compareBy({ it.deltaMillis }, { it.lineIndex }, { it.tx.id }))

        for (pair in candidatePairs) {
            if (matchedLineIndices.contains(pair.lineIndex)) continue
            if (matchedTxIds.contains(pair.tx.id)) continue

            matchedLineIndices.add(pair.lineIndex)
            matchedTxIds.add(pair.tx.id)

            val line = result[pair.lineIndex]
            val isTiming = pair.tx.occurredAt < windowStartOfDay || pair.tx.occurredAt > windowEndOfDay

            result[pair.lineIndex] = line.copy(
                matchedTransactionId = pair.tx.id,
                matchStatus = if (isTiming) StatementMatchStatus.TIMING_DIFFERENCE else StatementMatchStatus.MATCHED
            )
        }

        // Remaining unmatched lines are flagged MISSING_IN_APP
        for (i in result.indices) {
            if (!matchedLineIndices.contains(i)) {
                result[i] = result[i].copy(
                    matchedTransactionId = null,
                    matchStatus = StatementMatchStatus.MISSING_IN_APP
                )
            }
        }

        return result
    }

    /**
     * Computes the exact mathematical proof of reconciliation (Flow.md §11.3):
     * StmtClosing - AppBalance(EndOfDayIST(period_end)) ==
     *     (StmtOpening - AppBalance(StartOfDayIST^-(period_start)))
     *     + Σ MISSING_IN_APP - Σ MISSING_IN_STMT + Σ DISCREPANCY + Σ TIMING_DIFFERENCE
     */
    fun computeReconciliationProof(
        account: AccountEntity,
        statementImport: StatementImportEntity,
        statementLines: List<StatementLineEntity>,
        transactions: List<TransactionEntity>
    ): ReconciliationProof {
        val windowStartOfDay = DateTimeUtils.getStartOfDayEpochMillis(statementImport.periodStart)
        val windowEndOfDay = DateTimeUtils.getEndOfDayEpochMillis(statementImport.periodEnd)
        val startOfDayMinus = windowStartOfDay - 1L

        val appBalanceEnd = AccountBalanceCalculator.calculateAsOfBalance(
            account = account,
            transactions = transactions,
            asOfEpochMillis = windowEndOfDay
        )

        val appBalanceStartMinus = AccountBalanceCalculator.calculateAsOfBalance(
            account = account,
            transactions = transactions,
            asOfEpochMillis = startOfDayMinus
        )

        val stmtClosing = statementImport.closingBalancePaise
        val stmtOpening = statementImport.openingBalancePaise

        val lhs = stmtClosing - appBalanceEnd
        val openingDiff = stmtOpening - appBalanceStartMinus

        // 1. MISSING_IN_APP: statement lines with match_status == MISSING_IN_APP
        val missingInAppLines = statementLines.filter { it.matchStatus == StatementMatchStatus.MISSING_IN_APP }
        val missingInAppSum = missingInAppLines.sumOf { lineSignedAmount(it) }

        // 2. MISSING_IN_STMT: app transactions in window [StartOfDay, EndOfDay] not matched to any statement line
        val matchedTxIds = statementLines.mapNotNull { it.matchedTransactionId }.toSet()
        val eligibleTxInWindow = transactions.filter { tx ->
            tx.accountId == account.id &&
            tx.status in setOf(TransactionStatus.PENDING, TransactionStatus.CONFIRMED, TransactionStatus.CATEGORIZED) &&
            tx.occurredAt in windowStartOfDay..windowEndOfDay
        }
        val missingInStmtTx = eligibleTxInWindow.filter { !matchedTxIds.contains(it.id) }
        val missingInStmtSum = missingInStmtTx.sumOf { txSignedAmount(it) }

        // 3. DISCREPANCY: statement lines matched with discrepancy
        val discrepancyLines = statementLines.filter { it.matchStatus == StatementMatchStatus.MATCHED_WITH_DISCREPANCY }
        val discrepancyPairs = discrepancyLines.mapNotNull { line ->
            val tx = transactions.find { it.id == line.matchedTransactionId } ?: return@mapNotNull null
            DiscrepancyPair(line, tx)
        }
        val discrepancySum = discrepancyPairs.sumOf { lineSignedAmount(it.line) - txSignedAmount(it.tx) }

        // 4. TIMING_DIFFERENCE: statement lines matched with timing difference
        val timingDifferenceLines = statementLines.filter { it.matchStatus == StatementMatchStatus.TIMING_DIFFERENCE }
        val timingDifferencePairs = timingDifferenceLines.mapNotNull { line ->
            val tx = transactions.find { it.id == line.matchedTransactionId } ?: return@mapNotNull null
            TimingDifferencePair(line, tx)
        }
        val timingDifferenceSum = timingDifferenceLines.sumOf { lineSignedAmount(it) }

        val rhs = openingDiff + missingInAppSum - missingInStmtSum + discrepancySum + timingDifferenceSum
        val isBalanced = (lhs == rhs)

        return ReconciliationProof(
            stmtClosingPaise = stmtClosing,
            appBalanceEndPaise = appBalanceEnd,
            lhsPaise = lhs,
            stmtOpeningPaise = stmtOpening,
            appBalanceStartMinusPaise = appBalanceStartMinus,
            openingDifferentialPaise = openingDiff,
            missingInAppSumPaise = missingInAppSum,
            missingInStmtSumPaise = missingInStmtSum,
            discrepancySumPaise = discrepancySum,
            timingDifferenceSumPaise = timingDifferenceSum,
            rhsPaise = rhs,
            isBalanced = isBalanced,
            missingInAppLines = missingInAppLines,
            missingInStmtTransactions = missingInStmtTx,
            discrepancies = discrepancyPairs,
            timingDifferences = timingDifferencePairs
        )
    }

    private fun isDirectionCompatible(isDebit: Boolean, tx: TransactionEntity): Boolean {
        return if (isDebit) {
            tx.direction == TransactionDirection.OUT ||
            tx.kind == TransactionKind.EXPENSE ||
            tx.kind == TransactionKind.FEE ||
            tx.kind == TransactionKind.INTEREST_CHARGED
        } else {
            tx.direction == TransactionDirection.IN ||
            tx.kind == TransactionKind.INCOME ||
            tx.kind == TransactionKind.REFUND
        }
    }

    fun lineSignedAmount(line: StatementLineEntity): Long {
        return line.creditPaise - line.debitPaise
    }

    fun txSignedAmount(tx: TransactionEntity): Long {
        return when (tx.direction) {
            TransactionDirection.IN -> tx.amountPaise
            TransactionDirection.OUT -> -tx.amountPaise
        }
    }
}
