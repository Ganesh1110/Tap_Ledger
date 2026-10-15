package com.tapledger.domain.monthclose

import com.tapledger.core.database.DatabaseTransactionRunner
import com.tapledger.core.database.dao.AccountDao
import com.tapledger.core.database.dao.AdvanceLedgerDao
import com.tapledger.core.database.dao.BudgetAdjustmentDao
import com.tapledger.core.database.dao.BudgetAllocationDao
import com.tapledger.core.database.dao.BudgetPeriodDao
import com.tapledger.core.database.dao.CategoryDao
import com.tapledger.core.database.dao.MonthCloseDao
import com.tapledger.core.database.dao.TransactionAllocationDao
import com.tapledger.core.database.dao.TransactionDao
import com.tapledger.core.database.entity.AdvanceLedgerEntity
import com.tapledger.core.database.entity.BudgetAllocationEntity
import com.tapledger.core.database.entity.BudgetAdjustmentEntity
import com.tapledger.core.database.entity.BudgetPeriodEntity
import com.tapledger.core.database.entity.CategoryEntity
import com.tapledger.core.database.entity.MonthCloseEntity
import com.tapledger.core.model.AdvanceDirection
import com.tapledger.core.model.AdvancePolicy
import com.tapledger.core.model.BudgetBucket
import com.tapledger.core.model.BudgetPeriodStatus
import com.tapledger.core.model.MonthCloseReconciliationStatus
import com.tapledger.core.model.TransactionStatus
import com.tapledger.core.util.DateTimeUtils
import com.tapledger.data.repository.BudgetRepository
import com.tapledger.domain.calculator.AccountBalanceCalculator
import com.tapledger.domain.calculator.BudgetCalculator
import com.tapledger.domain.model.BudgetPeriodSummary
import com.tapledger.domain.model.IntegritySeverity
import com.tapledger.domain.validator.DataIntegrityChecker
import com.tapledger.core.model.SavingsGoalStatus
import com.tapledger.core.model.SavingsContributionSource
import com.tapledger.core.database.dao.SavingsGoalDao
import com.tapledger.core.database.dao.SavingsPlanEntryDao
import com.tapledger.core.database.dao.SavingsContributionDao
import com.tapledger.core.database.entity.SavingsContributionEntity
import com.tapledger.domain.calculator.SavingsPlanner
import java.time.YearMonth
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Flow.md §10: Budget Period Lifecycle & Ordered Month-Close Engine
 * - §10.1: Chronological sequential closing & atomic reopen protocol
 * - §10.2: Scoped pre-close checklist gates (now >= end_at, pending == 0, unassigned == 0, auditor == 0)
 * - §10.3: Headroom-capped deficit carry-forward & two-tier prioritized absorption
 * - §10.4: Reopening latest closed month only, reversing advance rows and snapshot atomically
 */
@Singleton
class MonthCloseEngine @Inject constructor(
    private val transactionRunner: DatabaseTransactionRunner,
    private val budgetPeriodDao: BudgetPeriodDao,
    private val monthCloseDao: MonthCloseDao,
    private val advanceLedgerDao: AdvanceLedgerDao,
    private val budgetAllocationDao: BudgetAllocationDao,
    private val budgetAdjustmentDao: BudgetAdjustmentDao,
    private val transactionDao: TransactionDao,
    private val transactionAllocationDao: TransactionAllocationDao,
    private val accountDao: AccountDao,
    private val categoryDao: CategoryDao,
    private val budgetRepository: BudgetRepository,
    private val dataIntegrityChecker: DataIntegrityChecker,
    private val savingsGoalDao: SavingsGoalDao? = null,
    private val savingsPlanEntryDao: SavingsPlanEntryDao? = null,
    private val savingsContributionDao: SavingsContributionDao? = null,
    private val savingsPlanner: SavingsPlanner? = null
) {

    data class PreCloseChecklistResult(
        val isNowPastPeriodEnd: Boolean,
        val isPreviousPeriodClosed: Boolean,
        val isPeriodReviewed: Boolean,
        val scopedPendingCount: Int,
        val scopedUnassignedCount: Int,
        val criticalOrErrorIssueCount: Int,
        val canClose: Boolean
    )

    /**
     * Evaluates the pre-close checklist gates for Period M (Flow.md §10.2).
     */
    suspend fun evaluatePreCloseChecklist(periodId: Long): PreCloseChecklistResult {
        val period = budgetPeriodDao.getById(periodId)
            ?: throw IllegalArgumentException("Budget period #$periodId not found")

        val now = DateTimeUtils.nowEpochMillis()
        val isNowPastPeriodEnd = now >= period.endAt

        val allPeriods = budgetPeriodDao.getAllPeriodsSync().sortedBy { it.startAt }
        val earlierOpenPeriods = allPeriods.filter { it.startAt < period.startAt && it.status == BudgetPeriodStatus.OPEN }
        val isPreviousPeriodClosed = earlierOpenPeriods.isEmpty()

        val isPeriodReviewed = period.isReviewed

        val allTransactions = transactionDao.getAllTransactionsSync()
        val scopedPendingCount = allTransactions.count {
            it.occurredAt < period.endAt && it.status == TransactionStatus.PENDING
        }
        val scopedUnassignedCount = allTransactions.count {
            it.occurredAt < period.endAt && it.accountId == null &&
                it.status != TransactionStatus.VOIDED && it.status != TransactionStatus.IGNORED
        }

        val allAccounts = accountDao.getAllAccountsSync()
        val allCategories = categoryDao.getAllCategoriesSync()
        val allAllocations = budgetAllocationDao.getAllocationsForPeriodSync(periodId)
        val allTxAllocations = transactionAllocationDao.getAllTransactionAllocationsSync()
        val allMonthCloses = monthCloseDao.getAllMonthClosesSync()

        val integrityIssues = dataIntegrityChecker.verifyIntegrity(
            accounts = allAccounts,
            categories = allCategories,
            periods = allPeriods,
            allocations = allAllocations,
            transactions = allTransactions,
            transactionAllocations = allTxAllocations,
            monthCloses = allMonthCloses
        )
        val criticalOrErrorCount = integrityIssues.count {
            it.severity == IntegritySeverity.CRITICAL || it.severity == IntegritySeverity.ERROR
        }

        val canClose = isNowPastPeriodEnd &&
            isPreviousPeriodClosed &&
            isPeriodReviewed &&
            scopedPendingCount == 0 &&
            scopedUnassignedCount == 0 &&
            criticalOrErrorCount == 0

        return PreCloseChecklistResult(
            isNowPastPeriodEnd = isNowPastPeriodEnd,
            isPreviousPeriodClosed = isPreviousPeriodClosed,
            isPeriodReviewed = isPeriodReviewed,
            scopedPendingCount = scopedPendingCount,
            scopedUnassignedCount = scopedUnassignedCount,
            criticalOrErrorIssueCount = criticalOrErrorCount,
            canClose = canClose
        )
    }

    /**
     * Executes the ordered month-close Room transaction for Period M (Flow.md §10.2, §10.3).
     */
    suspend fun closeMonth(
        periodId: Long,
        reconciliationStatus: MonthCloseReconciliationStatus = MonthCloseReconciliationStatus.RECONCILED,
        overrideReason: String? = null,
        allowUnreviewedOverride: Boolean = false,
        surplusGoalId: Long? = null
    ): Long {
        val now = DateTimeUtils.nowEpochMillis()
        return transactionRunner.run {
            val period = budgetPeriodDao.getById(periodId)
                ?: throw IllegalArgumentException("Budget period #$periodId not found")

            require(period.status == BudgetPeriodStatus.OPEN) {
                "Cannot close period #${period.id}: already ${period.status}"
            }

            // Gate 1: now >= period.endAt
            require(now >= period.endAt) {
                "Cannot close period #${period.id} before its end date (${period.endAt}) (§10.2)"
            }

            // Gate 2: Sequential chronological closing (all earlier periods must be CLOSED)
            val allPeriods = budgetPeriodDao.getAllPeriodsSync().sortedBy { it.startAt }
            val earlierOpen = allPeriods.filter { it.startAt < period.startAt && it.status == BudgetPeriodStatus.OPEN }
            if (earlierOpen.isNotEmpty()) {
                val earliest = earlierOpen.first()
                throw IllegalStateException("Cannot close period: earlier period (${earliest.year}-${earliest.month}) is still OPEN (§10.1, §10.3)")
            }

            // Gate 3: is_reviewed == true (or explicit confirmation)
            require(period.isReviewed || allowUnreviewedOverride) {
                "Cannot close period: period is marked Needs Review (is_reviewed == false) (§10.1, §10.2)"
            }

            // Gate 4: Scoped Pending == 0 (occurred_at < period.endAt)
            val allTransactions = transactionDao.getAllTransactionsSync()
            val pendingTx = allTransactions.filter {
                it.occurredAt < period.endAt && it.status == TransactionStatus.PENDING
            }
            if (pendingTx.isNotEmpty()) {
                throw IllegalStateException("Cannot close period: ${pendingTx.size} pending transactions exist before period end (§10.2)")
            }

            // Gate 5: Scoped Unassigned == 0 (occurred_at < period.endAt)
            val unassignedTx = allTransactions.filter {
                it.occurredAt < period.endAt && it.accountId == null &&
                    it.status != TransactionStatus.VOIDED && it.status != TransactionStatus.IGNORED
            }
            if (unassignedTx.isNotEmpty()) {
                throw IllegalStateException("Cannot close period: ${unassignedTx.size} unassigned transactions exist before period end (§3.1, §10.2)")
            }

            // Step 1: Ensure Next Period M+1 Exists (Initialize NextBase & determine Headroom)
            val nextYm = YearMonth.of(period.year, period.month).plusMonths(1)
            val nextPeriod = budgetRepository.getOrCreateBudgetPeriod(nextYm.year, nextYm.monthValue)

            // Calculate Period Summary for Period M
            val currentAllocations = budgetAllocationDao.getAllocationsForPeriodSync(period.id)
            val currentAdjustments = budgetAdjustmentDao.getAdjustmentsForPeriodSync(period.id)
            val currentAdvances = advanceLedgerDao.getAppliedToPeriodSync(period.id)
            val currentTxList = transactionDao.getTransactionsInPeriod(period.startAt, period.endAt)
            val currentTxAllocations = transactionAllocationDao.getAllocationsInPeriod(period.startAt, period.endAt)

            val summary = BudgetCalculator.calculateBudgetPeriodSummary(
                period = period,
                allocations = currentAllocations,
                adjustments = currentAdjustments,
                transactions = currentTxList,
                allocationsForTransactions = currentTxAllocations,
                advances = currentAdvances
            )

            val unassignedPoolRows = advanceLedgerDao.getUnassignedPoolSync()
            val nextAllocations = budgetAllocationDao.getAllocationsForPeriodSync(nextPeriod.id).associateBy { it.categoryId }
            val nextAdjustments = budgetAdjustmentDao.getAdjustmentsForPeriodSync(nextPeriod.id)
            val nextAdvances = advanceLedgerDao.getAppliedToPeriodSync(nextPeriod.id).toMutableList()
            val allCategories = categoryDao.getAllCategoriesSync()

            val plan = calculateAdvancePlan(
                period = period,
                nextPeriodId = nextPeriod.id,
                nextPeriodYear = nextPeriod.year,
                nextPeriodMonth = nextPeriod.month,
                summary = summary,
                nextAllocations = nextAllocations,
                nextAdjustments = nextAdjustments,
                nextAdvancesInitial = nextAdvances,
                unassignedPoolRows = unassignedPoolRows,
                allCategories = allCategories,
                now = now
            )

            if (plan.entriesToInsert.isNotEmpty()) {
                advanceLedgerDao.insertAll(plan.entriesToInsert)
            }

            // Lock Period M
            budgetPeriodDao.update(
                period.copy(
                    status = BudgetPeriodStatus.CLOSED,
                    closedAt = now,
                    updatedAt = now
                )
            )

            // Closing balances snapshot
            val activeAccounts = accountDao.getAllAccountsSync().filter { it.isActive }
            val balanceMap = mutableMapOf<String, Long>()
            for (acc in activeAccounts) {
                val accTxs = transactionDao.getTransactionsForAccountSync(acc.id)
                val bal = AccountBalanceCalculator.calculateAccountBalance(acc, accTxs, asOfEpochMillis = period.endAt).currentBalancePaise
                balanceMap[acc.id.toString()] = bal
            }
            val closingBalancesJson = buildString {
                append("{")
                val entries = balanceMap.entries.toList()
                for (i in entries.indices) {
                    val entry = entries[i]
                    append("\"${entry.key}\":${entry.value}")
                    if (i < entries.size - 1) append(",")
                }
                append("}")
            }

            // Insert month_closes record
            val monthClose = MonthCloseEntity(
                budgetPeriodId = period.id,
                closedAt = now,
                plannedIncomePaise = period.incomePaise,
                actualIncomePaise = summary.actualIncomePaise,
                totalBudgetedPaise = summary.totalBudgetedPaise,
                totalSpentPaise = summary.dashboardHeroSpendPaise,
                totalSavedPaise = summary.actualSavingsPaise,
                reconciliationStatus = reconciliationStatus,
                overrideReason = overrideReason,
                closingBalancesJson = closingBalancesJson
            )
            val closeId = monthCloseDao.insert(monthClose)

            // Phase 2 (M14): Optional surplus allocation to a savings goal
            if (surplusGoalId != null && summary.actualSavingsPaise > 0L && savingsContributionDao != null) {
                savingsContributionDao.insert(
                    SavingsContributionEntity(
                        id = 0L,
                        goalId = surplusGoalId,
                        amountPaise = summary.actualSavingsPaise,
                        source = SavingsContributionSource.MONTH_CLOSE,
                        transactionId = null,
                        periodId = period.id,
                        note = "Month close surplus from period #${period.id}",
                        createdAt = now
                    )
                )
            }

            // Phase 2 (M14): Generate savings plan for next period (M+1)
            if (savingsGoalDao != null && savingsPlanEntryDao != null && savingsPlanner != null) {
                val activeGoals = savingsGoalDao.getAllGoalsSync()
                    .filter { it.status == SavingsGoalStatus.ACTIVE }
                if (activeGoals.isNotEmpty()) {
                    val goalBalances = savingsContributionDao?.getAllContributionsSync()
                        ?.groupBy { it.goalId }
                        ?.mapValues { (_, contribs) -> contribs.sumOf { it.amountPaise } }
                        ?: emptyMap()

                    val planEntries = savingsPlanner.planForPeriod(
                        goals = activeGoals,
                        goalBalances = goalBalances,
                        periodId = nextPeriod.id,
                        totalSavingsPaise = summary.actualSavingsPaise,
                        createdAt = now
                    )
                    if (planEntries.isNotEmpty()) {
                        savingsPlanEntryDao.insertAll(planEntries)
                    }
                }
            }

            closeId
        }
    }

    /**
     * Reopens the latest closed budget period atomically (Flow.md §10.1, §10.4).
     */
    suspend fun reopenMonth(periodId: Long) {
        val now = DateTimeUtils.nowEpochMillis()

        transactionRunner.run {
            val period = budgetPeriodDao.getById(periodId)
                ?: throw IllegalArgumentException("Budget period #$periodId not found")

            require(period.status == BudgetPeriodStatus.CLOSED) {
                "Can only reopen a CLOSED budget period (current status: ${period.status})"
            }

            // §10.1, §10.4: Period reopening protocol: latest closed month only
            val allPeriods = budgetPeriodDao.getAllPeriodsSync()
            val laterClosedPeriods = allPeriods.filter {
                it.startAt > period.startAt && it.status == BudgetPeriodStatus.CLOSED
            }
            if (laterClosedPeriods.isNotEmpty()) {
                throw IllegalStateException("Can only reopen the latest closed budget period (§10.1, §10.4)")
            }

            // Step 1: Set Month M's status to OPEN and closed_at = NULL first
            // (Allows deleting M's rows in advance_ledger and month_closes without tripping M1 triggers)
            budgetPeriodDao.update(
                period.copy(
                    status = BudgetPeriodStatus.OPEN,
                    closedAt = null,
                    updatedAt = now
                )
            )

            // Step 2: Delete Month M's generated rows in advance_ledger
            advanceLedgerDao.deleteForSourcePeriod(period.id)

            // Step 3: Delete Month M's record in month_closes
            monthCloseDao.deleteForPeriod(period.id)
        }
    }

    data class MonthCloseAdvancePreviewRow(
        val categoryId: Long,
        val categoryName: String,
        val carryPaise: Long,
        val sweepPaise: Long,
        val appliedDeficitPaise: Long,
        val uncoveredPoolPaise: Long
    )

    data class MonthClosePreviewResult(
        val periodId: Long,
        val rows: List<MonthCloseAdvancePreviewRow>,
        val advanceEntries: List<AdvanceLedgerEntity>
    )

    private data class AdvancePlanResult(
        val entriesToInsert: List<AdvanceLedgerEntity>,
        val previewRows: List<MonthCloseAdvancePreviewRow>
    )

    /**
     * Read-only preview of advance rollover mutations for Period M per Flow.md §17.2 V11.
     * Guaranteed pure computation: performs zero database mutations.
     */
    suspend fun preview(periodId: Long): MonthClosePreviewResult {
        val period = budgetPeriodDao.getById(periodId)
            ?: throw IllegalArgumentException("Budget period #$periodId not found")

        val now = DateTimeUtils.nowEpochMillis()
        val nextYm = YearMonth.of(period.year, period.month).plusMonths(1)
        val existingNext = budgetPeriodDao.getByYearMonth(nextYm.year, nextYm.monthValue)

        val currentAllocations = budgetAllocationDao.getAllocationsForPeriodSync(period.id)
        val currentAdjustments = budgetAdjustmentDao.getAdjustmentsForPeriodSync(period.id)
        val currentAdvances = advanceLedgerDao.getAppliedToPeriodSync(period.id)
        val currentTxList = transactionDao.getTransactionsInPeriod(period.startAt, period.endAt)
        val currentTxAllocations = transactionAllocationDao.getAllocationsInPeriod(period.startAt, period.endAt)

        val summary = BudgetCalculator.calculateBudgetPeriodSummary(
            period = period,
            allocations = currentAllocations,
            adjustments = currentAdjustments,
            transactions = currentTxList,
            allocationsForTransactions = currentTxAllocations,
            advances = currentAdvances
        )

        val nextPeriodId = existingNext?.id ?: (period.id + 1L)
        val nextAllocations = if (existingNext != null) {
            budgetAllocationDao.getAllocationsForPeriodSync(existingNext.id).associateBy { it.categoryId }
        } else {
            currentAllocations.associateBy { it.categoryId }
        }
        val nextAdjustments = if (existingNext != null) {
            budgetAdjustmentDao.getAdjustmentsForPeriodSync(existingNext.id)
        } else {
            emptyList()
        }
        val nextAdvances = if (existingNext != null) {
            advanceLedgerDao.getAppliedToPeriodSync(existingNext.id).toMutableList()
        } else {
            mutableListOf()
        }

        val unassignedPoolRows = advanceLedgerDao.getUnassignedPoolSync()
        val allCategories = categoryDao.getAllCategoriesSync()

        val plan = calculateAdvancePlan(
            period = period,
            nextPeriodId = nextPeriodId,
            nextPeriodYear = nextYm.year,
            nextPeriodMonth = nextYm.monthValue,
            summary = summary,
            nextAllocations = nextAllocations,
            nextAdjustments = nextAdjustments,
            nextAdvancesInitial = nextAdvances,
            unassignedPoolRows = unassignedPoolRows,
            allCategories = allCategories,
            now = now
        )

        return MonthClosePreviewResult(
            periodId = period.id,
            rows = plan.previewRows,
            advanceEntries = plan.entriesToInsert
        )
    }

    private fun calculateAdvancePlan(
        period: BudgetPeriodEntity,
        nextPeriodId: Long,
        nextPeriodYear: Int,
        nextPeriodMonth: Int,
        summary: BudgetPeriodSummary,
        nextAllocations: Map<Long, BudgetAllocationEntity>,
        nextAdjustments: List<BudgetAdjustmentEntity>,
        nextAdvancesInitial: List<AdvanceLedgerEntity>,
        unassignedPoolRows: List<AdvanceLedgerEntity>,
        allCategories: List<CategoryEntity>,
        now: Long
    ): AdvancePlanResult {
        val nextAdvances = nextAdvancesInitial.toMutableList()
        val advanceEntriesToInsert = mutableListOf<AdvanceLedgerEntity>()
        val previewRows = mutableListOf<MonthCloseAdvancePreviewRow>()

        val activeCategoryIds = mutableSetOf<Long>()
        for (catSummary in summary.categorySummaries) {
            if (catSummary.baseBudgetPaise > 0L || catSummary.netSpentPaise != 0L || catSummary.adjustedBudgetPaise > 0L) {
                activeCategoryIds.add(catSummary.allocation.categoryId)
            }
        }
        for (poolRow in unassignedPoolRows) {
            activeCategoryIds.add(poolRow.categoryId)
        }

        for (catId in activeCategoryIds) {
            val category = allCategories.find { it.id == catId } ?: continue
            val catSummary = summary.categorySummaries.find { it.allocation.categoryId == catId }

            var carryPaise = 0L
            var sweepPaise = 0L
            var appliedDeficitPaise = 0L
            var uncoveredPoolPaise = 0L

            // Determine Headroom in M+1 (§10.3)
            val nextAlloc = nextAllocations[catId]
            val baseMPlus1 = nextAlloc?.amountPaise ?: 0L
            val adjInMPlus1 = nextAdjustments.filter { it.toCategoryId == catId }.sumOf { it.amountPaise }
            val adjOutMPlus1 = nextAdjustments.filter { it.fromCategoryId == catId }.sumOf { it.amountPaise }
            val advPosMPlus1 = nextAdvances.filter { it.categoryId == catId && it.direction == AdvanceDirection.POSITIVE }.sumOf { it.amountPaise }
            val advNegMPlus1 = nextAdvances.filter { it.categoryId == catId && it.direction == AdvanceDirection.NEGATIVE }.sumOf { it.amountPaise }

            var headroom = baseMPlus1 + adjInMPlus1 - adjOutMPlus1 + advPosMPlus1 - advNegMPlus1
            if (headroom < 0L) headroom = 0L

            // Priority 1: UncoveredPool(C) > 0? (§10.3)
            val poolNeg = unassignedPoolRows.filter { it.categoryId == catId && it.direction == AdvanceDirection.NEGATIVE }.sumOf { it.amountPaise }
            val poolPos = unassignedPoolRows.filter { it.categoryId == catId && it.direction == AdvanceDirection.POSITIVE }.sumOf { it.amountPaise }
            val uncoveredPool = (poolNeg - poolPos).coerceAtLeast(0L)

            if (uncoveredPool > 0L && headroom > 0L) {
                val absorbed = minOf(uncoveredPool, headroom)
                if (absorbed > 0L) {
                    val offsetRow = AdvanceLedgerEntity(
                        sourcePeriodId = period.id,
                        appliedToPeriodId = null,
                        categoryId = catId,
                        amountPaise = absorbed,
                        direction = AdvanceDirection.POSITIVE,
                        reason = "Offset row reducing unassigned deficit pool (§10.3)",
                        createdAt = now
                    )
                    advanceEntriesToInsert.add(offsetRow)

                    val appliedRow = AdvanceLedgerEntity(
                        sourcePeriodId = period.id,
                        appliedToPeriodId = nextPeriodId,
                        categoryId = catId,
                        amountPaise = absorbed,
                        direction = AdvanceDirection.NEGATIVE,
                        reason = "Priority 1 historical deficit absorbed into period $nextPeriodYear-$nextPeriodMonth",
                        createdAt = now
                    )
                    advanceEntriesToInsert.add(appliedRow)
                    nextAdvances.add(appliedRow)

                    appliedDeficitPaise = Math.addExact(appliedDeficitPaise, absorbed)
                    headroom -= absorbed
                }
            }

            // Bucket handling (§10.2)
            val bucket = catSummary?.allocation?.bucketSnapshot ?: category.defaultBucket
            val remaining = catSummary?.remainingPaise ?: 0L
            val policy = catSummary?.allocation?.advancePolicySnapshot ?: category.defaultAdvancePolicy

            if (bucket == BudgetBucket.SAVINGS) {
                val adjustedBudget = catSummary?.adjustedBudgetPaise ?: 0L
                val netSpent = catSummary?.netSpentPaise ?: 0L
                if (netSpent >= adjustedBudget) {
                    // Goal Met / Surpassed -> No deficit carry-forward (§8.4)
                } else {
                    val shortfall = adjustedBudget - netSpent
                    when (policy) {
                        AdvancePolicy.CARRY_TO_ADVANCE -> {
                            val shortfallRow = AdvanceLedgerEntity(
                                sourcePeriodId = period.id,
                                appliedToPeriodId = nextPeriodId,
                                categoryId = catId,
                                amountPaise = shortfall,
                                direction = AdvanceDirection.POSITIVE,
                                reason = "Unfulfilled savings goal target carried forward (§8.4, §10.2)",
                                createdAt = now
                            )
                            advanceEntriesToInsert.add(shortfallRow)
                            nextAdvances.add(shortfallRow)
                            carryPaise = Math.addExact(carryPaise, shortfall)
                        }
                        AdvancePolicy.EXPIRE, AdvancePolicy.SWEEP_TO_SAVINGS -> {
                            // Shortfall lapses without penalty
                        }
                    }
                }
            } else {
                // NEEDS or WANTS
                if (remaining < 0L) {
                    val deficit = -remaining
                    val appliedNew = minOf(deficit, headroom)
                    val uncoveredNew = deficit - appliedNew

                    if (appliedNew > 0L) {
                        val appliedRow = AdvanceLedgerEntity(
                            sourcePeriodId = period.id,
                            appliedToPeriodId = nextPeriodId,
                            categoryId = catId,
                            amountPaise = appliedNew,
                            direction = AdvanceDirection.NEGATIVE,
                            reason = "Deficit applied to period $nextPeriodYear-$nextPeriodMonth (§10.3)",
                            createdAt = now
                        )
                        advanceEntriesToInsert.add(appliedRow)
                        nextAdvances.add(appliedRow)
                        appliedDeficitPaise = Math.addExact(appliedDeficitPaise, appliedNew)
                    }

                    if (uncoveredNew > 0L) {
                        val poolRow = AdvanceLedgerEntity(
                            sourcePeriodId = period.id,
                            appliedToPeriodId = null,
                            categoryId = catId,
                            amountPaise = uncoveredNew,
                            direction = AdvanceDirection.NEGATIVE,
                            reason = "Uncovered deficit sent to unassigned pool (§10.3)",
                            createdAt = now
                        )
                        advanceEntriesToInsert.add(poolRow)
                        uncoveredPoolPaise = Math.addExact(uncoveredPoolPaise, uncoveredNew)
                    }
                } else if (remaining > 0L) {
                    when (policy) {
                        AdvancePolicy.CARRY_TO_ADVANCE -> {
                            val surplusRow = AdvanceLedgerEntity(
                                sourcePeriodId = period.id,
                                appliedToPeriodId = nextPeriodId,
                                categoryId = catId,
                                amountPaise = remaining,
                                direction = AdvanceDirection.POSITIVE,
                                reason = "Surplus budget carried forward",
                                createdAt = now
                            )
                            advanceEntriesToInsert.add(surplusRow)
                            nextAdvances.add(surplusRow)
                            carryPaise = Math.addExact(carryPaise, remaining)
                        }
                        AdvancePolicy.SWEEP_TO_SAVINGS -> {
                            val targetCatId = category.sweepTargetCategoryId ?: catId
                            val sweepRow = AdvanceLedgerEntity(
                                sourcePeriodId = period.id,
                                appliedToPeriodId = nextPeriodId,
                                categoryId = targetCatId,
                                amountPaise = remaining,
                                direction = AdvanceDirection.POSITIVE,
                                reason = "Surplus swept to savings category #$targetCatId",
                                createdAt = now
                            )
                            advanceEntriesToInsert.add(sweepRow)
                            nextAdvances.add(sweepRow)
                            sweepPaise = Math.addExact(sweepPaise, remaining)
                        }
                        AdvancePolicy.EXPIRE -> {
                            // Surplus lapses
                        }
                    }
                }
            }

            if (carryPaise > 0L || sweepPaise > 0L || appliedDeficitPaise > 0L || uncoveredPoolPaise > 0L) {
                previewRows.add(
                    MonthCloseAdvancePreviewRow(
                        categoryId = catId,
                        categoryName = category.name,
                        carryPaise = carryPaise,
                        sweepPaise = sweepPaise,
                        appliedDeficitPaise = appliedDeficitPaise,
                        uncoveredPoolPaise = uncoveredPoolPaise
                    )
                )
            }
        }

        return AdvancePlanResult(
            entriesToInsert = advanceEntriesToInsert,
            previewRows = previewRows
        )
    }
}
