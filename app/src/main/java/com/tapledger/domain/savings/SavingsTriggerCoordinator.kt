package com.tapledger.domain.savings

import android.content.Context
import com.tapledger.core.database.dao.AccountDao
import com.tapledger.core.database.dao.BudgetPeriodDao
import com.tapledger.core.database.dao.CommitmentPaymentDao
import com.tapledger.core.database.dao.RecurringCommitmentDao
import com.tapledger.core.database.dao.SavingsTransferPlanDao
import com.tapledger.core.database.dao.TransactionAllocationDao
import com.tapledger.core.database.dao.TransactionDao
import com.tapledger.core.database.entity.SavingsTransferPlanEntity
import com.tapledger.core.database.entity.TransactionEntity
import com.tapledger.core.model.AccountType
import com.tapledger.core.model.BudgetBucket
import com.tapledger.core.model.BudgetPeriodStatus
import com.tapledger.core.model.CommitmentPaymentStatus
import com.tapledger.core.model.TransactionDirection
import com.tapledger.core.model.TransactionKind
import com.tapledger.core.model.TransactionStatus
import com.tapledger.core.model.TransferPlanKind
import com.tapledger.core.model.TransferPlanStatus
import com.tapledger.core.preferences.SavingsGuidePreferences
import com.tapledger.core.util.DateTimeUtils
import com.tapledger.data.repository.BudgetRepository
import com.tapledger.data.repository.TransferPlanRepository
import com.tapledger.domain.calculator.BudgetCalculator
import com.tapledger.domain.model.BudgetCategorySummary
import com.tapledger.domain.model.BudgetPeriodSummary
import com.tapledger.domain.calculator.AccountBalanceCalculator
import com.tapledger.domain.calculator.CardDueItem
import com.tapledger.domain.calculator.SpareCalculationResult
import com.tapledger.domain.calculator.SpareCalculator
import com.tapledger.domain.calculator.UnpaidCommitmentItem
import com.tapledger.service.SavingsGuideNotificationHelper
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SavingsTriggerCoordinator(
    private val savingsTransferPlanDao: SavingsTransferPlanDao,
    private val transferPlanRepository: TransferPlanRepository,
    private val budgetPeriodDao: BudgetPeriodDao,
    private val accountDao: AccountDao,
    private val transactionDao: TransactionDao,
    private val transactionAllocationDao: TransactionAllocationDao,
    private val budgetRepository: BudgetRepository,
    private val recurringCommitmentDao: RecurringCommitmentDao,
    private val commitmentPaymentDao: CommitmentPaymentDao,
    private val savingsGuidePreferences: SavingsGuidePreferences,
    private val spareCalculator: SpareCalculator,
    private val context: Context? = null
) {
    @Inject
    constructor(
        @ApplicationContext appContext: Context,
        savingsTransferPlanDao: SavingsTransferPlanDao,
        transferPlanRepository: TransferPlanRepository,
        budgetPeriodDao: BudgetPeriodDao,
        accountDao: AccountDao,
        transactionDao: TransactionDao,
        transactionAllocationDao: TransactionAllocationDao,
        budgetRepository: BudgetRepository,
        recurringCommitmentDao: RecurringCommitmentDao,
        commitmentPaymentDao: CommitmentPaymentDao,
        savingsGuidePreferences: SavingsGuidePreferences,
        spareCalculator: SpareCalculator
    ) : this(
        savingsTransferPlanDao,
        transferPlanRepository,
        budgetPeriodDao,
        accountDao,
        transactionDao,
        transactionAllocationDao,
        budgetRepository,
        recurringCommitmentDao,
        commitmentPaymentDao,
        savingsGuidePreferences,
        spareCalculator,
        appContext
    )

    /**
     * Resolves the configured or default source (spending) and destination (savings) accounts.
     */
    suspend fun resolveAccounts(): Pair<com.tapledger.core.database.entity.AccountEntity?, com.tapledger.core.database.entity.AccountEntity?> {
        val accounts = accountDao.getAllAccountsSync().filter { it.isActive }
        val config = savingsGuidePreferences.getConfig()

        val spendingAccount = if (config.spendingAccountId != null) {
            accounts.find { it.id == config.spendingAccountId }
        } else {
            accounts.find { it.type == AccountType.BANK && it.name.contains("HDFC", ignoreCase = true) }
                ?: accounts.find { it.type == AccountType.BANK }
        }

        val savingsAccount = if (config.savingsAccountId != null) {
            accounts.find { it.id == config.savingsAccountId }
        } else {
            accounts.find { it.type == AccountType.BANK && it.id != spendingAccount?.id && it.name.contains("SBI", ignoreCase = true) }
                ?: accounts.find { it.type == AccountType.BANK && it.id != spendingAccount?.id }
        }

        return Pair(spendingAccount, savingsAccount)
    }

    /**
     * Evaluates spare funds for the given period using conservative math (Flow §18, D013, D015).
     */
    suspend fun calculateSpare(
        periodId: Long,
        nowEpochMs: Long = DateTimeUtils.nowEpochMillis()
    ): SpareCalculationResult? {
        val period = budgetPeriodDao.getById(periodId) ?: return null
        if (period.status == BudgetPeriodStatus.CLOSED) return null

        val (spendingAccount, savingsAccount) = resolveAccounts()
        if (spendingAccount == null || savingsAccount == null) return null

        val accounts = accountDao.getAllAccountsSync().filter { it.isActive }
        val spendingTxs = transactionDao.getTransactionsForAccountSync(spendingAccount.id)
        val spendingBalance = AccountBalanceCalculator.calculateAccountBalance(spendingAccount, spendingTxs, asOfEpochMillis = nowEpochMs).currentBalancePaise

        val cardDueItems = accounts
            .filter { it.type == AccountType.CREDIT_CARD }
            .map { card ->
                val cardTxs = transactionDao.getTransactionsForAccountSync(card.id)
                val bal = AccountBalanceCalculator.calculateAccountBalance(card, cardTxs, asOfEpochMillis = nowEpochMs).currentBalancePaise
                CardDueItem(
                    accountId = card.id,
                    cardName = card.name,
                    balancePaise = bal
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
        val categorySummaries = budgetSummary.categorySummaries
        val savingsCategories = categorySummaries.filter { it.allocation.bucketSnapshot == BudgetBucket.SAVINGS }
        val savingsAdjustedBudget = savingsCategories.sumOf { it.adjustedBudgetPaise }
        val actualSavings = savingsCategories.sumOf { it.netSpentPaise }
        val pendingCount = transactionDao.getPendingCountSync()

        val config = savingsGuidePreferences.getConfig()
        return spareCalculator.calculateSpare(
            hdfcBalancePaise = spendingBalance,
            cardsPaidFromHdfc = cardDueItems,
            categorySummaries = categorySummaries,
            unpaidCommitmentsFromHdfc = unpaidCommitmentItems,
            bufferPaise = config.bufferPaise,
            savingsAdjustedBudgetPaise = savingsAdjustedBudget,
            actualSavingsPaise = actualSavings,
            isDriftBannerActive = false,
            hasUnassignedPendingTransactions = pendingCount > 0
        )
    }

    /**
     * Trigger 1: Payday Detection (Milestone M21, §6).
     * An INCOME transaction is confirmed with amount * 100 >= planned_income * threshold% (default 50%).
     * Creates a PAYDAY plan and a local notification.
     */
    suspend fun onIncomeConfirmed(
        transaction: TransactionEntity,
        nowEpochMs: Long = DateTimeUtils.nowEpochMillis()
    ): SavingsTransferPlanEntity? {
        if (transaction.kind != TransactionKind.INCOME || transaction.status != TransactionStatus.CONFIRMED) {
            return null
        }

        val period = budgetPeriodDao.getPeriodForTimestamp(transaction.occurredAt) ?: return null
        if (period.status == BudgetPeriodStatus.CLOSED || period.incomePaise <= 0L) {
            return null
        }

        val config = savingsGuidePreferences.getConfig()
        val thresholdPaise = (period.incomePaise * config.paydayThresholdPct.toLong()) / 100L
        if (transaction.amountPaise < thresholdPaise) {
            return null
        }

        val (spendingAccount, savingsAccount) = resolveAccounts()
        if (spendingAccount == null || savingsAccount == null) return null

        // If spending account is explicitly set, income must arrive in it
        if (config.spendingAccountId != null && transaction.accountId != config.spendingAccountId) {
            return null
        }

        val spareResult = calculateSpare(period.id, nowEpochMs) ?: return null
        if (spareResult.isSafetyGateTriggered) return null

        val paydaySuggestion = spareResult.paydaySuggestionPaise
        if (paydaySuggestion <= 0L) return null

        val planId = transferPlanRepository.createOrUpdateSuggestedPlan(
            periodId = period.id,
            kind = TransferPlanKind.PAYDAY,
            sourceAccountId = spendingAccount.id,
            destAccountId = savingsAccount.id,
            suggestedPaise = paydaySuggestion,
            triggerTransactionId = transaction.id,
            nowEpochMs = nowEpochMs
        )

        val plan = savingsTransferPlanDao.getById(planId)
        if (config.notificationsEnabled && context != null && plan != null) {
            SavingsGuideNotificationHelper.postPaydayPlanNotification(
                context = context,
                planId = planId,
                amountPaise = paydaySuggestion,
                sourceAccountName = spendingAccount.name,
                destAccountName = savingsAccount.name
            )
            savingsGuidePreferences.incrementPlanNotificationCount(planId)
        }

        return plan
    }

    /**
     * Trigger 2: Month-End Sweep (Milestone M21, §6).
     * Checks if current day (in IST) is within the last N days of the period.
     * Generates a SWEEP plan if sweepable leftover is >= minSweepPaise (default ₹500).
     */
    suspend fun runMonthEndSweepCheck(
        nowEpochMs: Long = DateTimeUtils.nowEpochMillis()
    ): SavingsTransferPlanEntity? {
        val period = budgetPeriodDao.getOpenPeriodForTimestamp(nowEpochMs) ?: return null
        val config = savingsGuidePreferences.getConfig()

        val zone = DateTimeUtils.IST_ZONE
        val today = java.time.Instant.ofEpochMilli(nowEpochMs).atZone(zone).toLocalDate()
        val periodEnd = java.time.Instant.ofEpochMilli(period.endAt).atZone(zone).toLocalDate()
        val daysUntilEnd = ChronoUnit.DAYS.between(today, periodEnd)

        if (daysUntilEnd > config.sweepWindowDays.toLong()) {
            return null
        }

        val (spendingAccount, savingsAccount) = resolveAccounts()
        if (spendingAccount == null || savingsAccount == null) return null

        val spareResult = calculateSpare(period.id, nowEpochMs) ?: return null
        if (spareResult.isSafetyGateTriggered) return null

        val sweepSuggestion = spareResult.monthEndSweepPaise
        if (sweepSuggestion < config.minSweepPaise) return null

        val existingPlan = savingsTransferPlanDao.getByPeriodAndKind(period.id, TransferPlanKind.SWEEP)
        val planId = transferPlanRepository.createOrUpdateSuggestedPlan(
            periodId = period.id,
            kind = TransferPlanKind.SWEEP,
            sourceAccountId = spendingAccount.id,
            destAccountId = savingsAccount.id,
            suggestedPaise = sweepSuggestion,
            nowEpochMs = nowEpochMs
        )

        val plan = savingsTransferPlanDao.getById(planId)
        if (existingPlan == null && config.notificationsEnabled && context != null && plan != null) {
            SavingsGuideNotificationHelper.postSweepPlanNotification(
                context = context,
                planId = planId,
                amountPaise = sweepSuggestion,
                sourceAccountName = spendingAccount.name,
                destAccountName = savingsAccount.name
            )
            savingsGuidePreferences.incrementPlanNotificationCount(planId)
        }

        return plan
    }

    /**
     * Trigger 3 & 4: Reminders & Unverified Nudges (Milestone M21, §6).
     * - Re-nudge if a plan is still SUGGESTED after 2 days (Max 2 notifications per plan).
     * - Nudge if a plan in MARKED_MOVED is still unverified after 3 days.
     */
    suspend fun runPendingRemindersCheck(
        nowEpochMs: Long = DateTimeUtils.nowEpochMillis()
    ) {
        val openPeriods = budgetPeriodDao.getAllPeriodsSync().filter { it.status == BudgetPeriodStatus.OPEN }
        val config = savingsGuidePreferences.getConfig()

        for (period in openPeriods) {
            val plans = savingsTransferPlanDao.getPlansForPeriod(period.id)
            for (plan in plans) {
                if (plan.status == TransferPlanStatus.SUGGESTED) {
                    val elapsedMs = nowEpochMs - plan.createdAt
                    val twoDaysMs = 2L * 86_400_000L
                    if (elapsedMs >= twoDaysMs && savingsGuidePreferences.getPlanNotificationCount(plan.id) < 2) {
                        if (config.notificationsEnabled && context != null) {
                            SavingsGuideNotificationHelper.postPlanReminderNotification(
                                context = context,
                                planId = plan.id,
                                amountPaise = plan.suggestedPaise
                            )
                        }
                        savingsGuidePreferences.incrementPlanNotificationCount(plan.id)
                    }
                } else if (plan.status == TransferPlanStatus.MARKED_MOVED) {
                    val elapsedMs = nowEpochMs - (plan.movedAt ?: plan.createdAt)
                    val threeDaysMs = 3L * 86_400_000L
                    if (elapsedMs >= threeDaysMs && !savingsGuidePreferences.hasSentUnverifiedNudge(plan.id)) {
                        if (config.notificationsEnabled && context != null) {
                            SavingsGuideNotificationHelper.postUnverifiedNudgeNotification(
                                context = context,
                                planId = plan.id,
                                amountPaise = plan.movedPaise ?: plan.suggestedPaise
                            )
                        }
                        savingsGuidePreferences.setSentUnverifiedNudge(plan.id, true)
                    }
                }
            }
        }
    }

    /**
     * Trigger: Dip Guard (Milestone M22, §7 Dip Guard).
     * An outbound debit from the savings account that is NOT a transfer to another account.
     * Prompts the user: "Spending from savings. Which goal?" and posts a local notification.
     */
    suspend fun onSavingsDebitDetected(
        transaction: TransactionEntity,
        nowEpochMs: Long = DateTimeUtils.nowEpochMillis()
    ): Boolean {
        if (transaction.direction != TransactionDirection.OUT || transaction.status != TransactionStatus.CONFIRMED) {
            return false
        }
        if (transaction.transferGroupId != null) {
            // Transfers between accounts are handled via transfer lifecycle, not dip guard
            return false
        }

        val txAccountId = transaction.accountId ?: return false

        val (_, savingsAccount) = resolveAccounts()
        val isSavingsAccount = if (savingsAccount != null) {
            txAccountId == savingsAccount.id
        } else {
            val account = accountDao.getById(txAccountId)
            account?.isSavings == true
        }

        if (!isSavingsAccount) {
            return false
        }

        val config = savingsGuidePreferences.getConfig()
        val accountName = savingsAccount?.name ?: accountDao.getById(txAccountId)?.name ?: "Savings Account"

        if (config.notificationsEnabled && context != null) {
            SavingsGuideNotificationHelper.postDipGuardNotification(
                context = context,
                transactionId = transaction.id,
                amountPaise = transaction.amountPaise,
                accountName = accountName
            )
        }
        return true
    }
}
