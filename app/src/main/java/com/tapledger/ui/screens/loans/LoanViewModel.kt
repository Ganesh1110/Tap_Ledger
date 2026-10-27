package com.tapledger.ui.screens.loans

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tapledger.core.database.dao.BudgetPeriodDao
import com.tapledger.core.database.entity.AccountEntity
import com.tapledger.core.database.entity.LoanEntity
import com.tapledger.core.model.AccountType
import com.tapledger.core.model.MoneyFormatter
import com.tapledger.core.util.DateTimeUtils
import com.tapledger.data.repository.AccountRepository
import com.tapledger.data.repository.BudgetRepository
import com.tapledger.data.repository.EmiPaymentResult
import com.tapledger.data.repository.LoanRepository
import com.tapledger.domain.calculator.AmortizationInstallment
import com.tapledger.domain.calculator.LoanAffordabilityResult
import com.tapledger.domain.calculator.LoanCalculator
import com.tapledger.domain.calculator.PrepaymentSimulationResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class LoanUiState(
    val selectedTab: Int = 0, // 0: Active Loans, 1: Setup & Affordability, 2: Amortization, 3: Prepayment Simulator
    val activeLoans: List<LoanEntity> = emptyList(),
    val bankAccounts: List<AccountEntity> = emptyList(),
    val selectedLoan: LoanEntity? = null,
    val amortizationSchedule: List<AmortizationInstallment> = emptyList(),

    // Setup Form
    val setupName: String = "",
    val setupPrincipalRupees: String = "",
    val setupAnnualRatePercent: String = "9.5",
    val setupTenureMonths: String = "24",
    val setupEmiDay: String = "5",
    val setupBankAccountId: Long? = null,
    val liveCalculatedEmiPaise: Long = 0L,

    // Affordability Check
    val affordabilityMonthlyIncomeRupees: String = "75000",
    val affordabilityResult: LoanAffordabilityResult? = null,

    // Prepayment Simulator
    val prepaymentAmountRupees: String = "25000",
    val prepaymentSimulationResult: PrepaymentSimulationResult? = null,

    // Actions & Feedback
    val isPayingEmi: Boolean = false,
    val lastEmiPaymentResult: EmiPaymentResult? = null,
    val successMessage: String? = null,
    val errorMessage: String? = null,
    val isLoading: Boolean = false
)

@HiltViewModel
class LoanViewModel @Inject constructor(
    private val loanRepository: LoanRepository,
    private val accountRepository: AccountRepository,
    private val budgetRepository: BudgetRepository,
    private val budgetPeriodDao: BudgetPeriodDao,
    private val loanCalculator: LoanCalculator
) : ViewModel() {

    private val _uiState = MutableStateFlow(LoanUiState())
    val uiState: StateFlow<LoanUiState> = _uiState.asStateFlow()

    init {
        loadData()
        observeLoans()
    }

    private fun observeLoans() {
        viewModelScope.launch {
            loanRepository.observeAllActiveLoans().collect { loans ->
                _uiState.update { current ->
                    val selected = current.selectedLoan?.let { sel -> loans.find { it.id == sel.id } } ?: loans.firstOrNull()
                    current.copy(
                        activeLoans = loans,
                        selectedLoan = selected
                    )
                }
                updateAmortizationAndSimulation()
            }
        }
    }

    private fun loadData() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val accounts = withContext(Dispatchers.Default) { accountRepository.getAllAccountsSync() }
            val bankAccounts = accounts.filter { it.type == AccountType.BANK }

            val now = DateTimeUtils.nowEpochMillis()
            val currentPeriod = withContext(Dispatchers.Default) {
                budgetPeriodDao.getPeriodForTimestamp(now) ?: budgetPeriodDao.getEarliestPeriod()
            }
            val monthlyIncomeRupees = if (currentPeriod != null) {
                val allocs = withContext(Dispatchers.Default) { budgetRepository.getAllocationsForPeriodSync(currentPeriod.id) }
                val paise = allocs.sumOf { it.amountPaise }
                if (paise > 0L) (paise / 100L).toString() else "75000"
            } else "75000"

            _uiState.update {
                it.copy(
                    bankAccounts = bankAccounts,
                    setupBankAccountId = bankAccounts.firstOrNull()?.id,
                    affordabilityMonthlyIncomeRupees = monthlyIncomeRupees,
                    isLoading = false
                )
            }
            recalculateLiveEmiAndAffordability()
        }
    }

    fun selectTab(index: Int) {
        _uiState.update { it.copy(selectedTab = index) }
    }

    fun selectLoan(loan: LoanEntity) {
        _uiState.update { it.copy(selectedLoan = loan) }
        updateAmortizationAndSimulation()
    }

    fun onSetupFieldChanged(
        name: String? = null,
        principalRupees: String? = null,
        ratePercent: String? = null,
        tenureMonths: String? = null,
        emiDay: String? = null,
        bankAccountId: Long? = null
    ) {
        _uiState.update { current ->
            current.copy(
                setupName = name ?: current.setupName,
                setupPrincipalRupees = principalRupees ?: current.setupPrincipalRupees,
                setupAnnualRatePercent = ratePercent ?: current.setupAnnualRatePercent,
                setupTenureMonths = tenureMonths ?: current.setupTenureMonths,
                setupEmiDay = emiDay ?: current.setupEmiDay,
                setupBankAccountId = bankAccountId ?: current.setupBankAccountId
            )
        }
        recalculateLiveEmiAndAffordability()
    }

    fun onAffordabilityIncomeChanged(incomeRupees: String) {
        _uiState.update { it.copy(affordabilityMonthlyIncomeRupees = incomeRupees) }
        recalculateLiveEmiAndAffordability()
    }

    fun onPrepaymentAmountChanged(amountRupees: String) {
        _uiState.update { it.copy(prepaymentAmountRupees = amountRupees) }
        updateAmortizationAndSimulation()
    }

    private fun recalculateLiveEmiAndAffordability() {
        val state = _uiState.value
        val principalPaise = try {
            MoneyFormatter.parseRupeesStringToPaise(state.setupPrincipalRupees, allowZero = true)
        } catch (e: Exception) { 0L }

        val rateBp = parseRateToBp(state.setupAnnualRatePercent)
        val tenure = state.setupTenureMonths.toIntOrNull() ?: 12

        val liveEmi = if (principalPaise > 0L && tenure > 0) {
            loanCalculator.calculateEmi(principalPaise, rateBp, tenure)
        } else 0L

        val incomePaise = try {
            MoneyFormatter.parseRupeesStringToPaise(state.affordabilityMonthlyIncomeRupees, allowZero = true)
        } catch (e: Exception) { 75_000_00L }

        val existingDebtPaise = state.activeLoans.sumOf { it.emiAmountPaise }

        val affResult = if (principalPaise > 0L && tenure > 0 && incomePaise > 0L) {
            loanCalculator.checkAffordability(
                monthlyIncomePaise = incomePaise,
                existingMonthlyDebtPaise = existingDebtPaise,
                newPrincipalPaise = principalPaise,
                annualRateBp = rateBp,
                tenureMonths = tenure
            )
        } else null

        _uiState.update {
            it.copy(
                liveCalculatedEmiPaise = liveEmi,
                affordabilityResult = affResult
            )
        }
    }

    private fun updateAmortizationAndSimulation() {
        val loan = _uiState.value.selectedLoan ?: return
        val schedule = loanCalculator.generateAmortizationSchedule(
            principalPaise = loan.principalPaise,
            annualRateBp = loan.annualInterestRateBp,
            tenureMonths = loan.tenureMonths,
            startDateMs = loan.startDateMs
        )

        val prepayPaise = try {
            MoneyFormatter.parseRupeesStringToPaise(_uiState.value.prepaymentAmountRupees, allowZero = true)
        } catch (e: Exception) { 0L }

        val simulation = if (prepayPaise > 0L) {
            loanCalculator.simulatePartPrepayment(
                currentOutstandingPaise = loan.principalPaise,
                annualRateBp = loan.annualInterestRateBp,
                currentEmiPaise = loan.emiAmountPaise,
                remainingTenureMonths = loan.tenureMonths,
                prepaymentPaise = prepayPaise
            )
        } else null

        _uiState.update {
            it.copy(
                amortizationSchedule = schedule,
                prepaymentSimulationResult = simulation
            )
        }
    }

    fun createLoan() {
        viewModelScope.launch {
            val state = _uiState.value
            val name = state.setupName.trim()
            if (name.isEmpty()) {
                _uiState.update { it.copy(errorMessage = "Please enter a loan name (e.g. Home Loan, Car Loan)") }
                return@launch
            }

            val principalPaise = try {
                MoneyFormatter.parseRupeesStringToPaise(state.setupPrincipalRupees)
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = "Invalid loan principal amount") }
                return@launch
            }

            val rateBp = parseRateToBp(state.setupAnnualRatePercent)
            val tenureMonths = state.setupTenureMonths.toIntOrNull() ?: 12
            val emiDay = (state.setupEmiDay.toIntOrNull() ?: 5).coerceIn(1, 31)

            try {
                val created = loanRepository.createLoan(
                    name = name,
                    principalPaise = principalPaise,
                    annualRateBp = rateBp,
                    tenureMonths = tenureMonths,
                    emiDay = emiDay,
                    linkedBankAccountId = state.setupBankAccountId
                )
                _uiState.update {
                    it.copy(
                        selectedTab = 0,
                        selectedLoan = created,
                        setupName = "",
                        setupPrincipalRupees = "",
                        successMessage = "Loan '${created.name}' created successfully! Monthly EMI: ${MoneyFormatter.formatPaiseToRupees(created.emiAmountPaise)}"
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = e.message ?: "Failed to create loan") }
            }
        }
    }

    fun payEmi(loan: LoanEntity, fromAccountId: Long) {
        viewModelScope.launch {
            _uiState.update { it.copy(isPayingEmi = true, errorMessage = null) }
            try {
                val result = loanRepository.payEmi(
                    loanId = loan.id,
                    fromAccountId = fromAccountId
                )
                _uiState.update {
                    it.copy(
                        isPayingEmi = false,
                        lastEmiPaymentResult = result,
                        successMessage = "EMI of ${MoneyFormatter.formatPaiseToRupees(result.totalPaidPaise)} paid! (${MoneyFormatter.formatPaiseToRupees(result.principalPaidPaise)} Principal + ${MoneyFormatter.formatPaiseToRupees(result.interestPaidPaise)} Interest recorded as Needs expense)"
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isPayingEmi = false,
                        errorMessage = e.message ?: "Failed to record EMI payment"
                    )
                }
            }
        }
    }

    fun dismissMessages() {
        _uiState.update { it.copy(successMessage = null, errorMessage = null) }
    }

    private fun parseRateToBp(rateString: String): Int {
        val clean = rateString.trim().replace("%", "")
        if (clean.isEmpty()) return 950
        val parts = clean.split(".")
        val whole = parts[0].toIntOrNull() ?: 0
        val frac = if (parts.size > 1) {
            val f = parts[1].take(2).padEnd(2, '0')
            f.toIntOrNull() ?: 0
        } else 0
        return (whole * 100) + frac
    }
}
