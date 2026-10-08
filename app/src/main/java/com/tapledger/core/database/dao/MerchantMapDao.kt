package com.tapledger.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.tapledger.core.database.entity.MerchantMapEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MerchantMapDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(entry: MerchantMapEntity): Long

    @Query("SELECT * FROM merchant_map WHERE merchant_normalized = :merchantNormalized LIMIT 1")
    suspend fun findByMerchant(merchantNormalized: String): MerchantMapEntity?

    @Query("SELECT * FROM merchant_map ORDER BY updated_at DESC")
    fun observeAll(): Flow<List<MerchantMapEntity>>
}
