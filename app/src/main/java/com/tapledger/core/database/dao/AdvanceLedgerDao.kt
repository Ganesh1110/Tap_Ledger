package com.tapledger.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.tapledger.core.database.entity.AdvanceLedgerEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AdvanceLedgerDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entry: AdvanceLedgerEntity): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(entries: List<AdvanceLedgerEntity>): List<Long>

    @Query("SELECT * FROM advance_ledger WHERE budget_period_id = :periodId")
    fun observeForPeriod(periodId: Long): Flow<List<AdvanceLedgerEntity>>

    @Query("SELECT * FROM advance_ledger WHERE budget_period_id = :periodId")
    suspend fun getForPeriodSync(periodId: Long): List<AdvanceLedgerEntity>

    @Query("SELECT * FROM advance_ledger WHERE category_id = :categoryId ORDER BY created_at ASC")
    suspend fun getForCategory(categoryId: Long): List<AdvanceLedgerEntity>
}
