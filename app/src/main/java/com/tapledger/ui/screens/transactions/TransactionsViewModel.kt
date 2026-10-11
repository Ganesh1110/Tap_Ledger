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
import javax.inject.Inject

data class TransactionsUiState(
    val transactions: List<TransactionEntity> = emptyList(),
    val filteredTransactions: List<TransactionEntity> = emptyList(),
    val categoriesMap: Map<Long, CategoryEntity> = emptyMap(),
    val accountsMap: Map<Long, AccountEntity> = emptyMap(),
    val searchQuery: String = "",
    val selectedStatus: TransactionStatus? = null,
    val selectedDateFilterEpoch: Long? = null,
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

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedStatus = MutableStateFlow<TransactionStatus?>(null)
    val selectedStatus: StateFlow<TransactionStatus?> = _selectedStatus.asStateFlow()

    private val _selectedDateFilterEpoch = MutableStateFlow<Long?>(null)
    val selectedDateFilterEpoch: StateFlow<Long?> = _selectedDateFilterEpoch.asStateFlow()

    private val _categoriesMap = MutableStateFlow<Map<Long, CategoryEntity>>(emptyMap())
    private val _accountsMap = MutableStateFlow<Map<Long, AccountEntity>>(emptyMap())

    private data class FilterCriteria(
        val query: String,
        val status: TransactionStatus?,
        val dateEpoch: Long?
    )

    private val _filterCriteria = combine(
        _searchQuery,
        _selectedStatus,
        _selectedDateFilterEpoch
    ) { query, status, dateEpoch ->
        FilterCriteria(query, status, dateEpoch)
    }

    val uiState: StateFlow<TransactionsUiState> = combine(
        transactionRepository.observeAllTransactions(),
        _filterCriteria,
        _categoriesMap,
        _accountsMap
    ) { txList, filter, catMap, accMap ->
        val filtered = txList.filter { tx ->
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
            matchesStatus && matchesDate && matchesQuery
        }

        val heatmap = run {
            val now = DateTimeUtils.nowEpochMillis()
            val period = budgetPeriodDao.getPeriodForTimestamp(now)
                ?: budgetPeriodDao.getLatestPeriod()
            if (period != null) {
                val allocs = transactionAllocationDao.getAllocationsInPeriod(period.startAt, period.endAt)
                val buckets = catMap.mapValues { it.value.defaultBucket }
                InsightsChartBuilder.buildSpendingHeatmap(period, txList, allocs, buckets)
            } else {
                ChartState.Empty("No budget period active")
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
            heatmapState = heatmap,
            isLoading = false
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = TransactionsUiState()
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
