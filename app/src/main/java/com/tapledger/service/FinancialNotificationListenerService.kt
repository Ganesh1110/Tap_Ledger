package com.tapledger.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
    lateinit var financialIngestionPipeline: FinancialIngestionPipeline

    @Inject
    lateinit var restoreNotificationBuffer: com.tapledger.data.backup.RestoreNotificationBuffer

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    companion object {
        private val _connectedState = MutableStateFlow(false)
        val connectedState: StateFlow<Boolean> = _connectedState.asStateFlow()

        var isConnected: Boolean = false
            internal set(value) {
                field = value
                _connectedState.value = value
            }
        var lastConnectedAt: Long = 0L
            internal set
        var lastDisconnectedAt: Long = 0L
            internal set
        var lastNotificationSeenAt: Long = 0L
            internal set

        fun requestRebindService(context: Context) {
            val componentName = ComponentName(context, FinancialNotificationListenerService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                try {
                    requestRebind(componentName)
                } catch (e: Exception) {
                    Log.w("TapLedgerListener", "Standard requestRebind call failed: ${e.message}")
                }
            }

            // Perform component toggle trick to force Android's NotificationManagerService
            // to reconnect the binder even if the OS killed the process or put the binder in a dead state
            try {
                val pm = context.packageManager
                pm.setComponentEnabledSetting(
                    componentName,
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    PackageManager.DONT_KILL_APP
                )
                pm.setComponentEnabledSetting(
                    componentName,
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                    PackageManager.DONT_KILL_APP
                )
                Log.i("TapLedgerListener", "NotificationListener component toggled for reconnect")
            } catch (e: Exception) {
                Log.w("TapLedgerListener", "Failed to toggle component enabled setting", e)
            }
        }
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        isConnected = true
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
        isConnected = false
        lastDisconnectedAt = DateTimeUtils.nowEpochMillis()
        // Rebind request on disconnection
        requestRebindService(this)
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
        val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString().orEmpty()
        val subText = extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString()
        val tickerText = sbn.notification.tickerText?.toString()

        val textLines = extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)
        val lineTexts = textLines?.mapNotNull { it?.toString() } ?: emptyList()

        val effectiveText = when {
            bigText.isNotBlank() && bigText.length > text.length -> bigText
            text.isNotBlank() -> text
            tickerText != null && tickerText.isNotBlank() -> tickerText
            lineTexts.isNotEmpty() -> lineTexts.joinToString(" ")
            else -> ""
        }

        val messagesList = mutableListOf<String>()
        val msgArray = extras.getParcelableArray(Notification.EXTRA_MESSAGES)
        msgArray?.forEach { item ->
            when (item) {
                is android.os.Bundle -> {
                    val msgText = item.getCharSequence("text")?.toString()
                    if (!msgText.isNullOrBlank()) {
                        messagesList.add(msgText)
                    }
                }
                is CharSequence -> {
                    messagesList.add(item.toString())
                }
                else -> {
                    item?.toString()?.let { str ->
                        if (!str.startsWith("Bundle[")) {
                            messagesList.add(str)
                        }
                    }
                }
            }
        }
        lineTexts.forEach { line ->
            if (line.isNotBlank() && line != effectiveText) {
                messagesList.add(line)
            }
        }
        if (!tickerText.isNullOrBlank() && tickerText != effectiveText && tickerText != title) {
            messagesList.add(tickerText)
        }

        val event = NotificationEvent(
            packageName = pkg,
            title = title,
            text = effectiveText,
            subText = subText,
            messages = messagesList,
            postTime = sbn.postTime
        )

        Log.d("TapLedgerListener", "Notification received from pkg='$pkg' | title='$title'")

        if (restoreNotificationBuffer.isSpoolingActive()) {
            restoreNotificationBuffer.spoolEvent(event)
            return
        }

        serviceScope.launch {
            try {
                val result = financialIngestionPipeline.processEvent(event)
                if (result is IngestionResult.Success) {
                    Log.i("TapLedgerListener", "Ingested tx #${result.transactionId}: ${result.parsedEvent.amountPaise} paise")
                    showCapturedNotification(result)
                }
            } catch (e: Exception) {
                Log.e("TapLedgerListener", "Error processing notification event", e)
            }
        }
    }

    private fun showCapturedNotification(result: IngestionResult.Success) {
        val parsed = result.parsedEvent
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
        val accountDisplay = if (result.resolvedAccountId == null) {
            "Unassigned Account — Tap to resolve"
        } else {
            parsed.accountLast4?.let { "A/C **$it" } ?: "Account"
        }

        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("New Transaction Captured")
            .setContentText("$amountFormatted at $merchant. $accountDisplay")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("$amountFormatted at $merchant ($accountDisplay). Tap to categorize in TapLedger Pending Inbox.")
            )
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(parsed.sourceFingerprint.hashCode(), notification)
    }
}
