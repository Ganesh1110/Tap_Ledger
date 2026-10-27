package com.tapledger.ui.screens.insights

import com.tapledger.core.database.entity.BudgetPeriodEntity
import com.tapledger.core.database.entity.TransactionEntity
import com.tapledger.ui.charts.model.CategoryDonutModel
import com.tapledger.ui.charts.model.CategoryDonutSlice
import com.tapledger.ui.charts.model.ChartState
import com.tapledger.ui.charts.model.SixPeriodTrendModel
import com.tapledger.ui.charts.model.SpendingHeatmapModel
import com.tapledger.ui.charts.model.TopMerchantsModel

data class InsightsUiState(
    val selectedTab: Int = 0, // 0: Cash Flow (V4 12-period), 1: Calendar (V7), 2: Merchants (V8), 3: Categories (V6)
    val cashFlowTrendState: ChartState<SixPeriodTrendModel> = ChartState.Loading,
    val calendarHeatmapState: ChartState<SpendingHeatmapModel> = ChartState.Loading,
    val topMerchantsState: ChartState<TopMerchantsModel> = ChartState.Loading,
    val categoryDonutState: ChartState<CategoryDonutModel> = ChartState.Loading,
    val allPeriods: List<BudgetPeriodEntity> = emptyList(),
    val selectedPeriod: BudgetPeriodEntity? = null,
    val selectedHeatmapDayEpoch: Long? = null,
    val selectedHeatmapDayTxs: List<TransactionEntity> = emptyList(),
    val selectedDonutSlice: CategoryDonutSlice? = null,
    val totalInterestPaidPaise: Long = 0L,
    val debtToIncomeBp: Int = 0,
    val totalMonthlyDebtPaise: Long = 0L,
    val monthlyIncomePaise: Long = 0L,
    val activeLoansCount: Int = 0,
    val totalOutstandingLoanPrincipalPaise: Long = 0L,
    val isLoading: Boolean = true,
    val errorMessage: String? = null
)
