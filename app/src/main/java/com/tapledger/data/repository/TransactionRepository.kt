package com.tapledger.data.repository

import com.tapledger.core.database.DatabaseTransactionRunner
import com.tapledger.core.database.dao.AccountDao
import com.tapledger.core.database.dao.AuditLogDao
import com.tapledger.core.database.dao.BudgetPeriodDao
import com.tapledger.core.database.dao.CategoryDao
import com.tapledger.core.database.dao.TransactionAllocationDao
import com.tapledger.core.database.dao.TransactionDao
import com.tapledger.core.database.entity.AuditLogEntity
import com.tapledger.core.database.entity.TransactionAllocationEntity
import com.tapledger.core.database.entity.TransactionEntity
import com.tapledger.core.model.AccountType
import com.tapledger.core.model.AuditAction
import com.tapledger.core.model.BudgetBucket
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
    private val auditLogDao: AuditLogDao,
    private val accountDao: AccountDao,
    private val categoryDao: CategoryDao,
    private val budgetPeriodDao: BudgetPeriodDao
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

    private suspend fun isPeriodClosed(timestamp: Long): Boolean {
        val period = budgetPeriodDao.getClosedPeriodForTimestamp(timestamp)
        return period != null
    }

    private suspend fun checkTransferClosedPeriodBoundary(tx: TransactionEntity) {
        if (tx.transferGroupId != null) {
            val legs = transactionDao.getTransferLegs(tx.transferGroupId)
            for (leg in legs) {
                if (isPeriodClosed(leg.occurredAt)) {
                    throw IllegalStateException("Cannot alter transfer linked to a closed budget period")
                }
            }
        } else {
            if (isPeriodClosed(tx.occurredAt)) {
                throw IllegalStateException("Cannot alter transaction in a closed budget period")
            }
        }
    }

    /**
     * Atomically records a transfer between two accounts using directed legs (Flow.md §4, §7.1).
     * If destination account is savings, optionally applies a SAVINGS category to OUT leg.
     */
    suspend fun recordTransfer(
        fromAccountId: Long,
        toAccountId: Long,
        amountPaise: Long,
        occurredAt: Long,
        source: TransactionSource = TransactionSource.MANUAL,
        note: String? = null,
        savingsCategoryId: Long? = null
    ): Pair<Long, Long> {
        require(amountPaise > 0L) { "Transfer amount must be positive" }
        require(fromAccountId != toAccountId) { "Cannot transfer to the same account" }

        val now = DateTimeUtils.nowEpochMillis()
        val transferGroupId = UUID.randomUUID().toString()

        return transactionRunner.run {
            if (isPeriodClosed(occurredAt)) {
                throw IllegalStateException("Cannot alter transfer linked to a closed budget period")
            }

            val destAccount = accountDao.getById(toAccountId)
            val srcAccount = accountDao.getById(fromAccountId)

            val isDestSavings = destAccount?.let { it.isSavings || it.type == AccountType.INVESTMENT } ?: false
            val isSrcSavings = srcAccount?.let { it.isSavings || it.type == AccountType.INVESTMENT } ?: false

            var outStatus = TransactionStatus.CONFIRMED
            var inStatus = TransactionStatus.CONFIRMED

            if (isDestSavings) {
                if (savingsCategoryId != null) {
                    val category = categoryDao.getById(savingsCategoryId)
                    require(category?.defaultBucket == BudgetBucket.SAVINGS) {
                        "Savings transfer allocation must target a SAVINGS bucket category (Flow.md §8.3)"
                    }
                    outStatus = TransactionStatus.CATEGORIZED
                    inStatus = TransactionStatus.CONFIRMED
                } else {
                    outStatus = TransactionStatus.PENDING
                    inStatus = TransactionStatus.CONFIRMED
                }
            } else if (isSrcSavings) {
                if (savingsCategoryId != null) {
                    val category = categoryDao.getById(savingsCategoryId)
                    require(category?.defaultBucket == BudgetBucket.SAVINGS) {
                        "Savings transfer allocation must target a SAVINGS bucket category (Flow.md §8.3)"
                    }
                    inStatus = TransactionStatus.CATEGORIZED
                    outStatus = TransactionStatus.CONFIRMED
                } else {
                    inStatus = TransactionStatus.PENDING
                    outStatus = TransactionStatus.CONFIRMED
                }
            }

            val outLeg = TransactionEntity(
                accountId = fromAccountId,
                kind = TransactionKind.TRANSFER,
                direction = TransactionDirection.OUT,
                amountPaise = amountPaise,
                occurredAt = occurredAt,
                createdAt = now,
                updatedAt = now,
                status = outStatus,
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
                status = inStatus,
                source = source,
                transferGroupId = transferGroupId,
                counterpartyAccountId = fromAccountId,
                notes = note ?: "Transfer IN"
            )
            val inId = transactionDao.insert(inLeg)

            if (isDestSavings && savingsCategoryId != null) {
                transactionAllocationDao.insert(
                    TransactionAllocationEntity(
                        transactionId = outId,
                        categoryId = savingsCategoryId,
                        amountPaise = amountPaise
                    )
                )
            } else if (isSrcSavings && savingsCategoryId != null) {
                transactionAllocationDao.insert(
                    TransactionAllocationEntity(
                        transactionId = inId,
                        categoryId = savingsCategoryId,
                        amountPaise = amountPaise
                    )
                )
            }

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
     * Queries unlinked candidate PENDING transactions in different accounts with matching amount,
     * opposite direction within 24 hours, excluding CLOSED periods (Flow.md §7.1.1 & Amendment A2).
     */
    suspend fun findTransferPairCandidates(transaction: TransactionEntity): List<TransactionEntity> {
        val accountId = transaction.accountId ?: return emptyList()
        if (isPeriodClosed(transaction.occurredAt)) return emptyList()

        val oppositeDirection = if (transaction.direction == TransactionDirection.OUT) {
            TransactionDirection.IN
        } else {
            TransactionDirection.OUT
        }
        val startWindow = transaction.occurredAt - 86400_000L
        val endWindow = transaction.occurredAt + 86400_000L

        return transactionDao.findTransferPairCandidates(
            excludeAccountId = accountId,
            oppositeDirection = oppositeDirection,
            amountPaise = transaction.amountPaise,
            startWindow = startWindow,
            endWindow = endWindow
        )
    }

    /**
     * Atomically links two candidate transfer legs into a paired transfer (Flow.md §7.1).
     * Assigns shared transfer_group_id (UUID), reciprocal counterparty_account_id, kind = TRANSFER.
     * If destination account is savings, prompts for/applies SAVINGS category or leaves OUT leg PENDING.
     */
    suspend fun confirmTransferPair(
        leg1Id: Long,
        leg2Id: Long,
        savingsCategoryId: Long? = null
    ): String {
        val now = DateTimeUtils.nowEpochMillis()
        val transferGroupId = UUID.randomUUID().toString()

        return transactionRunner.run {
            val leg1 = transactionDao.getById(leg1Id)
                ?: throw IllegalArgumentException("Transaction #$leg1Id not found")
            val leg2 = transactionDao.getById(leg2Id)
                ?: throw IllegalArgumentException("Transaction #$leg2Id not found")

            require(leg1.accountId != null && leg2.accountId != null) {
                "Cannot pair transactions with unassigned accounts"
            }
            require(leg1.accountId != leg2.accountId) {
                "Cannot transfer to the same account"
            }
            require(leg1.amountPaise == leg2.amountPaise) {
                "Transfer pair amounts must match (${leg1.amountPaise} vs ${leg2.amountPaise})"
            }
            require(leg1.direction != leg2.direction) {
                "Transfer pair must have opposite directions"
            }
            require(leg1.transferGroupId == null && leg2.transferGroupId == null) {
                "One or both transactions are already paired"
            }

            if (isPeriodClosed(leg1.occurredAt) || isPeriodClosed(leg2.occurredAt)) {
                throw IllegalStateException("Cannot alter transfer linked to a closed budget period")
            }

            val (outLeg, inLeg) = if (leg1.direction == TransactionDirection.OUT) Pair(leg1, leg2) else Pair(leg2, leg1)
            val destAccount = accountDao.getById(inLeg.accountId!!)
            val srcAccount = accountDao.getById(outLeg.accountId!!)

            val isDestSavings = destAccount?.let { it.isSavings || it.type == AccountType.INVESTMENT } ?: false
            val isSrcSavings = srcAccount?.let { it.isSavings || it.type == AccountType.INVESTMENT } ?: false

            var finalOutStatus = TransactionStatus.CONFIRMED
            var finalInStatus = TransactionStatus.CONFIRMED

            if (isDestSavings) {
                // Savings contribution: OUT leg is operational and categorized to SAVINGS; IN leg is CONFIRMED
                if (savingsCategoryId != null) {
                    val category = categoryDao.getById(savingsCategoryId)
                    require(category?.defaultBucket == BudgetBucket.SAVINGS) {
                        "Savings transfer allocation must target a SAVINGS bucket category (Flow.md §8.3)"
                    }
                    finalOutStatus = TransactionStatus.CATEGORIZED
                    finalInStatus = TransactionStatus.CONFIRMED
                    transactionAllocationDao.deleteForTransaction(outLeg.id)
                    transactionAllocationDao.insert(
                        TransactionAllocationEntity(
                            transactionId = outLeg.id,
                            categoryId = savingsCategoryId,
                            amountPaise = outLeg.amountPaise
                        )
                    )
                } else {
                    finalOutStatus = TransactionStatus.PENDING
                    finalInStatus = TransactionStatus.CONFIRMED
                }
            } else if (isSrcSavings) {
                // Savings withdrawal: IN leg is operational and categorized to SAVINGS; OUT leg is CONFIRMED
                if (savingsCategoryId != null) {
                    val category = categoryDao.getById(savingsCategoryId)
                    require(category?.defaultBucket == BudgetBucket.SAVINGS) {
                        "Savings transfer allocation must target a SAVINGS bucket category (Flow.md §8.3)"
                    }
                    finalInStatus = TransactionStatus.CATEGORIZED
                    finalOutStatus = TransactionStatus.CONFIRMED
                    transactionAllocationDao.deleteForTransaction(inLeg.id)
                    transactionAllocationDao.insert(
                        TransactionAllocationEntity(
                            transactionId = inLeg.id,
                            categoryId = savingsCategoryId,
                            amountPaise = inLeg.amountPaise
                        )
                    )
                } else {
                    finalInStatus = TransactionStatus.PENDING
                    finalOutStatus = TransactionStatus.CONFIRMED
                }
            } else {
                // Standard transfer: both CONFIRMED, zero allocations
                transactionAllocationDao.deleteForTransaction(outLeg.id)
                transactionAllocationDao.deleteForTransaction(inLeg.id)
            }

            val updatedOut = outLeg.copy(
                kind = TransactionKind.TRANSFER,
                transferGroupId = transferGroupId,
                counterpartyAccountId = inLeg.accountId,
                status = finalOutStatus,
                updatedAt = now
            )
            val updatedIn = inLeg.copy(
                kind = TransactionKind.TRANSFER,
                transferGroupId = transferGroupId,
                counterpartyAccountId = outLeg.accountId,
                status = finalInStatus,
                updatedAt = now
            )

            transactionDao.update(updatedOut)
            transactionDao.update(updatedIn)

            auditLogDao.insert(
                AuditLogEntity(
                    transactionId = updatedOut.id,
                    action = AuditAction.UPDATED,
                    beforeSnapshotJson = outLeg.toJsonSnapshot(),
                    afterSnapshotJson = updatedOut.toJsonSnapshot(),
                    reason = "Linked transfer pair (group #$transferGroupId)",
                    createdAt = now
                )
            )
            auditLogDao.insert(
                AuditLogEntity(
                    transactionId = updatedIn.id,
                    action = AuditAction.UPDATED,
                    beforeSnapshotJson = inLeg.toJsonSnapshot(),
                    afterSnapshotJson = updatedIn.toJsonSnapshot(),
                    reason = "Linked transfer pair (group #$transferGroupId)",
                    createdAt = now
                )
            )

            transferGroupId
        }
    }

    /**
     * Manually generates a counterparty leg to turn a single transaction into a two-legged transfer (Flow.md §7.1.2).
     */
    suspend fun markAsTransfer(
        sourceTransactionId: Long,
        destinationAccountId: Long,
        savingsCategoryId: Long? = null
    ): String {
        val now = DateTimeUtils.nowEpochMillis()
        val transferGroupId = UUID.randomUUID().toString()

        return transactionRunner.run {
            val sourceTx = transactionDao.getById(sourceTransactionId)
                ?: throw IllegalArgumentException("Transaction #$sourceTransactionId not found")

            require(sourceTx.accountId != null) {
                "Cannot mark unassigned transaction as transfer"
            }
            require(sourceTx.accountId != destinationAccountId) {
                "Cannot transfer to the same account"
            }
            require(sourceTx.transferGroupId == null) {
                "Transaction is already paired in transfer group #${sourceTx.transferGroupId}"
            }

            if (isPeriodClosed(sourceTx.occurredAt)) {
                throw IllegalStateException("Cannot alter transfer linked to a closed budget period")
            }

            val isSourceOut = sourceTx.direction == TransactionDirection.OUT
            val oppositeDirection = if (isSourceOut) TransactionDirection.IN else TransactionDirection.OUT

            val destAccount = accountDao.getById(destinationAccountId)
                ?: throw IllegalArgumentException("Destination account #$destinationAccountId not found")
            val srcAccount = accountDao.getById(sourceTx.accountId!!)
                ?: throw IllegalArgumentException("Source account #${sourceTx.accountId} not found")

            val isDestSavings = (if (isSourceOut) destAccount else srcAccount).let { it.isSavings || it.type == AccountType.INVESTMENT }
            val isSrcSavings = (if (isSourceOut) srcAccount else destAccount).let { it.isSavings || it.type == AccountType.INVESTMENT }

            var outStatus = TransactionStatus.CONFIRMED
            var inStatus = TransactionStatus.CONFIRMED

            if (isDestSavings) {
                if (savingsCategoryId != null) {
                    val category = categoryDao.getById(savingsCategoryId)
                    require(category?.defaultBucket == BudgetBucket.SAVINGS) {
                        "Savings transfer allocation must target a SAVINGS bucket category (Flow.md §8.3)"
                    }
                    outStatus = TransactionStatus.CATEGORIZED
                    inStatus = TransactionStatus.CONFIRMED
                } else {
                    outStatus = TransactionStatus.PENDING
                    inStatus = TransactionStatus.CONFIRMED
                }
            } else if (isSrcSavings) {
                if (savingsCategoryId != null) {
                    val category = categoryDao.getById(savingsCategoryId)
                    require(category?.defaultBucket == BudgetBucket.SAVINGS) {
                        "Savings transfer allocation must target a SAVINGS bucket category (Flow.md §8.3)"
                    }
                    inStatus = TransactionStatus.CATEGORIZED
                    outStatus = TransactionStatus.CONFIRMED
                } else {
                    inStatus = TransactionStatus.PENDING
                    outStatus = TransactionStatus.CONFIRMED
                }
            }

            val sourceStatus = if (isSourceOut) outStatus else inStatus
            val counterpartyStatus = if (isSourceOut) inStatus else outStatus

            val updatedSource = sourceTx.copy(
                kind = TransactionKind.TRANSFER,
                transferGroupId = transferGroupId,
                counterpartyAccountId = destinationAccountId,
                status = sourceStatus,
                updatedAt = now
            )
            transactionDao.update(updatedSource)

            val counterpartyLeg = TransactionEntity(
                accountId = destinationAccountId,
                kind = TransactionKind.TRANSFER,
                direction = oppositeDirection,
                amountPaise = sourceTx.amountPaise,
                occurredAt = sourceTx.occurredAt,
                createdAt = now,
                updatedAt = now,
                status = counterpartyStatus,
                source = sourceTx.source,
                transferGroupId = transferGroupId,
                counterpartyAccountId = sourceTx.accountId,
                notes = if (oppositeDirection == TransactionDirection.IN) "Transfer IN" else "Transfer OUT"
            )
            val counterpartyId = transactionDao.insert(counterpartyLeg)

            val (outId, inId) = if (isSourceOut) Pair(sourceTx.id, counterpartyId) else Pair(counterpartyId, sourceTx.id)
            if (isDestSavings && savingsCategoryId != null) {
                transactionAllocationDao.deleteForTransaction(outId)
                transactionAllocationDao.insert(
                    TransactionAllocationEntity(
                        transactionId = outId,
                        categoryId = savingsCategoryId,
                        amountPaise = sourceTx.amountPaise
                    )
                )
            } else if (isSrcSavings && savingsCategoryId != null) {
                transactionAllocationDao.deleteForTransaction(inId)
                transactionAllocationDao.insert(
                    TransactionAllocationEntity(
                        transactionId = inId,
                        categoryId = savingsCategoryId,
                        amountPaise = sourceTx.amountPaise
                    )
                )
            }

            auditLogDao.insert(
                AuditLogEntity(
                    transactionId = updatedSource.id,
                    action = AuditAction.UPDATED,
                    beforeSnapshotJson = sourceTx.toJsonSnapshot(),
                    afterSnapshotJson = updatedSource.toJsonSnapshot(),
                    reason = "Marked as transfer (group #$transferGroupId)",
                    createdAt = now
                )
            )
            auditLogDao.insert(
                AuditLogEntity(
                    transactionId = counterpartyId,
                    action = AuditAction.CREATED,
                    beforeSnapshotJson = null,
                    afterSnapshotJson = counterpartyLeg.copy(id = counterpartyId).toJsonSnapshot(),
                    reason = "Created counterparty transfer leg (group #$transferGroupId)",
                    createdAt = now
                )
            )

            transferGroupId
        }
    }

    /**
     * Updates amount across both legs of a paired transfer and enforces Rule A1 re-split guard (§7.1.3, §7.1.4).
     */
    suspend fun updateTransferAmount(
        transferGroupId: String,
        newAmountPaise: Long,
        newAllocations: List<Pair<Long, Long>>? = null
    ) {
        require(newAmountPaise > 0L) { "Transfer amount must be positive" }
        val now = DateTimeUtils.nowEpochMillis()

        transactionRunner.run {
            val legs = transactionDao.getTransferLegs(transferGroupId)
            require(legs.isNotEmpty()) { "No transfer legs found for group $transferGroupId" }

            for (leg in legs) {
                if (isPeriodClosed(leg.occurredAt)) {
                    throw IllegalStateException("Cannot alter transfer linked to a closed budget period")
                }
            }

            for (leg in legs) {
                if (leg.status == TransactionStatus.CATEGORIZED) {
                    val allocationsToSet = if (newAllocations != null) {
                        require(newAllocations.sumOf { it.second } == newAmountPaise) {
                            "Sum of allocations must equal new transfer amount (Rule A1)"
                        }
                        newAllocations
                    } else {
                        val existing = transactionAllocationDao.getForTransaction(leg.id)
                        if (existing.size == 1) {
                            listOf(Pair(existing[0].categoryId, newAmountPaise))
                        } else {
                            throw IllegalArgumentException("Split adjustment allocations required when editing amount of split transfer leg (Rule A1)")
                        }
                    }

                    transactionAllocationDao.deleteForTransaction(leg.id)
                    transactionAllocationDao.insertAll(
                        allocationsToSet.map { (catId, amt) ->
                            TransactionAllocationEntity(
                                transactionId = leg.id,
                                categoryId = catId,
                                amountPaise = amt
                            )
                        }
                    )
                }

                val updatedLeg = leg.copy(
                    amountPaise = newAmountPaise,
                    updatedAt = now
                )
                transactionDao.update(updatedLeg)

                auditLogDao.insert(
                    AuditLogEntity(
                        transactionId = leg.id,
                        action = AuditAction.UPDATED,
                        beforeSnapshotJson = leg.toJsonSnapshot(),
                        afterSnapshotJson = updatedLeg.toJsonSnapshot(),
                        reason = "Updated transfer amount to $newAmountPaise paise",
                        createdAt = now
                    )
                )
            }
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

            checkTransferClosedPeriodBoundary(tx)

            require(tx.accountId != null) {
                "Cannot categorize a transaction with unassigned account (account_id is NULL)"
            }

            if (tx.kind == TransactionKind.TRANSFER) {
                val thisAccount = tx.accountId?.let { accountDao.getById(it) }
                val isThisSavings = thisAccount?.let { it.isSavings || it.type == AccountType.INVESTMENT } ?: false
                require(!isThisSavings) {
                    "Savings account leg of a transfer cannot have category allocations (Flow.md §8.3)"
                }

                val counterpartyAccount = tx.counterpartyAccountId?.let { accountDao.getById(it) }
                val isCounterpartySavings = counterpartyAccount?.let { it.isSavings || it.type == AccountType.INVESTMENT } ?: false
                require(isCounterpartySavings) {
                    "Standard non-savings transfers cannot have budgetary category allocations (Flow.md §7.2)"
                }

                for ((catId, _) in allocations) {
                    val category = categoryDao.getById(catId)
                    require(category?.defaultBucket == BudgetBucket.SAVINGS) {
                        "Savings transfer allocations must target SAVINGS bucket categories (Flow.md §8.3)"
                    }
                }
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

            checkTransferClosedPeriodBoundary(tx)

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
     * Automatically synchronizes across paired transfer legs (§7.1.3).
     */
    suspend fun ignoreTransaction(transactionId: Long, reason: String = "Ignored by user") {
        val now = DateTimeUtils.nowEpochMillis()
        transactionRunner.run {
            val tx = transactionDao.getById(transactionId)
                ?: throw IllegalArgumentException("Transaction #$transactionId not found")

            checkTransferClosedPeriodBoundary(tx)

            require(tx.status == TransactionStatus.PENDING) {
                "Can only ignore a PENDING transaction (current status: ${tx.status})"
            }

            val legsToIgnore = if (tx.transferGroupId != null) {
                transactionDao.getTransferLegs(tx.transferGroupId)
            } else {
                listOf(tx)
            }

            for (leg in legsToIgnore) {
                if (leg.status == TransactionStatus.IGNORED) continue
                val updatedLeg = leg.copy(
                    status = TransactionStatus.IGNORED,
                    updatedAt = now
                )
                transactionDao.update(updatedLeg)

                auditLogDao.insert(
                    AuditLogEntity(
                        transactionId = leg.id,
                        action = AuditAction.IGNORED,
                        beforeSnapshotJson = leg.toJsonSnapshot(),
                        afterSnapshotJson = updatedLeg.toJsonSnapshot(),
                        reason = reason,
                        createdAt = now
                    )
                )
            }
        }
    }

    /**
     * Un-ignores an ignored transaction (Flow.md §4: IGNORED -> PENDING).
     * Automatically synchronizes across paired transfer legs (§7.1.3).
     */
    suspend fun unignoreTransaction(transactionId: Long, reason: String = "Unignored by user") {
        val now = DateTimeUtils.nowEpochMillis()
        transactionRunner.run {
            val tx = transactionDao.getById(transactionId)
                ?: throw IllegalArgumentException("Transaction #$transactionId not found")

            checkTransferClosedPeriodBoundary(tx)

            require(tx.status == TransactionStatus.IGNORED) {
                "Can only unignore an IGNORED transaction (current status: ${tx.status})"
            }

            val legsToUnignore = if (tx.transferGroupId != null) {
                transactionDao.getTransferLegs(tx.transferGroupId)
            } else {
                listOf(tx)
            }

            for (leg in legsToUnignore) {
                if (leg.status != TransactionStatus.IGNORED) continue
                val updatedLeg = leg.copy(
                    status = TransactionStatus.PENDING,
                    updatedAt = now
                )
                transactionDao.update(updatedLeg)

                auditLogDao.insert(
                    AuditLogEntity(
                        transactionId = leg.id,
                        action = AuditAction.RESTORED,
                        beforeSnapshotJson = leg.toJsonSnapshot(),
                        afterSnapshotJson = updatedLeg.toJsonSnapshot(),
                        reason = reason,
                        createdAt = now
                    )
                )
            }
        }
    }

    /**
     * Voids a transaction safely without destructive deletion (Flow.md §4).
     * Permitted from PENDING, CONFIRMED, CATEGORIZED, IGNORED.
     * Records previous_status = current_status for reversible restore.
     * Automatically synchronizes across paired transfer legs (§7.1.3).
     */
    suspend fun voidTransaction(transactionId: Long, reason: String = "User voided") {
        val now = DateTimeUtils.nowEpochMillis()
        transactionRunner.run {
            val tx = transactionDao.getById(transactionId)
                ?: throw IllegalArgumentException("Transaction #$transactionId not found")

            checkTransferClosedPeriodBoundary(tx)

            require(tx.status != TransactionStatus.VOIDED) {
                "Transaction #$transactionId is already VOIDED"
            }

            val legsToVoid = if (tx.transferGroupId != null) {
                transactionDao.getTransferLegs(tx.transferGroupId)
            } else {
                listOf(tx)
            }

            for (leg in legsToVoid) {
                if (leg.status == TransactionStatus.VOIDED) continue
                val updatedLeg = leg.copy(
                    status = TransactionStatus.VOIDED,
                    previousStatus = leg.status,
                    updatedAt = now
                )
                transactionDao.update(updatedLeg)

                auditLogDao.insert(
                    AuditLogEntity(
                        transactionId = leg.id,
                        action = AuditAction.VOIDED,
                        beforeSnapshotJson = leg.toJsonSnapshot(),
                        afterSnapshotJson = updatedLeg.toJsonSnapshot(),
                        reason = reason,
                        createdAt = now
                    )
                )
            }
        }
    }

    /**
     * Restores a voided transaction to its previous status (Flow.md §4).
     * VOIDED -> PENDING (when previous_status == PENDING)
     * VOIDED -> CATEGORIZED (when previous_status == CATEGORIZED)
     * VOIDED -> CONFIRMED (when previous_status == CONFIRMED)
     * VOIDED -> IGNORED (when previous_status == IGNORED)
     * Invariant: Restoring to CONFIRMED or CATEGORIZED requires account_id != NULL (Flow.md §3.1).
     * Automatically synchronizes across paired transfer legs (§7.1.3).
     */
    suspend fun restoreTransaction(transactionId: Long, reason: String = "User restored") {
        val now = DateTimeUtils.nowEpochMillis()
        transactionRunner.run {
            val tx = transactionDao.getById(transactionId)
                ?: throw IllegalArgumentException("Transaction #$transactionId not found")

            checkTransferClosedPeriodBoundary(tx)

            require(tx.status == TransactionStatus.VOIDED) {
                "Can only restore a VOIDED transaction (current status: ${tx.status})"
            }

            val legsToRestore = if (tx.transferGroupId != null) {
                transactionDao.getTransferLegs(tx.transferGroupId)
            } else {
                listOf(tx)
            }

            for (leg in legsToRestore) {
                if (leg.status != TransactionStatus.VOIDED) continue
                val targetStatus = leg.previousStatus ?: TransactionStatus.PENDING

                if (targetStatus == TransactionStatus.CONFIRMED || targetStatus == TransactionStatus.CATEGORIZED) {
                    require(leg.accountId != null) {
                        "Cannot restore to $targetStatus while account_id is NULL (Flow.md §3.1)"
                    }
                }

                val updatedLeg = leg.copy(
                    status = targetStatus,
                    previousStatus = null,
                    updatedAt = now
                )
                transactionDao.update(updatedLeg)

                auditLogDao.insert(
                    AuditLogEntity(
                        transactionId = leg.id,
                        action = AuditAction.RESTORED,
                        beforeSnapshotJson = leg.toJsonSnapshot(),
                        afterSnapshotJson = updatedLeg.toJsonSnapshot(),
                        reason = reason,
                        createdAt = now
                    )
                )
            }
        }
    }
}
