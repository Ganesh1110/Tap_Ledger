package com.tapledger.data.repository

import com.tapledger.core.database.dao.MerchantMapDao
import com.tapledger.core.database.entity.MerchantMapEntity
import com.tapledger.core.util.DateTimeUtils
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MerchantRepository @Inject constructor(
    private val merchantMapDao: MerchantMapDao
) {

    fun observeAllMappings(): Flow<List<MerchantMapEntity>> = merchantMapDao.observeAll()

    suspend fun getSuggestedCategoryId(merchantRaw: String?): Long? {
        if (merchantRaw.isNullOrBlank()) return null
        val normalized = merchantRaw.trim().uppercase()
        val entry = merchantMapDao.findByMerchant(normalized)
        return entry?.categoryId
    }

    suspend fun rememberMerchantCategory(merchantRaw: String?, categoryId: Long) {
        if (merchantRaw.isNullOrBlank() || categoryId <= 0L) return
        val normalized = merchantRaw.trim().uppercase()
        val now = DateTimeUtils.nowEpochMillis()

        val existing = merchantMapDao.findByMerchant(normalized)
        val entity = if (existing != null) {
            existing.copy(
                categoryId = categoryId,
                updatedAt = now
            )
        } else {
            MerchantMapEntity(
                merchantNormalized = normalized,
                categoryId = categoryId,
                source = "USER_CONFIRMED",
                createdAt = now,
                updatedAt = now
            )
        }

        merchantMapDao.insertOrUpdate(entity)
    }
}
