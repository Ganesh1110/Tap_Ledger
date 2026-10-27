package com.tapledger.ui.screens.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tapledger.core.database.dao.BudgetPeriodDao
import com.tapledger.core.database.dao.MonthCloseDao
import com.tapledger.core.database.dao.TransactionAllocationDao
import com.tapledger.core.database.dao.TransactionDao
import com.tapledger.core.database.entity.AccountEntity
import com.tapledger.core.database.entity.TransactionEntity
import com.tapledger.core.model.BudgetPeriodStatus
import com.tapledger.core.model.MoneyFormatter
import com.tapledger.core.model.TransactionStatus
import com.tapledger.core.util.DateTimeUtils
import com.tapledger.data.repository.AccountRepository
import com.tapledger.data.repository.BudgetRepository
import com.tapledger.data.repository.TransactionRepository
import com.tapledger.domain.calculator.AccountBalanceCalculator
import com.tapledger.domain.calculator.BudgetCalculator
import com.tapledger.domain.model.AccountBalanceSummary
import com.tapledger.domain.model.BudgetPeriodSummary
import com.tapledger.domain.model.NetWorthSummary
import com.tapledger.ui.charts.builder.DashboardChartBuilder
import com.tapledger.ui.charts.model.BucketRingsModel
import com.tapledger.ui.charts.model.BurnUpChartModel
import com.tapledger.ui.charts.model.ChartState
import com.tapledger.ui.charts.model.DashboardHeroRingModel
import com.tapledger.ui.charts.model.SixPeriodTrendModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import com.tapledger.core.database.dao.CategoryDao
import com.tapledger.core.database.dao.CommitmentPaymentDao
import com.tapledger.core.database.dao.RecurringCommitmentDao
import com.tapledger.core.database.dao.SavingsContributionDao
import com.tapledger.core.database.dao.SavingsGoalDao
import com.tapledger.core.database.dao.SavingsTransferPlanDao
import com.tapledger.core.model.AccountType
import com.tapledger.core.model.BudgetBucket
import com.tapledger.core.model.CommitmentPaymentStatus
import com.tapledger.core.model.SavingsContributionSource
import com.tapledger.core.model.SavingsGoalStatus
import com.tapledger.core.model.TransferPlanKind
import com.tapledger.core.model.TransferPlanStatus
import com.tapledger.core.preferences.SavingsGuidePreferences
import com.tapledger.data.repository.SavingsRepository
import com.tapledger.data.repository.TransferPlanRepository
import com.tapledger.domain.calculator.CardDueItem
import com.tapledger.domain.calculator.GoalApportionmentCalculator
import com.tapledger.domain.calculator.GoalApportionmentItem
import com.tapledger.domain.calculator.SafeDailySpendCalculator
import com.tapledger.domain.calculator.SafeDailySpendResult
import com.tapledger.domain.calculator.SpareCalculator
import com.tapledger.domain.calculator.UnpaidCommitmentItem
import com.tapledger.ui.screens.dashboard.components.TransferPlanDashboardState
import kotlinx.coroutines.withContext
import java.time.LocalDate
import javax.inject.Inject

data class CategoryPaceAlert(
    val categoryId: Long,
    val categoryName: String,
    val percentageUsed: Int,
    val daysRemaining: Int,
    val isExceeded: Boolean
)

data class DashboardUiState(
    val isLoading: Boolean = true,
    val pendingCount: Int = 0,
    val accountSummaries: List<AccountBalanceSummary> = emptyList(),
    val netWorth: NetWorthSummary? = null,
    val budgetSummary: BudgetPeriodSummary? = null,
    val recentTransactions: List<TransactionEntity> = emptyList(),
    val currentYear: Int = 2026,
    val currentMonth: Int = 10,
    val heroRingState: ChartState<DashboardHeroRingModel> = ChartState.Loading,
    val burnUpChartState: ChartState<BurnUpChartModel> = ChartState.Loading,
    val bucketRingsState: ChartState<BucketRingsModel> = ChartState.Loading,
    val sixPeriodTrendState: ChartState<SixPeriodTrendModel> = ChartState.Loading,
    val transferPlanState: TransferPlanDashboardState? = null,
    val activeGoals: List<GoalApportionmentItem> = emptyList(),
    val preferredPayApp: String? = null,
    val safeDailySpend: SafeDailySpendResult? = null,
    val pendingSpendPaise: Long = 0L,
    val showIncomeDiscrepancyBanner: Boolean = false,
    val plannedIncomePaise: Long = 0L,
    val actualIncomePaise: Long = 0L,
    val currentPeriodId: Long? = null,
    val paceAlerts: List<CategoryPaceAlert> = emptyList()
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val accountRepository: AccountRepository,
    private val budgetRepository: BudgetRepository,
    private val transactionRepository: TransactionRepository,
    private val transactionDao: TransactionDao,
    private val transactionAllocationDao: TransactionAllocationDao,
    private val budgetPeriodDao: BudgetPeriodDao,
    private val monthCloseDao: MonthCloseDao,
    private val transferPlanRepository: TransferPlanRepository,
    private val savingsGuidePreferences: SavingsGuidePreferences,
    private val spareCalculator: SpareCalculator,
    private val goalApportionmentCalculator: GoalApportionmentCalculator,
    private val savingsRepository: SavingsRepository,
    private val savingsGoalDao: SavingsGoalDao,
    private val savingsContributionDao: SavingsContributionDao,
    private val recurringCommitmentDao: RecurringCommitmentDao,
    private val commitmentPaymentDao: CommitmentPaymentDao,
    private val categoryDao: CategoryDao,
    private val savingsTransferPlanDao: SavingsTransferPlanDao
) : ViewModel() {

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val today = LocalDate.now(DateTimeUtils.IST_ZONE)
            loadDashboard(today.year, today.monthValue)

            // Reactively refresh dashboard whenever transactions change
            transactionRepository.observeAllTransactions().collect {
                loadDashboard(_uiState.value.currentYear, _uiState.value.currentMonth)
            }
        }
    }

    fun loadDashboard(
        year: Int = _uiState.value.currentYear,
        month: Int = _uiState.value.currentMonth
    ) {
        viewModelScope.launch {
            val period = budgetRepository.getOrCreateBudgetPeriod(year, month)
            val allocations = budgetRepository.getAllocationsForPeriodSync(period.id)
            val adjustments = budgetRepository.getAdjustmentsForPeriodSync(period.id)

            val periodTxs = transactionDao.getTransactionsInPeriod(period.startAt, period.endAt)
            val periodAllocations = transactionAllocationDao.getAllocationsInPeriod(period.startAt, period.endAt)

            val budgetSummary = BudgetCalculator.calculateBudgetPeriodSummary(
                period = period,
                allocations = allocations,
                adjustments = adjustments,
                transactions = periodTxs,
                allocationsForTransactions = periodAllocations
            )

            val accounts = accountRepository.getAllAccountsSync()
            val accountSummaries = accounts.map { account ->
                val txs = transactionDao.getBalanceTransactionsForAccount(account.id, 0L)
                val earliestTx = txs.filter { it.status != TransactionStatus.IGNORED && it.status != TransactionStatus.VOIDED }.minOfOrNull { it.occurredAt }
                val effectiveOpeningDate = if (earliestTx != null && earliestTx < account.openingBalanceDate) {
                    DateTimeUtils.getStartOfDayEpochMillis(earliestTx)
                } else {
                    account.openingBalanceDate
                }
                val effectiveAccount = if (effectiveOpeningDate != account.openingBalanceDate) {
                    account.copy(openingBalanceDate = effectiveOpeningDate)
                } else {
                    account
                }
                AccountBalanceCalculator.calculateAccountBalance(effectiveAccount, txs)
            }
            val netWorth = AccountBalanceCalculator.calculateNetWorth(accountSummaries)
            val pendingCount = transactionDao.getPendingCountSync()

            val recentTxs = periodTxs.sortedByDescending { it.occurredAt }.take(5)

            val nowIST = DateTimeUtils.nowEpochMillis()
            val isClosed = period.status == BudgetPeriodStatus.CLOSED

            val (heroRing, burnUp) = withContext(Dispatchers.Default) {
                val hero = DashboardChartBuilder.buildHeroRing(budgetSummary, isClosed, nowIST)
                val burn = DashboardChartBuilder.buildBurnUpChart(budgetSummary, period, periodTxs, periodAllocations, nowIST)
                hero to burn
            }

            val (bucketRings, sixTrend) = withContext(Dispatchers.Default) {
                val rings = DashboardChartBuilder.buildBucketRings(budgetSummary)
                val allCloses = monthCloseDao.getAllMonthClosesSync()
                val allPeriods = budgetPeriodDao.getAllPeriodsSync()
                val trend = DashboardChartBuilder.buildSixPeriodTrend(allCloses, allPeriods, budgetSummary, period.id)
                rings to trend
            }

            // 7. Savings Transfer Guide calculation & plan evaluation (Flow.md §18, D013, D014, D015)
            val spendingAccountId = savingsGuidePreferences.getConfig().spendingAccountId
            val savingsAccountId = savingsGuidePreferences.getConfig().savingsAccountId

            val spendingAccount = if (spendingAccountId != null) {
                accounts.find { it.id == spendingAccountId }
            } else {
                accounts.find { it.type == AccountType.BANK && it.name.contains("HDFC", ignoreCase = true) }
                    ?: accounts.find { it.type == AccountType.BANK }
            }

            val savingsAccount = if (savingsAccountId != null) {
                accounts.find { it.id == savingsAccountId }
            } else {
                accounts.find { it.type == AccountType.BANK && it.id != spendingAccount?.id && it.name.contains("SBI", ignoreCase = true) }
                    ?: accounts.find { it.type == AccountType.BANK && it.id != spendingAccount?.id }
            }

            val transferPlanDashboardState = if (spendingAccount != null && savingsAccount != null) {
                val spendingSummary = accountSummaries.find { it.account.id == spendingAccount.id }
                val spendingBalance = spendingSummary?.currentBalancePaise ?: 0L

                val cardDueItems = accountSummaries
                    .filter { it.account.type == AccountType.CREDIT_CARD }
                    .map {
                        CardDueItem(
                            accountId = it.account.id,
                            cardName = it.account.name,
                            balancePaise = it.currentBalancePaise
                        )
                    }

                val allActiveCommitments = recurringCommitmentDao.getAllActiveSync()
                val paidCommitmentIds = commitmentPaymentDao.getPaymentsForPeriodSync(period.id)
                    .filter { it.status == CommitmentPaymentStatus.PAID }
                    .map { it.commitmentId }
                    .toSet()
                val unpaidCommitmentItems = allActiveCommitments
                    .filter { it.id !in paidCommitmentIds }
                    .map {
                        UnpaidCommitmentItem(
                            commitmentId = it.id,
                            name = it.name,
                            amountPaise = it.amountPaise,
                            accountId = it.accountId
                        )
                    }

                val savingsCategories = budgetSummary.categorySummaries
                    .filter { it.allocation.bucketSnapshot == BudgetBucket.SAVINGS }
                val savingsAdjustedBudget = savingsCategories.sumOf { it.adjustedBudgetPaise }
                val actualSavings = savingsCategories.sumOf { it.netSpentPaise }

                val bufferPaise = savingsGuidePreferences.getConfig().bufferPaise
                val spareResult = spareCalculator.calculateSpare(
                    hdfcBalancePaise = spendingBalance,
                    cardsPaidFromHdfc = cardDueItems,
                    categorySummaries = budgetSummary.categorySummaries,
                    unpaidCommitmentsFromHdfc = unpaidCommitmentItems,
                    bufferPaise = bufferPaise,
                    savingsAdjustedBudgetPaise = savingsAdjustedBudget,
                    actualSavingsPaise = actualSavings,
                    isDriftBannerActive = false,
                    hasUnassignedPendingTransactions = pendingCount > 0
                )

                val existingPlans = savingsTransferPlanDao.getPlansForPeriod(period.id)
                val activePlan = existingPlans.firstOrNull { it.status != TransferPlanStatus.DISMISSED && it.status != TransferPlanStatus.EXPIRED }

                // Alerts-first match detection (Flow.md §18, D014, Milestone M20)
                val detectedMatch = if (activePlan?.status != TransferPlanStatus.VERIFIED) {
                    val unlinkedOutTxs = periodTxs.filter { tx ->
                        tx.transferGroupId != null &&
                        tx.accountId == spendingAccount.id &&
                        tx.direction == com.tapledger.core.model.TransactionDirection.OUT &&
                        existingPlans.none { it.transferGroupId == tx.transferGroupId }
                    }
                    unlinkedOutTxs.firstNotNullOfOrNull { outTx ->
                        val matchingIn = periodTxs.find { inTx ->
                            inTx.transferGroupId == outTx.transferGroupId &&
                            inTx.accountId == savingsAccount.id &&
                            inTx.direction == com.tapledger.core.model.TransactionDirection.IN &&
                            inTx.amountPaise == outTx.amountPaise
                        }
                        if (matchingIn != null && outTx.transferGroupId != null) {
                            Pair(outTx.transferGroupId, outTx.amountPaise)
                        } else null
                    }
                } else null

                if (activePlan != null) {
                    TransferPlanDashboardState(
                        hasPlan = true,
                        planId = activePlan.id,
                        kind = activePlan.kind,
                        status = activePlan.status,
                        sourceAccountName = spendingAccount.name,
                        destAccountName = savingsAccount.name,
                        suggestedPaise = activePlan.suggestedPaise,
                        movedPaise = activePlan.movedPaise,
                        spareCalculation = spareResult,
                        isSafetyGateActive = spareResult.isSafetyGateTriggered,
                        safetyGateReason = spareResult.safetyGateReason,
                        detectedMatchingTransferGroupId = detectedMatch?.first,
                        detectedMatchingAmountPaise = detectedMatch?.second
                    )
                } else {
                    val suggestedPaise = spareResult.paydaySuggestionPaise
                    val createdPlanId = if (suggestedPaise > 0L && period.status == BudgetPeriodStatus.OPEN) {
                        transferPlanRepository.createOrUpdateSuggestedPlan(
                            periodId = period.id,
                            kind = TransferPlanKind.PAYDAY,
                            sourceAccountId = spendingAccount.id,
                            destAccountId = savingsAccount.id,
                            suggestedPaise = suggestedPaise
                        )
                    } else null

                    TransferPlanDashboardState(
                        hasPlan = true,
                        planId = createdPlanId,
                        kind = TransferPlanKind.PAYDAY,
                        status = TransferPlanStatus.SUGGESTED,
                        sourceAccountName = spendingAccount.name,
                        destAccountName = savingsAccount.name,
                        suggestedPaise = suggestedPaise,
                        movedPaise = null,
                        spareCalculation = spareResult,
                        isSafetyGateActive = spareResult.isSafetyGateTriggered,
                        safetyGateReason = spareResult.safetyGateReason,
                        detectedMatchingTransferGroupId = detectedMatch?.first,
                        detectedMatchingAmountPaise = detectedMatch?.second
                    )
                }
            } else null

            val activeGoals = savingsGoalDao.getAllGoalsSync()
                .filter { it.status == SavingsGoalStatus.ACTIVE }
                .map { goal ->
                    val balance = savingsContributionDao.getGoalBalance(goal.id)
                    GoalApportionmentItem(
                        goalId = goal.id,
                        name = goal.name,
                        priority = goal.priority,
                        targetPaise = goal.targetPaise,
                        currentBalancePaise = balance
                    )
                }

            // Safe Daily Spend Calculation (Flow.md §8.4/§8.5)
            val todayStartIST = DateTimeUtils.getStartOfDayEpochMillis(nowIST)
            val todayEndIST = todayStartIST + SafeDailySpendCalculator.MILLIS_PER_IST_DAY
            val todayTxIds = periodTxs
                .filter { it.occurredAt in todayStartIST until todayEndIST && it.status != TransactionStatus.VOIDED && it.status != TransactionStatus.IGNORED }
                .map { it.id }
                .toSet()

            val savingsCategoryIds = budgetSummary.categorySummaries
                .filter { it.allocation.bucketSnapshot == BudgetBucket.SAVINGS }
                .map { it.allocation.categoryId }
                .toSet()

            var todaySpentPaise = 0L
            for (alloc in periodAllocations) {
                if (alloc.transactionId in todayTxIds && alloc.categoryId !in savingsCategoryIds) {
                    val tx = periodTxs.find { it.id == alloc.transactionId }
                    if (tx != null) {
                        if (tx.kind == com.tapledger.core.model.TransactionKind.EXPENSE ||
                            tx.kind == com.tapledger.core.model.TransactionKind.FEE ||
                            tx.kind == com.tapledger.core.model.TransactionKind.INTEREST_CHARGED) {
                            todaySpentPaise += alloc.amountPaise
                        } else if (tx.kind == com.tapledger.core.model.TransactionKind.REFUND) {
                            todaySpentPaise -= alloc.amountPaise
                        }
                    }
                }
            }
            if (todaySpentPaise < 0L) todaySpentPaise = 0L

            val safeDailySpend = SafeDailySpendCalculator.calculate(
                heroBudgetPaise = budgetSummary.dashboardHeroBudgetPaise,
                heroSpentPaise = budgetSummary.dashboardHeroSpendPaise,
                periodStartAt = period.startAt,
                periodEndAt = period.endAt,
                nowIST = nowIST,
                isClosed = isClosed,
                todaySpentPaise = todaySpentPaise
            )

            val pendingExpenseTxs = periodTxs.filter {
                it.status == TransactionStatus.PENDING &&
                it.direction == com.tapledger.core.model.TransactionDirection.OUT &&
                it.kind != com.tapledger.core.model.TransactionKind.TRANSFER
            }
            val pendingSpendPaise = pendingExpenseTxs.sumOf { it.amountPaise }

            val plannedIncome = period.incomePaise
            val actualIncome = budgetSummary.actualIncomePaise
            val showDiscrepancy = period.status == BudgetPeriodStatus.OPEN && actualIncome > 0L && actualIncome != plannedIncome

            val paceAlerts = if (isClosed || !safeDailySpend.isPeriodActive) {
                emptyList()
            } else {
                val totalDays = safeDailySpend.totalDaysInPeriod.coerceAtLeast(1)
                val elapsedDays = safeDailySpend.elapsedDays
                val timePercentElapsed = (elapsedDays * 100) / totalDays

                budgetSummary.categorySummaries
                    .filter { it.allocation.bucketSnapshot != BudgetBucket.SAVINGS }
                    .filter { cat ->
                        (cat.percentageUsed >= 80 && cat.percentageUsed > timePercentElapsed + 15) || cat.isExceeded
                    }
                    .map { cat ->
                        CategoryPaceAlert(
                            categoryId = cat.allocation.categoryId,
                            categoryName = cat.allocation.categoryNameSnapshot,
                            percentageUsed = cat.percentageUsed,
                            daysRemaining = safeDailySpend.daysRemaining,
                            isExceeded = cat.isExceeded
                        )
                    }
            }

            _uiState.value = DashboardUiState(
                isLoading = false,
                pendingCount = pendingCount,
                accountSummaries = accountSummaries,
                netWorth = netWorth,
                budgetSummary = budgetSummary,
                recentTransactions = recentTxs,
                currentYear = year,
                currentMonth = month,
                heroRingState = heroRing,
                burnUpChartState = burnUp,
                bucketRingsState = bucketRings,
                sixPeriodTrendState = sixTrend,
                transferPlanState = transferPlanDashboardState,
                activeGoals = activeGoals,
                preferredPayApp = savingsGuidePreferences.getConfig().preferredPayApp,
                safeDailySpend = safeDailySpend,
                pendingSpendPaise = pendingSpendPaise,
                showIncomeDiscrepancyBanner = showDiscrepancy,
                plannedIncomePaise = plannedIncome,
                actualIncomePaise = actualIncome,
                currentPeriodId = period.id,
                paceAlerts = paceAlerts
            )
        }
    }

    fun syncPlannedIncomeToActual(periodId: Long, actualIncomePaise: Long) {
        viewModelScope.launch {
            try {
                budgetRepository.updatePeriodIncomeAndRebalance(periodId, actualIncomePaise)
                loadDashboard(_uiState.value.currentYear, _uiState.value.currentMonth)
            } catch (e: Exception) {
                // Ignore or log error
            }
        }
    }

    fun markPlanAsMoved(
        planId: Long,
        movedPaise: Long,
        onSuccess: (outTransactionId: Long, periodId: Long) -> Unit
    ) {
        viewModelScope.launch {
            try {
                val categories = categoryDao.getAllCategoriesSync()
                val savingsCat = categories.find { it.defaultBucket == BudgetBucket.SAVINGS }
                    ?: categories.firstOrNull()
                    ?: throw IllegalStateException("No category available for savings transfer")

                val targetPlan = if (planId > 0L) {
                    savingsTransferPlanDao.getById(planId)
                } else null

                val effectivePlanId = if (targetPlan != null) {
                    targetPlan.id
                } else {
                    val currentPeriod = budgetRepository.getOrCreateBudgetPeriod(_uiState.value.currentYear, _uiState.value.currentMonth)
                    val spendingId = savingsGuidePreferences.getConfig().spendingAccountId
                        ?: _uiState.value.accountSummaries.find { it.account.type == AccountType.BANK }?.account?.id
                        ?: 1L
                    val savingsId = savingsGuidePreferences.getConfig().savingsAccountId
                        ?: _uiState.value.accountSummaries.find { it.account.type == AccountType.BANK && it.account.id != spendingId }?.account?.id
                        ?: 2L
                    transferPlanRepository.createOrUpdateSuggestedPlan(
                        periodId = currentPeriod.id,
                        kind = TransferPlanKind.PAYDAY,
                        sourceAccountId = spendingId,
                        destAccountId = savingsId,
                        suggestedPaise = movedPaise
                    )
                }

                val periodId = targetPlan?.periodId
                    ?: budgetRepository.getOrCreateBudgetPeriod(_uiState.value.currentYear, _uiState.value.currentMonth).id

                val (outId, _) = transferPlanRepository.markAsMoved(
                    planId = effectivePlanId,
                    movedPaise = movedPaise,
                    savingsCategoryId = savingsCat.id
                )
                loadDashboard()
                onSuccess(outId, periodId)
            } catch (e: Exception) {
                // handle error
            }
        }
    }

    fun allocateToGoals(allocations: Map<Long, Long>, transactionId: Long, periodId: Long) {
        viewModelScope.launch {
            allocations.forEach { (goalId, amountPaise) ->
                if (amountPaise > 0L) {
                    savingsRepository.deposit(
                        goalId = goalId,
                        amountPaise = amountPaise,
                        note = "Savings Transfer Allocation",
                        transactionId = transactionId,
                        periodId = periodId,
                        source = SavingsContributionSource.TRANSACTION
                    )
                }
            }
            loadDashboard()
        }
    }

    fun dismissPlan(planId: Long) {
        viewModelScope.launch {
            transferPlanRepository.dismissPlan(planId)
            loadDashboard()
        }
    }

    fun confirmMatchingTransfer(
        planId: Long,
        transferGroupId: String,
        amountPaise: Long,
        onSuccess: (outTransactionId: Long, periodId: Long) -> Unit = { _, _ -> }
    ) {
        viewModelScope.launch {
            try {
                val targetPlan = if (planId > 0L) {
                    savingsTransferPlanDao.getById(planId)
                } else null

                val currentPeriod = budgetRepository.getOrCreateBudgetPeriod(_uiState.value.currentYear, _uiState.value.currentMonth)
                val effectivePlanId = if (targetPlan != null) {
                    targetPlan.id
                } else {
                    val spendingId = savingsGuidePreferences.getConfig().spendingAccountId
                        ?: _uiState.value.accountSummaries.find { it.account.type == AccountType.BANK }?.account?.id
                        ?: 1L
                    val savingsId = savingsGuidePreferences.getConfig().savingsAccountId
                        ?: _uiState.value.accountSummaries.find { it.account.type == AccountType.BANK && it.account.id != spendingId }?.account?.id
                        ?: 2L
                    transferPlanRepository.createOrUpdateSuggestedPlan(
                        periodId = currentPeriod.id,
                        kind = TransferPlanKind.PAYDAY,
                        sourceAccountId = spendingId,
                        destAccountId = savingsId,
                        suggestedPaise = amountPaise
                    )
                }

                transferPlanRepository.confirmPlanFromMatchingTransfer(
                    planId = effectivePlanId,
                    transferGroupId = transferGroupId,
                    amountPaise = amountPaise
                )

                val legs = transactionDao.getTransferLegs(transferGroupId)
                val outLeg = legs.find { it.direction == com.tapledger.core.model.TransactionDirection.OUT }
                val outLegId = outLeg?.id ?: 0L
                val periodId = targetPlan?.periodId ?: currentPeriod.id

                loadDashboard()
                onSuccess(outLegId, periodId)
            } catch (e: Exception) {
                // handle error
            }
        }
    }

    fun nextMonth() {
        val (newYear, newMonth) = if (_uiState.value.currentMonth == 12) {
            (_uiState.value.currentYear + 1) to 1
        } else {
            _uiState.value.currentYear to (_uiState.value.currentMonth + 1)
        }
        loadDashboard(newYear, newMonth)
    }

    fun prevMonth() {
        val (newYear, newMonth) = if (_uiState.value.currentMonth == 1) {
            (_uiState.value.currentYear - 1) to 12
        } else {
            _uiState.value.currentYear to (_uiState.value.currentMonth - 1)
        }
        loadDashboard(newYear, newMonth)
    }

    fun updateAccountOpeningBalance(account: AccountEntity, newOpeningBalanceRupees: String) {
        viewModelScope.launch {
            try {
                val newPaise = MoneyFormatter.toPaise(newOpeningBalanceRupees)
                val updated = account.copy(
                    openingBalancePaise = newPaise,
                    updatedAt = DateTimeUtils.nowEpochMillis()
                )
                accountRepository.updateAccount(updated)
                loadDashboard()
            } catch (e: Exception) {
                // handle invalid input
            }
        }
    }
}
