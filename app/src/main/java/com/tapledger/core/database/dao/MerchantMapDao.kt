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

    @Query("SELECT * FROM merchant_map WHERE merchant_normalized = :merchantNormalized ORDER BY times_confirmed DESC, last_used_at DESC LIMIT 1")
    suspend fun findByMerchant(merchantNormalized: String): MerchantMapEntity?

    @Query("SELECT * FROM merchant_map WHERE merchant_normalized = :merchantNormalized AND category_id = :categoryId LIMIT 1")
    suspend fun findByMerchantAndCategory(merchantNormalized: String, categoryId: Long): MerchantMapEntity?

    @Query("SELECT * FROM merchant_map WHERE merchant_normalized = :merchantNormalized")
    suspend fun getMappingsForMerchant(merchantNormalized: String): List<MerchantMapEntity>

    @Query("SELECT * FROM merchant_map ORDER BY last_used_at DESC")
    fun observeAll(): Flow<List<MerchantMapEntity>>
}
