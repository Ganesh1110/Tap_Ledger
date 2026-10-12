package com.tapledger.ui.screens.statement

import com.tapledger.core.database.entity.AccountEntity
import com.tapledger.core.database.entity.CategoryEntity
import com.tapledger.core.database.entity.StatementImportEntity
import com.tapledger.core.database.entity.StatementLineEntity
import com.tapledger.ui.charts.model.ChartState
import com.tapledger.ui.charts.model.ReconciliationBridgeModel

data class StatementReconciliationUiState(
    val isLoading: Boolean = true,
    val accounts: List<AccountEntity> = emptyList(),
    val categories: List<CategoryEntity> = emptyList(),
    val imports: List<StatementImportEntity> = emptyList(),
    val selectedImport: StatementImportEntity? = null,
    val selectedImportLines: List<StatementLineEntity> = emptyList(),
    val isArithmeticConsistent: Boolean = true,
    val reconciliationBridgeState: ChartState<ReconciliationBridgeModel> = ChartState.Loading,
    val errorMessage: String? = null,
    val successMessage: String? = null
)
