package com.tapledger.data.repository

import com.google.common.truth.Truth.assertThat
import com.tapledger.core.database.DatabaseTransactionRunner
import com.tapledger.core.database.dao.AccountDao
import com.tapledger.core.database.dao.AuditLogDao
import com.tapledger.core.database.dao.BudgetPeriodDao
import com.tapledger.core.database.dao.CategoryDao
import com.tapledger.core.database.dao.SavingsTransferPlanDao
import com.tapledger.core.database.dao.TransactionAllocationDao
import com.tapledger.core.database.dao.TransactionDao
import com.tapledger.core.database.entity.AccountEntity
import com.tapledger.core.database.entity.AuditLogEntity
import com.tapledger.core.database.entity.BudgetPeriodEntity
import com.tapledger.core.database.entity.CategoryEntity
import com.tapledger.core.database.entity.SavingsTransferPlanEntity
import com.tapledger.core.database.entity.TransactionAllocationEntity
import com.tapledger.core.database.entity.TransactionEntity
import com.tapledger.core.model.AccountType
import com.tapledger.core.model.AdvancePolicy
import com.tapledger.core.model.AuditAction
import com.tapledger.core.model.BudgetBucket
import com.tapledger.core.model.BudgetPeriodStatus
import com.tapledger.core.model.TransactionDirection
import com.tapledger.core.model.TransactionKind
import com.tapledger.core.model.TransactionStatus
import com.tapledger.core.model.TransferPlanKind
import com.tapledger.core.model.TransferPlanStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test

class TransferPlanRepositoryTest {

    private class DirectTransactionRunner : DatabaseTransactionRunner {
        override suspend fun <T> run(block: suspend () -> T): T = block()
    }

    private class FakeSavingsTransferPlanDao : SavingsTransferPlanDao {
        val storage = mutableMapOf<Long, SavingsTransferPlanEntity>()
        private var nextId = 1L

        override suspend fun insert(plan: SavingsTransferPlanEntity): Long {
            val id = if (plan.id == 0L) nextId++ else plan.id
            storage[id] = plan.copy(id = id)
            return id
        }

        override suspend fun insertOrUpdate(plan: SavingsTransferPlanEntity): Long {
            val id = if (plan.id == 0L) nextId++ else plan.id
            storage[id] = plan.copy(id = id)
            return id
        }

        override suspend fun update(plan: SavingsTransferPlanEntity) {
            storage[plan.id] = plan
        }

        override suspend fun delete(plan: SavingsTransferPlanEntity) {
            storage.remove(plan.id)
        }

        override suspend fun getById(id: Long): SavingsTransferPlanEntity? = storage[id]
        override fun getByIdFlow(id: Long): Flow<SavingsTransferPlanEntity?> = flowOf(storage[id])

        override suspend fun getByPeriodAndKind(periodId: Long, kind: TransferPlanKind): SavingsTransferPlanEntity? {
            return storage.values.firstOrNull { it.periodId == periodId && it.kind == kind }
        }

        override fun getByPeriodAndKindFlow(periodId: Long, kind: TransferPlanKind): Flow<SavingsTransferPlanEntity?> {
            return flowOf(storage.values.firstOrNull { it.periodId == periodId && it.kind == kind })
        }

        override suspend fun getPlansForPeriod(periodId: Long): List<SavingsTransferPlanEntity> {
            return storage.values.filter { it.periodId == periodId }
        }

        override fun getPlansForPeriodFlow(periodId: Long): Flow<List<SavingsTransferPlanEntity>> {
            return flowOf(storage.values.filter { it.periodId == periodId })
        }

        override fun getActivePlansForPeriodFlow(periodId: Long): Flow<List<SavingsTransferPlanEntity>> {
            return flowOf(storage.values.filter {
                it.periodId == periodId &&
                    (it.status == TransferPlanStatus.SUGGESTED || it.status == TransferPlanStatus.MARKED_MOVED || it.status == TransferPlanStatus.VERIFIED)
            })
        }

        override suspend fun getPlansByStatus(status: TransferPlanStatus): List<SavingsTransferPlanEntity> {
            return storage.values.filter { it.status == status }
        }

        override fun getPlansByStatusFlow(status: TransferPlanStatus): Flow<List<SavingsTransferPlanEntity>> {
            return flowOf(storage.values.filter { it.status == status })
        }

        override suspend fun getByTransferGroupId(transferGroupId: String): SavingsTransferPlanEntity? {
            return storage.values.firstOrNull { it.transferGroupId == transferGroupId }
        }

        override suspend fun findMarkedMovedPlans(): List<SavingsTransferPlanEntity> {
            return storage.values.filter { it.status == TransferPlanStatus.MARKED_MOVED }
        }

        override fun findMarkedMovedPlansFlow(): Flow<List<SavingsTransferPlanEntity>> {
            return flowOf(storage.values.filter { it.status == TransferPlanStatus.MARKED_MOVED })
        }

        override suspend fun expireSuggestedPlansForPeriod(periodId: Long) {
            storage.values.filter { it.periodId == periodId && it.status == TransferPlanStatus.SUGGESTED }.forEach {
                storage[it.id] = it.copy(status = TransferPlanStatus.EXPIRED)
            }
        }

        override suspend fun getAllPlansSync(): List<SavingsTransferPlanEntity> =
            storage.values.toList()
    }

    private class FakeAuditLogDao : AuditLogDao {
        val logs = mutableListOf<AuditLogEntity>()
        private var nextId = 1L

        override suspend fun insert(entry: AuditLogEntity): Long {
            val id = if (entry.id == 0L) nextId++ else entry.id
            logs.add(entry.copy(id = id))
            return id
        }

        override fun observeForTransaction(transactionId: Long): Flow<List<AuditLogEntity>> = emptyFlow()
        override suspend fun getForTransactionSync(transactionId: Long): List<AuditLogEntity> = logs.filter { it.transactionId == transactionId }
        override fun observeRecentAudits(limit: Int): Flow<List<AuditLogEntity>> = emptyFlow()
    }

    private class FakeBudgetPeriodDao : BudgetPeriodDao {
        val periods = mutableMapOf<Long, BudgetPeriodEntity>()

        override suspend fun insert(budgetPeriod: BudgetPeriodEntity): Long {
            periods[budgetPeriod.id] = budgetPeriod
            return budgetPeriod.id
        }

        override suspend fun update(budgetPeriod: BudgetPeriodEntity) {
            periods[budgetPeriod.id] = budgetPeriod
        }

        override suspend fun getById(id: Long): BudgetPeriodEntity? = periods[id]
        override suspend fun getByYearMonth(year: Int, month: Int): BudgetPeriodEntity? = periods.values.firstOrNull { it.year == year && it.month == month }
        override fun observeByYearMonth(year: Int, month: Int): Flow<BudgetPeriodEntity?> = flowOf(periods.values.firstOrNull { it.year == year && it.month == month })
        override fun getAllPeriods(): Flow<List<BudgetPeriodEntity>> = flowOf(periods.values.toList())
        override suspend fun getAllPeriodsSync(): List<BudgetPeriodEntity> = periods.values.toList()
        override suspend fun getPeriodForTimestamp(timestamp: Long): BudgetPeriodEntity? = periods.values.firstOrNull { timestamp in it.startAt..it.endAt }
        override suspend fun getEarliestPeriod(): BudgetPeriodEntity? = periods.values.minByOrNull { it.startAt }
        override suspend fun getLatestPeriod(): BudgetPeriodEntity? = periods.values.maxByOrNull { it.startAt }
        override suspend fun getOpenPeriodForTimestamp(timestamp: Long): BudgetPeriodEntity? = periods.values.firstOrNull { it.status == BudgetPeriodStatus.OPEN && timestamp in it.startAt..it.endAt }
        override suspend fun getFirstOpenPeriod(): BudgetPeriodEntity? = periods.values.firstOrNull { it.status == BudgetPeriodStatus.OPEN }
        override suspend fun getClosedPeriodEndingAfter(startOfDayTimestamp: Long): BudgetPeriodEntity? = periods.values.firstOrNull { it.status == BudgetPeriodStatus.CLOSED && it.endAt > startOfDayTimestamp }
        override suspend fun getClosedPeriodForTimestamp(timestamp: Long): BudgetPeriodEntity? = periods.values.firstOrNull { it.status == BudgetPeriodStatus.CLOSED && timestamp in it.startAt..it.endAt }
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
        override suspend fun findByLast4(last4: String): AccountEntity? = accounts.values.firstOrNull { it.identifierLast4 == last4 && it.isActive }
        override suspend fun findAllActiveByLast4(last4: String): List<AccountEntity> = accounts.values.filter { it.identifierLast4 == last4 && it.isActive }
        override suspend fun getActiveBankAccounts(): List<AccountEntity> = accounts.values.filter { it.type == AccountType.BANK && it.isActive }
    }

    private class FakeCategoryDao : CategoryDao {
        val categories = mutableMapOf<Long, CategoryEntity>()
        private var nextId = 1L

        override suspend fun insert(category: CategoryEntity): Long {
            val id = if (category.id == 0L) nextId++ else category.id
            categories[id] = category.copy(id = id)
            return id
        }

        override suspend fun insertAll(categories: List<CategoryEntity>): List<Long> = categories.map { insert(it) }
        override suspend fun update(category: CategoryEntity) { categories[category.id] = category }
        override suspend fun getById(id: Long): CategoryEntity? = categories[id]
        override fun getAllActiveCategories(): Flow<List<CategoryEntity>> = emptyFlow()
        override suspend fun getAllCategoriesSync(): List<CategoryEntity> = categories.values.toList()
        override suspend fun getByName(name: String): CategoryEntity? = categories.values.firstOrNull { it.name == name }
        override suspend fun getMaxSortOrder(): Int? = categories.values.maxOfOrNull { it.defaultSortOrder }
    }

    private class FakeTransactionAllocationDao : TransactionAllocationDao {
        val allocations = mutableListOf<TransactionAllocationEntity>()
        private var nextId = 1L

        override suspend fun insert(allocation: TransactionAllocationEntity): Long {
            val id = if (allocation.id == 0L) nextId++ else allocation.id
            allocations.add(allocation.copy(id = id))
            return id
        }

        override suspend fun insertAll(allocations: List<TransactionAllocationEntity>): List<Long> = allocations.map { insert(it) }
        override suspend fun update(allocation: TransactionAllocationEntity) {
            val idx = allocations.indexOfFirst { it.id == allocation.id }
            if (idx >= 0) allocations[idx] = allocation
        }
        override suspend fun deleteForTransaction(transactionId: Long) { allocations.removeAll { it.transactionId == transactionId } }
        override suspend fun getForTransaction(transactionId: Long): List<TransactionAllocationEntity> = allocations.filter { it.transactionId == transactionId }
        override fun observeForTransaction(transactionId: Long): Flow<List<TransactionAllocationEntity>> = emptyFlow()
        override suspend fun getAllocationsInPeriod(startEpochMillis: Long, endEpochMillis: Long): List<TransactionAllocationEntity> = emptyList()
        override suspend fun getAllocationsForCategoryInPeriod(categoryId: Long, startEpochMillis: Long, endEpochMillis: Long): List<TransactionAllocationEntity> = emptyList()
        override suspend fun getAllTransactionAllocationsSync(): List<TransactionAllocationEntity> = allocations.toList()
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
        override fun observeById(id: Long): Flow<TransactionEntity?> = flowOf(storage[id])
        override fun observeAllTransactions(): Flow<List<TransactionEntity>> = emptyFlow()
        override suspend fun getAllTransactionsSync(): List<TransactionEntity> = storage.values.toList()
        override fun observePendingTransactions(): Flow<List<TransactionEntity>> = emptyFlow()
        override fun observePendingCount(): Flow<Int> = emptyFlow()
        override suspend fun getPendingCountSync(): Int = storage.values.count { it.status == TransactionStatus.PENDING }
        override suspend fun getBalanceTransactionsForAccount(accountId: Long, sinceEpochMillis: Long): List<TransactionEntity> = storage.values.filter { it.accountId == accountId && it.occurredAt >= sinceEpochMillis }
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
        override suspend fun getTransferLegs(transferGroupId: String): List<TransactionEntity> = storage.values.filter { it.transferGroupId == transferGroupId }
        override suspend fun getRefundsForTransaction(originalTransactionId: Long): List<TransactionEntity> = emptyList()
        override suspend fun getTransactionsForAccountSync(accountId: Long): List<TransactionEntity> = storage.values.filter { it.accountId == accountId }
    }

    private lateinit var planDao: FakeSavingsTransferPlanDao
    private lateinit var auditDao: FakeAuditLogDao
    private lateinit var periodDao: FakeBudgetPeriodDao
    private lateinit var accountDao: FakeAccountDao
    private lateinit var categoryDao: FakeCategoryDao
    private lateinit var allocDao: FakeTransactionAllocationDao
    private lateinit var txDao: FakeTransactionDao
    private lateinit var txRepo: TransactionRepository
    private lateinit var repository: TransferPlanRepository

    @Before
    fun setUp() {
        val txRunner = DirectTransactionRunner()
        planDao = FakeSavingsTransferPlanDao()
        auditDao = FakeAuditLogDao()
        periodDao = FakeBudgetPeriodDao()
        accountDao = FakeAccountDao()
        categoryDao = FakeCategoryDao()
        allocDao = FakeTransactionAllocationDao()
        txDao = FakeTransactionDao()

        periodDao.periods[1L] = BudgetPeriodEntity(
            id = 1L,
            year = 2026,
            month = 10,
            startAt = 1000L,
            endAt = 2000L,
            incomePaise = 100_000_00L,
            status = BudgetPeriodStatus.OPEN,
            createdAt = 1000L,
            updatedAt = 1000L
        )

        accountDao.accounts[1L] = AccountEntity(
            id = 1L,
            name = "HDFC Spending",
            type = AccountType.BANK,
            identifierLast4 = "1234",
            openingBalancePaise = 50_000_00L,
            openingBalanceDate = 1000L,
            isSavings = false,
            isActive = true,
            createdAt = 1000L,
            updatedAt = 1000L
        )

        accountDao.accounts[2L] = AccountEntity(
            id = 2L,
            name = "SBI Savings",
            type = AccountType.BANK,
            identifierLast4 = "5678",
            openingBalancePaise = 100_000_00L,
            openingBalanceDate = 1000L,
            isSavings = true,
            isActive = true,
            createdAt = 1000L,
            updatedAt = 1000L
        )

        categoryDao.categories[50L] = CategoryEntity(
            id = 50L,
            name = "General Savings",
            defaultBucket = BudgetBucket.SAVINGS,
            defaultSortOrder = 1,
            defaultAdvancePolicy = AdvancePolicy.SWEEP_TO_SAVINGS,
            isActive = true,
            createdAt = 1000L,
            updatedAt = 1000L
        )

        txRepo = TransactionRepository(
            transactionRunner = txRunner,
            transactionDao = txDao,
            transactionAllocationDao = allocDao,
            auditLogDao = auditDao,
            accountDao = accountDao,
            categoryDao = categoryDao,
            budgetPeriodDao = periodDao
        )

        repository = TransferPlanRepository(
            savingsTransferPlanDao = planDao,
            transactionDao = txDao,
            budgetPeriodDao = periodDao,
            auditLogDao = auditDao,
            transactionRepository = txRepo,
            transactionRunner = txRunner
        )
    }

    @Test
    fun testIdempotentPlanCreation() {
        runBlocking {
            // First creation
            val planId1 = repository.createOrUpdateSuggestedPlan(
                periodId = 1L,
                kind = TransferPlanKind.PAYDAY,
                sourceAccountId = 1L,
                destAccountId = 2L,
                suggestedPaise = 12_000_00L
            )

            assertThat(planId1).isGreaterThan(0L)
            val plan1 = planDao.getById(planId1)
            assertThat(plan1?.suggestedPaise).isEqualTo(12_000_00L)
            assertThat(plan1?.status).isEqualTo(TransferPlanStatus.SUGGESTED)
            assertThat(auditDao.logs.any { it.action == AuditAction.PLAN_CREATED }).isTrue()

            // Second creation with updated amount updates existing plan without creating a duplicate
            val planId2 = repository.createOrUpdateSuggestedPlan(
                periodId = 1L,
                kind = TransferPlanKind.PAYDAY,
                sourceAccountId = 1L,
                destAccountId = 2L,
                suggestedPaise = 15_000_00L
            )

            assertThat(planId2).isEqualTo(planId1)
            assertThat(planDao.storage).hasSize(1)
            assertThat(planDao.getById(planId1)?.suggestedPaise).isEqualTo(15_000_00L)
        }
    }

    @Test
    fun testMarkAsMovedCreatesTransferPair() {
        runBlocking {
            val planId = repository.createOrUpdateSuggestedPlan(
                periodId = 1L,
                kind = TransferPlanKind.PAYDAY,
                sourceAccountId = 1L,
                destAccountId = 2L,
                suggestedPaise = 12_000_00L
            )

            val (outId, inId) = repository.markAsMoved(
                planId = planId,
                movedPaise = 12_000_00L,
                savingsCategoryId = 50L,
                movedAtEpochMs = 1500L
            )

            assertThat(outId).isGreaterThan(0L)
            assertThat(inId).isGreaterThan(0L)
            assertThat(outId).isNotEqualTo(inId)

            // Verify transfer legs in transactionDao
            val outLeg = txDao.getById(outId)
            val inLeg = txDao.getById(inId)
            assertThat(outLeg?.direction).isEqualTo(TransactionDirection.OUT)
            assertThat(outLeg?.status).isEqualTo(TransactionStatus.CATEGORIZED)
            assertThat(inLeg?.direction).isEqualTo(TransactionDirection.IN)
            assertThat(inLeg?.status).isEqualTo(TransactionStatus.CONFIRMED)
            assertThat(outLeg?.transferGroupId).isEqualTo(inLeg?.transferGroupId)

            // Verify allocation created for OUT leg targeting savings category
            val allocs = allocDao.getForTransaction(outId)
            assertThat(allocs).hasSize(1)
            assertThat(allocs[0].categoryId).isEqualTo(50L)
            assertThat(allocs[0].amountPaise).isEqualTo(12_000_00L)

            // Verify updated plan
            val updatedPlan = planDao.getById(planId)
            assertThat(updatedPlan?.status).isEqualTo(TransferPlanStatus.MARKED_MOVED)
            assertThat(updatedPlan?.movedPaise).isEqualTo(12_000_00L)
            assertThat(updatedPlan?.movedAt).isEqualTo(1500L)
            assertThat(updatedPlan?.transferGroupId).isEqualTo(outLeg?.transferGroupId)
            assertThat(auditDao.logs.any { it.action == AuditAction.PLAN_MOVED }).isTrue()
        }
    }

    @Test
    fun testVerifyPlanTransitionsToVerified() {
        runBlocking {
            val planId = repository.createOrUpdateSuggestedPlan(
                periodId = 1L,
                kind = TransferPlanKind.PAYDAY,
                sourceAccountId = 1L,
                destAccountId = 2L,
                suggestedPaise = 12_000_00L
            )

            repository.markAsMoved(
                planId = planId,
                movedPaise = 12_000_00L,
                savingsCategoryId = 50L,
                movedAtEpochMs = 1500L
            )

            repository.verifyPlan(planId = planId, verifiedAtEpochMs = 1600L)

            val verifiedPlan = planDao.getById(planId)
            assertThat(verifiedPlan?.status).isEqualTo(TransferPlanStatus.VERIFIED)
            assertThat(verifiedPlan?.verifiedAt).isEqualTo(1600L)
            assertThat(auditDao.logs.any { it.action == AuditAction.PLAN_ADOPTED }).isTrue()
        }
    }

    @Test
    fun testDismissPlan() {
        runBlocking {
            val planId = repository.createOrUpdateSuggestedPlan(
                periodId = 1L,
                kind = TransferPlanKind.PAYDAY,
                sourceAccountId = 1L,
                destAccountId = 2L,
                suggestedPaise = 12_000_00L
            )

            repository.dismissPlan(planId)

            val dismissedPlan = planDao.getById(planId)
            assertThat(dismissedPlan?.status).isEqualTo(TransferPlanStatus.DISMISSED)
            assertThat(auditDao.logs.any { it.action == AuditAction.PLAN_DISMISSED }).isTrue()
        }
    }

    @Test
    fun testExpireSuggestedPlansForPeriod() {
        runBlocking {
            repository.createOrUpdateSuggestedPlan(
                periodId = 1L,
                kind = TransferPlanKind.PAYDAY,
                sourceAccountId = 1L,
                destAccountId = 2L,
                suggestedPaise = 12_000_00L
            )

            repository.expireSuggestedPlansForPeriod(1L)

            val expired = repository.getByPeriodAndKindSync(1L, TransferPlanKind.PAYDAY)
            assertThat(expired?.status).isEqualTo(TransferPlanStatus.EXPIRED)
        }
    }

    @Test
    fun testIllegalTransitionsThrowException() {
        runBlocking {
            val planId = repository.createOrUpdateSuggestedPlan(
                periodId = 1L,
                kind = TransferPlanKind.PAYDAY,
                sourceAccountId = 1L,
                destAccountId = 2L,
                suggestedPaise = 12_000_00L
            )

            // Cannot verify a SUGGESTED plan directly without marking moved
            assertThrows(IllegalStateException::class.java) {
                runBlocking { repository.verifyPlan(planId) }
            }

            // Set to MARKED_MOVED then VERIFIED
            repository.markAsMoved(planId, 12_000_00L, 50L, 1500L)
            repository.verifyPlan(planId, 1600L)

            // Cannot dismiss an already VERIFIED plan
            assertThrows(IllegalStateException::class.java) {
                runBlocking { repository.dismissPlan(planId) }
            }
        }
    }

    @Test
    fun testClosedPeriodGuards() {
        runBlocking {
            periodDao.periods[2L] = BudgetPeriodEntity(
                id = 2L,
                year = 2026,
                month = 9,
                startAt = 100L,
                endAt = 900L,
                incomePaise = 100_000_00L,
                status = BudgetPeriodStatus.CLOSED,
                createdAt = 100L,
                updatedAt = 100L
            )

            assertThrows(IllegalStateException::class.java) {
                runBlocking {
                    repository.createOrUpdateSuggestedPlan(
                        periodId = 2L,
                        kind = TransferPlanKind.PAYDAY,
                        sourceAccountId = 1L,
                        destAccountId = 2L,
                        suggestedPaise = 10_000_00L
                    )
                }
            }
        }
    }
}

