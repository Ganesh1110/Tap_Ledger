package com.tapledger.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.tapledger.core.database.entity.TransactionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(transaction: TransactionEntity): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(transactions: List<TransactionEntity>): List<Long>

    @Update
    suspend fun update(transaction: TransactionEntity)

    @Query("SELECT * FROM transactions WHERE id = :id")
    suspend fun getById(id: Long): TransactionEntity?

    @Query("SELECT * FROM transactions WHERE id = :id")
    fun observeById(id: Long): Flow<TransactionEntity?>

    @Query("SELECT * FROM transactions ORDER BY occurred_at DESC, id DESC")
    fun observeAllTransactions(): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE status = 'PENDING' ORDER BY occurred_at DESC")
    fun observePendingTransactions(): Flow<List<TransactionEntity>>

    @Query("SELECT COUNT(*) FROM transactions WHERE status = 'PENDING'")
    fun observePendingCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM transactions WHERE status = 'PENDING'")
    suspend fun getPendingCountSync(): Int

    @Query("""
        SELECT * FROM transactions 
        WHERE account_id = :accountId 
          AND status IN ('PENDING', 'CATEGORIZED') 
          AND occurred_at >= :sinceEpochMillis
        ORDER BY occurred_at ASC
    """)
    suspend fun getBalanceTransactionsForAccount(accountId: Long, sinceEpochMillis: Long): List<TransactionEntity>

    @Query("""
        SELECT * FROM transactions 
        WHERE account_id = :accountId 
          AND status IN ('PENDING', 'CATEGORIZED') 
          AND occurred_at >= :sinceEpochMillis
        ORDER BY occurred_at ASC
    """)
    fun observeBalanceTransactionsForAccount(accountId: Long, sinceEpochMillis: Long): Flow<List<TransactionEntity>>

    @Query("""
        SELECT * FROM transactions 
        WHERE occurred_at BETWEEN :startEpochMillis AND :endEpochMillis
          AND status IN ('PENDING', 'CATEGORIZED')
        ORDER BY occurred_at DESC
    """)
    suspend fun getTransactionsInPeriod(startEpochMillis: Long, endEpochMillis: Long): List<TransactionEntity>

    @Query("""
        SELECT * FROM transactions 
        WHERE occurred_at BETWEEN :startEpochMillis AND :endEpochMillis
        ORDER BY occurred_at DESC
    """)
    fun observeTransactionsInPeriod(startEpochMillis: Long, endEpochMillis: Long): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE source_fingerprint = :fingerprint LIMIT 1")
    suspend fun findBySourceFingerprint(fingerprint: String): TransactionEntity?

    @Query("SELECT * FROM transactions WHERE upi_reference = :upiRef LIMIT 1")
    suspend fun findByUpiReference(upiRef: String): TransactionEntity?

    @Query("""
        SELECT * FROM transactions 
        WHERE amount_paise = :amountPaise 
          AND occurred_at BETWEEN :startWindow AND :endWindow
          AND source != :excludeSource
          AND status != 'VOIDED'
        ORDER BY occurred_at ASC
    """)
    suspend fun findCrossSourceCandidates(
        amountPaise: Long,
        startWindow: Long,
        endWindow: Long,
        excludeSource: String
    ): List<TransactionEntity>

    @Query("SELECT * FROM transactions WHERE transfer_group_id = :transferGroupId")
    suspend fun getTransferLegs(transferGroupId: Long): List<TransactionEntity>

    @Query("SELECT * FROM transactions WHERE refund_of_transaction_id = :originalTransactionId")
    suspend fun getRefundsForTransaction(originalTransactionId: Long): List<TransactionEntity>
}
