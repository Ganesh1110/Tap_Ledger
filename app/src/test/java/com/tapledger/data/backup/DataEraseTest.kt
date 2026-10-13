package com.tapledger.data.backup

import com.google.common.truth.Truth.assertThat
import com.tapledger.core.database.DatabaseTransactionRunner
import com.tapledger.core.database.ReferenceCategories
import com.tapledger.core.database.entity.AccountEntity
import com.tapledger.core.database.entity.BudgetPeriodEntity
import com.tapledger.core.model.AccountType
import com.tapledger.core.model.BudgetBucket
import com.tapledger.core.model.BudgetPeriodStatus
import com.tapledger.core.util.DateTimeUtils
import com.tapledger.data.repository.BudgetRepository
import com.tapledger.data.repository.OnboardingRepository
import com.tapledger.domain.onboarding.OnboardingCommitter
import com.tapledger.domain.onboarding.OnboardingDistribution
import com.tapledger.domain.onboarding.OnboardingDomainTest
import com.tapledger.domain.onboarding.OnboardingDraft
import com.tapledger.domain.onboarding.StagedAccount
import com.tapledger.domain.validator.DataIntegrityChecker
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import java.time.LocalDate

class DataEraseTest {

    private lateinit var fakeAccountDao: OnboardingDomainTest.FakeAccountDao
    private lateinit var fakeBudgetPeriodDao: OnboardingDomainTest.FakeBudgetPeriodDao
    private lateinit var fakeCategoryDao: OnboardingDomainTest.FakeCategoryDao
    private lateinit var fakeBudgetAllocationDao: OnboardingDomainTest.FakeBudgetAllocationDao
    private lateinit var onboardingRepository: OnboardingRepository
    private lateinit var committer: OnboardingCommitter
    private val dataIntegrityChecker = DataIntegrityChecker()

    private class DirectTxRunner : DatabaseTransactionRunner {
        override suspend fun <R> run(block: suspend () -> R): R = block()
    }

    @Before
    fun setUp() {
        fakeAccountDao = OnboardingDomainTest.FakeAccountDao()
        fakeBudgetPeriodDao = OnboardingDomainTest.FakeBudgetPeriodDao()
        fakeCategoryDao = OnboardingDomainTest.FakeCategoryDao()
        fakeBudgetAllocationDao = OnboardingDomainTest.FakeBudgetAllocationDao()

        onboardingRepository = OnboardingRepository(
            accountDao = fakeAccountDao,
            budgetPeriodDao = fakeBudgetPeriodDao
        )

        val budgetRepo = BudgetRepository(
            categoryDao = fakeCategoryDao,
            budgetPeriodDao = fakeBudgetPeriodDao,
            budgetAllocationDao = fakeBudgetAllocationDao,
            budgetAdjustmentDao = object : com.tapledger.core.database.dao.BudgetAdjustmentDao {
                override suspend fun insert(adjustment: com.tapledger.core.database.entity.BudgetAdjustmentEntity): Long = 1L
                override fun observeAdjustmentsForPeriod(periodId: Long): Flow<List<com.tapledger.core.database.entity.BudgetAdjustmentEntity>> = emptyFlow()
                override suspend fun getAdjustmentsForPeriodSync(periodId: Long) = emptyList<com.tapledger.core.database.entity.BudgetAdjustmentEntity>()
                override suspend fun getAllAdjustmentsSync() = emptyList<com.tapledger.core.database.entity.BudgetAdjustmentEntity>()
            },
            transactionRunner = DirectTxRunner()
        )

        committer = OnboardingCommitter(
            transactionRunner = DirectTxRunner(),
            accountDao = fakeAccountDao,
            categoryDao = fakeCategoryDao,
            budgetPeriodDao = fakeBudgetPeriodDao,
            budgetAllocationDao = fakeBudgetAllocationDao,
            budgetRepository = budgetRepo,
            dataIntegrityChecker = dataIntegrityChecker
        )
    }

    @Test
    fun testDataEraseAndReset_transitionsToNotOnboardedAndAllowsFreshCommit() {
        runBlocking {
            val today = LocalDate.now(DateTimeUtils.IST_ZONE)
            val asOfDate = DateTimeUtils.getStartOfMonthEpochMillis(today.year, today.monthValue)

            // 1. Initial State: Seed categories & perform onboarding
            ReferenceCategories.seedIfEmpty(fakeCategoryDao)
            val initialCats = fakeCategoryDao.getAllCategoriesSync()

            val incomePaise = 60_000_00L
            val (needs, wants, savings) = OnboardingDistribution.calculateBucketTargets(incomePaise)
            val allocs = mutableMapOf<Long, Long>()
            allocs.putAll(OnboardingDistribution.distributeBucket(needs, initialCats.filter { it.defaultBucket == BudgetBucket.NEEDS }))
            allocs.putAll(OnboardingDistribution.distributeBucket(wants, initialCats.filter { it.defaultBucket == BudgetBucket.WANTS }))
            allocs.putAll(OnboardingDistribution.distributeBucket(savings, initialCats.filter { it.defaultBucket == BudgetBucket.SAVINGS }))

            val draft1 = OnboardingDraft(
                monthlyIncomePaise = incomePaise,
                asOfDateEpochMillis = asOfDate,
                bankAccounts = listOf(StagedAccount(name = "Bank Old", type = AccountType.BANK, balancePaise = 20_000_00L)),
                cashBalancePaise = 1_000_00L,
                categoryAllocations = allocs
            )
            committer.commit(draft1)

            // App is now onboarded
            assertThat(onboardingRepository.isOnboarded()).isTrue()
            assertThat(fakeAccountDao.getAllAccountsSync()).isNotEmpty()
            assertThat(fakeBudgetPeriodDao.getAllPeriodsSync()).isNotEmpty()

            // 2. Simulate Data Erase Workflow:
            // - Clear accounts, periods, allocations
            fakeAccountDao.storage.clear()
            fakeBudgetPeriodDao.storage.clear()
            fakeBudgetAllocationDao.storage.clear()
            // - Ensure system reference categories exist
            ReferenceCategories.seedIfEmpty(fakeCategoryDao)

            // App must dynamically report isOnboarded == false
            assertThat(onboardingRepository.isOnboarded()).isFalse()
            assertThat(fakeAccountDao.getAllAccountsSync()).isEmpty()
            assertThat(fakeBudgetPeriodDao.getAllPeriodsSync()).isEmpty()
            assertThat(fakeCategoryDao.getAllCategoriesSync()).isNotEmpty()

            // 3. User can now start fresh first-run onboarding
            val draft2 = OnboardingDraft(
                monthlyIncomePaise = 90_000_00L,
                asOfDateEpochMillis = asOfDate,
                bankAccounts = listOf(StagedAccount(name = "Fresh Bank", type = AccountType.BANK, balancePaise = 50_000_00L)),
                cashBalancePaise = 3_000_00L,
                categoryAllocations = allocs
            )

            // Second onboarding commit must succeed on clean database
            committer.commit(draft2)

            assertThat(onboardingRepository.isOnboarded()).isTrue()
            val newAccounts = fakeAccountDao.getAllAccountsSync()
            assertThat(newAccounts).hasSize(2) // Fresh Bank + Cash
            assertThat(newAccounts.any { it.name == "Fresh Bank" }).isTrue()
            assertThat(newAccounts.any { it.name == "Bank Old" }).isFalse()
        }
    }

    @Test
    fun testDataEraseClearsMpinStateAndKeystore() = kotlinx.coroutines.test.runTest {
        val clock = com.tapledger.core.security.FakeMpinClock()
        val keystore = com.tapledger.core.security.FakeMpinKeystore()
        val prefs = com.tapledger.core.security.FakeSharedPreferences()
        val mpinManager = com.tapledger.core.security.MpinManagerImpl(keystore, clock, prefs)
        mpinManager.setMpin("4829", "4829")
        assertThat(mpinManager.isMpinSet.value).isTrue()
        assertThat(keystore.isKeyCleared).isFalse()

        // Simulate failed attempt
        mpinManager.verify("0000")
        assertThat(mpinManager.lockoutState.value.failedAttempts).isEqualTo(1)

        // Perform Data Erase security clearing
        mpinManager.clearAllSecurityData()

        // Assert MPIN state, attempts, Keystore key, and storage are cleared
        assertThat(mpinManager.isMpinSet.value).isFalse()
        assertThat(mpinManager.lockoutState.value.failedAttempts).isEqualTo(0)
        assertThat(keystore.isKeyCleared).isTrue()
        assertThat(prefs.storage).isEmpty()
    }
}
