package com.tapledger.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.tapledger.core.database.entity.CommitmentPaymentEntity
import com.tapledger.core.model.CommitmentPaymentStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface CommitmentPaymentDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(payment: CommitmentPaymentEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(payments: List<CommitmentPaymentEntity>): List<Long>

    @Update
    suspend fun update(payment: CommitmentPaymentEntity)

    @Query("SELECT * FROM commitment_payments WHERE id = :id")
    suspend fun getById(id: Long): CommitmentPaymentEntity?

    @Query("SELECT * FROM commitment_payments WHERE period_id = :periodId")
    fun getPaymentsForPeriodFlow(periodId: Long): Flow<List<CommitmentPaymentEntity>>

    @Query("SELECT * FROM commitment_payments WHERE period_id = :periodId")
    suspend fun getPaymentsForPeriodSync(periodId: Long): List<CommitmentPaymentEntity>

    @Query("SELECT * FROM commitment_payments WHERE commitment_id = :commitmentId AND period_id = :periodId LIMIT 1")
    suspend fun getPaymentForCommitmentAndPeriod(commitmentId: Long, periodId: Long): CommitmentPaymentEntity?

    @Query("UPDATE commitment_payments SET status = :status, paid_transaction_id = :transactionId WHERE commitment_id = :commitmentId AND period_id = :periodId")
    suspend fun markPaid(
        commitmentId: Long,
        periodId: Long,
        transactionId: Long,
        status: CommitmentPaymentStatus = CommitmentPaymentStatus.PAID
    )

    @Query("DELETE FROM commitment_payments WHERE period_id = :periodId")
    suspend fun deleteForPeriod(periodId: Long)
}
