package com.tapledger.data.repository

import androidx.room.withTransaction
import com.tapledger.core.database.TapLedgerDatabase
import com.tapledger.core.database.dao.AuditLogDao
import com.tapledger.core.database.dao.TransactionAllocationDao
import com.tapledger.core.database.dao.TransactionDao
import com.tapledger.core.database.entity.AuditLogEntity
import com.tapledger.core.database.entity.TransactionAllocationEntity
import com.tapledger.core.database.entity.TransactionEntity
import com.tapledger.core.model.AuditAction
import com.tapledger.core.model.TransactionKind
import com.tapledger.core.model.TransactionSource
import com.tapledger.core.model.TransactionStatus
import com.tapledger.core.model.TransferDirection
import com.tapledger.core.util.DateTimeUtils
import kotlinx.coroutines.flow.Flow
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TransactionRepository @Inject constructor(
    private val database: TapLedgerDatabase,
    private val transactionDao: TransactionDao,
    private val transactionAllocationDao: TransactionAllocationDao,
    private val auditLogDao: AuditLogDao
) {

    fun observeAllTransactions(): Flow<List<TransactionEntity>> = transactionDao.observeAllTransactions()

    fun observePendingTransactions(): Flow<List<TransactionEntity>> = transactionDao.observePendingTransactions()

    fun observePendingCount(): Flow<Int> = transactionDao.observePendingCount()

    suspend fun getTransactionById(id: Long): TransactionEntity? = transactionDao.getById(id)

    suspend fun getAllocationsForTransaction(transactionId: Long): List<TransactionAllocationEntity> =
        transactionAllocationDao.getForTransaction(transactionId)

    /**
     * Atomically records an expense.
     * If allocations are provided and sum equals amount, marked CATEGORIZED.
     * If no allocations, marked PENDING.
     */
    suspend fun recordExpense(
        accountId: Long,
        amountPaise: Long,
        occurredAt: Long,
        merchantRaw: String? = null,
        merchantNormalized: String? = null,
        upiReference: String? = null,
        source: TransactionSource = TransactionSource.MANUAL,
        sourceFingerprint: String? = null,
        note: String? = null,
        allocations: List<Pair<Long, Long>> = emptyList() // Pair<categoryId, amountPaise>
    ): Long {
        require(amountPaise > 0L) { "Expense amount must be positive" }

        val now = DateTimeUtils.nowEpochMillis()
        val isCategorized = allocations.isNotEmpty()

        if (isCategorized) {
            val totalAlloc = allocations.sumOf { it.second }
            require(totalAlloc == amountPaise) {
                "Sum of allocations ($totalAlloc) must equal transaction amount ($amountPaise)"
            }
        }

        return database.withTransaction {
            val status = if (isCategorized) TransactionStatus.CATEGORIZED else TransactionStatus.PENDING

            val tx = TransactionEntity(
                accountId = accountId,
                kind = TransactionKind.EXPENSE,
                amountPaise = amountPaise,
                merchantRaw = merchantRaw,
                merchantNormalized = merchantNormalized,
                upiReference = upiReference,
                occurredAt = occurredAt,
                createdAt = now,
                updatedAt = now,
                status = status,
                source = source,
                sourceFingerprint = sourceFingerprint,
                note = note
            )
            val txId = transactionDao.insert(tx)

            if (isCategorized) {
                val allocEntities = allocations.map { (catId, amt) ->
                    TransactionAllocationEntity(
                        transactionId = txId,
                        categoryId = catId,
                        amountPaise = amt,
                        createdAt = now,
                        updatedAt = now
                    )
                }
                transactionAllocationDao.insertAll(allocEntities)
            }

            auditLogDao.insert(
                AuditLogEntity(
                    transactionId = txId,
                    action = AuditAction.CREATED,
                    reason = "Initial expense record",
                    createdAt = now
                )
            )

            txId
        }
    }

    /**
     * Atomically records an income transaction.
     */
    suspend fun recordIncome(
        accountId: Long,
        amountPaise: Long,
        occurredAt: Long,
        merchantRaw: String? = null,
        merchantNormalized: String? = null,
        upiReference: String? = null,
        source: TransactionSource = TransactionSource.MANUAL,
        sourceFingerprint: String? = null,
        note: String? = null
    ): Long {
        require(amountPaise > 0L) { "Income amount must be positive" }
        val now = DateTimeUtils.nowEpochMillis()

        return database.withTransaction {
            val tx = TransactionEntity(
                accountId = accountId,
                kind = TransactionKind.INCOME,
                amountPaise = amountPaise,
                merchantRaw = merchantRaw,
                merchantNormalized = merchantNormalized,
                upiReference = upiReference,
                occurredAt = occurredAt,
                createdAt = now,
                updatedAt = now,
                status = TransactionStatus.CATEGORIZED,
                source = source,
                sourceFingerprint = sourceFingerprint,
                note = note
            )
            val txId = transactionDao.insert(tx)

            auditLogDao.insert(
                AuditLogEntity(
                    transactionId = txId,
                    action = AuditAction.CREATED,
                    reason = "Initial income record",
                    createdAt = now
                )
            )

            txId
        }
    }

    /**
     * Atomically records a transfer between two accounts using directed legs (Section 16).
     * Creates an OUT leg on source account and an IN leg on destination account.
     */
    suspend fun recordTransfer(
        fromAccountId: Long,
        toAccountId: Long,
        amountPaise: Long,
        occurredAt: Long,
        source: TransactionSource = TransactionSource.MANUAL,
        note: String? = null
    ): Pair<Long, Long> {
        require(amountPaise > 0L) { "Transfer amount must be positive" }
        require(fromAccountId != toAccountId) { "Cannot transfer to the same account" }

        val now = DateTimeUtils.nowEpochMillis()
        val transferGroupId = System.nanoTime()

        return database.withTransaction {
            val outLeg = TransactionEntity(
                accountId = fromAccountId,
                kind = TransactionKind.TRANSFER,
                amountPaise = amountPaise,
                occurredAt = occurredAt,
                createdAt = now,
                updatedAt = now,
                status = TransactionStatus.CATEGORIZED,
                source = source,
                transferGroupId = transferGroupId,
                transferDirection = TransferDirection.OUT,
                note = note ?: "Transfer OUT"
            )
            val outId = transactionDao.insert(outLeg)

            val inLeg = TransactionEntity(
                accountId = toAccountId,
                kind = TransactionKind.TRANSFER,
                amountPaise = amountPaise,
                occurredAt = occurredAt,
                createdAt = now,
                updatedAt = now,
                status = TransactionStatus.CATEGORIZED,
                source = source,
                transferGroupId = transferGroupId,
                transferDirection = TransferDirection.IN,
                note = note ?: "Transfer IN"
            )
            val inId = transactionDao.insert(inLeg)

            auditLogDao.insert(
                AuditLogEntity(
                    transactionId = outId,
                    action = AuditAction.CREATED,
                    reason = "Transfer OUT leg for group #$transferGroupId",
                    createdAt = now
                )
            )
            auditLogDao.insert(
                AuditLogEntity(
                    transactionId = inId,
                    action = AuditAction.CREATED,
                    reason = "Transfer IN leg for group #$transferGroupId",
                    createdAt = now
                )
            )

            Pair(outId, inId)
        }
    }

    /**
     * Atomically records a refund, optionally linked to original transaction (Section 18).
     */
    suspend fun recordRefund(
        accountId: Long,
        amountPaise: Long,
        occurredAt: Long,
        refundOfTransactionId: Long? = null,
        categoryId: Long? = null,
        merchantRaw: String? = null,
        note: String? = null,
        source: TransactionSource = TransactionSource.MANUAL
    ): Long {
        require(amountPaise > 0L) { "Refund amount must be positive" }
        val now = DateTimeUtils.nowEpochMillis()

        return database.withTransaction {
            val status = if (categoryId != null) TransactionStatus.CATEGORIZED else TransactionStatus.PENDING

            val tx = TransactionEntity(
                accountId = accountId,
                kind = TransactionKind.REFUND,
                amountPaise = amountPaise,
                merchantRaw = merchantRaw,
                occurredAt = occurredAt,
                createdAt = now,
                updatedAt = now,
                status = status,
                source = source,
                refundOfTransactionId = refundOfTransactionId,
                note = note
            )
            val txId = transactionDao.insert(tx)

            if (categoryId != null) {
                transactionAllocationDao.insert(
                    TransactionAllocationEntity(
                        transactionId = txId,
                        categoryId = categoryId,
                        amountPaise = amountPaise,
                        createdAt = now,
                        updatedAt = now
                    )
                )
            }

            auditLogDao.insert(
                AuditLogEntity(
                    transactionId = txId,
                    action = AuditAction.CREATED,
                    reason = "Refund record",
                    createdAt = now
                )
            )

            txId
        }
    }

    /**
     * Atomically categorizes or splits a pending/existing transaction (Section 12).
     */
    suspend fun splitTransaction(
        transactionId: Long,
        allocations: List<Pair<Long, Long>>, // Pair<categoryId, amountPaise>
        reason: String? = null
    ) {
        require(allocations.isNotEmpty()) { "Allocations list cannot be empty" }
        val now = DateTimeUtils.nowEpochMillis()

        database.withTransaction {
            val tx = transactionDao.getById(transactionId)
                ?: throw IllegalArgumentException("Transaction #$transactionId not found")

            val totalAllocated = allocations.sumOf { it.second }
            require(totalAllocated == tx.amountPaise) {
                "Sum of allocations ($totalAllocated) must equal transaction amount (${tx.amountPaise})"
            }

            // Remove existing allocations
            transactionAllocationDao.deleteForTransaction(transactionId)

            // Insert new allocations
            val entities = allocations.map { (catId, amt) ->
                TransactionAllocationEntity(
                    transactionId = transactionId,
                    categoryId = catId,
                    amountPaise = amt,
                    createdAt = now,
                    updatedAt = now
                )
            }
            transactionAllocationDao.insertAll(entities)

            // Update transaction status
            val updatedTx = tx.copy(
                status = TransactionStatus.CATEGORIZED,
                updatedAt = now
            )
            transactionDao.update(updatedTx)

            // Log audit
            auditLogDao.insert(
                AuditLogEntity(
                    transactionId = transactionId,
                    action = if (allocations.size > 1) AuditAction.SPLIT else AuditAction.CATEGORIZED,
                    reason = reason ?: "Categorized/split transaction",
                    createdAt = now
                )
            )
        }
    }

    /**
     * Voids a transaction safely without destructive deletion (Section 59, 73).
     */
    suspend fun voidTransaction(transactionId: Long, reason: String) {
        val now = DateTimeUtils.nowEpochMillis()
        database.withTransaction {
            val tx = transactionDao.getById(transactionId)
                ?: throw IllegalArgumentException("Transaction #$transactionId not found")

            val updated = tx.copy(
                status = TransactionStatus.VOIDED,
                updatedAt = now
            )
            transactionDao.update(updated)

            auditLogDao.insert(
                AuditLogEntity(
                    transactionId = transactionId,
                    action = AuditAction.VOIDED,
                    reason = reason,
                    createdAt = now
                )
            )
        }
    }

    /**
     * Restores a voided transaction (Section 59, 73).
     */
    suspend fun restoreTransaction(transactionId: Long, reason: String) {
        val now = DateTimeUtils.nowEpochMillis()
        database.withTransaction {
            val tx = transactionDao.getById(transactionId)
                ?: throw IllegalArgumentException("Transaction #$transactionId not found")

            val updated = tx.copy(
                status = TransactionStatus.PENDING,
                updatedAt = now
            )
            transactionDao.update(updated)

            auditLogDao.insert(
                AuditLogEntity(
                    transactionId = transactionId,
                    action = AuditAction.RESTORED,
                    reason = reason,
                    createdAt = now
                )
            )
        }
    }
}
