package com.tapledger.ui.screens.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tapledger.core.database.DatabaseSeeder
import com.tapledger.core.database.dao.TransactionAllocationDao
import com.tapledger.core.database.dao.TransactionDao
import com.tapledger.core.database.entity.TransactionEntity
import com.tapledger.core.util.DateTimeUtils
import com.tapledger.data.repository.AccountRepository
import com.tapledger.data.repository.BudgetRepository
import com.tapledger.data.repository.TransactionRepository
import com.tapledger.domain.calculator.AccountBalanceCalculator
import com.tapledger.domain.calculator.BudgetCalculator
import com.tapledger.domain.model.AccountBalanceSummary
import com.tapledger.domain.model.BudgetPeriodSummary
import com.tapledger.domain.model.NetWorthSummary
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
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
    val currentMonth: Int = 10
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val seeder: DatabaseSeeder,
    private val accountRepository: AccountRepository,
    private val budgetRepository: BudgetRepository,
    private val transactionRepository: TransactionRepository,
    private val transactionDao: TransactionDao,
    private val transactionAllocationDao: TransactionAllocationDao
) : ViewModel() {

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            // Seed database if launching for the first time
            seeder.seedInitialDataIfEmpty()
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

            _uiState.value = DashboardUiState(
                isLoading = false,
                pendingCount = pendingCount,
                accountSummaries = accountSummaries,
                netWorth = netWorth,
                budgetSummary = budgetSummary,
                recentTransactions = recentTxs,
                currentYear = year,
                currentMonth = month
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
}
