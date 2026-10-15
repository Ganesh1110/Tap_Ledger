package com.tapledger.data.repository

import com.tapledger.core.database.DatabaseTransactionRunner
import com.tapledger.core.database.dao.AccountDao
import com.tapledger.core.database.dao.SavingsContributionDao
import com.tapledger.core.database.dao.SavingsGoalDao
import com.tapledger.core.database.dao.TransactionDao
import com.tapledger.core.database.entity.SavingsContributionEntity
import com.tapledger.core.database.entity.SavingsGoalEntity
import com.tapledger.core.model.SavingsContributionSource
import com.tapledger.core.model.SavingsGoalStatus
import com.tapledger.core.util.DateTimeUtils
import com.tapledger.domain.calculator.AccountBalanceCalculator
import com.tapledger.domain.calculator.SavingsCalculator
import com.tapledger.domain.model.SavingsGoalSummary
import com.tapledger.domain.model.SavingsOverviewSummary
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SavingsRepository @Inject constructor(
    private val savingsGoalDao: SavingsGoalDao,
    private val savingsContributionDao: SavingsContributionDao,
    private val accountDao: AccountDao,
    private val transactionDao: TransactionDao,
    private val transactionRunner: DatabaseTransactionRunner
) {

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

            val isOverEarmarked = if (goal.linkedAccountId != null) {
                val account = accounts.find { it.id == goal.linkedAccountId }
                if (account != null) {
                    val allGoalsForAccount = savingsGoalDao.getGoalsByAccount(account.id)
                    val totalEarmarked = allGoalsForAccount.sumOf { savingsContributionDao.getGoalBalance(it.id) }
                    val accountTxs = transactionDao.getAllTransactionsSync()
                    val accountBalance = AccountBalanceCalculator.calculateAccountBalance(account, accountTxs).currentBalancePaise
                    totalEarmarked > accountBalance
                } else false
            } else false

            SavingsCalculator.calculateGoalSummary(
                goal = goal,
                balancePaise = balance,
                asOfEpochMs = DateTimeUtils.nowEpochMillis(),
                isOverEarmarked = isOverEarmarked
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

            val earmarkWarnings = SavingsCalculator.evaluateAccountEarmarks(
                activeGoals = goals,
                goalBalances = goalBalances,
                accountsMap = accountsMap,
                accountBalances = accountBalances
            )
            val overEarmarkedAccountIds = earmarkWarnings.map { it.accountId }.toSet()

            for (goal in goals) {
                val balance = goalBalances[goal.id] ?: 0L
                val isOverEarmarked = goal.linkedAccountId != null && overEarmarkedAccountIds.contains(goal.linkedAccountId)
                goalSummaries.add(
                    SavingsCalculator.calculateGoalSummary(
                        goal = goal,
                        balancePaise = balance,
                        asOfEpochMs = now,
                        isOverEarmarked = isOverEarmarked
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
}
