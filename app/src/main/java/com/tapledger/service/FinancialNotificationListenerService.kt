package com.tapledger.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.core.app.NotificationCompat
import com.tapledger.R
import com.tapledger.core.database.dao.AccountDao
import com.tapledger.core.database.dao.TransactionDao
import com.tapledger.core.model.MoneyFormatter
import com.tapledger.core.model.TransactionKind
import com.tapledger.core.model.TransactionSource
import com.tapledger.core.model.TransactionStatus
import com.tapledger.core.model.TransferDirection
import com.tapledger.core.util.DateTimeUtils
import com.tapledger.data.repository.TransactionRepository
import com.tapledger.parser.FinancialNotificationParser
import com.tapledger.parser.NotificationEvent
import com.tapledger.ui.MainActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class FinancialNotificationListenerService : NotificationListenerService() {

    @Inject
    lateinit var transactionRepository: TransactionRepository

    @Inject
    lateinit var transactionDao: TransactionDao

    @Inject
    lateinit var accountDao: AccountDao

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    companion object {
        var lastConnectedAt: Long = 0L
            private set
        var lastDisconnectedAt: Long = 0L
            private set
        var lastNotificationSeenAt: Long = 0L
            private set
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        lastConnectedAt = DateTimeUtils.nowEpochMillis()

        // Section 38: Active notification recovery
        try {
            val activeNotifications = getActiveNotifications()
            activeNotifications?.forEach { sbn ->
                processStatusBarNotification(sbn)
            }
        } catch (e: Exception) {
            // Ignore recovery failure if permissions or OS restriction
        }
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        lastDisconnectedAt = DateTimeUtils.nowEpochMillis()
        // Section 39: Rebind request on disconnection
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            requestRebind(ComponentName(this, FinancialNotificationListenerService::class.java))
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        if (sbn == null) return
        processStatusBarNotification(sbn)
    }

    private fun processStatusBarNotification(sbn: StatusBarNotification) {
        lastNotificationSeenAt = DateTimeUtils.nowEpochMillis()
        val pkg = sbn.packageName ?: return
        if (pkg == packageName) return // Ignore TapLedger's own notifications

        val extras = sbn.notification.extras ?: return
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
        val subText = extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString()

        // Section 20: Extract EXTRA_MESSAGES for grouped notifications
        val messagesList = mutableListOf<String>()
        val msgArray = extras.getParcelableArray(Notification.EXTRA_MESSAGES)
        msgArray?.forEach {
            it?.toString()?.let { str -> messagesList.add(str) }
        }

        val event = NotificationEvent(
            packageName = pkg,
            title = title,
            text = text,
            subText = subText,
            messages = messagesList,
            postTime = sbn.postTime
        )

        Log.d("TapLedgerListener", "Notification received from pkg='$pkg' | title='$title' | text='$text'")

        val parsed = FinancialNotificationParser.parse(event)
        if (parsed == null) {
            Log.d("TapLedgerListener", "Ignored: No financial transaction data parsed from '$title $text'")
            return
        }

        Log.i("TapLedgerListener", "Parsed successfully: ${parsed.amountPaise} paise | merchant=${parsed.merchantRaw} | acct=${parsed.accountLast4}")

        serviceScope.launch {
            handleParsedEvent(parsed)
        }
    }

    private suspend fun handleParsedEvent(parsed: com.tapledger.parser.ParsedFinancialEvent) {
        // Level 0 Deduplication: Exact notification fingerprint (Section 22)
        val existingByFingerprint = transactionDao.findBySourceFingerprint(parsed.sourceFingerprint)
        if (existingByFingerprint != null) {
            return // Exact duplicate notification -> auto-dedupe
        }

        // Level 1 Deduplication: Matching UPI reference (Section 22)
        if (!parsed.upiReference.isNullOrBlank()) {
            val existingByUpi = transactionDao.findByUpiReference(parsed.upiReference)
            if (existingByUpi != null) {
                return // Same financial event -> auto-dedupe
            }
        }

        // Map account identifier (last 4 digits) or fallback to default primary account
        val matchedAccount = if (!parsed.accountLast4.isNullOrBlank()) {
            accountDao.findByLast4(parsed.accountLast4)
        } else {
            null
        } ?: accountDao.getAllAccountsSync().firstOrNull() ?: return

        // Ingest into transaction repository
        if (parsed.kind == TransactionKind.EXPENSE) {
            transactionRepository.recordExpense(
                accountId = matchedAccount.id,
                amountPaise = parsed.amountPaise,
                occurredAt = parsed.occurredAt,
                merchantRaw = parsed.merchantRaw,
                merchantNormalized = parsed.merchantNormalized,
                upiReference = parsed.upiReference,
                source = parsed.source,
                sourceFingerprint = parsed.sourceFingerprint,
                allocations = emptyList() // Remains PENDING for user categorization
            )
        } else if (parsed.kind == TransactionKind.REFUND) {
            transactionRepository.recordRefund(
                accountId = matchedAccount.id,
                amountPaise = parsed.amountPaise,
                occurredAt = parsed.occurredAt,
                merchantRaw = parsed.merchantRaw,
                source = parsed.source
            )
        } else if (parsed.kind == TransactionKind.INCOME) {
            transactionRepository.recordIncome(
                accountId = matchedAccount.id,
                amountPaise = parsed.amountPaise,
                occurredAt = parsed.occurredAt,
                merchantRaw = parsed.merchantRaw,
                merchantNormalized = parsed.merchantNormalized,
                upiReference = parsed.upiReference,
                source = parsed.source,
                sourceFingerprint = parsed.sourceFingerprint
            )
        }

        // Post local TapLedger notification so user can tap to open app & categorize
        showCapturedNotification(parsed)
    }

    private fun showCapturedNotification(parsed: com.tapledger.parser.ParsedFinancialEvent) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "tapledger_captured_tx"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Captured Transactions",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notifications for newly captured financial transactions"
            }
            notificationManager.createNotificationChannel(channel)
        }

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val amountFormatted = MoneyFormatter.formatRupees(parsed.amountPaise)
        val merchant = parsed.merchantRaw ?: "Transaction"

        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("New Transaction Captured")
            .setContentText("$amountFormatted at $merchant. Tap to categorize.")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("$amountFormatted at $merchant (${parsed.accountLast4?.let { "A/C **$it" } ?: "Account"}). Tap to categorize in TapLedger Pending Inbox.")
            )
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(parsed.sourceFingerprint.hashCode(), notification)
    }
}
