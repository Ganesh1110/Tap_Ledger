package com.tapledger.ui.screens.manual

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tapledger.core.database.entity.AccountEntity
import com.tapledger.core.database.entity.CategoryEntity
import com.tapledger.core.model.MoneyFormatter
import com.tapledger.core.model.TransactionKind
import com.tapledger.core.util.DateTimeUtils
import com.tapledger.data.repository.AccountRepository
import com.tapledger.data.repository.BudgetRepository
import com.tapledger.data.repository.MerchantRepository
import com.tapledger.data.repository.TransactionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ManualEntryUiState(
    val accounts: List<AccountEntity> = emptyList(),
    val categories: List<CategoryEntity> = emptyList(),
    val selectedKind: TransactionKind = TransactionKind.EXPENSE,
    val selectedAccountId: Long? = null,
    val destinationAccountId: Long? = null, // for Transfer
    val selectedCategoryId: Long? = null,
    val amountRupeesText: String = "",
    val merchantText: String = "",
    val noteText: String = "",
    val selectedDateMillis: Long = DateTimeUtils.nowEpochMillis(),
    val isSuccess: Boolean = false,
    val errorMessage: String? = null
)

@HiltViewModel
class ManualEntryViewModel @Inject constructor(
    private val accountRepository: AccountRepository,
    private val budgetRepository: BudgetRepository,
    private val transactionRepository: TransactionRepository,
    private val merchantRepository: MerchantRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ManualEntryUiState())
    val uiState: StateFlow<ManualEntryUiState> = _uiState.asStateFlow()

    init {
        loadData()
    }

    private fun loadData() {
        viewModelScope.launch {
            val accounts = accountRepository.getAllAccountsSync()
            val categories = budgetRepository.getAllCategoriesSync()
            _uiState.value = _uiState.value.copy(
                accounts = accounts,
                categories = categories,
                selectedAccountId = accounts.firstOrNull()?.id,
                destinationAccountId = accounts.getOrNull(1)?.id,
                selectedCategoryId = categories.firstOrNull()?.id
            )
        }
    }

    fun onKindSelected(kind: TransactionKind) {
        _uiState.value = _uiState.value.copy(selectedKind = kind, errorMessage = null)
    }

    fun onAccountSelected(accountId: Long) {
        _uiState.value = _uiState.value.copy(selectedAccountId = accountId)
    }

    fun onDestinationAccountSelected(accountId: Long) {
        _uiState.value = _uiState.value.copy(destinationAccountId = accountId)
    }

    fun onCategorySelected(categoryId: Long) {
        _uiState.value = _uiState.value.copy(selectedCategoryId = categoryId)
    }

    fun onAmountChanged(amount: String) {
        if (amount.isEmpty() || amount.matches(Regex("""^\d+(\.\d{0,2})?$"""))) {
            _uiState.value = _uiState.value.copy(amountRupeesText = amount)
        }
    }

    fun onMerchantChanged(merchant: String) {
        _uiState.value = _uiState.value.copy(merchantText = merchant)
        if (merchant.isNotBlank()) {
            viewModelScope.launch {
                val suggestedCatId = merchantRepository.getSuggestedCategoryId(merchant)
                if (suggestedCatId != null) {
                    _uiState.value = _uiState.value.copy(selectedCategoryId = suggestedCatId)
                }
            }
        }
    }

    fun onNoteChanged(note: String) {
        _uiState.value = _uiState.value.copy(noteText = note)
    }

    fun onDateSelected(epochMillis: Long) {
        _uiState.value = _uiState.value.copy(selectedDateMillis = epochMillis)
    }

    fun submitTransaction() {
        val state = _uiState.value
        val amountPaise = try {
            MoneyFormatter.toPaise(state.amountRupeesText)
        } catch (e: Exception) {
            0L
        }

        if (amountPaise <= 0L) {
            _uiState.value = state.copy(errorMessage = "Please enter a valid amount")
            return
        }

        val accountId = state.selectedAccountId
        if (accountId == null) {
            _uiState.value = state.copy(errorMessage = "Please select an account")
            return
        }

        viewModelScope.launch {
            try {
                val occurredAt = state.selectedDateMillis
                when (state.selectedKind) {
                    TransactionKind.EXPENSE,
                    TransactionKind.FEE,
                    TransactionKind.INTEREST_CHARGED -> {
                        val categoryId = state.selectedCategoryId
                        if (categoryId != null && state.merchantText.isNotBlank()) {
                            merchantRepository.rememberMerchantCategory(state.merchantText, categoryId)
                        }
                        val allocations = if (categoryId != null) {
                            listOf(Pair(categoryId, amountPaise))
                        } else emptyList()

                        transactionRepository.recordExpense(
                            accountId = accountId,
                            amountPaise = amountPaise,
                            occurredAt = occurredAt,
                            merchantRaw = state.merchantText.ifBlank { null },
                            merchantNormalized = state.merchantText.trim().uppercase().ifBlank { null },
                            note = state.noteText.ifBlank { null },
                            allocations = allocations
                        )
                    }
                    TransactionKind.INCOME,
                    TransactionKind.INTEREST_EARNED -> {
                        transactionRepository.recordIncome(
                            accountId = accountId,
                            amountPaise = amountPaise,
                            occurredAt = occurredAt,
                            merchantRaw = state.merchantText.ifBlank { null },
                            merchantNormalized = state.merchantText.trim().uppercase().ifBlank { null },
                            note = state.noteText.ifBlank { null }
                        )
                    }
                    TransactionKind.TRANSFER -> {
                        val destAccountId = state.destinationAccountId
                        if (destAccountId == null || destAccountId == accountId) {
                            _uiState.value = state.copy(errorMessage = "Select a distinct destination account")
                            return@launch
                        }
                        transactionRepository.recordTransfer(
                            fromAccountId = accountId,
                            toAccountId = destAccountId,
                            amountPaise = amountPaise,
                            occurredAt = occurredAt,
                            note = state.noteText.ifBlank { null }
                        )
                    }
                    TransactionKind.REFUND -> {
                        transactionRepository.recordRefund(
                            accountId = accountId,
                            amountPaise = amountPaise,
                            occurredAt = occurredAt,
                            categoryId = state.selectedCategoryId,
                            merchantRaw = state.merchantText.ifBlank { null },
                            note = state.noteText.ifBlank { null }
                        )
                    }
                    TransactionKind.ADJUSTMENT -> {
                        // Handled via budget adjustment or direct
                    }
                }
                _uiState.value = state.copy(isSuccess = true, errorMessage = null)
            } catch (e: Exception) {
                _uiState.value = state.copy(errorMessage = e.message ?: "Failed to save transaction")
            }
        }
    }
}
