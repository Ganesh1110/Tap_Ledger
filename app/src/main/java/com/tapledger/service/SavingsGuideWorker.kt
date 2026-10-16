package com.tapledger.service

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.tapledger.domain.savings.SavingsTriggerCoordinator
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.TimeUnit

/**
 * Offline WorkManager worker that runs daily in the background (Phase 4, Milestone M21).
 * Executes Month-End sweep evaluation and delivers 2-day reminder and 3-day unverified nudges.
 * Zero network permissions required.
 */
class SavingsGuideWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface SavingsGuideWorkerEntryPoint {
        fun savingsTriggerCoordinator(): SavingsTriggerCoordinator
    }

    override suspend fun doWork(): Result {
        val entryPoint = EntryPointAccessors.fromApplication(
            applicationContext,
            SavingsGuideWorkerEntryPoint::class.java
        )
        val coordinator = entryPoint.savingsTriggerCoordinator()

        // 1. Evaluate month-end sweep (last N IST days of open period)
        coordinator.runMonthEndSweepCheck()

        // 2. Evaluate 2-day suggested re-nudges & 3-day unverified nudges
        coordinator.runPendingRemindersCheck()

        return Result.success()
    }

    companion object {
        const val WORK_NAME = "savings_guide_sweep_and_reminders"

        fun enqueueDailyCheck(context: Context) {
            val constraints = Constraints.Builder().build()
            val request = PeriodicWorkRequestBuilder<SavingsGuideWorker>(
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
