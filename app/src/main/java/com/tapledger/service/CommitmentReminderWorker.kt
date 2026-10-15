package com.tapledger.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.tapledger.core.database.dao.RecurringCommitmentDao
import com.tapledger.core.model.MoneyFormatter
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.time.LocalDate
import java.util.concurrent.TimeUnit

/**
 * Offline WorkManager worker that posts local reminders a day before the due date (Phase 3).
 * Runs daily without requiring network connectivity.
 */
class CommitmentReminderWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface CommitmentWorkerEntryPoint {
        fun recurringCommitmentDao(): RecurringCommitmentDao
    }

    override suspend fun doWork(): Result {
        val entryPoint = EntryPointAccessors.fromApplication(
            applicationContext,
            CommitmentWorkerEntryPoint::class.java
        )
        val commitmentDao = entryPoint.recurringCommitmentDao()

        val tomorrowDay = LocalDate.now().plusDays(1).dayOfMonth
        val activeCommitments = commitmentDao.getAllActiveSync()
        val dueTomorrow = activeCommitments.filter { it.dueDay == tomorrowDay }

        if (dueTomorrow.isNotEmpty()) {
            val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channelId = CHANNEL_ID

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    channelId,
                    "Commitment Reminders",
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = "Reminders for upcoming SIP, RD, and recurring commitments"
                }
                notificationManager.createNotificationChannel(channel)
            }

            for (commitment in dueTomorrow) {
                val formattedAmount = MoneyFormatter.formatPaiseToRupees(commitment.amountPaise)
                val notification = NotificationCompat.Builder(applicationContext, channelId)
                    .setSmallIcon(android.R.drawable.ic_dialog_info)
                    .setContentTitle("Upcoming ${commitment.type} Reminder")
                    .setContentText("${commitment.name}: $formattedAmount is due tomorrow")
                    .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                    .setAutoCancel(true)
                    .build()

                notificationManager.notify(
                    commitment.id.toInt() + NOTIFICATION_ID_BASE,
                    notification
                )
            }
        }

        return Result.success()
    }

    companion object {
        const val WORK_NAME = "commitment_due_reminders"
        const val CHANNEL_ID = "tapledger_commitment_reminders"
        private const val NOTIFICATION_ID_BASE = 50000

        fun enqueueDailyCheck(context: Context) {
            val constraints = Constraints.Builder().build()
            val request = PeriodicWorkRequestBuilder<CommitmentReminderWorker>(
                24, TimeUnit.HOURS
            )
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }
    }
}
