package com.tapledger.core.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.tapledger.core.database.entity.SavingsTransferPlanEntity
import com.tapledger.core.model.TransferPlanKind
import com.tapledger.core.model.TransferPlanStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface SavingsTransferPlanDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(plan: SavingsTransferPlanEntity): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(plan: SavingsTransferPlanEntity): Long

    @Update
    suspend fun update(plan: SavingsTransferPlanEntity)

    @Delete
    suspend fun delete(plan: SavingsTransferPlanEntity)

    @Query("SELECT * FROM savings_transfer_plans WHERE id = :id")
    suspend fun getById(id: Long): SavingsTransferPlanEntity?

    @Query("SELECT * FROM savings_transfer_plans WHERE id = :id")
    fun getByIdFlow(id: Long): Flow<SavingsTransferPlanEntity?>

    @Query("SELECT * FROM savings_transfer_plans WHERE period_id = :periodId AND kind = :kind")
    suspend fun getByPeriodAndKind(periodId: Long, kind: TransferPlanKind): SavingsTransferPlanEntity?

    @Query("SELECT * FROM savings_transfer_plans WHERE period_id = :periodId AND kind = :kind")
    fun getByPeriodAndKindFlow(periodId: Long, kind: TransferPlanKind): Flow<SavingsTransferPlanEntity?>

    @Query("SELECT * FROM savings_transfer_plans WHERE period_id = :periodId ORDER BY created_at DESC")
    suspend fun getPlansForPeriod(periodId: Long): List<SavingsTransferPlanEntity>

    @Query("SELECT * FROM savings_transfer_plans WHERE period_id = :periodId ORDER BY created_at DESC")
    fun getPlansForPeriodFlow(periodId: Long): Flow<List<SavingsTransferPlanEntity>>

    @Query("SELECT * FROM savings_transfer_plans WHERE period_id = :periodId AND status IN ('SUGGESTED', 'MARKED_MOVED', 'VERIFIED') ORDER BY created_at DESC")
    fun getActivePlansForPeriodFlow(periodId: Long): Flow<List<SavingsTransferPlanEntity>>

    @Query("SELECT * FROM savings_transfer_plans WHERE status = :status ORDER BY created_at DESC")
    suspend fun getPlansByStatus(status: TransferPlanStatus): List<SavingsTransferPlanEntity>

    @Query("SELECT * FROM savings_transfer_plans WHERE status = :status ORDER BY created_at DESC")
    fun getPlansByStatusFlow(status: TransferPlanStatus): Flow<List<SavingsTransferPlanEntity>>

    @Query("SELECT * FROM savings_transfer_plans WHERE transfer_group_id = :transferGroupId LIMIT 1")
    suspend fun getByTransferGroupId(transferGroupId: String): SavingsTransferPlanEntity?

    @Query("SELECT * FROM savings_transfer_plans WHERE status = 'MARKED_MOVED'")
    suspend fun findMarkedMovedPlans(): List<SavingsTransferPlanEntity>

    @Query("SELECT * FROM savings_transfer_plans WHERE status = 'MARKED_MOVED'")
    fun findMarkedMovedPlansFlow(): Flow<List<SavingsTransferPlanEntity>>

    @Query("UPDATE savings_transfer_plans SET status = 'EXPIRED' WHERE period_id = :periodId AND status = 'SUGGESTED'")
    suspend fun expireSuggestedPlansForPeriod(periodId: Long)
}
