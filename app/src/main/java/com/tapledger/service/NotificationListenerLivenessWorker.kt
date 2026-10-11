package com.tapledger.service

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.tapledger.core.util.DateTimeUtils
import com.tapledger.core.util.PermissionUtils
import java.util.concurrent.TimeUnit

/**
 * Background WorkManager worker for offline liveness checking per Flow.md §14.2.
 * Strictly runs offline (no network constraints). Verifies NotificationListenerService
 * connectivity and invokes rebind protocol on disconnection or stalled state.
 */
class NotificationListenerLivenessWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val context = applicationContext
        val isGranted = PermissionUtils.isNotificationListenerGranted(context)
        if (!isGranted) {
            return Result.success()
        }

        val isConnected = FinancialNotificationListenerService.isConnected
        if (!isConnected || FinancialNotificationListenerService.lastDisconnectedAt > FinancialNotificationListenerService.lastConnectedAt) {
            FinancialNotificationListenerService.requestRebindService(context)
        }

        return Result.success()
    }

    companion object {
        const val WORK_NAME = "notification_listener_liveness_check"

        /**
         * Enqueues 15-minute periodic offline liveness check per Flow.md §14.2.
         * Runs without internet or network constraints.
         */
        fun enqueuePeriodicCheck(context: Context) {
            val constraints = Constraints.Builder().build() // strictly offline / no network
            val request = PeriodicWorkRequestBuilder<NotificationListenerLivenessWorker>(
                15, TimeUnit.MINUTES
            )
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }

        /**
         * Adaptive health check per Flow.md §14.2:
         * Warns only when gap exceeds 3x average inter-arrival time.
         */
        fun isAdaptiveHealthStalled(
            lastSeenAt: Long,
            averageInterArrivalMillis: Long,
            now: Long = DateTimeUtils.nowEpochMillis()
        ): Boolean {
            if (lastSeenAt <= 0L || averageInterArrivalMillis <= 0L) return false
            val gap = now - lastSeenAt
            return gap > 3 * averageInterArrivalMillis
        }
    }
}
