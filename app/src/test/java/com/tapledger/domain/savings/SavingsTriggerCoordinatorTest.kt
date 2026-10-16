package com.tapledger.domain.savings

import com.google.common.truth.Truth.assertThat
import com.tapledger.core.database.DatabaseTransactionRunner
import com.tapledger.core.database.dao.AccountDao
import com.tapledger.core.database.dao.AuditLogDao
import com.tapledger.core.database.dao.BudgetAdjustmentDao
import com.tapledger.core.database.dao.BudgetAllocationDao
import com.tapledger.core.database.dao.BudgetPeriodDao
import com.tapledger.core.database.dao.CategoryDao
import com.tapledger.core.database.dao.CommitmentPaymentDao
import com.tapledger.core.database.dao.RecurringCommitmentDao
import com.tapledger.core.database.dao.SavingsTransferPlanDao
import com.tapledger.core.database.dao.TransactionAllocationDao
import com.tapledger.core.database.dao.TransactionDao
import com.tapledger.core.database.entity.AccountEntity
import com.tapledger.core.database.entity.AuditLogEntity
import com.tapledger.core.database.entity.BudgetAdjustmentEntity
import com.tapledger.core.database.entity.BudgetAllocationEntity
import com.tapledger.core.database.entity.BudgetPeriodEntity
import com.tapledger.core.database.entity.CategoryEntity
import com.tapledger.core.database.entity.CommitmentPaymentEntity
import com.tapledger.core.database.entity.RecurringCommitmentEntity
import com.tapledger.core.database.entity.SavingsTransferPlanEntity
import com.tapledger.core.database.entity.TransactionAllocationEntity
import com.tapledger.core.database.entity.TransactionEntity
import com.tapledger.core.model.AccountType
import com.tapledger.core.model.AdvancePolicy
import com.tapledger.core.model.BudgetBucket
import com.tapledger.core.model.BudgetPeriodStatus
import com.tapledger.core.model.CommitmentPaymentStatus
import com.tapledger.core.model.TransactionDirection
import com.tapledger.core.model.TransactionKind
import com.tapledger.core.model.TransactionSource
import com.tapledger.core.model.TransactionStatus
import com.tapledger.core.model.TransferPlanKind
import com.tapledger.core.model.TransferPlanStatus
import com.tapledger.core.preferences.SavingsGuideConfig
import com.tapledger.core.preferences.SavingsGuidePreferences
import com.tapledger.core.util.DateTimeUtils
import com.tapledger.data.repository.BudgetRepository
import com.tapledger.data.repository.TransactionRepository
import com.tapledger.data.repository.TransferPlanRepository
import com.tapledger.domain.calculator.SpareCalculator
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import java.time.Instant

class SavingsTriggerCoordinatorTest {

    private class DirectTransactionRunner : DatabaseTransactionRunner {
        override suspend fun <T> run(block: suspend () -> T): T = block()
    }

    private class FakeSavingsTransferPlanDao : SavingsTransferPlanDao {
        val plans = mutableMapOf<Long, SavingsTransferPlanEntity>()
        private var nextId = 1L

        override suspend fun insert(plan: SavingsTransferPlanEntity): Long {
            val id = if (plan.id == 0L) nextId++ else plan.id
            plans[id] = plan.copy(id = id)
            return id
        }

        override suspend fun insertOrUpdate(plan: SavingsTransferPlanEntity): Long {
            val id = if (plan.id == 0L) nextId++ else plan.id
            plans[id] = plan.copy(id = id)
            return id
        }

        override suspend fun update(plan: SavingsTransferPlanEntity) {
            plans[plan.id] = plan
        }

        override suspend fun delete(plan: SavingsTransferPlanEntity) {
            plans.remove(plan.id)
        }

        override suspend fun getById(id: Long): SavingsTransferPlanEntity? = plans[id]
        override fun getByIdFlow(id: Long): Flow<SavingsTransferPlanEntity?> = flowOf(plans[id])
        override suspend fun getByPeriodAndKind(periodId: Long, kind: TransferPlanKind): SavingsTransferPlanEntity? =
            plans.values.find { it.periodId == periodId && it.kind == kind }
        override fun getByPeriodAndKindFlow(periodId: Long, kind: TransferPlanKind): Flow<SavingsTransferPlanEntity?> =
            flowOf(plans.values.find { it.periodId == periodId && it.kind == kind })
        override suspend fun getPlansForPeriod(periodId: Long): List<SavingsTransferPlanEntity> =
            plans.values.filter { it.periodId == periodId }.sortedByDescending { it.createdAt }
        override fun getPlansForPeriodFlow(periodId: Long): Flow<List<SavingsTransferPlanEntity>> = emptyFlow()
        override fun getActivePlansForPeriodFlow(periodId: Long): Flow<List<SavingsTransferPlanEntity>> = emptyFlow()
        override suspend fun getPlansByStatus(status: TransferPlanStatus): List<SavingsTransferPlanEntity> =
            plans.values.filter { it.status == status }
        override fun getPlansByStatusFlow(status: TransferPlanStatus): Flow<List<SavingsTransferPlanEntity>> = emptyFlow()
        override suspend fun getByTransferGroupId(transferGroupId: String): SavingsTransferPlanEntity? =
            plans.values.find { it.transferGroupId == transferGroupId }
        override suspend fun findMarkedMovedPlans(): List<SavingsTransferPlanEntity> =
            plans.values.filter { it.status == TransferPlanStatus.MARKED_MOVED }
        override fun findMarkedMovedPlansFlow(): Flow<List<SavingsTransferPlanEntity>> = emptyFlow()
        override suspend fun expireSuggestedPlansForPeriod(periodId: Long) {
            plans.values.filter { it.periodId == periodId && it.status == TransferPlanStatus.SUGGESTED }.forEach {
                plans[it.id] = it.copy(status = TransferPlanStatus.EXPIRED)
            }
        }
        override suspend fun getAllPlansSync(): List<SavingsTransferPlanEntity> = plans.values.toList()
    }

    private class FakeAccountDao : AccountDao {
        val accounts = mutableMapOf<Long, AccountEntity>()
        private var nextId = 1L

        override suspend fun insert(account: AccountEntity): Long {
            val id = if (account.id == 0L) nextId++ else account.id
            accounts[id] = account.copy(id = id)
            return id
        }
        override suspend fun insertAll(accounts: List<AccountEntity>): List<Long> = accounts.map { insert(it) }
        override suspend fun update(account: AccountEntity) { accounts[account.id] = account }
        override suspend fun getById(id: Long): AccountEntity? = accounts[id]
        override fun getAllActiveAccounts(): Flow<List<AccountEntity>> = emptyFlow()
        override suspend fun getAllAccountsSync(): List<AccountEntity> = accounts.values.toList()
        override suspend fun findByLast4(last4: String): AccountEntity? = accounts.values.find { it.identifierLast4 == last4 }
        override suspend fun findAllActiveByLast4(last4: String): List<AccountEntity> = accounts.values.filter { it.identifierLast4 == last4 && it.isActive }
        override suspend fun getActiveBankAccounts(): List<AccountEntity> = accounts.values.filter { it.type == AccountType.BANK && it.isActive }
    }

    private class FakeBudgetPeriodDao : BudgetPeriodDao {
        val periods = mutableMapOf<Long, BudgetPeriodEntity>()
        private var nextId = 1L

        override suspend fun insert(budgetPeriod: BudgetPeriodEntity): Long {
            val id = if (budgetPeriod.id == 0L) nextId++ else budgetPeriod.id
            periods[id] = budgetPeriod.copy(id = id)
            return id
        }
        override suspend fun update(budgetPeriod: BudgetPeriodEntity) { periods[budgetPeriod.id] = budgetPeriod }
        override suspend fun getById(id: Long): BudgetPeriodEntity? = periods[id]
        override suspend fun getByYearMonth(year: Int, month: Int): BudgetPeriodEntity? =
            periods.values.find { it.year == year && it.month == month }
        override fun observeByYearMonth(year: Int, month: Int): Flow<BudgetPeriodEntity?> = emptyFlow()
        override fun getAllPeriods(): Flow<List<BudgetPeriodEntity>> = emptyFlow()
        override suspend fun getAllPeriodsSync(): List<BudgetPeriodEntity> = periods.values.toList()
        override suspend fun getPeriodForTimestamp(timestamp: Long): BudgetPeriodEntity? =
            periods.values.find { timestamp in it.startAt until it.endAt }
        override suspend fun getEarliestPeriod(): BudgetPeriodEntity? = periods.values.minByOrNull { it.startAt }
        override suspend fun getLatestPeriod(): BudgetPeriodEntity? = periods.values.maxByOrNull { it.startAt }
        override suspend fun getOpenPeriodForTimestamp(timestamp: Long): BudgetPeriodEntity? =
            periods.values.find { it.status == BudgetPeriodStatus.OPEN && timestamp in it.startAt until it.endAt }
        override suspend fun getFirstOpenPeriod(): BudgetPeriodEntity? =
            periods.values.filter { it.status == BudgetPeriodStatus.OPEN }.minByOrNull { it.startAt }
        override suspend fun getClosedPeriodEndingAfter(startOfDayTimestamp: Long): BudgetPeriodEntity? =
            periods.values.find { it.status == BudgetPeriodStatus.CLOSED && it.endAt > startOfDayTimestamp }
        override suspend fun getClosedPeriodForTimestamp(timestamp: Long): BudgetPeriodEntity? =
            periods.values.find { it.status == BudgetPeriodStatus.CLOSED && timestamp in it.startAt until it.endAt }
    }

    private class FakeTransactionDao : TransactionDao {
        val storage = mutableMapOf<Long, TransactionEntity>()
        private var nextId = 1L

        override suspend fun insert(transaction: TransactionEntity): Long {
            val id = if (transaction.id == 0L) nextId++ else transaction.id
            storage[id] = transaction.copy(id = id)
            return id
        }
        override suspend fun insertAll(transactions: List<TransactionEntity>): List<Long> = transactions.map { insert(it) }
        override suspend fun update(transaction: TransactionEntity) { storage[transaction.id] = transaction }
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
        override suspend fun findBySourceFingerprint(fingerprint: String): TransactionEntity? = null
        override suspend fun findByUpiReference(upiRef: String): TransactionEntity? = null
        override suspend fun findByExactKey(accountId: Long, upiRef: String, direction: TransactionDirection, amountPaise: Long): TransactionEntity? = null
        override suspend fun findByExactKeyUnassigned(upiRef: String, direction: TransactionDirection, amountPaise: Long): TransactionEntity? = null
        override suspend fun findByUpiDirectionAmountAcrossAllAccounts(upiRef: String, direction: TransactionDirection, amountPaise: Long): TransactionEntity? = null
        override suspend fun findFuzzyCandidates(accountId: Long?, direction: TransactionDirection, amountPaise: Long, startWindow: Long, endWindow: Long): List<TransactionEntity> = emptyList()
        override suspend fun findTransferPairCandidates(excludeAccountId: Long, oppositeDirection: TransactionDirection, amountPaise: Long, startWindow: Long, endWindow: Long): List<TransactionEntity> = emptyList()
        override suspend fun getTransferLegs(transferGroupId: String): List<TransactionEntity> = storage.values.filter { it.transferGroupId == transferGroupId }
        override suspend fun getRefundsForTransaction(originalTransactionId: Long): List<TransactionEntity> = emptyList()
        override suspend fun getTransactionsForAccountSync(accountId: Long): List<TransactionEntity> = storage.values.filter { it.accountId == accountId }
    }

    private class FakeBudgetAllocationDao : BudgetAllocationDao() {
        val allocations = mutableListOf<BudgetAllocationEntity>()
        private var nextId = 1L

        override suspend fun getAdjustmentsForPeriod(periodId: Long): List<BudgetAdjustmentEntity> = emptyList()
        override suspend fun getAdvancesForPeriod(periodId: Long): List<com.tapledger.core.database.entity.AdvanceLedgerEntity> = emptyList()
        override suspend fun insertInternal(allocation: BudgetAllocationEntity): Long {
            val id = if (allocation.id == 0L) nextId++ else allocation.id
            allocations.add(allocation.copy(id = id))
            return id
        }
        override suspend fun insertAllInternal(allocations: List<BudgetAllocationEntity>): List<Long> = allocations.map { insertInternal(it) }
        override suspend fun updateInternal(allocation: BudgetAllocationEntity) {
            val idx = allocations.indexOfFirst { it.id == allocation.id }
            if (idx >= 0) allocations[idx] = allocation
        }
        override fun observeAllocationsForPeriod(periodId: Long): Flow<List<BudgetAllocationEntity>> = emptyFlow()
        override suspend fun getAllocationsForPeriodSync(periodId: Long): List<BudgetAllocationEntity> = allocations.filter { it.budgetPeriodId == periodId }
        override suspend fun getAllAllocationsSync(): List<BudgetAllocationEntity> = allocations
        override suspend fun getAllocation(periodId: Long, categoryId: Long): BudgetAllocationEntity? =
            allocations.find { it.budgetPeriodId == periodId && it.categoryId == categoryId }
        override suspend fun deleteAllocation(periodId: Long, categoryId: Long) {
            allocations.removeAll { it.budgetPeriodId == periodId && it.categoryId == categoryId }
        }
    }

    private class FakeCategoryDao : CategoryDao {
        val categories = mutableMapOf<Long, CategoryEntity>()
        override suspend fun insert(category: CategoryEntity): Long {
            categories[category.id] = category
            return category.id
        }
        override suspend fun insertAll(categories: List<CategoryEntity>): List<Long> {
            categories.forEach { insert(it) }
            return categories.map { it.id }
        }
        override suspend fun update(category: CategoryEntity) { categories[category.id] = category }
        override suspend fun getById(id: Long): CategoryEntity? = categories[id]
        override fun getAllActiveCategories(): Flow<List<CategoryEntity>> = emptyFlow()
        override suspend fun getAllCategoriesSync(): List<CategoryEntity> = categories.values.toList()
        override suspend fun getByName(name: String): CategoryEntity? = categories.values.find { it.name == name }
        override suspend fun getMaxSortOrder(): Int? = categories.values.maxOfOrNull { it.defaultSortOrder }
    }

    private class FakeBudgetAdjustmentDao : BudgetAdjustmentDao {
        override suspend fun insert(adjustment: BudgetAdjustmentEntity): Long = 1L
        override suspend fun getAdjustmentsForPeriodSync(periodId: Long): List<BudgetAdjustmentEntity> = emptyList()
        override suspend fun getAllAdjustmentsSync(): List<BudgetAdjustmentEntity> = emptyList()
        override fun observeAdjustmentsForPeriod(periodId: Long): Flow<List<BudgetAdjustmentEntity>> = emptyFlow()
    }

    private class FakeRecurringCommitmentDao : RecurringCommitmentDao {
        val commitments = mutableListOf<RecurringCommitmentEntity>()
        override suspend fun insert(commitment: RecurringCommitmentEntity): Long = 1L
        override suspend fun update(commitment: RecurringCommitmentEntity) {}
        override suspend fun getById(id: Long): RecurringCommitmentEntity? = commitments.find { it.id == id }
        override fun getByIdFlow(id: Long): Flow<RecurringCommitmentEntity?> = flowOf(commitments.find { it.id == id })
        override fun getAllActiveFlow(): Flow<List<RecurringCommitmentEntity>> = flowOf(commitments.filter { it.active })
        override suspend fun getAllActiveSync(): List<RecurringCommitmentEntity> = commitments.filter { it.active }
        override fun getAllFlow(): Flow<List<RecurringCommitmentEntity>> = flowOf(commitments)
        override suspend fun getAllSync(): List<RecurringCommitmentEntity> = commitments
        override suspend fun updateActiveStatus(id: Long, active: Boolean) {}
        override suspend fun delete(id: Long) {}
    }

    private class FakeCommitmentPaymentDao : CommitmentPaymentDao {
        val payments = mutableListOf<CommitmentPaymentEntity>()
        override suspend fun insert(payment: CommitmentPaymentEntity): Long = 1L
        override suspend fun insertAll(payments: List<CommitmentPaymentEntity>): List<Long> = emptyList()
        override suspend fun update(payment: CommitmentPaymentEntity) {}
        override suspend fun getById(id: Long): CommitmentPaymentEntity? = payments.find { it.id == id }
        override fun getPaymentsForPeriodFlow(periodId: Long): Flow<List<CommitmentPaymentEntity>> = emptyFlow()
        override suspend fun getPaymentsForPeriodSync(periodId: Long): List<CommitmentPaymentEntity> = payments.filter { it.periodId == periodId }
        override suspend fun getPaymentForCommitmentAndPeriod(commitmentId: Long, periodId: Long): CommitmentPaymentEntity? =
            payments.find { it.commitmentId == commitmentId && it.periodId == periodId }
        override suspend fun markPaid(commitmentId: Long, periodId: Long, transactionId: Long, status: CommitmentPaymentStatus) {}
        override suspend fun deleteForPeriod(periodId: Long) {}
    }

    private class FakeAuditLogDao : AuditLogDao {
        val logs = mutableListOf<AuditLogEntity>()
        override suspend fun insert(entry: AuditLogEntity): Long {
            logs.add(entry)
            return entry.id
        }
        override fun observeForTransaction(transactionId: Long): Flow<List<AuditLogEntity>> = emptyFlow()
        override suspend fun getForTransactionSync(transactionId: Long): List<AuditLogEntity> = logs.filter { it.transactionId == transactionId }
        override fun observeRecentAudits(limit: Int): Flow<List<AuditLogEntity>> = emptyFlow()
    }

    private class FakeTransactionAllocationDao : TransactionAllocationDao {
        val allocations = mutableListOf<TransactionAllocationEntity>()
        override suspend fun insert(allocation: TransactionAllocationEntity): Long {
            allocations.add(allocation)
            return allocation.id
        }
        override suspend fun insertAll(allocations: List<TransactionAllocationEntity>): List<Long> {
            this.allocations.addAll(allocations)
            return allocations.map { it.id }
        }
        override suspend fun update(allocation: TransactionAllocationEntity) {}
        override suspend fun deleteForTransaction(transactionId: Long) {
            allocations.removeAll { it.transactionId == transactionId }
        }
        override suspend fun getForTransaction(transactionId: Long): List<TransactionAllocationEntity> = allocations.filter { it.transactionId == transactionId }
        override fun observeForTransaction(transactionId: Long): Flow<List<TransactionAllocationEntity>> = flowOf(allocations.filter { it.transactionId == transactionId })
        override suspend fun getAllocationsInPeriod(startEpochMillis: Long, endEpochMillis: Long): List<TransactionAllocationEntity> = allocations
        override suspend fun getAllocationsForCategoryInPeriod(categoryId: Long, startEpochMillis: Long, endEpochMillis: Long): List<TransactionAllocationEntity> = allocations.filter { it.categoryId == categoryId }
        override suspend fun getAllTransactionAllocationsSync(): List<TransactionAllocationEntity> = allocations
    }

    private lateinit var planDao: FakeSavingsTransferPlanDao
    private lateinit var accountDao: FakeAccountDao
    private lateinit var budgetPeriodDao: FakeBudgetPeriodDao
    private lateinit var transactionDao: FakeTransactionDao
    private lateinit var budgetAllocationDao: FakeBudgetAllocationDao
    private lateinit var categoryDao: FakeCategoryDao
    private lateinit var budgetAdjustmentDao: FakeBudgetAdjustmentDao
    private lateinit var recurringCommitmentDao: FakeRecurringCommitmentDao
    private lateinit var commitmentPaymentDao: FakeCommitmentPaymentDao
    private lateinit var auditLogDao: FakeAuditLogDao
    private lateinit var transactionAllocationDao: FakeTransactionAllocationDao
    private lateinit var budgetRepository: BudgetRepository
    private lateinit var transactionRepository: TransactionRepository
    private lateinit var transferPlanRepository: TransferPlanRepository
    private lateinit var preferences: SavingsGuidePreferences
    private lateinit var coordinator: SavingsTriggerCoordinator

    private val startAt = 1727740800000L // 2024-10-01 00:00:00 IST
    private val endAt = 1730419200000L   // 2024-11-01 00:00:00 IST

    @Before
    fun setUp() {
        val txRunner = DirectTransactionRunner()
        planDao = FakeSavingsTransferPlanDao()
        accountDao = FakeAccountDao()
        budgetPeriodDao = FakeBudgetPeriodDao()
        transactionDao = FakeTransactionDao()
        budgetAllocationDao = FakeBudgetAllocationDao()
        categoryDao = FakeCategoryDao()
        budgetAdjustmentDao = FakeBudgetAdjustmentDao()
        recurringCommitmentDao = FakeRecurringCommitmentDao()
        commitmentPaymentDao = FakeCommitmentPaymentDao()
        auditLogDao = FakeAuditLogDao()
        transactionAllocationDao = FakeTransactionAllocationDao()

        budgetRepository = BudgetRepository(
            transactionRunner = txRunner,
            categoryDao = categoryDao,
            budgetPeriodDao = budgetPeriodDao,
            budgetAllocationDao = budgetAllocationDao,
            budgetAdjustmentDao = budgetAdjustmentDao
        )

        transactionRepository = TransactionRepository(
            transactionRunner = txRunner,
            transactionDao = transactionDao,
            transactionAllocationDao = transactionAllocationDao,
            auditLogDao = auditLogDao,
            accountDao = accountDao,
            categoryDao = categoryDao,
            budgetPeriodDao = budgetPeriodDao
        )

        transferPlanRepository = TransferPlanRepository(
            savingsTransferPlanDao = planDao,
            transactionDao = transactionDao,
            budgetPeriodDao = budgetPeriodDao,
            auditLogDao = auditLogDao,
            transactionRepository = transactionRepository,
            transactionRunner = txRunner
        )

        val fakeSharedPrefs = com.tapledger.core.security.FakeSharedPreferences()
        preferences = SavingsGuidePreferences(prefs = fakeSharedPrefs)

        coordinator = SavingsTriggerCoordinator(
            savingsTransferPlanDao = planDao,
            transferPlanRepository = transferPlanRepository,
            budgetPeriodDao = budgetPeriodDao,
            accountDao = accountDao,
            transactionDao = transactionDao,
            transactionAllocationDao = transactionAllocationDao,
            budgetRepository = budgetRepository,
            recurringCommitmentDao = recurringCommitmentDao,
            commitmentPaymentDao = commitmentPaymentDao,
            savingsGuidePreferences = preferences,
            spareCalculator = SpareCalculator(),
            context = null // null context disables Android notification dispatch in JVM unit tests
        )

        runBlocking {
            // Seed Period
            budgetPeriodDao.insert(
                BudgetPeriodEntity(
                    id = 1L,
                    year = 2024,
                    month = 10,
                    startAt = startAt,
                    endAt = endAt,
                    incomePaise = 100_000_00L, // ₹1,00,000 planned income
                    status = BudgetPeriodStatus.OPEN,
                    createdAt = startAt,
                    updatedAt = startAt
                )
            )

            // Seed HDFC Spending
            accountDao.insert(
                AccountEntity(
                    id = 1L,
                    name = "HDFC Spending",
                    type = AccountType.BANK,
                    identifierLast4 = "1234",
                    openingBalancePaise = 60_000_00L,
                    openingBalanceDate = startAt,
                    isActive = true,
                    createdAt = startAt,
                    updatedAt = startAt
                )
            )

            // Seed SBI Savings
            accountDao.insert(
                AccountEntity(
                    id = 2L,
                    name = "SBI Savings",
                    type = AccountType.BANK,
                    identifierLast4 = "5678",
                    openingBalancePaise = 10_000_00L,
                    openingBalanceDate = startAt,
                    isActive = true,
                    createdAt = startAt,
                    updatedAt = startAt
                )
            )

            // Seed Needs category
            categoryDao.insert(
                CategoryEntity(
                    id = 10L,
                    name = "Rent & Bills",
                    defaultBucket = BudgetBucket.NEEDS,
                    defaultSortOrder = 1,
                    defaultAdvancePolicy = AdvancePolicy.EXPIRE
                )
            )
            budgetAllocationDao.insert(
                BudgetAllocationEntity(
                    budgetPeriodId = 1L,
                    categoryId = 10L,
                    categoryNameSnapshot = "Rent & Bills",
                    bucketSnapshot = BudgetBucket.NEEDS,
                    sortOrderSnapshot = 1,
                    advancePolicySnapshot = AdvancePolicy.EXPIRE,
                    amountPaise = 30_000_00L,
                    createdAt = startAt,
                    updatedAt = startAt
                )
            )

            // Seed Savings category
            categoryDao.insert(
                CategoryEntity(
                    id = 50L,
                    name = "Emergency Fund",
                    defaultBucket = BudgetBucket.SAVINGS,
                    defaultSortOrder = 2,
                    defaultAdvancePolicy = AdvancePolicy.SWEEP_TO_SAVINGS
                )
            )
            budgetAllocationDao.insert(
                BudgetAllocationEntity(
                    budgetPeriodId = 1L,
                    categoryId = 50L,
                    categoryNameSnapshot = "Emergency Fund",
                    bucketSnapshot = BudgetBucket.SAVINGS,
                    sortOrderSnapshot = 2,
                    advancePolicySnapshot = AdvancePolicy.SWEEP_TO_SAVINGS,
                    amountPaise = 20_000_00L,
                    createdAt = startAt,
                    updatedAt = startAt
                )
            )

            preferences.updateConfig(
                SavingsGuideConfig(
                    spendingAccountId = 1L,
                    savingsAccountId = 2L,
                    bufferPaise = 2_000_00L,
                    paydayThresholdPct = 50,
                    sweepWindowDays = 3,
                    minSweepPaise = 50_000L,
                    notificationsEnabled = true
                )
            )
        }
    }

    @Test
    fun testPaydayTrigger_confirmedIncomeExceedsThreshold_createsPaydayPlan() = runBlocking {
        val paydayTx = TransactionEntity(
            id = 101L,
            accountId = 1L,
            kind = TransactionKind.INCOME,
            direction = TransactionDirection.IN,
            amountPaise = 60_000_00L, // ₹60,000 >= 50% of ₹1,00,000
            occurredAt = startAt + 86_400_000L,
            status = TransactionStatus.CONFIRMED,
            source = TransactionSource.MANUAL,
            createdAt = startAt + 86_400_000L,
            updatedAt = startAt + 86_400_000L
        )

        val plan = coordinator.onIncomeConfirmed(paydayTx, nowEpochMs = startAt + 86_400_000L)
        assertThat(plan).isNotNull()
        assertThat(plan?.kind).isEqualTo(TransferPlanKind.PAYDAY)
        assertThat(plan?.status).isEqualTo(TransferPlanStatus.SUGGESTED)
        assertThat(plan?.triggerTransactionId).isEqualTo(101L)
        assertThat(plan?.suggestedPaise).isGreaterThan(0L)

        // Second payday income in same period updates existing plan idempotently
        val secondPaydayTx = paydayTx.copy(id = 102L, amountPaise = 70_000_00L)
        val updatedPlan = coordinator.onIncomeConfirmed(secondPaydayTx, nowEpochMs = startAt + 100_000_000L)
        assertThat(updatedPlan?.id).isEqualTo(plan?.id)
        assertThat(planDao.plans).hasSize(1)
    }

    @Test
    fun testPaydayTrigger_incomeBelowThreshold_doesNotTriggerPlan() = runBlocking {
        val smallIncomeTx = TransactionEntity(
            id = 103L,
            accountId = 1L,
            kind = TransactionKind.INCOME,
            direction = TransactionDirection.IN,
            amountPaise = 20_000_00L, // ₹20,000 < 50% threshold
            occurredAt = startAt + 86_400_000L,
            status = TransactionStatus.CONFIRMED,
            source = TransactionSource.MANUAL,
            createdAt = startAt + 86_400_000L,
            updatedAt = startAt + 86_400_000L
        )

        val plan = coordinator.onIncomeConfirmed(smallIncomeTx, nowEpochMs = startAt + 86_400_000L)
        assertThat(plan).isNull()
        assertThat(planDao.plans).isEmpty()
    }

    @Test
    fun testPaydayTrigger_closedPeriod_doesNotTriggerPlan() = runBlocking {
        budgetPeriodDao.periods[1L] = budgetPeriodDao.periods[1L]!!.copy(status = BudgetPeriodStatus.CLOSED)

        val paydayTx = TransactionEntity(
            id = 104L,
            accountId = 1L,
            kind = TransactionKind.INCOME,
            direction = TransactionDirection.IN,
            amountPaise = 80_000_00L,
            occurredAt = startAt + 86_400_000L,
            status = TransactionStatus.CONFIRMED,
            source = TransactionSource.MANUAL,
            createdAt = startAt + 86_400_000L,
            updatedAt = startAt + 86_400_000L
        )

        val plan = coordinator.onIncomeConfirmed(paydayTx, nowEpochMs = startAt + 86_400_000L)
        assertThat(plan).isNull()
        assertThat(planDao.plans).isEmpty()
    }

    @Test
    fun testMonthEndSweep_withinLast3Days_createsSweepPlan() = runBlocking {
        // 2 days before endAt (within 3 days window)
        val nearEndMs = endAt - (2L * 86_400_000L)

        val sweepPlan = coordinator.runMonthEndSweepCheck(nowEpochMs = nearEndMs)
        assertThat(sweepPlan).isNotNull()
        assertThat(sweepPlan?.kind).isEqualTo(TransferPlanKind.SWEEP)
        assertThat(sweepPlan?.status).isEqualTo(TransferPlanStatus.SUGGESTED)
        assertThat(sweepPlan?.suggestedPaise).isAtLeast(50_000L) // >= ₹500
    }

    @Test
    fun testMonthEndSweep_outsideWindow_doesNotTrigger() = runBlocking {
        // 10 days before endAt (outside 3 days window)
        val earlyMs = endAt - (10L * 86_400_000L)

        val sweepPlan = coordinator.runMonthEndSweepCheck(nowEpochMs = earlyMs)
        assertThat(sweepPlan).isNull()
    }

    @Test
    fun testReminders_suggestedPlanAfter2Days_firesReminderUpToMax2() = runBlocking {
        val now = startAt + (5L * 86_400_000L)
        val plan = SavingsTransferPlanEntity(
            id = 201L,
            periodId = 1L,
            kind = TransferPlanKind.PAYDAY,
            sourceAccountId = 1L,
            destAccountId = 2L,
            suggestedPaise = 10_000_00L,
            status = TransferPlanStatus.SUGGESTED,
            createdAt = now - (3L * 86_400_000L) // 3 days ago (> 2 days)
        )
        planDao.insert(plan)

        assertThat(preferences.getPlanNotificationCount(201L)).isEqualTo(0)

        // First check: increments notif count from 0 to 1
        coordinator.runPendingRemindersCheck(nowEpochMs = now)
        assertThat(preferences.getPlanNotificationCount(201L)).isEqualTo(1)

        // Second check: increments from 1 to 2
        coordinator.runPendingRemindersCheck(nowEpochMs = now)
        assertThat(preferences.getPlanNotificationCount(201L)).isEqualTo(2)

        // Third check: capped at 2, should not increment further
        coordinator.runPendingRemindersCheck(nowEpochMs = now)
        assertThat(preferences.getPlanNotificationCount(201L)).isEqualTo(2)
    }

    @Test
    fun testUnverifiedNudge_markedMovedAfter3Days_firesOnce() = runBlocking {
        val now = startAt + (6L * 86_400_000L)
        val plan = SavingsTransferPlanEntity(
            id = 202L,
            periodId = 1L,
            kind = TransferPlanKind.PAYDAY,
            sourceAccountId = 1L,
            destAccountId = 2L,
            suggestedPaise = 10_000_00L,
            movedPaise = 10_000_00L,
            status = TransferPlanStatus.MARKED_MOVED,
            transferGroupId = "xfer-202",
            movedAt = now - (4L * 86_400_000L), // 4 days ago (> 3 days)
            createdAt = now - (4L * 86_400_000L)
        )
        planDao.insert(plan)

        assertThat(preferences.hasSentUnverifiedNudge(202L)).isFalse()

        // First check: sends nudge and flags preference
        coordinator.runPendingRemindersCheck(nowEpochMs = now)
        assertThat(preferences.hasSentUnverifiedNudge(202L)).isTrue()

        // Second check: already sent, remains true without duplicate firing
        coordinator.runPendingRemindersCheck(nowEpochMs = now)
        assertThat(preferences.hasSentUnverifiedNudge(202L)).isTrue()
    }

    @Test
    fun testDipGuard_outboundDebitFromSavingsAccount_triggersDipGuard() = runBlocking {
        val debitTx = TransactionEntity(
            id = 701L,
            accountId = 2L, // Savings account (SBI)
            kind = TransactionKind.EXPENSE,
            direction = TransactionDirection.OUT,
            amountPaise = 3_500_00L,
            occurredAt = startAt + 1000L,
            status = TransactionStatus.CONFIRMED,
            source = TransactionSource.MANUAL,
            transferGroupId = null, // Direct debit, not a transfer
            createdAt = startAt + 1000L,
            updatedAt = startAt + 1000L
        )

        val triggered = coordinator.onSavingsDebitDetected(debitTx)
        assertThat(triggered).isTrue()
    }

    @Test
    fun testDipGuard_transferLegFromSavings_doesNotTriggerDipGuard() = runBlocking {
        val transferTx = TransactionEntity(
            id = 702L,
            accountId = 2L, // Savings account (SBI)
            kind = TransactionKind.TRANSFER,
            direction = TransactionDirection.OUT,
            amountPaise = 5_000_00L,
            occurredAt = startAt + 1000L,
            status = TransactionStatus.CONFIRMED,
            source = TransactionSource.MANUAL,
            transferGroupId = "xfer-702", // Valid transfer leg
            createdAt = startAt + 1000L,
            updatedAt = startAt + 1000L
        )

        val triggered = coordinator.onSavingsDebitDetected(transferTx)
        assertThat(triggered).isFalse()
    }

    @Test
    fun testDipGuard_debitFromSpendingAccount_doesNotTriggerDipGuard() = runBlocking {
        val spendingDebitTx = TransactionEntity(
            id = 703L,
            accountId = 1L, // Spending account (HDFC)
            kind = TransactionKind.EXPENSE,
            direction = TransactionDirection.OUT,
            amountPaise = 1_200_00L,
            occurredAt = startAt + 1000L,
            status = TransactionStatus.CONFIRMED,
            source = TransactionSource.MANUAL,
            transferGroupId = null,
            createdAt = startAt + 1000L,
            updatedAt = startAt + 1000L
        )

        val triggered = coordinator.onSavingsDebitDetected(spendingDebitTx)
        assertThat(triggered).isFalse()
    }
}
