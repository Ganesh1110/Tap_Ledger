package com.tapledger.ui.screens.saving

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tapledger.core.database.dao.BudgetPeriodDao
import com.tapledger.core.database.dao.SavingsTransferPlanDao
import com.tapledger.core.database.dao.TransactionDao
import com.tapledger.core.model.CompoundingFrequency
import com.tapledger.core.model.RecurringCommitmentType
import com.tapledger.core.model.SavingsContributionSource
import com.tapledger.core.model.TransactionDirection
import com.tapledger.core.model.TransactionStatus
import com.tapledger.core.model.TransferPlanStatus
import com.tapledger.core.preferences.SavingsGuidePreferences
import com.tapledger.core.util.DateTimeUtils
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
    private val accountRepository: AccountRepository,
    private val savingsTransferPlanDao: SavingsTransferPlanDao,
    private val budgetPeriodDao: BudgetPeriodDao,
    private val savingsGuidePreferences: SavingsGuidePreferences,
    private val transactionDao: TransactionDao
) : ViewModel() {

    val uiState: StateFlow<SavingHomeUiState> = combine(
        combine(
            savingsRepository.getOverviewSummaryFlow(),
            accountRepository.getAllActiveAccounts(),
            savingsRepository.getEmergencyFundCoverageTenthsFlow()
        ) { overview, accounts, efTenths ->
            Triple(overview, accounts, efTenths)
        },
        savingsRepository.getAllCommitmentsFlow(),
        savingsRepository.getAllFixedDepositsFlow(),
        savingsRepository.getMilestonesFlow(),
        budgetPeriodDao.getAllPeriods()
    ) { (overview, accounts, efTenths), commitments, fds, milestones, periods ->
        val now = DateTimeUtils.nowEpochMillis()
        val currentPeriod = periods.find { now in it.startAt until it.endAt } ?: periods.firstOrNull()

        // Transfer summary: moved amounts for current period
        val plans = if (currentPeriod != null) {
            savingsTransferPlanDao.getPlansForPeriod(currentPeriod.id)
        } else emptyList()

        val activeMovedPlans = plans.filter {
            it.status == TransferPlanStatus.MARKED_MOVED || it.status == TransferPlanStatus.VERIFIED
        }
        val movedPaise = activeMovedPlans.sumOf { it.movedPaise ?: it.suggestedPaise }
        val isVerified = activeMovedPlans.isNotEmpty() && activeMovedPlans.all { it.status == TransferPlanStatus.VERIFIED }
        val transferSummary = if (movedPaise > 0L) SavingsTransferSummary(movedPaise, isVerified) else null

        // Dip guard: Outbound debits from savings account that are not transfers
        val config = savingsGuidePreferences.getConfig()
        val sbiId = config.savingsAccountId ?: accounts.find { it.isSavings }?.id
        val pendingSavingsDebits = if (sbiId != null) {
            transactionDao.getTransactionsForAccountSync(sbiId)
                .filter { it.direction == TransactionDirection.OUT && it.status == TransactionStatus.CONFIRMED && it.transferGroupId == null }
                .take(5)
        } else emptyList()

        SavingHomeUiState(
            isLoading = false,
            totalSavedPaise = overview.totalSavedPaise,
            activeGoalsCount = overview.activeGoalsCount,
            completedGoalsCount = overview.completedGoalsCount,
            goals = overview.goals,
            accounts = accounts,
            earmarkWarnings = overview.accountEarmarkWarnings,
            emergencyFundTenths = efTenths,
            commitments = commitments,
            fixedDeposits = fds,
            milestones = milestones,
            transferSummary = transferSummary,
            pendingSavingsDebits = pendingSavingsDebits,
            errorMessage = null
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = SavingHomeUiState(isLoading = true)
    )

    fun attributeDebitToGoal(
        transactionId: Long,
        goalId: Long,
        amountPaise: Long,
        note: String? = null,
        onSuccess: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        viewModelScope.launch {
            try {
                savingsRepository.withdraw(
                    goalId = goalId,
                    amountPaise = amountPaise,
                    note = note ?: "Spending from savings",
                    transactionId = transactionId,
                    source = SavingsContributionSource.TRANSACTION
                )
                onSuccess()
            } catch (e: Exception) {
                onError(e.message ?: "Failed to attribute savings debit")
            }
        }
    }

    fun applyRuleContribution(
        goalId: Long,
        amountPaise: Long,
        note: String,
        onSuccess: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        viewModelScope.launch {
            try {
                savingsRepository.applyRuleContribution(
                    goalId = goalId,
                    amountPaise = amountPaise,
                    note = note
                )
                onSuccess()
            } catch (e: Exception) {
                onError(e.message ?: "Failed to apply rule contribution")
            }
        }
    }

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

    // --- Phase 3: Commitments & Fixed Deposits ---

    fun createCommitment(
        type: RecurringCommitmentType,
        name: String,
        amountPaise: Long,
        dueDay: Int,
        accountId: Long?,
        goalId: Long?,
        onSuccess: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        viewModelScope.launch {
            try {
                savingsRepository.createCommitment(type, name, amountPaise, dueDay, accountId, goalId)
                onSuccess()
            } catch (e: Exception) {
                onError(e.message ?: "Failed to create commitment")
            }
        }
    }

    fun deleteCommitment(id: Long, onSuccess: () -> Unit = {}, onError: (String) -> Unit = {}) {
        viewModelScope.launch {
            try {
                savingsRepository.deleteCommitment(id)
                onSuccess()
            } catch (e: Exception) {
                onError(e.message ?: "Failed to delete commitment")
            }
        }
    }

    fun createFixedDeposit(
        name: String,
        principalPaise: Long,
        rateBp: Int,
        startMs: Long,
        maturityMs: Long,
        compounding: CompoundingFrequency,
        goalId: Long?,
        onSuccess: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        viewModelScope.launch {
            try {
                savingsRepository.createFixedDeposit(name, principalPaise, rateBp, startMs, maturityMs, compounding, goalId)
                onSuccess()
            } catch (e: Exception) {
                onError(e.message ?: "Failed to create fixed deposit")
            }
        }
    }

    fun deleteFixedDeposit(id: Long, onSuccess: () -> Unit = {}, onError: (String) -> Unit = {}) {
        viewModelScope.launch {
            try {
                savingsRepository.deleteFixedDeposit(id)
                onSuccess()
            } catch (e: Exception) {
                onError(e.message ?: "Failed to delete fixed deposit")
            }
        }
    }
}
