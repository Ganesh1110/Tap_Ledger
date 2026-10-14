package com.tapledger.service

import com.google.common.truth.Truth.assertThat
import com.tapledger.core.database.DatabaseTransactionRunner
import com.tapledger.core.database.dao.AccountDao
import com.tapledger.core.database.dao.AuditLogDao
import com.tapledger.core.database.dao.BudgetAdjustmentDao
import com.tapledger.core.database.dao.BudgetAllocationDao
import com.tapledger.core.database.dao.BudgetPeriodDao
import com.tapledger.core.database.dao.CategoryDao
import com.tapledger.core.database.dao.TransactionDao
import com.tapledger.core.database.dao.UnparsedAlertDao
import com.tapledger.core.database.entity.AccountEntity
import com.tapledger.core.database.entity.AdvanceLedgerEntity
import com.tapledger.core.database.entity.AuditLogEntity
import com.tapledger.core.database.entity.BudgetAdjustmentEntity
import com.tapledger.core.database.entity.BudgetAllocationEntity
import com.tapledger.core.database.entity.BudgetPeriodEntity
import com.tapledger.core.database.entity.CategoryEntity
import com.tapledger.core.database.entity.TransactionEntity
import com.tapledger.core.database.entity.UnparsedAlertEntity
import com.tapledger.core.model.AccountType
import com.tapledger.core.model.AuditAction
import com.tapledger.core.model.BudgetBucket
import com.tapledger.core.model.BudgetPeriodStatus
import com.tapledger.core.model.TransactionDirection
import com.tapledger.core.model.TransactionKind
import com.tapledger.core.model.TransactionSource
import com.tapledger.core.model.TransactionStatus
import com.tapledger.core.util.DateTimeUtils
import com.tapledger.data.repository.BudgetRepository
import com.tapledger.parser.NotificationEvent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import java.time.YearMonth

class IngestionPipelineTest {

    private class DirectTransactionRunner : DatabaseTransactionRunner {
        override suspend fun <R> run(block: suspend () -> R): R = block()
    }

    private class FakeTransactionDao : TransactionDao {
        val storage = mutableMapOf<Long, TransactionEntity>()
        private var nextId = 1L

        override suspend fun insert(transaction: TransactionEntity): Long {
            val id = if (transaction.id == 0L) nextId++ else transaction.id
            storage[id] = transaction.copy(id = id)
            return id
        }

        override suspend fun insertAll(transactions: List<TransactionEntity>): List<Long> {
            return transactions.map { insert(it) }
        }

        override suspend fun update(transaction: TransactionEntity) {
            storage[transaction.id] = transaction
        }

        override suspend fun getById(id: Long): TransactionEntity? = storage[id]
        override fun observeById(id: Long): Flow<TransactionEntity?> = emptyFlow()
        override fun observeAllTransactions(): Flow<List<TransactionEntity>> = emptyFlow()
        override suspend fun getAllTransactionsSync(): List<TransactionEntity> = storage.values.toList()
        override fun observePendingTransactions(): Flow<List<TransactionEntity>> = emptyFlow()
        override fun observePendingCount(): Flow<Int> = emptyFlow()
        override suspend fun getPendingCountSync(): Int = storage.values.count { it.status == TransactionStatus.PENDING }

        override suspend fun getBalanceTransactionsForAccount(accountId: Long, sinceEpochMillis: Long): List<TransactionEntity> =
            storage.values.filter { it.accountId == accountId && it.occurredAt >= sinceEpochMillis }

        override fun observeBalanceTransactionsForAccount(accountId: Long, sinceEpochMillis: Long): Flow<List<TransactionEntity>> = emptyFlow()

        override suspend fun getTransactionsInPeriod(startEpochMillis: Long, endEpochMillis: Long): List<TransactionEntity> =
            storage.values.filter { it.occurredAt in startEpochMillis until endEpochMillis }

        override fun observeTransactionsInPeriod(startEpochMillis: Long, endEpochMillis: Long): Flow<List<TransactionEntity>> = emptyFlow()

        override suspend fun findBySourceFingerprint(fingerprint: String): TransactionEntity? =
            storage.values.find { it.sourceFingerprint == fingerprint }

        override suspend fun findByUpiReference(upiRef: String): TransactionEntity? =
            storage.values.find { it.upiReference == upiRef }

        override suspend fun findByExactKey(
            accountId: Long,
            upiRef: String,
            direction: TransactionDirection,
            amountPaise: Long
        ): TransactionEntity? = storage.values.find {
            it.accountId == accountId && it.upiReference == upiRef && it.direction == direction && it.amountPaise == amountPaise
        }

        override suspend fun findByExactKeyUnassigned(
            upiRef: String,
            direction: TransactionDirection,
            amountPaise: Long
        ): TransactionEntity? = storage.values.find {
            it.accountId == null && it.upiReference == upiRef && it.direction == direction && it.amountPaise == amountPaise
        }

        override suspend fun findByUpiDirectionAmountAcrossAllAccounts(
            upiRef: String,
            direction: TransactionDirection,
            amountPaise: Long
        ): TransactionEntity? = storage.values.find {
            it.upiReference == upiRef && it.direction == direction && it.amountPaise == amountPaise
        }

        override suspend fun findFuzzyCandidates(
            accountId: Long?,
            direction: TransactionDirection,
            amountPaise: Long,
            startWindow: Long,
            endWindow: Long
        ): List<TransactionEntity> = storage.values.filter {
            (it.accountId == accountId) &&
                    it.direction == direction &&
                    it.amountPaise == amountPaise &&
                    it.occurredAt in startWindow..endWindow &&
                    it.status != TransactionStatus.VOIDED
        }.sortedByDescending { it.occurredAt }

        override suspend fun findTransferPairCandidates(
            excludeAccountId: Long,
            oppositeDirection: TransactionDirection,
            amountPaise: Long,
            startWindow: Long,
            endWindow: Long
        ): List<TransactionEntity> = emptyList()

        override suspend fun getTransferLegs(transferGroupId: String): List<TransactionEntity> = emptyList()
        override suspend fun getRefundsForTransaction(originalTransactionId: Long): List<TransactionEntity> = emptyList()
        override suspend fun getTransactionsForAccountSync(accountId: Long): List<TransactionEntity> =
            storage.values.filter { it.accountId == accountId }
    }

    private class FakeAccountDao : AccountDao {
        val accounts = mutableListOf<AccountEntity>()
        private var nextId = 1L

        override suspend fun insert(account: AccountEntity): Long {
            val id = if (account.id == 0L) nextId++ else account.id
            accounts.add(account.copy(id = id))
            return id
        }

        override suspend fun insertAll(accounts: List<AccountEntity>): List<Long> = accounts.map { insert(it) }
        override suspend fun update(account: AccountEntity) {
            val idx = accounts.indexOfFirst { it.id == account.id }
            if (idx >= 0) accounts[idx] = account
        }

        override suspend fun getById(id: Long): AccountEntity? = accounts.find { it.id == id }
        override fun getAllActiveAccounts(): Flow<List<AccountEntity>> = emptyFlow()
        override suspend fun getAllAccountsSync(): List<AccountEntity> = accounts

        override suspend fun findByLast4(last4: String): AccountEntity? =
            accounts.find { it.identifierLast4 == last4 && it.isActive }

        override suspend fun findAllActiveByLast4(last4: String): List<AccountEntity> =
            accounts.filter { it.identifierLast4 == last4 && it.isActive }

        override suspend fun getActiveBankAccounts(): List<AccountEntity> =
            accounts.filter { it.type == AccountType.BANK && it.isActive }
    }

    private class FakeBudgetPeriodDao : BudgetPeriodDao {
        val periods = mutableListOf<BudgetPeriodEntity>()
        private var nextId = 1L

        override suspend fun insert(budgetPeriod: BudgetPeriodEntity): Long {
            val id = if (budgetPeriod.id == 0L) nextId++ else budgetPeriod.id
            periods.add(budgetPeriod.copy(id = id))
            return id
        }

        override suspend fun update(budgetPeriod: BudgetPeriodEntity) {
            val idx = periods.indexOfFirst { it.id == budgetPeriod.id }
            if (idx >= 0) periods[idx] = budgetPeriod
        }

        override suspend fun getById(id: Long): BudgetPeriodEntity? = periods.find { it.id == id }

        override suspend fun getByYearMonth(year: Int, month: Int): BudgetPeriodEntity? =
            periods.find { it.year == year && it.month == month }

        override fun observeByYearMonth(year: Int, month: Int): Flow<BudgetPeriodEntity?> = emptyFlow()
        override fun getAllPeriods(): Flow<List<BudgetPeriodEntity>> = emptyFlow()
        override suspend fun getAllPeriodsSync(): List<BudgetPeriodEntity> = periods

        override suspend fun getPeriodForTimestamp(timestamp: Long): BudgetPeriodEntity? =
            periods.find { timestamp >= it.startAt && timestamp < it.endAt }

        override suspend fun getEarliestPeriod(): BudgetPeriodEntity? = periods.minByOrNull { it.startAt }
        override suspend fun getLatestPeriod(): BudgetPeriodEntity? = periods.maxByOrNull { it.startAt }

        override suspend fun getOpenPeriodForTimestamp(timestamp: Long): BudgetPeriodEntity? =
            periods.find { it.status == BudgetPeriodStatus.OPEN && timestamp >= it.startAt && timestamp < it.endAt }

        override suspend fun getFirstOpenPeriod(): BudgetPeriodEntity? =
            periods.filter { it.status == BudgetPeriodStatus.OPEN }.minByOrNull { it.startAt }

        override suspend fun getClosedPeriodEndingAfter(startOfDayTimestamp: Long): BudgetPeriodEntity? =
            periods.find { it.status == BudgetPeriodStatus.CLOSED && it.endAt > startOfDayTimestamp }

        override suspend fun getClosedPeriodForTimestamp(timestamp: Long): BudgetPeriodEntity? =
            periods.find { it.status == BudgetPeriodStatus.CLOSED && timestamp >= it.startAt && timestamp < it.endAt }
    }

    private class FakeBudgetAllocationDao : BudgetAllocationDao() {
        val allocations = mutableListOf<BudgetAllocationEntity>()
        private var nextId = 1L

        override suspend fun getAdjustmentsForPeriod(periodId: Long): List<BudgetAdjustmentEntity> = emptyList()
        override suspend fun getAdvancesForPeriod(periodId: Long): List<AdvanceLedgerEntity> = emptyList()

        override suspend fun insertInternal(allocation: BudgetAllocationEntity): Long {
            val id = if (allocation.id == 0L) nextId++ else allocation.id
            allocations.add(allocation.copy(id = id))
            return id
        }

        override suspend fun insertAllInternal(allocations: List<BudgetAllocationEntity>): List<Long> =
            allocations.map { insertInternal(it) }

        override suspend fun updateInternal(allocation: BudgetAllocationEntity) {
            val idx = allocations.indexOfFirst { it.id == allocation.id }
            if (idx >= 0) allocations[idx] = allocation
        }

        override fun observeAllocationsForPeriod(periodId: Long): Flow<List<BudgetAllocationEntity>> = emptyFlow()
        override suspend fun getAllocationsForPeriodSync(periodId: Long): List<BudgetAllocationEntity> =
            allocations.filter { it.budgetPeriodId == periodId }

        override suspend fun getAllAllocationsSync(): List<BudgetAllocationEntity> = allocations
        override suspend fun getAllocation(periodId: Long, categoryId: Long): BudgetAllocationEntity? =
            allocations.find { it.budgetPeriodId == periodId && it.categoryId == categoryId }

        override suspend fun deleteAllocation(periodId: Long, categoryId: Long) {
            allocations.removeAll { it.budgetPeriodId == periodId && it.categoryId == categoryId }
        }
    }

    private class FakeCategoryDao : CategoryDao {
        val categories = mutableListOf<CategoryEntity>()

        override suspend fun insert(category: CategoryEntity): Long {
            categories.add(category)
            return category.id
        }

        override suspend fun insertAll(categories: List<CategoryEntity>): List<Long> {
            this.categories.addAll(categories)
            return categories.map { it.id }
        }

        override suspend fun update(category: CategoryEntity) {}
        override suspend fun getById(id: Long): CategoryEntity? = categories.find { it.id == id }
        override suspend fun getByName(name: String): CategoryEntity? = categories.find { it.name == name }
        override suspend fun getMaxSortOrder(): Int? = categories.maxOfOrNull { it.defaultSortOrder }
        override fun getAllActiveCategories(): Flow<List<CategoryEntity>> = emptyFlow()
        override suspend fun getAllCategoriesSync(): List<CategoryEntity> = categories
    }

    private class FakeBudgetAdjustmentDao : BudgetAdjustmentDao {
        override suspend fun insert(adjustment: BudgetAdjustmentEntity): Long = 1L
        override suspend fun getAdjustmentsForPeriodSync(periodId: Long): List<BudgetAdjustmentEntity> = emptyList()
        override suspend fun getAllAdjustmentsSync(): List<BudgetAdjustmentEntity> = emptyList()
        override fun observeAdjustmentsForPeriod(periodId: Long): Flow<List<BudgetAdjustmentEntity>> = emptyFlow()
    }

    private class FakeUnparsedAlertDao : UnparsedAlertDao {
        val alerts = mutableListOf<UnparsedAlertEntity>()

        override suspend fun insert(alert: UnparsedAlertEntity): Long {
            alerts.add(alert)
            return alert.id
        }

        override fun observeAll(): Flow<List<UnparsedAlertEntity>> = emptyFlow()
        override fun observeCount(): Flow<Int> = emptyFlow()
        override suspend fun purgeOlderThan(thresholdTimestamp: Long): Int = 0
    }

    private class FakeAuditLogDao : AuditLogDao {
        val logs = mutableListOf<AuditLogEntity>()

        override suspend fun insert(entry: AuditLogEntity): Long {
            logs.add(entry)
            return entry.id
        }

        override fun observeForTransaction(transactionId: Long): Flow<List<AuditLogEntity>> = emptyFlow()
        override suspend fun getForTransactionSync(transactionId: Long): List<AuditLogEntity> =
            logs.filter { it.transactionId == transactionId }
        override fun observeRecentAudits(limit: Int): Flow<List<AuditLogEntity>> = emptyFlow()
    }

    private lateinit var transactionDao: FakeTransactionDao
    private lateinit var accountDao: FakeAccountDao
    private lateinit var budgetPeriodDao: FakeBudgetPeriodDao
    private lateinit var budgetAllocationDao: FakeBudgetAllocationDao
    private lateinit var categoryDao: FakeCategoryDao
    private lateinit var budgetAdjustmentDao: FakeBudgetAdjustmentDao
    private lateinit var unparsedAlertDao: FakeUnparsedAlertDao
    private lateinit var auditLogDao: FakeAuditLogDao
    private lateinit var budgetRepository: BudgetRepository
    private lateinit var pipeline: FinancialIngestionPipeline

    @Before
    fun setUp() {
        runBlocking {
            transactionDao = FakeTransactionDao()
            accountDao = FakeAccountDao()
            budgetPeriodDao = FakeBudgetPeriodDao()
            budgetAllocationDao = FakeBudgetAllocationDao()
            categoryDao = FakeCategoryDao()
            budgetAdjustmentDao = FakeBudgetAdjustmentDao()
            unparsedAlertDao = FakeUnparsedAlertDao()
            auditLogDao = FakeAuditLogDao()

            budgetRepository = BudgetRepository(
                transactionRunner = DirectTransactionRunner(),
                categoryDao = categoryDao,
                budgetPeriodDao = budgetPeriodDao,
                budgetAllocationDao = budgetAllocationDao,
                budgetAdjustmentDao = budgetAdjustmentDao
            )

            pipeline = FinancialIngestionPipeline(
                transactionRunner = DirectTransactionRunner(),
                transactionDao = transactionDao,
                accountDao = accountDao,
                budgetPeriodDao = budgetPeriodDao,
                budgetRepository = budgetRepository,
                unparsedAlertDao = unparsedAlertDao,
                auditLogDao = auditLogDao
            )

            // Seed default active account
            accountDao.insert(
                AccountEntity(
                    id = 1L,
                    name = "HDFC Salary",
                    type = AccountType.BANK,
                    identifierLast4 = "1234",
                    openingBalancePaise = 0L,
                    openingBalanceDate = 1700000000000L,
                    isActive = true,
                    createdAt = 1700000000000L,
                    updatedAt = 1700000000000L
                )
            )

            // Seed default active categories
            categoryDao.insert(CategoryEntity(id = 10L, name = "Groceries", defaultBucket = BudgetBucket.NEEDS, defaultSortOrder = 1))
            categoryDao.insert(CategoryEntity(id = 20L, name = "Dining", defaultBucket = BudgetBucket.WANTS, defaultSortOrder = 2))

            // Seed initial budget period for now
            val now = DateTimeUtils.nowEpochMillis()
            val ym = DateTimeUtils.toYearMonth(now)
            budgetRepository.getOrCreateBudgetPeriod(ym.year, ym.monthValue)
        }
    }

    @Test
    fun testThreeStagePipeline_normalExpense() = runBlocking {
        val now = DateTimeUtils.nowEpochMillis()
        val event = NotificationEvent(
            packageName = "com.google.android.apps.messaging",
            title = "HDFC Bank",
            text = "Sent Rs. 850.00 from HDFC Bank A/C **1234 to SWIGGY on 08-10-26 via UPI Ref 428192837482. Avl Bal: Rs 49,150.00",
            postTime = now
        )

        val result = pipeline.processEvent(event)
        assertThat(result).isInstanceOf(IngestionResult.Success::class.java)

        val success = result as IngestionResult.Success
        assertThat(success.resolvedAccountId).isEqualTo(1L)
        assertThat(success.isPossibleDuplicate).isFalse()

        // Verify transaction inserted with status PENDING and non-null direction OUT
        val inserted = transactionDao.getById(success.transactionId)
        assertThat(inserted).isNotNull()
        assertThat(inserted?.status).isEqualTo(TransactionStatus.PENDING)
        assertThat(inserted?.direction).isEqualTo(TransactionDirection.OUT)
        assertThat(inserted?.amountPaise).isEqualTo(85000L)
        assertThat(inserted?.merchantRaw).isEqualTo("SWIGGY")
        assertThat(inserted?.upiReference).isEqualTo("428192837482")

        // Verify audit log
        val logs = auditLogDao.getForTransactionSync(success.transactionId)
        assertThat(logs).hasSize(1)
        assertThat(logs[0].action).isEqualTo(AuditAction.CREATED)
    }

    @Test
    fun testStage0_duplicateSystemEventDroppedWithin60s() = runBlocking {
        val now = DateTimeUtils.nowEpochMillis()
        val event = NotificationEvent(
            packageName = "com.google.android.apps.messaging",
            title = "HDFC Bank",
            text = "Sent Rs. 500.00 from HDFC Bank A/C **1234 to UBER via UPI Ref 991199119911",
            postTime = now
        )

        val firstResult = pipeline.processEvent(event)
        assertThat(firstResult).isInstanceOf(IngestionResult.Success::class.java)

        // Same notification re-delivered by OS within 60s
        val secondResult = pipeline.processEvent(event)
        assertThat(secondResult).isInstanceOf(IngestionResult.DroppedSystemDuplicate::class.java)

        // Only 1 transaction should exist
        assertThat(transactionDao.getAllTransactionsSync()).hasSize(1)
    }

    @Test
    fun testExactDeduplication_assignedWithUpiRef() = runBlocking {
        val now = DateTimeUtils.nowEpochMillis()
        val event1 = NotificationEvent(
            packageName = "com.google.android.apps.messaging",
            title = "HDFC Bank",
            text = "Sent Rs. 1,000.00 from HDFC Bank A/C **1234 to AMAZON via UPI Ref 112233445566",
            postTime = now
        )
        val res1 = pipeline.processEvent(event1)
        assertThat(res1).isInstanceOf(IngestionResult.Success::class.java)
        val txId1 = (res1 as IngestionResult.Success).transactionId

        // Second notification arrives later (with different postTime) but identical exact key (account, upiRef, direction, amount)
        val event2 = NotificationEvent(
            packageName = "com.phonepe.app",
            title = "PhonePe",
            text = "Paid Rs. 1,000.00 to AMAZON using HDFC Bank A/C **1234 UPI Ref 112233445566",
            postTime = now + 5000L
        )
        val res2 = pipeline.processEvent(event2)
        assertThat(res2).isInstanceOf(IngestionResult.DroppedExactDuplicate::class.java)

        // Verify audit log has DUPLICATE_DROPPED on matched txId1
        val logs = auditLogDao.getForTransactionSync(txId1)
        assertThat(logs.any { it.action == AuditAction.DUPLICATE_DROPPED }).isTrue()
    }

    @Test
    fun testExactDeduplication_unassignedWithUpiRef() = runBlocking {
        val now = DateTimeUtils.nowEpochMillis()
        // Account 9999 does not exist -> unassigned account
        val event1 = NotificationEvent(
            packageName = "com.google.android.apps.messaging",
            title = "Unknown Bank",
            text = "Paid Rs. 450.00 from Card **9999 to SWIGGY via UPI Ref 887766554433",
            postTime = now
        )
        val res1 = pipeline.processEvent(event1)
        assertThat(res1).isInstanceOf(IngestionResult.Success::class.java)
        val success1 = res1 as IngestionResult.Success
        assertThat(success1.resolvedAccountId).isNull()

        // Second alert arrives with same UPI ref, amount, direction across all accounts
        val event2 = NotificationEvent(
            packageName = "com.swiggy.android",
            title = "Swiggy",
            text = "Paid Rs. 450.00 to SWIGGY via UPI Ref 887766554433",
            postTime = now + 10_000L
        )
        val res2 = pipeline.processEvent(event2)
        assertThat(res2).isInstanceOf(IngestionResult.DroppedExactDuplicate::class.java)
    }

    @Test
    fun testDeduplicationWithoutUpiRef_crossSourceWithin60s() = runBlocking {
        val now = DateTimeUtils.nowEpochMillis()
        // Alert 1 from SMS without UPI reference
        val event1 = NotificationEvent(
            packageName = "com.google.android.apps.messaging",
            title = "Bank SMS",
            text = "Sent Rs. 250.00 from A/C **1234 to ZOMATO on today",
            postTime = now
        )
        val res1 = pipeline.processEvent(event1)
        assertThat(res1).isInstanceOf(IngestionResult.Success::class.java)
        val tx1 = (res1 as IngestionResult.Success).transactionId

        // Alert 2 from different package (UPI push notification) for same merchant & amount within 30s
        val event2 = NotificationEvent(
            packageName = "net.one97.paytm",
            title = "Paytm",
            text = "Paid Rs. 250.00 to ZOMATO from A/C **1234",
            postTime = now + 30_000L
        )
        val res2 = pipeline.processEvent(event2)
        assertThat(res2).isInstanceOf(IngestionResult.DroppedCrossSourceDuplicate::class.java)

        val logs = auditLogDao.getForTransactionSync(tx1)
        assertThat(logs.any { it.action == AuditAction.DUPLICATE_DROPPED }).isTrue()
    }

    @Test
    fun testCandidateDuplicate_samePackage_rapidLegitimateTaps() = runBlocking {
        val now = DateTimeUtils.nowEpochMillis()
        // Event 1: ₹50 Metro fare from Metro app
        val event1 = NotificationEvent(
            packageName = "com.dmrc.metro",
            title = "Metro",
            text = "Paid Rs. 50.00 at METRO from A/C **1234",
            postTime = now
        )
        val res1 = pipeline.processEvent(event1)
        assertThat(res1).isInstanceOf(IngestionResult.Success::class.java)
        val tx1 = (res1 as IngestionResult.Success).transactionId

        // Event 2: Rapid legitimate purchase from SAME app (tapping family member in) within 30s
        val event2 = NotificationEvent(
            packageName = "com.dmrc.metro",
            title = "Metro",
            text = "Paid Rs. 50.00 at METRO from A/C **1234",
            postTime = now + 30_000L
        )
        val res2 = pipeline.processEvent(event2)
        // Must NOT be dropped silently! Safely ingested with possibleDuplicateOfId
        assertThat(res2).isInstanceOf(IngestionResult.Success::class.java)
        val success2 = res2 as IngestionResult.Success
        assertThat(success2.isPossibleDuplicate).isTrue()
        assertThat(success2.possibleDuplicateOfId).isEqualTo(tx1)

        val tx2 = transactionDao.getById(success2.transactionId)
        assertThat(tx2?.possibleDuplicateOfId).isEqualTo(tx1)
    }

    @Test
    fun testBoundedIngestion_futureTimestampCappedToNow() = runBlocking {
        val now = DateTimeUtils.nowEpochMillis()
        val futureTime = now + 7 * 86_400_000L // 7 days in future (> now + 1 day)

        val event = NotificationEvent(
            packageName = "com.google.android.apps.messaging",
            title = "Bank",
            text = "Paid Rs. 150.00 from A/C **1234 to CHAI POINT",
            postTime = futureTime
        )

        val result = pipeline.processEvent(event)
        assertThat(result).isInstanceOf(IngestionResult.Success::class.java)
        val success = result as IngestionResult.Success

        val tx = transactionDao.getById(success.transactionId)
        assertThat(tx).isNotNull()
        // Flow.md §3.1: clamped to now, original_occurred_at records the original future timestamp
        assertThat(tx?.occurredAt).isAtMost(now + 1000L)
        assertThat(tx?.originalOccurredAt).isEqualTo(futureTime)
    }

    @Test
    fun testClosedPeriodCheck_redirectsToEarliestOpenPeriod() = runBlocking {
        val now = DateTimeUtils.nowEpochMillis()
        val nowYm = DateTimeUtils.toYearMonth(now)
        val pastYm = nowYm.minusMonths(2)

        // Seed a CLOSED past period
        val closedStart = DateTimeUtils.getStartOfMonthEpochMillis(pastYm.year, pastYm.monthValue)
        val closedEnd = DateTimeUtils.getEndOfMonthEpochMillis(pastYm.year, pastYm.monthValue)
        budgetPeriodDao.insert(
            BudgetPeriodEntity(
                year = pastYm.year,
                month = pastYm.monthValue,
                startAt = closedStart,
                endAt = closedEnd,
                incomePaise = 5000000L,
                status = BudgetPeriodStatus.CLOSED,
                closedAt = now,
                createdAt = now,
                updatedAt = now
            )
        )

        val pastOccurredAt = closedStart + 5000L
        val event = NotificationEvent(
            packageName = "com.google.android.apps.messaging",
            title = "Bank",
            text = "Sent Rs. 300.00 from A/C **1234 to OLD VENDOR",
            postTime = pastOccurredAt
        )

        val result = pipeline.processEvent(event)
        assertThat(result).isInstanceOf(IngestionResult.Success::class.java)
        val success = result as IngestionResult.Success

        val tx = transactionDao.getById(success.transactionId)
        assertThat(tx).isNotNull()
        // Booked into earliest OPEN period containing now; occurred_at set to now, original preserved
        assertThat(tx?.occurredAt).isAtLeast(now - 2000L)
        assertThat(tx?.originalOccurredAt).isEqualTo(pastOccurredAt)
    }

    @Test
    fun testContiguousPeriodAutoCreation_initializesWithNeedsReview() = runBlocking {
        val now = DateTimeUtils.nowEpochMillis()
        val nowYm = DateTimeUtils.toYearMonth(now)
        val nextYm = nowYm.plusMonths(1)
        val nextMonthTimestamp = DateTimeUtils.getStartOfMonthEpochMillis(nextYm.year, nextYm.monthValue) + 1000L

        // Prior to event, next month does not exist
        assertThat(budgetPeriodDao.getByYearMonth(nextYm.year, nextYm.monthValue)).isNull()

        // Contiguous get-or-create for next month timestamp
        val createdPeriod = budgetRepository.getOrCreateBudgetPeriodForTimestamp(nextMonthTimestamp)

        // Next month period must have been created contiguously with is_reviewed = false
        assertThat(createdPeriod).isNotNull()
        assertThat(createdPeriod.status).isEqualTo(BudgetPeriodStatus.OPEN)
        assertThat(createdPeriod.isReviewed).isFalse()

        // Allocations must have been snapshotted from the latest period
        val allocations = budgetAllocationDao.getAllocationsForPeriodSync(createdPeriod.id)
        assertThat(allocations).isNotEmpty()
    }

    @Test
    fun testUnparsedAlertsLogging_potentialFinancialEvent() = runBlocking {
        val now = DateTimeUtils.nowEpochMillis()
        // Text mentions financial keywords (bank, debited, a/c) but amount is completely broken/missing
        val event = NotificationEvent(
            packageName = "com.google.android.apps.messaging",
            title = "Bank",
            text = "Your bank a/c was debited for your recent order details call us",
            postTime = now
        )

        val result = pipeline.processEvent(event)
        assertThat(result).isInstanceOf(IngestionResult.Unparsed::class.java)

        // Must be logged to unparsed_alerts table for user diagnostic review
        assertThat(unparsedAlertDao.alerts).hasSize(1)
        val alert = unparsedAlertDao.alerts[0]
        assertThat(alert.errorReason).isEqualTo("PARSER_UNMATCHED")
        assertThat(alert.packageName).isEqualTo("com.google.android.apps.messaging")
    }

    @Test
    fun testPureNonFinancialAlert_ignoredWithoutAlertLogging() = runBlocking {
        val now = DateTimeUtils.nowEpochMillis()
        val event = NotificationEvent(
            packageName = "com.google.android.apps.messaging",
            title = "OTP Bank",
            text = "Your secret OTP is 482910 for login. Do not share.",
            postTime = now
        )

        val result = pipeline.processEvent(event)
        assertThat(result).isInstanceOf(IngestionResult.IgnoredNonFinancial::class.java)

        // Must NOT pollute unparsed_alerts
        assertThat(unparsedAlertDao.alerts).isEmpty()
    }
}
