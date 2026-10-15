package com.tapledger.ui.screens.saving

import com.tapledger.core.database.entity.AccountEntity
import com.tapledger.domain.model.AccountEarmarkWarning
import com.tapledger.domain.model.SavingsGoalSummary

data class SavingHomeUiState(
    val isLoading: Boolean = true,
    val totalSavedPaise: Long = 0L,
    val activeGoalsCount: Int = 0,
    val completedGoalsCount: Int = 0,
    val goals: List<SavingsGoalSummary> = emptyList(),
    val accounts: List<AccountEntity> = emptyList(),
    val earmarkWarnings: List<AccountEarmarkWarning> = emptyList(),
    val errorMessage: String? = null
)
