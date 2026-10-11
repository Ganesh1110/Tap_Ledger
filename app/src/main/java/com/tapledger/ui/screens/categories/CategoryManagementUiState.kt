package com.tapledger.ui.screens.categories

import com.tapledger.core.database.entity.BudgetAdjustmentEntity
import com.tapledger.core.database.entity.CategoryEntity
import com.tapledger.ui.charts.model.BulletBarsModel
import com.tapledger.ui.charts.model.CategoryDonutModel
import com.tapledger.ui.charts.model.CategoryDonutSlice
import com.tapledger.ui.charts.model.ChartState

enum class BudgetViewMode {
    ENVELOPES,
    DONUT
}

data class CategoryItemState(
    val category: CategoryEntity,
    val currentAllocationPaise: Long = 0L,
    val spentPaise: Long = 0L,
    val remainingPaise: Long = 0L
)

data class CategoryManagementUiState(
    val isLoading: Boolean = true,
    val currentPeriodId: Long = 0L,
    val currentYear: Int = 0,
    val currentMonth: Int = 0,
    val categories: List<CategoryItemState> = emptyList(),
    val adjustments: List<BudgetAdjustmentEntity> = emptyList(),
    val errorMessage: String? = null,
    val bulletBarsState: ChartState<BulletBarsModel> = ChartState.Loading,
    val categoryDonutState: ChartState<CategoryDonutModel> = ChartState.Loading,
    val selectedDonutSlice: CategoryDonutSlice? = null,
    val viewMode: BudgetViewMode = BudgetViewMode.ENVELOPES
)
