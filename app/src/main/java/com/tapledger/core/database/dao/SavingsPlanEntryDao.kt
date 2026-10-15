package com.tapledger.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.tapledger.core.database.entity.SavingsPlanEntryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SavingsPlanEntryDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: SavingsPlanEntryEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entries: List<SavingsPlanEntryEntity>): List<Long>

    @Query("SELECT * FROM savings_plan_entries WHERE period_id = :periodId")
    fun getEntriesForPeriod(periodId: Long): Flow<List<SavingsPlanEntryEntity>>

    @Query("SELECT * FROM savings_plan_entries WHERE period_id = :periodId")
    suspend fun getEntriesForPeriodSync(periodId: Long): List<SavingsPlanEntryEntity>

    @Query("DELETE FROM savings_plan_entries WHERE period_id = :periodId")
    suspend fun deleteForPeriod(periodId: Long)
}
