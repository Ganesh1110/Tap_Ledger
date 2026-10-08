package com.tapledger.data.repository

import com.tapledger.core.database.dao.AccountDao
import com.tapledger.core.database.dao.AuditLogDao
import com.tapledger.core.database.dao.BudgetAllocationDao
import com.tapledger.core.database.dao.BudgetPeriodDao
import com.tapledger.core.database.dao.CategoryDao
import com.tapledger.core.database.dao.MonthCloseDao
import com.tapledger.core.database.dao.TransactionAllocationDao
import com.tapledger.core.database.dao.TransactionDao
import com.tapledger.core.database.entity.AuditLogEntity
import com.tapledger.domain.model.IntegrityIssue
import com.tapledger.domain.validator.DataIntegrityChecker
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DiagnosticsRepository @Inject constructor(
    private val accountDao: AccountDao,
    private val categoryDao: CategoryDao,
    private val budgetPeriodDao: BudgetPeriodDao,
    private val budgetAllocationDao: BudgetAllocationDao,
    private val transactionDao: TransactionDao,
    private val transactionAllocationDao: TransactionAllocationDao,
    private val monthCloseDao: MonthCloseDao,
    private val auditLogDao: AuditLogDao
) {

    fun observeAuditLogs(limit: Int = 100): Flow<List<AuditLogEntity>> =
        auditLogDao.observeRecentAudits(limit)

    suspend fun runIntegrityAudit(): List<IntegrityIssue> {
        val accounts = accountDao.getAllAccountsSync()
        val categories = categoryDao.getAllCategoriesSync()
        val periods = budgetPeriodDao.getAllPeriodsSync()
        val allocations = budgetAllocationDao.getAllAllocationsSync()
        val transactions = transactionDao.getAllTransactionsSync()
        val txAllocations = transactionAllocationDao.getAllTransactionAllocationsSync()
        val monthCloses = monthCloseDao.getAllMonthClosesSync()

        val checker = DataIntegrityChecker()
        return checker.verifyIntegrity(
            accounts = accounts,
            categories = categories,
            periods = periods,
            allocations = allocations,
            transactions = transactions,
            transactionAllocations = txAllocations,
            monthCloses = monthCloses
        )
    }
}
