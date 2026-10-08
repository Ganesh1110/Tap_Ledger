package com.tapledger.data.repository

import com.tapledger.core.database.dao.BudgetAllocationDao
import com.tapledger.core.database.dao.BudgetPeriodDao
import com.tapledger.core.database.dao.BudgetAdjustmentDao
import com.tapledger.core.database.dao.MonthCloseDao
import com.tapledger.core.database.dao.TransactionAllocationDao
import com.tapledger.core.database.dao.TransactionDao
import com.tapledger.core.database.entity.MonthCloseEntity
import com.tapledger.domain.calculator.BudgetCalculator
import com.tapledger.domain.model.BudgetPeriodSummary
import com.tapledger.domain.monthclose.MonthCloseEngine
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MonthCloseRepository @Inject constructor(
    private val monthCloseEngine: MonthCloseEngine,
    private val budgetPeriodDao: BudgetPeriodDao,
    private val budgetAllocationDao: BudgetAllocationDao,
    private val budgetAdjustmentDao: BudgetAdjustmentDao,
    private val transactionDao: TransactionDao,
    private val transactionAllocationDao: TransactionAllocationDao,
    private val monthCloseDao: MonthCloseDao
) {

    fun observeMonthCloseForPeriod(periodId: Long): Flow<MonthCloseEntity?> =
        monthCloseDao.observeByPeriodId(periodId)

    suspend fun getPeriodSummary(year: Int, month: Int): BudgetPeriodSummary? {
        val period = budgetPeriodDao.getByYearMonth(year, month) ?: return null
        val allocations = budgetAllocationDao.getAllocationsForPeriodSync(period.id)
        val adjustments = budgetAdjustmentDao.getAdjustmentsForPeriodSync(period.id)
        val transactions = transactionDao.getTransactionsInPeriod(period.startAt, period.endAt)
        val txAllocations = transactionAllocationDao.getAllocationsInPeriod(period.startAt, period.endAt)

        return BudgetCalculator.calculateBudgetPeriodSummary(
            period = period,
            allocations = allocations,
            adjustments = adjustments,
            transactions = transactions,
            allocationsForTransactions = txAllocations
        )
    }

    suspend fun closeMonth(
        summary: BudgetPeriodSummary,
        reconciliationStatus: String = "RECONCILED",
        overrideReason: String? = null
    ): Long = monthCloseEngine.closeMonth(summary, reconciliationStatus, overrideReason)

    suspend fun reopenMonth(periodId: Long) = monthCloseEngine.reopenMonth(periodId)
}
