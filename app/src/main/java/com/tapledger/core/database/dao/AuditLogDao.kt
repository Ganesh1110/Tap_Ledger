package com.tapledger.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.tapledger.core.database.entity.AuditLogEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AuditLogDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entry: AuditLogEntity): Long

    @Query("SELECT * FROM transaction_audit_log WHERE transaction_id = :transactionId ORDER BY created_at ASC")
    fun observeForTransaction(transactionId: Long): Flow<List<AuditLogEntity>>

    @Query("SELECT * FROM transaction_audit_log WHERE transaction_id = :transactionId ORDER BY created_at ASC")
    suspend fun getForTransactionSync(transactionId: Long): List<AuditLogEntity>

    @Query("SELECT * FROM transaction_audit_log ORDER BY created_at DESC LIMIT :limit")
    fun observeRecentAudits(limit: Int = 100): Flow<List<AuditLogEntity>>
}
