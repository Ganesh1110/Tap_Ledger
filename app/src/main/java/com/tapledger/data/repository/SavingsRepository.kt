package com.tapledger.data.repository

import com.tapledger.core.database.DatabaseTransactionRunner
import com.tapledger.core.database.dao.AccountDao
import com.tapledger.core.database.dao.CommitmentPaymentDao
import com.tapledger.core.database.dao.FixedDepositDao
import com.tapledger.core.database.dao.MonthCloseDao
import com.tapledger.core.database.dao.RecurringCommitmentDao
import com.tapledger.core.database.dao.SavingsContributionDao
import com.tapledger.core.database.dao.SavingsGoalDao
import com.tapledger.core.database.dao.SavingsPlanEntryDao
import com.tapledger.core.database.dao.TransactionDao
import com.tapledger.core.database.entity.CommitmentPaymentEntity
import com.tapledger.core.database.entity.FixedDepositEntity
import com.tapledger.core.database.entity.RecurringCommitmentEntity
import com.tapledger.core.database.entity.SavingsContributionEntity
import com.tapledger.core.database.entity.SavingsGoalEntity
import com.tapledger.core.database.entity.SavingsPlanEntryEntity
import com.tapledger.core.model.CommitmentPaymentStatus
import com.tapledger.core.model.CompoundingFrequency
import com.tapledger.core.model.RecurringCommitmentType
import com.tapledger.core.model.SavingsContributionSource
import com.tapledger.core.model.SavingsGoalStatus
import com.tapledger.core.util.DateTimeUtils
import com.tapledger.domain.calculator.AccountBalanceCalculator
import com.tapledger.domain.calculator.EmergencyFundCalculator
import com.tapledger.domain.calculator.SavingsCalculator
import com.tapledger.domain.model.SavingsGoalSummary
import com.tapledger.domain.model.SavingsOverviewSummary
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SavingsRepository(
    private val savingsGoalDao: SavingsGoalDao,
    private val savingsContributionDao: SavingsContributionDao,
    private val savingsPlanEntryDao: SavingsPlanEntryDao,
    private val recurringCommitmentDao: RecurringCommitmentDao,
    private val commitmentPaymentDao: CommitmentPaymentDao,
    private val fixedDepositDao: FixedDepositDao,
    private val monthCloseDao: MonthCloseDao,
    private val emergencyFundCalculator: EmergencyFundCalculator,
    private val accountDao: AccountDao,
    private val transactionDao: TransactionDao,
    private val transactionRunner: DatabaseTransactionRunner,
    private val savingsMilestoneEvaluator: com.tapledger.domain.calculator.SavingsMilestoneEvaluator
) {
    private var savingsGuidePreferences: com.tapledger.core.preferences.SavingsGuidePreferences? = null

    @Inject
    constructor(
        savingsGoalDao: SavingsGoalDao,
        savingsContributionDao: SavingsContributionDao,
        savingsPlanEntryDao: SavingsPlanEntryDao,
        recurringCommitmentDao: RecurringCommitmentDao,
        commitmentPaymentDao: CommitmentPaymentDao,
        fixedDepositDao: FixedDepositDao,
        monthCloseDao: MonthCloseDao,
        emergencyFundCalculator: EmergencyFundCalculator,
        accountDao: AccountDao,
        transactionDao: TransactionDao,
        transactionRunner: DatabaseTransactionRunner,
        savingsMilestoneEvaluator: com.tapledger.domain.calculator.SavingsMilestoneEvaluator,
        savingsGuidePreferences: com.tapledger.core.preferences.SavingsGuidePreferences
    ) : this(
        savingsGoalDao,
        savingsContributionDao,
        savingsPlanEntryDao,
        recurringCommitmentDao,
        commitmentPaymentDao,
        fixedDepositDao,
        monthCloseDao,
        emergencyFundCalculator,
        accountDao,
        transactionDao,
        transactionRunner,
        savingsMilestoneEvaluator
    ) {
        this.savingsGuidePreferences = savingsGuidePreferences
    }

    fun getAllGoalsFlow(): Flow<List<SavingsGoalEntity>> = savingsGoalDao.getAllNonArchivedGoals()

    fun getAllActiveGoalsFlow(): Flow<List<SavingsGoalEntity>> = savingsGoalDao.getAllActiveGoals()

    fun getGoalByIdFlow(id: Long): Flow<SavingsGoalEntity?> = savingsGoalDao.getByIdFlow(id)

    suspend fun getGoalById(id: Long): SavingsGoalEntity? = savingsGoalDao.getById(id)

    fun getContributionsForGoal(goalId: Long): Flow<List<SavingsContributionEntity>> =
        savingsContributionDao.getContributionsByGoal(goalId)

    fun getTotalSavedFlow(): Flow<Long> = savingsContributionDao.getTotalSavedFlow()

    suspend fun createGoal(
        name: String,
        targetPaise: Long,
        deadlineEpochMs: Long? = null,
        priority: Int = 1,
        linkedAccountId: Long? = null
    ): Long {
        require(name.isNotBlank()) { "Goal name cannot be empty" }
        require(targetPaise > 0L) { "Goal target must be positive" }

        val now = DateTimeUtils.nowEpochMillis()
        val goal = SavingsGoalEntity(
            name = name.trim(),
            targetPaise = targetPaise,
            deadlineEpochMs = deadlineEpochMs,
            priority = priority,
            status = SavingsGoalStatus.ACTIVE,
            linkedAccountId = linkedAccountId,
            createdAt = now,
            updatedAt = now
        )
        return savingsGoalDao.insert(goal)
    }

    suspend fun updateGoal(
        id: Long,
        name: String,
        targetPaise: Long,
        deadlineEpochMs: Long? = null,
        priority: Int = 1,
        linkedAccountId: Long? = null
    ) {
        require(name.isNotBlank()) { "Goal name cannot be empty" }
        require(targetPaise > 0L) { "Goal target must be positive" }

        transactionRunner.run {
            val existing = savingsGoalDao.getById(id)
                ?: throw IllegalArgumentException("Goal with id $id not found")

            val updated = existing.copy(
                name = name.trim(),
                targetPaise = targetPaise,
                deadlineEpochMs = deadlineEpochMs,
                priority = priority,
                linkedAccountId = linkedAccountId,
                updatedAt = DateTimeUtils.nowEpochMillis()
            )
            savingsGoalDao.update(updated)
        }
    }

    suspend fun deposit(
        goalId: Long,
        amountPaise: Long,
        note: String? = null,
        transactionId: Long? = null,
        periodId: Long? = null,
        source: SavingsContributionSource = SavingsContributionSource.MANUAL
    ): Long {
        require(amountPaise > 0L) { "Deposit amount must be strictly positive" }

        return transactionRunner.run {
            val goal = savingsGoalDao.getById(goalId)
                ?: throw IllegalArgumentException("Goal with id $goalId not found")
            check(goal.status != SavingsGoalStatus.ARCHIVED) { "Cannot contribute to an archived goal" }

            val now = DateTimeUtils.nowEpochMillis()
            val contribution = SavingsContributionEntity(
                goalId = goalId,
                amountPaise = amountPaise,
                source = source,
                transactionId = transactionId,
                periodId = periodId,
                note = note?.trim()?.ifEmpty { null },
                createdAt = now
            )
            val contributionId = savingsContributionDao.insert(contribution)

            // Check if goal reached completion
            val newBalance = savingsContributionDao.getGoalBalance(goalId)
            if (newBalance >= goal.targetPaise && goal.status == SavingsGoalStatus.ACTIVE) {
                savingsGoalDao.updateStatus(goalId, SavingsGoalStatus.DONE, now)
            }

            contributionId
        }
    }

    suspend fun withdraw(
        goalId: Long,
        amountPaise: Long,
        note: String? = null,
        transactionId: Long? = null,
        periodId: Long? = null,
        source: SavingsContributionSource = SavingsContributionSource.MANUAL
    ): Long {
        require(amountPaise > 0L) { "Withdrawal amount must be strictly positive" }

        return transactionRunner.run {
            val goal = savingsGoalDao.getById(goalId)
                ?: throw IllegalArgumentException("Goal with id $goalId not found")
            val currentBalance = savingsContributionDao.getGoalBalance(goalId)
            check(currentBalance >= amountPaise) {
                "Withdrawal amount ($amountPaise paise) exceeds goal balance ($currentBalance paise)"
            }

            val now = DateTimeUtils.nowEpochMillis()
            val contribution = SavingsContributionEntity(
                goalId = goalId,
                amountPaise = -amountPaise,
                source = source,
                transactionId = transactionId,
                periodId = periodId,
                note = note?.trim()?.ifEmpty { null },
                createdAt = now
            )
            val contributionId = savingsContributionDao.insert(contribution)

            // If goal was DONE and is now below target, reactivate it
            val newBalance = currentBalance - amountPaise
            if (newBalance < goal.targetPaise && goal.status == SavingsGoalStatus.DONE) {
                savingsGoalDao.updateStatus(goalId, SavingsGoalStatus.ACTIVE, now)
            }

            contributionId
        }
    }

    suspend fun pauseGoal(id: Long) {
        val now = DateTimeUtils.nowEpochMillis()
        savingsGoalDao.updateStatus(id, SavingsGoalStatus.PAUSED, now)
    }

    suspend fun resumeGoal(id: Long) {
        val now = DateTimeUtils.nowEpochMillis()
        val goal = savingsGoalDao.getById(id) ?: return
        val balance = savingsContributionDao.getGoalBalance(id)
        val newStatus = if (balance >= goal.targetPaise) SavingsGoalStatus.DONE else SavingsGoalStatus.ACTIVE
        savingsGoalDao.updateStatus(id, newStatus, now)
    }

    suspend fun completeGoal(id: Long) {
        val now = DateTimeUtils.nowEpochMillis()
        savingsGoalDao.updateStatus(id, SavingsGoalStatus.DONE, now)
    }

    suspend fun archiveGoal(id: Long) {
        val now = DateTimeUtils.nowEpochMillis()
        savingsGoalDao.updateStatus(id, SavingsGoalStatus.ARCHIVED, now)
    }

    fun getGoalSummaryFlow(goalId: Long): Flow<SavingsGoalSummary?> {
        return combine(
            savingsGoalDao.getByIdFlow(goalId),
            savingsContributionDao.getGoalBalanceFlow(goalId),
            accountDao.getAllActiveAccounts()
        ) { goal, balance, accounts ->
            if (goal == null) return@combine null

            val defaultSavingsAccountId = savingsGuidePreferences?.getConfig()?.savingsAccountId
                ?: accounts.find { it.isSavings }?.id
            val backingAccountId = goal.linkedAccountId ?: defaultSavingsAccountId
            val backingAccount = backingAccountId?.let { id -> accounts.find { it.id == id } }

            val (isOverEarmarked, backedPaise, backedBp) = if (backingAccount != null) {
                val allGoalsForAccount = savingsGoalDao.getGoalsByAccount(backingAccount.id)
                val totalEarmarked = allGoalsForAccount.sumOf { savingsContributionDao.getGoalBalance(it.id) }
                val accountTxs = transactionDao.getAllTransactionsSync()
                val accountBalance = AccountBalanceCalculator.calculateAccountBalance(backingAccount, accountTxs).currentBalancePaise
                val over = totalEarmarked > accountBalance
                val backed = minOf(balance, maxOf(0L, accountBalance))
                val bp = if (balance > 0L) ((backed * 10_000L) / balance).toInt().coerceIn(0, 10_000) else 10_000
                Triple(over, backed, bp)
            } else {
                Triple(false, balance, 10000)
            }

            SavingsCalculator.calculateGoalSummary(
                goal = goal,
                balancePaise = balance,
                asOfEpochMs = DateTimeUtils.nowEpochMillis(),
                isOverEarmarked = isOverEarmarked,
                backedPaise = backedPaise,
                backedBp = backedBp,
                backingAccountName = backingAccount?.name
            )
        }
    }

    fun getOverviewSummaryFlow(): Flow<SavingsOverviewSummary> {
        return combine(
            savingsGoalDao.getAllNonArchivedGoals(),
            accountDao.getAllActiveAccounts()
        ) { goals, accounts ->
            val now = DateTimeUtils.nowEpochMillis()
            val allTxs = transactionDao.getAllTransactionsSync()
            val accountBalances = accounts.associate { acct ->
                acct.id to AccountBalanceCalculator.calculateAccountBalance(acct, allTxs).currentBalancePaise
            }
            val accountsMap = accounts.associateBy { it.id }

            val goalBalances = mutableMapOf<Long, Long>()
            val goalSummaries = mutableListOf<SavingsGoalSummary>()

            var totalSaved = 0L
            var activeCount = 0
            var completedCount = 0

            for (goal in goals) {
                val balance = savingsContributionDao.getGoalBalance(goal.id)
                goalBalances[goal.id] = balance
                totalSaved += balance
                if (goal.status == SavingsGoalStatus.ACTIVE) activeCount++
                if (goal.status == SavingsGoalStatus.DONE) completedCount++
            }

            val defaultSavingsAccountId = savingsGuidePreferences?.getConfig()?.savingsAccountId
                ?: accounts.find { it.isSavings }?.id

            val earmarkWarnings = SavingsCalculator.evaluateAccountEarmarks(
                activeGoals = goals,
                goalBalances = goalBalances,
                accountsMap = accountsMap,
                accountBalances = accountBalances,
                defaultSavingsAccountId = defaultSavingsAccountId
            )
            val overEarmarkedAccountIds = earmarkWarnings.map { it.accountId }.toSet()

            // Calculate backed amounts by grouping goals by backing account
            val backedAmounts = mutableMapOf<Long, Pair<Long, Int>>() // goalId -> (backedPaise, backedBp)
            val goalsByBackingAccount = goals
                .filter { it.status == SavingsGoalStatus.ACTIVE }
                .groupBy { it.linkedAccountId ?: defaultSavingsAccountId }

            for ((accountId, acctGoals) in goalsByBackingAccount) {
                var remaining = if (accountId != null) accountBalances[accountId] ?: 0L else 0L
                val sortedGoals = acctGoals.sortedWith(compareBy({ it.priority }, { it.createdAt }))
                for (g in sortedGoals) {
                    val balance = goalBalances[g.id] ?: 0L
                    val backed = minOf(balance, maxOf(0L, remaining))
                    remaining = maxOf(0L, remaining - backed)
                    val bp = if (balance > 0L) ((backed * 10_000L) / balance).toInt().coerceIn(0, 10_000) else 10_000
                    backedAmounts[g.id] = backed to bp
                }
            }

            for (goal in goals) {
                val balance = goalBalances[goal.id] ?: 0L
                val backingAccountId = goal.linkedAccountId ?: defaultSavingsAccountId
                val backingAccountName = backingAccountId?.let { accountsMap[it]?.name }
                val (backed, bp) = backedAmounts[goal.id] ?: (balance to 10000)
                val isOverEarmarked = backingAccountId != null && overEarmarkedAccountIds.contains(backingAccountId)
                goalSummaries.add(
                    SavingsCalculator.calculateGoalSummary(
                        goal = goal,
                        balancePaise = balance,
                        asOfEpochMs = now,
                        isOverEarmarked = isOverEarmarked,
                        backedPaise = backed,
                        backedBp = bp,
                        backingAccountName = backingAccountName
                    )
                )
            }

            SavingsOverviewSummary(
                totalSavedPaise = totalSaved,
                activeGoalsCount = activeCount,
                completedGoalsCount = completedCount,
                goals = goalSummaries,
                accountEarmarkWarnings = earmarkWarnings
            )
        }
    }

    // --- Phase 2: Monthly Savings Plan & Emergency Fund ---

    fun getSavingsPlanForPeriod(periodId: Long): Flow<List<SavingsPlanEntryEntity>> =
        savingsPlanEntryDao.getEntriesForPeriod(periodId)

    suspend fun getSavingsPlanForPeriodSync(periodId: Long): List<SavingsPlanEntryEntity> =
        savingsPlanEntryDao.getEntriesForPeriodSync(periodId)

    fun getEmergencyFundCoverageTenthsFlow(lookbackMonths: Int = 3): Flow<Long> {
        return combine(
            savingsContributionDao.getTotalSavedFlow(),
            monthCloseDao.getAllMonthClosesFlow()
        ) { totalSaved, monthCloses ->
            emergencyFundCalculator.calculateFromMonthCloses(
                totalSavingsPaise = totalSaved,
                monthCloses = monthCloses,
                lookbackMonths = lookbackMonths
            )
        }
    }

    // --- Phase 3: Recurring Commitments (SIP, RD, Chit, Gold) ---

    fun getAllCommitmentsFlow(): Flow<List<RecurringCommitmentEntity>> =
        recurringCommitmentDao.getAllFlow()

    fun getAllActiveCommitmentsFlow(): Flow<List<RecurringCommitmentEntity>> =
        recurringCommitmentDao.getAllActiveFlow()

    suspend fun createCommitment(
        type: RecurringCommitmentType,
        name: String,
        amountPaise: Long,
        dueDay: Int,
        accountId: Long? = null,
        goalId: Long? = null
    ): Long {
        require(name.isNotBlank()) { "Commitment name cannot be empty" }
        require(amountPaise > 0L) { "Commitment amount must be positive" }
        require(dueDay in 1..31) { "Due day must be between 1 and 31" }

        val commitment = RecurringCommitmentEntity(
            type = type,
            name = name.trim(),
            amountPaise = amountPaise,
            dueDay = dueDay,
            accountId = accountId,
            goalId = goalId,
            active = true,
            createdAt = DateTimeUtils.nowEpochMillis()
        )
        return recurringCommitmentDao.insert(commitment)
    }

    suspend fun updateCommitmentActiveStatus(id: Long, active: Boolean) {
        recurringCommitmentDao.updateActiveStatus(id, active)
    }

    suspend fun deleteCommitment(id: Long) {
        recurringCommitmentDao.delete(id)
    }

    fun getCommitmentPaymentsForPeriod(periodId: Long): Flow<List<CommitmentPaymentEntity>> =
        commitmentPaymentDao.getPaymentsForPeriodFlow(periodId)

    suspend fun markCommitmentPaid(
        commitmentId: Long,
        periodId: Long,
        transactionId: Long
    ) {
        val existing = commitmentPaymentDao.getPaymentForCommitmentAndPeriod(commitmentId, periodId)
        if (existing != null) {
            commitmentPaymentDao.markPaid(
                commitmentId = commitmentId,
                periodId = periodId,
                transactionId = transactionId,
                status = CommitmentPaymentStatus.PAID
            )
        } else {
            commitmentPaymentDao.insert(
                CommitmentPaymentEntity(
                    commitmentId = commitmentId,
                    periodId = periodId,
                    paidTransactionId = transactionId,
                    status = CommitmentPaymentStatus.PAID,
                    createdAt = DateTimeUtils.nowEpochMillis()
                )
            )
        }
    }

    // --- Phase 3: Fixed Deposits ---

    fun getAllFixedDepositsFlow(): Flow<List<FixedDepositEntity>> =
        fixedDepositDao.getAllFlow()

    suspend fun createFixedDeposit(
        name: String,
        principalPaise: Long,
        rateBp: Int,
        startMs: Long,
        maturityMs: Long,
        compounding: CompoundingFrequency = CompoundingFrequency.QUARTERLY,
        goalId: Long? = null
    ): Long {
        require(name.isNotBlank()) { "Fixed deposit name cannot be empty" }
        require(principalPaise > 0L) { "Principal must be positive" }
        require(rateBp > 0) { "Interest rate must be positive" }
        require(maturityMs > startMs) { "Maturity date must be after start date" }

        val fd = FixedDepositEntity(
            name = name.trim(),
            principalPaise = principalPaise,
            rateBp = rateBp,
            startMs = startMs,
            maturityMs = maturityMs,
            compounding = compounding,
            goalId = goalId,
            createdAt = DateTimeUtils.nowEpochMillis()
        )
        return fixedDepositDao.insert(fd)
    }

    suspend fun deleteFixedDeposit(id: Long) {
        fixedDepositDao.delete(id)
    }

    // --- Phase 4: Habits, Rules & Milestones ---

    suspend fun applyRuleContribution(
        goalId: Long,
        amountPaise: Long,
        note: String,
        transactionId: Long? = null,
        periodId: Long? = null
    ): Long {
        return deposit(
            goalId = goalId,
            amountPaise = amountPaise,
            note = note,
            transactionId = transactionId,
            periodId = periodId,
            source = SavingsContributionSource.RULE
        )
    }

    fun getMilestonesFlow(lookbackMonths: Int = 3): Flow<List<com.tapledger.domain.model.SavingsMilestoneBadge>> {
        return combine(
            getOverviewSummaryFlow(),
            getEmergencyFundCoverageTenthsFlow(lookbackMonths)
        ) { overview, efTenths ->
            val totalContributions = savingsContributionDao.getAllContributionsSync().size
            savingsMilestoneEvaluator.evaluateMilestones(
                totalSavedPaise = overview.totalSavedPaise,
                emergencyFundTenths = efTenths,
                totalGoalsCount = overview.goals.size,
                completedGoalsCount = overview.completedGoalsCount,
                totalContributionsCount = totalContributions
            )
        }
    }
}
