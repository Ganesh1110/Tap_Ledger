package com.tapledger.core.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.tapledger.core.database.entity.LoanEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface LoanDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(loan: LoanEntity): Long

    @Update
    suspend fun update(loan: LoanEntity)

    @Delete
    suspend fun delete(loan: LoanEntity)

    @Query("SELECT * FROM loans WHERE id = :id")
    suspend fun getById(id: Long): LoanEntity?

    @Query("SELECT * FROM loans WHERE account_id = :accountId LIMIT 1")
    suspend fun getByAccountId(accountId: Long): LoanEntity?

    @Query("SELECT * FROM loans WHERE is_active = 1")
    suspend fun getAllActiveSync(): List<LoanEntity>

    @Query("SELECT * FROM loans WHERE is_active = 1 ORDER BY id DESC")
    fun observeAllActive(): Flow<List<LoanEntity>>

    @Query("SELECT * FROM loans ORDER BY id DESC")
    fun observeAll(): Flow<List<LoanEntity>>

    @Query("SELECT * FROM loans")
    suspend fun getAllSync(): List<LoanEntity>
}
