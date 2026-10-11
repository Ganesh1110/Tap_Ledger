package com.tapledger.ui.screens.insights

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tapledger.core.database.dao.BudgetPeriodDao
import com.tapledger.core.database.dao.MonthCloseDao
import com.tapledger.core.database.dao.TransactionAllocationDao
import com.tapledger.core.database.dao.TransactionDao
import com.tapledger.core.database.entity.BudgetPeriodEntity
import com.tapledger.core.model.BudgetPeriodStatus
import com.tapledger.core.util.DateTimeUtils
import com.tapledger.data.repository.BudgetRepository
import com.tapledger.data.repository.TransactionRepository
import com.tapledger.domain.calculator.BudgetCalculator
import com.tapledger.ui.charts.builder.BudgetChartBuilder
import com.tapledger.ui.charts.builder.DashboardChartBuilder
import com.tapledger.ui.charts.builder.InsightsChartBuilder
import com.tapledger.ui.charts.model.CategoryDonutSlice
import com.tapledger.ui.charts.util.ChartMath
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class InsightsViewModel @Inject constructor(
    private val budgetPeriodDao: BudgetPeriodDao,
    private val monthCloseDao: MonthCloseDao,
    private val budgetRepository: BudgetRepository,
    private val transactionRepository: TransactionRepository,
    private val transactionDao: TransactionDao,
    private val transactionAllocationDao: TransactionAllocationDao
) : ViewModel() {

    private val _uiState = MutableStateFlow(InsightsUiState())
    val uiState: StateFlow<InsightsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            loadAllData()

            // Reactively refresh when transactions change
            transactionRepository.observeAllTransactions().collect {
                loadAllData()
            }
        }
    }

    fun selectTab(tabIndex: Int) {
        _uiState.update { it.copy(selectedTab = tabIndex) }
    }

    fun selectPeriod(period: BudgetPeriodEntity) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    selectedPeriod = period,
                    selectedHeatmapDayEpoch = null,
                    selectedHeatmapDayTxs = emptyList(),
                    selectedDonutSlice = null
                )
            }
            refreshPeriodDependentVisuals(period)
        }
    }

    fun selectHeatmapDay(epochMillis: Long) {
        viewModelScope.launch {
            val currentState = _uiState.value
            val isCurrentlySelected = currentState.selectedHeatmapDayEpoch == epochMillis

            if (isCurrentlySelected) {
                _uiState.update {
                    it.copy(
                        selectedHeatmapDayEpoch = null,
                        selectedHeatmapDayTxs = emptyList()
                    )
                }
            } else {
                val dayStart = epochMillis
                val dayEnd = epochMillis + ChartMath.MILLIS_PER_IST_DAY
                val dayTxs = withContext(Dispatchers.Default) {
                    transactionDao.getTransactionsInPeriod(dayStart, dayEnd)
                        .filter { it.status == com.tapledger.core.model.TransactionStatus.CATEGORIZED }
                }

                _uiState.update {
                    it.copy(
                        selectedHeatmapDayEpoch = epochMillis,
                        selectedHeatmapDayTxs = dayTxs
                    )
                }
            }
        }
    }

    fun clearHeatmapDay() {
        _uiState.update {
            it.copy(
                selectedHeatmapDayEpoch = null,
                selectedHeatmapDayTxs = emptyList()
            )
        }
    }

    fun selectDonutSlice(slice: CategoryDonutSlice?) {
        _uiState.update { it.copy(selectedDonutSlice = slice) }
    }

    private suspend fun loadAllData() {
        try {
            val periods = withContext(Dispatchers.Default) {
                budgetPeriodDao.getAllPeriodsSync().sortedByDescending { it.startAt }
            }

            val nowIST = DateTimeUtils.nowEpochMillis()
            val openPeriod = periods.firstOrNull { it.status == BudgetPeriodStatus.OPEN && nowIST >= it.startAt && nowIST < it.endAt }
                ?: periods.firstOrNull()

            val selectedPeriod = _uiState.value.selectedPeriod ?: openPeriod

            val (cashFlowTrend, topMerchants) = withContext(Dispatchers.Default) {
                // 1. Cash flow trend over up to 12 periods (11 closed + 1 in-progress)
                val closedCloses = monthCloseDao.getAllMonthClosesSync()
                val currentSummary = if (openPeriod != null) {
                    val allocs = budgetRepository.getAllocationsForPeriodSync(openPeriod.id)
                    val adjs = budgetRepository.getAdjustmentsForPeriodSync(openPeriod.id)
                    val txs = transactionDao.getTransactionsInPeriod(openPeriod.startAt, openPeriod.endAt)
                    val txAllocs = transactionAllocationDao.getAllocationsInPeriod(openPeriod.startAt, openPeriod.endAt)
                    BudgetCalculator.calculateBudgetPeriodSummary(openPeriod, allocs, adjs, txs, txAllocs)
                } else null

                val trend = DashboardChartBuilder.buildSixPeriodTrend(
                    closedCloses = closedCloses,
                    allPeriods = periods,
                    currentSummary = currentSummary,
                    currentPeriodId = openPeriod?.id,
                    maxClosedPeriods = 11
                )

                // 2. Top Merchants
                val allTxs = transactionDao.getAllTransactionsSync()
                val merchants = InsightsChartBuilder.buildTopMerchants(allTxs)

                trend to merchants
            }

            _uiState.update {
                it.copy(
                    allPeriods = periods,
                    selectedPeriod = selectedPeriod,
                    cashFlowTrendState = cashFlowTrend,
                    topMerchantsState = topMerchants,
                    isLoading = false
                )
            }

            if (selectedPeriod != null) {
                refreshPeriodDependentVisuals(selectedPeriod)
            }
        } catch (e: Exception) {
            _uiState.update {
                it.copy(
                    isLoading = false,
                    errorMessage = e.message
                )
            }
        }
    }

    private suspend fun refreshPeriodDependentVisuals(period: BudgetPeriodEntity) {
        val (heatmap, donut) = withContext(Dispatchers.Default) {
            val periodTxs = transactionDao.getTransactionsInPeriod(period.startAt, period.endAt)
            val periodAllocs = transactionAllocationDao.getAllocationsInPeriod(period.startAt, period.endAt)
            val categories = budgetRepository.getAllCategoriesSync()

            // V7 Spending Calendar Heatmap
            val categoryBuckets = categories.associate { it.id to it.defaultBucket }
            val h = InsightsChartBuilder.buildSpendingHeatmap(period, periodTxs, periodAllocs, categoryBuckets)

            // V6 Category Donut
            val allocs = budgetRepository.getAllocationsForPeriodSync(period.id)
            val adjs = budgetRepository.getAdjustmentsForPeriodSync(period.id)
            val summary = BudgetCalculator.calculateBudgetPeriodSummary(period, allocs, adjs, periodTxs, periodAllocs)
            val categoryNames = categories.associate { it.id to it.name }
            val d = BudgetChartBuilder.buildCategoryDonut(summary, categoryNames)

            h to d
        }

        _uiState.update {
            it.copy(
                calendarHeatmapState = heatmap,
                categoryDonutState = donut
            )
        }
    }
}
