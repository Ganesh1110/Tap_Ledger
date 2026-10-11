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
    private val dataIntegrityChecker: DataIntegrityChecker
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
        allowUnreviewedOverride: Boolean = false
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

            // Step 2: Loop Active Categories across Open Downstream Periods
            val unassignedPoolRows = advanceLedgerDao.getUnassignedPoolSync()
            val nextAllocations = budgetAllocationDao.getAllocationsForPeriodSync(nextPeriod.id).associateBy { it.categoryId }
            val nextAdjustments = budgetAdjustmentDao.getAdjustmentsForPeriodSync(nextPeriod.id)
            val nextAdvances = advanceLedgerDao.getAppliedToPeriodSync(nextPeriod.id).toMutableList()

            val advanceEntriesToInsert = mutableListOf<AdvanceLedgerEntity>()

            // Find all active categories
            val allCategories = categoryDao.getAllCategoriesSync()
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
                        // Offset Row in unassigned pool (applied_to = NULL, direction = POSITIVE)
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

                        // Application Row in M+1 (applied_to = M+1, direction = NEGATIVE)
                        val appliedRow = AdvanceLedgerEntity(
                            sourcePeriodId = period.id,
                            appliedToPeriodId = nextPeriod.id,
                            categoryId = catId,
                            amountPaise = absorbed,
                            direction = AdvanceDirection.NEGATIVE,
                            reason = "Priority 1 historical deficit absorbed into period ${nextPeriod.year}-${nextPeriod.month}",
                            createdAt = now
                        )
                        advanceEntriesToInsert.add(appliedRow)
                        nextAdvances.add(appliedRow)

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
                        // Under-saved
                        val shortfall = adjustedBudget - netSpent
                        when (policy) {
                            AdvancePolicy.CARRY_TO_ADVANCE -> {
                                val shortfallRow = AdvanceLedgerEntity(
                                    sourcePeriodId = period.id,
                                    appliedToPeriodId = nextPeriod.id,
                                    categoryId = catId,
                                    amountPaise = shortfall,
                                    direction = AdvanceDirection.POSITIVE,
                                    reason = "Unfulfilled savings goal target carried forward (§8.4, §10.2)",
                                    createdAt = now
                                )
                                advanceEntriesToInsert.add(shortfallRow)
                                nextAdvances.add(shortfallRow)
                            }
                            AdvancePolicy.EXPIRE, AdvancePolicy.SWEEP_TO_SAVINGS -> {
                                // Shortfall lapses without penalty
                            }
                        }
                    }
                } else {
                    // NEEDS or WANTS
                    if (remaining < 0L) {
                        // Overspent -> Priority 2: Deficit Handling (§10.3)
                        val deficit = -remaining
                        val appliedNew = minOf(deficit, headroom)
                        val uncoveredNew = deficit - appliedNew

                        if (appliedNew > 0L) {
                            val appliedRow = AdvanceLedgerEntity(
                                sourcePeriodId = period.id,
                                appliedToPeriodId = nextPeriod.id,
                                categoryId = catId,
                                amountPaise = appliedNew,
                                direction = AdvanceDirection.NEGATIVE,
                                reason = "Deficit applied to period ${nextPeriod.year}-${nextPeriod.month} (§10.3)",
                                createdAt = now
                            )
                            advanceEntriesToInsert.add(appliedRow)
                            nextAdvances.add(appliedRow)
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
                        }
                    } else if (remaining > 0L) {
                        when (policy) {
                            AdvancePolicy.CARRY_TO_ADVANCE -> {
                                val surplusRow = AdvanceLedgerEntity(
                                    sourcePeriodId = period.id,
                                    appliedToPeriodId = nextPeriod.id,
                                    categoryId = catId,
                                    amountPaise = remaining,
                                    direction = AdvanceDirection.POSITIVE,
                                    reason = "Surplus budget carried forward",
                                    createdAt = now
                                )
                                advanceEntriesToInsert.add(surplusRow)
                                nextAdvances.add(surplusRow)
                            }
                            AdvancePolicy.SWEEP_TO_SAVINGS -> {
                                val targetCatId = category.sweepTargetCategoryId ?: catId
                                val sweepRow = AdvanceLedgerEntity(
                                    sourcePeriodId = period.id,
                                    appliedToPeriodId = nextPeriod.id,
                                    categoryId = targetCatId,
                                    amountPaise = remaining,
                                    direction = AdvanceDirection.POSITIVE,
                                    reason = "Surplus swept to savings category #$targetCatId",
                                    createdAt = now
                                )
                                advanceEntriesToInsert.add(sweepRow)
                                nextAdvances.add(sweepRow)
                            }
                            AdvancePolicy.EXPIRE -> {
                                // Surplus lapses
                            }
                        }
                    }
                }
            }

            if (advanceEntriesToInsert.isNotEmpty()) {
                advanceLedgerDao.insertAll(advanceEntriesToInsert)
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
}
