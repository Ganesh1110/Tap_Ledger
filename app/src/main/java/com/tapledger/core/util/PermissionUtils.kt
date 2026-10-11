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

    fun createNotificationListenerSettingsIntent(): android.content.Intent {
        return android.content.Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).apply {
            flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
        }
    }

    fun createAppDetailsSettingsIntent(context: Context): android.content.Intent {
        return android.content.Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = android.net.Uri.fromParts("package", context.packageName, null)
            flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
        }
    }
}
