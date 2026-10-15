package com.tapledger.ui.screens.dashboard

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.PointOfSale
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import com.tapledger.BuildConfig
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tapledger.core.model.BudgetBucket
import com.tapledger.core.util.DateTimeUtils
import com.tapledger.core.model.MoneyFormatter
import com.tapledger.core.util.PermissionUtils
import com.tapledger.domain.model.BudgetCategorySummary
import com.tapledger.domain.model.BudgetPeriodSummary
import com.tapledger.ui.components.AccountCard
import com.tapledger.ui.components.BucketProgressCard
import com.tapledger.ui.components.MoneyIndicator
import com.tapledger.ui.components.MoneySemantic
import com.tapledger.ui.components.MoneyText
import com.tapledger.ui.components.SectionHeader
import com.tapledger.ui.components.SkeletonCard
import com.tapledger.ui.components.SkeletonList
import com.tapledger.ui.components.StatBlock
import com.tapledger.ui.components.TransactionRow
import com.tapledger.ui.screens.dashboard.components.DashboardBucketRingsCard
import com.tapledger.ui.screens.dashboard.components.DashboardBurnUpCard
import com.tapledger.ui.screens.dashboard.components.DashboardHeroRingCard
import com.tapledger.ui.screens.dashboard.components.DashboardSixPeriodTrendCard
import com.tapledger.ui.theme.ExpenseRed
import com.tapledger.ui.theme.IncomeGreen
import com.tapledger.ui.theme.Spacing
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
    onNavigateToDiagnostics: () -> Unit,
    onNavigateToInsights: () -> Unit = {},
    onNavigateToAppLockSettings: () -> Unit = {},
    onShowWhatsNew: () -> Unit = {}
) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()

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

    val listState = rememberLazyListState(initialFirstVisibleItemIndex = initialScrollIndex)
    val isExpandedFab by remember {
        derivedStateOf { listState.firstVisibleItemIndex == 0 }
    }

    Scaffold(
        floatingActionButton = {
            ExtendedFloatingActionButton(
                text = { Text("Add Transaction") },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                expanded = isExpandedFab,
                onClick = onNavigateToManualEntry,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            )
        }
    ) { padding ->
        if (state.isLoading) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = Spacing.md, vertical = Spacing.md),
                verticalArrangement = Arrangement.spacedBy(Spacing.md)
            ) {
                SkeletonCard(height = 64.dp)
                SkeletonCard(height = 140.dp)
                SkeletonList(itemCount = 3)
            }
        } else {
            var showSettingsMenu by remember { mutableStateOf(false) }

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = Spacing.md),
                contentPadding = PaddingValues(top = Spacing.md, bottom = Spacing.listBottomPadding),
                verticalArrangement = Arrangement.spacedBy(Spacing.md)
            ) {
                // 1. Month Switcher Header & Quick Navigation
                item(key = "month_switcher") {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(
                                    onClick = { viewModel.prevMonth() },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = "Previous Month"
                                    )
                                }

                                AnimatedContent(
                                    targetState = state.currentYear to state.currentMonth,
                                    transitionSpec = {
                                        val isForward = (targetState.first * 12 + targetState.second) >
                                                (initialState.first * 12 + initialState.second)
                                        slideInHorizontally { width -> if (isForward) width else -width } togetherWith
                                                slideOutHorizontally { width -> if (isForward) -width else width }
                                    },
                                    label = "monthTransition"
                                ) { (year, month) ->
                                    Text(
                                        text = DateTimeUtils.formatMonthYear(year, month),
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = Spacing.xs)
                                    )
                                }

                                IconButton(
                                    onClick = { viewModel.nextMonth() },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                        contentDescription = "Next Month"
                                    )
                                }
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                                        .clickable { onNavigateToSetup() },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Lock,
                                        contentDescription = "Offline Vault",
                                        modifier = Modifier.size(18.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Spacer(modifier = Modifier.width(Spacing.xs))
                                Box {
                                    IconButton(
                                        onClick = { showSettingsMenu = true },
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Settings,
                                            contentDescription = "Settings",
                                            modifier = Modifier.size(20.dp),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    DropdownMenu(
                                        expanded = showSettingsMenu,
                                        onDismissRequest = { showSettingsMenu = false }
                                    ) {
                                        DropdownMenuItem(
                                            text = { Text("What's New & Updates") },
                                            onClick = {
                                                showSettingsMenu = false
                                                onShowWhatsNew()
                                            },
                                            leadingIcon = { Icon(Icons.Default.AutoAwesome, contentDescription = null) }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Backup & Restore") },
                                            onClick = {
                                                showSettingsMenu = false
                                                onNavigateToBackup()
                                            },
                                            leadingIcon = { Icon(Icons.Default.Save, contentDescription = null) }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Data Diagnostics") },
                                            onClick = {
                                                showSettingsMenu = false
                                                onNavigateToDiagnostics()
                                            },
                                            leadingIcon = { Icon(Icons.Default.Security, contentDescription = null) }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("App Lock & Security") },
                                            onClick = {
                                                showSettingsMenu = false
                                                onNavigateToAppLockSettings()
                                            },
                                            leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Reliability Setup") },
                                            onClick = {
                                                showSettingsMenu = false
                                                onNavigateToSetup()
                                            },
                                            leadingIcon = { Icon(Icons.Default.Settings, contentDescription = null) }
                                        )
                                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = Spacing.md, vertical = Spacing.xs)
                                        ) {
                                            Text(
                                                text = "TapLedger v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Notification Listener Warning (if not granted) - Compact for 5.5" displays
                        if (!isListenerGranted) {
                            Spacer(modifier = Modifier.height(Spacing.xs))
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.errorContainer
                                ),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = Spacing.sm, vertical = Spacing.xs),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        modifier = Modifier.weight(1f),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Warning,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onErrorContainer,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(Spacing.xs))
                                        Column {
                                            Text(
                                                text = "Listener Inactive",
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onErrorContainer
                                            )
                                            Text(
                                                text = "Allow alerts for auto-tracking",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.85f),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(Spacing.xs))
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(Spacing.xxs),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        TextButton(
                                            onClick = onNavigateToSetup,
                                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Text("Guide", fontSize = 11.sp, color = MaterialTheme.colorScheme.onErrorContainer)
                                        }
                                        Button(
                                            onClick = {
                                                val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                                                context.startActivity(intent)
                                            },
                                            colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                                                containerColor = MaterialTheme.colorScheme.onErrorContainer,
                                                contentColor = MaterialTheme.colorScheme.errorContainer
                                            ),
                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp)
                                        ) {
                                            Text("Enable", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(Spacing.xs))

                        // Quick Actions (Fixed row that doesn't clip)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
                        ) {
                            // 1. Pending Button
                            Surface(
                                onClick = onNavigateToPending,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp),
                                color = if (state.pendingCount > 0) WarningAmber.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceContainerLow,
                                border = BorderStroke(
                                    1.dp,
                                    if (state.pendingCount > 0) WarningAmber.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                                )
                            ) {
                                Row(
                                    modifier = Modifier.padding(vertical = 10.dp, horizontal = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    if (state.pendingCount > 0) {
                                        BadgedBox(
                                            badge = {
                                                Badge { Text("${state.pendingCount}") }
                                            }
                                        ) {
                                            Icon(
                                                Icons.Default.NotificationsActive,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp),
                                                tint = WarningAmber
                                            )
                                        }
                                    } else {
                                        Icon(
                                            Icons.Default.NotificationsActive,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Pending",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = if (state.pendingCount > 0) FontWeight.Bold else FontWeight.Medium,
                                        color = if (state.pendingCount > 0) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1
                                    )
                                }
                            }

                            // 2. Reconcile Button
                            Surface(
                                onClick = onNavigateToStatements,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerLow,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                            ) {
                                Row(
                                    modifier = Modifier.padding(vertical = 10.dp, horizontal = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Sync,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Reconcile",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1
                                    )
                                }
                            }

                            // 3. Close Month Button
                            Surface(
                                onClick = onNavigateToMonthClose,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerLow,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                            ) {
                                Row(
                                    modifier = Modifier.padding(vertical = 10.dp, horizontal = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.PointOfSale,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Close Month",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1
                                    )
                                }
                            }

                            // 4. Insights Button
                            Surface(
                                onClick = onNavigateToInsights,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerLow,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                            ) {
                                Row(
                                    modifier = Modifier.padding(vertical = 10.dp, horizontal = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.PieChart,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Insights",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }
                }

                // 2. V1: Hero Ring Card (Total budget, spent, remaining, net worth)
                item(key = "budget_hero_ring") {
                    DashboardHeroRingCard(
                        heroRingState = state.heroRingState,
                        netWorthPaise = state.netWorth?.netWorthPaise
                    )
                }

                // 3. V2: Burn-Up Line Card (Pace vs cap, scrubbable)
                item(key = "budget_burn_up") {
                    DashboardBurnUpCard(
                        burnUpState = state.burnUpChartState
                    )
                }

                // 4. Category Envelopes (Core daily operational view)
                state.budgetSummary?.let { bs ->
                    item(key = "category_budgets_header") {
                        SectionHeader(
                            title = "Category Budgets",
                            subtitle = "${bs.categorySummaries.size} active categories",
                            actionText = "Manage",
                            onActionClick = onNavigateToCategories
                        )
                    }

                    items(
                        items = bs.categorySummaries,
                        key = { "cat_${it.allocation.categoryId}" }
                    ) { catSummary ->
                        DashboardCategoryRow(summary = catSummary)
                    }
                }

                // 5. Recent Activity (Quick ledger verification)
                if (state.recentTransactions.isNotEmpty()) {
                    item(key = "recent_activity_header") {
                        SectionHeader(
                            title = "Recent Activity",
                            subtitle = "Latest ledger entries",
                            actionText = "View All",
                            onActionClick = onNavigateToTransactions
                        )
                    }

                    items(
                        items = state.recentTransactions,
                        key = { "tx_${it.id}" }
                    ) { tx ->
                        TransactionRow(
                            tx = tx,
                            onClick = onNavigateToTransactions
                        )
                    }
                }

                // 6. Accounts (Available balances & credit owed)
                if (state.accountSummaries.isNotEmpty()) {
                    item(key = "accounts_header") {
                        SectionHeader(
                            title = "Accounts",
                            subtitle = "Available balances & credit owed",
                            actionText = "View All",
                            onActionClick = onNavigateToAccounts
                        )
                    }

                    items(
                        items = state.accountSummaries,
                        key = { "acc_${it.account.id}" }
                    ) { accSummary ->
                        AccountCard(
                            summary = accSummary,
                            onClick = onNavigateToAccounts
                        )
                    }
                }

                // 7. V3: 50/30/20 Budget Buckets
                item(key = "budget_bucket_rings") {
                    DashboardBucketRingsCard(
                        bucketRingsState = state.bucketRingsState,
                        onManageCategories = onNavigateToCategories
                    )
                }

                // 8. V4: Multi-Period Trend (Collapsed by default on home screen, full deep-dive in Insights)
                item(key = "six_period_trend") {
                    DashboardSixPeriodTrendCard(
                        trendState = state.sixPeriodTrendState,
                        initiallyExpanded = false
                    )
                }
            }
        }
    }
}

/**
 * Category Row on Dashboard: ₹spent / ₹budget, % used, single continuous bar, no stray end dot.
 */
@Composable
private fun DashboardCategoryRow(
    summary: BudgetCategorySummary,
    modifier: Modifier = Modifier
) {
    val totalBudget = summary.adjustedBudgetPaise
    val netSpent = summary.netSpentPaise
    val remaining = summary.remainingPaise

    val percentageBp = com.tapledger.ui.charts.util.ChartMath.computeBasisPoints(netSpent, totalBudget)

    val progressColor = when {
        summary.isExceeded -> ExpenseRed
        summary.isWarning -> WarningAmber
        else -> IncomeGreen
    }

    val statusIcon = when {
        summary.isExceeded -> Icons.Default.Error
        summary.isWarning -> Icons.Default.Warning
        else -> Icons.Default.CheckCircle
    }

    val statusText = when {
        summary.isExceeded -> "Over"
        summary.isWarning -> "≥80%"
        else -> "OK"
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {
                contentDescription = "${summary.allocation.categoryNameSnapshot}: spent ${MoneyFormatter.formatPaiseToRupees(netSpent)} of ${MoneyFormatter.formatPaiseToRupees(totalBudget)}, remaining ${MoneyFormatter.formatPaiseToRupees(remaining)}"
            },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        border = BorderStroke(
            1.dp,
            if (summary.isExceeded) ExpenseRed.copy(alpha = 0.35f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
        ),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.md, vertical = Spacing.sm)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = summary.allocation.categoryNameSnapshot,
                    style = MaterialTheme.typography.titleMedium.copy(fontSize = 15.sp),
                    fontWeight = FontWeight.SemiBold
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = statusIcon,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = progressColor
                    )
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = progressColor
                    )
                }
            }

            Spacer(modifier = Modifier.height(Spacing.xs))

            // Single continuous progress track
            com.tapledger.ui.charts.render.ProgressBarRender(
                percentageBp = percentageBp,
                color = progressColor,
                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
            )

            Spacer(modifier = Modifier.height(Spacing.xs))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    MoneyText(
                        amountPaise = netSpent,
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                        semantic = MoneySemantic.Expense
                    )
                    Text(
                        text = " / ",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    MoneyText(
                        amountPaise = totalBudget,
                        style = MaterialTheme.typography.bodySmall,
                        semantic = MoneySemantic.Neutral
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = if (remaining >= 0) "Left: " else "Exceeded: ",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    MoneyText(
                        amountPaise = kotlin.math.abs(remaining),
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                        semantic = if (remaining >= 0) MoneySemantic.Income else MoneySemantic.Expense
                    )
                }
            }
        }
    }
}
