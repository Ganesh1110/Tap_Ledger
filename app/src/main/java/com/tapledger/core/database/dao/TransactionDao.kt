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

    @Query("SELECT * FROM transactions")
    suspend fun getAllTransactionsSync(): List<TransactionEntity>

    @Query("SELECT * FROM transactions WHERE status = 'PENDING' ORDER BY occurred_at DESC")
    fun observePendingTransactions(): Flow<List<TransactionEntity>>

    @Query("SELECT COUNT(*) FROM transactions WHERE status = 'PENDING'")
    fun observePendingCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM transactions WHERE status = 'PENDING'")
    suspend fun getPendingCountSync(): Int

    @Query("""
        SELECT * FROM transactions 
        WHERE account_id = :accountId 
          AND status IN ('PENDING', 'CONFIRMED', 'CATEGORIZED') 
          AND occurred_at >= :sinceEpochMillis
        ORDER BY occurred_at ASC
    """)
    suspend fun getBalanceTransactionsForAccount(accountId: Long, sinceEpochMillis: Long): List<TransactionEntity>

    @Query("""
        SELECT * FROM transactions 
        WHERE account_id = :accountId 
          AND status IN ('PENDING', 'CONFIRMED', 'CATEGORIZED') 
          AND occurred_at >= :sinceEpochMillis
        ORDER BY occurred_at ASC
    """)
    fun observeBalanceTransactionsForAccount(accountId: Long, sinceEpochMillis: Long): Flow<List<TransactionEntity>>

    @Query("""
        SELECT * FROM transactions 
        WHERE occurred_at >= :startEpochMillis AND occurred_at < :endEpochMillis
          AND status IN ('PENDING', 'CONFIRMED', 'CATEGORIZED')
        ORDER BY occurred_at DESC
    """)
    suspend fun getTransactionsInPeriod(startEpochMillis: Long, endEpochMillis: Long): List<TransactionEntity>

    @Query("""
        SELECT * FROM transactions 
        WHERE occurred_at >= :startEpochMillis AND occurred_at < :endEpochMillis
        ORDER BY occurred_at DESC
    """)
    fun observeTransactionsInPeriod(startEpochMillis: Long, endEpochMillis: Long): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE source_fingerprint = :fingerprint LIMIT 1")
    suspend fun findBySourceFingerprint(fingerprint: String): TransactionEntity?

    @Query("SELECT * FROM transactions WHERE upi_reference = :upiRef LIMIT 1")
    suspend fun findByUpiReference(upiRef: String): TransactionEntity?

    @Query("""
        SELECT * FROM transactions 
        WHERE account_id = :accountId 
          AND upi_reference = :upiRef 
          AND direction = :direction 
          AND amount_paise = :amountPaise 
        LIMIT 1
    """)
    suspend fun findByExactKey(
        accountId: Long,
        upiRef: String,
        direction: com.tapledger.core.model.TransactionDirection,
        amountPaise: Long
    ): TransactionEntity?

    @Query("""
        SELECT * FROM transactions 
        WHERE account_id IS NULL 
          AND upi_reference = :upiRef 
          AND direction = :direction 
          AND amount_paise = :amountPaise 
        LIMIT 1
    """)
    suspend fun findByExactKeyUnassigned(
        upiRef: String,
        direction: com.tapledger.core.model.TransactionDirection,
        amountPaise: Long
    ): TransactionEntity?

    @Query("""
        SELECT * FROM transactions 
        WHERE ((account_id = :accountId) OR (:accountId IS NULL AND account_id IS NULL))
          AND direction = :direction 
          AND amount_paise = :amountPaise 
          AND occurred_at BETWEEN :startWindow AND :endWindow
          AND status != 'VOIDED'
        ORDER BY occurred_at DESC
    """)
    suspend fun findFuzzyCandidates(
        accountId: Long?,
        direction: com.tapledger.core.model.TransactionDirection,
        amountPaise: Long,
        startWindow: Long,
        endWindow: Long
    ): List<TransactionEntity>

    @Query("""
        SELECT * FROM transactions 
        WHERE account_id != :excludeAccountId 
          AND transfer_group_id IS NULL 
          AND status = 'PENDING' 
          AND amount_paise = :amountPaise 
          AND direction = :oppositeDirection 
          AND occurred_at BETWEEN :startWindow AND :endWindow
          AND NOT EXISTS (
              SELECT 1 FROM budget_periods p 
              WHERE transactions.occurred_at >= p.start_at 
                AND transactions.occurred_at < p.end_at 
                AND p.status = 'CLOSED'
          )
        ORDER BY occurred_at ASC
    """)
    suspend fun findTransferPairCandidates(
        excludeAccountId: Long,
        oppositeDirection: com.tapledger.core.model.TransactionDirection,
        amountPaise: Long,
        startWindow: Long,
        endWindow: Long
    ): List<TransactionEntity>

    @Query("SELECT * FROM transactions WHERE transfer_group_id = :transferGroupId")
    suspend fun getTransferLegs(transferGroupId: String): List<TransactionEntity>

    @Query("SELECT * FROM transactions WHERE refund_of_transaction_id = :originalTransactionId")
    suspend fun getRefundsForTransaction(originalTransactionId: Long): List<TransactionEntity>

    @Query("SELECT * FROM transactions WHERE account_id = :accountId AND status != 'VOIDED'")
    suspend fun getTransactionsForAccountSync(accountId: Long): List<TransactionEntity>
}
