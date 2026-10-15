package com.tapledger.ui.charts.builder

import com.google.common.truth.Truth.assertThat
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
import com.tapledger.core.database.entity.AccountEntity
import com.tapledger.core.database.entity.AdvanceLedgerEntity
import com.tapledger.core.database.entity.BudgetAllocationEntity
import com.tapledger.core.database.entity.BudgetPeriodEntity
import com.tapledger.core.database.entity.CategoryEntity
import com.tapledger.core.database.entity.MonthCloseEntity
import com.tapledger.core.database.entity.TransactionAllocationEntity
import com.tapledger.core.database.entity.TransactionEntity
import com.tapledger.core.model.AccountType
import com.tapledger.core.model.AdvanceDirection
import com.tapledger.core.model.AdvancePolicy
import com.tapledger.core.model.BudgetBucket
import com.tapledger.core.model.BudgetPeriodStatus
import com.tapledger.core.model.MonthCloseReconciliationStatus
import com.tapledger.core.model.TransactionDirection
import com.tapledger.core.model.TransactionKind
import com.tapledger.core.model.TransactionSource
import com.tapledger.core.model.TransactionStatus
import com.tapledger.data.repository.BudgetRepository
import com.tapledger.data.statement.DiscrepancyPair
import com.tapledger.data.statement.ReconciliationProof
import com.tapledger.data.statement.TimingDifferencePair
import com.tapledger.domain.calculator.BudgetCalculator
import com.tapledger.domain.model.BudgetPeriodSummary
import com.tapledger.domain.monthclose.MonthCloseEngine
import com.tapledger.domain.validator.DataIntegrityChecker
import com.tapledger.ui.charts.model.ChartState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import org.junit.Test

/**
 * Formal verification of Invariants I1 through I10, I12, and I13 per Flow.md §17.4.
 */
class ChartInvariantsTest {

    private val startAt = 1_700_000_000_000L
    private val endAt = startAt + 30L * 86_400_000L

    private val samplePeriod = BudgetPeriodEntity(
        id = 1L,
        year = 2026,
        month = 10,
        startAt = startAt,
        endAt = endAt,
        incomePaise = 100_000_00L,
        status = BudgetPeriodStatus.OPEN,
        isReviewed = true,
        createdAt = startAt,
        updatedAt = startAt
    )

    private val catNeeds = CategoryEntity(id = 10L, name = "Groceries", defaultBucket = BudgetBucket.NEEDS, defaultAdvancePolicy = AdvancePolicy.EXPIRE, defaultSortOrder = 1, createdAt = startAt, updatedAt = startAt)
    private val catWants = CategoryEntity(id = 20L, name = "Dining", defaultBucket = BudgetBucket.WANTS, defaultAdvancePolicy = AdvancePolicy.EXPIRE, defaultSortOrder = 2, createdAt = startAt, updatedAt = startAt)
    private val catSavings = CategoryEntity(id = 30L, name = "Emergency Fund", defaultBucket = BudgetBucket.SAVINGS, defaultAdvancePolicy = AdvancePolicy.CARRY_TO_ADVANCE, defaultSortOrder = 3, createdAt = startAt, updatedAt = startAt)

    private val allocNeeds = BudgetAllocationEntity(id = 1L, budgetPeriodId = 1L, categoryId = 10L, amountPaise = 50_000_00L, bucketSnapshot = BudgetBucket.NEEDS, advancePolicySnapshot = AdvancePolicy.EXPIRE, categoryNameSnapshot = "Groceries", sortOrderSnapshot = 1, createdAt = startAt, updatedAt = startAt)
    private val allocWants = BudgetAllocationEntity(id = 2L, budgetPeriodId = 1L, categoryId = 20L, amountPaise = 30_000_00L, bucketSnapshot = BudgetBucket.WANTS, advancePolicySnapshot = AdvancePolicy.EXPIRE, categoryNameSnapshot = "Dining", sortOrderSnapshot = 2, createdAt = startAt, updatedAt = startAt)
    private val allocSavings = BudgetAllocationEntity(id = 3L, budgetPeriodId = 1L, categoryId = 30L, amountPaise = 20_000_00L, bucketSnapshot = BudgetBucket.SAVINGS, advancePolicySnapshot = AdvancePolicy.CARRY_TO_ADVANCE, categoryNameSnapshot = "Emergency Fund", sortOrderSnapshot = 3, createdAt = startAt, updatedAt = startAt)

    // Helper to compute summary
    private fun computeSummary(
        txs: List<TransactionEntity>,
        allocs: List<TransactionAllocationEntity>
    ): BudgetPeriodSummary {
        return BudgetCalculator.calculateBudgetPeriodSummary(
            period = samplePeriod,
            allocations = listOf(allocNeeds, allocWants, allocSavings),
            adjustments = emptyList(),
            transactions = txs,
            allocationsForTransactions = allocs
        )
    }

    // --- Invariant I1: Hero Spend == Needs + Wants Spent == Last Burn-Up Point ---
    @Test
    fun testI1_HeroSpendEqualsNeedsWantsSpentEqualsLastBurnUpPoint() {
        val tx1 = TransactionEntity(id = 100L, accountId = 1L, kind = TransactionKind.EXPENSE, direction = TransactionDirection.OUT, amountPaise = 15_000_00L, status = TransactionStatus.CATEGORIZED, source = TransactionSource.MANUAL, occurredAt = startAt + 1000L, createdAt = startAt, updatedAt = startAt)
        val ta1 = TransactionAllocationEntity(id = 1L, transactionId = 100L, categoryId = 10L, amountPaise = 15_000_00L)

        val tx2 = TransactionEntity(id = 200L, accountId = 1L, kind = TransactionKind.EXPENSE, direction = TransactionDirection.OUT, amountPaise = 8_000_00L, status = TransactionStatus.CATEGORIZED, source = TransactionSource.MANUAL, occurredAt = startAt + 86_400_000L + 500L, createdAt = startAt, updatedAt = startAt)
        val ta2 = TransactionAllocationEntity(id = 2L, transactionId = 200L, categoryId = 20L, amountPaise = 8_000_00L)

        val txSavings = TransactionEntity(id = 300L, accountId = 1L, kind = TransactionKind.TRANSFER, direction = TransactionDirection.OUT, amountPaise = 5_000_00L, status = TransactionStatus.CATEGORIZED, source = TransactionSource.MANUAL, occurredAt = startAt + 2L * 86_400_000L, createdAt = startAt, updatedAt = startAt)
        val taSavings = TransactionAllocationEntity(id = 3L, transactionId = 300L, categoryId = 30L, amountPaise = 5_000_00L)

        val summary = computeSummary(listOf(tx1, tx2, txSavings), listOf(ta1, ta2, taSavings))

        // V1 Hero Ring
        val heroRingState = DashboardChartBuilder.buildHeroRing(summary, isPeriodClosed = true, nowIST = endAt)
        assertThat(heroRingState).isInstanceOf(ChartState.Data::class.java)
        val heroRing = (heroRingState as ChartState.Data).data

        // V3 Bucket Rings
        val bucketRingsState = DashboardChartBuilder.buildBucketRings(summary)
        assertThat(bucketRingsState).isInstanceOf(ChartState.Data::class.java)
        val bucketRings = (bucketRingsState as ChartState.Data).data

        // V2 Burn-Up Chart
        val burnUpState = DashboardChartBuilder.buildBurnUpChart(
            summary = summary,
            period = samplePeriod.copy(status = BudgetPeriodStatus.CLOSED),
            transactions = listOf(tx1, tx2, txSavings),
            allocations = listOf(ta1, ta2, taSavings),
            nowIST = endAt
        )
        assertThat(burnUpState).isInstanceOf(ChartState.Data::class.java)
        val burnUp = (burnUpState as ChartState.Data).data

        // Assert I1: Hero Spend == Needs + Wants == Last Burn-up Point
        val sumNeedsWants = bucketRings.needs.spentPaise + bucketRings.wants.spentPaise
        assertThat(heroRing.spentPaise).isEqualTo(23_000_00L)
        assertThat(sumNeedsWants).isEqualTo(heroRing.spentPaise)
        assertThat(burnUp.lastCumulativePaise).isEqualTo(heroRing.spentPaise)
    }

    // --- Invariant I2: Burn-up Series Monotonically Non-Decreasing Over Positive Spend ---
    @Test
    fun testI2_BurnUpSeriesIsMonotonicallyNonDecreasingOverPositiveSpend() {
        val txs = listOf(
            TransactionEntity(id = 1L, accountId = 1L, kind = TransactionKind.EXPENSE, direction = TransactionDirection.OUT, amountPaise = 1_000_00L, status = TransactionStatus.CATEGORIZED, source = TransactionSource.MANUAL, occurredAt = startAt + 1000L, createdAt = startAt, updatedAt = startAt),
            TransactionEntity(id = 2L, accountId = 1L, kind = TransactionKind.EXPENSE, direction = TransactionDirection.OUT, amountPaise = 2_000_00L, status = TransactionStatus.CATEGORIZED, source = TransactionSource.MANUAL, occurredAt = startAt + 86_400_000L, createdAt = startAt, updatedAt = startAt),
            TransactionEntity(id = 3L, accountId = 1L, kind = TransactionKind.EXPENSE, direction = TransactionDirection.OUT, amountPaise = 3_000_00L, status = TransactionStatus.CATEGORIZED, source = TransactionSource.MANUAL, occurredAt = startAt + 2L * 86_400_000L, createdAt = startAt, updatedAt = startAt)
        )
        val tas = listOf(
            TransactionAllocationEntity(id = 1L, transactionId = 1L, categoryId = 10L, amountPaise = 1_000_00L),
            TransactionAllocationEntity(id = 2L, transactionId = 2L, categoryId = 10L, amountPaise = 2_000_00L),
            TransactionAllocationEntity(id = 3L, transactionId = 3L, categoryId = 10L, amountPaise = 3_000_00L)
        )
        val summary = computeSummary(txs, tas)

        val burnUpState = DashboardChartBuilder.buildBurnUpChart(
            summary = summary,
            period = samplePeriod.copy(status = BudgetPeriodStatus.CLOSED),
            transactions = txs,
            allocations = tas,
            nowIST = endAt
        )
        val burnUp = (burnUpState as ChartState.Data).data

        // Monotonic check
        for (i in 1 until burnUp.cumulativePoints.size) {
            val prev = burnUp.cumulativePoints[i - 1].netSpentPaise
            val curr = burnUp.cumulativePoints[i].netSpentPaise
            assertThat(curr).isAtLeast(prev)
        }
    }

    // --- Invariant I3: Six-Period Trend Clustered Bars Match Month Closes for Closed Periods ---
    @Test
    fun testI3_SixPeriodTrendMatchesMonthClosesForClosedPeriods() {
        val close1 = MonthCloseEntity(
            id = 1L,
            budgetPeriodId = 1L,
            closedAt = startAt + 100_000L,
            plannedIncomePaise = 50_000_00L,
            actualIncomePaise = 52_000_00L,
            totalBudgetedPaise = 50_000_00L,
            totalSpentPaise = 42_000_00L,
            totalSavedPaise = 10_000_00L,
            reconciliationStatus = MonthCloseReconciliationStatus.RECONCILED,
            closingBalancesJson = "{}"
        )

        val trendState = DashboardChartBuilder.buildSixPeriodTrend(
            closedCloses = listOf(close1),
            allPeriods = listOf(samplePeriod),
            currentSummary = null,
            currentPeriodId = null
        )
        val trend = (trendState as ChartState.Data).data

        assertThat(trend.periods).hasSize(1)
        val bar = trend.periods[0]
        assertThat(bar.incomePaise).isEqualTo(close1.actualIncomePaise)
        assertThat(bar.spentPaise).isEqualTo(close1.totalSpentPaise)
        assertThat(bar.savedPaise).isEqualTo(close1.totalSavedPaise)
        assertThat(bar.isInProgress).isFalse()
    }

    // --- Invariant I4: V6 Category Donut Slices Sum to Exactly 10,000 Bp & Exclude Refund-Heavy ---
    @Test
    fun testI4_CategoryDonutSlicesSumToExactly10000BpAndExcludesRefundHeavy() {
        val txPos1 = TransactionEntity(id = 1L, accountId = 1L, kind = TransactionKind.EXPENSE, direction = TransactionDirection.OUT, amountPaise = 1_000_00L, status = TransactionStatus.CATEGORIZED, source = TransactionSource.MANUAL, occurredAt = startAt, createdAt = startAt, updatedAt = startAt)
        val taPos1 = TransactionAllocationEntity(id = 1L, transactionId = 1L, categoryId = 10L, amountPaise = 1_000_00L)

        val txPos2 = TransactionEntity(id = 2L, accountId = 1L, kind = TransactionKind.EXPENSE, direction = TransactionDirection.OUT, amountPaise = 2_000_00L, status = TransactionStatus.CATEGORIZED, source = TransactionSource.MANUAL, occurredAt = startAt, createdAt = startAt, updatedAt = startAt)
        val taPos2 = TransactionAllocationEntity(id = 2L, transactionId = 2L, categoryId = 20L, amountPaise = 2_000_00L)

        val txRefund = TransactionEntity(id = 3L, accountId = 1L, kind = TransactionKind.REFUND, direction = TransactionDirection.IN, amountPaise = 500_00L, status = TransactionStatus.CATEGORIZED, source = TransactionSource.MANUAL, occurredAt = startAt, createdAt = startAt, updatedAt = startAt)
        val taRefund = TransactionAllocationEntity(id = 3L, transactionId = 3L, categoryId = 30L, amountPaise = 500_00L)

        val allocWantsRefund = allocSavings.copy(categoryId = 30L, bucketSnapshot = BudgetBucket.WANTS)
        val summary = BudgetCalculator.calculateBudgetPeriodSummary(
            period = samplePeriod,
            allocations = listOf(allocNeeds, allocWants, allocWantsRefund),
            adjustments = emptyList(),
            transactions = listOf(txPos1, txPos2, txRefund),
            allocationsForTransactions = listOf(taPos1, taPos2, taRefund)
        )

        val categoryNames = mapOf(10L to "Groceries", 20L to "Dining", 30L to "Gadgets")
        val donutState = BudgetChartBuilder.buildCategoryDonut(summary, categoryNames)
        val donut = (donutState as ChartState.Data).data

        // Slices sum to exactly 10,000 bp
        val sumBp = donut.slices.sumOf { it.percentageBp }
        assertThat(sumBp).isEqualTo(10_000)

        // Refund-heavy category excluded from donut slices and present in refundCategories
        assertThat(donut.slices.any { it.categoryId == 30L }).isFalse()
        assertThat(donut.refundCategories).hasSize(1)
        assertThat(donut.refundCategories[0].categoryId).isEqualTo(30L)
        assertThat(donut.refundCategories[0].netRefundPaise).isEqualTo(500_00L)
    }

    // --- Invariant I5: V9 Net Worth Final Point == Live NetWorth; Closed Period Points == Json ---
    @Test
    fun testI5_NetWorthFinalPointMatchesLiveNetWorthAndClosedPointsMatchJson() {
        val bankAccount = AccountEntity(id = 1L, name = "HDFC Bank", type = AccountType.BANK, identifierLast4 = "1234", openingBalancePaise = 100_000_00L, openingBalanceDate = startAt, isActive = true, createdAt = startAt, updatedAt = startAt)
        val ccAccount = AccountEntity(id = 2L, name = "ICICI Card", type = AccountType.CREDIT_CARD, identifierLast4 = "5678", openingBalancePaise = -20_000_00L, openingBalanceDate = startAt, creditLimitPaise = 100_000_00L, isActive = true, createdAt = startAt, updatedAt = startAt)

        val close = MonthCloseEntity(
            id = 1L,
            budgetPeriodId = 1L,
            closedAt = endAt,
            plannedIncomePaise = 100_000_00L,
            actualIncomePaise = 100_000_00L,
            totalBudgetedPaise = 100_000_00L,
            totalSpentPaise = 0L,
            totalSavedPaise = 0L,
            reconciliationStatus = MonthCloseReconciliationStatus.RECONCILED,
            closingBalancesJson = "{\"1\":10000000,\"2\":-2000000}"
        )

        val nwState = AccountsChartBuilder.buildNetWorthChart(
            range = NetWorthRange.ALL,
            accounts = listOf(bankAccount, ccAccount),
            allTransactions = emptyList(),
            monthCloses = listOf(close),
            periods = listOf(samplePeriod),
            asOfNow = endAt + 10_000L
        )
        val nw = (nwState as ChartState.Data).data

        // Live net worth = 100,000 - 20,000 = 80,000
        assertThat(nw.currentNetWorthPaise).isEqualTo(80_000_00L)
        assertThat(nw.points.last().netWorthPaise).isEqualTo(nw.currentNetWorthPaise)

        // Closed period point from json
        val closedPoint = nw.points.first { it.label == "${samplePeriod.month}/${samplePeriod.year % 100}" }
        assertThat(closedPoint.netWorthPaise).isEqualTo(80_000_00L)
        assertThat(closedPoint.assetsPaise).isEqualTo(100_000_00L)
        assertThat(closedPoint.liabilitiesPaise).isEqualTo(20_000_00L)
    }

    // --- Invariant I6: V10 Account Composition Sum(Assets) - Sum(Liabilities) == NetWorth ---
    @Test
    fun testI6_AccountCompositionAssetsMinusLiabilitiesEqualsNetWorth() {
        val bank1 = AccountEntity(id = 1L, name = "Bank 1", type = AccountType.BANK, identifierLast4 = "1111", openingBalancePaise = 50_000_00L, openingBalanceDate = startAt, isActive = true, createdAt = startAt, updatedAt = startAt)
        val bank2 = AccountEntity(id = 2L, name = "Bank 2", type = AccountType.BANK, identifierLast4 = "2222", openingBalancePaise = 30_000_00L, openingBalanceDate = startAt, isActive = true, createdAt = startAt, updatedAt = startAt)
        val cc = AccountEntity(id = 3L, name = "Credit Card", type = AccountType.CREDIT_CARD, identifierLast4 = "3333", openingBalancePaise = -15_000_00L, openingBalanceDate = startAt, creditLimitPaise = 50_000_00L, isActive = true, createdAt = startAt, updatedAt = startAt)

        val compState = AccountsChartBuilder.buildAccountComposition(
            accounts = listOf(bank1, bank2, cc),
            allTransactions = emptyList(),
            asOfNow = startAt
        )
        val comp = (compState as ChartState.Data).data

        assertThat(comp.totalAssetsPaise).isEqualTo(80_000_00L)
        assertThat(comp.totalLiabilitiesPaise).isEqualTo(15_000_00L)
        assertThat(comp.netWorthPaise).isEqualTo(65_000_00L)
        assertThat(comp.totalAssetsPaise - comp.totalLiabilitiesPaise).isEqualTo(comp.netWorthPaise)
    }

    // --- Invariant I7: V11 MonthCloseEngine.preview(periodId) rows == real month close advance ledger mutations ---
    @Test
    fun testI7_MonthCloseEnginePreviewRowsMatchRealCloseAdvanceLedgerMutations() = runBlocking {
        val fakeTxRunner = object : DatabaseTransactionRunner {
            override suspend fun <R> run(block: suspend () -> R): R = block()
        }

        val periodStore = mutableMapOf<Long, BudgetPeriodEntity>()
        val allocStore = mutableMapOf<Long, BudgetAllocationEntity>()
        val advanceStore = mutableListOf<AdvanceLedgerEntity>()
        val monthCloseStore = mutableListOf<MonthCloseEntity>()

        periodStore[samplePeriod.id] = samplePeriod
        val nextPeriod = samplePeriod.copy(id = 2L, month = 11, startAt = endAt, endAt = endAt + 30L * 86_400_000L)
        periodStore[nextPeriod.id] = nextPeriod

        val fakePeriodDao = object : BudgetPeriodDao {
            override suspend fun insert(budgetPeriod: BudgetPeriodEntity): Long { periodStore[budgetPeriod.id] = budgetPeriod; return budgetPeriod.id }
            override suspend fun update(budgetPeriod: BudgetPeriodEntity) { periodStore[budgetPeriod.id] = budgetPeriod }
            override suspend fun getById(id: Long): BudgetPeriodEntity? = periodStore[id]
            override suspend fun getByYearMonth(year: Int, month: Int): BudgetPeriodEntity? = periodStore.values.firstOrNull { it.year == year && it.month == month }
            override fun observeByYearMonth(year: Int, month: Int): Flow<BudgetPeriodEntity?> = emptyFlow()
            override fun getAllPeriods(): Flow<List<BudgetPeriodEntity>> = emptyFlow()
            override suspend fun getAllPeriodsSync(): List<BudgetPeriodEntity> = periodStore.values.toList()
            override suspend fun getPeriodForTimestamp(timestamp: Long): BudgetPeriodEntity? = periodStore.values.firstOrNull { timestamp >= it.startAt && timestamp < it.endAt }
            override suspend fun getEarliestPeriod(): BudgetPeriodEntity? = periodStore.values.minByOrNull { it.startAt }
            override suspend fun getLatestPeriod(): BudgetPeriodEntity? = periodStore.values.maxByOrNull { it.startAt }
            override suspend fun getOpenPeriodForTimestamp(timestamp: Long): BudgetPeriodEntity? = null
            override suspend fun getFirstOpenPeriod(): BudgetPeriodEntity? = null
            override suspend fun getClosedPeriodEndingAfter(startOfDayTimestamp: Long): BudgetPeriodEntity? = null
            override suspend fun getClosedPeriodForTimestamp(timestamp: Long): BudgetPeriodEntity? = null
        }

        val fakeAllocDao = object : BudgetAllocationDao() {
            var nextId = 100L
            override suspend fun getAdjustmentsForPeriod(periodId: Long): List<com.tapledger.core.database.entity.BudgetAdjustmentEntity> = emptyList()
            override suspend fun getAdvancesForPeriod(periodId: Long): List<AdvanceLedgerEntity> = emptyList()
            override suspend fun insertInternal(allocation: BudgetAllocationEntity): Long {
                val id = if (allocation.id == 0L) nextId++ else allocation.id
                allocStore[id] = allocation.copy(id = id)
                return id
            }
            override suspend fun insertAllInternal(allocations: List<BudgetAllocationEntity>): List<Long> =
                allocations.map { insertInternal(it) }
            override suspend fun updateInternal(allocation: BudgetAllocationEntity) { allocStore[allocation.id] = allocation }
            override fun observeAllocationsForPeriod(periodId: Long): Flow<List<BudgetAllocationEntity>> = emptyFlow()
            override suspend fun getAllocationsForPeriodSync(periodId: Long): List<BudgetAllocationEntity> =
                allocStore.values.filter { it.budgetPeriodId == periodId }
            override suspend fun getAllAllocationsSync(): List<BudgetAllocationEntity> = allocStore.values.toList()
            override suspend fun getAllocation(periodId: Long, categoryId: Long): BudgetAllocationEntity? =
                allocStore.values.firstOrNull { it.budgetPeriodId == periodId && it.categoryId == categoryId }
            override suspend fun deleteAllocation(periodId: Long, categoryId: Long) {
                allocStore.entries.removeIf { it.value.budgetPeriodId == periodId && it.value.categoryId == categoryId }
            }
        }

        allocStore[1L] = allocNeeds
        allocStore[2L] = allocWants
        allocStore[3L] = allocSavings
        allocStore[4L] = allocNeeds.copy(id = 4L, budgetPeriodId = 2L)
        allocStore[5L] = allocWants.copy(id = 5L, budgetPeriodId = 2L)
        allocStore[6L] = allocSavings.copy(id = 6L, budgetPeriodId = 2L)

        val fakeAdvanceDao = object : AdvanceLedgerDao {
            var nextId = 1L
            override suspend fun insert(entry: AdvanceLedgerEntity): Long {
                val id = if (entry.id == 0L) nextId++ else entry.id
                advanceStore.add(entry.copy(id = id))
                return id
            }
            override suspend fun insertAll(entries: List<AdvanceLedgerEntity>): List<Long> = entries.map { insert(it) }
            override fun observeAppliedToPeriod(periodId: Long): Flow<List<AdvanceLedgerEntity>> = emptyFlow()
            override suspend fun getAppliedToPeriodSync(periodId: Long): List<AdvanceLedgerEntity> =
                advanceStore.filter { it.appliedToPeriodId == periodId }
            override suspend fun getSourcePeriodSync(periodId: Long): List<AdvanceLedgerEntity> =
                advanceStore.filter { it.sourcePeriodId == periodId }
            override suspend fun getUnassignedDeficitPoolSync(): List<AdvanceLedgerEntity> =
                advanceStore.filter { it.appliedToPeriodId == null && it.direction == AdvanceDirection.NEGATIVE }
            override suspend fun getUnassignedPoolSync(): List<AdvanceLedgerEntity> =
                advanceStore.filter { it.appliedToPeriodId == null }
            override suspend fun getForCategory(categoryId: Long): List<AdvanceLedgerEntity> =
                advanceStore.filter { it.categoryId == categoryId }
            override suspend fun deleteForSourcePeriod(periodId: Long) { advanceStore.removeAll { it.sourcePeriodId == periodId } }
            override suspend fun getAllAdvancesSync(): List<AdvanceLedgerEntity> = advanceStore
        }

        val fakeMonthCloseDao = object : MonthCloseDao {
            override suspend fun insert(monthClose: MonthCloseEntity): Long { monthCloseStore.add(monthClose); return 1L }
            override suspend fun getByPeriodId(periodId: Long): MonthCloseEntity? = monthCloseStore.firstOrNull { it.budgetPeriodId == periodId }
            override fun observeByPeriodId(periodId: Long): Flow<MonthCloseEntity?> = emptyFlow()
            override suspend fun deleteForPeriod(periodId: Long) { monthCloseStore.removeAll { it.budgetPeriodId == periodId } }
            override suspend fun getAllMonthClosesSync(): List<MonthCloseEntity> = monthCloseStore
            override fun getAllMonthClosesFlow(): Flow<List<MonthCloseEntity>> = emptyFlow()
        }

        val fakeAccountDao = object : AccountDao {
            override suspend fun insert(account: AccountEntity): Long = 1L
            override suspend fun insertAll(accounts: List<AccountEntity>): List<Long> = emptyList()
            override suspend fun update(account: AccountEntity) {}
            override suspend fun getById(id: Long): AccountEntity? = null
            override fun getAllActiveAccounts(): Flow<List<AccountEntity>> = emptyFlow()
            override suspend fun getAllAccountsSync(): List<AccountEntity> = emptyList()
            override suspend fun findByLast4(last4: String): AccountEntity? = null
            override suspend fun findAllActiveByLast4(last4: String): List<AccountEntity> = emptyList()
            override suspend fun getActiveBankAccounts(): List<AccountEntity> = emptyList()
        }

        val fakeCategoryDao = object : CategoryDao {
            val cats = listOf(catNeeds, catWants, catSavings)
            override suspend fun insert(category: CategoryEntity): Long = 1L
            override suspend fun insertAll(categories: List<CategoryEntity>): List<Long> = emptyList()
            override suspend fun update(category: CategoryEntity) {}
            override suspend fun getById(id: Long): CategoryEntity? = cats.firstOrNull { it.id == id }
            override fun getAllActiveCategories(): Flow<List<CategoryEntity>> = emptyFlow()
            override suspend fun getAllCategoriesSync(): List<CategoryEntity> = cats
            override suspend fun getByName(name: String): CategoryEntity? = cats.firstOrNull { it.name == name }
            override suspend fun getMaxSortOrder(): Int? = cats.maxOfOrNull { it.defaultSortOrder }
        }

        val fakeAdjustmentDao = object : BudgetAdjustmentDao {
            override suspend fun insert(adjustment: com.tapledger.core.database.entity.BudgetAdjustmentEntity): Long = 1L
            override fun observeAdjustmentsForPeriod(periodId: Long) = emptyFlow<List<com.tapledger.core.database.entity.BudgetAdjustmentEntity>>()
            override suspend fun getAdjustmentsForPeriodSync(periodId: Long) = emptyList<com.tapledger.core.database.entity.BudgetAdjustmentEntity>()
            override suspend fun getAllAdjustmentsSync() = emptyList<com.tapledger.core.database.entity.BudgetAdjustmentEntity>()
        }

        val fakeTxDao = object : TransactionDao {
            override suspend fun insert(transaction: TransactionEntity): Long = 1L
            override suspend fun insertAll(transactions: List<TransactionEntity>): List<Long> = emptyList()
            override suspend fun update(transaction: TransactionEntity) {}
            override suspend fun getById(id: Long): TransactionEntity? = null
            override fun observeById(id: Long): Flow<TransactionEntity?> = emptyFlow()
            override fun observeAllTransactions(): Flow<List<TransactionEntity>> = emptyFlow()
            override suspend fun getAllTransactionsSync(): List<TransactionEntity> = emptyList()
            override fun observePendingTransactions(): Flow<List<TransactionEntity>> = emptyFlow()
            override fun observePendingCount(): Flow<Int> = emptyFlow()
            override suspend fun getPendingCountSync(): Int = 0
            override suspend fun getBalanceTransactionsForAccount(accountId: Long, sinceEpochMillis: Long): List<TransactionEntity> = emptyList()
            override fun observeBalanceTransactionsForAccount(accountId: Long, sinceEpochMillis: Long): Flow<List<TransactionEntity>> = emptyFlow()
            override suspend fun getTransactionsInPeriod(startEpochMillis: Long, endEpochMillis: Long): List<TransactionEntity> = emptyList()
            override fun observeTransactionsInPeriod(startEpochMillis: Long, endEpochMillis: Long): Flow<List<TransactionEntity>> = emptyFlow()
            override suspend fun findBySourceFingerprint(fingerprint: String): TransactionEntity? = null
            override suspend fun findByUpiReference(upiRef: String): TransactionEntity? = null
            override suspend fun findByExactKey(accountId: Long, upiRef: String, direction: TransactionDirection, amountPaise: Long): TransactionEntity? = null
            override suspend fun findByExactKeyUnassigned(upiRef: String, direction: TransactionDirection, amountPaise: Long): TransactionEntity? = null
            override suspend fun findByUpiDirectionAmountAcrossAllAccounts(upiRef: String, direction: TransactionDirection, amountPaise: Long): TransactionEntity? = null
            override suspend fun findFuzzyCandidates(accountId: Long?, direction: TransactionDirection, amountPaise: Long, startWindow: Long, endWindow: Long): List<TransactionEntity> = emptyList()
            override suspend fun findTransferPairCandidates(excludeAccountId: Long, oppositeDirection: TransactionDirection, amountPaise: Long, startWindow: Long, endWindow: Long): List<TransactionEntity> = emptyList()
            override suspend fun getTransferLegs(transferGroupId: String): List<TransactionEntity> = emptyList()
            override suspend fun getRefundsForTransaction(originalTransactionId: Long): List<TransactionEntity> = emptyList()
            override suspend fun getTransactionsForAccountSync(accountId: Long): List<TransactionEntity> = emptyList()
        }

        val fakeTxAllocDao = object : TransactionAllocationDao {
            override suspend fun insert(allocation: TransactionAllocationEntity): Long = 1L
            override suspend fun insertAll(allocations: List<TransactionAllocationEntity>): List<Long> = emptyList()
            override suspend fun update(allocation: TransactionAllocationEntity) {}
            override suspend fun deleteForTransaction(transactionId: Long) {}
            override suspend fun getForTransaction(transactionId: Long): List<TransactionAllocationEntity> = emptyList()
            override fun observeForTransaction(transactionId: Long): Flow<List<TransactionAllocationEntity>> = emptyFlow()
            override suspend fun getAllocationsInPeriod(startEpochMillis: Long, endEpochMillis: Long): List<TransactionAllocationEntity> = emptyList()
            override suspend fun getAllocationsForCategoryInPeriod(categoryId: Long, startEpochMillis: Long, endEpochMillis: Long): List<TransactionAllocationEntity> = emptyList()
            override suspend fun getAllTransactionAllocationsSync(): List<TransactionAllocationEntity> = emptyList()
        }

        val budgetRepo = BudgetRepository(fakeTxRunner, fakeCategoryDao, fakePeriodDao, fakeAllocDao, fakeAdjustmentDao)
        val checker = DataIntegrityChecker()

        val engine = MonthCloseEngine(
            transactionRunner = fakeTxRunner,
            budgetPeriodDao = fakePeriodDao,
            monthCloseDao = fakeMonthCloseDao,
            advanceLedgerDao = fakeAdvanceDao,
            budgetAllocationDao = fakeAllocDao,
            budgetAdjustmentDao = fakeAdjustmentDao,
            transactionDao = fakeTxDao,
            transactionAllocationDao = fakeTxAllocDao,
            accountDao = fakeAccountDao,
            categoryDao = fakeCategoryDao,
            budgetRepository = budgetRepo,
            dataIntegrityChecker = checker
        )

        // 1. Preview
        val previewResult = engine.preview(samplePeriod.id)

        // 2. Real Close
        engine.closeMonth(samplePeriod.id, allowUnreviewedOverride = true)

        // 3. Assert I7: preview entries == real close advance ledger entries
        val realCloseEntries = advanceStore.filter { it.sourcePeriodId == samplePeriod.id }
        assertThat(previewResult.advanceEntries.size).isEqualTo(realCloseEntries.size)

        for (i in previewResult.advanceEntries.indices) {
            val p = previewResult.advanceEntries[i]
            val r = realCloseEntries[i]
            assertThat(p.categoryId).isEqualTo(r.categoryId)
            assertThat(p.amountPaise).isEqualTo(r.amountPaise)
            assertThat(p.direction).isEqualTo(r.direction)
            assertThat(p.appliedToPeriodId).isEqualTo(r.appliedToPeriodId)
        }
    }

    // --- Invariant I8: V12 Residual == 0 on Valid Proof & Non-Zero with Red Bar Otherwise ---
    @Test
    fun testI8_ReconciliationBridgeResidualZeroOnValidProofAndRedBarOtherwise() {
        val balancedProof = ReconciliationProof(
            stmtClosingPaise = 50_000_00L,
            appBalanceEndPaise = 40_000_00L,
            lhsPaise = 50_000_00L,
            stmtOpeningPaise = 30_000_00L,
            appBalanceStartMinusPaise = 25_000_00L,
            openingDifferentialPaise = 5_000_00L,
            missingInAppSumPaise = 6_000_00L,
            missingInStmtSumPaise = 1_000_00L,
            discrepancySumPaise = 0L,
            timingDifferenceSumPaise = 0L,
            rhsPaise = 50_000_00L,
            isBalanced = true,
            missingInAppLines = emptyList(),
            missingInStmtTransactions = emptyList(),
            discrepancies = emptyList(),
            timingDifferences = emptyList()
        )

        val balancedBridgeState = ReconciliationChartBuilder.buildReconciliationBridge(balancedProof)
        val balancedBridge = (balancedBridgeState as ChartState.Data).data

        assertThat(balancedBridge.residualPaise).isEqualTo(0L)
        assertThat(balancedBridge.isReconciled).isTrue()
        assertThat(balancedBridge.steps.any { it.isResidual }).isFalse()

        // Unbalanced proof (e.g. discrepancy not accounted for)
        val unbalancedProof = balancedProof.copy(
            stmtClosingPaise = 52_000_00L, // 2,000 paise unaccounted difference
            isBalanced = false
        )
        val unbalancedBridgeState = ReconciliationChartBuilder.buildReconciliationBridge(unbalancedProof)
        val unbalancedBridge = (unbalancedBridgeState as ChartState.Data).data

        assertThat(unbalancedBridge.residualPaise).isEqualTo(2_000_00L)
        assertThat(unbalancedBridge.isReconciled).isFalse()
        assertThat(unbalancedBridge.steps.any { it.isResidual }).isTrue()
    }

    // --- Invariant I9: Status Filtering - Only CATEGORIZED in Spend Visuals ---
    @Test
    fun testI9_StatusFiltering_OnlyCategorizedInSpendVisuals() {
        val txPending = TransactionEntity(id = 1L, accountId = 1L, kind = TransactionKind.EXPENSE, direction = TransactionDirection.OUT, amountPaise = 5_000_00L, status = TransactionStatus.PENDING, source = TransactionSource.MANUAL, occurredAt = startAt, createdAt = startAt, updatedAt = startAt)
        val taPending = TransactionAllocationEntity(id = 1L, transactionId = 1L, categoryId = 10L, amountPaise = 5_000_00L)

        val txConfirmed = TransactionEntity(id = 2L, accountId = 1L, kind = TransactionKind.EXPENSE, direction = TransactionDirection.OUT, amountPaise = 7_000_00L, status = TransactionStatus.CONFIRMED, source = TransactionSource.MANUAL, occurredAt = startAt, createdAt = startAt, updatedAt = startAt)
        val taConfirmed = TransactionAllocationEntity(id = 2L, transactionId = 2L, categoryId = 10L, amountPaise = 7_000_00L)

        val txIgnored = TransactionEntity(id = 3L, accountId = 1L, kind = TransactionKind.EXPENSE, direction = TransactionDirection.OUT, amountPaise = 3_000_00L, status = TransactionStatus.IGNORED, source = TransactionSource.MANUAL, occurredAt = startAt, createdAt = startAt, updatedAt = startAt)
        val taIgnored = TransactionAllocationEntity(id = 3L, transactionId = 3L, categoryId = 10L, amountPaise = 3_000_00L)

        val txVoided = TransactionEntity(id = 4L, accountId = 1L, kind = TransactionKind.EXPENSE, direction = TransactionDirection.OUT, amountPaise = 2_000_00L, status = TransactionStatus.VOIDED, source = TransactionSource.MANUAL, occurredAt = startAt, createdAt = startAt, updatedAt = startAt)
        val taVoided = TransactionAllocationEntity(id = 4L, transactionId = 4L, categoryId = 10L, amountPaise = 2_000_00L)

        val txCategorized = TransactionEntity(id = 5L, accountId = 1L, kind = TransactionKind.EXPENSE, direction = TransactionDirection.OUT, amountPaise = 10_000_00L, status = TransactionStatus.CATEGORIZED, source = TransactionSource.MANUAL, occurredAt = startAt, createdAt = startAt, updatedAt = startAt)
        val taCategorized = TransactionAllocationEntity(id = 5L, transactionId = 5L, categoryId = 10L, amountPaise = 10_000_00L)

        val allTxs = listOf(txPending, txConfirmed, txIgnored, txVoided, txCategorized)
        val allTas = listOf(taPending, taConfirmed, taIgnored, taVoided, taCategorized)

        val summary = computeSummary(allTxs, allTas)

        // Only CATEGORIZED hits budget spend (₹10,000)
        assertThat(summary.dashboardHeroSpendPaise).isEqualTo(10_000_00L)

        val heroRing = (DashboardChartBuilder.buildHeroRing(summary, isPeriodClosed = false, nowIST = startAt) as ChartState.Data).data
        assertThat(heroRing.spentPaise).isEqualTo(10_000_00L)

        // Meanwhile balance visuals DO count PENDING and CONFIRMED
        val bank = AccountEntity(id = 1L, name = "Bank", type = AccountType.BANK, identifierLast4 = "1234", openingBalancePaise = 100_000_00L, openingBalanceDate = startAt - 1000L, isActive = true, createdAt = startAt, updatedAt = startAt)
        val comp = (AccountsChartBuilder.buildAccountComposition(listOf(bank), allTxs, asOfNow = startAt + 10_000L) as ChartState.Data).data

        // Balance includes PENDING (5000), CONFIRMED (7000), and CATEGORIZED (10000) = 22,000 out
        // 100,000 - 22,000 = 78,000
        assertThat(comp.totalAssetsPaise).isEqualTo(78_000_00L)
    }

    // --- Invariant I10: Unassigned account_id IS NULL Excluded from Balance Visuals with Banner ---
    @Test
    fun testI10_UnassignedAccountIdNullExcludedFromBalanceVisualsWithBanner() {
        val txUnassigned = TransactionEntity(id = 1L, accountId = null, kind = TransactionKind.EXPENSE, direction = TransactionDirection.OUT, amountPaise = 5_000_00L, status = TransactionStatus.CATEGORIZED, source = TransactionSource.MANUAL, occurredAt = startAt, createdAt = startAt, updatedAt = startAt)
        val bank = AccountEntity(id = 1L, name = "Bank", type = AccountType.BANK, identifierLast4 = "1234", openingBalancePaise = 50_000_00L, openingBalanceDate = startAt - 1000L, isActive = true, createdAt = startAt, updatedAt = startAt)

        val compState = AccountsChartBuilder.buildAccountComposition(listOf(bank), listOf(txUnassigned), asOfNow = startAt)
        val comp = (compState as ChartState.Data).data

        // Balance unaffected by unassigned tx
        assertThat(comp.totalAssetsPaise).isEqualTo(50_000_00L)
        assertThat(comp.unassignedCount).isEqualTo(1)
        assertThat(comp.unassignedExcludedMessage).isEqualTo("1 unassigned transactions excluded")
    }

    // --- Invariant I12: ₹100 Cr Values Handled & Overflow Triggers ChartState.Error ---
    @Test
    fun testI12_LargeValuesAndArithmeticOverflowEmitsChartStateError() {
        val hundredCrPaise = 100_000_000_000_00L // ₹100 Crore in paise
        val largeSummary = samplePeriod.copy(incomePaise = hundredCrPaise)

        val summary = BudgetCalculator.calculateBudgetPeriodSummary(
            period = largeSummary,
            allocations = listOf(allocNeeds.copy(amountPaise = hundredCrPaise)),
            adjustments = emptyList(),
            transactions = emptyList(),
            allocationsForTransactions = emptyList()
        )

        // ₹100 Cr does not crash or throw
        val ringState = DashboardChartBuilder.buildHeroRing(summary, isPeriodClosed = false, nowIST = startAt)
        assertThat(ringState).isInstanceOf(ChartState.Data::class.java)

        // Overflow condition triggers ChartState.Error
        val overflowingSummary = summary.copy(dashboardHeroSpendPaise = Long.MAX_VALUE, dashboardHeroBudgetPaise = -100L)
        val errorState = DashboardChartBuilder.buildHeroRing(overflowingSummary, isPeriodClosed = false, nowIST = startAt)
        assertThat(errorState).isInstanceOf(ChartState.Error::class.java)
    }

    // --- Invariant I13: Savings Envelopes Never Exceeded Never Red ---
    @Test
    fun testI13_SavingsEnvelopesNeverExceededNeverRed() {
        // Savings spent > adjustedBudget (over-saved, celebrate!)
        val txSavingsSurplus = TransactionEntity(id = 1L, accountId = 1L, kind = TransactionKind.TRANSFER, direction = TransactionDirection.OUT, amountPaise = 30_000_00L, status = TransactionStatus.CATEGORIZED, source = TransactionSource.MANUAL, occurredAt = startAt, createdAt = startAt, updatedAt = startAt)
        val taSavingsSurplus = TransactionAllocationEntity(id = 1L, transactionId = 1L, categoryId = 30L, amountPaise = 30_000_00L)

        val summarySurplus = computeSummary(listOf(txSavingsSurplus), listOf(taSavingsSurplus))
        val bulletBarsSurplus = (BudgetChartBuilder.buildEnvelopeBulletBars(summarySurplus) as ChartState.Data).data
        val savingsItemSurplus = bulletBarsSurplus.envelopes.first { it.bucket == BudgetBucket.SAVINGS }

        assertThat(savingsItemSurplus.isExceeded).isFalse()
        assertThat(savingsItemSurplus.isGoalAchieved).isTrue()

        // Savings spent < adjustedBudget (under-saved)
        val summaryUnder = computeSummary(emptyList(), emptyList())
        val bulletBarsUnder = (BudgetChartBuilder.buildEnvelopeBulletBars(summaryUnder) as ChartState.Data).data
        val savingsItemUnder = bulletBarsUnder.envelopes.first { it.bucket == BudgetBucket.SAVINGS }

        assertThat(savingsItemUnder.isExceeded).isFalse()
        assertThat(savingsItemUnder.isWarning).isFalse()
    }

    // --- Invariant I11: IST Boundary Precision & 86,400,000 ms Day ---
    @Test
    fun testI11_EveryIstDayIsExactly86400000msAndMonthBoundariesPrecision() {
        val millisPerDay = 86_400_000L
        val testYearMonths = listOf(
            Triple(2025, 2, 28), // 28-day Feb
            Triple(2028, 2, 29), // 29-day Feb (leap year 2028)
            Triple(2026, 4, 30), // 30-day April
            Triple(2026, 10, 31) // 31-day October
        )

        for ((year, month, expectedDays) in testYearMonths) {
            val startMillis = com.tapledger.core.util.DateTimeUtils.getStartOfMonthEpochMillis(year, month)
            val endMillis = com.tapledger.core.util.DateTimeUtils.getEndOfMonthEpochMillis(year, month)
            val durationMillis = endMillis - startMillis
            val expectedDurationMillis = expectedDays.toLong() * millisPerDay
            assertThat(durationMillis).isEqualTo(expectedDurationMillis)

            for (day in 1..expectedDays) {
                val dayStart = startMillis + (day - 1).toLong() * millisPerDay
                val zdt = com.tapledger.core.util.DateTimeUtils.toZonedDateTime(dayStart)
                assertThat(zdt.year).isEqualTo(year)
                assertThat(zdt.monthValue).isEqualTo(month)
                assertThat(zdt.dayOfMonth).isEqualTo(day)
                assertThat(zdt.hour).isEqualTo(0)
                assertThat(zdt.minute).isEqualTo(0)
                assertThat(zdt.second).isEqualTo(0)
                assertThat(zdt.nano).isEqualTo(0)
                assertThat(zdt.zone).isEqualTo(com.tapledger.core.util.DateTimeUtils.IST_ZONE)
            }
        }
    }

    // --- Invariant I14: Float Permitted ONLY Inside ui/charts/render/** ---
    @Test
    fun testI14_FloatPermittedOnlyInsideRenderPackage() {
        val scanner = com.tapledger.core.model.ZeroFloatScannerTest()

        // 1. Core / financial model rejects Float
        val coreViolations = scanner.scanLine("com/tapledger/core/model/Budget.kt", 1, "val amount: Float = 100.0f")
        assertThat(coreViolations).hasSize(1)
        assertThat(coreViolations[0].message).contains("forbidden 'Float' outside render")

        // 2. Core rejects toFloat()
        val toFloatViolations = scanner.scanLine("com/tapledger/domain/calculator/BudgetCalculator.kt", 1, "val r = paise.toFloat() / 100")
        assertThat(toFloatViolations).hasSize(1)
        assertThat(toFloatViolations[0].message).contains("forbidden 'toFloat()' outside render")

        // 3. UI Screen rejects Float
        val uiViolations = scanner.scanLine("com/tapledger/ui/screens/dashboard/DashboardScreen.kt", 1, "val p: Float = 0.5f")
        assertThat(uiViolations).hasSize(1)
        assertThat(uiViolations[0].message).contains("forbidden 'Float' outside render")

        // 4. Render package allows Float
        val renderViolations = scanner.scanLine("com/tapledger/ui/charts/render/RingChartRender.kt", 1, "val sweepAngle: Float = 180f")
        assertThat(renderViolations).isEmpty()

        // 5. Double and BigDecimal are banned EVERYWHERE including render
        val renderDoubleViolations = scanner.scanLine("com/tapledger/ui/charts/render/RingChartRender.kt", 1, "val d: Double = 100.0")
        assertThat(renderDoubleViolations).hasSize(1)
        assertThat(renderDoubleViolations[0].message).contains("forbidden 'Double'")
    }
}
