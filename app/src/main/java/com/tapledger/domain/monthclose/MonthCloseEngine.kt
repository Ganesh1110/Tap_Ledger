package com.tapledger.domain.monthclose

import androidx.room.withTransaction
import com.tapledger.core.database.TapLedgerDatabase
import com.tapledger.core.database.dao.AdvanceLedgerDao
import com.tapledger.core.database.dao.BudgetPeriodDao
import com.tapledger.core.database.dao.MonthCloseDao
import com.tapledger.core.database.entity.AdvanceLedgerEntity
import com.tapledger.core.database.entity.MonthCloseEntity
import com.tapledger.core.model.AdvanceDirection
import com.tapledger.core.model.AdvancePolicy
import com.tapledger.core.model.BudgetPeriodStatus
import com.tapledger.core.util.DateTimeUtils
import com.tapledger.domain.model.BudgetPeriodSummary
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MonthCloseEngine @Inject constructor(
    private val database: TapLedgerDatabase,
    private val budgetPeriodDao: BudgetPeriodDao,
    private val monthCloseDao: MonthCloseDao,
    private val advanceLedgerDao: AdvanceLedgerDao
) {

    /**
     * Closes a budget month atomically according to configured category policies (Section 27 & 28).
     * Locks the period and writes MonthCloseEntity and AdvanceLedger records.
     */
    suspend fun closeMonth(
        summary: BudgetPeriodSummary,
        reconciliationStatus: String = "RECONCILED",
        overrideReason: String? = null
    ): Long {
        val period = summary.period
        val now = DateTimeUtils.nowEpochMillis()

        return database.withTransaction {
            // 1. Process category policies & overspend for each category
            val advanceEntries = mutableListOf<AdvanceLedgerEntity>()

            for (catSummary in summary.categorySummaries) {
                val remaining = catSummary.remainingPaise
                val policy = catSummary.allocation.advancePolicySnapshot

                if (remaining < 0L) {
                    // Section 28: Overspending must NEVER disappear! Recorded as negative advance.
                    advanceEntries.add(
                        AdvanceLedgerEntity(
                            budgetPeriodId = period.id,
                            categoryId = catSummary.allocation.categoryId,
                            amountPaise = -remaining,
                            direction = AdvanceDirection.NEGATIVE,
                            reason = "Overspending at month close",
                            createdAt = now
                        )
                    )
                } else if (remaining > 0L) {
                    when (policy) {
                        AdvancePolicy.CARRY_TO_ADVANCE -> {
                            advanceEntries.add(
                                AdvanceLedgerEntity(
                                    budgetPeriodId = period.id,
                                    categoryId = catSummary.allocation.categoryId,
                                    amountPaise = remaining,
                                    direction = AdvanceDirection.POSITIVE,
                                    reason = "Unused budget carry-forward",
                                    createdAt = now
                                )
                            )
                        }
                        AdvancePolicy.SWEEP_TO_SAVINGS -> {
                            // Section 27: Logical budget operation assigning to savings pool
                            advanceEntries.add(
                                AdvanceLedgerEntity(
                                    budgetPeriodId = period.id,
                                    categoryId = catSummary.allocation.categoryId,
                                    amountPaise = remaining,
                                    direction = AdvanceDirection.POSITIVE,
                                    reason = "Sweep to savings budget operation",
                                    createdAt = now
                                )
                            )
                        }
                        AdvancePolicy.EXPIRE -> {
                            // Budget expires without advance entry
                        }
                    }
                }
            }

            if (advanceEntries.isNotEmpty()) {
                advanceLedgerDao.insertAll(advanceEntries)
            }

            // 2. Lock budget period status (Section 57)
            budgetPeriodDao.update(
                period.copy(
                    status = BudgetPeriodStatus.CLOSED,
                    updatedAt = now
                )
            )

            // 3. Record month close
            val monthClose = MonthCloseEntity(
                budgetPeriodId = period.id,
                closedAt = now,
                closedBy = "USER",
                reconciliationStatus = reconciliationStatus,
                overrideReason = overrideReason,
                createdAt = now
            )
            monthCloseDao.insert(monthClose)
        }
    }

    /**
     * Reopens a closed month with explicit user action (Section 58).
     */
    suspend fun reopenMonth(periodId: Long) {
        val now = DateTimeUtils.nowEpochMillis()
        database.withTransaction {
            val period = budgetPeriodDao.getById(periodId)
                ?: throw IllegalArgumentException("Budget period #$periodId not found")

            monthCloseDao.deleteForPeriod(periodId)
            budgetPeriodDao.update(
                period.copy(
                    status = BudgetPeriodStatus.OPEN,
                    updatedAt = now
                )
            )
        }
    }
}
