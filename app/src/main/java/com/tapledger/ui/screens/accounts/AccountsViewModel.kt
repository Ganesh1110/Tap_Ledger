package com.tapledger.ui.screens.accounts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tapledger.core.database.dao.BudgetPeriodDao
import com.tapledger.core.database.dao.MonthCloseDao
import com.tapledger.core.database.dao.TransactionDao
import com.tapledger.core.database.entity.AccountEntity
import com.tapledger.core.model.AccountType
import com.tapledger.core.model.MoneyFormatter
import com.tapledger.core.model.TransactionStatus
import com.tapledger.core.preferences.LoanPreferences
import com.tapledger.core.util.DateTimeUtils
import com.tapledger.data.repository.AccountRepository
import com.tapledger.data.repository.TransactionRepository
import com.tapledger.domain.calculator.AccountBalanceCalculator
import com.tapledger.ui.charts.builder.AccountsChartBuilder
import com.tapledger.ui.charts.builder.NetWorthRange
import com.tapledger.ui.charts.model.ChartState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import kotlin.math.abs

/**
 * ViewModel for Accounts & Balances screen per Flow.md §17.2 V9 & V10.
 * Pure presentation over AccountBalanceCalculator and AccountsChartBuilder.
 * End-to-end Long paise and Int permille/bp. Zero Float, Double, or BigDecimal.
 */
@HiltViewModel
class AccountsViewModel @Inject constructor(
    private val accountRepository: AccountRepository,
    private val transactionRepository: TransactionRepository,
    private val transactionDao: TransactionDao,
    private val monthCloseDao: MonthCloseDao,
    private val budgetPeriodDao: BudgetPeriodDao,
    private val loanPreferences: LoanPreferences
) : ViewModel() {

    private val _uiState = MutableStateFlow(AccountsUiState())
    val uiState: StateFlow<AccountsUiState> = _uiState.asStateFlow()

    init {
        loadData()
        viewModelScope.launch {
            transactionRepository.observeAllTransactions().collect {
                loadData()
            }
        }
        viewModelScope.launch {
            loanPreferences.configFlow.collect {
                loadData()
            }
        }
    }

    fun loadData() {
        viewModelScope.launch {
            try {
                val accounts = accountRepository.getAllAccountsSync()
                val allTxs = transactionDao.getAllTransactionsSync()
                val nowIST = DateTimeUtils.nowEpochMillis()
                val loanConfig = loanPreferences.getConfig()

                val allSummaries = accounts.map { account ->
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
                    AccountBalanceCalculator.calculateAccountBalance(effectiveAccount, txs, asOfEpochMillis = nowIST)
                }

                val accountSummaries = if (loanConfig.loanTrackerEnabled) {
                    allSummaries
                } else {
                    allSummaries.filter { it.account.type != AccountType.LOAN }
                }

                val assets = accountSummaries.filter { it.account.type != AccountType.CREDIT_CARD && it.account.type != AccountType.LOAN }
                val liabilities = accountSummaries.filter {
                    it.account.type == AccountType.CREDIT_CARD || (loanConfig.includeLoansInNetWorth && it.account.type == AccountType.LOAN)
                }
                val totalAssets = assets.sumOf { it.currentBalancePaise }
                val totalLiabilities = liabilities.sumOf { abs(it.currentBalancePaise) }
                val netWorth = Math.subtractExact(totalAssets, totalLiabilities)

                val monthCloses = monthCloseDao.getAllMonthClosesSync()
                val periods = budgetPeriodDao.getAllPeriodsSync()
                val range = _uiState.value.selectedRange

                val effectiveAccounts = accounts.filter {
                    when (it.type) {
                        AccountType.LOAN -> loanConfig.loanTrackerEnabled && loanConfig.includeLoansInNetWorth
                        else -> true
                    }
                }

                val (netWorthChart, composition) = withContext(Dispatchers.Default) {
                    val nw = AccountsChartBuilder.buildNetWorthChart(
                        range = range,
                        accounts = effectiveAccounts,
                        allTransactions = allTxs,
                        monthCloses = monthCloses,
                        periods = periods,
                        asOfNow = nowIST
                    )
                    val comp = AccountsChartBuilder.buildAccountComposition(
                        accounts = effectiveAccounts,
                        allTransactions = allTxs,
                        asOfNow = nowIST
                    )
                    nw to comp
                }

                val unassignedMessage = when {
                    netWorthChart is ChartState.Data -> netWorthChart.data.unassignedExcludedMessage
                    composition is ChartState.Data -> composition.data.unassignedExcludedMessage
                    else -> null
                }

                _uiState.update { current ->
                    current.copy(
                        isLoading = false,
                        accountSummaries = accountSummaries,
                        totalAssetsPaise = totalAssets,
                        totalLiabilitiesPaise = totalLiabilities,
                        netWorthPaise = netWorth,
                        netWorthChartState = netWorthChart,
                        accountCompositionState = composition,
                        unassignedExcludedMessage = unassignedMessage,
                        errorMessage = null
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = e.message ?: "Failed to load account analytics"
                    )
                }
            }
        }
    }

    fun setRange(range: NetWorthRange) {
        _uiState.update { it.copy(selectedRange = range) }
        loadData()
    }

    fun toggleStackedView() {
        _uiState.update { it.copy(isStackedView = !it.isStackedView) }
    }

    fun toggleShowTable() {
        _uiState.update { it.copy(showTable = !it.showTable) }
    }

    suspend fun isOpeningBalanceLocked(account: AccountEntity): Boolean {
        val startOfDay = DateTimeUtils.getStartOfDayEpochMillis(account.openingBalanceDate)
        return budgetPeriodDao.getClosedPeriodEndingAfter(startOfDay) != null
    }

    fun correctAccountBalance(account: AccountEntity, targetBalancePaise: Long, notes: String = "Balance correction") {
        viewModelScope.launch {
            try {
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
                val current = AccountBalanceCalculator.calculateAccountBalance(
                    account = effectiveAccount,
                    transactions = txs,
                    asOfEpochMillis = DateTimeUtils.nowEpochMillis()
                )
                val delta = targetBalancePaise - current.currentBalancePaise
                if (delta != 0L) {
                    transactionRepository.recordBalanceAdjustment(account.id, delta, notes)
                    loadData()
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = e.message ?: "Failed to correct balance") }
            }
        }
    }

    fun updateAccountOpeningBalance(account: AccountEntity, newOpeningBalanceRupees: String) {
        viewModelScope.launch {
            try {
                if (isOpeningBalanceLocked(account)) {
                    _uiState.update {
                        it.copy(errorMessage = "Opening balance is locked because this period is closed. To adjust today's balance, use Correct Balance.")
                    }
                    return@launch
                }
                val newPaise = MoneyFormatter.toPaise(newOpeningBalanceRupees)
                val txs = transactionDao.getBalanceTransactionsForAccount(account.id, 0L)
                val earliestTx = txs.filter { it.status != TransactionStatus.IGNORED && it.status != TransactionStatus.VOIDED }.minOfOrNull { it.occurredAt }
                val effectiveOpeningDate = if (earliestTx != null && earliestTx < account.openingBalanceDate) {
                    DateTimeUtils.getStartOfDayEpochMillis(earliestTx)
                } else {
                    account.openingBalanceDate
                }
                val updated = account.copy(
                    openingBalancePaise = newPaise,
                    openingBalanceDate = effectiveOpeningDate,
                    updatedAt = DateTimeUtils.nowEpochMillis()
                )
                accountRepository.updateAccount(updated)
                loadData()
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = "Invalid starting balance amount") }
            }
        }
    }

    fun recordTransfer(
        fromAccountId: Long,
        toAccountId: Long,
        amountRupees: String,
        note: String? = null
    ) {
        viewModelScope.launch {
            try {
                if (fromAccountId == toAccountId) {
                    _uiState.update { it.copy(errorMessage = "Source and destination accounts must be different") }
                    return@launch
                }
                val amountPaise = try {
                    MoneyFormatter.toPaise(amountRupees)
                } catch (e: Exception) {
                    _uiState.update { it.copy(errorMessage = "Invalid transfer amount") }
                    return@launch
                }
                if (amountPaise <= 0L) {
                    _uiState.update { it.copy(errorMessage = "Transfer amount must be greater than zero") }
                    return@launch
                }
                transactionRepository.recordTransfer(
                    fromAccountId = fromAccountId,
                    toAccountId = toAccountId,
                    amountPaise = amountPaise,
                    occurredAt = DateTimeUtils.nowEpochMillis(),
                    note = note?.trim()?.ifBlank { null }
                )
                loadData()
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = e.message ?: "Failed to record transfer") }
            }
        }
    }

    fun clearErrorMessage() {
        _uiState.update { it.copy(errorMessage = null) }
    }
}
