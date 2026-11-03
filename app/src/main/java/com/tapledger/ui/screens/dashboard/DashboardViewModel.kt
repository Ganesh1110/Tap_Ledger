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
import com.tapledger.core.model.TransactionStatus
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
import com.tapledger.core.database.dao.BudgetAllocationDao
import com.tapledger.core.database.dao.CategoryDao
import com.tapledger.core.database.dao.CommitmentPaymentDao
import com.tapledger.core.database.dao.RecurringCommitmentDao
import com.tapledger.core.model.AccountType
import com.tapledger.core.model.BudgetBucket
import com.tapledger.core.model.CommitmentPaymentStatus
import com.tapledger.core.preferences.LoanPreferences
import com.tapledger.core.preferences.LoanPreferencesConfig
import com.tapledger.domain.calculator.SafeDailySpendCalculator
import com.tapledger.domain.calculator.SafeDailySpendResult
import kotlinx.coroutines.withContext
import java.time.LocalDate
import javax.inject.Inject

data class CategoryPaceAlert(
    val categoryId: Long,
    val categoryName: String,
    val percentageUsed: Int,
    val daysRemaining: Int,
    val isExceeded: Boolean
)

data class DashboardUiState(
    val isLoading: Boolean = true,
    val pendingCount: Int = 0,
    val accountSummaries: List<AccountBalanceSummary> = emptyList(),
    val netWorth: NetWorthSummary? = null,
    val budgetSummary: BudgetPeriodSummary? = null,
    val recentTransactions: List<TransactionEntity> = emptyList(),
    val currentYear: Int = java.time.LocalDate.now(com.tapledger.core.util.DateTimeUtils.IST_ZONE).year,
    val currentMonth: Int = java.time.LocalDate.now(com.tapledger.core.util.DateTimeUtils.IST_ZONE).monthValue,
    val heroRingState: ChartState<DashboardHeroRingModel> = ChartState.Loading,
    val burnUpChartState: ChartState<BurnUpChartModel> = ChartState.Loading,
    val bucketRingsState: ChartState<BucketRingsModel> = ChartState.Loading,
    val sixPeriodTrendState: ChartState<SixPeriodTrendModel> = ChartState.Loading,
    val safeDailySpend: SafeDailySpendResult? = null,
    val pendingSpendPaise: Long = 0L,
    val showIncomeDiscrepancyBanner: Boolean = false,
    val plannedIncomePaise: Long = 0L,
    val actualIncomePaise: Long = 0L,
    val currentPeriodId: Long? = null,
    val paceAlerts: List<CategoryPaceAlert> = emptyList(),
    val isLoanTrackerEnabled: Boolean = true,
    val loanConfig: LoanPreferencesConfig = LoanPreferencesConfig()
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val accountRepository: AccountRepository,
    private val budgetRepository: BudgetRepository,
    private val transactionRepository: TransactionRepository,
    private val transactionDao: TransactionDao,
    private val transactionAllocationDao: TransactionAllocationDao,
    private val budgetPeriodDao: BudgetPeriodDao,
    private val monthCloseDao: MonthCloseDao,
    private val loanPreferences: LoanPreferences,
    private val recurringCommitmentDao: RecurringCommitmentDao,
    private val commitmentPaymentDao: CommitmentPaymentDao,
    private val categoryDao: CategoryDao,
    private val budgetAllocationDao: BudgetAllocationDao
) : ViewModel() {

    private var selectedYear = LocalDate.now(DateTimeUtils.IST_ZONE).year
    private var selectedMonth = LocalDate.now(DateTimeUtils.IST_ZONE).monthValue
    private var loadDashboardJob: kotlinx.coroutines.Job? = null

    private val _uiState = MutableStateFlow(
        DashboardUiState(
            currentYear = selectedYear,
            currentMonth = selectedMonth
        )
    )
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            loadDashboard(selectedYear, selectedMonth)

            // Reactively refresh dashboard whenever transactions change
            transactionRepository.observeAllTransactions().collect {
                loadDashboard(selectedYear, selectedMonth)
            }
        }
        viewModelScope.launch {
            loanPreferences.configFlow.collect {
                loadDashboard(selectedYear, selectedMonth)
            }
        }
        viewModelScope.launch {
            categoryDao.getAllActiveCategories().collect {
                loadDashboard(selectedYear, selectedMonth)
            }
        }
        viewModelScope.launch {
            budgetAllocationDao.observeAllAllocations().collect {
                loadDashboard(selectedYear, selectedMonth)
            }
        }
    }

    fun loadDashboard(
        year: Int = selectedYear,
        month: Int = selectedMonth
    ) {
        selectedYear = year
        selectedMonth = month
        loadDashboardJob?.cancel()
        loadDashboardJob = viewModelScope.launch {
            try {
                val period = budgetRepository.getOrCreateBudgetPeriod(year, month)
            val allocations = budgetRepository.getAllocationsForPeriodSync(period.id)
            val adjustments = budgetRepository.getAdjustmentsForPeriodSync(period.id)

            val periodTxs = transactionDao.getTransactionsInPeriod(period.startAt, period.endAt)
            val periodAllocations = transactionAllocationDao.getAllocationsInPeriod(period.startAt, period.endAt)

            val allCategories = categoryDao.getAllCategoriesSync()
            val categoryNames = allCategories.associate { it.id to it.name }
            val budgetSummary = BudgetCalculator.calculateBudgetPeriodSummary(
                period = period,
                allocations = allocations,
                adjustments = adjustments,
                transactions = periodTxs,
                allocationsForTransactions = periodAllocations,
                categoryNames = categoryNames
            )

            val accounts = accountRepository.getAllAccountsSync()
            val accountSummaries = accounts.map { account ->
                val txs = transactionDao.getBalanceTransactionsForAccount(account.id, 0L)
                val earliestTx = txs.filter { it.status != TransactionStatus.IGNORED && it.status != TransactionStatus.VOIDED }.minOfOrNull { it.occurredAt }
                val effectiveOpeningDate = if (earliestTx != null && earliestTx < account.openingBalanceDate) {
                    DateTimeUtils.getStartOfDayEpochMillis(earliestTx)
                } else {
                    account.openingBalanceDate
                }
                val effectiveAccount = if (effectiveOpeningDate != account.openingBalanceDate) {
                    account.copy(openingBalanceDate = effectiveOpeningDate)
                } else {
                    account
                }
                AccountBalanceCalculator.calculateAccountBalance(effectiveAccount, txs)
            }
            val loanConfig = loanPreferences.getConfig()
            val effectiveSummaries = if (loanConfig.loanTrackerEnabled && loanConfig.includeLoansInNetWorth) {
                accountSummaries
            } else {
                accountSummaries.filter { it.account.type != AccountType.LOAN }
            }
            val netWorth = AccountBalanceCalculator.calculateNetWorth(effectiveSummaries)
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

            // Safe Daily Spend Calculation (Flow.md §8.4/§8.5)
            val todayStartIST = DateTimeUtils.getStartOfDayEpochMillis(nowIST)
            val todayEndIST = todayStartIST + SafeDailySpendCalculator.MILLIS_PER_IST_DAY
            val todayTxIds = periodTxs
                .filter { it.occurredAt in todayStartIST until todayEndIST && it.status != TransactionStatus.VOIDED && it.status != TransactionStatus.IGNORED }
                .map { it.id }
                .toSet()

            val savingsCategoryIds = budgetSummary.categorySummaries
                .filter { it.allocation.bucketSnapshot == BudgetBucket.SAVINGS }
                .map { it.allocation.categoryId }
                .toSet()

            var todaySpentPaise = 0L
            for (alloc in periodAllocations) {
                if (alloc.transactionId in todayTxIds && alloc.categoryId !in savingsCategoryIds) {
                    val tx = periodTxs.find { it.id == alloc.transactionId }
                    if (tx != null) {
                        if (tx.kind == com.tapledger.core.model.TransactionKind.EXPENSE ||
                            tx.kind == com.tapledger.core.model.TransactionKind.FEE ||
                            tx.kind == com.tapledger.core.model.TransactionKind.INTEREST_CHARGED) {
                            todaySpentPaise += alloc.amountPaise
                        } else if (tx.kind == com.tapledger.core.model.TransactionKind.REFUND) {
                            todaySpentPaise -= alloc.amountPaise
                        }
                    }
                }
            }
            if (todaySpentPaise < 0L) todaySpentPaise = 0L

            val safeDailySpend = SafeDailySpendCalculator.calculate(
                heroBudgetPaise = budgetSummary.dashboardHeroBudgetPaise,
                heroSpentPaise = budgetSummary.dashboardHeroSpendPaise,
                periodStartAt = period.startAt,
                periodEndAt = period.endAt,
                nowIST = nowIST,
                isClosed = isClosed,
                todaySpentPaise = todaySpentPaise
            )

            val pendingExpenseTxs = periodTxs.filter {
                it.status == TransactionStatus.PENDING &&
                it.direction == com.tapledger.core.model.TransactionDirection.OUT &&
                it.kind != com.tapledger.core.model.TransactionKind.TRANSFER
            }
            val pendingSpendPaise = pendingExpenseTxs.sumOf { it.amountPaise }

            val plannedIncome = period.incomePaise
            val actualIncome = budgetSummary.actualIncomePaise
            // Only prompt to re-plan if actual differs from planned by more than 5% tolerance
            val diffPaise = kotlin.math.abs(actualIncome - plannedIncome)
            val hasSignificantDiscrepancy = plannedIncome > 0L && (diffPaise * 100L > plannedIncome * 5L)
            val showDiscrepancy = period.status == BudgetPeriodStatus.OPEN && actualIncome > 0L && hasSignificantDiscrepancy

            val paceAlerts = if (isClosed || !safeDailySpend.isPeriodActive) {
                emptyList()
            } else {
                val totalDays = safeDailySpend.totalDaysInPeriod.coerceAtLeast(1)
                val elapsedDays = safeDailySpend.elapsedDays
                val timePercentElapsed = (elapsedDays * 100) / totalDays

                budgetSummary.categorySummaries
                    .filter { it.allocation.bucketSnapshot != BudgetBucket.SAVINGS }
                    .filter { cat ->
                        (cat.percentageUsed >= 80 && cat.percentageUsed > timePercentElapsed + 15) || cat.isExceeded
                    }
                    .map { cat ->
                        CategoryPaceAlert(
                            categoryId = cat.allocation.categoryId,
                            categoryName = cat.allocation.categoryNameSnapshot,
                            percentageUsed = cat.percentageUsed,
                            daysRemaining = safeDailySpend.daysRemaining,
                            isExceeded = cat.isExceeded
                        )
                    }
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
                sixPeriodTrendState = sixTrend,
                safeDailySpend = safeDailySpend,
                pendingSpendPaise = pendingSpendPaise,
                showIncomeDiscrepancyBanner = showDiscrepancy,
                plannedIncomePaise = plannedIncome,
                actualIncomePaise = actualIncome,
                currentPeriodId = period.id,
                paceAlerts = paceAlerts,
                isLoanTrackerEnabled = loanConfig.loanTrackerEnabled,
                loanConfig = loanConfig
            )
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            android.util.Log.e("DashboardViewModel", "Error loading dashboard", e)
        }
    }
}

    fun syncPlannedIncomeToActual(periodId: Long, actualIncomePaise: Long) {
        viewModelScope.launch {
            try {
                budgetRepository.updatePeriodIncomeAndRebalance(periodId, actualIncomePaise)
                loadDashboard(_uiState.value.currentYear, _uiState.value.currentMonth)
            } catch (e: Exception) {
                // Ignore or log error
            }
        }
    }

    fun nextMonth() {
        val (newYear, newMonth) = if (selectedMonth == 12) {
            (selectedYear + 1) to 1
        } else {
            selectedYear to (selectedMonth + 1)
        }
        loadDashboard(newYear, newMonth)
    }

    fun prevMonth() {
        val (newYear, newMonth) = if (selectedMonth == 1) {
            (selectedYear - 1) to 12
        } else {
            selectedYear to (selectedMonth - 1)
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
