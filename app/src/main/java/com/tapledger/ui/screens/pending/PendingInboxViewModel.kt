package com.tapledger.ui.screens.pending

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tapledger.core.database.entity.CategoryEntity
import com.tapledger.core.database.entity.TransactionEntity
import com.tapledger.data.repository.BudgetRepository
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
    private val budgetRepository: BudgetRepository
) : ViewModel() {

    private val _pendingTransactions = MutableStateFlow<List<TransactionEntity>>(emptyList())
    val pendingTransactions: StateFlow<List<TransactionEntity>> = _pendingTransactions.asStateFlow()

    private val _categories = MutableStateFlow<List<CategoryEntity>>(emptyList())
    val categories: StateFlow<List<CategoryEntity>> = _categories.asStateFlow()

    init {
        viewModelScope.launch {
            transactionRepository.observePendingTransactions().collectLatest {
                _pendingTransactions.value = it
            }
        }
        viewModelScope.launch {
            _categories.value = budgetRepository.getAllCategoriesSync()
        }
    }

    fun categorizeTransaction(transactionId: Long, categoryId: Long, amountPaise: Long) {
        viewModelScope.launch {
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
