package com.tapledger

import android.app.Application
import androidx.work.WorkManager
import com.tapledger.service.NotificationListenerLivenessWorker
import com.tapledger.service.SavingsGuideWorker
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class TapLedgerApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        NotificationListenerLivenessWorker.enqueuePeriodicCheck(this)
        WorkManager.getInstance(this).cancelUniqueWork(SavingsGuideWorker.WORK_NAME)
    }
}
