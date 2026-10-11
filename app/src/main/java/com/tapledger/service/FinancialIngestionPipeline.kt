package com.tapledger.service

import androidx.room.withTransaction
import com.tapledger.core.database.TapLedgerDatabase
import com.tapledger.core.database.dao.AccountDao
import com.tapledger.core.database.dao.AuditLogDao
import com.tapledger.core.database.dao.BudgetPeriodDao
import com.tapledger.core.database.dao.TransactionDao
import com.tapledger.core.database.dao.UnparsedAlertDao
import com.tapledger.core.database.entity.AuditLogEntity
import com.tapledger.core.database.entity.TransactionEntity
import com.tapledger.core.database.entity.UnparsedAlertEntity
import com.tapledger.core.model.AuditAction
import com.tapledger.core.model.BudgetPeriodStatus
import com.tapledger.core.model.TransactionSource
import com.tapledger.core.model.TransactionStatus
import com.tapledger.core.util.DateTimeUtils
import com.tapledger.data.repository.BudgetRepository
import com.tapledger.parser.FinancialNotificationParser
import com.tapledger.parser.NotificationEvent
import com.tapledger.parser.ParsedFinancialEvent
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

sealed interface IngestionResult {
    data class Success(
        val transactionId: Long,
        val isPossibleDuplicate: Boolean,
        val possibleDuplicateOfId: Long?,
        val resolvedAccountId: Long?,
        val parsedEvent: ParsedFinancialEvent
    ) : IngestionResult

    data class DroppedSystemDuplicate(val hash: String) : IngestionResult
    data class DroppedExactDuplicate(val matchedTransactionId: Long, val upiReference: String?) : IngestionResult
    data class DroppedCrossSourceDuplicate(val matchedTransactionId: Long) : IngestionResult
    data class Unparsed(val reason: String) : IngestionResult
    object IgnoredNonFinancial : IngestionResult
}

@Singleton
class FinancialIngestionPipeline @Inject constructor(
    private val transactionRunner: com.tapledger.core.database.DatabaseTransactionRunner,
    private val transactionDao: TransactionDao,
    private val accountDao: AccountDao,
    private val budgetPeriodDao: BudgetPeriodDao,
    private val budgetRepository: BudgetRepository,
    private val unparsedAlertDao: UnparsedAlertDao,
    private val auditLogDao: AuditLogDao
) {

    // 60-second in-memory circular/TTL cache for raw system events
    private val recentEventHashes = ConcurrentHashMap<String, Long>()

    /**
     * Executes the complete 3-stage ingestion pipeline specified in Flow.md §3.
     */
    suspend fun processEvent(event: NotificationEvent): IngestionResult {
        val now = DateTimeUtils.nowEpochMillis()

        // 1. Stage 0: Drop Duplicate System Event (Seen hash in last 60s)
        val combinedText = buildString {
            if (event.title.isNotBlank()) append(event.title).append(" ")
            if (event.text.isNotBlank()) append(event.text).append(" ")
            if (!event.subText.isNullOrBlank()) append(event.subText).append(" ")
            for (msg in event.messages) {
                append(msg).append(" ")
            }
        }.trim()

        val eventHash = hashSystemEvent(event.packageName, event.postTime, combinedText)
        cleanupOldEventHashes(now)
        val previousTimestamp = recentEventHashes.putIfAbsent(eventHash, now)
        if (previousTimestamp != null && (now - previousTimestamp) <= 60_000L) {
            return IngestionResult.DroppedSystemDuplicate(eventHash)
        }

        // 2. Parser Execution
        val parsed = FinancialNotificationParser.parse(event)
        if (parsed == null) {
            if (FinancialNotificationParser.isPotentialFinancialAlert(combinedText)) {
                unparsedAlertDao.insert(
                    UnparsedAlertEntity(
                        packageName = event.packageName,
                        title = event.title,
                        rawText = combinedText,
                        occurredAt = event.postTime,
                        errorReason = "PARSER_UNMATCHED",
                        createdAt = now
                    )
                )
                return IngestionResult.Unparsed("PARSER_UNMATCHED")
            }
            return IngestionResult.IgnoredNonFinancial
        }

        // 3. Step 1: Resolve Account Attribution (Flow.md §3.1)
        val resolvedAccountId = resolveAccount(parsed)

        // 4. Step 2: Deduplication Check (Flow.md §3.1, §3.2)
        // Check 2.0: Exact match with source_fingerprint (Exact notification redelivery / duplicate)
        val fingerprintMatch = transactionDao.findBySourceFingerprint(parsed.sourceFingerprint)
        if (fingerprintMatch != null) {
            auditLogDao.insert(
                AuditLogEntity(
                    transactionId = fingerprintMatch.id,
                    action = AuditAction.DUPLICATE_DROPPED,
                    reason = "Exact duplicate sourceFingerprint=${parsed.sourceFingerprint}",
                    createdAt = now
                )
            )
            return IngestionResult.DroppedExactDuplicate(fingerprintMatch.id, parsed.upiReference)
        }

        // Check 2A: Exact match with non-null upi_reference
        if (!parsed.upiReference.isNullOrBlank()) {
            val exactMatch = if (resolvedAccountId != null) {
                transactionDao.findByExactKey(
                    accountId = resolvedAccountId,
                    upiRef = parsed.upiReference,
                    direction = parsed.direction,
                    amountPaise = parsed.amountPaise
                ) ?: transactionDao.findByExactKeyUnassigned(
                    upiRef = parsed.upiReference,
                    direction = parsed.direction,
                    amountPaise = parsed.amountPaise
                )
            } else {
                transactionDao.findByUpiDirectionAmountAcrossAllAccounts(
                    upiRef = parsed.upiReference,
                    direction = parsed.direction,
                    amountPaise = parsed.amountPaise
                )
            }

            if (exactMatch != null) {
                auditLogDao.insert(
                    AuditLogEntity(
                        transactionId = exactMatch.id,
                        action = AuditAction.DUPLICATE_DROPPED,
                        reason = "Exact duplicate upiReference=${parsed.upiReference}",
                        createdAt = now
                    )
                )
                return IngestionResult.DroppedExactDuplicate(exactMatch.id, parsed.upiReference)
            }
        }

        // Check 2B: Dedupe without upi_reference (Δt ≤ 60s, same amount + direction)
        // Different package + same merchant -> Drop Duplicate & Log to Audit Trail
        val window60sStart = parsed.occurredAt - 60_000L
        val window60sEnd = parsed.occurredAt + 60_000L
        val candidates60s = transactionDao.findFuzzyCandidates(
            accountId = resolvedAccountId,
            direction = parsed.direction,
            amountPaise = parsed.amountPaise,
            startWindow = window60sStart,
            endWindow = window60sEnd
        )

        val crossSourceMatch = candidates60s.firstOrNull { existing ->
            !parsed.merchantNormalized.isNullOrBlank() &&
                    existing.merchantNormalized.equals(parsed.merchantNormalized, ignoreCase = true) &&
                    isDifferentSourceOrPackage(existing, parsed.source, event.packageName)
        }

        if (crossSourceMatch != null) {
            auditLogDao.insert(
                AuditLogEntity(
                    transactionId = crossSourceMatch.id,
                    action = AuditAction.DUPLICATE_DROPPED,
                    reason = "Cross-source duplicate without upi_reference within 60s",
                    createdAt = now
                )
            )
            return IngestionResult.DroppedCrossSourceDuplicate(crossSourceMatch.id)
        }

        // Check 2C: Candidate Duplicate (Same app / missing merchant: Δt ≤ 10 min + same amount + same direction)
        val window10mStart = parsed.occurredAt - 600_000L
        val window10mEnd = parsed.occurredAt + 600_000L
        val candidates10m = transactionDao.findFuzzyCandidates(
            accountId = resolvedAccountId,
            direction = parsed.direction,
            amountPaise = parsed.amountPaise,
            startWindow = window10mStart,
            endWindow = window10mEnd
        )
        val possibleDuplicateOfId = candidates10m.firstOrNull()?.id

        // 5. Step 3: Get or Create Contiguous Period & Closed-Period Check (Flow.md §3.1, §10.1)
        var effectiveOccurredAt = parsed.occurredAt
        var originalOccurredAt: Long? = null

        // Automated Ingestion Boundary: occurred_at <= now + 1 day
        if (effectiveOccurredAt > now + 86_400_000L) {
            originalOccurredAt = effectiveOccurredAt
            effectiveOccurredAt = now
        }

        // Boundary check: historical occurred_at before first period
        val earliestPeriod = budgetPeriodDao.getEarliestPeriod()
        if (earliestPeriod != null && effectiveOccurredAt < earliestPeriod.startAt) {
            if (originalOccurredAt == null) originalOccurredAt = effectiveOccurredAt
            effectiveOccurredAt = now
        }

        // Closed Period Check: if period containing occurred_at is CLOSED
        val targetPeriod = budgetPeriodDao.getPeriodForTimestamp(effectiveOccurredAt)
        if (targetPeriod != null && targetPeriod.status == BudgetPeriodStatus.CLOSED) {
            if (originalOccurredAt == null) originalOccurredAt = effectiveOccurredAt
            effectiveOccurredAt = now
        }

        // Ensure contiguous period exists for effectiveOccurredAt
        budgetRepository.getOrCreateBudgetPeriodForTimestamp(effectiveOccurredAt)

        // 6. Room Atomic Insert: transactions (status = PENDING)
        val txId = try {
            transactionRunner.run {
                val tx = TransactionEntity(
                    accountId = resolvedAccountId,
                    kind = parsed.kind,
                    direction = parsed.direction,
                    amountPaise = parsed.amountPaise,
                    merchantRaw = parsed.merchantRaw,
                    merchantNormalized = parsed.merchantNormalized,
                    upiReference = parsed.upiReference,
                    occurredAt = effectiveOccurredAt,
                    originalOccurredAt = originalOccurredAt,
                    createdAt = now,
                    updatedAt = now,
                    status = TransactionStatus.PENDING,
                    source = parsed.source,
                    sourceFingerprint = parsed.sourceFingerprint,
                    possibleDuplicateOfId = possibleDuplicateOfId,
                    checkpointBalancePaise = parsed.reportedAvailableBalancePaise,
                    checkpointType = parsed.checkpointType ?: if (parsed.reportedAvailableBalancePaise != null) com.tapledger.core.model.CheckpointType.AVAILABLE_BALANCE else null
                )
                val insertedId = transactionDao.insert(tx)

                auditLogDao.insert(
                    AuditLogEntity(
                        transactionId = insertedId,
                        action = AuditAction.CREATED,
                        reason = "Notification Ingestion from ${event.packageName}",
                        createdAt = now
                    )
                )

                insertedId
            }
        } catch (e: android.database.sqlite.SQLiteConstraintException) {
            val existing = transactionDao.findBySourceFingerprint(parsed.sourceFingerprint)
            val fallbackId = existing?.id ?: 0L
            return IngestionResult.DroppedExactDuplicate(fallbackId, parsed.upiReference)
        }

        return IngestionResult.Success(
            transactionId = txId,
            isPossibleDuplicate = (possibleDuplicateOfId != null),
            possibleDuplicateOfId = possibleDuplicateOfId,
            resolvedAccountId = resolvedAccountId,
            parsedEvent = parsed
        )
    }

    private suspend fun resolveAccount(parsed: ParsedFinancialEvent): Long? {
        if (!parsed.accountLast4.isNullOrBlank()) {
            val matches = accountDao.findAllActiveByLast4(parsed.accountLast4)
            if (matches.size == 1) {
                return matches.first().id
            }
        }

        if (parsed.isCardMentioned) {
            return null // Inbox Quarantine: 'Pick Card'
        }

        val activeBanks = accountDao.getActiveBankAccounts()
        return if (activeBanks.size == 1) {
            activeBanks.first().id
        } else {
            null // Inbox Quarantine: Unmapped account
        }
    }

    private fun isDifferentSourceOrPackage(
        existing: TransactionEntity,
        incomingSource: TransactionSource,
        incomingPackage: String
    ): Boolean {
        if (existing.source != incomingSource) return true
        val existingFingerprint = existing.sourceFingerprint ?: return false
        val firstColon = existingFingerprint.indexOf(':')
        if (firstColon > 0) {
            val existingPkg = existingFingerprint.substring(0, firstColon)
            return !existingPkg.equals(incomingPackage, ignoreCase = true)
        }
        return false
    }

    private fun hashSystemEvent(pkg: String, postTime: Long, text: String): String {
        val input = "$pkg:$postTime:${text.trim().lowercase()}"
        val md = MessageDigest.getInstance("SHA-256")
        val bytes = md.digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun cleanupOldEventHashes(now: Long) {
        val cutoff = now - 60_000L
        val iterator = recentEventHashes.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (entry.value < cutoff) {
                iterator.remove()
            }
        }
    }
}
