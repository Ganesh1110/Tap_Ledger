package com.tapledger.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.tapledger.core.database.entity.FixedDepositEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FixedDepositDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(fd: FixedDepositEntity): Long

    @Update
    suspend fun update(fd: FixedDepositEntity)

    @Query("SELECT * FROM fixed_deposits WHERE id = :id")
    suspend fun getById(id: Long): FixedDepositEntity?

    @Query("SELECT * FROM fixed_deposits WHERE id = :id")
    fun getByIdFlow(id: Long): Flow<FixedDepositEntity?>

    @Query("SELECT * FROM fixed_deposits ORDER BY maturity_ms ASC")
    fun getAllFlow(): Flow<List<FixedDepositEntity>>

    @Query("SELECT * FROM fixed_deposits ORDER BY maturity_ms ASC")
    suspend fun getAllSync(): List<FixedDepositEntity>

    @Query("SELECT * FROM fixed_deposits WHERE goal_id = :goalId ORDER BY maturity_ms ASC")
    fun getByGoalFlow(goalId: Long): Flow<List<FixedDepositEntity>>

    @Query("SELECT * FROM fixed_deposits WHERE goal_id = :goalId ORDER BY maturity_ms ASC")
    suspend fun getByGoalSync(goalId: Long): List<FixedDepositEntity>

    @Query("DELETE FROM fixed_deposits WHERE id = :id")
    suspend fun delete(id: Long)
}
