package com.tapledger.core.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

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

    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        return powerManager?.isIgnoringBatteryOptimizations(context.packageName) ?: false
    }

    fun createNotificationListenerSettingsIntent(): Intent {
        return Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
    }

    fun createIgnoreBatteryOptimizationsIntent(context: Context): Intent {
        return try {
            Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
        } catch (_: Exception) {
            createAppDetailsSettingsIntent(context)
        }
    }

    fun createAppDetailsSettingsIntent(context: Context): Intent {
        return Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
    }

    @Composable
    fun rememberNotificationListenerGranted(): Boolean {
        val context = LocalContext.current
        val lifecycleOwner = LocalLifecycleOwner.current
        var isGranted by remember { mutableStateOf(isNotificationListenerGranted(context)) }

        DisposableEffect(lifecycleOwner) {
            val observer = LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) {
                    isGranted = isNotificationListenerGranted(context)
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose {
                lifecycleOwner.lifecycle.removeObserver(observer)
            }
        }

        return isGranted
    }
}

