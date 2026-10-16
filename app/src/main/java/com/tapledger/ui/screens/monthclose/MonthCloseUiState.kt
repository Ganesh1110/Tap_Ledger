package com.tapledger.ui.screens.monthclose

import com.tapledger.core.database.entity.MonthCloseEntity
import com.tapledger.domain.model.BudgetPeriodSummary
import com.tapledger.ui.charts.model.AdvanceRolloverChartModel
import com.tapledger.ui.charts.model.ChartState

data class SavingsChecklistSummary(
    val targetPaise: Long = 0L,
    val movedPaise: Long = 0L,
    val verifiedCount: Int = 0
)

data class MonthCloseUiState(
    val isLoading: Boolean = true,
    val currentYear: Int = 0,
    val currentMonth: Int = 0,
    val summary: BudgetPeriodSummary? = null,
    val monthCloseRecord: MonthCloseEntity? = null,
    val pendingCount: Int = 0,
    val advanceRolloverState: ChartState<AdvanceRolloverChartModel> = ChartState.Loading,
    val savingsSummary: SavingsChecklistSummary? = null,
    val errorMessage: String? = null
)
