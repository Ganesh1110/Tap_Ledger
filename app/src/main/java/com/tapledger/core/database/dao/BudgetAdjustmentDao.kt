package com.tapledger.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.tapledger.core.database.entity.BudgetAdjustmentEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BudgetAdjustmentDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(adjustment: BudgetAdjustmentEntity): Long

    @Query("SELECT * FROM budget_adjustments WHERE budget_period_id = :periodId ORDER BY created_at ASC")
    fun observeAdjustmentsForPeriod(periodId: Long): Flow<List<BudgetAdjustmentEntity>>

    @Query("SELECT * FROM budget_adjustments WHERE budget_period_id = :periodId ORDER BY created_at ASC")
    suspend fun getAdjustmentsForPeriodSync(periodId: Long): List<BudgetAdjustmentEntity>

    @Query("SELECT * FROM budget_adjustments")
    suspend fun getAllAdjustmentsSync(): List<BudgetAdjustmentEntity>
}
