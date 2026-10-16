package com.tapledger.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.tapledger.ui.navigation.MainScaffold
import com.tapledger.ui.theme.TapLedgerTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(
            android.view.WindowManager.LayoutParams.FLAG_SECURE,
            android.view.WindowManager.LayoutParams.FLAG_SECURE
        )
        enableEdgeToEdge()
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

    override fun onResume() {
        super.onResume()
        if (com.tapledger.core.util.PermissionUtils.isNotificationListenerGranted(this)) {
            com.tapledger.service.FinancialNotificationListenerService.requestRebindService(this)
        }
    }
}
