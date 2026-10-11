package com.tapledger.ui.screens.onboarding

import androidx.lifecycle.SavedStateHandle
import com.google.common.truth.Truth.assertThat
import com.tapledger.core.database.DatabaseTransactionRunner
import com.tapledger.core.database.ReferenceCategories
import com.tapledger.core.database.entity.AccountEntity
import com.tapledger.core.database.entity.BudgetAllocationEntity
import com.tapledger.core.database.entity.BudgetPeriodEntity
import com.tapledger.core.database.entity.CategoryEntity
import com.tapledger.core.model.AccountType
import com.tapledger.core.model.BudgetBucket
import com.tapledger.core.util.DateTimeUtils
import com.tapledger.data.repository.BudgetRepository
import com.tapledger.domain.onboarding.OnboardingCommitter
import com.tapledger.domain.onboarding.OnboardingDomainTest
import com.tapledger.domain.validator.DataIntegrityChecker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class OnboardingViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var fakeAccountDao: OnboardingDomainTest.FakeAccountDao
    private lateinit var fakeCategoryDao: OnboardingDomainTest.FakeCategoryDao
    private lateinit var fakeBudgetPeriodDao: OnboardingDomainTest.FakeBudgetPeriodDao
    private lateinit var fakeBudgetAllocationDao: OnboardingDomainTest.FakeBudgetAllocationDao
    private lateinit var committer: OnboardingCommitter
    private lateinit var savedStateHandle: SavedStateHandle

    private class DirectTxRunner : DatabaseTransactionRunner {
        override suspend fun <R> run(block: suspend () -> R): R = block()
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakeAccountDao = OnboardingDomainTest.FakeAccountDao()
        fakeCategoryDao = OnboardingDomainTest.FakeCategoryDao()
        fakeBudgetPeriodDao = OnboardingDomainTest.FakeBudgetPeriodDao()
        fakeBudgetAllocationDao = OnboardingDomainTest.FakeBudgetAllocationDao()
        savedStateHandle = SavedStateHandle()

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
            dataIntegrityChecker = DataIntegrityChecker()
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun testInitialState_defaultsAndCategoryLoading() = runTest(testDispatcher) {
        val viewModel = OnboardingViewModel(savedStateHandle, committer, fakeCategoryDao)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertThat(state.step).isEqualTo(OnboardingStep.O1_WELCOME)
        assertThat(state.bankAccounts).isNotEmpty()
        assertThat(state.categories).isNotEmpty()
    }

    @Test
    fun testIncomeInput_and503020Defaults() = runTest(testDispatcher) {
        val viewModel = OnboardingViewModel(savedStateHandle, committer, fakeCategoryDao)
        advanceUntilIdle()

        viewModel.setIncomeInput("60000")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertThat(state.incomePaise).isEqualTo(60_000_00L)
        assertThat(state.categoryAllocations.values.sum()).isEqualTo(60_000_00L)
    }

    @Test
    fun testStepNavigation_andValidation() = runTest(testDispatcher) {
        val viewModel = OnboardingViewModel(savedStateHandle, committer, fakeCategoryDao)
        advanceUntilIdle()

        // O1 -> O2
        assertThat(viewModel.nextStep()).isTrue()
        assertThat(viewModel.uiState.value.step).isEqualTo(OnboardingStep.O2_INCOME)

        // O2 without income fails
        assertThat(viewModel.nextStep()).isFalse()
        assertThat(viewModel.uiState.value.incomeError).isNotNull()

        // Set income -> O2 -> O3 succeeds
        viewModel.setIncomeInput("80000")
        assertThat(viewModel.nextStep()).isTrue()
        assertThat(viewModel.uiState.value.step).isEqualTo(OnboardingStep.O3_ACCOUNTS)

        // Previous step
        assertThat(viewModel.previousStep()).isTrue()
        assertThat(viewModel.uiState.value.step).isEqualTo(OnboardingStep.O2_INCOME)
    }

    @Test
    fun testNetWorthPreview() = runTest(testDispatcher) {
        val viewModel = OnboardingViewModel(savedStateHandle, committer, fakeCategoryDao)
        advanceUntilIdle()

        viewModel.updateBankAccount(0, StagedAccountUi(name = "Bank A", balanceInput = "50000"))
        viewModel.setCashBalanceInput("5000")
        viewModel.addCreditCard()
        viewModel.updateCreditCard(0, StagedCreditCardUi(name = "Card 1", outstandingInput = "10000"))

        val nw = viewModel.getNetWorthPreview()
        assertThat(nw.totalAssetsPaise).isEqualTo(55_000_00L) // 50k + 5k
        assertThat(nw.totalLiabilitiesPaise).isEqualTo(10_000_00L) // 10k
        assertThat(nw.netWorthPaise).isEqualTo(45_000_00L) // 55k - 10k
    }

    @Test
    fun testFullFlow_commitSuccess() = runTest(testDispatcher) {
        val viewModel = OnboardingViewModel(savedStateHandle, committer, fakeCategoryDao)
        advanceUntilIdle()

        val today = LocalDate.now(DateTimeUtils.IST_ZONE)
        val asOfDate = DateTimeUtils.getStartOfMonthEpochMillis(today.year, today.monthValue)
        viewModel.setAsOfDate(asOfDate)

        viewModel.setIncomeInput("50000")
        viewModel.updateBankAccount(0, StagedAccountUi(name = "HDFC", balanceInput = "25000"))
        viewModel.setCashBalanceInput("2000")

        // Advance steps to O6
        viewModel.setStep(OnboardingStep.O6_REVIEW)

        viewModel.commit()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertThat(state.commitError).isNull()
        assertThat(state.isCommitted).isTrue()

        // Verified in database
        assertThat(fakeAccountDao.getAllAccountsSync()).hasSize(2) // HDFC + Cash
        assertThat(fakeBudgetPeriodDao.getAllPeriodsSync()).hasSize(1)
    }
}
