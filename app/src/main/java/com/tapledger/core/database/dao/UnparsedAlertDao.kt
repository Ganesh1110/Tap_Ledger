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

    @Query("SELECT * FROM unparsed_alerts ORDER BY received_at DESC")
    fun observeAll(): Flow<List<UnparsedAlertEntity>>

    @Query("SELECT COUNT(*) FROM unparsed_alerts WHERE status = 'NEW'")
    fun observeNewCount(): Flow<Int>
}
