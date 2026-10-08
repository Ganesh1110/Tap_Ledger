package com.tapledger.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.tapledger.core.database.entity.BudgetAllocationEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BudgetAllocationDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(allocation: BudgetAllocationEntity): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(allocations: List<BudgetAllocationEntity>): List<Long>

    @Update
    suspend fun update(allocation: BudgetAllocationEntity)

    @Query("SELECT * FROM budget_allocations WHERE budget_period_id = :periodId ORDER BY sort_order_snapshot ASC, category_name_snapshot ASC")
    fun observeAllocationsForPeriod(periodId: Long): Flow<List<BudgetAllocationEntity>>

    @Query("SELECT * FROM budget_allocations WHERE budget_period_id = :periodId ORDER BY sort_order_snapshot ASC, category_name_snapshot ASC")
    suspend fun getAllocationsForPeriodSync(periodId: Long): List<BudgetAllocationEntity>

    @Query("SELECT * FROM budget_allocations")
    suspend fun getAllAllocationsSync(): List<BudgetAllocationEntity>

    @Query("SELECT * FROM budget_allocations WHERE budget_period_id = :periodId AND category_id = :categoryId LIMIT 1")
    suspend fun getAllocation(periodId: Long, categoryId: Long): BudgetAllocationEntity?
}
