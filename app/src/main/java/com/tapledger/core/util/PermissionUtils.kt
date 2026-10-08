package com.tapledger.core.util

import android.content.Context
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat

object PermissionUtils {

    fun isNotificationListenerGranted(context: Context): Boolean {
        val packageName = context.packageName
        val enabledPackages = NotificationManagerCompat.getEnabledListenerPackages(context)
        if (enabledPackages.contains(packageName)) {
            return true
        }
        val flat = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
        return flat != null && flat.contains(packageName)
    }
}
