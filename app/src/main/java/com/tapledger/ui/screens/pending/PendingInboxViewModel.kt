package com.tapledger.ui.screens.pending

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tapledger.core.database.entity.AccountEntity
import com.tapledger.core.database.entity.CategoryEntity
import com.tapledger.core.database.entity.TransactionEntity
import com.tapledger.data.repository.AccountRepository
import com.tapledger.data.repository.BudgetRepository
import com.tapledger.data.repository.MerchantRepository
import com.tapledger.data.repository.TransactionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PendingInboxViewModel @Inject constructor(
    private val transactionRepository: TransactionRepository,
    private val budgetRepository: BudgetRepository,
    private val merchantRepository: MerchantRepository,
    private val accountRepository: AccountRepository
) : ViewModel() {

    private val _pendingTransactions = MutableStateFlow<List<TransactionEntity>>(emptyList())
    val pendingTransactions: StateFlow<List<TransactionEntity>> = _pendingTransactions.asStateFlow()

    private val _categories = MutableStateFlow<List<CategoryEntity>>(emptyList())
    val categories: StateFlow<List<CategoryEntity>> = _categories.asStateFlow()

    private val _accounts = MutableStateFlow<List<AccountEntity>>(emptyList())
    val accounts: StateFlow<List<AccountEntity>> = _accounts.asStateFlow()

    private val _suggestedCategoryMap = MutableStateFlow<Map<Long, Long>>(emptyMap())
    val suggestedCategoryMap: StateFlow<Map<Long, Long>> = _suggestedCategoryMap.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    init {
        viewModelScope.launch {
            transactionRepository.observePendingTransactions().collectLatest { txList ->
                _pendingTransactions.value = txList
                val suggestions = mutableMapOf<Long, Long>()
                for (tx in txList) {
                    val suggestedCatId = merchantRepository.getSuggestedCategoryId(tx.merchantRaw)
                    if (suggestedCatId != null) {
                        suggestions[tx.id] = suggestedCatId
                    }
                }
                _suggestedCategoryMap.value = suggestions
            }
        }
        viewModelScope.launch {
            _categories.value = budgetRepository.getAllCategoriesSync()
        }
        viewModelScope.launch {
            accountRepository.getAllActiveAccounts().collectLatest {
                _accounts.value = it
            }
        }
    }

    fun assignAccount(transactionId: Long, accountId: Long) {
        viewModelScope.launch {
            try {
                transactionRepository.assignAccount(transactionId, accountId)
                val tx = _pendingTransactions.value.find { it.id == transactionId }
                if (tx != null && (tx.kind == com.tapledger.core.model.TransactionKind.INCOME || tx.kind == com.tapledger.core.model.TransactionKind.INTEREST_EARNED)) {
                    transactionRepository.confirmTransaction(transactionId, "Auto-confirmed income upon account assignment")
                }
            } catch (e: Exception) {
                _errorMessage.value = e.message ?: "Failed to assign account"
            }
        }
    }

    fun confirmTransaction(transactionId: Long) {
        viewModelScope.launch {
            try {
                transactionRepository.confirmTransaction(transactionId, "Confirmed from Pending Inbox")
            } catch (e: Exception) {
                _errorMessage.value = e.message ?: "Failed to confirm transaction"
            }
        }
    }

    fun categorizeTransaction(
        transactionId: Long,
        categoryId: Long,
        amountPaise: Long,
        accountId: Long? = null
    ) {
        viewModelScope.launch {
            try {
                val tx = _pendingTransactions.value.find { it.id == transactionId }
                val targetAccountId = accountId ?: tx?.accountId
                if (targetAccountId == null) {
                    _errorMessage.value = "Please select an account for this transaction first"
                    return@launch
                }

                if (tx?.accountId == null) {
                    transactionRepository.assignAccount(transactionId, targetAccountId)
                }

                tx?.merchantRaw?.let { merchant ->
                    merchantRepository.rememberMerchantCategory(merchant, categoryId)
                }

                transactionRepository.splitTransaction(
                    transactionId = transactionId,
                    allocations = listOf(Pair(categoryId, amountPaise)),
                    reason = "Categorized via Pending Inbox"
                )
            } catch (e: Exception) {
                _errorMessage.value = e.message ?: "Failed to categorize transaction"
            }
        }
    }

    fun voidTransaction(transactionId: Long) {
        viewModelScope.launch {
            try {
                transactionRepository.voidTransaction(transactionId, "Voided from Pending Inbox")
            } catch (e: Exception) {
                _errorMessage.value = e.message ?: "Failed to void transaction"
            }
        }
    }

    fun clearErrorMessage() {
        _errorMessage.value = null
    }
}
