package com.tapledger.data.repository

import androidx.room.withTransaction
import com.tapledger.core.database.TapLedgerDatabase
import com.tapledger.core.database.dao.AccountDao
import com.tapledger.core.database.dao.StatementImportDao
import com.tapledger.core.database.dao.StatementLineDao
import com.tapledger.core.database.dao.TransactionDao
import com.tapledger.core.database.entity.StatementImportEntity
import com.tapledger.core.database.entity.StatementLineEntity
import com.tapledger.core.model.StatementImportStatus
import com.tapledger.core.model.StatementMatchStatus
import com.tapledger.core.model.TransactionSource
import com.tapledger.core.util.DateTimeUtils
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
            val importEntity = StatementImportEntity(
                accountId = accountId,
                periodStart = periodStart,
                periodEnd = periodEnd,
                format = format,
                openingBalancePaise = openingBalancePaise,
                closingBalancePaise = closingBalancePaise,
                status = StatementImportStatus.IMPORTED,
                createdAt = now
            )
            val importId = statementImportDao.insert(importEntity)

            // 1. Parse CSV lines
            val rawLines = StatementCsvParser.parseCsv(csvContent, importId, now)
            if (rawLines.isEmpty()) {
                return@withTransaction importId
            }

            // 2. Fetch existing app transactions for the account
            val appTransactions = transactionDao.getTransactionsForAccountSync(accountId)

            // 3. Match statement lines with app transactions
            val matchedLines = StatementReconciliationEngine.matchStatementLines(rawLines, appTransactions)

            // 4. Save matched lines
            statementLineDao.insertAll(matchedLines)

            // 5. Update import status
            val allMatched = matchedLines.all { it.matchStatus == StatementMatchStatus.MATCHED || it.matchStatus == StatementMatchStatus.RESOLVED }
            val isArithmeticValid = StatementReconciliationEngine.verifyStatementArithmetic(
                openingBalancePaise, matchedLines, closingBalancePaise
            )

            val updatedStatus = when {
                allMatched && isArithmeticValid -> StatementImportStatus.RECONCILED
                else -> StatementImportStatus.IMPORTED
            }

            statementImportDao.update(importEntity.copy(status = updatedStatus))

            importId
        }
    }

    suspend fun createAppTransactionFromLine(
        line: StatementLineEntity,
        accountId: Long,
        categoryId: Long
    ) {
        val isDebit = line.debitPaise > 0
        val amountPaise = if (isDebit) line.debitPaise else line.creditPaise

        database.withTransaction {
            val txId = if (isDebit) {
                transactionRepository.recordExpense(
                    accountId = accountId,
                    amountPaise = amountPaise,
                    occurredAt = line.transactionDate,
                    merchantRaw = line.description,
                    upiReference = line.reference,
                    source = TransactionSource.STATEMENT,
                    allocations = listOf(categoryId to amountPaise)
                )
            } else {
                transactionRepository.recordIncome(
                    accountId = accountId,
                    amountPaise = amountPaise,
                    occurredAt = line.transactionDate,
                    merchantRaw = line.description,
                    upiReference = line.reference,
                    source = TransactionSource.STATEMENT
                )
            }

            val updatedLine = line.copy(
                matchedTransactionId = txId,
                matchStatus = StatementMatchStatus.MATCHED
            )
            statementLineDao.update(updatedLine)
        }
    }

    suspend fun resolveLine(line: StatementLineEntity) {
        val updated = line.copy(matchStatus = StatementMatchStatus.RESOLVED)
        statementLineDao.update(updated)
    }
}
