package com.tapledger.ui.screens.transactions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tapledger.core.database.entity.TransactionEntity
import com.tapledger.data.repository.TransactionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TransactionsViewModel @Inject constructor(
    private val transactionRepository: TransactionRepository
) : ViewModel() {

    private val _transactions = MutableStateFlow<List<TransactionEntity>>(emptyList())
    val transactions: StateFlow<List<TransactionEntity>> = _transactions.asStateFlow()

    init {
        viewModelScope.launch {
            transactionRepository.observeAllTransactions().collectLatest { list ->
                _transactions.value = list
            }
        }
    }

    fun voidTransaction(transactionId: Long, reason: String = "User voided") {
        viewModelScope.launch {
            transactionRepository.voidTransaction(transactionId, reason)
        }
    }

    fun restoreTransaction(transactionId: Long, reason: String = "User restored") {
        viewModelScope.launch {
            transactionRepository.restoreTransaction(transactionId, reason)
        }
    }
}
