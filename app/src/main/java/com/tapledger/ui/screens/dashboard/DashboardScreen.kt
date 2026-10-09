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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.NotificationsActive
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import com.tapledger.ui.theme.ExpenseRed
import com.tapledger.ui.theme.IncomeGreen
import com.tapledger.ui.theme.Spacing
import com.tapledger.ui.theme.WarningAmber
import com.tapledger.ui.theme.rememberAnimatedProgress

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
                                            text = { Text("Reliability Setup") },
                                            onClick = {
                                                showSettingsMenu = false
                                                onNavigateToSetup()
                                            },
                                            leadingIcon = { Icon(Icons.Default.Settings, contentDescription = null) }
                                        )
                                    }
                                }
                            }
                        }

                        // Notification Listener Warning (if not granted)
                        if (!isListenerGranted) {
                            Spacer(modifier = Modifier.height(Spacing.xs))
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.errorContainer
                                ),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Column(modifier = Modifier.padding(Spacing.sm)) {
                                    Text(
                                        text = "Notification Listener Inactive",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                    Text(
                                        text = "Allow SMS and UPI notifications for automatic expense tracking.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                    Spacer(modifier = Modifier.height(Spacing.xs))
                                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                                        Button(
                                            onClick = {
                                                val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                                                context.startActivity(intent)
                                            },
                                            colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                                                containerColor = MaterialTheme.colorScheme.onErrorContainer,
                                                contentColor = MaterialTheme.colorScheme.errorContainer
                                            )
                                        ) {
                                            Text("Enable", fontSize = 11.sp)
                                        }
                                        OutlinedButton(onClick = onNavigateToSetup) {
                                            Text("Setup Guide", fontSize = 11.sp)
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
                        }
                    }
                }

                // 2. Hero Card: Spent ÷ Budget Progress & Net Worth
                state.budgetSummary?.let { bs ->
                    item(key = "budget_hero") {
                        DashboardHeroCard(
                            budgetSummary = bs,
                            netWorthPaise = state.netWorth?.netWorthPaise
                        )
                    }
                }

                // 3. Needs / Wants / Savings / Unallocated Bucket Progress
                state.budgetSummary?.let { bs ->
                    item(key = "buckets_header") {
                        SectionHeader(
                            title = "Budget Buckets",
                            subtitle = "50/30/20 Allocation Rule",
                            actionText = "Manage",
                            onActionClick = onNavigateToCategories
                        )
                    }

                    item(key = "bucket_needs") {
                        BucketProgressCard(
                            bucket = BudgetBucket.NEEDS,
                            allocatedPaise = bs.bucketTotals[BudgetBucket.NEEDS] ?: 0L,
                            spentPaise = bs.bucketSpent[BudgetBucket.NEEDS] ?: 0L
                        )
                    }

                    item(key = "bucket_wants") {
                        BucketProgressCard(
                            bucket = BudgetBucket.WANTS,
                            allocatedPaise = bs.bucketTotals[BudgetBucket.WANTS] ?: 0L,
                            spentPaise = bs.bucketSpent[BudgetBucket.WANTS] ?: 0L
                        )
                    }

                    item(key = "bucket_savings") {
                        BucketProgressCard(
                            bucket = BudgetBucket.SAVINGS,
                            allocatedPaise = bs.bucketTotals[BudgetBucket.SAVINGS] ?: 0L,
                            spentPaise = bs.bucketSpent[BudgetBucket.SAVINGS] ?: 0L
                        )
                    }

                    if (bs.unallocatedBudgetPaise > 0L) {
                        item(key = "bucket_unallocated") {
                            BucketProgressCard(
                                bucket = BudgetBucket.UNALLOCATED,
                                allocatedPaise = bs.unallocatedBudgetPaise,
                                spentPaise = 0L
                            )
                        }
                    }
                }

                // 4. Category Budgets
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

                // 5. Accounts (Compact cards)
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

                // 6. Recent Activity
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
            }
        }
    }
}

/**
 * Modern Hero Card replacing the old donut chart with a high-readability progress bar and Net Worth stat.
 */
@Composable
private fun DashboardHeroCard(
    budgetSummary: BudgetPeriodSummary,
    netWorthPaise: Long?,
    modifier: Modifier = Modifier
) {
    val totalBudget = budgetSummary.totalBudgetedPaise
    val totalSpent = budgetSummary.totalSpentPaise
    val remainingPaise = totalBudget - totalSpent

    val rawProgress = if (totalBudget > 0L) totalSpent.toFloat() / totalBudget.toFloat() else 0f
    val animatedProgress by rememberAnimatedProgress(targetFraction = rawProgress.coerceIn(0f, 1f))
    val percentUsed = (rawProgress * 100).toInt()

    val isOverBudget = totalSpent > totalBudget && totalBudget > 0L
    val isWarning = rawProgress >= 0.8f && !isOverBudget

    val progressColor = when {
        isOverBudget -> ExpenseRed
        isWarning -> WarningAmber
        else -> MaterialTheme.colorScheme.primary
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {
                contentDescription = "Monthly budget: spent ${totalSpent / 100} of ${totalBudget / 100} rupees, remaining ${remainingPaise / 100} rupees"
            },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.lg)
        ) {
            // Net Worth Stat
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Net Worth",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (netWorthPaise != null) {
                        MoneyText(
                            amountPaise = netWorthPaise,
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            semantic = if (netWorthPaise >= 0) MoneySemantic.Income else MoneySemantic.Expense
                        )
                    }
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "Budget Remaining",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    MoneyText(
                        amountPaise = kotlin.math.abs(remainingPaise),
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        semantic = if (remainingPaise >= 0) MoneySemantic.Income else MoneySemantic.Expense
                    )
                }
            }

            Spacer(modifier = Modifier.height(Spacing.md))

            // Progress bar
            LinearProgressIndicator(
                progress = { animatedProgress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp)
                    .clip(RoundedCornerShape(5.dp)),
                color = progressColor,
                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                strokeCap = StrokeCap.Round
            )

            Spacer(modifier = Modifier.height(Spacing.xs))

            // Spend detail text: "₹850 of ₹50,000 · 1.7%"
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    MoneyText(
                        amountPaise = totalSpent,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                        semantic = MoneySemantic.Expense
                    )
                    Text(
                        text = " of ",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    MoneyText(
                        amountPaise = totalBudget,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                        semantic = MoneySemantic.Neutral
                    )
                    Text(
                        text = " · $percentUsed%",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = progressColor
                    )
                }

                Text(
                    text = if (remainingPaise >= 0) "${MoneyFormatter.formatRupees(remainingPaise)} left" else "${MoneyFormatter.formatRupees(kotlin.math.abs(remainingPaise))} over",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = if (remainingPaise >= 0) IncomeGreen else ExpenseRed
                )
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

    val rawProgress = if (totalBudget > 0L) netSpent.toFloat() / totalBudget.toFloat() else 0f
    val animatedProgress by rememberAnimatedProgress(targetFraction = rawProgress.coerceIn(0f, 1f))

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
                contentDescription = "${summary.allocation.categoryNameSnapshot}: spent ${netSpent / 100} of ${totalBudget / 100} rupees, remaining ${remaining / 100} rupees"
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
            LinearProgressIndicator(
                progress = { animatedProgress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = progressColor,
                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                strokeCap = StrokeCap.Round
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
