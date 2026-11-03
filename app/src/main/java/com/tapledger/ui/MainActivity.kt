package com.tapledger.ui

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.app.NotificationCompat
import com.tapledger.R
import com.tapledger.ui.navigation.MainScaffold
import com.tapledger.ui.theme.TapLedgerTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    companion object {
        val navRouteFlow = kotlinx.coroutines.flow.MutableSharedFlow<String>(replay = 1, extraBufferCapacity = 1)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!com.tapledger.BuildConfig.DEBUG) {
            window.setFlags(
                android.view.WindowManager.LayoutParams.FLAG_SECURE,
                android.view.WindowManager.LayoutParams.FLAG_SECURE
            )
        }
        enableEdgeToEdge()
        if (intent?.getBooleanExtra("trigger_test_notification", false) == true) {
            triggerTestNotification()
        }
        intent?.getStringExtra("navigate_to")?.let {
            navRouteFlow.tryEmit(it)
        }
        val startDestination = intent?.getStringExtra("start_destination") ?: com.tapledger.ui.navigation.Screen.Dashboard.route
        val initialScrollIndex = intent?.getIntExtra("scroll_index", 0) ?: 0
        setContent {
            TapLedgerTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainScaffold(
                        startDestination = startDestination,
                        initialDashboardScrollIndex = initialScrollIndex
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.getStringExtra("navigate_to")?.let {
            navRouteFlow.tryEmit(it)
        }
        if (intent.getBooleanExtra("trigger_test_notification", false)) {
            triggerTestNotification()
        }
    }

    override fun onResume() {
        super.onResume()
        if (com.tapledger.core.util.PermissionUtils.isNotificationListenerGranted(this)) {
            com.tapledger.service.FinancialNotificationListenerService.requestRebindService(this)
        }
    }

    fun triggerTestNotification() {
        val channelId = "tapledger_captured_tx"
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
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
        val largeIcon = try {
            BitmapFactory.decodeResource(resources, R.mipmap.ic_launcher)
        } catch (e: Exception) {
            null
        }
        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_notification)
            .apply {
                if (largeIcon != null) setLargeIcon(largeIcon)
            }
            .setColor(0xFF4F46E5.toInt())
            .setContentTitle("New Transaction Captured")
            .setContentText("₹500.00 at Medical Store. A/C **1234")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("₹500.00 at Medical Store (A/C **1234). Tap to categorize in TapLedger Pending Inbox.")
            )
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(99999, notification)
    }
}
