package com.tapledger.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.tapledger.core.database.entity.StatementLineEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface StatementLineDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(lines: List<StatementLineEntity>): List<Long>

    @Update
    suspend fun update(line: StatementLineEntity)

    @Query("SELECT * FROM statement_lines WHERE statement_import_id = :importId ORDER BY line_number ASC")
    fun observeLinesForImport(importId: Long): Flow<List<StatementLineEntity>>

    @Query("SELECT * FROM statement_lines WHERE statement_import_id = :importId ORDER BY line_number ASC")
    suspend fun getLinesForImportSync(importId: Long): List<StatementLineEntity>

    @Query("SELECT * FROM statement_lines WHERE matched_transaction_id = :transactionId LIMIT 1")
    suspend fun findByMatchedTransaction(transactionId: Long): StatementLineEntity?

    @Query("""
        SELECT COUNT(*) FROM statement_lines 
        WHERE statement_import_id = :importId AND match_status != 'MATCHED' AND match_status != 'RESOLVED'
    """)
    suspend fun getUnresolvedCount(importId: Long): Int
}
