package com.tapledger.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.tapledger.core.database.entity.AdvanceLedgerEntity
import com.tapledger.core.database.entity.BudgetAdjustmentEntity
import com.tapledger.core.database.entity.BudgetAllocationEntity
import com.tapledger.core.model.AdvanceDirection
import kotlinx.coroutines.flow.Flow

@Dao
abstract class BudgetAllocationDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    protected abstract suspend fun insertInternal(allocation: BudgetAllocationEntity): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    protected abstract suspend fun insertAllInternal(allocations: List<BudgetAllocationEntity>): List<Long>

    @Update
    protected abstract suspend fun updateInternal(allocation: BudgetAllocationEntity)

    @Query("SELECT * FROM budget_adjustments WHERE budget_period_id = :periodId")
    abstract suspend fun getAdjustmentsForPeriod(periodId: Long): List<BudgetAdjustmentEntity>

    @Query("SELECT * FROM advance_ledger WHERE applied_to_period_id = :periodId")
    abstract suspend fun getAdvancesForPeriod(periodId: Long): List<AdvanceLedgerEntity>

    /**
     * Enforces Flow.md §8.2 DAO Invariant Guard (Rule B2 Protection):
     * BaseAllocated(C) >= AdvanceNegative(C) + AdjOut(C) - AdjIn(C) - AdvancePositive(C)
     * Any write violating this boundary is rejected with IllegalArgumentException.
     */
    suspend fun verifyDaoGuard(allocation: BudgetAllocationEntity) {
        val adjustments = getAdjustmentsForPeriod(allocation.budgetPeriodId)
        val advances = getAdvancesForPeriod(allocation.budgetPeriodId)

        val catId = allocation.categoryId
        val adjOut = adjustments.filter { it.fromCategoryId == catId }.sumOf { it.amountPaise }
        val adjIn = adjustments.filter { it.toCategoryId == catId }.sumOf { it.amountPaise }
        val advPos = advances.filter { it.categoryId == catId && it.direction == AdvanceDirection.POSITIVE }.sumOf { it.amountPaise }
        val advNeg = advances.filter { it.categoryId == catId && it.direction == AdvanceDirection.NEGATIVE }.sumOf { it.amountPaise }

        val minRequiredBase = advNeg + adjOut - adjIn - advPos
        if (allocation.amountPaise < minRequiredBase) {
            throw IllegalArgumentException(
                "Base allocation (${allocation.amountPaise} paise) cannot be reduced below committed net negative adjustments ($minRequiredBase paise) for category #$catId"
            )
        }
    }

    @Transaction
    open suspend fun insert(allocation: BudgetAllocationEntity): Long {
        verifyDaoGuard(allocation)
        return insertInternal(allocation)
    }

    @Transaction
    open suspend fun insertAll(allocations: List<BudgetAllocationEntity>): List<Long> {
        for (alloc in allocations) {
            verifyDaoGuard(alloc)
        }
        return insertAllInternal(allocations)
    }

    @Transaction
    open suspend fun update(allocation: BudgetAllocationEntity) {
        verifyDaoGuard(allocation)
        updateInternal(allocation)
    }

    @Query("SELECT * FROM budget_allocations WHERE budget_period_id = :periodId ORDER BY sort_order_snapshot ASC, category_name_snapshot ASC")
    abstract fun observeAllocationsForPeriod(periodId: Long): Flow<List<BudgetAllocationEntity>>

    @Query("SELECT * FROM budget_allocations WHERE budget_period_id = :periodId ORDER BY sort_order_snapshot ASC, category_name_snapshot ASC")
    abstract suspend fun getAllocationsForPeriodSync(periodId: Long): List<BudgetAllocationEntity>

    @Query("SELECT * FROM budget_allocations")
    abstract suspend fun getAllAllocationsSync(): List<BudgetAllocationEntity>

    @Query("SELECT * FROM budget_allocations WHERE budget_period_id = :periodId AND category_id = :categoryId LIMIT 1")
    abstract suspend fun getAllocation(periodId: Long, categoryId: Long): BudgetAllocationEntity?
}
