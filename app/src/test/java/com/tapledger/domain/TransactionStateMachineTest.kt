package com.tapledger.domain

import com.google.common.truth.Truth.assertThat
import com.tapledger.core.database.DatabaseTransactionRunner
import com.tapledger.core.database.dao.AuditLogDao
import com.tapledger.core.database.dao.TransactionAllocationDao
import com.tapledger.core.database.dao.TransactionDao
import com.tapledger.core.database.entity.AuditLogEntity
import com.tapledger.core.database.entity.TransactionAllocationEntity
import com.tapledger.core.database.entity.TransactionEntity
import com.tapledger.core.model.AuditAction
import com.tapledger.core.model.TransactionDirection
import com.tapledger.core.model.TransactionKind
import com.tapledger.core.model.TransactionSource
import com.tapledger.core.model.TransactionStatus
import com.tapledger.data.repository.TransactionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test

/**
 * Validates Flow.md §4: Complete 5-State Machine & State Transitions
 * - PENDING, CONFIRMED, CATEGORIZED, IGNORED, VOIDED
 * - Reversible void/restore with previous_status
 * - Account attribution guard on CONFIRMED and CATEGORIZED
 */
class TransactionStateMachineTest {

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

    private class FakeTransactionAllocationDao : TransactionAllocationDao {
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

    private class FakeAuditLogDao : AuditLogDao {
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

    private class FakeAccountDao : com.tapledger.core.database.dao.AccountDao {
        val storage = mutableMapOf<Long, com.tapledger.core.database.entity.AccountEntity>()
        private var nextId = 1L

        override suspend fun insert(account: com.tapledger.core.database.entity.AccountEntity): Long {
            val id = if (account.id == 0L) nextId++ else account.id
            storage[id] = account.copy(id = id)
            return id
        }
        override suspend fun insertAll(accounts: List<com.tapledger.core.database.entity.AccountEntity>): List<Long> = accounts.map { insert(it) }
        override suspend fun update(account: com.tapledger.core.database.entity.AccountEntity) { storage[account.id] = account }
        override suspend fun getById(id: Long): com.tapledger.core.database.entity.AccountEntity? = storage[id]
        override fun getAllActiveAccounts(): Flow<List<com.tapledger.core.database.entity.AccountEntity>> = emptyFlow()
        override suspend fun getAllAccountsSync(): List<com.tapledger.core.database.entity.AccountEntity> = storage.values.toList()
        override suspend fun findByLast4(last4: String): com.tapledger.core.database.entity.AccountEntity? = storage.values.firstOrNull { it.identifierLast4 == last4 && it.isActive }
        override suspend fun findAllActiveByLast4(last4: String): List<com.tapledger.core.database.entity.AccountEntity> = storage.values.filter { it.identifierLast4 == last4 && it.isActive }
        override suspend fun getActiveBankAccounts(): List<com.tapledger.core.database.entity.AccountEntity> = storage.values.filter { it.type == com.tapledger.core.model.AccountType.BANK && it.isActive }
    }

    private class FakeCategoryDao : com.tapledger.core.database.dao.CategoryDao {
        val storage = mutableMapOf<Long, com.tapledger.core.database.entity.CategoryEntity>()
        private var nextId = 1L

        override suspend fun insert(category: com.tapledger.core.database.entity.CategoryEntity): Long {
            val id = if (category.id == 0L) nextId++ else category.id
            storage[id] = category.copy(id = id)
            return id
        }
        override suspend fun insertAll(categories: List<com.tapledger.core.database.entity.CategoryEntity>): List<Long> = categories.map { insert(it) }
        override suspend fun update(category: com.tapledger.core.database.entity.CategoryEntity) { storage[category.id] = category }
        override suspend fun getById(id: Long): com.tapledger.core.database.entity.CategoryEntity? = storage[id]
        override fun getAllActiveCategories(): Flow<List<com.tapledger.core.database.entity.CategoryEntity>> = emptyFlow()
        override suspend fun getAllCategoriesSync(): List<com.tapledger.core.database.entity.CategoryEntity> = storage.values.toList()
        override suspend fun getByName(name: String): com.tapledger.core.database.entity.CategoryEntity? = storage.values.firstOrNull { it.name == name }
        override suspend fun getMaxSortOrder(): Int? = storage.values.maxOfOrNull { it.defaultSortOrder }
    }

    private class FakeBudgetPeriodDao : com.tapledger.core.database.dao.BudgetPeriodDao {
        val storage = mutableMapOf<Long, com.tapledger.core.database.entity.BudgetPeriodEntity>()
        private var nextId = 1L

        override suspend fun insert(budgetPeriod: com.tapledger.core.database.entity.BudgetPeriodEntity): Long {
            val id = if (budgetPeriod.id == 0L) nextId++ else budgetPeriod.id
            storage[id] = budgetPeriod.copy(id = id)
            return id
        }
        override suspend fun update(budgetPeriod: com.tapledger.core.database.entity.BudgetPeriodEntity) { storage[budgetPeriod.id] = budgetPeriod }
        override suspend fun getById(id: Long): com.tapledger.core.database.entity.BudgetPeriodEntity? = storage[id]
        override suspend fun getByYearMonth(year: Int, month: Int): com.tapledger.core.database.entity.BudgetPeriodEntity? = storage.values.firstOrNull { it.year == year && it.month == month }
        override fun observeByYearMonth(year: Int, month: Int): Flow<com.tapledger.core.database.entity.BudgetPeriodEntity?> = emptyFlow()
        override fun getAllPeriods(): Flow<List<com.tapledger.core.database.entity.BudgetPeriodEntity>> = emptyFlow()
        override suspend fun getAllPeriodsSync(): List<com.tapledger.core.database.entity.BudgetPeriodEntity> = storage.values.toList()
        override suspend fun getPeriodForTimestamp(timestamp: Long): com.tapledger.core.database.entity.BudgetPeriodEntity? = storage.values.firstOrNull { timestamp >= it.startAt && timestamp < it.endAt }
        override suspend fun getEarliestPeriod(): com.tapledger.core.database.entity.BudgetPeriodEntity? = storage.values.minByOrNull { it.startAt }
        override suspend fun getLatestPeriod(): com.tapledger.core.database.entity.BudgetPeriodEntity? = storage.values.maxByOrNull { it.startAt }
        override suspend fun getOpenPeriodForTimestamp(timestamp: Long): com.tapledger.core.database.entity.BudgetPeriodEntity? = storage.values.firstOrNull { it.status == com.tapledger.core.model.BudgetPeriodStatus.OPEN && timestamp >= it.startAt && timestamp < it.endAt }
        override suspend fun getFirstOpenPeriod(): com.tapledger.core.database.entity.BudgetPeriodEntity? = storage.values.filter { it.status == com.tapledger.core.model.BudgetPeriodStatus.OPEN }.minByOrNull { it.startAt }
        override suspend fun getClosedPeriodEndingAfter(startOfDayTimestamp: Long): com.tapledger.core.database.entity.BudgetPeriodEntity? =
            storage.values.firstOrNull { it.status == com.tapledger.core.model.BudgetPeriodStatus.CLOSED && it.endAt > startOfDayTimestamp }
        override suspend fun getClosedPeriodForTimestamp(timestamp: Long): com.tapledger.core.database.entity.BudgetPeriodEntity? =
            storage.values.firstOrNull { it.status == com.tapledger.core.model.BudgetPeriodStatus.CLOSED && timestamp >= it.startAt && timestamp < it.endAt }
    }

    private lateinit var transactionDao: FakeTransactionDao
    private lateinit var allocationDao: FakeTransactionAllocationDao
    private lateinit var auditLogDao: FakeAuditLogDao
    private lateinit var accountDao: FakeAccountDao
    private lateinit var categoryDao: FakeCategoryDao
    private lateinit var budgetPeriodDao: FakeBudgetPeriodDao
    private lateinit var repository: TransactionRepository

    @Before
    fun setUp() {
        transactionDao = FakeTransactionDao()
        allocationDao = FakeTransactionAllocationDao()
        auditLogDao = FakeAuditLogDao()
        accountDao = FakeAccountDao()
        categoryDao = FakeCategoryDao()
        budgetPeriodDao = FakeBudgetPeriodDao()

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
    fun testStatusTransitions() = runBlocking {
        // 1. Initial State: Expense without allocations -> PENDING
        val txId = repository.recordExpense(
            accountId = 1L,
            amountPaise = 50000L,
            occurredAt = 1000L,
            merchantRaw = "Swiggy"
        )
        val initialTx = repository.getTransactionById(txId)
        assertThat(initialTx?.status).isEqualTo(TransactionStatus.PENDING)

        // 2. PENDING -> CATEGORIZED (categorize/split)
        repository.splitTransaction(
            transactionId = txId,
            allocations = listOf(Pair(10L, 50000L)),
            reason = "Categorized to Dining"
        )
        val categorizedTx = repository.getTransactionById(txId)
        assertThat(categorizedTx?.status).isEqualTo(TransactionStatus.CATEGORIZED)
        assertThat(allocationDao.getForTransaction(txId)).hasSize(1)

        // 3. CATEGORIZED -> CATEGORIZED (re-split)
        repository.splitTransaction(
            transactionId = txId,
            allocations = listOf(Pair(10L, 30000L), Pair(20L, 20000L)),
            reason = "Re-split between Dining and Groceries"
        )
        val resplitTx = repository.getTransactionById(txId)
        assertThat(resplitTx?.status).isEqualTo(TransactionStatus.CATEGORIZED)
        assertThat(allocationDao.getForTransaction(txId)).hasSize(2)

        // 4. CATEGORIZED -> PENDING (uncategorize)
        repository.uncategorizeTransaction(txId, reason = "Reset to pending")
        val uncatTx = repository.getTransactionById(txId)
        assertThat(uncatTx?.status).isEqualTo(TransactionStatus.PENDING)
        // Allocations must have been deleted
        assertThat(allocationDao.getForTransaction(txId)).isEmpty()

        // 5. PENDING -> CONFIRMED
        repository.confirmTransaction(txId, reason = "User confirmed")
        val confirmedTx = repository.getTransactionById(txId)
        assertThat(confirmedTx?.status).isEqualTo(TransactionStatus.CONFIRMED)

        // 6. CONFIRMED -> PENDING (unconfirm)
        repository.unconfirmTransaction(txId, reason = "User unconfirmed")
        val unconfirmedTx = repository.getTransactionById(txId)
        assertThat(unconfirmedTx?.status).isEqualTo(TransactionStatus.PENDING)

        // 7. PENDING -> IGNORED
        repository.ignoreTransaction(txId, reason = "Promotional alert")
        val ignoredTx = repository.getTransactionById(txId)
        assertThat(ignoredTx?.status).isEqualTo(TransactionStatus.IGNORED)

        // 8. IGNORED -> PENDING (unignore)
        repository.unignoreTransaction(txId, reason = "Unignored")
        val unignoredTx = repository.getTransactionById(txId)
        assertThat(unignoredTx?.status).isEqualTo(TransactionStatus.PENDING)
    }

    @Test
    fun testInitialCreationStatuses() = runBlocking {
        // Manual Income: [*] -> CONFIRMED (Flow.md §4)
        val incomeId = repository.recordIncome(
            accountId = 1L,
            amountPaise = 15000000L,
            occurredAt = 1000L,
            note = "Salary"
        )
        assertThat(repository.getTransactionById(incomeId)?.status).isEqualTo(TransactionStatus.CONFIRMED)

        // Manual Transfer: [*] -> CONFIRMED on both legs (Flow.md §4)
        val (outLegId, inLegId) = repository.recordTransfer(
            fromAccountId = 1L,
            toAccountId = 2L,
            amountPaise = 200000L,
            occurredAt = 2000L
        )
        assertThat(repository.getTransactionById(outLegId)?.status).isEqualTo(TransactionStatus.CONFIRMED)
        assertThat(repository.getTransactionById(inLegId)?.status).isEqualTo(TransactionStatus.CONFIRMED)

        // Manual Expense with Allocations: [*] -> CATEGORIZED (Flow.md §4)
        val expenseCategorizedId = repository.recordExpense(
            accountId = 1L,
            amountPaise = 10000L,
            occurredAt = 3000L,
            allocations = listOf(Pair(10L, 10000L))
        )
        assertThat(repository.getTransactionById(expenseCategorizedId)?.status).isEqualTo(TransactionStatus.CATEGORIZED)
    }

    @Test
    fun testVoidAndRestoreReversibility() = runBlocking {
        // A. PENDING -> VOIDED -> PENDING
        val pId = repository.recordExpense(accountId = 1L, amountPaise = 1000L, occurredAt = 100L)
        repository.voidTransaction(pId, "Voided pending")
        val voidedPending = repository.getTransactionById(pId)
        assertThat(voidedPending?.status).isEqualTo(TransactionStatus.VOIDED)
        assertThat(voidedPending?.previousStatus).isEqualTo(TransactionStatus.PENDING)

        repository.restoreTransaction(pId, "Restored pending")
        val restoredPending = repository.getTransactionById(pId)
        assertThat(restoredPending?.status).isEqualTo(TransactionStatus.PENDING)
        assertThat(restoredPending?.previousStatus).isNull()

        // B. CONFIRMED -> VOIDED -> CONFIRMED
        val cId = repository.recordIncome(accountId = 1L, amountPaise = 2000L, occurredAt = 200L)
        repository.voidTransaction(cId, "Voided confirmed")
        val voidedConfirmed = repository.getTransactionById(cId)
        assertThat(voidedConfirmed?.status).isEqualTo(TransactionStatus.VOIDED)
        assertThat(voidedConfirmed?.previousStatus).isEqualTo(TransactionStatus.CONFIRMED)

        repository.restoreTransaction(cId, "Restored confirmed")
        val restoredConfirmed = repository.getTransactionById(cId)
        assertThat(restoredConfirmed?.status).isEqualTo(TransactionStatus.CONFIRMED)
        assertThat(restoredConfirmed?.previousStatus).isNull()

        // C. CATEGORIZED -> VOIDED -> CATEGORIZED
        val catId = repository.recordExpense(
            accountId = 1L,
            amountPaise = 3000L,
            occurredAt = 300L,
            allocations = listOf(Pair(10L, 3000L))
        )
        repository.voidTransaction(catId, "Voided categorized")
        val voidedCategorized = repository.getTransactionById(catId)
        assertThat(voidedCategorized?.status).isEqualTo(TransactionStatus.VOIDED)
        assertThat(voidedCategorized?.previousStatus).isEqualTo(TransactionStatus.CATEGORIZED)

        repository.restoreTransaction(catId, "Restored categorized")
        val restoredCategorized = repository.getTransactionById(catId)
        assertThat(restoredCategorized?.status).isEqualTo(TransactionStatus.CATEGORIZED)
        assertThat(restoredCategorized?.previousStatus).isNull()

        // D. IGNORED -> VOIDED -> IGNORED
        val igId = repository.recordExpense(accountId = 1L, amountPaise = 4000L, occurredAt = 400L)
        repository.ignoreTransaction(igId, "Ignored alert")
        repository.voidTransaction(igId, "Voided ignored")
        val voidedIgnored = repository.getTransactionById(igId)
        assertThat(voidedIgnored?.status).isEqualTo(TransactionStatus.VOIDED)
        assertThat(voidedIgnored?.previousStatus).isEqualTo(TransactionStatus.IGNORED)

        repository.restoreTransaction(igId, "Restored ignored")
        val restoredIgnored = repository.getTransactionById(igId)
        assertThat(restoredIgnored?.status).isEqualTo(TransactionStatus.IGNORED)
        assertThat(restoredIgnored?.previousStatus).isNull()
    }

    @Test
    fun testUnassignedAccountQuarantineAndGuards() = runBlocking {
        // Ingest unassigned transaction (account_id = null)
        val unassignedTx = TransactionEntity(
            id = 500L,
            accountId = null,
            kind = TransactionKind.EXPENSE,
            direction = TransactionDirection.OUT,
            amountPaise = 25000L,
            occurredAt = 1000L,
            createdAt = 1000L,
            updatedAt = 1000L,
            status = TransactionStatus.PENDING,
            source = TransactionSource.NOTIFICATION
        )
        transactionDao.insert(unassignedTx)

        // 1. Cannot transition to CONFIRMED while account_id is NULL
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { repository.confirmTransaction(500L) }
        }

        // 2. Cannot transition to CATEGORIZED while account_id is NULL
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking {
                repository.splitTransaction(500L, listOf(Pair(10L, 25000L)))
            }
        }

        // 3. Can transition PENDING -> IGNORED with account_id = null (Exemption §3.1)
        repository.ignoreTransaction(500L)
        assertThat(repository.getTransactionById(500L)?.status).isEqualTo(TransactionStatus.IGNORED)

        // 4. Can transition IGNORED -> VOIDED with account_id = null (Exemption §3.1)
        repository.voidTransaction(500L, "Voided unassigned")
        assertThat(repository.getTransactionById(500L)?.status).isEqualTo(TransactionStatus.VOIDED)

        // 5. Restoring voided unassigned transaction restores to IGNORED without error
        repository.restoreTransaction(500L)
        assertThat(repository.getTransactionById(500L)?.status).isEqualTo(TransactionStatus.IGNORED)

        // 6. Assign account unlocks confirmation and categorization
        repository.unignoreTransaction(500L)
        repository.assignAccount(500L, accountId = 1L)
        repository.confirmTransaction(500L)
        assertThat(repository.getTransactionById(500L)?.status).isEqualTo(TransactionStatus.CONFIRMED)
    }

    @Test
    fun testAuditLogSnapshotsRecorded() = runBlocking {
        val txId = repository.recordExpense(
            accountId = 1L,
            amountPaise = 10000L,
            occurredAt = 5000L
        )

        // Verify initial CREATED audit entry has afterSnapshotJson
        val logs1 = auditLogDao.getForTransactionSync(txId)
        assertThat(logs1).hasSize(1)
        assertThat(logs1[0].action).isEqualTo(AuditAction.CREATED)
        assertThat(logs1[0].afterSnapshotJson).isNotNull()
        assertThat(logs1[0].afterSnapshotJson).contains("\"status\":\"PENDING\"")

        // Void transaction
        repository.voidTransaction(txId, "Voided by user")
        val logs2 = auditLogDao.getForTransactionSync(txId)
        assertThat(logs2).hasSize(2)
        val voidLog = logs2[1]
        assertThat(voidLog.action).isEqualTo(AuditAction.VOIDED)
        assertThat(voidLog.beforeSnapshotJson).contains("\"status\":\"PENDING\"")
        assertThat(voidLog.afterSnapshotJson).contains("\"status\":\"VOIDED\"")
        assertThat(voidLog.afterSnapshotJson).contains("\"previousStatus\":\"PENDING\"")

        // Restore transaction
        repository.restoreTransaction(txId, "Restored by user")
        val logs3 = auditLogDao.getForTransactionSync(txId)
        assertThat(logs3).hasSize(3)
        val restoreLog = logs3[2]
        assertThat(restoreLog.action).isEqualTo(AuditAction.RESTORED)
        assertThat(restoreLog.beforeSnapshotJson).contains("\"status\":\"VOIDED\"")
        assertThat(restoreLog.afterSnapshotJson).contains("\"status\":\"PENDING\"")
        assertThat(restoreLog.afterSnapshotJson).contains("\"previousStatus\":null")
    }
}
