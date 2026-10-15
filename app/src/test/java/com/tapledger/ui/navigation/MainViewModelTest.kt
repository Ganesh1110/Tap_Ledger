package com.tapledger.ui.navigation

import com.google.common.truth.Truth.assertThat
import com.tapledger.core.database.dao.AccountDao
import com.tapledger.core.database.dao.BudgetPeriodDao
import com.tapledger.core.database.entity.AccountEntity
import com.tapledger.core.database.entity.BudgetPeriodEntity
import com.tapledger.core.model.AccountType
import com.tapledger.core.model.BudgetPeriodStatus
import com.tapledger.core.security.AppLockCoordinator
import com.tapledger.core.security.FakeMpinClock
import com.tapledger.core.security.FakeMpinKeystore
import com.tapledger.core.security.FakeSharedPreferences
import com.tapledger.core.security.MpinManagerImpl
import com.tapledger.core.version.AppVersionManagerImpl
import com.tapledger.core.version.ReleaseNotesRepositoryImpl
import com.tapledger.data.backup.BackupManager
import com.tapledger.data.repository.OnboardingRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.lang.reflect.Proxy

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val appStatePrefs = FakeSharedPreferences()
    private val versionPrefs = FakeSharedPreferences()
    private val mpinPrefs = FakeSharedPreferences()

    private val accountsFlow = MutableStateFlow<List<AccountEntity>>(emptyList())
    private val periodsFlow = MutableStateFlow<List<BudgetPeriodEntity>>(emptyList())

    private lateinit var mpinManager: MpinManagerImpl
    private lateinit var appLockCoordinator: AppLockCoordinator
    private lateinit var versionManager: AppVersionManagerImpl
    private lateinit var onboardingRepository: OnboardingRepository

    private fun createTestAccount(): AccountEntity = AccountEntity(
        id = 1L,
        name = "Primary Bank",
        type = AccountType.BANK,
        identifierLast4 = "1234",
        openingBalancePaise = 100_000L,
        openingBalanceDate = 1_700_000_000_000L,
        isActive = true,
        createdAt = 1_700_000_000_000L,
        updatedAt = 1_700_000_000_000L
    )

    private fun createTestPeriod(): BudgetPeriodEntity = BudgetPeriodEntity(
        id = 1L,
        year = 2026,
        month = 10,
        startAt = 1_700_000_000_000L,
        endAt = 1_702_000_000_000L,
        incomePaise = 500_000L,
        status = BudgetPeriodStatus.OPEN,
        createdAt = 1_700_000_000_000L,
        updatedAt = 1_700_000_000_000L
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        val clock = FakeMpinClock()
        val keystore = FakeMpinKeystore()
        mpinManager = MpinManagerImpl(keystore = keystore, clock = clock, prefs = mpinPrefs)
        appLockCoordinator = AppLockCoordinator(mpinManager, clock)

        val releaseRepo = ReleaseNotesRepositoryImpl()
        versionManager = AppVersionManagerImpl(
            context = null,
            releaseNotesRepository = releaseRepo,
            customPrefs = versionPrefs,
            injectedVersionCode = 1L,
            injectedVersionName = "1.0.0",
            injectedDatabaseVersion = 2
        )

        val accountDao = Proxy.newProxyInstance(
            AccountDao::class.java.classLoader,
            arrayOf(AccountDao::class.java)
        ) { _, method, _ ->
            when (method.name) {
                "getAllActiveAccounts" -> accountsFlow
                "getAllAccountsSync" -> accountsFlow.value
                else -> null
            }
        } as AccountDao

        val budgetPeriodDao = Proxy.newProxyInstance(
            BudgetPeriodDao::class.java.classLoader,
            arrayOf(BudgetPeriodDao::class.java)
        ) { _, method, _ ->
            when (method.name) {
                "getAllPeriods" -> periodsFlow
                "getAllPeriodsSync" -> periodsFlow.value
                else -> null
            }
        } as BudgetPeriodDao

        onboardingRepository = OnboardingRepository(
            accountDao = accountDao,
            budgetPeriodDao = budgetPeriodDao
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `shows whats new once user is onboarded and allows dismissal`() = runTest {
        val viewModel = MainViewModel(
            onboardingRepository = onboardingRepository,
            mpinManager = mpinManager,
            appLockCoordinator = appLockCoordinator,
            appVersionManager = versionManager,
            customPrefs = appStatePrefs
        )

        advanceUntilIdle()
        assertThat(viewModel.shouldShowWhatsNew.value).isFalse()

        // User finishes onboarding
        accountsFlow.value = listOf(createTestAccount())
        periodsFlow.value = listOf(createTestPeriod())
        advanceUntilIdle()

        // What's new popup triggers
        assertThat(viewModel.shouldShowWhatsNew.value).isTrue()
        assertThat(viewModel.currentReleaseNotes.versionName).isEqualTo("1.0.0")

        // User clicks "Got It"
        viewModel.dismissWhatsNew()
        advanceUntilIdle()

        assertThat(viewModel.shouldShowWhatsNew.value).isFalse()
        assertThat(versionManager.lastSeenVersionCode).isEqualTo(1L)
    }

    @Test
    fun `manual trigger opens whats new dialog`() = runTest {
        val viewModel = MainViewModel(
            onboardingRepository = onboardingRepository,
            mpinManager = mpinManager,
            appLockCoordinator = appLockCoordinator,
            appVersionManager = versionManager,
            customPrefs = appStatePrefs
        )

        // Version already seen
        versionManager.markWhatsNewSeen()
        accountsFlow.value = listOf(createTestAccount())
        periodsFlow.value = listOf(createTestPeriod())
        advanceUntilIdle()

        assertThat(viewModel.shouldShowWhatsNew.value).isFalse()

        // User opens What's New manually from Settings
        viewModel.showWhatsNewManually()
        assertThat(viewModel.shouldShowWhatsNew.value).isTrue()

        viewModel.dismissWhatsNew()
        assertThat(viewModel.shouldShowWhatsNew.value).isFalse()
    }
}
