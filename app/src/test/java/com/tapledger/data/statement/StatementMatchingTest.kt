package com.tapledger.data.statement

import com.google.common.truth.Truth.assertThat
import com.tapledger.core.database.entity.StatementLineEntity
import com.tapledger.core.database.entity.TransactionEntity
import com.tapledger.core.model.StatementMatchStatus
import com.tapledger.core.model.TransactionDirection
import com.tapledger.core.model.TransactionKind
import com.tapledger.core.model.TransactionSource
import com.tapledger.core.model.TransactionStatus
import com.tapledger.core.util.DateTimeUtils
import org.junit.Test
import java.time.LocalDate

class StatementMatchingTest {

    @Test
    fun testLineMatchingPipeline() {
        val windowStart = LocalDate.of(2024, 1, 1).atStartOfDay(DateTimeUtils.IST_ZONE).toInstant().toEpochMilli()
        val windowEnd = LocalDate.of(2024, 1, 31).atStartOfDay(DateTimeUtils.IST_ZONE).toInstant().toEpochMilli()

        // 1. Phase 1 - UPI Reference Exact Match
        val line1Date = LocalDate.of(2024, 1, 2).atTime(10, 0).atZone(DateTimeUtils.IST_ZONE).toInstant().toEpochMilli()
        val line1 = StatementLineEntity(
            id = 1,
            importId = 1,
            transactionDate = line1Date,
            description = "UPI / Merchant",
            reference = "UPI12345",
            debitPaise = 500_00L,
            creditPaise = 0L
        )
        val tx1 = TransactionEntity(
            id = 101,
            accountId = 1,
            kind = TransactionKind.EXPENSE,
            direction = TransactionDirection.OUT,
            amountPaise = 500_00L,
            occurredAt = line1Date,
            upiReference = "UPI12345",
            createdAt = 1000L,
            updatedAt = 1000L,
            status = TransactionStatus.CATEGORIZED,
            source = TransactionSource.NOTIFICATION
        )

        // 2. Phase 1 - Reference Match with Discrepancy (Amounts differ)
        val line2Date = LocalDate.of(2024, 1, 5).atTime(10, 0).atZone(DateTimeUtils.IST_ZONE).toInstant().toEpochMilli()
        val line2 = StatementLineEntity(
            id = 2,
            importId = 1,
            transactionDate = line2Date,
            description = "UPI / Fee",
            reference = "UPI-FEE-99",
            debitPaise = 105_00L, // Stmt says ₹105
            creditPaise = 0L
        )
        val tx2 = TransactionEntity(
            id = 102,
            accountId = 1,
            kind = TransactionKind.EXPENSE,
            direction = TransactionDirection.OUT,
            amountPaise = 100_00L, // App says ₹100
            occurredAt = line2Date,
            upiReference = "UPI-FEE-99",
            createdAt = 1000L,
            updatedAt = 1000L,
            status = TransactionStatus.CATEGORIZED,
            source = TransactionSource.NOTIFICATION
        )

        // 3. Phase 2 - Global Minimal Delta Match with effective date (t_effective)
        val line3Date = LocalDate.of(2024, 1, 10).atTime(10, 0).atZone(DateTimeUtils.IST_ZONE).toInstant().toEpochMilli()
        val line3 = StatementLineEntity(
            id = 3,
            importId = 1,
            transactionDate = line3Date,
            description = "CARD PAYMENT",
            reference = null,
            debitPaise = 2500_00L,
            creditPaise = 0L
        )
        val tx3 = TransactionEntity(
            id = 103,
            accountId = 1,
            kind = TransactionKind.EXPENSE,
            direction = TransactionDirection.OUT,
            amountPaise = 2500_00L,
            occurredAt = line3Date + 60000L,
            originalOccurredAt = line3Date,
            createdAt = 1000L,
            updatedAt = 1000L,
            status = TransactionStatus.CATEGORIZED,
            source = TransactionSource.NOTIFICATION
        )

        // 4. Phase 2 - Timing Difference Classification strictly by occurred_at
        // Statement Line cleared on Jan 31 (inside window).
        // App Tx alert booked on Feb 02 (outside window: occurred_at > windowEnd).
        // But t_effective is Jan 31, so delta <= 2 days matches them in Phase 2!
        val line4Date = LocalDate.of(2024, 1, 31).atTime(12, 0).atZone(DateTimeUtils.IST_ZONE).toInstant().toEpochMilli()
        val line4 = StatementLineEntity(
            id = 4,
            importId = 1,
            transactionDate = line4Date,
            description = "GROCERY STORE",
            reference = null,
            debitPaise = 750_00L,
            creditPaise = 0L
        )
        val tx4Date = LocalDate.of(2024, 2, 2).atTime(12, 0).atZone(DateTimeUtils.IST_ZONE).toInstant().toEpochMilli()
        val tx4 = TransactionEntity(
            id = 104,
            accountId = 1,
            kind = TransactionKind.EXPENSE,
            direction = TransactionDirection.OUT,
            amountPaise = 750_00L,
            occurredAt = tx4Date, // Strictly outside statement window
            originalOccurredAt = line4Date, // Exact match via t_effective
            createdAt = 1000L,
            updatedAt = 1000L,
            status = TransactionStatus.CATEGORIZED,
            source = TransactionSource.NOTIFICATION
        )

        // 5. Unmatched Line (Missing in App)
        val line5Date = LocalDate.of(2024, 1, 15).atTime(12, 0).atZone(DateTimeUtils.IST_ZONE).toInstant().toEpochMilli()
        val line5 = StatementLineEntity(
            id = 5,
            importId = 1,
            transactionDate = line5Date,
            description = "DIRECT DEBIT UTILITY",
            reference = null,
            debitPaise = 1500_00L,
            creditPaise = 0L
        )

        // 6. Unmatched App Transaction (Missing in Statement)
        val txUnmatched = TransactionEntity(
            id = 105,
            accountId = 1,
            kind = TransactionKind.EXPENSE,
            direction = TransactionDirection.OUT,
            amountPaise = 999_00L,
            occurredAt = line5Date,
            createdAt = 1000L,
            updatedAt = 1000L,
            status = TransactionStatus.CATEGORIZED,
            source = TransactionSource.MANUAL
        )

        val allLines = listOf(line1, line2, line3, line4, line5)
        val allTxs = listOf(tx1, tx2, tx3, tx4, txUnmatched)

        val matchedLines = StatementReconciliationEngine.matchStatementLines(
            lines = allLines,
            transactions = allTxs,
            periodStart = windowStart,
            periodEnd = windowEnd
        )

        // Verify line 1: Exact reference match
        assertThat(matchedLines[0].matchStatus).isEqualTo(StatementMatchStatus.MATCHED)
        assertThat(matchedLines[0].matchedTransactionId).isEqualTo(101L)

        // Verify line 2: Reference match with discrepancy
        assertThat(matchedLines[1].matchStatus).isEqualTo(StatementMatchStatus.MATCHED_WITH_DISCREPANCY)
        assertThat(matchedLines[1].matchedTransactionId).isEqualTo(102L)

        // Verify line 3: Phase 2 minimal delta match inside window
        assertThat(matchedLines[2].matchStatus).isEqualTo(StatementMatchStatus.MATCHED)
        assertThat(matchedLines[2].matchedTransactionId).isEqualTo(103L)

        // Verify line 4: Phase 2 match classified as TIMING_DIFFERENCE because occurred_at is outside window
        assertThat(matchedLines[3].matchStatus).isEqualTo(StatementMatchStatus.TIMING_DIFFERENCE)
        assertThat(matchedLines[3].matchedTransactionId).isEqualTo(104L)

        // Verify line 5: Unmatched line classified as MISSING_IN_APP
        assertThat(matchedLines[4].matchStatus).isEqualTo(StatementMatchStatus.MISSING_IN_APP)
        assertThat(matchedLines[4].matchedTransactionId).isNull()
    }
}
