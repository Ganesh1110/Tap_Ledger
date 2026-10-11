package com.tapledger.ui.screens.onboarding

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tapledger.core.database.ReferenceCategories
import com.tapledger.core.database.dao.CategoryDao
import com.tapledger.core.database.entity.CategoryEntity
import com.tapledger.core.model.AccountType
import com.tapledger.core.model.BudgetBucket
import com.tapledger.core.model.MoneyFormatter
import com.tapledger.core.util.DateTimeUtils
import com.tapledger.domain.calculator.AccountBalanceCalculator
import com.tapledger.domain.model.NetWorthSummary
import com.tapledger.domain.onboarding.OnboardingCommitter
import com.tapledger.domain.onboarding.OnboardingDistribution
import com.tapledger.domain.onboarding.OnboardingDraft
import com.tapledger.domain.onboarding.OnboardingValidator
import com.tapledger.domain.onboarding.StagedAccount
import com.tapledger.domain.onboarding.StagedCreditCard
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.Serializable
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject

enum class OnboardingStep(val index: Int) {
    O1_WELCOME(0),
    O2_INCOME(1),
    O3_ACCOUNTS(2),
    O4_BUDGET(3),
    O5_RELIABILITY(4),
    O6_REVIEW(5)
}

data class StagedAccountUi(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val type: AccountType = AccountType.BANK,
    val identifierLast4: String = "",
    val balanceInput: String = "0",
    val isSavings: Boolean = false,
    val error: String? = null
) : Serializable

data class StagedCreditCardUi(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val identifierLast4: String = "",
    val outstandingInput: String = "0",
    val creditLimitInput: String = "",
    val statementDay: Int? = null,
    val dueDay: Int? = null,
    val error: String? = null
) : Serializable

data class OnboardingUiState(
    val step: OnboardingStep = OnboardingStep.O1_WELCOME,
    val incomeInput: String = "",
    val incomePaise: Long = 0L,
    val incomeError: String? = null,
    val asOfDateEpochMillis: Long = 0L,
    val bankAccounts: List<StagedAccountUi> = emptyList(),
    val cashBalanceInput: String = "0",
    val cashBalancePaise: Long = 0L,
    val cashError: String? = null,
    val creditCards: List<StagedCreditCardUi> = emptyList(),
    val categories: List<CategoryEntity> = emptyList(),
    val categoryAllocations: Map<Long, Long> = emptyMap(),
    val isCommitting: Boolean = false,
    val commitError: String? = null,
    val isCommitted: Boolean = false
)

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val committer: OnboardingCommitter,
    private val categoryDao: CategoryDao
) : ViewModel() {

    private val _uiState = MutableStateFlow(OnboardingUiState())
    val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()

    init {
        val todayStartOfDay = DateTimeUtils.getStartOfDayEpochMillis(DateTimeUtils.nowEpochMillis())
        val initialStepIndex = savedStateHandle.get<Int>("step_index") ?: 0
        val savedIncome = savedStateHandle.get<String>("income_input") ?: ""
        val savedAsOfDate = savedStateHandle.get<Long>("as_of_date") ?: todayStartOfDay
        val savedBankAccounts = savedStateHandle.get<ArrayList<StagedAccountUi>>("bank_accounts") ?: arrayListOf(
            StagedAccountUi(name = "Main Bank", type = AccountType.BANK, balanceInput = "0")
        )
        val savedCash = savedStateHandle.get<String>("cash_balance") ?: "0"
        val savedCards = savedStateHandle.get<ArrayList<StagedCreditCardUi>>("credit_cards") ?: arrayListOf()
        val savedAllocations = savedStateHandle.get<HashMap<Long, Long>>("allocations") ?: hashMapOf()

        val parsedIncome = try { MoneyFormatter.parseOpeningBalance(savedIncome) } catch (_: Exception) { 0L }
        val parsedCash = try { MoneyFormatter.parseOpeningBalance(savedCash) } catch (_: Exception) { 0L }

        _uiState.update {
            it.copy(
                step = OnboardingStep.entries.getOrElse(initialStepIndex) { OnboardingStep.O1_WELCOME },
                incomeInput = savedIncome,
                incomePaise = parsedIncome,
                asOfDateEpochMillis = savedAsOfDate,
                bankAccounts = savedBankAccounts,
                cashBalanceInput = savedCash,
                cashBalancePaise = parsedCash,
                creditCards = savedCards,
                categoryAllocations = savedAllocations
            )
        }

        loadCategoriesAndSeedDefaults()
    }

    private fun loadCategoriesAndSeedDefaults() {
        viewModelScope.launch {
            ReferenceCategories.seedIfEmpty(categoryDao)
            val allCats = categoryDao.getAllCategoriesSync().filter { it.isActive }
            _uiState.update { current ->
                val updatedState = current.copy(categories = allCats)
                if (updatedState.categoryAllocations.isEmpty() && updatedState.incomePaise > 0L) {
                    updatedState.copy(categoryAllocations = computeDefaultAllocations(updatedState.incomePaise, allCats))
                } else {
                    updatedState
                }
            }
        }
    }

    private fun computeDefaultAllocations(incomePaise: Long, categories: List<CategoryEntity>): Map<Long, Long> {
        if (incomePaise <= 0L || categories.isEmpty()) return emptyMap()
        val (needs, wants, savings) = OnboardingDistribution.calculateBucketTargets(incomePaise)
        val allocs = mutableMapOf<Long, Long>()
        allocs.putAll(OnboardingDistribution.distributeBucket(needs, categories.filter { it.defaultBucket == BudgetBucket.NEEDS }))
        allocs.putAll(OnboardingDistribution.distributeBucket(wants, categories.filter { it.defaultBucket == BudgetBucket.WANTS }))
        allocs.putAll(OnboardingDistribution.distributeBucket(savings, categories.filter { it.defaultBucket == BudgetBucket.SAVINGS }))
        return allocs
    }

    private fun persistState() {
        val s = _uiState.value
        savedStateHandle["step_index"] = s.step.index
        savedStateHandle["income_input"] = s.incomeInput
        savedStateHandle["as_of_date"] = s.asOfDateEpochMillis
        savedStateHandle["bank_accounts"] = ArrayList(s.bankAccounts)
        savedStateHandle["cash_balance"] = s.cashBalanceInput
        savedStateHandle["credit_cards"] = ArrayList(s.creditCards)
        savedStateHandle["allocations"] = HashMap(s.categoryAllocations)
    }

    fun setStep(step: OnboardingStep) {
        _uiState.update { it.copy(step = step) }
        persistState()
    }

    fun nextStep(): Boolean {
        val current = _uiState.value
        when (current.step) {
            OnboardingStep.O1_WELCOME -> {
                setStep(OnboardingStep.O2_INCOME)
                return true
            }
            OnboardingStep.O2_INCOME -> {
                if (current.incomePaise <= 0L) {
                    _uiState.update { it.copy(incomeError = "Please enter a valid monthly income greater than ₹0") }
                    return false
                }
                _uiState.update { it.copy(incomeError = null) }
                // Pre-populate allocations if empty
                if (current.categoryAllocations.isEmpty()) {
                    val allocs = computeDefaultAllocations(current.incomePaise, current.categories)
                    _uiState.update { it.copy(categoryAllocations = allocs) }
                }
                setStep(OnboardingStep.O3_ACCOUNTS)
                return true
            }
            OnboardingStep.O3_ACCOUNTS -> {
                // Validate accounts
                val nameList = mutableListOf<String>()
                var hasError = false
                val validatedBanks = current.bankAccounts.map { acc ->
                    val err = OnboardingValidator.validateAccountName(acc.name, nameList)
                    if (err != null) {
                        hasError = true
                        acc.copy(error = err)
                    } else {
                        nameList.add(acc.name.trim())
                        acc.copy(error = null)
                    }
                }
                val cashErr = OnboardingValidator.validateAccountName("Cash", nameList)
                if (cashErr != null) {
                    hasError = true
                    _uiState.update { it.copy(cashError = "Account name 'Cash' conflicts with another account") }
                } else {
                    nameList.add("Cash")
                    _uiState.update { it.copy(cashError = null) }
                }

                val validatedCards = current.creditCards.map { card ->
                    val err = OnboardingValidator.validateAccountName(card.name, nameList)
                    if (err != null) {
                        hasError = true
                        card.copy(error = err)
                    } else {
                        nameList.add(card.name.trim())
                        card.copy(error = null)
                    }
                }

                _uiState.update { it.copy(bankAccounts = validatedBanks, creditCards = validatedCards) }
                if (hasError) return false

                setStep(OnboardingStep.O4_BUDGET)
                return true
            }
            OnboardingStep.O4_BUDGET -> {
                val totalAllocated = current.categoryAllocations.values.sum()
                if (totalAllocated != current.incomePaise) {
                    // Cannot proceed if allocations don't match planned income
                    return false
                }
                setStep(OnboardingStep.O5_RELIABILITY)
                return true
            }
            OnboardingStep.O5_RELIABILITY -> {
                setStep(OnboardingStep.O6_REVIEW)
                return true
            }
            OnboardingStep.O6_REVIEW -> {
                commit()
                return true
            }
        }
    }

    fun previousStep(): Boolean {
        val current = _uiState.value
        if (current.step.index == 0) return false
        val prevStep = OnboardingStep.entries[current.step.index - 1]
        setStep(prevStep)
        return true
    }

    fun setIncomeInput(input: String) {
        val parsed = try {
            MoneyFormatter.parseOpeningBalance(input)
        } catch (_: Exception) {
            0L
        }
        _uiState.update { current ->
            val updatedAllocs = if (parsed > 0L && current.categories.isNotEmpty()) {
                computeDefaultAllocations(parsed, current.categories)
            } else {
                current.categoryAllocations
            }
            current.copy(
                incomeInput = input,
                incomePaise = parsed,
                incomeError = if (parsed > 0L) null else current.incomeError,
                categoryAllocations = updatedAllocs
            )
        }
        persistState()
    }

    fun setAsOfDate(epochMillis: Long) {
        _uiState.update { it.copy(asOfDateEpochMillis = epochMillis) }
        persistState()
    }

    fun addBankAccount() {
        _uiState.update { current ->
            current.copy(
                bankAccounts = current.bankAccounts + StagedAccountUi(name = "Bank ${current.bankAccounts.size + 1}")
            )
        }
        persistState()
    }

    fun updateBankAccount(index: Int, updated: StagedAccountUi) {
        _uiState.update { current ->
            if (index in current.bankAccounts.indices) {
                val list = current.bankAccounts.toMutableList()
                list[index] = updated
                current.copy(bankAccounts = list)
            } else current
        }
        persistState()
    }

    fun removeBankAccount(index: Int) {
        _uiState.update { current ->
            if (index in current.bankAccounts.indices && current.bankAccounts.size > 1) {
                val list = current.bankAccounts.toMutableList()
                list.removeAt(index)
                current.copy(bankAccounts = list)
            } else current
        }
        persistState()
    }

    fun setCashBalanceInput(input: String) {
        val parsed = try {
            MoneyFormatter.parseOpeningBalance(input)
        } catch (_: Exception) {
            0L
        }
        _uiState.update { it.copy(cashBalanceInput = input, cashBalancePaise = parsed) }
        persistState()
    }

    fun addCreditCard() {
        _uiState.update { current ->
            current.copy(
                creditCards = current.creditCards + StagedCreditCardUi(name = "Credit Card ${current.creditCards.size + 1}")
            )
        }
        persistState()
    }

    fun updateCreditCard(index: Int, updated: StagedCreditCardUi) {
        _uiState.update { current ->
            if (index in current.creditCards.indices) {
                val list = current.creditCards.toMutableList()
                list[index] = updated
                current.copy(creditCards = list)
            } else current
        }
        persistState()
    }

    fun removeCreditCard(index: Int) {
        _uiState.update { current ->
            if (index in current.creditCards.indices) {
                val list = current.creditCards.toMutableList()
                list.removeAt(index)
                current.copy(creditCards = list)
            } else current
        }
        persistState()
    }

    fun setCategoryAllocation(categoryId: Long, amountPaise: Long) {
        _uiState.update { current ->
            val updated = current.categoryAllocations.toMutableMap()
            updated[categoryId] = maxOf(0L, amountPaise)
            current.copy(categoryAllocations = updated)
        }
        persistState()
    }

    fun resetAllocationsToDefault() {
        val s = _uiState.value
        val defaults = computeDefaultAllocations(s.incomePaise, s.categories)
        _uiState.update { it.copy(categoryAllocations = defaults) }
        persistState()
    }

    fun getNetWorthPreview(): NetWorthSummary {
        val s = _uiState.value
        val balances = mutableListOf<Long>()
        for (b in s.bankAccounts) {
            val amt = try { MoneyFormatter.parseOpeningBalance(b.balanceInput) } catch (_: Exception) { 0L }
            balances.add(amt)
        }
        val cash = try { MoneyFormatter.parseOpeningBalance(s.cashBalanceInput) } catch (_: Exception) { 0L }
        balances.add(cash)
        for (c in s.creditCards) {
            val owed = try { MoneyFormatter.parseOpeningBalance(c.outstandingInput) } catch (_: Exception) { 0L }
            balances.add(if (owed == 0L) 0L else -owed)
        }
        return AccountBalanceCalculator.calculateNetWorthFromBalances(balances)
    }

    fun commit() {
        val s = _uiState.value
        if (s.isCommitting || s.isCommitted) return

        viewModelScope.launch {
            _uiState.update { it.copy(isCommitting = true, commitError = null) }
            try {
                val stagedBanks = s.bankAccounts.map { b ->
                    val amt = MoneyFormatter.parseOpeningBalance(b.balanceInput)
                    StagedAccount(
                        name = b.name.trim(),
                        type = b.type,
                        identifierLast4 = b.identifierLast4.trim().ifEmpty { null },
                        balancePaise = amt,
                        isSavings = b.isSavings
                    )
                }
                val cashAmt = MoneyFormatter.parseOpeningBalance(s.cashBalanceInput)
                val stagedCards = s.creditCards.map { c ->
                    val owed = MoneyFormatter.parseOpeningBalance(c.outstandingInput)
                    val limit = if (c.creditLimitInput.isNotBlank()) {
                        MoneyFormatter.parseOpeningBalance(c.creditLimitInput).takeIf { it > 0L }
                    } else null
                    StagedCreditCard(
                        name = c.name.trim(),
                        identifierLast4 = c.identifierLast4.trim().ifEmpty { null },
                        outstandingOwedPaise = owed,
                        creditLimitPaise = limit,
                        statementDay = c.statementDay,
                        dueDay = c.dueDay
                    )
                }

                val draft = OnboardingDraft(
                    monthlyIncomePaise = s.incomePaise,
                    asOfDateEpochMillis = s.asOfDateEpochMillis,
                    bankAccounts = stagedBanks,
                    cashBalancePaise = cashAmt,
                    creditCards = stagedCards,
                    categoryAllocations = s.categoryAllocations
                )

                committer.commit(draft)
                _uiState.update { it.copy(isCommitting = false, isCommitted = true) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isCommitting = false, commitError = e.message ?: "Failed to save setup data") }
            }
        }
    }
}
