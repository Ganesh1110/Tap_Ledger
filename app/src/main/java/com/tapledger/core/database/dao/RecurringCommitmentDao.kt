package com.tapledger.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.tapledger.core.database.entity.RecurringCommitmentEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RecurringCommitmentDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(commitment: RecurringCommitmentEntity): Long

    @Update
    suspend fun update(commitment: RecurringCommitmentEntity)

    @Query("SELECT * FROM recurring_commitments WHERE id = :id")
    suspend fun getById(id: Long): RecurringCommitmentEntity?

    @Query("SELECT * FROM recurring_commitments WHERE id = :id")
    fun getByIdFlow(id: Long): Flow<RecurringCommitmentEntity?>

    @Query("SELECT * FROM recurring_commitments WHERE active = 1 ORDER BY due_day ASC, name ASC")
    fun getAllActiveFlow(): Flow<List<RecurringCommitmentEntity>>

    @Query("SELECT * FROM recurring_commitments WHERE active = 1 ORDER BY due_day ASC, name ASC")
    suspend fun getAllActiveSync(): List<RecurringCommitmentEntity>

    @Query("SELECT * FROM recurring_commitments ORDER BY due_day ASC, name ASC")
    fun getAllFlow(): Flow<List<RecurringCommitmentEntity>>

    @Query("SELECT * FROM recurring_commitments ORDER BY due_day ASC, name ASC")
    suspend fun getAllSync(): List<RecurringCommitmentEntity>

    @Query("UPDATE recurring_commitments SET active = :active WHERE id = :id")
    suspend fun updateActiveStatus(id: Long, active: Boolean)

    @Query("DELETE FROM recurring_commitments WHERE id = :id")
    suspend fun delete(id: Long)
}
