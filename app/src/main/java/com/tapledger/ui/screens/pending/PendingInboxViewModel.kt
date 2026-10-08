package com.tapledger.ui.screens.pending

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tapledger.core.database.entity.CategoryEntity
import com.tapledger.core.database.entity.TransactionEntity
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
    private val merchantRepository: MerchantRepository
) : ViewModel() {

    private val _pendingTransactions = MutableStateFlow<List<TransactionEntity>>(emptyList())
    val pendingTransactions: StateFlow<List<TransactionEntity>> = _pendingTransactions.asStateFlow()

    private val _categories = MutableStateFlow<List<CategoryEntity>>(emptyList())
    val categories: StateFlow<List<CategoryEntity>> = _categories.asStateFlow()

    private val _suggestedCategoryMap = MutableStateFlow<Map<Long, Long>>(emptyMap())
    val suggestedCategoryMap: StateFlow<Map<Long, Long>> = _suggestedCategoryMap.asStateFlow()

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
    }

    fun categorizeTransaction(transactionId: Long, categoryId: Long, amountPaise: Long) {
        viewModelScope.launch {
            val tx = _pendingTransactions.value.find { it.id == transactionId }
            tx?.merchantRaw?.let { merchant ->
                merchantRepository.rememberMerchantCategory(merchant, categoryId)
            }

            transactionRepository.splitTransaction(
                transactionId = transactionId,
                allocations = listOf(Pair(categoryId, amountPaise)),
                reason = "Categorized via Pending Inbox"
            )
        }
    }

    fun voidTransaction(transactionId: Long) {
        viewModelScope.launch {
            transactionRepository.voidTransaction(transactionId, "Voided from Pending Inbox")
        }
    }
}
