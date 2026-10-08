package com.tapledger.data.statement

import com.google.common.truth.Truth.assertThat
import com.tapledger.core.database.entity.StatementLineEntity
import com.tapledger.core.database.entity.TransactionEntity
import com.tapledger.core.model.StatementMatchStatus
import com.tapledger.core.model.TransactionKind
import com.tapledger.core.model.TransactionSource
import com.tapledger.core.model.TransactionStatus
import org.junit.Test

class StatementReconciliationEngineTest {

    @Test
    fun verifyStatementArithmetic_validatesBalances() {
        val lines = listOf(
            StatementLineEntity(
                id = 1,
                statementImportId = 10,
                lineNumber = 1,
                transactionDate = 1000L,
                description = "Salary",
                creditPaise = 50_000_00L,
                debitPaise = 0L,
                createdAt = 1000L
            ),
            StatementLineEntity(
                id = 2,
                statementImportId = 10,
                lineNumber = 2,
                transactionDate = 2000L,
                description = "Rent",
                creditPaise = 0L,
                debitPaise = 15_000_00L,
                createdAt = 2000L
            )
        )

        // Opening ₹10,000 + Credits ₹50,000 - Debits ₹15,000 = Closing ₹45,000
        val isValid = StatementReconciliationEngine.verifyStatementArithmetic(
            openingBalancePaise = 10_000_00L,
            lines = lines,
            reportedClosingBalancePaise = 45_000_00L
        )
        assertThat(isValid).isTrue()

        // Mismatched closing balance fails verification
        val isInvalid = StatementReconciliationEngine.verifyStatementArithmetic(
            openingBalancePaise = 10_000_00L,
            lines = lines,
            reportedClosingBalancePaise = 46_000_00L
        )
        assertThat(isInvalid).isFalse()
    }

    @Test
    fun matchStatementLines_enforcesOneToOneConstraint() {
        // Two identical ₹50 debits in statement
        val lines = listOf(
            StatementLineEntity(
                id = 1,
                statementImportId = 10,
                lineNumber = 1,
                transactionDate = 1000L,
                description = "Chai",
                debitPaise = 50_00L,
                creditPaise = 0L,
                createdAt = 1000L
            ),
            StatementLineEntity(
                id = 2,
                statementImportId = 10,
                lineNumber = 2,
                transactionDate = 1200L,
                description = "Chai",
                debitPaise = 50_00L,
                creditPaise = 0L,
                createdAt = 1200L
            )
        )

        // Only ONE matching ₹50 transaction in app
        val txs = listOf(
            TransactionEntity(
                id = 101,
                accountId = 1,
                kind = TransactionKind.EXPENSE,
                amountPaise = 50_00L,
                occurredAt = 1050L,
                createdAt = 1050L,
                updatedAt = 1050L,
                status = TransactionStatus.CATEGORIZED,
                source = TransactionSource.NOTIFICATION
            )
        )

        val matchedLines = StatementReconciliationEngine.matchStatementLines(lines, txs)

        // Exactly one should match transaction 101, the other must be MISSING_IN_APP
        assertThat(matchedLines[0].matchStatus).isEqualTo(StatementMatchStatus.MATCHED)
        assertThat(matchedLines[0].matchedTransactionId).isEqualTo(101L)

        assertThat(matchedLines[1].matchStatus).isEqualTo(StatementMatchStatus.MISSING_IN_APP)
        assertThat(matchedLines[1].matchedTransactionId).isNull()
    }
}
