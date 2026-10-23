package com.tapledger.domain.account

import com.google.common.truth.Truth.assertThat
import com.tapledger.core.database.DatabaseTransactionRunner
import com.tapledger.core.database.dao.AuditLogDao
import com.tapledger.core.database.dao.TransactionAllocationDao
import com.tapledger.core.database.dao.TransactionDao
import com.tapledger.core.database.entity.AccountEntity
import com.tapledger.core.database.entity.AuditLogEntity
import com.tapledger.core.database.entity.BudgetPeriodEntity
import com.tapledger.core.database.entity.CategoryEntity
import com.tapledger.core.database.entity.TransactionAllocationEntity
import com.tapledger.core.database.entity.TransactionEntity
import com.tapledger.core.model.AccountType
import com.tapledger.core.model.BudgetPeriodStatus
import com.tapledger.core.model.TransactionDirection
import com.tapledger.core.model.TransactionKind
import com.tapledger.core.model.TransactionStatus
import com.tapledger.core.util.DateTimeUtils
import com.tapledger.data.repository.TransactionRepository
import com.tapledger.domain.calculator.AccountBalanceCalculator
import com.tapledger.domain.onboarding.OnboardingDomainTest
import com.tapledger.domain.validator.DataIntegrityChecker
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import java.time.LocalDate

class CorrectBalanceTest {

    private lateinit var fakeAccountDao: OnboardingDomainTest.FakeAccountDao
    private lateinit var fakeBudgetPeriodDao: OnboardingDomainTest.FakeBudgetPeriodDao
    private lateinit var fakeCategoryDao: OnboardingDomainTest.FakeCategoryDao
    private lateinit var fakeTransactionDao: FakeTransactionDao
    private lateinit var fakeAllocationDao: FakeTransactionAllocationDao
    private lateinit var fakeAuditLogDao: FakeAuditLogDao
    private lateinit var repository: TransactionRepository
    private val dataIntegrityChecker = DataIntegrityChecker()

    private class DirectTxRunner : DatabaseTransactionRunner {
        override suspend fun <R> run(block: suspend () -> R): R = block()
    }

    class FakeTransactionDao : TransactionDao {
        val storage = mutableMapOf<Long, TransactionEntity>()
        private var nextId = 1L

        override suspend fun insert(transaction: TransactionEntity): Long {
            val id = if (transaction.id == 0L) nextId++ else transaction.id
            storage[id] = transaction.copy(id = id)
            return id
        }

        override suspend fun insertAll(transactions: List<TransactionEntity>): List<Long> =
            transactions.map { insert(it) }

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

    class FakeTransactionAllocationDao : TransactionAllocationDao {
        val allocations = mutableListOf<TransactionAllocationEntity>()
        private var nextId = 1L

        override suspend fun insert(allocation: TransactionAllocationEntity): Long {
            val id = if (allocation.id == 0L) nextId++ else allocation.id
            allocations.add(allocation.copy(id = id))
            return id
        }

        override suspend fun insertAll(allocations: List<TransactionAllocationEntity>): List<Long> =
            allocations.map { insert(it) }

        override suspend fun update(allocation: TransactionAllocationEntity) {
            val idx = allocations.indexOfFirst { it.id == allocation.id }
            if (idx >= 0) allocations[idx] = allocation
        }

        override suspend fun deleteForTransaction(transactionId: Long) {
            allocations.removeAll { it.transactionId == transactionId }
        }

        override suspend fun getForTransaction(transactionId: Long): List<TransactionAllocationEntity> =
            allocations.filter { it.transactionId == transactionId }

        override fun observeForTransaction(transactionId: Long): Flow<List<TransactionAllocationEntity>> = emptyFlow()
        override suspend fun getAllocationsInPeriod(startEpochMillis: Long, endEpochMillis: Long): List<TransactionAllocationEntity> = emptyList()
        override suspend fun getAllocationsForCategoryInPeriod(categoryId: Long, startEpochMillis: Long, endEpochMillis: Long): List<TransactionAllocationEntity> = emptyList()
        override suspend fun getAllTransactionAllocationsSync(): List<TransactionAllocationEntity> = allocations
        override fun observeAllAllocations(): Flow<List<TransactionAllocationEntity>> = emptyFlow()
    }

    class FakeAuditLogDao : AuditLogDao {
        val logs = mutableListOf<AuditLogEntity>()
        private var nextId = 1L

        override suspend fun insert(entry: AuditLogEntity): Long {
            val id = if (entry.id == 0L) nextId++ else entry.id
            logs.add(entry.copy(id = id))
            return id
        }

        override fun observeForTransaction(transactionId: Long): Flow<List<AuditLogEntity>> = emptyFlow()
        override suspend fun getForTransactionSync(transactionId: Long): List<AuditLogEntity> =
            logs.filter { it.transactionId == transactionId }
        override fun observeRecentAudits(limit: Int): Flow<List<AuditLogEntity>> = emptyFlow()
    }

    @Before
    fun setUp() {
        fakeAccountDao = OnboardingDomainTest.FakeAccountDao()
        fakeBudgetPeriodDao = OnboardingDomainTest.FakeBudgetPeriodDao()
        fakeCategoryDao = OnboardingDomainTest.FakeCategoryDao()
        fakeTransactionDao = FakeTransactionDao()
        fakeAllocationDao = FakeTransactionAllocationDao()
        fakeAuditLogDao = FakeAuditLogDao()

        repository = TransactionRepository(
            transactionRunner = DirectTxRunner(),
            transactionDao = fakeTransactionDao,
            transactionAllocationDao = fakeAllocationDao,
            auditLogDao = fakeAuditLogDao,
            accountDao = fakeAccountDao,
            categoryDao = fakeCategoryDao,
            budgetPeriodDao = fakeBudgetPeriodDao
        )
    }

    @Test
    fun testPositiveDelta_inflowAdjustmentCorrectsBalance() {
        runBlocking {
            val today = LocalDate.now(DateTimeUtils.IST_ZONE)
            val openingDate = DateTimeUtils.getStartOfMonthEpochMillis(today.year, today.monthValue)

            val account = AccountEntity(
                id = 1L,
                name = "Bank Account",
                type = AccountType.BANK,
                identifierLast4 = "1234",
                openingBalancePaise = 10_000_00L, // Current starting = 10,000
                openingBalanceDate = openingDate,
                isActive = true,
                createdAt = 0L,
                updatedAt = 0L
            )
            fakeAccountDao.insert(account)

            // Target balance is 12,500. Delta = +2,500
            val targetPaise = 12_500_00L
            val currentPaise = 10_000_00L
            val delta = targetPaise - currentPaise

            val txId = repository.recordBalanceAdjustment(account.id, delta, "Corrected to passbook")
            assertThat(txId).isGreaterThan(0L)

            // Verify transaction properties per Flow.md §6.4
            val tx = fakeTransactionDao.getById(txId)!!
            assertThat(tx.kind).isEqualTo(TransactionKind.ADJUSTMENT)
            assertThat(tx.direction).isEqualTo(TransactionDirection.IN)
            assertThat(tx.amountPaise).isEqualTo(2_500_00L)
            assertThat(tx.status).isEqualTo(TransactionStatus.CONFIRMED)
            assertThat(tx.notes).isEqualTo("Corrected to passbook")

            // Allocations MUST be empty (zero rows)
            val allocs = fakeAllocationDao.getForTransaction(txId)
            assertThat(allocs).isEmpty()

            // Audit log recorded
            val auditLogs = fakeAuditLogDao.getForTransactionSync(txId)
            assertThat(auditLogs).isNotEmpty()

            // Verify new balance matches target
            val txs = fakeTransactionDao.getBalanceTransactionsForAccount(account.id, account.openingBalanceDate)
            val balanceSummary = AccountBalanceCalculator.calculateAccountBalance(account, txs, DateTimeUtils.nowEpochMillis())
            assertThat(balanceSummary.currentBalancePaise).isEqualTo(targetPaise)

            // Verify DataIntegrityChecker reports ZERO violations
            val issues = dataIntegrityChecker.verifyIntegrity(
                accounts = listOf(account),
                categories = emptyList(),
                periods = emptyList(),
                allocations = emptyList(),
                transactions = listOf(tx),
                transactionAllocations = emptyList(),
                monthCloses = emptyList()
            )
            assertThat(issues).isEmpty()
        }
    }

    @Test
    fun testNegativeDelta_outflowAdjustmentCorrectsBalance() {
        runBlocking {
            val today = LocalDate.now(DateTimeUtils.IST_ZONE)
            val openingDate = DateTimeUtils.getStartOfMonthEpochMillis(today.year, today.monthValue)

            val account = AccountEntity(
                id = 2L,
                name = "Cash",
                type = AccountType.CASH,
                identifierLast4 = null,
                openingBalancePaise = 5_000_00L,
                openingBalanceDate = openingDate,
                isActive = true,
                createdAt = 0L,
                updatedAt = 0L
            )
            fakeAccountDao.insert(account)

            // Target balance is 4,200. Delta = -800
            val targetPaise = 4_200_00L
            val delta = targetPaise - account.openingBalancePaise

            val txId = repository.recordBalanceAdjustment(account.id, delta)
            val tx = fakeTransactionDao.getById(txId)!!

            assertThat(tx.kind).isEqualTo(TransactionKind.ADJUSTMENT)
            assertThat(tx.direction).isEqualTo(TransactionDirection.OUT)
            assertThat(tx.amountPaise).isEqualTo(800_00L)
            assertThat(tx.status).isEqualTo(TransactionStatus.CONFIRMED)

            // Verify new balance
            val txs = fakeTransactionDao.getBalanceTransactionsForAccount(account.id, account.openingBalanceDate)
            val balanceSummary = AccountBalanceCalculator.calculateAccountBalance(account, txs, DateTimeUtils.nowEpochMillis())
            assertThat(balanceSummary.currentBalancePaise).isEqualTo(targetPaise)
        }
    }

    @Test
    fun testZeroDelta_rejected() {
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking {
                repository.recordBalanceAdjustment(1L, 0L)
            }
        }
    }

    @Test
    fun testStartingBalanceLock_enforcesRule5() {
        runBlocking {
            val account = AccountEntity(
                id = 3L,
                name = "Bank Locked",
                type = AccountType.BANK,
                identifierLast4 = "5555",
                openingBalancePaise = 10_000_00L,
                openingBalanceDate = 1_000_000L, // Jan 15
                isActive = true,
                createdAt = 0L,
                updatedAt = 0L
            )

            // No closed periods yet -> NOT locked
            val closedPeriodBefore = fakeBudgetPeriodDao.getClosedPeriodEndingAfter(
                DateTimeUtils.getStartOfDayEpochMillis(account.openingBalanceDate)
            )
            assertThat(closedPeriodBefore).isNull()

            // Period closes with endAt = 2_000_000L (> opening_balance_date)
            val closedPeriod = BudgetPeriodEntity(
                id = 1L,
                year = 2026,
                month = 1,
                startAt = 0L,
                endAt = 2_000_000L,
                incomePaise = 50_000_00L,
                status = BudgetPeriodStatus.CLOSED,
                createdAt = 0L,
                updatedAt = 0L
            )
            fakeBudgetPeriodDao.insert(closedPeriod)

            // Now locked per §6.1 rule 5
            val closedPeriodAfter = fakeBudgetPeriodDao.getClosedPeriodEndingAfter(
                DateTimeUtils.getStartOfDayEpochMillis(account.openingBalanceDate)
            )
            assertThat(closedPeriodAfter).isNotNull()
        }
    }
}
