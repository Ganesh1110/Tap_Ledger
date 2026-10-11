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
import com.tapledger.core.model.TransactionStatus
import com.tapledger.data.repository.TransactionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import java.util.UUID

/**
 * Validates Flow.md §7.1, §7.2: Transfer Double-Entry Architecture & Lifecycle Synchronization
 * - Two-legged double-entry with shared UUID, equal amount, opposite direction, reciprocal counterparty_account_id
 * - Lifecycle synchronization: voiding, restoring, ignoring across paired legs
 * - Closed period boundary pre-checks
 * - Amount edit & re-split guard (Rule A1)
 */
class TransferArchitectureTest {

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
            return storage.values.filter {
                it.accountId != null &&
                it.accountId != excludeAccountId &&
                it.transferGroupId == null &&
                it.status == TransactionStatus.PENDING &&
                it.amountPaise == amountPaise &&
                it.direction == oppositeDirection &&
                it.occurredAt in startWindow..endWindow
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
        transactionDao = FakeTransactionDao()
        allocationDao = FakeTransactionAllocationDao()
        auditLogDao = FakeAuditLogDao()
        accountDao = FakeAccountDao()
        categoryDao = FakeCategoryDao()
        budgetPeriodDao = FakeBudgetPeriodDao()

        // Seed bank accounts: HDFC (id=1) and SBI (id=2)
        accountDao.insert(AccountEntity(id = 1, name = "HDFC Savings", type = AccountType.BANK, identifierLast4 = "1111", openingBalancePaise = 50_000_00L, openingBalanceDate = 0L, createdAt = 0L, updatedAt = 0L))
        accountDao.insert(AccountEntity(id = 2, name = "SBI Savings", type = AccountType.BANK, identifierLast4 = "2222", openingBalancePaise = 20_000_00L, openingBalanceDate = 0L, createdAt = 0L, updatedAt = 0L))

        // Seed category
        categoryDao.insert(CategoryEntity(id = 10, name = "Emergency Fund", defaultBucket = BudgetBucket.SAVINGS, defaultSortOrder = 1))

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
    fun testTwoLeggedTransfer() = runBlocking {
        // Record transfer of ₹10,000 from HDFC (1) to SBI (2)
        val (outId, inId) = repository.recordTransfer(
            fromAccountId = 1L,
            toAccountId = 2L,
            amountPaise = 10_000_00L,
            occurredAt = 5000L
        )

        val outLeg = repository.getTransactionById(outId)!!
        val inLeg = repository.getTransactionById(inId)!!

        // Validate double-entry invariants (§7.1)
        assertThat(outLeg.kind).isEqualTo(TransactionKind.TRANSFER)
        assertThat(inLeg.kind).isEqualTo(TransactionKind.TRANSFER)

        assertThat(outLeg.accountId).isEqualTo(1L)
        assertThat(inLeg.accountId).isEqualTo(2L)

        assertThat(outLeg.direction).isEqualTo(TransactionDirection.OUT)
        assertThat(inLeg.direction).isEqualTo(TransactionDirection.IN)

        assertThat(outLeg.amountPaise).isEqualTo(10_000_00L)
        assertThat(inLeg.amountPaise).isEqualTo(10_000_00L)

        // Must share a non-null UUID transfer_group_id
        assertThat(outLeg.transferGroupId).isNotNull()
        assertThat(outLeg.transferGroupId).isEqualTo(inLeg.transferGroupId)
        // Valid UUID format check
        UUID.fromString(outLeg.transferGroupId!!)

        // Reciprocal counterparty_account_id
        assertThat(outLeg.counterpartyAccountId).isEqualTo(2L)
        assertThat(inLeg.counterpartyAccountId).isEqualTo(1L)

        // Both legs seeded as CONFIRMED for standard transfer
        assertThat(outLeg.status).isEqualTo(TransactionStatus.CONFIRMED)
        assertThat(inLeg.status).isEqualTo(TransactionStatus.CONFIRMED)
    }

    @Test
    fun testLifecycleSynchronization_voidAndRestore() = runBlocking {
        val (outId, inId) = repository.recordTransfer(
            fromAccountId = 1L,
            toAccountId = 2L,
            amountPaise = 5_000_00L,
            occurredAt = 5000L
        )

        // 1. Voiding leg 1 must automatically void leg 2 (§7.1.3)
        repository.voidTransaction(outId, reason = "Voided by user")

        val voidedOut = repository.getTransactionById(outId)!!
        val voidedIn = repository.getTransactionById(inId)!!
        assertThat(voidedOut.status).isEqualTo(TransactionStatus.VOIDED)
        assertThat(voidedIn.status).isEqualTo(TransactionStatus.VOIDED)
        assertThat(voidedOut.previousStatus).isEqualTo(TransactionStatus.CONFIRMED)
        assertThat(voidedIn.previousStatus).isEqualTo(TransactionStatus.CONFIRMED)

        // 2. Restoring leg 2 must automatically restore leg 1 (§7.1.3)
        repository.restoreTransaction(inId, reason = "Restored by user")

        val restoredOut = repository.getTransactionById(outId)!!
        val restoredIn = repository.getTransactionById(inId)!!
        assertThat(restoredOut.status).isEqualTo(TransactionStatus.CONFIRMED)
        assertThat(restoredIn.status).isEqualTo(TransactionStatus.CONFIRMED)
    }

    @Test
    fun testLifecycleSynchronization_ignoreAndUnignore() = runBlocking {
        // Create pending transfer pair
        val t1 = repository.recordExpense(accountId = 1L, amountPaise = 2_000_00L, occurredAt = 5000L)
        val t2 = repository.recordIncome(accountId = 2L, amountPaise = 2_000_00L, occurredAt = 5000L)
        repository.unconfirmTransaction(t2) // Make t2 PENDING

        val transferGroupId = repository.confirmTransferPair(t1, t2)
        val legs = transactionDao.getTransferLegs(transferGroupId)
        val leg1 = legs[0]
        val leg2 = legs[1]

        // Unconfirm both so they are PENDING
        repository.unconfirmTransaction(leg1.id)
        repository.unconfirmTransaction(leg2.id)

        // 1. Ignoring leg 1 automatically ignores leg 2
        repository.ignoreTransaction(leg1.id)
        assertThat(repository.getTransactionById(leg1.id)?.status).isEqualTo(TransactionStatus.IGNORED)
        assertThat(repository.getTransactionById(leg2.id)?.status).isEqualTo(TransactionStatus.IGNORED)

        // 2. Unignoring leg 2 automatically unignores leg 1
        repository.unignoreTransaction(leg2.id)
        assertThat(repository.getTransactionById(leg1.id)?.status).isEqualTo(TransactionStatus.PENDING)
        assertThat(repository.getTransactionById(leg2.id)?.status).isEqualTo(TransactionStatus.PENDING)
    }

    @Test
    fun testClosedPeriodBoundaryPreCheck() = runBlocking {
        // Create closed period covering timestamp 1000L..2000L
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

        // Attempting to record transfer inside closed period throws exception upfront
        val createEx = assertThrows(IllegalStateException::class.java) {
            runBlocking {
                repository.recordTransfer(
                    fromAccountId = 1L,
                    toAccountId = 2L,
                    amountPaise = 1000_00L,
                    occurredAt = 1500L
                )
            }
        }
        assertThat(createEx.message).contains("Cannot alter transfer linked to a closed budget period")

        // Create an existing transfer spanning timestamp 1500L directly into DB
        val uuid = UUID.randomUUID().toString()
        val leg1Id = transactionDao.insert(
            TransactionEntity(
                accountId = 1L,
                kind = TransactionKind.TRANSFER,
                direction = TransactionDirection.OUT,
                amountPaise = 500_00L,
                occurredAt = 1500L, // inside closed period
                createdAt = 0L,
                updatedAt = 0L,
                status = TransactionStatus.CONFIRMED,
                source = com.tapledger.core.model.TransactionSource.MANUAL,
                transferGroupId = uuid,
                counterpartyAccountId = 2L
            )
        )
        val leg2Id = transactionDao.insert(
            TransactionEntity(
                accountId = 2L,
                kind = TransactionKind.TRANSFER,
                direction = TransactionDirection.IN,
                amountPaise = 500_00L,
                occurredAt = 3000L, // outside closed period
                createdAt = 0L,
                updatedAt = 0L,
                status = TransactionStatus.CONFIRMED,
                source = com.tapledger.core.model.TransactionSource.MANUAL,
                transferGroupId = uuid,
                counterpartyAccountId = 1L
            )
        )

        // Modifying/voiding leg2 (even though leg2 occurredAt is open) MUST fail upfront because leg1 is in closed period (§7.1.3)
        val voidEx = assertThrows(IllegalStateException::class.java) {
            runBlocking {
                repository.voidTransaction(leg2Id)
            }
        }
        assertThat(voidEx.message).contains("Cannot alter transfer linked to a closed budget period")
    }

    @Test
    fun testAmountEditAndReSplitGuard() = runBlocking {
        // Setup savings account ICICI Savings (id=3)
        accountDao.insert(AccountEntity(id = 3, name = "ICICI Savings", type = AccountType.BANK, identifierLast4 = "3333", openingBalancePaise = 0L, openingBalanceDate = 0L, isSavings = true, createdAt = 0L, updatedAt = 0L))

        // Record savings transfer of ₹10,000 from HDFC (1) to ICICI Savings (3) categorized to Category 10
        val (outId, inId) = repository.recordTransfer(
            fromAccountId = 1L,
            toAccountId = 3L,
            amountPaise = 10_000_00L,
            occurredAt = 5000L,
            savingsCategoryId = 10L
        )

        val outLeg = repository.getTransactionById(outId)!!
        assertThat(outLeg.status).isEqualTo(TransactionStatus.CATEGORIZED)
        val groupId = outLeg.transferGroupId!!

        // 1. Update transfer amount to ₹15,000 with matching single-allocation adjustment
        repository.updateTransferAmount(
            transferGroupId = groupId,
            newAmountPaise = 15_000_00L
        )

        val updatedOut = repository.getTransactionById(outId)!!
        val updatedIn = repository.getTransactionById(inId)!!
        assertThat(updatedOut.amountPaise).isEqualTo(15_000_00L)
        assertThat(updatedIn.amountPaise).isEqualTo(15_000_00L)

        val allocations = allocationDao.getForTransaction(outId)
        assertThat(allocations).hasSize(1)
        assertThat(allocations[0].amountPaise).isEqualTo(15_000_00L)

        // 2. Editing with explicit split allocations matching amount succeeds
        val newAllocations = listOf(Pair(10L, 15_000_00L))
        repository.updateTransferAmount(
            transferGroupId = groupId,
            newAmountPaise = 20_000_00L,
            newAllocations = listOf(Pair(10L, 20_000_00L))
        )
        assertThat(repository.getTransactionById(outId)?.amountPaise).isEqualTo(20_000_00L)
        assertThat(allocationDao.getForTransaction(outId)[0].amountPaise).isEqualTo(20_000_00L)

        // 3. Updating with mismatched allocations throws IllegalArgumentException (Rule A1)
        val mismatchEx = assertThrows(IllegalArgumentException::class.java) {
            runBlocking {
                repository.updateTransferAmount(
                    transferGroupId = groupId,
                    newAmountPaise = 30_000_00L,
                    newAllocations = listOf(Pair(10L, 25_000_00L)) // 25,000 != 30,000
                )
            }
        }
        assertThat(mismatchEx.message).contains("Rule A1")
    }
}
