package com.tapledger.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.tapledger.core.database.entity.SavingsGoalEntity
import com.tapledger.core.model.SavingsGoalStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface SavingsGoalDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(goal: SavingsGoalEntity): Long

    @Update
    suspend fun update(goal: SavingsGoalEntity)

    @Query("SELECT * FROM savings_goals WHERE id = :id")
    suspend fun getById(id: Long): SavingsGoalEntity?

    @Query("SELECT * FROM savings_goals WHERE id = :id")
    fun getByIdFlow(id: Long): Flow<SavingsGoalEntity?>

    @Query("SELECT * FROM savings_goals WHERE status = 'ACTIVE' ORDER BY priority ASC, created_at ASC")
    fun getAllActiveGoals(): Flow<List<SavingsGoalEntity>>

    @Query("SELECT * FROM savings_goals WHERE status != 'ARCHIVED' ORDER BY priority ASC, created_at ASC")
    fun getAllNonArchivedGoals(): Flow<List<SavingsGoalEntity>>

    @Query("SELECT * FROM savings_goals ORDER BY priority ASC, created_at ASC")
    suspend fun getAllGoalsSync(): List<SavingsGoalEntity>

    @Query("SELECT * FROM savings_goals WHERE linked_account_id = :accountId AND status != 'ARCHIVED'")
    suspend fun getGoalsByAccount(accountId: Long): List<SavingsGoalEntity>

    @Query("UPDATE savings_goals SET status = :status, updated_at = :updatedAt WHERE id = :id")
    suspend fun updateStatus(id: Long, status: SavingsGoalStatus, updatedAt: Long)
}
