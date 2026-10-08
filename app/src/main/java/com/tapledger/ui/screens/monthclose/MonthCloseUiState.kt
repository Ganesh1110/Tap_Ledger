package com.tapledger.ui.screens.monthclose

import com.tapledger.core.database.entity.MonthCloseEntity
import com.tapledger.domain.model.BudgetPeriodSummary

data class MonthCloseUiState(
    val isLoading: Boolean = true,
    val currentYear: Int = 0,
    val currentMonth: Int = 0,
    val summary: BudgetPeriodSummary? = null,
    val monthCloseRecord: MonthCloseEntity? = null,
    val pendingCount: Int = 0,
    val errorMessage: String? = null
)
