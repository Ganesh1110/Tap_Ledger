package com.tapledger.data.repository

import com.tapledger.core.database.DatabaseTransactionRunner
import com.tapledger.core.database.dao.AuditLogDao
import com.tapledger.core.database.dao.BudgetPeriodDao
import com.tapledger.core.database.dao.SavingsTransferPlanDao
import com.tapledger.core.database.dao.TransactionDao
import com.tapledger.core.database.entity.AuditLogEntity
import com.tapledger.core.database.entity.SavingsTransferPlanEntity
import com.tapledger.core.database.entity.toJsonSnapshot
import com.tapledger.core.model.AuditAction
import com.tapledger.core.model.BudgetPeriodStatus
import com.tapledger.core.model.TransferPlanKind
import com.tapledger.core.model.TransferPlanStatus
import com.tapledger.core.util.DateTimeUtils
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TransferPlanRepository @Inject constructor(
    private val savingsTransferPlanDao: SavingsTransferPlanDao,
    private val transactionDao: TransactionDao,
    private val budgetPeriodDao: BudgetPeriodDao,
    private val auditLogDao: AuditLogDao,
    private val transactionRepository: TransactionRepository,
    private val transactionRunner: DatabaseTransactionRunner
) {

    fun getPlanById(id: Long): Flow<SavingsTransferPlanEntity?> =
        savingsTransferPlanDao.getByIdFlow(id)

    fun getPlansForPeriod(periodId: Long): Flow<List<SavingsTransferPlanEntity>> =
        savingsTransferPlanDao.getPlansForPeriodFlow(periodId)

    fun getActivePlansForPeriod(periodId: Long): Flow<List<SavingsTransferPlanEntity>> =
        savingsTransferPlanDao.getActivePlansForPeriodFlow(periodId)

    fun getByPeriodAndKind(periodId: Long, kind: TransferPlanKind): Flow<SavingsTransferPlanEntity?> =
        savingsTransferPlanDao.getByPeriodAndKindFlow(periodId, kind)

    suspend fun getPlanByIdSync(id: Long): SavingsTransferPlanEntity? =
        savingsTransferPlanDao.getById(id)

    suspend fun getByPeriodAndKindSync(periodId: Long, kind: TransferPlanKind): SavingsTransferPlanEntity? =
        savingsTransferPlanDao.getByPeriodAndKind(periodId, kind)

    /**
     * Idempotently creates or updates a suggested plan for the given (periodId, kind).
     * If an existing plan is in SUGGESTED state, updates the suggested amount.
     * If an existing plan is already MARKED_MOVED or VERIFIED, preserves the active transfer without overwriting.
     */
    suspend fun createOrUpdateSuggestedPlan(
        periodId: Long,
        kind: TransferPlanKind,
        sourceAccountId: Long,
        destAccountId: Long,
        suggestedPaise: Long,
        triggerTransactionId: Long? = null,
        nowEpochMs: Long = DateTimeUtils.nowEpochMillis()
    ): Long {
        require(sourceAccountId != destAccountId) { "Source and destination accounts must be distinct" }
        require(suggestedPaise >= 0L) { "Suggested paise cannot be negative" }

        val period = budgetPeriodDao.getById(periodId)
        if (period?.status == BudgetPeriodStatus.CLOSED) {
            throw IllegalStateException("Cannot create or update plan for a closed budget period")
        }

        return transactionRunner.run {
            val existing = savingsTransferPlanDao.getByPeriodAndKind(periodId, kind)
            if (existing == null) {
                val newPlan = SavingsTransferPlanEntity(
                    periodId = periodId,
                    kind = kind,
                    sourceAccountId = sourceAccountId,
                    destAccountId = destAccountId,
                    suggestedPaise = suggestedPaise,
                    status = TransferPlanStatus.SUGGESTED,
                    triggerTransactionId = triggerTransactionId,
                    createdAt = nowEpochMs
                )
                val id = savingsTransferPlanDao.insert(newPlan)
                auditLogDao.insert(
                    AuditLogEntity(
                        transactionId = null,
                        action = AuditAction.PLAN_CREATED,
                        beforeSnapshotJson = null,
                        afterSnapshotJson = newPlan.copy(id = id).toJsonSnapshot(),
                        reason = "Created $kind savings transfer plan #$id",
                        createdAt = nowEpochMs
                    )
                )
                id
            } else {
                if (existing.status == TransferPlanStatus.SUGGESTED) {
                    val updated = existing.copy(
                        suggestedPaise = suggestedPaise,
                        sourceAccountId = sourceAccountId,
                        destAccountId = destAccountId,
                        triggerTransactionId = triggerTransactionId ?: existing.triggerTransactionId
                    )
                    savingsTransferPlanDao.update(updated)
                    existing.id
                } else {
                    // Plan is already MARKED_MOVED, VERIFIED, or DISMISSED; do not disturb its state
                    existing.id
                }
            }
        }
    }

    /**
     * Marks a plan as moved (Flow.md §18, D013, D014).
     * Runs in one Room transaction:
     * 1. Records two-legged transfer (OUT leg CATEGORIZED to savings category, IN leg CONFIRMED).
     * 2. Links the generated transfer_group_id to the plan.
     * 3. Sets plan status to MARKED_MOVED.
     */
    suspend fun markAsMoved(
        planId: Long,
        movedPaise: Long,
        savingsCategoryId: Long,
        movedAtEpochMs: Long = DateTimeUtils.nowEpochMillis()
    ): Pair<Long, Long> {
        require(movedPaise > 0L) { "Moved amount must be positive" }

        return transactionRunner.run {
            val plan = savingsTransferPlanDao.getById(planId)
                ?: throw IllegalArgumentException("Savings transfer plan #$planId not found")

            val period = budgetPeriodDao.getById(plan.periodId)
            if (period?.status == BudgetPeriodStatus.CLOSED) {
                throw IllegalStateException("Cannot mark plan as moved for a closed budget period")
            }

            if (plan.status != TransferPlanStatus.SUGGESTED && plan.status != TransferPlanStatus.MARKED_MOVED) {
                throw IllegalStateException("Cannot mark plan #$planId as moved from status ${plan.status}")
            }

            // Create two-legged transfer
            val (outId, inId) = transactionRepository.recordTransfer(
                fromAccountId = plan.sourceAccountId,
                toAccountId = plan.destAccountId,
                amountPaise = movedPaise,
                occurredAt = movedAtEpochMs,
                savingsCategoryId = savingsCategoryId,
                note = "Savings Transfer (${plan.kind.name})"
            )

            val outLeg = transactionDao.getById(outId)
            val transferGroupId = outLeg?.transferGroupId

            val updatedPlan = plan.copy(
                status = TransferPlanStatus.MARKED_MOVED,
                movedPaise = movedPaise,
                movedAt = movedAtEpochMs,
                transferGroupId = transferGroupId
            )
            savingsTransferPlanDao.update(updatedPlan)

            auditLogDao.insert(
                AuditLogEntity(
                    transactionId = outId,
                    action = AuditAction.PLAN_MOVED,
                    beforeSnapshotJson = plan.toJsonSnapshot(),
                    afterSnapshotJson = updatedPlan.toJsonSnapshot(),
                    reason = "Marked plan #$planId as moved ($movedPaise paise)",
                    createdAt = movedAtEpochMs
                )
            )

            Pair(outId, inId)
        }
    }

    /**
     * Adopts a matching bank alert to verify the plan (D014, Milestone M20).
     */
    suspend fun verifyPlan(
        planId: Long,
        verifiedAtEpochMs: Long = DateTimeUtils.nowEpochMillis(),
        reason: String = "Verified by bank alert"
    ) {
        transactionRunner.run {
            val plan = savingsTransferPlanDao.getById(planId)
                ?: throw IllegalArgumentException("Savings transfer plan #$planId not found")

            if (plan.status != TransferPlanStatus.MARKED_MOVED) {
                throw IllegalStateException("Cannot verify plan #$planId in status ${plan.status}")
            }

            val updatedPlan = plan.copy(
                status = TransferPlanStatus.VERIFIED,
                verifiedAt = verifiedAtEpochMs
            )
            savingsTransferPlanDao.update(updatedPlan)

            auditLogDao.insert(
                AuditLogEntity(
                    transactionId = null,
                    action = AuditAction.PLAN_ADOPTED,
                    beforeSnapshotJson = plan.toJsonSnapshot(),
                    afterSnapshotJson = updatedPlan.toJsonSnapshot(),
                    reason = reason,
                    createdAt = verifiedAtEpochMs
                )
            )
        }
    }

    /**
     * User dismisses the plan for this period.
     */
    suspend fun dismissPlan(
        planId: Long,
        nowEpochMs: Long = DateTimeUtils.nowEpochMillis()
    ) {
        transactionRunner.run {
            val plan = savingsTransferPlanDao.getById(planId) ?: return@run
            if (plan.status == TransferPlanStatus.VERIFIED) {
                throw IllegalStateException("Cannot dismiss an already verified plan")
            }

            val updatedPlan = plan.copy(status = TransferPlanStatus.DISMISSED)
            savingsTransferPlanDao.update(updatedPlan)

            auditLogDao.insert(
                AuditLogEntity(
                    transactionId = null,
                    action = AuditAction.PLAN_DISMISSED,
                    beforeSnapshotJson = plan.toJsonSnapshot(),
                    afterSnapshotJson = updatedPlan.toJsonSnapshot(),
                    reason = "User dismissed transfer plan #$planId",
                    createdAt = nowEpochMs
                )
            )
        }
    }

    /**
     * Expires all SUGGESTED plans for a period upon period close (Flow.md §18, Section 8).
     */
    suspend fun expireSuggestedPlansForPeriod(periodId: Long) {
        savingsTransferPlanDao.expireSuggestedPlansForPeriod(periodId)
    }

    suspend fun findMarkedMovedPlans(): List<SavingsTransferPlanEntity> =
        savingsTransferPlanDao.findMarkedMovedPlans()

    fun findMarkedMovedPlansFlow(): Flow<List<SavingsTransferPlanEntity>> =
        savingsTransferPlanDao.findMarkedMovedPlansFlow()

    /**
     * Confirms a matching two-legged transfer detected by the user or system (Alerts-First Path, Flow.md §18, D014).
     * Upgrades the plan from SUGGESTED to VERIFIED, links transferGroupId and records audit trail.
     */
    suspend fun confirmPlanFromMatchingTransfer(
        planId: Long,
        transferGroupId: String,
        amountPaise: Long,
        nowEpochMs: Long = DateTimeUtils.nowEpochMillis()
    ) {
        transactionRunner.run {
            val plan = savingsTransferPlanDao.getById(planId)
                ?: throw IllegalArgumentException("Savings transfer plan #$planId not found")

            val updatedPlan = plan.copy(
                status = TransferPlanStatus.VERIFIED,
                movedPaise = amountPaise,
                transferGroupId = transferGroupId,
                movedAt = nowEpochMs,
                verifiedAt = nowEpochMs
            )
            savingsTransferPlanDao.update(updatedPlan)

            auditLogDao.insert(
                AuditLogEntity(
                    transactionId = null,
                    action = AuditAction.PLAN_ADOPTED,
                    beforeSnapshotJson = plan.toJsonSnapshot(),
                    afterSnapshotJson = updatedPlan.toJsonSnapshot(),
                    reason = "Plan confirmed from matching transfer group $transferGroupId",
                    createdAt = nowEpochMs
                )
            )
        }
    }
}
