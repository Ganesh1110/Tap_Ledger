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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
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
import com.tapledger.ui.components.DonutChart
import com.tapledger.ui.components.DonutSlice
import com.tapledger.ui.components.MoneyText
import com.tapledger.ui.theme.AmberWarning
import com.tapledger.ui.theme.EmeraldPrimary
import com.tapledger.ui.theme.ExpenseRed
import com.tapledger.ui.theme.IncomeGreen
import com.tapledger.ui.theme.RoseError
import com.tapledger.ui.theme.WarningAmber

@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel = hiltViewModel(),
    initialScrollIndex: Int = 0,
    onNavigateToManualEntry: () -> Unit,
    onNavigateToTransactions: () -> Unit,
    onNavigateToPending: () -> Unit,
    onNavigateToAccounts: () -> Unit,
    onNavigateToBackup: () -> Unit,
    onNavigateToSetup: () -> Unit,
    onNavigateToCategories: () -> Unit,
    onNavigateToStatements: () -> Unit,
    onNavigateToMonthClose: () -> Unit,
    onNavigateToDiagnostics: () -> Unit
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

    Scaffold { padding ->
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
            val listState = rememberLazyListState(initialFirstVisibleItemIndex = initialScrollIndex)
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp),
                contentPadding = PaddingValues(top = 16.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 1. Month Header & Quick Navigation
                item {
                    Column(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = DateTimeUtils.formatMonthYear(state.currentYear, state.currentMonth),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "● Secure (Offline)",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = IncomeGreen
                            )
                        }
                        state.netWorth?.let { nw ->
                            Text(
                                text = "Net Worth: ${MoneyFormatter.formatRupees(nw.netWorthPaise)}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                    }
                }

                // Quick Action Pill Bar
                item {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(horizontal = 2.dp)
                    ) {
                        item {
                            AssistChip(
                                onClick = onNavigateToPending,
                                label = { Text("📥 Pending (${state.pendingCount})") }
                            )
                        }
                        item {
                            AssistChip(
                                onClick = onNavigateToStatements,
                                label = { Text("📊 Reconcile") }
                            )
                        }
                        item {
                            AssistChip(
                                onClick = onNavigateToMonthClose,
                                label = { Text("🔒 Month Close") }
                            )
                        }
                        item {
                            AssistChip(
                                onClick = onNavigateToDiagnostics,
                                label = { Text("🛡️ Health") }
                            )
                        }
                        item {
                            AssistChip(
                                onClick = onNavigateToBackup,
                                label = { Text("💾 Backup") }
                            )
                        }
                    }
                }

                // Permission Warning Banner if Notification Access is missing
                if (!isListenerGranted) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer
                            )
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
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
                                            color = MaterialTheme.colorScheme.onErrorContainer,
                                            fontSize = 15.sp
                                        )
                                        Text(
                                            text = "TapLedger needs notification listener access to automatically capture bank SMS/UPI alerts.",
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.onErrorContainer
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(12.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Button(
                                        onClick = {
                                            val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                                            context.startActivity(intent)
                                        },
                                        modifier = Modifier.weight(1f),
                                        colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                                            containerColor = MaterialTheme.colorScheme.onErrorContainer,
                                            contentColor = MaterialTheme.colorScheme.errorContainer
                                        )
                                    ) {
                                        Text("Open Settings", fontSize = 11.sp)
                                    }
                                    OutlinedButton(
                                        onClick = onNavigateToSetup,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text("HyperOS Setup", fontSize = 11.sp)
                                    }
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
    val palette = listOf(
        MaterialTheme.colorScheme.primary,
        MaterialTheme.colorScheme.secondary,
        MaterialTheme.colorScheme.tertiary,
        IncomeGreen,
        WarningAmber,
        ExpenseRed
    )

    val slices = bs.categorySummaries
        .filter { it.netSpentPaise > 0L }
        .mapIndexed { idx, cat ->
            DonutSlice(
                label = cat.allocation.categoryNameSnapshot,
                amountPaise = cat.netSpentPaise,
                color = palette[idx % palette.size]
            )
        }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                DonutChart(
                    slices = slices,
                    totalAmountPaise = bs.totalSpentPaise,
                    chartSize = 130.dp,
                    strokeWidth = 18.dp
                )

                Spacer(modifier = Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Monthly Overview", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Income", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        MoneyText(amountPaise = bs.totalIncomePaise, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Budgeted", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        MoneyText(amountPaise = bs.totalBudgetedPaise, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Spent", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        MoneyText(amountPaise = bs.totalSpentPaise, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold), color = ExpenseRed)
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Remaining", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        MoneyText(amountPaise = bs.totalRemainingPaise, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold), color = if (bs.totalRemainingPaise >= 0) IncomeGreen else ExpenseRed)
                    }
                }
            }
        }
    }
}

@Composable
fun AccountBalanceRow(summary: AccountBalanceSummary) {
    val isCreditCard = summary.account.type == com.tapledger.core.model.AccountType.CREDIT_CARD
    val displayPaise = if (isCreditCard) kotlin.math.abs(summary.currentBalancePaise) else summary.currentBalancePaise

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
                Text(
                    text = if (isCreditCard) "Outstanding Liability ${summary.account.identifierLast4?.let { "•••• $it" } ?: ""}"
                           else "Available Balance ${summary.account.identifierLast4?.let { "•••• $it" } ?: ""}",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            MoneyText(
                amountPaise = displayPaise,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                color = if (isCreditCard && displayPaise > 0L) RoseError else MaterialTheme.colorScheme.onSurface
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
