package com.tapledger.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.tapledger.core.database.entity.MonthCloseEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MonthCloseDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(monthClose: MonthCloseEntity): Long

    @Query("SELECT * FROM month_closes WHERE budget_period_id = :periodId LIMIT 1")
    suspend fun getByPeriodId(periodId: Long): MonthCloseEntity?

    @Query("SELECT * FROM month_closes WHERE budget_period_id = :periodId LIMIT 1")
    fun observeByPeriodId(periodId: Long): Flow<MonthCloseEntity?>

    @Query("DELETE FROM month_closes WHERE budget_period_id = :periodId")
    suspend fun deleteForPeriod(periodId: Long)
}
