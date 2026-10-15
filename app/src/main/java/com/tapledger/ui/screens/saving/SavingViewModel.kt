package com.tapledger.ui.screens.saving

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tapledger.data.repository.AccountRepository
import com.tapledger.data.repository.SavingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SavingViewModel @Inject constructor(
    private val savingsRepository: SavingsRepository,
    private val accountRepository: AccountRepository
) : ViewModel() {

    val uiState: StateFlow<SavingHomeUiState> = combine(
        savingsRepository.getOverviewSummaryFlow(),
        accountRepository.getAllActiveAccounts()
    ) { overview, accounts ->
        SavingHomeUiState(
            isLoading = false,
            totalSavedPaise = overview.totalSavedPaise,
            activeGoalsCount = overview.activeGoalsCount,
            completedGoalsCount = overview.completedGoalsCount,
            goals = overview.goals,
            accounts = accounts,
            earmarkWarnings = overview.accountEarmarkWarnings,
            errorMessage = null
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = SavingHomeUiState(isLoading = true)
    )

    fun getGoalDetailFlow(goalId: Long): StateFlow<GoalDetailUiState> {
        return combine(
            savingsRepository.getGoalSummaryFlow(goalId),
            savingsRepository.getContributionsForGoal(goalId),
            accountRepository.getAllActiveAccounts()
        ) { summary, contributions, accounts ->
            val linkedAccount = summary?.goal?.linkedAccountId?.let { id ->
                accounts.find { it.id == id }
            }
            GoalDetailUiState(
                isLoading = false,
                goalSummary = summary,
                linkedAccount = linkedAccount,
                contributions = contributions,
                isOverEarmarked = summary?.isOverEarmarked ?: false,
                errorMessage = null
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = GoalDetailUiState(isLoading = true)
        )
    }

    fun createGoal(
        name: String,
        targetPaise: Long,
        deadlineEpochMs: Long?,
        priority: Int,
        linkedAccountId: Long?,
        onSuccess: (Long) -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        viewModelScope.launch {
            try {
                val goalId = savingsRepository.createGoal(
                    name = name,
                    targetPaise = targetPaise,
                    deadlineEpochMs = deadlineEpochMs,
                    priority = priority,
                    linkedAccountId = linkedAccountId
                )
                onSuccess(goalId)
            } catch (e: Exception) {
                onError(e.message ?: "Failed to create goal")
            }
        }
    }

    fun updateGoal(
        id: Long,
        name: String,
        targetPaise: Long,
        deadlineEpochMs: Long?,
        priority: Int,
        linkedAccountId: Long?,
        onSuccess: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        viewModelScope.launch {
            try {
                savingsRepository.updateGoal(
                    id = id,
                    name = name,
                    targetPaise = targetPaise,
                    deadlineEpochMs = deadlineEpochMs,
                    priority = priority,
                    linkedAccountId = linkedAccountId
                )
                onSuccess()
            } catch (e: Exception) {
                onError(e.message ?: "Failed to update goal")
            }
        }
    }

    fun deposit(
        goalId: Long,
        amountPaise: Long,
        note: String?,
        onSuccess: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        viewModelScope.launch {
            try {
                savingsRepository.deposit(
                    goalId = goalId,
                    amountPaise = amountPaise,
                    note = note
                )
                onSuccess()
            } catch (e: Exception) {
                onError(e.message ?: "Failed to deposit")
            }
        }
    }

    fun withdraw(
        goalId: Long,
        amountPaise: Long,
        note: String?,
        onSuccess: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        viewModelScope.launch {
            try {
                savingsRepository.withdraw(
                    goalId = goalId,
                    amountPaise = amountPaise,
                    note = note
                )
                onSuccess()
            } catch (e: Exception) {
                onError(e.message ?: "Failed to withdraw")
            }
        }
    }

    fun pauseGoal(id: Long, onSuccess: () -> Unit = {}, onError: (String) -> Unit = {}) {
        viewModelScope.launch {
            try {
                savingsRepository.pauseGoal(id)
                onSuccess()
            } catch (e: Exception) {
                onError(e.message ?: "Failed to pause goal")
            }
        }
    }

    fun resumeGoal(id: Long, onSuccess: () -> Unit = {}, onError: (String) -> Unit = {}) {
        viewModelScope.launch {
            try {
                savingsRepository.resumeGoal(id)
                onSuccess()
            } catch (e: Exception) {
                onError(e.message ?: "Failed to resume goal")
            }
        }
    }

    fun archiveGoal(id: Long, onSuccess: () -> Unit = {}, onError: (String) -> Unit = {}) {
        viewModelScope.launch {
            try {
                savingsRepository.archiveGoal(id)
                onSuccess()
            } catch (e: Exception) {
                onError(e.message ?: "Failed to archive goal")
            }
        }
    }
}
