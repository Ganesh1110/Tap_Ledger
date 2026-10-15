package com.tapledger.ui.screens.setup

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tapledger.BuildConfig
import com.tapledger.core.database.TapLedgerDatabase
import com.tapledger.core.util.PermissionUtils
import com.tapledger.ui.theme.ExpenseRed
import com.tapledger.ui.theme.IncomeGreen
import com.tapledger.ui.theme.Spacing
import com.tapledger.ui.theme.WarningAmber

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetupScreen(
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val deviceName = remember {
        val manufacturer = Build.MANUFACTURER.replaceFirstChar { it.uppercase() }
        val model = Build.MODEL
        if (model.startsWith(manufacturer, ignoreCase = true)) model else "$manufacturer $model"
    }

    val isListenerGranted = remember(context) {
        PermissionUtils.isNotificationListenerGranted(context)
    }

    val prefs = remember(context) {
        context.getSharedPreferences("tapledger_setup_prefs", Context.MODE_PRIVATE)
    }

    var autostartConfirmed by remember {
        mutableStateOf(prefs.getBoolean("autostart_confirmed", false))
    }
    var batteryUnrestrictedConfirmed by remember {
        mutableStateOf(prefs.getBoolean("battery_unrestricted_confirmed", false))
    }
    var recentsLockConfirmed by remember {
        mutableStateOf(prefs.getBoolean("recents_lock_confirmed", false))
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Reliability Setup",
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = Spacing.md),
            contentPadding = PaddingValues(top = Spacing.xs, bottom = Spacing.listBottomPadding),
            verticalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            // Header Info Card
            item(key = "header_info") {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(Spacing.md)) {
                        Text(
                            text = "Device: $deviceName",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "TapLedger v${BuildConfig.VERSION_NAME} (Build ${BuildConfig.VERSION_CODE}) • SQLite Schema v${TapLedgerDatabase.DATABASE_VERSION}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(Spacing.xs))
                        Text(
                            text = "To ensure TapLedger accurately logs transactions during long periods between purchases, verify that the system allows background execution without aggressive battery killing.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Requirement 1: Notification Listener Access (Live detectable status)
            item(key = "req_listener") {
                RequirementCard(
                    title = "Automatic Transaction Reader",
                    description = "Enables TapLedger to read incoming bank SMS and payment alerts offline.",
                    status = if (isListenerGranted) Status.Granted else Status.NeedsAction,
                    actionText = if (!isListenerGranted) "Open Listener Settings" else null,
                    onAction = {
                        try {
                            val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                data = Uri.fromParts("package", context.packageName, null)
                            }
                            context.startActivity(intent)
                        }
                    }
                )
            }

            // Requirement 2: Battery Optimization (Unrestricted)
            item(key = "req_battery") {
                RequirementCard(
                    title = "Battery Optimization: Unrestricted",
                    description = "Prevents Android from putting TapLedger to sleep when your phone is locked.",
                    status = if (batteryUnrestrictedConfirmed) Status.Granted else Status.Undetectable,
                    isManualChecked = batteryUnrestrictedConfirmed,
                    onManualCheckChange = {
                        batteryUnrestrictedConfirmed = it
                        prefs.edit().putBoolean("battery_unrestricted_confirmed", it).apply()
                    },
                    manualLabel = "I've set Battery to Unrestricted",
                    actionText = "Open App Battery Settings",
                    onAction = {
                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = Uri.fromParts("package", context.packageName, null)
                        }
                        context.startActivity(intent)
                    }
                )
            }

            // Requirement 3: Autostart / Background Launch
            item(key = "req_autostart") {
                RequirementCard(
                    title = "Autostart Permissions",
                    description = "Allows the app reader to restart automatically if the phone reboots.",
                    status = if (autostartConfirmed) Status.Granted else Status.Undetectable,
                    isManualChecked = autostartConfirmed,
                    onManualCheckChange = {
                        autostartConfirmed = it
                        prefs.edit().putBoolean("autostart_confirmed", it).apply()
                    },
                    manualLabel = "I've enabled Autostart",
                    actionText = "Open App Permissions",
                    onAction = {
                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = Uri.fromParts("package", context.packageName, null)
                        }
                        context.startActivity(intent)
                    }
                )
            }

            // Requirement 4: App Lock in Recents
            item(key = "req_recents") {
                RequirementCard(
                    title = "Lock in Recents Overview",
                    description = "Prevents 'Clear All Recent Apps' from stopping the transaction listener service.",
                    status = if (recentsLockConfirmed) Status.Granted else Status.Undetectable,
                    isManualChecked = recentsLockConfirmed,
                    onManualCheckChange = {
                        recentsLockConfirmed = it
                        prefs.edit().putBoolean("recents_lock_confirmed", it).apply()
                    },
                    manualLabel = "I've locked TapLedger in Recents"
                )
            }

            // Single primary completion button
            item(key = "done_button") {
                Spacer(modifier = Modifier.height(Spacing.xs))
                Button(
                    onClick = onNavigateBack,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text("Done", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

private enum class Status {
    Granted,
    NeedsAction,
    Undetectable
}

@Composable
private fun RequirementCard(
    title: String,
    description: String,
    status: Status,
    modifier: Modifier = Modifier,
    actionText: String? = null,
    onAction: (() -> Unit)? = null,
    isManualChecked: Boolean = false,
    onManualCheckChange: ((Boolean) -> Unit)? = null,
    manualLabel: String? = null
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(modifier = Modifier.padding(Spacing.md)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )

                // Status chip
                val (statusText, statusColor, statusBg) = when (status) {
                    Status.Granted -> Triple("Granted", IncomeGreen, IncomeGreen.copy(alpha = 0.15f))
                    Status.NeedsAction -> Triple("Needs Action", ExpenseRed, ExpenseRed.copy(alpha = 0.15f))
                    Status.Undetectable -> Triple(
                        if (isManualChecked) "Confirmed" else "Manual Check",
                        if (isManualChecked) IncomeGreen else WarningAmber,
                        if (isManualChecked) IncomeGreen.copy(alpha = 0.15f) else WarningAmber.copy(alpha = 0.15f)
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(statusBg)
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = statusText,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = statusColor
                    )
                }
            }

            Spacer(modifier = Modifier.height(Spacing.xs))
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Manual toggle for undetectable requirements
            if (onManualCheckChange != null && manualLabel != null) {
                Spacer(modifier = Modifier.height(Spacing.xs))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = manualLabel,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium
                    )
                    Switch(
                        checked = isManualChecked,
                        onCheckedChange = onManualCheckChange
                    )
                }
            }

            // Action button
            if (actionText != null && onAction != null) {
                Spacer(modifier = Modifier.height(Spacing.xs))
                OutlinedButton(
                    onClick = onAction,
                    contentPadding = PaddingValues(horizontal = Spacing.sm, vertical = 4.dp),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(actionText, fontSize = 12.sp)
                }
            }
        }
    }
}
