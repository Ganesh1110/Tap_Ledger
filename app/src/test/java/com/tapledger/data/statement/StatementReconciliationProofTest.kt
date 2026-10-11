package com.tapledger.data.statement

import com.google.common.truth.Truth.assertThat
import com.tapledger.core.database.entity.AccountEntity
import com.tapledger.core.database.entity.StatementImportEntity
import com.tapledger.core.database.entity.StatementLineEntity
import com.tapledger.core.database.entity.TransactionEntity
import com.tapledger.core.model.AccountType
import com.tapledger.core.model.StatementImportStatus
import com.tapledger.core.model.StatementMatchStatus
import com.tapledger.core.model.TransactionDirection
import com.tapledger.core.model.TransactionKind
import com.tapledger.core.model.TransactionSource
import com.tapledger.core.model.TransactionStatus
import com.tapledger.core.util.DateTimeUtils
import org.junit.Test
import java.time.LocalDate
import kotlin.random.Random

class StatementReconciliationProofTest {

    @Test
    fun testReconciliationAlgebraicIdentity() {
        val windowStart = LocalDate.of(2024, 1, 1).atStartOfDay(DateTimeUtils.IST_ZONE).toInstant().toEpochMilli()
        val windowEnd = LocalDate.of(2024, 1, 31).atStartOfDay(DateTimeUtils.IST_ZONE).toInstant().toEpochMilli()

        val account = AccountEntity(
            id = 1,
            name = "Main Checking",
            type = AccountType.BANK,
            identifierLast4 = "1234",
            openingBalancePaise = 100_000_00L, // Account opening ₹1,00,000
            openingBalanceDate = LocalDate.of(2023, 12, 1).atStartOfDay(DateTimeUtils.IST_ZONE).toInstant().toEpochMilli(),
            createdAt = 1000L,
            updatedAt = 1000L
        )

        // -------------------------------------------------------------
        // Deterministic Verification with all 5 terms present:
        // -------------------------------------------------------------
        // 1. Pre-window transaction (Dec 14, 2023): ₹10,000 expense
        //    Impacts AppBalance(start-) without being in StmtOpening
        val txPreWindow = TransactionEntity(
            id = 1,
            accountId = 1,
            kind = TransactionKind.EXPENSE,
            direction = TransactionDirection.OUT,
            amountPaise = 10_000_00L,
            occurredAt = LocalDate.of(2023, 12, 14).atTime(10, 0).atZone(DateTimeUtils.IST_ZONE).toInstant().toEpochMilli(),
            createdAt = 1000L,
            updatedAt = 1000L,
            status = TransactionStatus.CATEGORIZED,
            source = TransactionSource.MANUAL
        )

        // 2. Exact Match in window: ₹5,000 credit (Salary advance)
        val lineExactDate = LocalDate.of(2024, 1, 6).atTime(10, 0).atZone(DateTimeUtils.IST_ZONE).toInstant().toEpochMilli()
        val lineExact = StatementLineEntity(
            id = 10,
            importId = 1,
            transactionDate = lineExactDate,
            description = "SALARY ADV",
            creditPaise = 5_000_00L,
            debitPaise = 0L,
            matchedTransactionId = 2,
            matchStatus = StatementMatchStatus.MATCHED
        )
        val txExact = TransactionEntity(
            id = 2,
            accountId = 1,
            kind = TransactionKind.INCOME,
            direction = TransactionDirection.IN,
            amountPaise = 5_000_00L,
            occurredAt = lineExactDate,
            createdAt = 1000L,
            updatedAt = 1000L,
            status = TransactionStatus.CATEGORIZED,
            source = TransactionSource.MANUAL
        )

        // 3. Discrepancy Match: Line debit ₹1,200, App tx debit ₹1,000
        val lineDiscrepancyDate = LocalDate.of(2024, 1, 12).atTime(10, 0).atZone(DateTimeUtils.IST_ZONE).toInstant().toEpochMilli()
        val lineDiscrepancy = StatementLineEntity(
            id = 11,
            importId = 1,
            transactionDate = lineDiscrepancyDate,
            description = "HOTEL",
            creditPaise = 0L,
            debitPaise = 1_200_00L, // Stmt debit ₹1,200
            matchedTransactionId = 3,
            matchStatus = StatementMatchStatus.MATCHED_WITH_DISCREPANCY
        )
        val txDiscrepancy = TransactionEntity(
            id = 3,
            accountId = 1,
            kind = TransactionKind.EXPENSE,
            direction = TransactionDirection.OUT,
            amountPaise = 1_000_00L, // App debit ₹1,000
            occurredAt = lineDiscrepancyDate,
            createdAt = 1000L,
            updatedAt = 1000L,
            status = TransactionStatus.CATEGORIZED,
            source = TransactionSource.MANUAL
        )

        // 4. Timing Difference: Statement cleared on Jan 31 (debit ₹800),
        //    App transaction alert booked on Feb 02 (strictly outside window > windowEnd)
        val lineTimingDate = LocalDate.of(2024, 1, 31).atTime(12, 0).atZone(DateTimeUtils.IST_ZONE).toInstant().toEpochMilli()
        val lineTiming = StatementLineEntity(
            id = 12,
            importId = 1,
            transactionDate = lineTimingDate,
            description = "E-COMMERCE",
            creditPaise = 0L,
            debitPaise = 800_00L,
            matchedTransactionId = 4,
            matchStatus = StatementMatchStatus.TIMING_DIFFERENCE
        )
        val txTiming = TransactionEntity(
            id = 4,
            accountId = 1,
            kind = TransactionKind.EXPENSE,
            direction = TransactionDirection.OUT,
            amountPaise = 800_00L,
            occurredAt = LocalDate.of(2024, 2, 2).atTime(12, 0).atZone(DateTimeUtils.IST_ZONE).toInstant().toEpochMilli(),
            originalOccurredAt = lineTimingDate,
            createdAt = 1000L,
            updatedAt = 1000L,
            status = TransactionStatus.CATEGORIZED,
            source = TransactionSource.NOTIFICATION
        )

        // 5. Missing In App: Statement line not found in app (Bank fee ₹50)
        val lineMissingInAppDate = LocalDate.of(2024, 1, 18).atTime(10, 0).atZone(DateTimeUtils.IST_ZONE).toInstant().toEpochMilli()
        val lineMissingInApp = StatementLineEntity(
            id = 13,
            importId = 1,
            transactionDate = lineMissingInAppDate,
            description = "ANNUAL SMS CHARGE",
            creditPaise = 0L,
            debitPaise = 50_00L,
            matchedTransactionId = null,
            matchStatus = StatementMatchStatus.MISSING_IN_APP
        )

        // 6. Missing In Statement: App transaction not in statement (Cash withdrawal ₹2,000)
        val txMissingInStmtDate = LocalDate.of(2024, 1, 19).atTime(10, 0).atZone(DateTimeUtils.IST_ZONE).toInstant().toEpochMilli()
        val txMissingInStmt = TransactionEntity(
            id = 5,
            accountId = 1,
            kind = TransactionKind.EXPENSE,
            direction = TransactionDirection.OUT,
            amountPaise = 2_000_00L,
            occurredAt = txMissingInStmtDate,
            createdAt = 1000L,
            updatedAt = 1000L,
            status = TransactionStatus.CATEGORIZED,
            source = TransactionSource.MANUAL
        )

        val deterministicLines = listOf(lineExact, lineDiscrepancy, lineTiming, lineMissingInApp)
        val deterministicTxs = listOf(txPreWindow, txExact, txDiscrepancy, txTiming, txMissingInStmt)

        // Calculate statement balances internally consistent:
        // StmtOpening = ₹95,000
        val stmtOpening = 95_000_00L
        val totalDebits = deterministicLines.sumOf { it.debitPaise }
        val totalCredits = deterministicLines.sumOf { it.creditPaise }
        val stmtClosing = stmtOpening + totalCredits - totalDebits

        val importEntity = StatementImportEntity(
            id = 1,
            accountId = 1,
            periodStart = windowStart,
            periodEnd = windowEnd,
            openingBalancePaise = stmtOpening,
            closingBalancePaise = stmtClosing,
            totalDebitsPaise = totalDebits,
            totalCreditsPaise = totalCredits,
            lineCount = deterministicLines.size,
            status = StatementImportStatus.IMPORTED,
            createdAt = 1000L
        )

        val proof = StatementReconciliationEngine.computeReconciliationProof(
            account = account,
            statementImport = importEntity,
            statementLines = deterministicLines,
            transactions = deterministicTxs
        )

        assertThat(proof.isBalanced).isTrue()
        assertThat(proof.lhsPaise).isEqualTo(proof.rhsPaise)

        // -------------------------------------------------------------
        // Property / Randomized Identity Invariant Verification:
        // -------------------------------------------------------------
        val random = Random(42)
        val inWindowMin = windowStart + 3600_000L
        val inWindowMax = DateTimeUtils.getEndOfDayEpochMillis(windowEnd) - 3600_000L

        for (iter in 1..100) {
            val randStmtOpening = random.nextLong(10_000_00L, 1_000_000_00L)
            val randLines = mutableListOf<StatementLineEntity>()
            val randTxs = mutableListOf<TransactionEntity>()

            var lineIdCounter = 100L
            var txIdCounter = 1000L

            // Randomized pre-window transactions (0 to 3)
            val numPreWindow = random.nextInt(4)
            for (i in 0 until numPreWindow) {
                val amt = random.nextLong(100_00L, 10_000_00L)
                val isCredit = random.nextBoolean()
                randTxs.add(
                    TransactionEntity(
                        id = txIdCounter++,
                        accountId = 1,
                        kind = if (isCredit) TransactionKind.INCOME else TransactionKind.EXPENSE,
                        direction = if (isCredit) TransactionDirection.IN else TransactionDirection.OUT,
                        amountPaise = amt,
                        occurredAt = windowStart - random.nextLong(3600_000L, 864000000L),
                        createdAt = 1000L,
                        updatedAt = 1000L,
                        status = TransactionStatus.CATEGORIZED,
                        source = TransactionSource.MANUAL
                    )
                )
            }

            // Randomized exact matched lines in window (1 to 5)
            val numExact = random.nextInt(1, 6)
            for (i in 0 until numExact) {
                val amt = random.nextLong(50_00L, 50_000_00L)
                val isCredit = random.nextBoolean()
                val date = random.nextLong(inWindowMin, inWindowMax)
                val txId = txIdCounter++
                randLines.add(
                    StatementLineEntity(
                        id = lineIdCounter++,
                        importId = 2,
                        transactionDate = date,
                        description = "Exact #$i",
                        creditPaise = if (isCredit) amt else 0L,
                        debitPaise = if (isCredit) 0L else amt,
                        matchedTransactionId = txId,
                        matchStatus = StatementMatchStatus.MATCHED
                    )
                )
                randTxs.add(
                    TransactionEntity(
                        id = txId,
                        accountId = 1,
                        kind = if (isCredit) TransactionKind.INCOME else TransactionKind.EXPENSE,
                        direction = if (isCredit) TransactionDirection.IN else TransactionDirection.OUT,
                        amountPaise = amt,
                        occurredAt = date,
                        createdAt = 1000L,
                        updatedAt = 1000L,
                        status = TransactionStatus.CATEGORIZED,
                        source = TransactionSource.MANUAL
                    )
                )
            }

            // Randomized discrepancy lines (0 to 3)
            val numDiscrepancy = random.nextInt(4)
            for (i in 0 until numDiscrepancy) {
                val lineAmt = random.nextLong(100_00L, 5000_00L)
                val txAmt = random.nextLong(100_00L, 5000_00L)
                val isCredit = random.nextBoolean()
                val date = random.nextLong(inWindowMin, inWindowMax)
                val txId = txIdCounter++
                randLines.add(
                    StatementLineEntity(
                        id = lineIdCounter++,
                        importId = 2,
                        transactionDate = date,
                        description = "Discrepancy #$i",
                        creditPaise = if (isCredit) lineAmt else 0L,
                        debitPaise = if (isCredit) 0L else lineAmt,
                        matchedTransactionId = txId,
                        matchStatus = StatementMatchStatus.MATCHED_WITH_DISCREPANCY
                    )
                )
                randTxs.add(
                    TransactionEntity(
                        id = txId,
                        accountId = 1,
                        kind = if (isCredit) TransactionKind.INCOME else TransactionKind.EXPENSE,
                        direction = if (isCredit) TransactionDirection.IN else TransactionDirection.OUT,
                        amountPaise = txAmt,
                        occurredAt = date,
                        createdAt = 1000L,
                        updatedAt = 1000L,
                        status = TransactionStatus.CATEGORIZED,
                        source = TransactionSource.MANUAL
                    )
                )
            }

            // Randomized timing difference lines (0 to 3)
            val numTiming = random.nextInt(4)
            for (i in 0 until numTiming) {
                val amt = random.nextLong(100_00L, 5000_00L)
                val isCredit = random.nextBoolean()
                val date = random.nextLong(inWindowMin, inWindowMax)
                val txId = txIdCounter++
                randLines.add(
                    StatementLineEntity(
                        id = lineIdCounter++,
                        importId = 2,
                        transactionDate = date,
                        description = "Timing #$i",
                        creditPaise = if (isCredit) amt else 0L,
                        debitPaise = if (isCredit) 0L else amt,
                        matchedTransactionId = txId,
                        matchStatus = StatementMatchStatus.TIMING_DIFFERENCE
                    )
                )
                // App tx booked after window (> EndOfDayIST)
                randTxs.add(
                    TransactionEntity(
                        id = txId,
                        accountId = 1,
                        kind = if (isCredit) TransactionKind.INCOME else TransactionKind.EXPENSE,
                        direction = if (isCredit) TransactionDirection.IN else TransactionDirection.OUT,
                        amountPaise = amt,
                        occurredAt = DateTimeUtils.getEndOfDayEpochMillis(windowEnd) + random.nextLong(86400000L, 864000000L),
                        originalOccurredAt = date,
                        createdAt = 1000L,
                        updatedAt = 1000L,
                        status = TransactionStatus.CATEGORIZED,
                        source = TransactionSource.NOTIFICATION
                    )
                )
            }

            // Randomized missing in app lines (0 to 3)
            val numMissingInApp = random.nextInt(4)
            for (i in 0 until numMissingInApp) {
                val amt = random.nextLong(100_00L, 5000_00L)
                val isCredit = random.nextBoolean()
                randLines.add(
                    StatementLineEntity(
                        id = lineIdCounter++,
                        importId = 2,
                        transactionDate = random.nextLong(inWindowMin, inWindowMax),
                        description = "MissingInApp #$i",
                        creditPaise = if (isCredit) amt else 0L,
                        debitPaise = if (isCredit) 0L else amt,
                        matchedTransactionId = null,
                        matchStatus = StatementMatchStatus.MISSING_IN_APP
                    )
                )
            }

            // Randomized missing in statement txs (0 to 3)
            val numMissingInStmt = random.nextInt(4)
            for (i in 0 until numMissingInStmt) {
                val amt = random.nextLong(100_00L, 5000_00L)
                val isCredit = random.nextBoolean()
                randTxs.add(
                    TransactionEntity(
                        id = txIdCounter++,
                        accountId = 1,
                        kind = if (isCredit) TransactionKind.INCOME else TransactionKind.EXPENSE,
                        direction = if (isCredit) TransactionDirection.IN else TransactionDirection.OUT,
                        amountPaise = amt,
                        occurredAt = random.nextLong(inWindowMin, inWindowMax),
                        createdAt = 1000L,
                        updatedAt = 1000L,
                        status = TransactionStatus.CATEGORIZED,
                        source = TransactionSource.MANUAL
                    )
                )
            }

            val rDebits = randLines.sumOf { it.debitPaise }
            val rCredits = randLines.sumOf { it.creditPaise }
            val randStmtClosing = randStmtOpening + rCredits - rDebits

            val randImportEntity = StatementImportEntity(
                id = 2,
                accountId = 1,
                periodStart = windowStart,
                periodEnd = windowEnd,
                openingBalancePaise = randStmtOpening,
                closingBalancePaise = randStmtClosing,
                totalDebitsPaise = rDebits,
                totalCreditsPaise = rCredits,
                lineCount = randLines.size,
                status = StatementImportStatus.IMPORTED,
                createdAt = 1000L
            )

            val randProof = StatementReconciliationEngine.computeReconciliationProof(
                account = account,
                statementImport = randImportEntity,
                statementLines = randLines,
                transactions = randTxs
            )

            assertThat(randProof.isBalanced).isTrue()
            assertThat(randProof.lhsPaise).isEqualTo(randProof.rhsPaise)
        }
    }
}
