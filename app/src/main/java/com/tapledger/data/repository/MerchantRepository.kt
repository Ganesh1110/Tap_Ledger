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

    /**
     * Flow.md §5.1: Dynamic Integer Confidence Scoring without floating-point primitives.
     * IsHighConfidence(M) <=> (times_confirmed * 100L) >= (80L * (times_confirmed + times_overridden)) && (times_confirmed >= 2)
     */
    fun isHighConfidence(timesConfirmed: Long, timesOverridden: Long): Boolean {
        val total = timesConfirmed + timesOverridden
        return timesConfirmed >= 2L && (timesConfirmed * 100L) >= (80L * total)
    }

    fun isHighConfidence(timesConfirmed: Int, timesOverridden: Int): Boolean {
        return isHighConfidence(timesConfirmed.toLong(), timesOverridden.toLong())
    }

    /**
     * Flow.md §5.1 Strict Guard against P2P & Data Poisoning:
     * VPAs with telephone numbers (e.g. 9876543210@paytm) or individual personal names
     * (e.g. rahul.verma@okhdfcbank) denote peer-to-peer transfers and are strictly excluded from learning.
     */
    fun isP2PTransfer(merchantOrVpa: String?): Boolean {
        if (merchantOrVpa.isNullOrBlank()) return false
        val text = merchantOrVpa.trim().lowercase()

        // 1. Phone number VPA check: 10 digits or with 91 prefix
        val phoneVpaRegex = Regex("""^(?:\+?91)?[6-9]\d{9}@\w+$""")
        if (phoneVpaRegex.matches(text)) return true

        // Plain phone number
        val plainPhoneRegex = Regex("""^(?:\+?91)?[6-9]\d{9}$""")
        if (plainPhoneRegex.matches(text)) return true

        // 2. Personal name pattern in VPA: e.g. rahul.verma@okhdfcbank, john.doe@upi
        val personalVpaRegex = Regex("""^[a-z]{3,}\.[a-z]{2,}(?:\d+)?@\w+$""")
        if (personalVpaRegex.matches(text)) {
            // Exclude known commercial prefixes
            val isKnownCommercial = text.startsWith("swiggy.") ||
                    text.startsWith("zomato.") ||
                    text.startsWith("uber.") ||
                    text.startsWith("ola.") ||
                    text.startsWith("amazon.") ||
                    text.startsWith("flipkart.") ||
                    text.startsWith("paytmqr.") ||
                    text.startsWith("bharatpe.")
            if (!isKnownCommercial) return true
        }

        return false
    }

    /**
     * Returns suggested category ID only if IsHighConfidence evaluates to true (Flow.md §5.1).
     */
    suspend fun getSuggestedCategoryId(merchantRaw: String?): Long? {
        if (merchantRaw.isNullOrBlank()) return null
        if (isP2PTransfer(merchantRaw)) return null
        val normalized = merchantRaw.trim().uppercase()
        val mappings = merchantMapDao.getMappingsForMerchant(normalized)
        val candidate = mappings
            .filter { isHighConfidence(it.timesConfirmed, it.timesOverridden) }
            .maxWithOrNull(compareByDescending<MerchantMapEntity> { it.timesConfirmed }.thenByDescending { it.lastUsedAt })
            ?: return null
        return candidate.categoryId
    }

    /**
     * Learns merchant category mapping (Flow.md §5.1):
     * - Rejects P2P transfers
     * - Excludes multi-category splits (isSplit = true)
     * - Stores full distinct canonical key (never wildcard)
     * - Applies override penalty to previous category when user changes it
     */
    suspend fun rememberMerchantCategory(
        merchantRaw: String?,
        categoryId: Long,
        isSplit: Boolean = false
    ) {
        if (merchantRaw.isNullOrBlank() || categoryId <= 0L) return
        if (isSplit) return // Multi-category split allocations are ambiguous and never increment times_confirmed
        if (isP2PTransfer(merchantRaw)) return // P2P transfers strictly excluded

        val normalized = merchantRaw.trim().uppercase()
        val now = DateTimeUtils.nowEpochMillis()

        // Flow.md §5.1: If there was an active high-confidence shortcut for another category, apply override penalty
        val activeSuggested = getSuggestedCategoryId(normalized)
        if (activeSuggested != null && activeSuggested != categoryId) {
            val previous = merchantMapDao.findByMerchantAndCategory(normalized, activeSuggested)
            if (previous != null) {
                merchantMapDao.insertOrUpdate(
                    previous.copy(
                        timesOverridden = previous.timesOverridden + 1,
                        lastUsedAt = now
                    )
                )
            }
        }

        val targetExisting = merchantMapDao.findByMerchantAndCategory(normalized, categoryId)
        val toSave = if (targetExisting != null) {
            targetExisting.copy(
                timesConfirmed = targetExisting.timesConfirmed + 1,
                lastUsedAt = now
            )
        } else {
            MerchantMapEntity(
                merchantNormalized = normalized,
                categoryId = categoryId,
                timesConfirmed = 1,
                timesOverridden = 0,
                lastUsedAt = now
            )
        }

        merchantMapDao.insertOrUpdate(toSave)
    }
}
