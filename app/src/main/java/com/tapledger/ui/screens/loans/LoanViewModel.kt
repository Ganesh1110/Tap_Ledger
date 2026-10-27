package com.tapledger.ui.screens.loans

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tapledger.core.database.dao.BudgetPeriodDao
import com.tapledger.core.database.entity.AccountEntity
import com.tapledger.core.database.entity.LoanEntity
import com.tapledger.core.model.AccountType
import com.tapledger.core.model.MoneyFormatter
import com.tapledger.core.util.DateTimeUtils
import com.tapledger.core.database.entity.LoanScheduleInstallmentEntity
import com.tapledger.core.database.entity.LoanScheduleVersionEntity
import com.tapledger.data.repository.AccountRepository
import com.tapledger.data.repository.BudgetRepository
import com.tapledger.data.repository.EmiPaymentResult
import com.tapledger.data.repository.ForeclosureExecutionResult
import com.tapledger.data.repository.LoanRepository
import com.tapledger.data.repository.PartPrepaymentExecutionResult
import com.tapledger.domain.calculator.AmortizationInstallment
import com.tapledger.domain.calculator.ForeclosureSettlement
import com.tapledger.domain.calculator.InterestCalculationMethod
import com.tapledger.domain.calculator.LoanAffordabilityResult
import com.tapledger.domain.calculator.LoanCalculator
import com.tapledger.domain.calculator.PrepaymentSimulationResult
import com.tapledger.domain.calculator.PrepaymentStrategy
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
    val scheduleVersions: List<LoanScheduleVersionEntity> = emptyList(),
    val selectedScheduleVersionId: Long? = null,

    // Setup Form
    val setupName: String = "",
    val setupPrincipalRupees: String = "",
    val setupAnnualRatePercent: String = "9.5",
    val setupTenureMonths: String = "24",
    val setupEmiDay: String = "5",
    val setupCalculationMethod: InterestCalculationMethod = InterestCalculationMethod.MONTHLY_REDUCING,
    val setupBankAccountId: Long? = null,
    val liveCalculatedEmiPaise: Long = 0L,

    // Affordability Check
    val affordabilityMonthlyIncomeRupees: String = "75000",
    val safeDtiThresholdPercent: String = "30",
    val moderateDtiThresholdPercent: String = "45",
    val affordabilityResult: LoanAffordabilityResult? = null,

    // Prepayment Simulator & Execution
    val prepaymentAmountRupees: String = "25000",
    val prepaymentFixedFeeRupees: String = "0",
    val prepaymentPercentFeePercent: String = "0",
    val prepaymentSimulationResult: PrepaymentSimulationResult? = null,

    // Foreclosure Settlement
    val foreclosureSettlement: ForeclosureSettlement? = null,

    // Actions & Feedback
    val isExecutingAction: Boolean = false,
    val lastEmiPaymentResult: EmiPaymentResult? = null,
    val lastPrepaymentResult: PartPrepaymentExecutionResult? = null,
    val lastForeclosureResult: ForeclosureExecutionResult? = null,
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
        loadVersionsForLoan(loan.id)
        updateAmortizationAndSimulation()
    }

    private fun loadVersionsForLoan(loanId: Long) {
        viewModelScope.launch {
            val versions = withContext(Dispatchers.Default) {
                loanRepository.getScheduleVersionsForLoan(loanId)
            }
            _uiState.update {
                it.copy(
                    scheduleVersions = versions,
                    selectedScheduleVersionId = versions.lastOrNull()?.id
                )
            }
            loadInstallmentsForSelectedVersion()
        }
    }

    fun selectScheduleVersion(versionId: Long) {
        _uiState.update { it.copy(selectedScheduleVersionId = versionId) }
        loadInstallmentsForSelectedVersion()
    }

    private fun loadInstallmentsForSelectedVersion() {
        val versionId = _uiState.value.selectedScheduleVersionId ?: return
        viewModelScope.launch {
            val dbInstallments = withContext(Dispatchers.Default) {
                loanRepository.getInstallmentsForVersion(versionId)
            }
            if (dbInstallments.isNotEmpty()) {
                val schedule = dbInstallments.map { inst ->
                    AmortizationInstallment(
                        monthIndex = inst.monthIndex,
                        dueDateMs = inst.dueDateMs,
                        openingPrincipalPaise = inst.openingPrincipalPaise,
                        emiPaise = inst.emiPaise,
                        interestPaise = inst.interestPaise,
                        principalPaidPaise = inst.principalPaidPaise,
                        closingPrincipalPaise = inst.closingPrincipalPaise
                    )
                }
                _uiState.update { it.copy(amortizationSchedule = schedule) }
            }
        }
    }

    fun onSetupFieldChanged(
        name: String? = null,
        principalRupees: String? = null,
        ratePercent: String? = null,
        tenureMonths: String? = null,
        emiDay: String? = null,
        calculationMethod: InterestCalculationMethod? = null,
        bankAccountId: Long? = null
    ) {
        _uiState.update { current ->
            current.copy(
                setupName = name ?: current.setupName,
                setupPrincipalRupees = principalRupees ?: current.setupPrincipalRupees,
                setupAnnualRatePercent = ratePercent ?: current.setupAnnualRatePercent,
                setupTenureMonths = tenureMonths ?: current.setupTenureMonths,
                setupEmiDay = emiDay ?: current.setupEmiDay,
                setupCalculationMethod = calculationMethod ?: current.setupCalculationMethod,
                setupBankAccountId = bankAccountId ?: current.setupBankAccountId
            )
        }
        recalculateLiveEmiAndAffordability()
    }

    fun onAffordabilityThresholdsChanged(safePercent: String, moderatePercent: String) {
        _uiState.update {
            it.copy(
                safeDtiThresholdPercent = safePercent,
                moderateDtiThresholdPercent = moderatePercent
            )
        }
        recalculateLiveEmiAndAffordability()
    }

    fun onAffordabilityIncomeChanged(incomeRupees: String) {
        _uiState.update { it.copy(affordabilityMonthlyIncomeRupees = incomeRupees) }
        recalculateLiveEmiAndAffordability()
    }

    fun onPrepaymentAmountChanged(
        amountRupees: String,
        fixedFeeRupees: String? = null,
        percentFeePercent: String? = null
    ) {
        _uiState.update {
            it.copy(
                prepaymentAmountRupees = amountRupees,
                prepaymentFixedFeeRupees = fixedFeeRupees ?: it.prepaymentFixedFeeRupees,
                prepaymentPercentFeePercent = percentFeePercent ?: it.prepaymentPercentFeePercent
            )
        }
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

        val safeBp = (state.safeDtiThresholdPercent.toIntOrNull() ?: 30) * 100
        val modBp = (state.moderateDtiThresholdPercent.toIntOrNull() ?: 45) * 100

        val affResult = if (principalPaise > 0L && tenure > 0 && incomePaise > 0L) {
            loanCalculator.checkAffordability(
                monthlyIncomePaise = incomePaise,
                existingMonthlyDebtPaise = existingDebtPaise,
                newPrincipalPaise = principalPaise,
                annualRateBp = rateBp,
                tenureMonths = tenure,
                safeDtiThresholdBp = safeBp,
                moderateDtiThresholdBp = modBp
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

        val prepayPaise = try {
            MoneyFormatter.parseRupeesStringToPaise(_uiState.value.prepaymentAmountRupees, allowZero = true)
        } catch (e: Exception) { 0L }

        val fixedFeePaise = try {
            MoneyFormatter.parseRupeesStringToPaise(_uiState.value.prepaymentFixedFeeRupees, allowZero = true)
        } catch (e: Exception) { 0L }

        val percentFeeBp = parseRateToBp(_uiState.value.prepaymentPercentFeePercent)

        val calcMethod = try {
            InterestCalculationMethod.valueOf(loan.calculationMethod)
        } catch (e: Exception) {
            InterestCalculationMethod.MONTHLY_REDUCING
        }

        val simulation = if (prepayPaise > 0L) {
            loanCalculator.simulatePartPrepayment(
                currentOutstandingPaise = loan.principalPaise,
                annualRateBp = loan.annualInterestRateBp,
                currentEmiPaise = loan.emiAmountPaise,
                remainingTenureMonths = loan.tenureMonths,
                prepaymentPaise = prepayPaise,
                fixedFeePaise = fixedFeePaise,
                percentageFeeBp = percentFeeBp,
                method = calcMethod
            )
        } else null

        val foreclosure = loanCalculator.calculateForeclosureSettlement(
            outstandingPrincipalPaise = loan.principalPaise,
            annualRateBp = loan.annualInterestRateBp,
            daysElapsedSinceLastPayment = 0,
            fixedFeePaise = fixedFeePaise,
            percentageFeeBp = percentFeeBp,
            method = calcMethod
        )

        _uiState.update {
            it.copy(
                prepaymentSimulationResult = simulation,
                foreclosureSettlement = foreclosure
            )
        }
    }

    fun applyPartPrepayment(loan: LoanEntity, fromAccountId: Long, strategy: PrepaymentStrategy) {
        viewModelScope.launch {
            _uiState.update { it.copy(isExecutingAction = true, errorMessage = null) }
            val prepayPaise = try {
                MoneyFormatter.parseRupeesStringToPaise(_uiState.value.prepaymentAmountRupees)
            } catch (e: Exception) {
                _uiState.update { it.copy(isExecutingAction = false, errorMessage = "Invalid prepayment amount") }
                return@launch
            }
            val fixedFeePaise = try {
                MoneyFormatter.parseRupeesStringToPaise(_uiState.value.prepaymentFixedFeeRupees, allowZero = true)
            } catch (e: Exception) { 0L }
            val percentFeeBp = parseRateToBp(_uiState.value.prepaymentPercentFeePercent)

            try {
                val result = loanRepository.recordPartPrepayment(
                    loanId = loan.id,
                    fromAccountId = fromAccountId,
                    totalPaymentPaise = prepayPaise,
                    strategy = strategy,
                    fixedFeePaise = fixedFeePaise,
                    percentageFeeBp = percentFeeBp
                )
                _uiState.update {
                    it.copy(
                        isExecutingAction = false,
                        lastPrepaymentResult = result,
                        successMessage = "Part-payment applied! Principal reduced by ${MoneyFormatter.formatPaiseToRupees(result.allocation.principalReductionPaise)}. Schedule version ${result.scheduleVersionNumber} generated."
                    )
                }
                loadVersionsForLoan(loan.id)
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isExecutingAction = false,
                        errorMessage = e.message ?: "Failed to execute part-prepayment"
                    )
                }
            }
        }
    }

    fun forecloseLoan(loan: LoanEntity, fromAccountId: Long) {
        viewModelScope.launch {
            _uiState.update { it.copy(isExecutingAction = true, errorMessage = null) }
            val fixedFeePaise = try {
                MoneyFormatter.parseRupeesStringToPaise(_uiState.value.prepaymentFixedFeeRupees, allowZero = true)
            } catch (e: Exception) { 0L }
            val percentFeeBp = parseRateToBp(_uiState.value.prepaymentPercentFeePercent)

            try {
                val result = loanRepository.recordForeclosure(
                    loanId = loan.id,
                    fromAccountId = fromAccountId,
                    fixedFeePaise = fixedFeePaise,
                    percentageFeeBp = percentFeeBp
                )
                _uiState.update {
                    it.copy(
                        isExecutingAction = false,
                        lastForeclosureResult = result,
                        successMessage = "Loan '${loan.name}' fully foreclosed and closed! Settlement of ${MoneyFormatter.formatPaiseToRupees(result.settlement.totalSettlementAmountPaise)} completed."
                    )
                }
                loadVersionsForLoan(loan.id)
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isExecutingAction = false,
                        errorMessage = e.message ?: "Failed to foreclose loan"
                    )
                }
            }
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
                    linkedBankAccountId = state.setupBankAccountId,
                    calculationMethod = state.setupCalculationMethod
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
            _uiState.update { it.copy(isExecutingAction = true, errorMessage = null) }
            try {
                val result = loanRepository.payEmi(
                    loanId = loan.id,
                    fromAccountId = fromAccountId
                )
                _uiState.update {
                    it.copy(
                        isExecutingAction = false,
                        lastEmiPaymentResult = result,
                        successMessage = "EMI of ${MoneyFormatter.formatPaiseToRupees(result.totalPaidPaise)} paid! (${MoneyFormatter.formatPaiseToRupees(result.principalPaidPaise)} Principal + ${MoneyFormatter.formatPaiseToRupees(result.interestPaidPaise)} Interest recorded as Needs expense)"
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isExecutingAction = false,
                        errorMessage = e.message ?: "Failed to record EMI payment"
                    )
                }
            }
        }
    }

    fun dismissMessages() {
        _uiState.update { it.copy(successMessage = null, errorMessage = null) }
    }

    fun deleteLoan(loan: LoanEntity) {
        viewModelScope.launch {
            _uiState.update { it.copy(isExecutingAction = true) }
            try {
                loanRepository.deleteLoan(loan.id)
                _uiState.update {
                    it.copy(
                        isExecutingAction = false,
                        selectedLoan = if (it.selectedLoan?.id == loan.id) null else it.selectedLoan,
                        amortizationSchedule = if (it.selectedLoan?.id == loan.id) emptyList() else it.amortizationSchedule,
                        scheduleVersions = if (it.selectedLoan?.id == loan.id) emptyList() else it.scheduleVersions,
                        successMessage = "Loan '${loan.name}' deleted."
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isExecutingAction = false,
                        errorMessage = e.message ?: "Failed to delete loan"
                    )
                }
            }
        }
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
