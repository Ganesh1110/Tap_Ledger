package com.tapledger.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.tapledger.core.database.entity.UnparsedAlertEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface UnparsedAlertDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(alert: UnparsedAlertEntity): Long

    @Query("SELECT * FROM unparsed_alerts ORDER BY occurred_at DESC")
    fun observeAll(): Flow<List<UnparsedAlertEntity>>

    @Query("SELECT COUNT(*) FROM unparsed_alerts")
    fun observeCount(): Flow<Int>

    @Query("DELETE FROM unparsed_alerts WHERE occurred_at < :thresholdTimestamp")
    suspend fun purgeOlderThan(thresholdTimestamp: Long): Int
}
