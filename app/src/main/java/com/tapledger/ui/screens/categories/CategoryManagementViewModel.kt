package com.tapledger.ui.screens.categories

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tapledger.core.model.AdvancePolicy
import com.tapledger.core.model.BudgetBucket
import com.tapledger.core.model.MoneyFormatter
import com.tapledger.core.util.DateTimeUtils
import com.tapledger.data.repository.BudgetRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

@HiltViewModel
class CategoryManagementViewModel @Inject constructor(
    private val budgetRepository: BudgetRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(CategoryManagementUiState())
    val uiState: StateFlow<CategoryManagementUiState> = _uiState.asStateFlow()

    init {
        loadData()
    }

    fun loadData() {
        viewModelScope.launch {
            try {
                val today = LocalDate.now(DateTimeUtils.IST_ZONE)
                val currentYear = today.year
                val currentMonth = today.monthValue

                val period = budgetRepository.getOrCreateBudgetPeriod(currentYear, currentMonth)

                combine(
                    budgetRepository.getAllActiveCategories(),
                    budgetRepository.observeAllocationsForPeriod(period.id),
                    budgetRepository.observeAdjustmentsForPeriod(period.id)
                ) { categories, allocations, adjustments ->
                    val allocationMap = allocations.associateBy { it.categoryId }
                    val itemStates = categories.map { cat ->
                        CategoryItemState(
                            category = cat,
                            currentAllocationPaise = allocationMap[cat.id]?.amountPaise ?: 0L
                        )
                    }
                    CategoryManagementUiState(
                        isLoading = false,
                        currentPeriodId = period.id,
                        currentYear = currentYear,
                        currentMonth = currentMonth,
                        categories = itemStates,
                        adjustments = adjustments,
                        errorMessage = null
                    )
                }.collect { state ->
                    _uiState.value = state
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = e.message ?: "Failed to load category budget data"
                    )
                }
            }
        }
    }

    fun addCategory(
        name: String,
        bucket: BudgetBucket,
        advancePolicy: AdvancePolicy,
        initialBudgetRupees: String
    ) {
        viewModelScope.launch {
            try {
                if (name.isBlank()) {
                    _uiState.update { it.copy(errorMessage = "Category name cannot be empty") }
                    return@launch
                }
                val amountPaise = try {
                    if (initialBudgetRupees.isBlank()) 0L else MoneyFormatter.toPaise(initialBudgetRupees)
                } catch (e: Exception) {
                    0L
                }

                val periodId = _uiState.value.currentPeriodId
                budgetRepository.createCategory(
                    name = name,
                    defaultBucket = bucket,
                    defaultAdvancePolicy = advancePolicy,
                    initialAllocationPaise = amountPaise,
                    periodId = if (periodId > 0L) periodId else null
                )
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = e.message ?: "Failed to add category") }
            }
        }
    }

    fun updateCategory(
        categoryId: Long,
        name: String,
        bucket: BudgetBucket,
        advancePolicy: AdvancePolicy,
        newBudgetRupees: String
    ) {
        viewModelScope.launch {
            try {
                val existing = budgetRepository.getCategoryById(categoryId) ?: return@launch
                val updated = existing.copy(
                    name = name.trim(),
                    defaultBucket = bucket,
                    defaultAdvancePolicy = advancePolicy
                )
                budgetRepository.updateCategory(updated)

                val amountPaise = try {
                    if (newBudgetRupees.isBlank()) 0L else MoneyFormatter.toPaise(newBudgetRupees)
                } catch (e: Exception) {
                    0L
                }

                val periodId = _uiState.value.currentPeriodId
                if (periodId > 0L) {
                    budgetRepository.updateBudgetAllocationAmount(periodId, categoryId, amountPaise)
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = e.message ?: "Failed to update category") }
            }
        }
    }

    fun deactivateCategory(categoryId: Long) {
        viewModelScope.launch {
            try {
                budgetRepository.deactivateCategory(categoryId)
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = e.message ?: "Failed to deactivate category") }
            }
        }
    }

    fun transferBudget(
        fromCategoryId: Long,
        toCategoryId: Long,
        amountRupees: String,
        reason: String?
    ) {
        viewModelScope.launch {
            try {
                if (fromCategoryId == toCategoryId) {
                    _uiState.update { it.copy(errorMessage = "Select two different categories for transfer") }
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

                val periodId = _uiState.value.currentPeriodId
                if (periodId <= 0L) return@launch

                // Record audit log entry in budget_adjustments
                budgetRepository.recordBudgetAdjustment(
                    periodId = periodId,
                    fromCategoryId = fromCategoryId,
                    toCategoryId = toCategoryId,
                    amountPaise = amountPaise,
                    reason = reason
                )

                // Adjust allocation snapshot amounts
                val currentFromAlloc = _uiState.value.categories.find { it.category.id == fromCategoryId }?.currentAllocationPaise ?: 0L
                val currentToAlloc = _uiState.value.categories.find { it.category.id == toCategoryId }?.currentAllocationPaise ?: 0L

                val newFromAlloc = (currentFromAlloc - amountPaise).coerceAtLeast(0L)
                val newToAlloc = currentToAlloc + amountPaise

                budgetRepository.updateBudgetAllocationAmount(periodId, fromCategoryId, newFromAlloc)
                budgetRepository.updateBudgetAllocationAmount(periodId, toCategoryId, newToAlloc)
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = e.message ?: "Failed to transfer budget") }
            }
        }
    }

    fun clearErrorMessage() {
        _uiState.update { it.copy(errorMessage = null) }
    }
}
