package com.tapledger.ui.screens.manual

import com.google.common.truth.Truth.assertThat
import com.tapledger.core.database.DatabaseTransactionRunner
import com.tapledger.core.database.dao.AccountDao
import com.tapledger.core.database.dao.AuditLogDao
import com.tapledger.core.database.dao.BudgetAllocationDao
import com.tapledger.core.database.dao.BudgetPeriodDao
import com.tapledger.core.database.dao.CategoryDao
import com.tapledger.core.database.dao.MerchantMapDao
import com.tapledger.core.database.dao.TransactionAllocationDao
import com.tapledger.core.database.dao.TransactionDao
import com.tapledger.core.database.entity.AccountEntity
import com.tapledger.core.database.entity.CategoryEntity
import com.tapledger.core.database.entity.TransactionEntity
import com.tapledger.core.model.AccountType
import com.tapledger.core.model.BudgetBucket
import com.tapledger.core.model.TransactionKind
import com.tapledger.core.util.DateTimeUtils
import com.tapledger.data.repository.AccountRepository
import com.tapledger.data.repository.BudgetRepository
import com.tapledger.data.repository.MerchantRepository
import com.tapledger.data.repository.TransactionRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.emptyFlow
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
class ManualEntryViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private val recordedTransactions = mutableListOf<TransactionEntity>()

    private lateinit var accountRepository: AccountRepository
    private lateinit var budgetRepository: BudgetRepository
    private lateinit var transactionRepository: TransactionRepository
    private lateinit var merchantRepository: MerchantRepository

    private val testAccount = AccountEntity(
        id = 1L,
        name = "HDFC Bank",
        type = AccountType.BANK,
        identifierLast4 = "1234",
        openingBalancePaise = 50_000_00L,
        openingBalanceDate = 1_700_000_000_000L,
        isActive = true,
        createdAt = 1_700_000_000_000L,
        updatedAt = 1_700_000_000_000L
    )

    private val testCategory = CategoryEntity(
        id = 10L,
        name = "Groceries",
        defaultBucket = BudgetBucket.NEEDS,
        defaultSortOrder = 1
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        recordedTransactions.clear()

        val runner = object : DatabaseTransactionRunner {
            override suspend fun <R> run(block: suspend () -> R): R = block()
        }

        val accountDao = Proxy.newProxyInstance(
            AccountDao::class.java.classLoader,
            arrayOf(AccountDao::class.java)
        ) { _, method, args ->
            when (method.name) {
                "getAllAccountsSync" -> listOf(testAccount)
                "getById" -> testAccount
                "getAllActiveAccounts" -> emptyFlow<List<AccountEntity>>()
                else -> null
            }
        } as AccountDao

        val categoryDao = Proxy.newProxyInstance(
            CategoryDao::class.java.classLoader,
            arrayOf(CategoryDao::class.java)
        ) { _, method, _ ->
            when (method.name) {
                "getAllCategoriesSync" -> listOf(testCategory)
                "getAllActiveCategories" -> emptyFlow<List<CategoryEntity>>()
                else -> null
            }
        } as CategoryDao

        val budgetPeriodDao = Proxy.newProxyInstance(
            BudgetPeriodDao::class.java.classLoader,
            arrayOf(BudgetPeriodDao::class.java)
        ) { _, _, _ -> null } as BudgetPeriodDao

        val budgetAllocationDao = object : BudgetAllocationDao() {
            override suspend fun insertInternal(allocation: com.tapledger.core.database.entity.BudgetAllocationEntity): Long = 1L
            override suspend fun insertAllInternal(allocations: List<com.tapledger.core.database.entity.BudgetAllocationEntity>): List<Long> = emptyList()
            override suspend fun updateInternal(allocation: com.tapledger.core.database.entity.BudgetAllocationEntity) {}
            override suspend fun getAdjustmentsForPeriod(periodId: Long): List<com.tapledger.core.database.entity.BudgetAdjustmentEntity> = emptyList()
            override suspend fun getAdvancesForPeriod(periodId: Long): List<com.tapledger.core.database.entity.AdvanceLedgerEntity> = emptyList()
            override fun observeAllocationsForPeriod(periodId: Long) = emptyFlow<List<com.tapledger.core.database.entity.BudgetAllocationEntity>>()
            override suspend fun getAllocationsForPeriodSync(periodId: Long) = emptyList<com.tapledger.core.database.entity.BudgetAllocationEntity>()
            override suspend fun getAllAllocationsSync() = emptyList<com.tapledger.core.database.entity.BudgetAllocationEntity>()
            override suspend fun getAllocation(periodId: Long, categoryId: Long) = null
            override suspend fun deleteAllocation(periodId: Long, categoryId: Long) {}
        }

        val budgetAdjustmentDao = Proxy.newProxyInstance(
            com.tapledger.core.database.dao.BudgetAdjustmentDao::class.java.classLoader,
            arrayOf(com.tapledger.core.database.dao.BudgetAdjustmentDao::class.java)
        ) { _, _, _ -> null } as com.tapledger.core.database.dao.BudgetAdjustmentDao

        val transactionDao = Proxy.newProxyInstance(
            TransactionDao::class.java.classLoader,
            arrayOf(TransactionDao::class.java)
        ) { _, method, args ->
            when (method.name) {
                "insert" -> {
                    val tx = args[0] as TransactionEntity
                    val id = (recordedTransactions.size + 1).toLong()
                    val saved = tx.copy(id = id)
                    recordedTransactions.add(saved)
                    id
                }
                "getById" -> recordedTransactions.firstOrNull()
                else -> null
            }
        } as TransactionDao

        val transactionAllocationDao = Proxy.newProxyInstance(
            TransactionAllocationDao::class.java.classLoader,
            arrayOf(TransactionAllocationDao::class.java)
        ) { _, method, _ ->
            when (method.name) {
                "insertAll" -> emptyList<Long>()
                else -> null
            }
        } as TransactionAllocationDao

        val auditLogDao = Proxy.newProxyInstance(
            AuditLogDao::class.java.classLoader,
            arrayOf(AuditLogDao::class.java)
        ) { _, _, _ -> 1L } as AuditLogDao

        val merchantMapDao = Proxy.newProxyInstance(
            MerchantMapDao::class.java.classLoader,
            arrayOf(MerchantMapDao::class.java)
        ) { _, method, _ ->
            when (method.name) {
                "observeAll" -> emptyFlow<List<com.tapledger.core.database.entity.MerchantMapEntity>>()
                "findByMerchant" -> null
                "findByMerchantAndCategory" -> null
                "getMappingsForMerchant" -> emptyList<com.tapledger.core.database.entity.MerchantMapEntity>()
                "insertOrUpdate" -> 1L
                else -> null
            }
        } as MerchantMapDao

        accountRepository = AccountRepository(
            accountDao = accountDao,
            budgetPeriodDao = budgetPeriodDao,
            transactionDao = transactionDao
        )

        budgetRepository = BudgetRepository(
            transactionRunner = runner,
            categoryDao = categoryDao,
            budgetPeriodDao = budgetPeriodDao,
            budgetAllocationDao = budgetAllocationDao,
            budgetAdjustmentDao = budgetAdjustmentDao
        )

        transactionRepository = TransactionRepository(
            transactionRunner = runner,
            transactionDao = transactionDao,
            transactionAllocationDao = transactionAllocationDao,
            auditLogDao = auditLogDao,
            accountDao = accountDao,
            categoryDao = categoryDao,
            budgetPeriodDao = budgetPeriodDao
        )

        merchantRepository = MerchantRepository(
            merchantMapDao = merchantMapDao
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun testDefaultDateIsPresentAndCanBeSelected() = runTest {
        val before = DateTimeUtils.nowEpochMillis()
        val viewModel = ManualEntryViewModel(
            accountRepository = accountRepository,
            budgetRepository = budgetRepository,
            transactionRepository = transactionRepository,
            merchantRepository = merchantRepository
        )
        advanceUntilIdle()

        val after = DateTimeUtils.nowEpochMillis()
        val state = viewModel.uiState.value
        assertThat(state.selectedDateMillis).isAtLeast(before)
        assertThat(state.selectedDateMillis).isAtMost(after)

        // Select a custom chosen date in the past
        val customEpochMillis = 1700000000000L
        viewModel.onDateSelected(customEpochMillis)
        assertThat(viewModel.uiState.value.selectedDateMillis).isEqualTo(customEpochMillis)
    }

    @Test
    fun testSubmitTransaction_recordsExpenseWithChosenDate() = runTest {
        val viewModel = ManualEntryViewModel(
            accountRepository = accountRepository,
            budgetRepository = budgetRepository,
            transactionRepository = transactionRepository,
            merchantRepository = merchantRepository
        )
        advanceUntilIdle()

        // Choose a past date (e.g. 5 days ago)
        val customDate = DateTimeUtils.nowEpochMillis() - 5L * 86_400_000L
        viewModel.onDateSelected(customDate)
        viewModel.onAmountChanged("250.00")
        viewModel.onMerchantChanged("Supermarket")
        viewModel.onNoteChanged("Weekly grocery shopping")

        viewModel.submitTransaction()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.isSuccess).isTrue()
        assertThat(recordedTransactions).hasSize(1)

        val tx = recordedTransactions.first()
        assertThat(tx.occurredAt).isEqualTo(customDate)
        assertThat(tx.amountPaise).isEqualTo(250_00L)
        assertThat(tx.kind).isEqualTo(TransactionKind.EXPENSE)
        assertThat(tx.merchantRaw).isEqualTo("Supermarket")
    }

    @Test
    fun testSubmitTransaction_recordsIncomeWithChosenDate() = runTest {
        val viewModel = ManualEntryViewModel(
            accountRepository = accountRepository,
            budgetRepository = budgetRepository,
            transactionRepository = transactionRepository,
            merchantRepository = merchantRepository
        )
        advanceUntilIdle()

        val customDate = DateTimeUtils.nowEpochMillis() - 10L * 86_400_000L
        viewModel.onKindSelected(TransactionKind.INCOME)
        viewModel.onDateSelected(customDate)
        viewModel.onAmountChanged("15000")
        viewModel.onMerchantChanged("Freelance Client")

        viewModel.submitTransaction()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.isSuccess).isTrue()
        assertThat(recordedTransactions).hasSize(1)

        val tx = recordedTransactions.first()
        assertThat(tx.occurredAt).isEqualTo(customDate)
        assertThat(tx.amountPaise).isEqualTo(15000_00L)
        assertThat(tx.kind).isEqualTo(TransactionKind.INCOME)
        assertThat(tx.merchantRaw).isEqualTo("Freelance Client")
    }

    @Test
    fun testAdjustmentKind_setsErrorMessage_doesNotSilentlySucceed() = runTest {
        val viewModel = ManualEntryViewModel(
            accountRepository = accountRepository,
            budgetRepository = budgetRepository,
            transactionRepository = transactionRepository,
            merchantRepository = merchantRepository
        )
        advanceUntilIdle()

        viewModel.onKindSelected(TransactionKind.ADJUSTMENT)
        viewModel.onAmountChanged("500")

        viewModel.submitTransaction()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.isSuccess).isFalse()
        assertThat(viewModel.uiState.value.errorMessage).contains("Adjustments cannot be recorded as manual transactions")
    }

    @Test
    fun testDateBeforeOpeningBalance_showsWarningMessage() = runTest {
        val viewModel = ManualEntryViewModel(
            accountRepository = accountRepository,
            budgetRepository = budgetRepository,
            transactionRepository = transactionRepository,
            merchantRepository = merchantRepository
        )
        advanceUntilIdle()

        // testAccount openingBalanceDate is 1_700_000_000_000L
        val priorDate = 1_700_000_000_000L - 86_400_000L
        viewModel.onDateSelected(priorDate)

        assertThat(viewModel.uiState.value.openingBalanceWarningMessage).isNotNull()
        assertThat(viewModel.uiState.value.openingBalanceWarningMessage).contains("before HDFC Bank's opening balance date")
    }
}
