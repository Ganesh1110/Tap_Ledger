package com.tapledger.ui.screens.transactions

import com.google.common.truth.Truth.assertThat
import com.tapledger.core.database.DatabaseTransactionRunner
import com.tapledger.core.database.dao.AccountDao
import com.tapledger.core.database.dao.AuditLogDao
import com.tapledger.core.database.dao.BudgetAllocationDao
import com.tapledger.core.database.dao.BudgetAdjustmentDao
import com.tapledger.core.database.dao.BudgetPeriodDao
import com.tapledger.core.database.dao.CategoryDao
import com.tapledger.core.database.dao.TransactionAllocationDao
import com.tapledger.core.database.dao.TransactionDao
import com.tapledger.core.database.entity.AccountEntity
import com.tapledger.core.database.entity.CategoryEntity
import com.tapledger.core.database.entity.TransactionAllocationEntity
import com.tapledger.core.database.entity.TransactionEntity
import com.tapledger.core.model.AccountType
import com.tapledger.core.model.BudgetBucket
import com.tapledger.core.model.TransactionDirection
import com.tapledger.core.model.TransactionKind
import com.tapledger.core.model.TransactionStatus
import com.tapledger.core.util.DateTimeUtils
import com.tapledger.data.repository.AccountRepository
import com.tapledger.data.repository.BudgetRepository
import com.tapledger.data.repository.TransactionRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.lang.reflect.Proxy
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class TransactionsSearchAndFilterTest {

    private val testDispatcher = StandardTestDispatcher()

    private val today = LocalDate.now(DateTimeUtils.IST_ZONE)
    private val currentMonthStart = DateTimeUtils.getStartOfMonthEpochMillis(today.year, today.monthValue)
    private val day1OccurredAt = currentMonthStart + 12 * 3600 * 1000L // Noon on 1st of month

    private val testBank = AccountEntity(
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

    private val testCash = AccountEntity(
        id = 2L,
        name = "Wallet Cash",
        type = AccountType.CASH,
        identifierLast4 = null,
        openingBalancePaise = 5_000_00L,
        openingBalanceDate = 1_700_000_000_000L,
        isActive = true,
        createdAt = 1_700_000_000_000L,
        updatedAt = 1_700_000_000_000L
    )

    private val groceriesCat = CategoryEntity(
        id = 10L,
        name = "Groceries",
        defaultBucket = BudgetBucket.NEEDS,
        defaultSortOrder = 1
    )

    private val diningCat = CategoryEntity(
        id = 20L,
        name = "Dining Out",
        defaultBucket = BudgetBucket.WANTS,
        defaultSortOrder = 2
    )

    private val tx1ExpenseGroceries = TransactionEntity(
        id = 101L,
        accountId = 1L,
        amountPaise = 1500_00L, // ₹1,500
        direction = TransactionDirection.OUT,
        kind = TransactionKind.EXPENSE,
        status = TransactionStatus.CATEGORIZED,
        source = com.tapledger.core.model.TransactionSource.SMS,
        merchantRaw = "Nature's Basket",
        merchantNormalized = "Natures Basket",
        notes = "Weekly grocery run",
        occurredAt = day1OccurredAt,
        createdAt = day1OccurredAt,
        updatedAt = day1OccurredAt
    )

    private val tx2ExpenseDining = TransactionEntity(
        id = 102L,
        accountId = 1L,
        amountPaise = 500_00L, // ₹500
        direction = TransactionDirection.OUT,
        kind = TransactionKind.EXPENSE,
        status = TransactionStatus.PENDING,
        source = com.tapledger.core.model.TransactionSource.MANUAL,
        merchantRaw = "Starbucks Coffee",
        merchantNormalized = "Starbucks",
        notes = "Espresso with friends",
        occurredAt = day1OccurredAt + 3600_000L,
        createdAt = day1OccurredAt + 3600_000L,
        updatedAt = day1OccurredAt + 3600_000L
    )

    private val tx3Income = TransactionEntity(
        id = 103L,
        accountId = 1L,
        amountPaise = 75_000_00L, // ₹75,000
        direction = TransactionDirection.IN,
        kind = TransactionKind.INCOME,
        status = TransactionStatus.CATEGORIZED,
        source = com.tapledger.core.model.TransactionSource.MANUAL,
        merchantRaw = "Acme Corp Salary",
        merchantNormalized = "Acme Corp",
        notes = "October Salary",
        occurredAt = day1OccurredAt,
        createdAt = day1OccurredAt,
        updatedAt = day1OccurredAt
    )

    private val tx4Transfer = TransactionEntity(
        id = 104L,
        accountId = 2L,
        amountPaise = 2_000_00L, // ₹2,000
        direction = TransactionDirection.IN,
        kind = TransactionKind.TRANSFER,
        status = TransactionStatus.CATEGORIZED,
        source = com.tapledger.core.model.TransactionSource.MANUAL,
        merchantRaw = "ATM Withdrawal",
        merchantNormalized = "ATM Withdrawal",
        notes = "Cash replenishment",
        occurredAt = day1OccurredAt + 7200_000L,
        createdAt = day1OccurredAt + 7200_000L,
        updatedAt = day1OccurredAt + 7200_000L
    )

    private val allocations = listOf(
        TransactionAllocationEntity(id = 1L, transactionId = 101L, categoryId = 10L, amountPaise = 1500_00L),
        TransactionAllocationEntity(id = 2L, transactionId = 102L, categoryId = 20L, amountPaise = 500_00L)
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun TestScope.createViewModel(
        txList: List<TransactionEntity> = listOf(tx1ExpenseGroceries, tx2ExpenseDining, tx3Income, tx4Transfer),
        allocList: List<TransactionAllocationEntity> = allocations,
        categories: List<CategoryEntity> = listOf(groceriesCat, diningCat),
        accounts: List<AccountEntity> = listOf(testBank, testCash)
    ): TransactionsViewModel {
        val runner = object : DatabaseTransactionRunner {
            override suspend fun <R> run(block: suspend () -> R): R = block()
        }

        val accountDao = Proxy.newProxyInstance(
            AccountDao::class.java.classLoader,
            arrayOf(AccountDao::class.java)
        ) { _, method, _ ->
            when (method.name) {
                "getAllAccountsSync" -> accounts
                "getAllActiveAccounts" -> emptyFlow<List<AccountEntity>>()
                else -> null
            }
        } as AccountDao

        val categoryDao = Proxy.newProxyInstance(
            CategoryDao::class.java.classLoader,
            arrayOf(CategoryDao::class.java)
        ) { _, method, _ ->
            when (method.name) {
                "getAllCategoriesSync" -> categories
                "getAllActiveCategories" -> emptyFlow<List<CategoryEntity>>()
                else -> null
            }
        } as CategoryDao

        val budgetPeriodDao = Proxy.newProxyInstance(
            BudgetPeriodDao::class.java.classLoader,
            arrayOf(BudgetPeriodDao::class.java)
        ) { _, method, _ ->
            when (method.name) {
                "getPeriodForDate" -> null
                "getCurrentPeriod" -> null
                else -> null
            }
        } as BudgetPeriodDao

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
            BudgetAdjustmentDao::class.java.classLoader,
            arrayOf(BudgetAdjustmentDao::class.java)
        ) { _, _, _ -> null } as BudgetAdjustmentDao

        val transactionDao = Proxy.newProxyInstance(
            TransactionDao::class.java.classLoader,
            arrayOf(TransactionDao::class.java)
        ) { _, method, _ ->
            when (method.name) {
                "observeAllTransactions" -> flowOf(txList)
                "getAllTransactionsSync" -> txList
                else -> null
            }
        } as TransactionDao

        val txAllocDao = Proxy.newProxyInstance(
            TransactionAllocationDao::class.java.classLoader,
            arrayOf(TransactionAllocationDao::class.java)
        ) { _, method, _ ->
            when (method.name) {
                "observeAllAllocations" -> flowOf(allocList)
                else -> null
            }
        } as TransactionAllocationDao

        val auditLogDao = Proxy.newProxyInstance(
            AuditLogDao::class.java.classLoader,
            arrayOf(AuditLogDao::class.java)
        ) { _, _, _ -> 1L } as AuditLogDao

        val accountRepo = AccountRepository(
            accountDao = accountDao,
            budgetPeriodDao = budgetPeriodDao,
            transactionDao = transactionDao
        )

        val budgetRepo = BudgetRepository(
            transactionRunner = runner,
            categoryDao = categoryDao,
            budgetPeriodDao = budgetPeriodDao,
            budgetAllocationDao = budgetAllocationDao,
            budgetAdjustmentDao = budgetAdjustmentDao
        )

        val txRepo = TransactionRepository(
            transactionRunner = runner,
            transactionDao = transactionDao,
            transactionAllocationDao = txAllocDao,
            auditLogDao = auditLogDao,
            accountDao = accountDao,
            categoryDao = categoryDao,
            budgetPeriodDao = budgetPeriodDao
        )

        val vm = TransactionsViewModel(
            transactionRepository = txRepo,
            accountRepository = accountRepo,
            budgetRepository = budgetRepo,
            transactionAllocationDao = txAllocDao,
            budgetPeriodDao = budgetPeriodDao
        )

        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            vm.uiState.collect()
        }

        return vm
    }

    @Test
    fun defaultState_showsAllTransactionsForCurrentMonth() = runTest {
        val vm = createViewModel()
        advanceUntilIdle()

        val state = vm.uiState.value
        assertThat(state.filteredTransactions).hasSize(4)
        assertThat(state.isAnyFilterActive).isFalse()
    }

    @Test
    fun filterByKind_filtersExpensesCorrectly() = runTest {
        val vm = createViewModel()
        advanceUntilIdle()

        vm.onKindSelected(TransactionKind.EXPENSE)
        advanceUntilIdle()

        val state = vm.uiState.value
        assertThat(state.selectedKind).isEqualTo(TransactionKind.EXPENSE)
        assertThat(state.filteredTransactions.map { it.id }).containsExactly(101L, 102L)
        assertThat(state.isAnyFilterActive).isTrue()

        // Toggle off
        vm.onKindSelected(null)
        advanceUntilIdle()
        assertThat(vm.uiState.value.filteredTransactions).hasSize(4)
    }

    @Test
    fun filterByKind_filtersIncomeAndTransferCorrectly() = runTest {
        val vm = createViewModel()
        advanceUntilIdle()

        vm.onKindSelected(TransactionKind.INCOME)
        advanceUntilIdle()
        assertThat(vm.uiState.value.filteredTransactions.map { it.id }).containsExactly(103L)

        vm.onKindSelected(TransactionKind.TRANSFER)
        advanceUntilIdle()
        assertThat(vm.uiState.value.filteredTransactions.map { it.id }).containsExactly(104L)
    }

    @Test
    fun filterByCategory_filtersMatchingTransactions() = runTest {
        val vm = createViewModel()
        advanceUntilIdle()

        vm.onCategorySelected(10L) // Groceries
        advanceUntilIdle()

        val state = vm.uiState.value
        assertThat(state.selectedCategoryId).isEqualTo(10L)
        assertThat(state.filteredTransactions.map { it.id }).containsExactly(101L)
        assertThat(state.isAnyFilterActive).isTrue()
    }

    @Test
    fun filterByBucket_filtersMatchingTransactionsInBucket() = runTest {
        val vm = createViewModel()
        advanceUntilIdle()

        // Needs bucket should match Groceries (tx 101)
        vm.onBucketSelected(BudgetBucket.NEEDS)
        advanceUntilIdle()

        assertThat(vm.uiState.value.filteredTransactions.map { it.id }).containsExactly(101L)

        // Wants bucket should match Dining Out (tx 102)
        vm.onBucketSelected(BudgetBucket.WANTS)
        advanceUntilIdle()

        assertThat(vm.uiState.value.filteredTransactions.map { it.id }).containsExactly(102L)
    }

    @Test
    fun filterByAccount_filtersMatchingAccount() = runTest {
        val vm = createViewModel()
        advanceUntilIdle()

        vm.onAccountSelected(2L) // Wallet Cash
        advanceUntilIdle()

        val state = vm.uiState.value
        assertThat(state.selectedAccountId).isEqualTo(2L)
        assertThat(state.filteredTransactions.map { it.id }).containsExactly(104L)
        assertThat(state.isAnyFilterActive).isTrue()
    }

    @Test
    fun filterByStatus_filtersPendingCorrectly() = runTest {
        val vm = createViewModel()
        advanceUntilIdle()

        vm.onStatusSelected(TransactionStatus.PENDING)
        advanceUntilIdle()

        val state = vm.uiState.value
        assertThat(state.selectedStatus).isEqualTo(TransactionStatus.PENDING)
        assertThat(state.filteredTransactions.map { it.id }).containsExactly(102L)
        assertThat(state.isAnyFilterActive).isTrue()
    }

    @Test
    fun searchQuery_matchesMerchantRawAndNormalized() = runTest {
        val vm = createViewModel()
        advanceUntilIdle()

        vm.onSearchQueryChanged("Starbucks")
        advanceUntilIdle()

        assertThat(vm.uiState.value.filteredTransactions.map { it.id }).containsExactly(102L)
        assertThat(vm.uiState.value.isAnyFilterActive).isTrue()
    }

    @Test
    fun searchQuery_matchesNotes() = runTest {
        val vm = createViewModel()
        advanceUntilIdle()

        vm.onSearchQueryChanged("replenishment")
        advanceUntilIdle()

        assertThat(vm.uiState.value.filteredTransactions.map { it.id }).containsExactly(104L)
    }

    @Test
    fun searchQuery_matchesAccountName() = runTest {
        val vm = createViewModel()
        advanceUntilIdle()

        vm.onSearchQueryChanged("Wallet")
        advanceUntilIdle()

        assertThat(vm.uiState.value.filteredTransactions.map { it.id }).containsExactly(104L)
    }

    @Test
    fun searchQuery_matchesCategoryName() = runTest {
        val vm = createViewModel()
        advanceUntilIdle()

        vm.onSearchQueryChanged("Groceries")
        advanceUntilIdle()

        assertThat(vm.uiState.value.filteredTransactions.map { it.id }).containsExactly(101L)
    }

    @Test
    fun searchQuery_matchesRupeeAmount() = runTest {
        val vm = createViewModel()
        advanceUntilIdle()

        // 500 should match ₹500 (50000 paise)
        vm.onSearchQueryChanged("500")
        advanceUntilIdle()

        val matchedIds = vm.uiState.value.filteredTransactions.map { it.id }
        assertThat(matchedIds).contains(102L)
    }

    @Test
    fun clearAllFilters_resetsAllActiveFilters() = runTest {
        val vm = createViewModel()
        advanceUntilIdle()

        vm.onSearchQueryChanged("test")
        vm.onKindSelected(TransactionKind.EXPENSE)
        vm.onCategorySelected(10L)
        vm.onBucketSelected(BudgetBucket.NEEDS)
        vm.onAccountSelected(1L)
        vm.onStatusSelected(TransactionStatus.PENDING)
        vm.toggleMonthFilter() // deactivates month filter
        advanceUntilIdle()

        assertThat(vm.uiState.value.isAnyFilterActive).isTrue()

        vm.clearAllFilters()
        advanceUntilIdle()

        val state = vm.uiState.value
        assertThat(state.searchQuery).isEmpty()
        assertThat(state.selectedKind).isNull()
        assertThat(state.selectedCategoryId).isNull()
        assertThat(state.selectedBucket).isNull()
        assertThat(state.selectedAccountId).isNull()
        assertThat(state.selectedStatus).isNull()
        assertThat(state.isMonthFilterActive).isTrue()
        assertThat(state.isAnyFilterActive).isFalse()
        assertThat(state.filteredTransactions).hasSize(4)
    }
}
