package com.tapledger.data.repository

import androidx.room.withTransaction
import com.tapledger.core.database.TapLedgerDatabase
import com.tapledger.core.database.dao.AccountDao
import com.tapledger.core.database.dao.AuditLogDao
import com.tapledger.core.database.dao.BudgetPeriodDao
import com.tapledger.core.database.dao.StatementImportDao
import com.tapledger.core.database.dao.StatementLineDao
import com.tapledger.core.database.dao.TransactionDao
import com.tapledger.core.database.entity.AuditLogEntity
import com.tapledger.core.database.entity.StatementImportEntity
import com.tapledger.core.database.entity.StatementLineEntity
import com.tapledger.core.model.AuditAction
import com.tapledger.core.model.BudgetPeriodStatus
import com.tapledger.core.model.StatementImportStatus
import com.tapledger.core.model.StatementMatchStatus
import com.tapledger.core.model.TransactionSource
import com.tapledger.core.util.DateTimeUtils
import com.tapledger.data.statement.ReconciliationProof
import com.tapledger.data.statement.StatementCsvParser
import com.tapledger.data.statement.StatementReconciliationEngine
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StatementRepository @Inject constructor(
    private val database: TapLedgerDatabase,
    private val statementImportDao: StatementImportDao,
    private val statementLineDao: StatementLineDao,
    private val transactionDao: TransactionDao,
    private val accountDao: AccountDao,
    private val budgetPeriodDao: BudgetPeriodDao,
    private val auditLogDao: AuditLogDao,
    private val transactionRepository: TransactionRepository
) {

    fun observeAllAccounts(): Flow<List<com.tapledger.core.database.entity.AccountEntity>> =
        accountDao.getAllActiveAccounts()

    fun observeAllImports(): Flow<List<StatementImportEntity>> = statementImportDao.observeAll()

    fun observeLinesForImport(importId: Long): Flow<List<StatementLineEntity>> =
        statementLineDao.observeLinesForImport(importId)

    suspend fun getImportById(id: Long): StatementImportEntity? = statementImportDao.getById(id)

    suspend fun importStatement(
        accountId: Long,
        csvContent: String,
        format: String = "CSV",
        openingBalancePaise: Long,
        closingBalancePaise: Long,
        periodStart: Long,
        periodEnd: Long
    ): List<Long> {
        val now = DateTimeUtils.nowEpochMillis()
        val dummyImportId = 0L
        val rawLines = StatementCsvParser.parseCsv(csvContent, dummyImportId, now)
        val breakdowns = com.tapledger.data.statement.StatementPdfParser.splitLinesIntoMonthlyBreakdown(
            lines = rawLines,
            overallOpeningPaise = openingBalancePaise,
            overallClosingPaise = closingBalancePaise
        )

        if (breakdowns.size > 1) {
            val importIds = mutableListOf<Long>()
            for (monthData in breakdowns) {
                val id = importParsedLines(
                    accountId = accountId,
                    rawLines = monthData.lines,
                    format = format,
                    openingBalancePaise = monthData.openingBalancePaise,
                    closingBalancePaise = monthData.closingBalancePaise,
                    periodStart = monthData.periodStart,
                    periodEnd = monthData.periodEnd,
                    now = now
                )
                importIds.add(id)
            }
            return importIds
        } else {
            val id = importParsedLines(
                accountId = accountId,
                rawLines = rawLines,
                format = format,
                openingBalancePaise = openingBalancePaise,
                closingBalancePaise = closingBalancePaise,
                periodStart = periodStart,
                periodEnd = periodEnd,
                now = now
            )
            return listOf(id)
        }
    }

    suspend fun importPdfStatement(
        accountId: Long,
        inputStream: java.io.InputStream,
        password: String? = null,
        openingBalancePaise: Long? = null,
        closingBalancePaise: Long? = null,
        periodStart: Long? = null,
        periodEnd: Long? = null
    ): List<Long> {
        val now = DateTimeUtils.nowEpochMillis()
        val dummyImportId = 0L
        val pdfResult = com.tapledger.data.statement.StatementPdfParser.parsePdf(
            inputStream = inputStream,
            password = password,
            importId = dummyImportId,
            createdAt = now
        )
        val finalOpening = openingBalancePaise?.takeIf { it != 0L }
            ?: pdfResult.detectedOpeningBalancePaise
            ?: 0L
        val finalClosing = closingBalancePaise?.takeIf { it != 0L }
            ?: pdfResult.detectedClosingBalancePaise
            ?: 0L

        val breakdowns = if (pdfResult.monthlyBreakdown.isNotEmpty()) {
            pdfResult.monthlyBreakdown
        } else {
            com.tapledger.data.statement.StatementPdfParser.splitLinesIntoMonthlyBreakdown(
                lines = pdfResult.lines,
                overallOpeningPaise = finalOpening,
                overallClosingPaise = finalClosing
            )
        }

        if (breakdowns.size > 1) {
            val importIds = mutableListOf<Long>()
            for (monthData in breakdowns) {
                val id = importParsedLines(
                    accountId = accountId,
                    rawLines = monthData.lines,
                    format = "PDF",
                    openingBalancePaise = monthData.openingBalancePaise,
                    closingBalancePaise = monthData.closingBalancePaise,
                    periodStart = monthData.periodStart,
                    periodEnd = monthData.periodEnd,
                    now = now
                )
                importIds.add(id)
            }
            return importIds
        } else if (breakdowns.size == 1) {
            val single = breakdowns[0]
            val id = importParsedLines(
                accountId = accountId,
                rawLines = single.lines,
                format = "PDF",
                openingBalancePaise = single.openingBalancePaise,
                closingBalancePaise = single.closingBalancePaise,
                periodStart = single.periodStart,
                periodEnd = single.periodEnd,
                now = now
            )
            return listOf(id)
        } else {
            val finalStart = periodStart?.takeIf { it != 0L }
                ?: pdfResult.detectedPeriodStart
                ?: DateTimeUtils.getStartOfDayEpochMillis(now)
            val finalEnd = periodEnd?.takeIf { it != 0L }
                ?: pdfResult.detectedPeriodEnd
                ?: DateTimeUtils.getEndOfDayEpochMillis(now)

            val id = importParsedLines(
                accountId = accountId,
                rawLines = pdfResult.lines,
                format = "PDF",
                openingBalancePaise = finalOpening,
                closingBalancePaise = finalClosing,
                periodStart = finalStart,
                periodEnd = finalEnd,
                now = now
            )
            return listOf(id)
        }
    }

    private suspend fun importParsedLines(
        accountId: Long,
        rawLines: List<StatementLineEntity>,
        format: String,
        openingBalancePaise: Long,
        closingBalancePaise: Long,
        periodStart: Long,
        periodEnd: Long,
        now: Long
    ): Long {
        return database.withTransaction {
            val totalDebits = rawLines.sumOf { it.debitPaise }
            val totalCredits = rawLines.sumOf { it.creditPaise }
            val lineCount = rawLines.size

            // 1. Verify internal arithmetic (Flow.md §11.1)
            val isArithmeticValid = StatementReconciliationEngine.verifyStatementArithmetic(
                openingBalancePaise = openingBalancePaise,
                lines = rawLines,
                reportedClosingBalancePaise = closingBalancePaise
            )

            // 2. Verify statement chain invariant (Flow.md §11.1)
            val prevImport = statementImportDao.getLatestImportForAccount(accountId, periodStart)
            val isChainValid = StatementReconciliationEngine.verifyStatementChain(
                previousClosingBalancePaise = prevImport?.closingBalancePaise,
                currentOpeningBalancePaise = openingBalancePaise
            )

            val initialStatus = when {
                !isArithmeticValid -> StatementImportStatus.ARITHMETIC_ERROR
                else -> StatementImportStatus.IMPORTED
            }

            val importEntity = StatementImportEntity(
                accountId = accountId,
                periodStart = periodStart,
                periodEnd = periodEnd,
                openingBalancePaise = openingBalancePaise,
                closingBalancePaise = closingBalancePaise,
                totalDebitsPaise = totalDebits,
                totalCreditsPaise = totalCredits,
                lineCount = lineCount,
                status = initialStatus,
                createdAt = now
            )
            val importId = statementImportDao.insert(importEntity)

            if (rawLines.isEmpty()) {
                return@withTransaction importId
            }

            val linesWithImportId = rawLines.map { it.copy(importId = importId) }

            // 3. Fetch active app transactions for the account
            val appTransactions = transactionDao.getTransactionsForAccountSync(accountId)

            // 4. Two-phase match statement lines with app transactions (Flow.md §11.2)
            val matchedLines = StatementReconciliationEngine.matchStatementLines(
                lines = linesWithImportId,
                transactions = appTransactions,
                periodStart = periodStart,
                periodEnd = periodEnd
            )

            // 5. Save matched lines
            statementLineDao.insertAll(matchedLines)

            // 6. Update import status if arithmetic was valid
            if (isArithmeticValid) {
                val allFullyMatched = matchedLines.all {
                    it.matchStatus == StatementMatchStatus.MATCHED ||
                    it.matchStatus == StatementMatchStatus.RESOLVED ||
                    it.matchStatus == StatementMatchStatus.TIMING_DIFFERENCE
                }
                val finalStatus = if (allFullyMatched) {
                    StatementImportStatus.RECONCILED
                } else {
                    StatementImportStatus.IMPORTED
                }
                statementImportDao.update(importEntity.copy(id = importId, status = finalStatus))
            }

            importId
        }
    }

    suspend fun createAppTransactionFromLine(
        line: StatementLineEntity,
        accountId: Long,
        categoryId: Long? = null
    ): Long {
        val isDebit = line.debitPaise > 0L
        val amountPaise = if (isDebit) line.debitPaise else line.creditPaise

        return database.withTransaction {
            val now = DateTimeUtils.nowEpochMillis()

            // Flow.md §11.2 (3): Closed Period Guard:
            // If line.transactionDate falls within an already CLOSED month,
            // book into earliest open period (or now) with original_occurred_at = line.transactionDate
            val period = budgetPeriodDao.getPeriodForTimestamp(line.transactionDate)
            val isClosedPeriod = period != null && period.status == BudgetPeriodStatus.CLOSED

            val (effectiveOccurredAt, originalOccurredAt) = if (isClosedPeriod) {
                now to line.transactionDate
            } else {
                line.transactionDate to null
            }

            val txId = if (isDebit) {
                val allocations = if (categoryId != null && categoryId > 0L) {
                    listOf(categoryId to amountPaise)
                } else {
                    emptyList()
                }
                transactionRepository.recordExpense(
                    accountId = accountId,
                    amountPaise = amountPaise,
                    occurredAt = effectiveOccurredAt,
                    merchantRaw = line.description,
                    upiReference = line.reference,
                    source = TransactionSource.STATEMENT,
                    allocations = allocations,
                    originalOccurredAt = originalOccurredAt
                )
            } else {
                transactionRepository.recordIncome(
                    accountId = accountId,
                    amountPaise = amountPaise,
                    occurredAt = effectiveOccurredAt,
                    merchantRaw = line.description,
                    upiReference = line.reference,
                    source = TransactionSource.STATEMENT,
                    originalOccurredAt = originalOccurredAt
                )
            }

            if (isClosedPeriod) {
                auditLogDao.insert(
                    AuditLogEntity(
                        transactionId = txId,
                        action = AuditAction.CREATED,
                        beforeSnapshotJson = null,
                        afterSnapshotJson = null,
                        reason = "Reconciliation adjustment: booked to open period due to closed period at ${line.transactionDate} (Flow.md §11.2)",
                        createdAt = now
                    )
                )
            }

            val updatedLine = line.copy(
                matchedTransactionId = txId,
                matchStatus = StatementMatchStatus.MATCHED
            )
            statementLineDao.update(updatedLine)
            checkAndUpdateImportStatus(line.importId)

            txId
        }
    }

    suspend fun resolveLine(line: StatementLineEntity) {
        statementLineDao.update(line.copy(matchStatus = StatementMatchStatus.RESOLVED))
        checkAndUpdateImportStatus(line.importId)
    }

    suspend fun checkAndUpdateImportStatus(importId: Long) {
        val importEntity = statementImportDao.getById(importId) ?: return
        val lines = statementLineDao.getLinesForImportSync(importId)
        val allHandled = lines.isNotEmpty() && lines.none { it.matchStatus == StatementMatchStatus.MISSING_IN_APP }
        if (allHandled) {
            val isArithmeticValid = StatementReconciliationEngine.verifyStatementArithmetic(
                openingBalancePaise = importEntity.openingBalancePaise,
                lines = lines,
                reportedClosingBalancePaise = importEntity.closingBalancePaise
            )
            val newStatus = if (isArithmeticValid) {
                StatementImportStatus.RECONCILED
            } else {
                StatementImportStatus.ARITHMETIC_ERROR
            }
            statementImportDao.update(importEntity.copy(status = newStatus))
            if (isArithmeticValid) {
                val period = (if (importEntity.periodStart > 0L) budgetPeriodDao.getPeriodForTimestamp(importEntity.periodStart) else null)
                    ?: (if (importEntity.periodEnd > 0L) budgetPeriodDao.getPeriodForTimestamp(importEntity.periodEnd) else null)
                    ?: budgetPeriodDao.getPeriodForTimestamp(importEntity.createdAt)
                period?.let { p ->
                    if (!p.isReviewed) {
                        budgetPeriodDao.update(p.copy(isReviewed = true))
                    }
                }
            }
        }
    }

    suspend fun updateImportBalances(importId: Long, openingPaise: Long, closingPaise: Long) {
        val importEntity = statementImportDao.getById(importId) ?: return
        val lines = statementLineDao.getLinesForImportSync(importId).toMutableList()

        // Reconcile line directions using running balances if present
        var prevBal = openingPaise
        for (i in lines.indices) {
            val line = lines[i]
            val bal = line.balanceAfterPaise
            if (bal != null) {
                val delta = bal - prevBal
                if (delta > 0L && line.debitPaise > 0L && line.creditPaise == 0L) {
                    val corrected = line.copy(debitPaise = 0L, creditPaise = delta)
                    statementLineDao.update(corrected)
                    lines[i] = corrected
                } else if (delta < 0L && line.creditPaise > 0L && line.debitPaise == 0L) {
                    val corrected = line.copy(debitPaise = -delta, creditPaise = 0L)
                    statementLineDao.update(corrected)
                    lines[i] = corrected
                }
                prevBal = bal
            }
        }

        val totalDebits = lines.sumOf { it.debitPaise }
        val totalCredits = lines.sumOf { it.creditPaise }

        val isArithmeticValid = StatementReconciliationEngine.verifyStatementArithmetic(
            openingBalancePaise = openingPaise,
            lines = lines,
            reportedClosingBalancePaise = closingPaise
        )
        val allHandled = lines.isNotEmpty() && lines.none { it.matchStatus == StatementMatchStatus.MISSING_IN_APP }
        val finalStatus = if (allHandled && isArithmeticValid) {
            StatementImportStatus.RECONCILED
        } else if (!isArithmeticValid) {
            StatementImportStatus.ARITHMETIC_ERROR
        } else {
            StatementImportStatus.IMPORTED
        }
        statementImportDao.update(
            importEntity.copy(
                openingBalancePaise = openingPaise,
                closingBalancePaise = closingPaise,
                totalDebitsPaise = totalDebits,
                totalCreditsPaise = totalCredits,
                status = finalStatus
            )
        )
        if (allHandled && isArithmeticValid) {
            val period = (if (importEntity.periodStart > 0L) budgetPeriodDao.getPeriodForTimestamp(importEntity.periodStart) else null)
                ?: (if (importEntity.periodEnd > 0L) budgetPeriodDao.getPeriodForTimestamp(importEntity.periodEnd) else null)
                ?: budgetPeriodDao.getPeriodForTimestamp(importEntity.createdAt)
            period?.let { p ->
                if (!p.isReviewed) {
                    budgetPeriodDao.update(p.copy(isReviewed = true))
                }
            }
        }
    }

    suspend fun getReconciliationProof(importId: Long): ReconciliationProof? {
        val importEntity = statementImportDao.getById(importId) ?: return null
        val account = accountDao.getById(importEntity.accountId) ?: return null
        val lines = statementLineDao.getLinesForImportSync(importId)
        val transactions = transactionDao.getTransactionsForAccountSync(importEntity.accountId)

        return StatementReconciliationEngine.computeReconciliationProof(
            account = account,
            statementImport = importEntity,
            statementLines = lines,
            transactions = transactions
        )
    }

    suspend fun deleteImport(importId: Long) = database.withTransaction {
        statementLineDao.deleteLinesForImport(importId)
        statementImportDao.deleteImport(importId)
    }
}
