package com.tapledger.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.tapledger.core.database.entity.StatementImportEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface StatementImportDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(statementImport: StatementImportEntity): Long

    @Update
    suspend fun update(statementImport: StatementImportEntity)

    @Query("SELECT * FROM statement_imports WHERE id = :id")
    suspend fun getById(id: Long): StatementImportEntity?

    @Query("SELECT * FROM statement_imports ORDER BY period_end DESC")
    fun observeAll(): Flow<List<StatementImportEntity>>
}
