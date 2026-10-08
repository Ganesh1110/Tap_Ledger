package com.tapledger.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.tapledger.core.database.entity.TransactionAllocationEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionAllocationDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(allocation: TransactionAllocationEntity): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(allocations: List<TransactionAllocationEntity>): List<Long>

    @Update
    suspend fun update(allocation: TransactionAllocationEntity)

    @Query("DELETE FROM transaction_allocations WHERE transaction_id = :transactionId")
    suspend fun deleteForTransaction(transactionId: Long)

    @Query("SELECT * FROM transaction_allocations WHERE transaction_id = :transactionId")
    suspend fun getForTransaction(transactionId: Long): List<TransactionAllocationEntity>

    @Query("SELECT * FROM transaction_allocations WHERE transaction_id = :transactionId")
    fun observeForTransaction(transactionId: Long): Flow<List<TransactionAllocationEntity>>

    @Query("""
        SELECT ta.* FROM transaction_allocations ta
        INNER JOIN transactions t ON ta.transaction_id = t.id
        WHERE t.occurred_at BETWEEN :startEpochMillis AND :endEpochMillis
          AND t.status IN ('PENDING', 'CATEGORIZED')
    """)
    suspend fun getAllocationsInPeriod(startEpochMillis: Long, endEpochMillis: Long): List<TransactionAllocationEntity>

    @Query("""
        SELECT ta.* FROM transaction_allocations ta
        INNER JOIN transactions t ON ta.transaction_id = t.id
        WHERE ta.category_id = :categoryId
          AND t.occurred_at BETWEEN :startEpochMillis AND :endEpochMillis
          AND t.status IN ('PENDING', 'CATEGORIZED')
    """)
    suspend fun getAllocationsForCategoryInPeriod(categoryId: Long, startEpochMillis: Long, endEpochMillis: Long): List<TransactionAllocationEntity>

    @Query("SELECT * FROM transaction_allocations")
    suspend fun getAllTransactionAllocationsSync(): List<TransactionAllocationEntity>
}
