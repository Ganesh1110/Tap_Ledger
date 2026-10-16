package com.tapledger.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import com.tapledger.core.model.MoneyFormatter

object SavingsGuideNotificationHelper {

    const val CHANNEL_ID = "tapledger_savings_guide"
    private const val NOTIFICATION_ID_BASE = 60000

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Savings Transfer Guide",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notifications and reminders for savings transfers"
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun postPaydayPlanNotification(
        context: Context,
        planId: Long,
        amountPaise: Long,
        sourceAccountName: String,
        destAccountName: String
    ) {
        ensureChannel(context)
        val formatted = MoneyFormatter.formatPaiseToRupees(amountPaise)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Payday Savings Plan")
            .setContentText("Move $formatted from $sourceAccountName to $destAccountName")
            .setStyle(NotificationCompat.BigTextStyle().bigText("Payday detected! Move $formatted from $sourceAccountName to $destAccountName to meet your monthly savings goal."))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify((NOTIFICATION_ID_BASE + planId % 10000).toInt(), notification)
    }

    fun postSweepPlanNotification(
        context: Context,
        planId: Long,
        amountPaise: Long,
        sourceAccountName: String,
        destAccountName: String
    ) {
        ensureChannel(context)
        val formatted = MoneyFormatter.formatPaiseToRupees(amountPaise)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Month-End Sweep Plan")
            .setContentText("Sweep $formatted safe leftover to $destAccountName")
            .setStyle(NotificationCompat.BigTextStyle().bigText("Month-end sweep: $formatted leftover from expired budgets is safe to sweep into $destAccountName."))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify((NOTIFICATION_ID_BASE + planId % 10000).toInt(), notification)
    }

    fun postPlanReminderNotification(
        context: Context,
        planId: Long,
        amountPaise: Long
    ) {
        ensureChannel(context)
        val formatted = MoneyFormatter.formatPaiseToRupees(amountPaise)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Pending Transfer Reminder")
            .setContentText("Remember to move $formatted to your savings account")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify((NOTIFICATION_ID_BASE + planId % 10000).toInt(), notification)
    }

    fun postUnverifiedNudgeNotification(
        context: Context,
        planId: Long,
        amountPaise: Long
    ) {
        ensureChannel(context)
        val formatted = MoneyFormatter.formatPaiseToRupees(amountPaise)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Transfer Unconfirmed")
            .setContentText("Transfer of $formatted is waiting for bank alert verification")
            .setStyle(NotificationCompat.BigTextStyle().bigText("Transfer of $formatted marked moved 3+ days ago has not yet been confirmed by bank alert. Check statement or correct balance if needed."))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify((NOTIFICATION_ID_BASE + planId % 10000).toInt(), notification)
    }

    fun postDipGuardNotification(
        context: Context,
        transactionId: Long,
        amountPaise: Long,
        accountName: String
    ) {
        ensureChannel(context)
        val formatted = MoneyFormatter.formatPaiseToRupees(amountPaise)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Spending from savings")
            .setContentText("A debit of $formatted occurred on $accountName. Which goal was this for?")
            .setStyle(NotificationCompat.BigTextStyle().bigText("Spending from savings detected: $formatted debited from $accountName. Tap to attribute this withdrawal to a savings goal."))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify((NOTIFICATION_ID_BASE + 20000 + (transactionId % 10000)).toInt(), notification)
    }
}
