package com.tapledger.service

import com.google.common.truth.Truth.assertThat
import com.tapledger.core.database.dao.MerchantMapDao
import com.tapledger.core.database.entity.MerchantMapEntity
import com.tapledger.data.repository.MerchantRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test

/**
 * Validates Flow.md §5.1 Adaptive Merchant Learning:
 * - Dynamic integer confidence scoring: (times_confirmed * 100L) >= (80L * (times_confirmed + times_overridden)) && times_confirmed >= 2
 * - Strict P2P transfer rejection (telephone numbers, personal names)
 * - Merchant qualification & canonical key invariance (no wildcard truncation)
 * - Exclusion of multi-category splits from learning
 * - Override penalty application
 */
class MerchantLearningTest {

    private class FakeMerchantMapDao : MerchantMapDao {
        val storage = mutableMapOf<Pair<String, Long>, MerchantMapEntity>()

        override suspend fun insertOrUpdate(entry: MerchantMapEntity): Long {
            storage[Pair(entry.merchantNormalized, entry.categoryId)] = entry
            return entry.id
        }

        override suspend fun findByMerchant(merchantNormalized: String): MerchantMapEntity? {
            return storage.values
                .filter { it.merchantNormalized == merchantNormalized }
                .sortedWith(compareByDescending<MerchantMapEntity> { it.timesConfirmed }.thenByDescending { it.lastUsedAt })
                .firstOrNull()
        }

        override suspend fun findByMerchantAndCategory(merchantNormalized: String, categoryId: Long): MerchantMapEntity? {
            return storage[Pair(merchantNormalized, categoryId)]
        }

        override suspend fun getMappingsForMerchant(merchantNormalized: String): List<MerchantMapEntity> {
            return storage.values.filter { it.merchantNormalized == merchantNormalized }
        }

        override fun observeAll(): Flow<List<MerchantMapEntity>> = emptyFlow()
    }

    private lateinit var fakeDao: FakeMerchantMapDao
    private lateinit var repository: MerchantRepository

    @Before
    fun setUp() {
        fakeDao = FakeMerchantMapDao()
        repository = MerchantRepository(fakeDao)
    }

    @Test
    fun testDynamicIntegerConfidenceScoring() {
        // Less than 2 confirmations -> always false
        assertThat(repository.isHighConfidence(0L, 0L)).isFalse()
        assertThat(repository.isHighConfidence(1L, 0L)).isFalse()

        // Exactly 2 confirmations, 0 overrides -> 100% >= 80% and >= 2 -> true
        assertThat(repository.isHighConfidence(2L, 0L)).isTrue()

        // 3 confirmations, 1 override -> 3/4 = 75% < 80% -> false
        assertThat(repository.isHighConfidence(3L, 1L)).isFalse()

        // 4 confirmations, 1 override -> 4/5 = 80% >= 80% -> true
        assertThat(repository.isHighConfidence(4L, 1L)).isTrue()

        // 7 confirmations, 2 overrides -> 7/9 = 77.7% < 80% -> false
        assertThat(repository.isHighConfidence(7L, 2L)).isFalse()

        // 8 confirmations, 2 overrides -> 8/10 = 80% -> true
        assertThat(repository.isHighConfidence(8L, 2L)).isTrue()
    }

    @Test
    fun testStrictP2PRejection() = runBlocking {
        // Phone number VPAs
        assertThat(repository.isP2PTransfer("9876543210@paytm")).isTrue()
        assertThat(repository.isP2PTransfer("+919876543210@okaxis")).isTrue()
        assertThat(repository.isP2PTransfer("919876543210@ybl")).isTrue()
        assertThat(repository.isP2PTransfer("9876543210")).isTrue()

        // Personal name VPAs
        assertThat(repository.isP2PTransfer("rahul.verma@okhdfcbank")).isTrue()
        assertThat(repository.isP2PTransfer("priya.sharma12@okaxis")).isTrue()
        assertThat(repository.isP2PTransfer("john.doe@upi")).isTrue()

        // Commercial handles should NOT be rejected
        assertThat(repository.isP2PTransfer("swiggy.bangalore@icici")).isFalse()
        assertThat(repository.isP2PTransfer("paytmqr2810050501011@paytm")).isFalse()
        assertThat(repository.isP2PTransfer("bharatpe.98765432@icici")).isFalse()
        assertThat(repository.isP2PTransfer("SWIGGY_BANGALORE")).isFalse()
        assertThat(repository.isP2PTransfer("DMART RETAIL")).isFalse()

        // Verify that P2P VPAs are never stored into merchant_map
        repository.rememberMerchantCategory("9876543210@paytm", 101L)
        assertThat(fakeDao.storage).isEmpty()

        repository.rememberMerchantCategory("rahul.verma@okhdfcbank", 101L)
        assertThat(fakeDao.storage).isEmpty()
    }

    @Test
    fun testMultiSplitExcludedFromLearning() = runBlocking {
        val merchant = "SWIGGY_BANGALORE"
        val categoryId = 101L

        // Attempting to learn from a multi-category split
        repository.rememberMerchantCategory(merchant, categoryId, isSplit = true)

        // Storage must remain empty
        assertThat(fakeDao.storage).isEmpty()
        assertThat(repository.getSuggestedCategoryId(merchant)).isNull()
    }

    @Test
    fun testCanonicalKeyInvariance_noWildcardTruncation() = runBlocking {
        val fullVpa = "paytmqr2810050501011@paytm"
        val categoryId = 101L

        repository.rememberMerchantCategory(fullVpa, categoryId, isSplit = false)

        val stored = fakeDao.findByMerchant(fullVpa.uppercase())
        assertThat(stored).isNotNull()
        // Stored key must be the exact full VPA, never a truncated wildcard pattern like "paytmqr*"
        assertThat(stored?.merchantNormalized).isEqualTo(fullVpa.uppercase())
    }

    @Test
    fun testOverridePenaltySuppressesShortcut() = runBlocking {
        val merchant = "SWIGGY"
        val foodCat = 101L
        val groceryCat = 102L

        // User confirms Food twice -> reaches high confidence (2/2 = 100%)
        repository.rememberMerchantCategory(merchant, foodCat)
        repository.rememberMerchantCategory(merchant, foodCat)

        assertThat(repository.getSuggestedCategoryId(merchant)).isEqualTo(foodCat)

        // User overrides to Grocery -> Food gets override penalty
        repository.rememberMerchantCategory(merchant, groceryCat)

        // Food now has 2 confirmed, 1 overridden -> 2/3 = 66% < 80% -> shortcut suppressed!
        // Grocery only has 1 confirmed (< 2 minimum) -> shortcut suppressed!
        assertThat(repository.getSuggestedCategoryId(merchant)).isNull()

        // Grocery confirmed a second time -> 2 confirmed, 0 overridden -> 100% -> Grocery suggested!
        repository.rememberMerchantCategory(merchant, groceryCat)
        assertThat(repository.getSuggestedCategoryId(merchant)).isEqualTo(groceryCat)
    }
}
