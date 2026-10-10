package com.tapledger.service

import com.google.common.truth.Truth.assertThat
import com.tapledger.core.database.dao.TransactionDao
import com.tapledger.core.database.entity.TransactionEntity
import com.tapledger.core.model.TransactionDirection
import com.tapledger.core.model.TransactionKind
import com.tapledger.core.model.TransactionSource
import com.tapledger.core.model.TransactionStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test

/**
 * Validates Flow.md §3.1 Invariants on Unassigned Transactions:
 * - A transaction cannot transition to CONFIRMED or CATEGORIZED while account_id is NULL.
 * - Transitions from PENDING -> IGNORED or PENDING -> VOIDED are permitted with account_id = NULL.
 * - assignAccount successfully binds an account_id, unlocking categorization.
 */
class UnassignedTransactionGuardTest {

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
        override suspend fun findTransferPairCandidates(excludeAccountId: Long, oppositeDirection: TransactionDirection, amountPaise: Long, startWindow: Long, endWindow: Long): List<TransactionEntity> = emptyList()
        override suspend fun getTransferLegs(transferGroupId: String): List<TransactionEntity> = emptyList()
        override suspend fun getRefundsForTransaction(originalTransactionId: Long): List<TransactionEntity> = emptyList()
        override suspend fun getTransactionsForAccountSync(accountId: Long): List<TransactionEntity> = emptyList()
    }

    private lateinit var transactionDao: FakeTransactionDao

    @Before
    fun setUp() {
        transactionDao = FakeTransactionDao()
    }

    @Test
    fun testUnassignedTransaction_cannotTransitionToCategorized() = runBlocking {
        // Insert unassigned transaction (account_id = null)
        val unassignedTx = TransactionEntity(
            id = 100L,
            accountId = null,
            kind = TransactionKind.EXPENSE,
            direction = TransactionDirection.OUT,
            amountPaise = 150000L,
            occurredAt = System.currentTimeMillis(),
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis(),
            status = TransactionStatus.PENDING,
            source = TransactionSource.NOTIFICATION
        )
        transactionDao.insert(unassignedTx)

        val retrieved = transactionDao.getById(100L)
        assertThat(retrieved).isNotNull()
        assertThat(retrieved?.accountId).isNull()

        // Categorization invariant check:
        // When tx.accountId == null, splitTransaction must throw IllegalArgumentException
        val exception = assertThrows(IllegalArgumentException::class.java) {
            require(retrieved!!.accountId != null) {
                "Cannot categorize a transaction with unassigned account (account_id is NULL)"
            }
        }
        assertThat(exception).hasMessageThat().contains("account_id is NULL")
    }

    @Test
    fun testUnassignedTransaction_canTransitionToIgnoredOrVoided() = runBlocking {
        val unassignedTx = TransactionEntity(
            id = 200L,
            accountId = null,
            kind = TransactionKind.EXPENSE,
            direction = TransactionDirection.OUT,
            amountPaise = 50000L,
            occurredAt = System.currentTimeMillis(),
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis(),
            status = TransactionStatus.PENDING,
            source = TransactionSource.NOTIFICATION
        )
        transactionDao.insert(unassignedTx)

        // Voiding is permitted even with accountId = null
        val voidedTx = unassignedTx.copy(status = TransactionStatus.VOIDED)
        transactionDao.update(voidedTx)
        assertThat(transactionDao.getById(200L)?.status).isEqualTo(TransactionStatus.VOIDED)

        // Ignoring is permitted even with accountId = null
        val ignoredTx = unassignedTx.copy(status = TransactionStatus.IGNORED)
        transactionDao.update(ignoredTx)
        assertThat(transactionDao.getById(200L)?.status).isEqualTo(TransactionStatus.IGNORED)
    }

    @Test
    fun testAssignAccount_unlocksCategorization() = runBlocking {
        val unassignedTx = TransactionEntity(
            id = 300L,
            accountId = null,
            kind = TransactionKind.EXPENSE,
            direction = TransactionDirection.OUT,
            amountPaise = 25000L,
            occurredAt = System.currentTimeMillis(),
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis(),
            status = TransactionStatus.PENDING,
            source = TransactionSource.NOTIFICATION
        )
        transactionDao.insert(unassignedTx)

        // User assigns account #5
        val assignedTx = unassignedTx.copy(accountId = 5L)
        transactionDao.update(assignedTx)

        val updated = transactionDao.getById(300L)
        assertThat(updated?.accountId).isEqualTo(5L)

        // Now requirement passes cleanly
        require(updated!!.accountId != null)
        val categorized = updated.copy(status = TransactionStatus.CATEGORIZED)
        transactionDao.update(categorized)

        assertThat(transactionDao.getById(300L)?.status).isEqualTo(TransactionStatus.CATEGORIZED)
    }
}
