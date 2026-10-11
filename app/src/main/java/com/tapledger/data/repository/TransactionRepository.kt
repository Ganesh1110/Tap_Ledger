package com.tapledger.data.repository

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
import com.tapledger.core.util.DateTimeUtils
import kotlinx.coroutines.flow.Flow
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TransactionRepository @Inject constructor(
    private val transactionRunner: DatabaseTransactionRunner,
    private val transactionDao: TransactionDao,
    private val transactionAllocationDao: TransactionAllocationDao,
    private val auditLogDao: AuditLogDao
) {

    private fun TransactionEntity.toJsonSnapshot(): String = buildString {
        append("{")
        append("\"id\":").append(id).append(",")
        append("\"accountId\":").append(accountId).append(",")
        append("\"kind\":\"").append(kind.name).append("\",")
        append("\"direction\":\"").append(direction.name).append("\",")
        append("\"amountPaise\":").append(amountPaise).append(",")
        append("\"status\":\"").append(status.name).append("\",")
        append("\"previousStatus\":").append(previousStatus?.let { "\"$it\"" } ?: "null").append(",")
        append("\"source\":\"").append(source.name).append("\",")
        append("\"occurredAt\":").append(occurredAt)
        append("}")
    }

    fun observeAllTransactions(): Flow<List<TransactionEntity>> = transactionDao.observeAllTransactions()

    fun observePendingTransactions(): Flow<List<TransactionEntity>> = transactionDao.observePendingTransactions()

    fun observePendingCount(): Flow<Int> = transactionDao.observePendingCount()

    suspend fun getTransactionById(id: Long): TransactionEntity? = transactionDao.getById(id)

    suspend fun getAllocationsForTransaction(transactionId: Long): List<TransactionAllocationEntity> =
        transactionAllocationDao.getForTransaction(transactionId)

    /**
     * Atomically records an expense (Flow.md §4).
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
            require(allocations.all { it.second > 0L }) { "Split allocation amount must be positive" }
            require(allocations.map { it.first }.toSet().size == allocations.size) { "Duplicate category in split" }
        }

        return transactionRunner.run {
            val status = if (isCategorized) TransactionStatus.CATEGORIZED else TransactionStatus.PENDING

            val tx = TransactionEntity(
                accountId = accountId,
                kind = TransactionKind.EXPENSE,
                direction = TransactionDirection.OUT,
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
                notes = note
            )
            val txId = transactionDao.insert(tx)

            if (isCategorized) {
                val allocEntities = allocations.map { (catId, amt) ->
                    TransactionAllocationEntity(
                        transactionId = txId,
                        categoryId = catId,
                        amountPaise = amt
                    )
                }
                transactionAllocationDao.insertAll(allocEntities)
            }

            val savedTx = tx.copy(id = txId)
            auditLogDao.insert(
                AuditLogEntity(
                    transactionId = txId,
                    action = AuditAction.CREATED,
                    beforeSnapshotJson = null,
                    afterSnapshotJson = savedTx.toJsonSnapshot(),
                    reason = "Initial expense record",
                    createdAt = now
                )
            )

            txId
        }
    }

    /**
     * Atomically records an income transaction (Flow.md §4: [*] -> CONFIRMED).
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

        return transactionRunner.run {
            val tx = TransactionEntity(
                accountId = accountId,
                kind = TransactionKind.INCOME,
                direction = TransactionDirection.IN,
                amountPaise = amountPaise,
                merchantRaw = merchantRaw,
                merchantNormalized = merchantNormalized,
                upiReference = upiReference,
                occurredAt = occurredAt,
                createdAt = now,
                updatedAt = now,
                status = TransactionStatus.CONFIRMED,
                source = source,
                sourceFingerprint = sourceFingerprint,
                notes = note
            )
            val txId = transactionDao.insert(tx)

            val savedTx = tx.copy(id = txId)
            auditLogDao.insert(
                AuditLogEntity(
                    transactionId = txId,
                    action = AuditAction.CREATED,
                    beforeSnapshotJson = null,
                    afterSnapshotJson = savedTx.toJsonSnapshot(),
                    reason = "Initial income record",
                    createdAt = now
                )
            )

            txId
        }
    }

    /**
     * Atomically records a transfer between two accounts using directed legs (Flow.md §4, §7.1).
     * Both legs seeded as CONFIRMED.
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
        val transferGroupId = UUID.randomUUID().toString()

        return transactionRunner.run {
            val outLeg = TransactionEntity(
                accountId = fromAccountId,
                kind = TransactionKind.TRANSFER,
                direction = TransactionDirection.OUT,
                amountPaise = amountPaise,
                occurredAt = occurredAt,
                createdAt = now,
                updatedAt = now,
                status = TransactionStatus.CONFIRMED,
                source = source,
                transferGroupId = transferGroupId,
                counterpartyAccountId = toAccountId,
                notes = note ?: "Transfer OUT"
            )
            val outId = transactionDao.insert(outLeg)

            val inLeg = TransactionEntity(
                accountId = toAccountId,
                kind = TransactionKind.TRANSFER,
                direction = TransactionDirection.IN,
                amountPaise = amountPaise,
                occurredAt = occurredAt,
                createdAt = now,
                updatedAt = now,
                status = TransactionStatus.CONFIRMED,
                source = source,
                transferGroupId = transferGroupId,
                counterpartyAccountId = fromAccountId,
                notes = note ?: "Transfer IN"
            )
            val inId = transactionDao.insert(inLeg)

            auditLogDao.insert(
                AuditLogEntity(
                    transactionId = outId,
                    action = AuditAction.CREATED,
                    beforeSnapshotJson = null,
                    afterSnapshotJson = outLeg.copy(id = outId).toJsonSnapshot(),
                    reason = "Transfer OUT leg for group #$transferGroupId",
                    createdAt = now
                )
            )
            auditLogDao.insert(
                AuditLogEntity(
                    transactionId = inId,
                    action = AuditAction.CREATED,
                    beforeSnapshotJson = null,
                    afterSnapshotJson = inLeg.copy(id = inId).toJsonSnapshot(),
                    reason = "Transfer IN leg for group #$transferGroupId",
                    createdAt = now
                )
            )

            Pair(outId, inId)
        }
    }

    /**
     * Atomically records a refund, optionally linked to original transaction (Flow.md §4, §9.2).
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

        return transactionRunner.run {
            val status = if (categoryId != null) TransactionStatus.CATEGORIZED else TransactionStatus.PENDING

            val tx = TransactionEntity(
                accountId = accountId,
                kind = TransactionKind.REFUND,
                direction = TransactionDirection.IN,
                amountPaise = amountPaise,
                merchantRaw = merchantRaw,
                occurredAt = occurredAt,
                createdAt = now,
                updatedAt = now,
                status = status,
                source = source,
                refundOfTransactionId = refundOfTransactionId,
                notes = note
            )
            val txId = transactionDao.insert(tx)

            if (categoryId != null) {
                transactionAllocationDao.insert(
                    TransactionAllocationEntity(
                        transactionId = txId,
                        categoryId = categoryId,
                        amountPaise = amountPaise
                    )
                )
            }

            auditLogDao.insert(
                AuditLogEntity(
                    transactionId = txId,
                    action = AuditAction.CREATED,
                    beforeSnapshotJson = null,
                    afterSnapshotJson = tx.copy(id = txId).toJsonSnapshot(),
                    reason = "Refund record",
                    createdAt = now
                )
            )

            txId
        }
    }

    /**
     * Confirms a transaction (Flow.md §4: PENDING -> CONFIRMED).
     * Invariant: tx.accountId cannot be NULL (Flow.md §3.1).
     */
    suspend fun confirmTransaction(transactionId: Long, reason: String = "Confirmed by user") {
        val now = DateTimeUtils.nowEpochMillis()
        transactionRunner.run {
            val tx = transactionDao.getById(transactionId)
                ?: throw IllegalArgumentException("Transaction #$transactionId not found")

            require(tx.accountId != null) {
                "Cannot confirm a transaction with unassigned account (account_id is NULL)"
            }
            require(tx.status == TransactionStatus.PENDING) {
                "Can only confirm a PENDING transaction (current status: ${tx.status})"
            }

            val updatedTx = tx.copy(
                status = TransactionStatus.CONFIRMED,
                updatedAt = now
            )
            transactionDao.update(updatedTx)

            auditLogDao.insert(
                AuditLogEntity(
                    transactionId = transactionId,
                    action = AuditAction.UPDATED,
                    beforeSnapshotJson = tx.toJsonSnapshot(),
                    afterSnapshotJson = updatedTx.toJsonSnapshot(),
                    reason = reason,
                    createdAt = now
                )
            )
        }
    }

    /**
     * Un-confirms a transaction (Flow.md §4: CONFIRMED -> PENDING).
     */
    suspend fun unconfirmTransaction(transactionId: Long, reason: String = "Unconfirmed by user") {
        val now = DateTimeUtils.nowEpochMillis()
        transactionRunner.run {
            val tx = transactionDao.getById(transactionId)
                ?: throw IllegalArgumentException("Transaction #$transactionId not found")

            require(tx.status == TransactionStatus.CONFIRMED) {
                "Can only un-confirm a CONFIRMED transaction (current status: ${tx.status})"
            }

            val updatedTx = tx.copy(
                status = TransactionStatus.PENDING,
                updatedAt = now
            )
            transactionDao.update(updatedTx)

            auditLogDao.insert(
                AuditLogEntity(
                    transactionId = transactionId,
                    action = AuditAction.UPDATED,
                    beforeSnapshotJson = tx.toJsonSnapshot(),
                    afterSnapshotJson = updatedTx.toJsonSnapshot(),
                    reason = reason,
                    createdAt = now
                )
            )
        }
    }

    /**
     * Atomically categorizes or splits a pending/categorized transaction (Flow.md §4, §9.1).
     * Invariant: A transaction cannot transition to CATEGORIZED while account_id is NULL (Flow.md §3.1).
     */
    suspend fun splitTransaction(
        transactionId: Long,
        allocations: List<Pair<Long, Long>>, // Pair<categoryId, amountPaise>
        reason: String? = null
    ) {
        require(allocations.isNotEmpty()) { "Allocations list cannot be empty" }
        val now = DateTimeUtils.nowEpochMillis()

        transactionRunner.run {
            val tx = transactionDao.getById(transactionId)
                ?: throw IllegalArgumentException("Transaction #$transactionId not found")

            require(tx.accountId != null) {
                "Cannot categorize a transaction with unassigned account (account_id is NULL)"
            }
            require(tx.status == TransactionStatus.PENDING || tx.status == TransactionStatus.CATEGORIZED) {
                "Cannot categorize transaction with status ${tx.status}"
            }

            val totalAllocated = allocations.sumOf { it.second }
            require(totalAllocated == tx.amountPaise) {
                "Sum of allocations ($totalAllocated) must equal transaction amount (${tx.amountPaise})"
            }
            require(allocations.all { it.second > 0L }) { "Split allocation amount must be positive" }
            require(allocations.map { it.first }.toSet().size == allocations.size) { "Duplicate category in split" }

            // Remove existing allocations
            transactionAllocationDao.deleteForTransaction(transactionId)

            // Insert new allocations
            val entities = allocations.map { (catId, amt) ->
                TransactionAllocationEntity(
                    transactionId = transactionId,
                    categoryId = catId,
                    amountPaise = amt
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
                    beforeSnapshotJson = tx.toJsonSnapshot(),
                    afterSnapshotJson = updatedTx.toJsonSnapshot(),
                    reason = reason ?: "Categorized/split transaction",
                    createdAt = now
                )
            )
        }
    }

    /**
     * Un-categorizes a categorized transaction (Flow.md §4: CATEGORIZED -> PENDING).
     * Deletes all envelope split allocations and reverts status to PENDING.
     */
    suspend fun uncategorizeTransaction(transactionId: Long, reason: String = "Uncategorized by user") {
        val now = DateTimeUtils.nowEpochMillis()
        transactionRunner.run {
            val tx = transactionDao.getById(transactionId)
                ?: throw IllegalArgumentException("Transaction #$transactionId not found")

            require(tx.status == TransactionStatus.CATEGORIZED) {
                "Can only uncategorize a CATEGORIZED transaction (current status: ${tx.status})"
            }

            transactionAllocationDao.deleteForTransaction(transactionId)

            val updatedTx = tx.copy(
                status = TransactionStatus.PENDING,
                updatedAt = now
            )
            transactionDao.update(updatedTx)

            auditLogDao.insert(
                AuditLogEntity(
                    transactionId = transactionId,
                    action = AuditAction.UNCATEGORIZED,
                    beforeSnapshotJson = tx.toJsonSnapshot(),
                    afterSnapshotJson = updatedTx.toJsonSnapshot(),
                    reason = reason,
                    createdAt = now
                )
            )
        }
    }

    /**
     * Resolves an unassigned account on a pending transaction (Flow.md §3.1).
     */
    suspend fun assignAccount(transactionId: Long, accountId: Long) {
        val now = DateTimeUtils.nowEpochMillis()
        transactionRunner.run {
            val tx = transactionDao.getById(transactionId)
                ?: throw IllegalArgumentException("Transaction #$transactionId not found")

            val updatedTx = tx.copy(
                accountId = accountId,
                updatedAt = now
            )
            transactionDao.update(updatedTx)

            auditLogDao.insert(
                AuditLogEntity(
                    transactionId = transactionId,
                    action = AuditAction.UPDATED,
                    beforeSnapshotJson = tx.toJsonSnapshot(),
                    afterSnapshotJson = updatedTx.toJsonSnapshot(),
                    reason = "Assigned account #$accountId to transaction",
                    createdAt = now
                )
            )
        }
    }

    /**
     * Ignores a transaction (Flow.md §4: PENDING -> IGNORED).
     * Permitted even when account_id is NULL (§3.1 exemption). Excluded from financial math.
     */
    suspend fun ignoreTransaction(transactionId: Long, reason: String = "Ignored by user") {
        val now = DateTimeUtils.nowEpochMillis()
        transactionRunner.run {
            val tx = transactionDao.getById(transactionId)
                ?: throw IllegalArgumentException("Transaction #$transactionId not found")

            require(tx.status == TransactionStatus.PENDING) {
                "Can only ignore a PENDING transaction (current status: ${tx.status})"
            }

            val updatedTx = tx.copy(
                status = TransactionStatus.IGNORED,
                updatedAt = now
            )
            transactionDao.update(updatedTx)

            auditLogDao.insert(
                AuditLogEntity(
                    transactionId = transactionId,
                    action = AuditAction.IGNORED,
                    beforeSnapshotJson = tx.toJsonSnapshot(),
                    afterSnapshotJson = updatedTx.toJsonSnapshot(),
                    reason = reason,
                    createdAt = now
                )
            )
        }
    }

    /**
     * Un-ignores an ignored transaction (Flow.md §4: IGNORED -> PENDING).
     */
    suspend fun unignoreTransaction(transactionId: Long, reason: String = "Unignored by user") {
        val now = DateTimeUtils.nowEpochMillis()
        transactionRunner.run {
            val tx = transactionDao.getById(transactionId)
                ?: throw IllegalArgumentException("Transaction #$transactionId not found")

            require(tx.status == TransactionStatus.IGNORED) {
                "Can only unignore an IGNORED transaction (current status: ${tx.status})"
            }

            val updatedTx = tx.copy(
                status = TransactionStatus.PENDING,
                updatedAt = now
            )
            transactionDao.update(updatedTx)

            auditLogDao.insert(
                AuditLogEntity(
                    transactionId = transactionId,
                    action = AuditAction.RESTORED,
                    beforeSnapshotJson = tx.toJsonSnapshot(),
                    afterSnapshotJson = updatedTx.toJsonSnapshot(),
                    reason = reason,
                    createdAt = now
                )
            )
        }
    }

    /**
     * Voids a transaction safely without destructive deletion (Flow.md §4).
     * Permitted from PENDING, CONFIRMED, CATEGORIZED, IGNORED.
     * Records previous_status = current_status for reversible restore.
     * Permitted even when account_id is NULL (§3.1 exemption).
     */
    suspend fun voidTransaction(transactionId: Long, reason: String = "User voided") {
        val now = DateTimeUtils.nowEpochMillis()
        transactionRunner.run {
            val tx = transactionDao.getById(transactionId)
                ?: throw IllegalArgumentException("Transaction #$transactionId not found")

            require(tx.status != TransactionStatus.VOIDED) {
                "Transaction #$transactionId is already VOIDED"
            }

            val updatedTx = tx.copy(
                status = TransactionStatus.VOIDED,
                previousStatus = tx.status,
                updatedAt = now
            )
            transactionDao.update(updatedTx)

            auditLogDao.insert(
                AuditLogEntity(
                    transactionId = transactionId,
                    action = AuditAction.VOIDED,
                    beforeSnapshotJson = tx.toJsonSnapshot(),
                    afterSnapshotJson = updatedTx.toJsonSnapshot(),
                    reason = reason,
                    createdAt = now
                )
            )
        }
    }

    /**
     * Restores a voided transaction to its previous status (Flow.md §4).
     * VOIDED -> PENDING (when previous_status == PENDING)
     * VOIDED -> CATEGORIZED (when previous_status == CATEGORIZED)
     * VOIDED -> CONFIRMED (when previous_status == CONFIRMED)
     * VOIDED -> IGNORED (when previous_status == IGNORED)
     * Invariant: Restoring to CONFIRMED or CATEGORIZED requires account_id != NULL (Flow.md §3.1).
     */
    suspend fun restoreTransaction(transactionId: Long, reason: String = "User restored") {
        val now = DateTimeUtils.nowEpochMillis()
        transactionRunner.run {
            val tx = transactionDao.getById(transactionId)
                ?: throw IllegalArgumentException("Transaction #$transactionId not found")

            require(tx.status == TransactionStatus.VOIDED) {
                "Can only restore a VOIDED transaction (current status: ${tx.status})"
            }

            val targetStatus = tx.previousStatus ?: TransactionStatus.PENDING

            if (targetStatus == TransactionStatus.CONFIRMED || targetStatus == TransactionStatus.CATEGORIZED) {
                require(tx.accountId != null) {
                    "Cannot restore to $targetStatus while account_id is NULL (Flow.md §3.1)"
                }
            }

            val updatedTx = tx.copy(
                status = targetStatus,
                previousStatus = null,
                updatedAt = now
            )
            transactionDao.update(updatedTx)

            auditLogDao.insert(
                AuditLogEntity(
                    transactionId = transactionId,
                    action = AuditAction.RESTORED,
                    beforeSnapshotJson = tx.toJsonSnapshot(),
                    afterSnapshotJson = updatedTx.toJsonSnapshot(),
                    reason = reason,
                    createdAt = now
                )
            )
        }
    }
}
