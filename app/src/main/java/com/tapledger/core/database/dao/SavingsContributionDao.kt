package com.tapledger.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.tapledger.core.database.entity.SavingsContributionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SavingsContributionDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(contribution: SavingsContributionEntity): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(contributions: List<SavingsContributionEntity>): List<Long>

    @Query("SELECT * FROM savings_contributions WHERE id = :id")
    suspend fun getById(id: Long): SavingsContributionEntity?

    @Query("SELECT * FROM savings_contributions WHERE goal_id = :goalId ORDER BY created_at DESC")
    fun getContributionsByGoal(goalId: Long): Flow<List<SavingsContributionEntity>>

    @Query("SELECT * FROM savings_contributions WHERE goal_id = :goalId ORDER BY created_at DESC")
    suspend fun getContributionsByGoalSync(goalId: Long): List<SavingsContributionEntity>

    @Query("SELECT COALESCE(SUM(amount_paise), 0) FROM savings_contributions WHERE goal_id = :goalId")
    fun getGoalBalanceFlow(goalId: Long): Flow<Long>

    @Query("SELECT COALESCE(SUM(amount_paise), 0) FROM savings_contributions WHERE goal_id = :goalId")
    suspend fun getGoalBalance(goalId: Long): Long

    @Query("SELECT COALESCE(SUM(c.amount_paise), 0) FROM savings_contributions c INNER JOIN savings_goals g ON c.goal_id = g.id WHERE g.status != 'ARCHIVED'")
    fun getTotalSavedFlow(): Flow<Long>

    @Query("SELECT COALESCE(SUM(c.amount_paise), 0) FROM savings_contributions c INNER JOIN savings_goals g ON c.goal_id = g.id WHERE g.status != 'ARCHIVED'")
    suspend fun getTotalSavedSync(): Long

    @Query("SELECT * FROM savings_contributions ORDER BY created_at DESC")
    suspend fun getAllContributionsSync(): List<SavingsContributionEntity>
}
