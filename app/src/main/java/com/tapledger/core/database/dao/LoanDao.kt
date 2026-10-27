package com.tapledger.core.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.tapledger.core.database.entity.LoanEntity
import com.tapledger.core.database.entity.LoanScheduleInstallmentEntity
import com.tapledger.core.database.entity.LoanScheduleVersionEntity
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

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertScheduleVersion(version: LoanScheduleVersionEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertScheduleInstallments(installments: List<LoanScheduleInstallmentEntity>)

    @Query("SELECT * FROM loan_schedule_versions WHERE loan_id = :loanId ORDER BY version_number ASC")
    suspend fun getScheduleVersionsForLoan(loanId: Long): List<LoanScheduleVersionEntity>

    @Query("SELECT * FROM loan_schedule_versions WHERE loan_id = :loanId ORDER BY version_number ASC")
    fun observeScheduleVersionsForLoan(loanId: Long): Flow<List<LoanScheduleVersionEntity>>

    @Query("SELECT * FROM loan_schedule_versions WHERE loan_id = :loanId ORDER BY version_number DESC LIMIT 1")
    suspend fun getLatestScheduleVersion(loanId: Long): LoanScheduleVersionEntity?

    @Query("SELECT * FROM loan_schedule_installments WHERE schedule_version_id = :versionId ORDER BY month_index ASC")
    suspend fun getInstallmentsForVersion(versionId: Long): List<LoanScheduleInstallmentEntity>

    @Query("SELECT * FROM loan_schedule_installments WHERE schedule_version_id = :versionId ORDER BY month_index ASC")
    fun observeInstallmentsForVersion(versionId: Long): Flow<List<LoanScheduleInstallmentEntity>>

    @Query("DELETE FROM loan_schedule_installments WHERE schedule_version_id IN (SELECT id FROM loan_schedule_versions WHERE loan_id = :loanId)")
    suspend fun deleteInstallmentsByLoanId(loanId: Long)

    @Query("DELETE FROM loan_schedule_versions WHERE loan_id = :loanId")
    suspend fun deleteScheduleVersionsByLoanId(loanId: Long)

    @Query("SELECT COUNT(*) FROM transactions WHERE account_id = :accountId OR counterparty_account_id = :accountId")
    suspend fun countTransactionsForAccount(accountId: Long): Int

    @Query("DELETE FROM accounts WHERE id = :accountId")
    suspend fun deleteAccountById(accountId: Long)
}
