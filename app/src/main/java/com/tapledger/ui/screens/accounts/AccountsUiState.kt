package com.tapledger.ui.screens.accounts

import com.tapledger.domain.model.AccountBalanceSummary
import com.tapledger.ui.charts.builder.NetWorthRange
import com.tapledger.ui.charts.model.AccountCompositionModel
import com.tapledger.ui.charts.model.ChartState
import com.tapledger.ui.charts.model.NetWorthChartModel

/**
 * UI State for Accounts & Balances screen per Flow.md §17.2 V9 & V10.
 * End-to-end Long paise and Int permille/bp. Zero Float, Double, or BigDecimal.
 */
data class AccountsUiState(
    val isLoading: Boolean = true,
    val accountSummaries: List<AccountBalanceSummary> = emptyList(),
    val totalAssetsPaise: Long = 0L,
    val totalLiabilitiesPaise: Long = 0L,
    val netWorthPaise: Long = 0L,
    val selectedRange: NetWorthRange = NetWorthRange.M6,
    val isStackedView: Boolean = false,
    val showTable: Boolean = false,
    val netWorthChartState: ChartState<NetWorthChartModel> = ChartState.Loading,
    val accountCompositionState: ChartState<AccountCompositionModel> = ChartState.Loading,
    val unassignedExcludedMessage: String? = null,
    val errorMessage: String? = null
)
