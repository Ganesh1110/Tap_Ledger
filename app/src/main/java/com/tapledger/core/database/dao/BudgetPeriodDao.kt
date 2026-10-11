package com.tapledger.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.tapledger.core.database.entity.BudgetPeriodEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BudgetPeriodDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(budgetPeriod: BudgetPeriodEntity): Long

    @Update
    suspend fun update(budgetPeriod: BudgetPeriodEntity)

    @Query("SELECT * FROM budget_periods WHERE id = :id")
    suspend fun getById(id: Long): BudgetPeriodEntity?

    @Query("SELECT * FROM budget_periods WHERE year = :year AND month = :month LIMIT 1")
    suspend fun getByYearMonth(year: Int, month: Int): BudgetPeriodEntity?

    @Query("SELECT * FROM budget_periods WHERE year = :year AND month = :month LIMIT 1")
    fun observeByYearMonth(year: Int, month: Int): Flow<BudgetPeriodEntity?>

    @Query("SELECT * FROM budget_periods ORDER BY year DESC, month DESC")
    fun getAllPeriods(): Flow<List<BudgetPeriodEntity>>

    @Query("SELECT * FROM budget_periods")
    suspend fun getAllPeriodsSync(): List<BudgetPeriodEntity>

    @Query("SELECT * FROM budget_periods WHERE :timestamp >= start_at AND :timestamp < end_at LIMIT 1")
    suspend fun getPeriodForTimestamp(timestamp: Long): BudgetPeriodEntity?

    @Query("SELECT * FROM budget_periods ORDER BY start_at ASC LIMIT 1")
    suspend fun getEarliestPeriod(): BudgetPeriodEntity?

    @Query("SELECT * FROM budget_periods ORDER BY start_at DESC LIMIT 1")
    suspend fun getLatestPeriod(): BudgetPeriodEntity?

    @Query("SELECT * FROM budget_periods WHERE status = 'OPEN' AND :timestamp >= start_at AND :timestamp < end_at LIMIT 1")
    suspend fun getOpenPeriodForTimestamp(timestamp: Long): BudgetPeriodEntity?

    @Query("SELECT * FROM budget_periods WHERE status = 'OPEN' ORDER BY start_at ASC LIMIT 1")
    suspend fun getFirstOpenPeriod(): BudgetPeriodEntity?

    @Query("SELECT * FROM budget_periods WHERE status = 'CLOSED' AND end_at > :startOfDayTimestamp LIMIT 1")
    suspend fun getClosedPeriodEndingAfter(startOfDayTimestamp: Long): BudgetPeriodEntity?
}
