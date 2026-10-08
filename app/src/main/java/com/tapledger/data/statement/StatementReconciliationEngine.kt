package com.tapledger.data.statement

import com.tapledger.core.database.entity.StatementImportEntity
import com.tapledger.core.database.entity.StatementLineEntity
import com.tapledger.core.database.entity.TransactionEntity
import com.tapledger.core.model.StatementMatchStatus
import com.tapledger.core.model.TransactionKind
import com.tapledger.core.model.TransactionStatus
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import kotlin.math.abs

data class StatementParseResult(
    val importEntity: StatementImportEntity,
    val lines: List<StatementLineEntity>,
    val isArithmeticallyConsistent: Boolean,
    val calculatedClosingBalancePaise: Long
)

object StatementReconciliationEngine {

    /**
     * Verifies internal arithmetic of bank statement (Section 53):
     * opening balance + credits - debits == closing balance
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
     * Executes 1-to-1 matching between statement lines and application transactions (Section 51).
     * Guarantees that multiple transactions with the same amount never match the same statement row.
     */
    fun matchStatementLines(
        lines: List<StatementLineEntity>,
        transactions: List<TransactionEntity>,
        dateToleranceMillis: Long = 86_400_000L * 2 // ±2 days
    ): List<StatementLineEntity> {
        val matchedTxIds = mutableSetOf<Long>()
        val updatedLines = mutableListOf<StatementLineEntity>()

        for (line in lines) {
            val lineAmount = if (line.debitPaise > 0) line.debitPaise else line.creditPaise
            val isDebit = line.debitPaise > 0

            // Level 1: Match by unique reference / UPI reference
            var matchedTx: TransactionEntity? = null
            if (!line.reference.isNullOrBlank()) {
                matchedTx = transactions.firstOrNull { tx ->
                    !matchedTxIds.contains(tx.id) &&
                    tx.status != TransactionStatus.VOIDED &&
                    tx.upiReference != null &&
                    tx.upiReference == line.reference
                }
            }

            // Level 2: Match by exact amount + compatible direction + date tolerance
            if (matchedTx == null) {
                matchedTx = transactions.firstOrNull { tx ->
                    if (matchedTxIds.contains(tx.id)) return@firstOrNull false
                    if (tx.status == TransactionStatus.VOIDED) return@firstOrNull false
                    if (tx.amountPaise != lineAmount) return@firstOrNull false

                    val compatibleDirection = if (isDebit) {
                        tx.kind == TransactionKind.EXPENSE || tx.kind == TransactionKind.TRANSFER || tx.kind == TransactionKind.FEE
                    } else {
                        tx.kind == TransactionKind.INCOME || tx.kind == TransactionKind.REFUND || tx.kind == TransactionKind.TRANSFER
                    }
                    if (!compatibleDirection) return@firstOrNull false

                    val dateDiff = abs(tx.occurredAt - line.transactionDate)
                    dateDiff <= dateToleranceMillis
                }
            }

            if (matchedTx != null) {
                matchedTxIds.add(matchedTx.id)
                updatedLines.add(
                    line.copy(
                        matchedTransactionId = matchedTx.id,
                        matchStatus = StatementMatchStatus.MATCHED
                    )
                )
            } else {
                updatedLines.add(
                    line.copy(
                        matchedTransactionId = null,
                        matchStatus = StatementMatchStatus.MISSING_IN_APP
                    )
                )
            }
        }

        return updatedLines
    }
}
