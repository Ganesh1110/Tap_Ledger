package com.tapledger.domain.transfer

import com.google.common.truth.Truth.assertThat
import com.tapledger.core.database.DatabaseTransactionRunner
import com.tapledger.core.database.dao.AccountDao
import com.tapledger.core.database.dao.AuditLogDao
import com.tapledger.core.database.dao.BudgetPeriodDao
import com.tapledger.core.database.dao.CategoryDao
import com.tapledger.core.database.dao.TransactionAllocationDao
import com.tapledger.core.database.dao.TransactionDao
import com.tapledger.core.database.entity.AccountEntity
import com.tapledger.core.database.entity.AuditLogEntity
import com.tapledger.core.database.entity.BudgetPeriodEntity
import com.tapledger.core.database.entity.CategoryEntity
import com.tapledger.core.database.entity.TransactionAllocationEntity
import com.tapledger.core.database.entity.TransactionEntity
import com.tapledger.core.model.AccountType
import com.tapledger.core.model.BudgetBucket
import com.tapledger.core.model.BudgetPeriodStatus
import com.tapledger.core.model.TransactionDirection
import com.tapledger.core.model.TransactionKind
import com.tapledger.core.model.TransactionSource
import com.tapledger.core.model.TransactionStatus
import com.tapledger.data.repository.TransactionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import java.util.UUID

/**
 * Validates Flow.md §7.1.1, §7.1.2 & Amendment A2:
 * - Candidate transfer detection
 * - Exclusion of CLOSED periods from candidates (Amendment A2)
 * - Confirmation of candidate pairs (standard vs savings prompt)
 * - Manual transfer creation (Mark as Transfer)
 */
class TransferPairingTest {

    private class DirectTransactionRunner : DatabaseTransactionRunner {
        override suspend fun <R> run(block: suspend () -> R): R = block()
    }

    private class FakeAccountDao : AccountDao {
        val storage = mutableMapOf<Long, AccountEntity>()
        private var nextId = 1L

        override suspend fun insert(account: AccountEntity): Long {
            val id = if (account.id == 0L) nextId++ else account.id
            storage[id] = account.copy(id = id)
            return id
        }
        override suspend fun insertAll(accounts: List<AccountEntity>): List<Long> = accounts.map { insert(it) }
        override suspend fun update(account: AccountEntity) { storage[account.id] = account }
        override suspend fun getById(id: Long): AccountEntity? = storage[id]
        override fun getAllActiveAccounts(): Flow<List<AccountEntity>> = emptyFlow()
        override suspend fun getAllAccountsSync(): List<AccountEntity> = storage.values.toList()
        override suspend fun findByLast4(last4: String): AccountEntity? = storage.values.firstOrNull { it.identifierLast4 == last4 && it.isActive }
        override suspend fun findAllActiveByLast4(last4: String): List<AccountEntity> = storage.values.filter { it.identifierLast4 == last4 && it.isActive }
        override suspend fun getActiveBankAccounts(): List<AccountEntity> = storage.values.filter { it.type == AccountType.BANK && it.isActive }
    }

    private class FakeCategoryDao : CategoryDao {
        val storage = mutableMapOf<Long, CategoryEntity>()
        private var nextId = 1L

        override suspend fun insert(category: CategoryEntity): Long {
            val id = if (category.id == 0L) nextId++ else category.id
            storage[id] = category.copy(id = id)
            return id
        }
        override suspend fun insertAll(categories: List<CategoryEntity>): List<Long> = categories.map { insert(it) }
        override suspend fun update(category: CategoryEntity) { storage[category.id] = category }
        override suspend fun getById(id: Long): CategoryEntity? = storage[id]
        override fun getAllActiveCategories(): Flow<List<CategoryEntity>> = emptyFlow()
        override suspend fun getAllCategoriesSync(): List<CategoryEntity> = storage.values.toList()
        override suspend fun getByName(name: String): CategoryEntity? = storage.values.firstOrNull { it.name == name }
        override suspend fun getMaxSortOrder(): Int? = storage.values.maxOfOrNull { it.defaultSortOrder }
    }

    private class FakeBudgetPeriodDao : BudgetPeriodDao {
        val storage = mutableMapOf<Long, BudgetPeriodEntity>()
        private var nextId = 1L

        override suspend fun insert(budgetPeriod: BudgetPeriodEntity): Long {
            val id = if (budgetPeriod.id == 0L) nextId++ else budgetPeriod.id
            storage[id] = budgetPeriod.copy(id = id)
            return id
        }
        override suspend fun update(budgetPeriod: BudgetPeriodEntity) { storage[budgetPeriod.id] = budgetPeriod }
        override suspend fun getById(id: Long): BudgetPeriodEntity? = storage[id]
        override suspend fun getByYearMonth(year: Int, month: Int): BudgetPeriodEntity? = storage.values.firstOrNull { it.year == year && it.month == month }
        override fun observeByYearMonth(year: Int, month: Int): Flow<BudgetPeriodEntity?> = emptyFlow()
        override fun getAllPeriods(): Flow<List<BudgetPeriodEntity>> = emptyFlow()
        override suspend fun getAllPeriodsSync(): List<BudgetPeriodEntity> = storage.values.toList()
        override suspend fun getPeriodForTimestamp(timestamp: Long): BudgetPeriodEntity? = storage.values.firstOrNull { timestamp >= it.startAt && timestamp < it.endAt }
        override suspend fun getEarliestPeriod(): BudgetPeriodEntity? = storage.values.minByOrNull { it.startAt }
        override suspend fun getLatestPeriod(): BudgetPeriodEntity? = storage.values.maxByOrNull { it.startAt }
        override suspend fun getOpenPeriodForTimestamp(timestamp: Long): BudgetPeriodEntity? = storage.values.firstOrNull { it.status == BudgetPeriodStatus.OPEN && timestamp >= it.startAt && timestamp < it.endAt }
        override suspend fun getFirstOpenPeriod(): BudgetPeriodEntity? = storage.values.filter { it.status == BudgetPeriodStatus.OPEN }.minByOrNull { it.startAt }
        override suspend fun getClosedPeriodEndingAfter(startOfDayTimestamp: Long): BudgetPeriodEntity? =
            storage.values.firstOrNull { it.status == BudgetPeriodStatus.CLOSED && it.endAt > startOfDayTimestamp }
        override suspend fun getClosedPeriodForTimestamp(timestamp: Long): BudgetPeriodEntity? =
            storage.values.firstOrNull { it.status == BudgetPeriodStatus.CLOSED && timestamp >= it.startAt && timestamp < it.endAt }
    }

    private class FakeTransactionDao(private val budgetPeriodDao: FakeBudgetPeriodDao) : TransactionDao {
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

        override suspend fun findTransferPairCandidates(
            excludeAccountId: Long,
            oppositeDirection: TransactionDirection,
            amountPaise: Long,
            startWindow: Long,
            endWindow: Long
        ): List<TransactionEntity> {
            return storage.values.filter { tx ->
                val isClosedPeriod = budgetPeriodDao.getClosedPeriodForTimestamp(tx.occurredAt) != null
                tx.accountId != null &&
                tx.accountId != excludeAccountId &&
                tx.transferGroupId == null &&
                tx.status == TransactionStatus.PENDING &&
                tx.amountPaise == amountPaise &&
                tx.direction == oppositeDirection &&
                tx.occurredAt in startWindow..endWindow &&
                !isClosedPeriod // Amendment A2
            }
        }

        override suspend fun getTransferLegs(transferGroupId: String): List<TransactionEntity> {
            return storage.values.filter { it.transferGroupId == transferGroupId }
        }

        override suspend fun getRefundsForTransaction(originalTransactionId: Long): List<TransactionEntity> = emptyList()
        override suspend fun getTransactionsForAccountSync(accountId: Long): List<TransactionEntity> = emptyList()
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
        override suspend fun getAllTransactionAllocationsSync(): List<TransactionAllocationEntity> = allocations
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

    private lateinit var transactionDao: FakeTransactionDao
    private lateinit var allocationDao: FakeTransactionAllocationDao
    private lateinit var auditLogDao: FakeAuditLogDao
    private lateinit var accountDao: FakeAccountDao
    private lateinit var categoryDao: FakeCategoryDao
    private lateinit var budgetPeriodDao: FakeBudgetPeriodDao
    private lateinit var repository: TransactionRepository

    @Before
    fun setUp() = runBlocking {
        budgetPeriodDao = FakeBudgetPeriodDao()
        transactionDao = FakeTransactionDao(budgetPeriodDao)
        allocationDao = FakeTransactionAllocationDao()
        auditLogDao = FakeAuditLogDao()
        accountDao = FakeAccountDao()
        categoryDao = FakeCategoryDao()

        // HDFC checking (1) and SBI checking (2)
        accountDao.insert(AccountEntity(id = 1, name = "HDFC Checking", type = AccountType.BANK, identifierLast4 = "1111", openingBalancePaise = 50_000_00L, openingBalanceDate = 0L, createdAt = 0L, updatedAt = 0L))
        accountDao.insert(AccountEntity(id = 2, name = "SBI Checking", type = AccountType.BANK, identifierLast4 = "2222", openingBalancePaise = 20_000_00L, openingBalanceDate = 0L, createdAt = 0L, updatedAt = 0L))

        // Savings Account: Zerodha Mutual Funds (id=3)
        accountDao.insert(AccountEntity(id = 3, name = "Zerodha MF", type = AccountType.INVESTMENT, identifierLast4 = null, openingBalancePaise = 0L, openingBalanceDate = 0L, isSavings = true, createdAt = 0L, updatedAt = 0L))

        // Savings Category
        categoryDao.insert(CategoryEntity(id = 50, name = "SIP Mutual Funds", defaultBucket = BudgetBucket.SAVINGS, defaultSortOrder = 1))

        repository = TransactionRepository(
            transactionRunner = DirectTransactionRunner(),
            transactionDao = transactionDao,
            transactionAllocationDao = allocationDao,
            auditLogDao = auditLogDao,
            accountDao = accountDao,
            categoryDao = categoryDao,
            budgetPeriodDao = budgetPeriodDao
        )
    }

    @Test
    fun testCandidatePairDetection() = runBlocking {
        val baseTime = 100_000_000L

        // Incoming transaction T1: HDFC OUT ₹10,000 at baseTime
        val t1Id = repository.recordExpense(accountId = 1L, amountPaise = 10_000_00L, occurredAt = baseTime)
        val t1 = repository.getTransactionById(t1Id)!!

        // Existing transaction T2: SBI IN ₹10,000 at baseTime + 2 hours (within 24h)
        val t2Id = repository.recordIncome(accountId = 2L, amountPaise = 10_000_00L, occurredAt = baseTime + 7200_000L)
        repository.unconfirmTransaction(t2Id) // status = PENDING

        // Query candidates for t1
        val candidates = repository.findTransferPairCandidates(t1)
        assertThat(candidates).hasSize(1)
        assertThat(candidates[0].id).isEqualTo(t2Id)
    }

    @Test
    fun testCandidatePairExcludesClosedPeriod() = runBlocking {
        // Closed Period [1000L, 2000L)
        budgetPeriodDao.insert(
            BudgetPeriodEntity(
                id = 1,
                year = 2024,
                month = 1,
                startAt = 1000L,
                endAt = 2000L,
                incomePaise = 0L,
                status = BudgetPeriodStatus.CLOSED,
                createdAt = 0L,
                updatedAt = 0L
            )
        )

        // Incoming transaction at 3000L (open period)
        val t1Id = repository.recordExpense(accountId = 1L, amountPaise = 10_000_00L, occurredAt = 3000L)
        val t1 = repository.getTransactionById(t1Id)!!

        // Existing candidate at 1500L (inside CLOSED period)
        val t2Id = transactionDao.insert(
            TransactionEntity(
                accountId = 2L,
                kind = TransactionKind.INCOME,
                direction = TransactionDirection.IN,
                amountPaise = 10_000_00L,
                occurredAt = 1500L, // inside CLOSED period
                createdAt = 0L,
                updatedAt = 0L,
                status = TransactionStatus.PENDING,
                source = TransactionSource.NOTIFICATION
            )
        )

        // Candidate search MUST strictly exclude candidate in closed period (Amendment A2)
        val candidates = repository.findTransferPairCandidates(t1)
        assertThat(candidates).isEmpty()
    }

    @Test
    fun testConfirmCandidatePair_standard() = runBlocking {
        val t1Id = repository.recordExpense(accountId = 1L, amountPaise = 10_000_00L, occurredAt = 5000L)
        val t2Id = repository.recordIncome(accountId = 2L, amountPaise = 10_000_00L, occurredAt = 6000L)
        repository.unconfirmTransaction(t2Id)

        val transferGroupId = repository.confirmTransferPair(t1Id, t2Id)
        val leg1 = repository.getTransactionById(t1Id)!!
        val leg2 = repository.getTransactionById(t2Id)!!

        assertThat(leg1.transferGroupId).isEqualTo(transferGroupId)
        assertThat(leg2.transferGroupId).isEqualTo(transferGroupId)
        assertThat(leg1.kind).isEqualTo(TransactionKind.TRANSFER)
        assertThat(leg2.kind).isEqualTo(TransactionKind.TRANSFER)

        // Standard transfer: both CONFIRMED, zero allocations
        assertThat(leg1.status).isEqualTo(TransactionStatus.CONFIRMED)
        assertThat(leg2.status).isEqualTo(TransactionStatus.CONFIRMED)
        assertThat(allocationDao.getForTransaction(t1Id)).isEmpty()
        assertThat(allocationDao.getForTransaction(t2Id)).isEmpty()
    }

    @Test
    fun testConfirmCandidatePair_savingsContribution() = runBlocking {
        // HDFC (1) -> Zerodha MF (3, savings account)
        val outId = repository.recordExpense(accountId = 1L, amountPaise = 25_000_00L, occurredAt = 5000L)
        val inId = transactionDao.insert(
            TransactionEntity(
                accountId = 3L, // Zerodha MF
                kind = TransactionKind.TRANSFER,
                direction = TransactionDirection.IN,
                amountPaise = 25_000_00L,
                occurredAt = 5000L,
                createdAt = 0L,
                updatedAt = 0L,
                status = TransactionStatus.PENDING,
                source = TransactionSource.NOTIFICATION
            )
        )

        // 1. Confirming without category prompt selection: OUT leg stays PENDING, IN leg becomes CONFIRMED (§7.1)
        val groupId1 = repository.confirmTransferPair(outId, inId, savingsCategoryId = null)
        var legOut = repository.getTransactionById(outId)!!
        var legIn = repository.getTransactionById(inId)!!
        assertThat(legOut.status).isEqualTo(TransactionStatus.PENDING)
        assertThat(legIn.status).isEqualTo(TransactionStatus.CONFIRMED)

        // 2. Setting category on OUT leg (e.g. SIP category 50)
        repository.splitTransaction(outId, listOf(Pair(50L, 25_000_00L)))
        legOut = repository.getTransactionById(outId)!!
        assertThat(legOut.status).isEqualTo(TransactionStatus.CATEGORIZED)
        val allocations = allocationDao.getForTransaction(outId)
        assertThat(allocations).hasSize(1)
        assertThat(allocations[0].categoryId).isEqualTo(50L)
    }

    @Test
    fun testMarkAsTransfer() = runBlocking {
        // Single SMS received for HDFC debit ₹5,000
        val outId = repository.recordExpense(accountId = 1L, amountPaise = 5_000_00L, occurredAt = 8000L)

        // User marks as transfer to SBI (2)
        val groupId = repository.markAsTransfer(
            sourceTransactionId = outId,
            destinationAccountId = 2L
        )

        val legs = transactionDao.getTransferLegs(groupId)
        assertThat(legs).hasSize(2)

        val sourceLeg = legs.first { it.id == outId }
        val generatedLeg = legs.first { it.id != outId }

        assertThat(sourceLeg.kind).isEqualTo(TransactionKind.TRANSFER)
        assertThat(generatedLeg.kind).isEqualTo(TransactionKind.TRANSFER)
        assertThat(sourceLeg.direction).isEqualTo(TransactionDirection.OUT)
        assertThat(generatedLeg.direction).isEqualTo(TransactionDirection.IN)
        assertThat(generatedLeg.accountId).isEqualTo(2L)
        assertThat(sourceLeg.counterpartyAccountId).isEqualTo(2L)
        assertThat(generatedLeg.counterpartyAccountId).isEqualTo(1L)
        assertThat(generatedLeg.amountPaise).isEqualTo(5_000_00L)
    }
}
