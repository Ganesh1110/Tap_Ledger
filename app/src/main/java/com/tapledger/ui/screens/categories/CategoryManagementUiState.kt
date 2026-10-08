package com.tapledger.ui.screens.categories

import com.tapledger.core.database.entity.BudgetAdjustmentEntity
import com.tapledger.core.database.entity.CategoryEntity

data class CategoryItemState(
    val category: CategoryEntity,
    val currentAllocationPaise: Long = 0L
)

data class CategoryManagementUiState(
    val isLoading: Boolean = true,
    val currentPeriodId: Long = 0L,
    val currentYear: Int = 0,
    val currentMonth: Int = 0,
    val categories: List<CategoryItemState> = emptyList(),
    val adjustments: List<BudgetAdjustmentEntity> = emptyList(),
    val errorMessage: String? = null
)
