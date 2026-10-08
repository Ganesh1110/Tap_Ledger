package com.tapledger.ui.screens.statement

import com.tapledger.core.database.entity.AccountEntity
import com.tapledger.core.database.entity.CategoryEntity
import com.tapledger.core.database.entity.StatementImportEntity
import com.tapledger.core.database.entity.StatementLineEntity

data class StatementReconciliationUiState(
    val isLoading: Boolean = true,
    val accounts: List<AccountEntity> = emptyList(),
    val categories: List<CategoryEntity> = emptyList(),
    val imports: List<StatementImportEntity> = emptyList(),
    val selectedImport: StatementImportEntity? = null,
    val selectedImportLines: List<StatementLineEntity> = emptyList(),
    val isArithmeticConsistent: Boolean = true,
    val errorMessage: String? = null
)
