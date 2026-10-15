package com.tapledger.ui.screens.saving

import com.tapledger.core.database.entity.AccountEntity
import com.tapledger.core.database.entity.SavingsContributionEntity
import com.tapledger.domain.model.SavingsGoalSummary

data class GoalDetailUiState(
    val isLoading: Boolean = true,
    val goalSummary: SavingsGoalSummary? = null,
    val linkedAccount: AccountEntity? = null,
    val contributions: List<SavingsContributionEntity> = emptyList(),
    val isOverEarmarked: Boolean = false,
    val errorMessage: String? = null
)
