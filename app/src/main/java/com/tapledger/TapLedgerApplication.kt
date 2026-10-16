package com.tapledger

import android.app.Application
import com.tapledger.service.NotificationListenerLivenessWorker
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class TapLedgerApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        PDFBoxResourceLoader.init(applicationContext)
        NotificationListenerLivenessWorker.enqueuePeriodicCheck(this)
    }
}
