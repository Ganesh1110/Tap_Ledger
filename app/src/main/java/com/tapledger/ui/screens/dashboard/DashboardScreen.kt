package com.tapledger.ui.screens.dashboard

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.tapledger.core.model.MoneyFormatter
import com.tapledger.core.util.DateTimeUtils
import com.tapledger.core.util.PermissionUtils
import com.tapledger.domain.model.AccountBalanceSummary
import com.tapledger.domain.model.BudgetCategorySummary
import com.tapledger.ui.theme.AmberWarning
import com.tapledger.ui.theme.EmeraldPrimary
import com.tapledger.ui.theme.RoseError

@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel = hiltViewModel(),
    onNavigateToManualEntry: () -> Unit,
    onNavigateToTransactions: () -> Unit,
    onNavigateToPending: () -> Unit,
    onNavigateToAccounts: () -> Unit,
    onNavigateToBackup: () -> Unit,
    onNavigateToSetup: () -> Unit,
    onNavigateToCategories: () -> Unit,
    onNavigateToStatements: () -> Unit
) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsState()

    // Request POST_NOTIFICATIONS runtime permission on Android 13+ (API 33)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val postNotificationLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission()
        ) { }

        LaunchedEffect(Unit) {
            if (ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                postNotificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    val isListenerGranted = remember(context) {
        PermissionUtils.isNotificationListenerGranted(context)
    }

    LaunchedEffect(Unit) {
        viewModel.loadDashboard()
    }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = onNavigateToManualEntry,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Transaction")
            }
        }
    ) { padding ->
        if (state.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp),
                contentPadding = PaddingValues(vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 1. Month Header & Quick Navigation
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = DateTimeUtils.formatMonthYear(state.currentYear, state.currentMonth),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            state.netWorth?.let { nw ->
                                Text(
                                    text = "Net Worth: ${MoneyFormatter.formatRupees(nw.netWorthPaise)}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            IconButton(icon = Icons.Default.Category, onClick = onNavigateToCategories)
                            IconButton(icon = Icons.Default.ReceiptLong, onClick = onNavigateToStatements)
                            IconButton(icon = Icons.Default.History, onClick = onNavigateToTransactions)
                            IconButton(icon = Icons.Default.AccountBalance, onClick = onNavigateToAccounts)
                            IconButton(icon = Icons.Default.Backup, onClick = onNavigateToBackup)
                            IconButton(icon = Icons.Default.Settings, onClick = onNavigateToSetup)
                        }
                    }
                }

                // Permission Warning Banner if Notification Access is missing
                if (!isListenerGranted) {
                    item {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                                    context.startActivity(intent)
                                },
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .padding(16.dp)
                                    .fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.NotificationsOff,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onErrorContainer
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Notification Access Required",
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                    Text(
                                        text = "Tap here to enable Notification Listener permission in System Settings so tapLedger can capture bank notifications.",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                }
                            }
                        }
                    }
                }

                // 2. Pending Inbox Card (Section 44)
                if (state.pendingCount > 0) {
                    item {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onNavigateToPending() },
                            colors = CardDefaults.cardColors(
                                containerColor = AmberWarning.copy(alpha = 0.15f)
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .padding(16.dp)
                                    .fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.NotificationsActive,
                                    contentDescription = null,
                                    tint = AmberWarning
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "${state.pendingCount} Pending Transactions",
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "Affects real account balance; tap to classify",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }

                // 3. Monthly Budget Summary Card (Section 6, 71)
                state.budgetSummary?.let { bs ->
                    item {
                        BudgetOverviewCard(bs = bs)
                    }
                }

                // 4. Account Balances Card (Section 8, 9, 23)
                item {
                    Text(
                        text = "Accounts",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                items(state.accountSummaries) { accSummary ->
                    AccountBalanceRow(summary = accSummary)
                }

                // 5. Category Budgets (Section 24, 25)
                state.budgetSummary?.let { bs ->
                    item {
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Category Budgets",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            IconButton(icon = Icons.Default.Category, onClick = onNavigateToCategories)
                        }
                    }

                    items(bs.categorySummaries) { catSummary ->
                        CategoryBudgetRow(summary = catSummary)
                    }
                }
            }
        }
    }
}

@Composable
fun IconButton(icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun BudgetOverviewCard(bs: com.tapledger.domain.model.BudgetPeriodSummary) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("Income", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        MoneyFormatter.formatRupees(bs.totalIncomePaise),
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                }
                Column {
                    Text("Budgeted", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        MoneyFormatter.formatRupees(bs.totalBudgetedPaise),
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                }
                Column {
                    Text("Spent", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        MoneyFormatter.formatRupees(bs.totalSpentPaise),
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                }
                Column {
                    Text("Remaining", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        MoneyFormatter.formatRupees(bs.totalRemainingPaise),
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = if (bs.totalRemainingPaise >= 0) EmeraldPrimary else RoseError
                    )
                }
            }
            if (bs.unallocatedBudgetPaise > 0) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Unallocated: ${MoneyFormatter.formatRupees(bs.unallocatedBudgetPaise)}",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
        }
    }
}

@Composable
fun AccountBalanceRow(summary: AccountBalanceSummary) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .padding(14.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = summary.account.name,
                    fontWeight = FontWeight.Medium,
                    fontSize = 15.sp
                )
                summary.account.identifierLast4?.let {
                    Text(
                        text = "•••• $it",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Text(
                text = MoneyFormatter.formatRupees(summary.currentBalancePaise),
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp,
                color = when {
                    summary.currentBalancePaise < 0 -> RoseError
                    else -> MaterialTheme.colorScheme.onSurface
                }
            )
        }
    }
}

@Composable
fun CategoryBudgetRow(summary: BudgetCategorySummary) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = summary.allocation.categoryNameSnapshot,
                        fontWeight = FontWeight.Medium,
                        fontSize = 14.sp
                    )
                    if (summary.isExceeded) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = "Overspent",
                            modifier = Modifier.size(16.dp),
                            tint = RoseError
                        )
                    }
                }
                Text(
                    text = "${MoneyFormatter.formatRupees(summary.netSpentPaise)} / ${MoneyFormatter.formatRupees(summary.adjustedBudgetPaise)}",
                    fontSize = 13.sp,
                    color = if (summary.isExceeded) RoseError else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            val progress = (summary.percentageUsed.toFloat() / 100f).coerceIn(0f, 1f)
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = when {
                    summary.isExceeded -> RoseError
                    summary.isWarning -> AmberWarning
                    else -> EmeraldPrimary
                },
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
        }
    }
}
