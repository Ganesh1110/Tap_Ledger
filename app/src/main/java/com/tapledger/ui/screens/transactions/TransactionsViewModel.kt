package com.tapledger.ui.screens.transactions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tapledger.core.database.dao.BudgetPeriodDao
import com.tapledger.core.database.dao.TransactionAllocationDao
import com.tapledger.core.database.entity.AccountEntity
import com.tapledger.core.database.entity.CategoryEntity
import com.tapledger.core.database.entity.TransactionEntity
import com.tapledger.core.model.TransactionStatus
import com.tapledger.core.util.DateTimeUtils
import com.tapledger.data.repository.AccountRepository
import com.tapledger.data.repository.BudgetRepository
import com.tapledger.data.repository.TransactionRepository
import com.tapledger.ui.charts.builder.InsightsChartBuilder
import com.tapledger.ui.charts.model.ChartState
import com.tapledger.ui.charts.model.SpendingHeatmapModel
import com.tapledger.ui.charts.util.ChartMath
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.update
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject

data class MonthFilterState(
    val year: Int,
    val month: Int,
    val isFilterActive: Boolean = true
)

data class TransactionsUiState(
    val transactions: List<TransactionEntity> = emptyList(),
    val filteredTransactions: List<TransactionEntity> = emptyList(),
    val categoriesMap: Map<Long, CategoryEntity> = emptyMap(),
    val accountsMap: Map<Long, AccountEntity> = emptyMap(),
    val searchQuery: String = "",
    val selectedStatus: TransactionStatus? = null,
    val selectedDateFilterEpoch: Long? = null,
    val selectedYear: Int = 2026,
    val selectedMonth: Int = 10,
    val isMonthFilterActive: Boolean = true,
    val heatmapState: ChartState<SpendingHeatmapModel> = ChartState.Loading,
    val isLoading: Boolean = true
)

@HiltViewModel
class TransactionsViewModel @Inject constructor(
    private val transactionRepository: TransactionRepository,
    private val accountRepository: AccountRepository,
    private val budgetRepository: BudgetRepository,
    private val transactionAllocationDao: TransactionAllocationDao,
    private val budgetPeriodDao: BudgetPeriodDao
) : ViewModel() {

    private val today = LocalDate.now(DateTimeUtils.IST_ZONE)

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedStatus = MutableStateFlow<TransactionStatus?>(null)
    val selectedStatus: StateFlow<TransactionStatus?> = _selectedStatus.asStateFlow()

    private val _selectedDateFilterEpoch = MutableStateFlow<Long?>(null)
    val selectedDateFilterEpoch: StateFlow<Long?> = _selectedDateFilterEpoch.asStateFlow()

    private val _monthFilterState = MutableStateFlow(
        MonthFilterState(
            year = today.year,
            month = today.monthValue,
            isFilterActive = true
        )
    )
    val monthFilterState: StateFlow<MonthFilterState> = _monthFilterState.asStateFlow()

    private val _categoriesMap = MutableStateFlow<Map<Long, CategoryEntity>>(emptyMap())
    private val _accountsMap = MutableStateFlow<Map<Long, AccountEntity>>(emptyMap())

    private data class FilterCriteria(
        val query: String,
        val status: TransactionStatus?,
        val dateEpoch: Long?,
        val monthFilter: MonthFilterState
    )

    private val _filterCriteria = combine(
        _searchQuery,
        _selectedStatus,
        _selectedDateFilterEpoch,
        _monthFilterState
    ) { query, status, dateEpoch, monthFilter ->
        FilterCriteria(query, status, dateEpoch, monthFilter)
    }

    val uiState: StateFlow<TransactionsUiState> = combine(
        transactionRepository.observeAllTransactions(),
        _filterCriteria,
        _categoriesMap,
        _accountsMap
    ) { txList, filter, catMap, accMap ->
        val monthStart = DateTimeUtils.getStartOfMonthEpochMillis(filter.monthFilter.year, filter.monthFilter.month)
        val monthEnd = DateTimeUtils.getEndOfMonthEpochMillis(filter.monthFilter.year, filter.monthFilter.month)

        val filtered = txList.filter { tx ->
            val matchesMonth = if (!filter.monthFilter.isFilterActive) true else {
                tx.occurredAt >= monthStart && tx.occurredAt < monthEnd
            }
            val matchesStatus = (filter.status == null || tx.status == filter.status)
            val matchesDate = if (filter.dateEpoch == null) true else {
                tx.occurredAt >= filter.dateEpoch && tx.occurredAt < (filter.dateEpoch + ChartMath.MILLIS_PER_IST_DAY)
            }
            val matchesQuery = if (filter.query.isBlank()) true else {
                val q = filter.query.trim().lowercase()
                (tx.merchantRaw?.lowercase()?.contains(q) == true) ||
                        (tx.notes?.lowercase()?.contains(q) == true) ||
                        tx.amountPaise.toString().contains(q)
            }
            matchesMonth && matchesStatus && matchesDate && matchesQuery
        }

        val heatmap = run {
            try {
                val period = budgetRepository.getOrCreateBudgetPeriod(filter.monthFilter.year, filter.monthFilter.month)
                val allocs = transactionAllocationDao.getAllocationsInPeriod(period.startAt, period.endAt)
                val buckets = catMap.mapValues { it.value.defaultBucket }
                InsightsChartBuilder.buildSpendingHeatmap(period, txList, allocs, buckets)
            } catch (e: Exception) {
                ChartState.Empty("Unable to load budget period")
            }
        }

        TransactionsUiState(
            transactions = txList,
            filteredTransactions = filtered,
            categoriesMap = catMap,
            accountsMap = accMap,
            searchQuery = filter.query,
            selectedStatus = filter.status,
            selectedDateFilterEpoch = filter.dateEpoch,
            selectedYear = filter.monthFilter.year,
            selectedMonth = filter.monthFilter.month,
            isMonthFilterActive = filter.monthFilter.isFilterActive,
            heatmapState = heatmap,
            isLoading = false
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = TransactionsUiState(
            selectedYear = today.year,
            selectedMonth = today.monthValue,
            isMonthFilterActive = true
        )
    )

    init {
        viewModelScope.launch {
            val cats = budgetRepository.getAllCategoriesSync().associateBy { it.id }
            _categoriesMap.value = cats
            val accs = accountRepository.getAllAccountsSync().associateBy { it.id }
            _accountsMap.value = accs
        }
    }

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
    }

    fun onStatusSelected(status: TransactionStatus?) {
        _selectedStatus.value = status
    }

    fun onSelectHeatmapDay(epochMillis: Long) {
        if (_selectedDateFilterEpoch.value == epochMillis) {
            _selectedDateFilterEpoch.value = null
        } else {
            _selectedDateFilterEpoch.value = epochMillis
        }
    }

    fun clearDateFilter() {
        _selectedDateFilterEpoch.value = null
    }

    fun prevMonth() {
        clearDateFilter()
        _monthFilterState.update { current ->
            val ym = YearMonth.of(current.year, current.month).minusMonths(1)
            current.copy(year = ym.year, month = ym.monthValue, isFilterActive = true)
        }
    }

    fun nextMonth() {
        clearDateFilter()
        _monthFilterState.update { current ->
            val ym = YearMonth.of(current.year, current.month).plusMonths(1)
            current.copy(year = ym.year, month = ym.monthValue, isFilterActive = true)
        }
    }

    fun jumpToCurrentMonth() {
        clearDateFilter()
        val now = LocalDate.now(DateTimeUtils.IST_ZONE)
        _monthFilterState.update { current ->
            current.copy(year = now.year, month = now.monthValue, isFilterActive = true)
        }
    }

    fun toggleMonthFilter() {
        _monthFilterState.update { current ->
            current.copy(isFilterActive = !current.isFilterActive)
        }
    }

    fun selectMonth(year: Int, month: Int) {
        clearDateFilter()
        _monthFilterState.update { current ->
            current.copy(year = year, month = month, isFilterActive = true)
        }
    }

    fun voidTransaction(transactionId: Long, reason: String = "User voided") {
        viewModelScope.launch {
            transactionRepository.voidTransaction(transactionId, reason)
        }
    }

    fun restoreTransaction(transactionId: Long, reason: String = "User restored") {
        viewModelScope.launch {
            transactionRepository.restoreTransaction(transactionId, reason)
        }
    }
}
