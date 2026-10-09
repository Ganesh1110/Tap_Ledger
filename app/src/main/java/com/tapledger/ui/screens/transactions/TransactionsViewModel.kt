package com.tapledger.ui.screens.transactions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tapledger.core.database.entity.AccountEntity
import com.tapledger.core.database.entity.CategoryEntity
import com.tapledger.core.database.entity.TransactionEntity
import com.tapledger.core.model.TransactionStatus
import com.tapledger.data.repository.AccountRepository
import com.tapledger.data.repository.BudgetRepository
import com.tapledger.data.repository.TransactionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TransactionsUiState(
    val transactions: List<TransactionEntity> = emptyList(),
    val filteredTransactions: List<TransactionEntity> = emptyList(),
    val categoriesMap: Map<Long, CategoryEntity> = emptyMap(),
    val accountsMap: Map<Long, AccountEntity> = emptyMap(),
    val searchQuery: String = "",
    val selectedStatus: TransactionStatus? = null,
    val isLoading: Boolean = true
)

@HiltViewModel
class TransactionsViewModel @Inject constructor(
    private val transactionRepository: TransactionRepository,
    private val accountRepository: AccountRepository,
    private val budgetRepository: BudgetRepository
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedStatus = MutableStateFlow<TransactionStatus?>(null)
    val selectedStatus: StateFlow<TransactionStatus?> = _selectedStatus.asStateFlow()

    private val _categoriesMap = MutableStateFlow<Map<Long, CategoryEntity>>(emptyMap())
    private val _accountsMap = MutableStateFlow<Map<Long, AccountEntity>>(emptyMap())

    val uiState: StateFlow<TransactionsUiState> = combine(
        transactionRepository.observeAllTransactions(),
        _searchQuery,
        _selectedStatus,
        _categoriesMap,
        _accountsMap
    ) { txList, query, status, catMap, accMap ->
        val filtered = txList.filter { tx ->
            val matchesStatus = (status == null || tx.status == status)
            val matchesQuery = if (query.isBlank()) true else {
                val q = query.trim().lowercase()
                (tx.merchantRaw?.lowercase()?.contains(q) == true) ||
                        (tx.note?.lowercase()?.contains(q) == true) ||
                        tx.amountPaise.toString().contains(q)
            }
            matchesStatus && matchesQuery
        }

        TransactionsUiState(
            transactions = txList,
            filteredTransactions = filtered,
            categoriesMap = catMap,
            accountsMap = accMap,
            searchQuery = query,
            selectedStatus = status,
            isLoading = false
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = TransactionsUiState()
    )

    init {
        viewModelScope.launch {
            val cats = budgetRepository.getAllCategoriesSync().associateBy { it.id }
            _categoriesMap.value = cats
            val accs = accountRepository.getAllAccountsSync().associateBy { it.id }
            _accountsMap.value = accs
        }
    }

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
    }

    fun onStatusSelected(status: TransactionStatus?) {
        _selectedStatus.value = status
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
