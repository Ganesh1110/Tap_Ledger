package com.tapledger.ui.screens.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tapledger.core.database.dao.BudgetPeriodDao
import com.tapledger.core.database.dao.MonthCloseDao
import com.tapledger.core.database.dao.TransactionAllocationDao
import com.tapledger.core.database.dao.TransactionDao
import com.tapledger.core.database.entity.AccountEntity
import com.tapledger.core.database.entity.TransactionEntity
import com.tapledger.core.model.BudgetPeriodStatus
import com.tapledger.core.model.MoneyFormatter
import com.tapledger.core.util.DateTimeUtils
import com.tapledger.data.repository.AccountRepository
import com.tapledger.data.repository.BudgetRepository
import com.tapledger.data.repository.TransactionRepository
import com.tapledger.domain.calculator.AccountBalanceCalculator
import com.tapledger.domain.calculator.BudgetCalculator
import com.tapledger.domain.model.AccountBalanceSummary
import com.tapledger.domain.model.BudgetPeriodSummary
import com.tapledger.domain.model.NetWorthSummary
import com.tapledger.ui.charts.builder.DashboardChartBuilder
import com.tapledger.ui.charts.model.BucketRingsModel
import com.tapledger.ui.charts.model.BurnUpChartModel
import com.tapledger.ui.charts.model.ChartState
import com.tapledger.ui.charts.model.DashboardHeroRingModel
import com.tapledger.ui.charts.model.SixPeriodTrendModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import javax.inject.Inject

data class DashboardUiState(
    val isLoading: Boolean = true,
    val pendingCount: Int = 0,
    val accountSummaries: List<AccountBalanceSummary> = emptyList(),
    val netWorth: NetWorthSummary? = null,
    val budgetSummary: BudgetPeriodSummary? = null,
    val recentTransactions: List<TransactionEntity> = emptyList(),
    val currentYear: Int = 2026,
    val currentMonth: Int = 10,
    val heroRingState: ChartState<DashboardHeroRingModel> = ChartState.Loading,
    val burnUpChartState: ChartState<BurnUpChartModel> = ChartState.Loading,
    val bucketRingsState: ChartState<BucketRingsModel> = ChartState.Loading,
    val sixPeriodTrendState: ChartState<SixPeriodTrendModel> = ChartState.Loading
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val accountRepository: AccountRepository,
    private val budgetRepository: BudgetRepository,
    private val transactionRepository: TransactionRepository,
    private val transactionDao: TransactionDao,
    private val transactionAllocationDao: TransactionAllocationDao,
    private val budgetPeriodDao: BudgetPeriodDao,
    private val monthCloseDao: MonthCloseDao
) : ViewModel() {

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val today = LocalDate.now(DateTimeUtils.IST_ZONE)
            loadDashboard(today.year, today.monthValue)

            // Reactively refresh dashboard whenever transactions change
            transactionRepository.observeAllTransactions().collect {
                loadDashboard(_uiState.value.currentYear, _uiState.value.currentMonth)
            }
        }
    }

    fun loadDashboard(
        year: Int = _uiState.value.currentYear,
        month: Int = _uiState.value.currentMonth
    ) {
        viewModelScope.launch {
            val period = budgetRepository.getOrCreateBudgetPeriod(year, month)
            val allocations = budgetRepository.getAllocationsForPeriodSync(period.id)
            val adjustments = budgetRepository.getAdjustmentsForPeriodSync(period.id)

            val periodTxs = transactionDao.getTransactionsInPeriod(period.startAt, period.endAt)
            val periodAllocations = transactionAllocationDao.getAllocationsInPeriod(period.startAt, period.endAt)

            val budgetSummary = BudgetCalculator.calculateBudgetPeriodSummary(
                period = period,
                allocations = allocations,
                adjustments = adjustments,
                transactions = periodTxs,
                allocationsForTransactions = periodAllocations
            )

            val accounts = accountRepository.getAllAccountsSync()
            val accountSummaries = accounts.map { account ->
                val txs = transactionDao.getBalanceTransactionsForAccount(account.id, account.openingBalanceDate)
                AccountBalanceCalculator.calculateAccountBalance(account, txs)
            }
            val netWorth = AccountBalanceCalculator.calculateNetWorth(accountSummaries)
            val pendingCount = transactionDao.getPendingCountSync()

            val recentTxs = periodTxs.sortedByDescending { it.occurredAt }.take(5)

            val nowIST = DateTimeUtils.nowEpochMillis()
            val isClosed = period.status == BudgetPeriodStatus.CLOSED

            val (heroRing, burnUp) = withContext(Dispatchers.Default) {
                val hero = DashboardChartBuilder.buildHeroRing(budgetSummary, isClosed, nowIST)
                val burn = DashboardChartBuilder.buildBurnUpChart(budgetSummary, period, periodTxs, periodAllocations, nowIST)
                hero to burn
            }

            val (bucketRings, sixTrend) = withContext(Dispatchers.Default) {
                val rings = DashboardChartBuilder.buildBucketRings(budgetSummary)
                val allCloses = monthCloseDao.getAllMonthClosesSync()
                val allPeriods = budgetPeriodDao.getAllPeriodsSync()
                val trend = DashboardChartBuilder.buildSixPeriodTrend(allCloses, allPeriods, budgetSummary, period.id)
                rings to trend
            }

            _uiState.value = DashboardUiState(
                isLoading = false,
                pendingCount = pendingCount,
                accountSummaries = accountSummaries,
                netWorth = netWorth,
                budgetSummary = budgetSummary,
                recentTransactions = recentTxs,
                currentYear = year,
                currentMonth = month,
                heroRingState = heroRing,
                burnUpChartState = burnUp,
                bucketRingsState = bucketRings,
                sixPeriodTrendState = sixTrend
            )
        }
    }

    fun nextMonth() {
        val (newYear, newMonth) = if (_uiState.value.currentMonth == 12) {
            (_uiState.value.currentYear + 1) to 1
        } else {
            _uiState.value.currentYear to (_uiState.value.currentMonth + 1)
        }
        loadDashboard(newYear, newMonth)
    }

    fun prevMonth() {
        val (newYear, newMonth) = if (_uiState.value.currentMonth == 1) {
            (_uiState.value.currentYear - 1) to 12
        } else {
            _uiState.value.currentYear to (_uiState.value.currentMonth - 1)
        }
        loadDashboard(newYear, newMonth)
    }

    fun updateAccountOpeningBalance(account: AccountEntity, newOpeningBalanceRupees: String) {
        viewModelScope.launch {
            try {
                val newPaise = MoneyFormatter.toPaise(newOpeningBalanceRupees)
                val updated = account.copy(
                    openingBalancePaise = newPaise,
                    updatedAt = DateTimeUtils.nowEpochMillis()
                )
                accountRepository.updateAccount(updated)
                loadDashboard()
            } catch (e: Exception) {
                // handle invalid input
            }
        }
    }
}
