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
    ): Long {
        val now = DateTimeUtils.nowEpochMillis()

        return database.withTransaction {
            val dummyImportId = 0L
            val rawLines = StatementCsvParser.parseCsv(csvContent, dummyImportId, now)
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

            txId
        }
    }

    suspend fun resolveLine(line: StatementLineEntity) {
        val updated = line.copy(matchStatus = StatementMatchStatus.RESOLVED)
        statementLineDao.update(updated)
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
}
