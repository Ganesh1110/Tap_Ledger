package com.tapledger.ui.screens.saving

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import com.tapledger.core.database.entity.FixedDepositEntity
import com.tapledger.core.database.entity.RecurringCommitmentEntity
import com.tapledger.core.database.entity.TransactionEntity
import com.tapledger.domain.calculator.FdCalculator
import com.tapledger.ui.charts.render.ProgressBarRender
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tapledger.core.model.GoalTrackStatus
import com.tapledger.core.model.MoneyFormatter
import com.tapledger.core.model.SavingsGoalStatus
import com.tapledger.domain.model.SavingsGoalSummary
import com.tapledger.ui.charts.render.RingChartRender
import com.tapledger.ui.components.EmptyState
import com.tapledger.ui.components.SectionHeader
import com.tapledger.ui.theme.ExpenseRed
import com.tapledger.ui.theme.IncomeGreen
import com.tapledger.ui.theme.IncomeGreenContainer
import com.tapledger.ui.theme.Spacing
import com.tapledger.ui.theme.WarningAmber
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SavingHomeScreen(
    viewModel: SavingViewModel = hiltViewModel(),
    canNavigateBack: Boolean = true,
    onNavigateBack: () -> Unit = {},
    onSwitchToManager: (() -> Unit)? = null,
    onNavigateToSettings: () -> Unit = {},
    onNavigateToGoalDetail: (goalId: Long) -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var showCreateSheet by remember { mutableStateOf(false) }
    var selectedDipDebit by remember { mutableStateOf<TransactionEntity?>(null) }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Savings & Goals",
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    if (canNavigateBack) {
                        IconButton(onClick = onNavigateBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back"
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Savings Guide Settings"
                        )
                    }
                    if (onSwitchToManager != null) {
                        AssistChip(
                            onClick = onSwitchToManager,
                            label = { Text("Manager") },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.AccountBalanceWallet,
                                    contentDescription = "Switch to Manager",
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            modifier = Modifier.padding(end = Spacing.sm)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showCreateSheet = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Icon(imageVector = Icons.Default.Add, contentDescription = "Add Goal")
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
                    .padding(horizontal = Spacing.md),
                contentPadding = PaddingValues(top = Spacing.sm, bottom = Spacing.listBottomPadding),
                verticalArrangement = Arrangement.spacedBy(Spacing.md)
            ) {
                // 0. Monthly Savings Transfer Status (Milestone M22, §7, D015)
                if (state.transferSummary != null) {
                    item(key = "monthly_transfer_status") {
                        val summary = state.transferSummary!!
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = if (summary.isVerified) IncomeGreenContainer.copy(alpha = 0.5f) else MaterialTheme.colorScheme.surfaceContainerHigh
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(Spacing.md),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                                    modifier = Modifier.weight(1f, fill = false)
                                ) {
                                    Icon(
                                        imageVector = if (summary.isVerified) Icons.Default.CheckCircle else Icons.Default.Sync,
                                        contentDescription = null,
                                        tint = if (summary.isVerified) IncomeGreen else WarningAmber,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Column {
                                        Text(
                                            text = "This month moved ${MoneyFormatter.formatPaiseToRupees(summary.movedPaise)}",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = if (summary.isVerified) "Verified by bank alert" else "Self-reported (Unverified by bank)",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = if (summary.isVerified) IncomeGreen.copy(alpha = 0.15f) else WarningAmber.copy(alpha = 0.15f)
                                ) {
                                    Text(
                                        text = if (summary.isVerified) "✓ Verified" else "Pending Alert",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (summary.isVerified) IncomeGreen else WarningAmber,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // 0b. Dip Guard: Pending Savings Debits (Milestone M22, §7 Dip Guard)
                if (state.pendingSavingsDebits.isNotEmpty()) {
                    item(key = "dip_guard_alert") {
                        val debit = state.pendingSavingsDebits.first()
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.25f)
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(Spacing.md),
                                verticalArrangement = Arrangement.spacedBy(Spacing.xs)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Warning,
                                        contentDescription = null,
                                        tint = WarningAmber,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(Spacing.xs))
                                    Text(
                                        text = "Spending from Savings Detected",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Text(
                                    text = "A debit of ${MoneyFormatter.formatPaiseToRupees(debit.amountPaise)} occurred on savings. Which goal was this for?",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(Spacing.xs))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End
                                ) {
                                    androidx.compose.material3.TextButton(
                                        onClick = { selectedDipDebit = debit }
                                    ) {
                                        Text("Attribute to Goal")
                                    }
                                }
                            }
                        }
                    }
                }

                // 1. Total Saved Hero Card
                item(key = "total_saved_hero") {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                        ),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(Spacing.lg)
                        ) {
                            Text(
                                text = "Total Earmarked Savings",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(Spacing.xs))
                            Text(
                                text = MoneyFormatter.formatPaiseToRupees(state.totalSavedPaise),
                                style = MaterialTheme.typography.headlineLarge,
                                fontWeight = FontWeight.Bold,
                                color = IncomeGreen
                            )
                            Spacer(modifier = Modifier.height(Spacing.sm))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "${state.activeGoalsCount} Active Goals",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "${state.completedGoalsCount} Completed",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                // 1b. Emergency Fund Coverage Card (Phase 2)
                item(key = "emergency_fund_card") {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                        ),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(Spacing.lg)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Emergency Fund Coverage",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                val whole = state.emergencyFundTenths / 10L
                                val frac = state.emergencyFundTenths % 10L
                                Text(
                                    text = "$whole.$frac months",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = if (state.emergencyFundTenths >= 30L) IncomeGreen else WarningAmber
                                )
                            }
                            Spacer(modifier = Modifier.height(Spacing.xs))
                            val progressBp = ((state.emergencyFundTenths * 10_000L) / 60L).coerceIn(0L, 10_000L).toInt()
                            ProgressBarRender(
                                percentageBp = progressBp,
                                color = if (state.emergencyFundTenths >= 30L) IncomeGreen else WarningAmber,
                                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                                height = 8.dp
                            )
                            Spacer(modifier = Modifier.height(Spacing.xs))
                            Text(
                                text = "Target: 6.0 months of average expenses",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // 2. Earmark Warnings (D011: soft warning if earmarked > account balance)
                if (state.earmarkWarnings.isNotEmpty()) {
                    item(key = "earmark_warnings") {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(Spacing.md),
                                verticalArrangement = Arrangement.spacedBy(Spacing.xs)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Warning,
                                        contentDescription = null,
                                        tint = WarningAmber,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(Spacing.xs))
                                    Text(
                                        text = "Account Balance Earmark Warning (D011)",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                state.earmarkWarnings.forEach { warning ->
                                    Text(
                                        text = "• ${warning.accountName}: Earmarked ${MoneyFormatter.formatPaiseToRupees(warning.totalEarmarkedPaise)} exceeds account balance of ${MoneyFormatter.formatPaiseToRupees(warning.accountBalancePaise)} by ${MoneyFormatter.formatPaiseToRupees(warning.deficitPaise)}.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }

                // 3. Section Header
                item(key = "goals_header") {
                    SectionHeader(title = "Your Goals (${state.goals.size})")
                }

                // 4. Goals List
                if (state.goals.isEmpty()) {
                    item(key = "goals_empty") {
                        EmptyState(
                            title = "No savings goals yet",
                            subtitle = "Tap '+' below to create your first savings goal and start earmarking your money."
                        )
                    }
                } else {
                    items(state.goals, key = { it.goal.id }) { goalSummary ->
                        GoalCard(
                            summary = goalSummary,
                            onClick = { onNavigateToGoalDetail(goalSummary.goal.id) }
                        )
                    }
                }

                // 5. Recurring Commitments (Phase 3: SIP, RD, Chit, Gold)
                if (state.commitments.isNotEmpty()) {
                    item(key = "commitments_header") {
                        SectionHeader(title = "Recurring Commitments (${state.commitments.size})")
                    }
                    items(state.commitments, key = { "commitment_${it.id}" }) { commitment ->
                        CommitmentCard(commitment = commitment)
                    }
                }

                // 6. Fixed Deposits (Phase 3)
                if (state.fixedDeposits.isNotEmpty()) {
                    item(key = "fds_header") {
                        SectionHeader(title = "Fixed Deposits (${state.fixedDeposits.size})")
                    }
                    items(state.fixedDeposits, key = { "fd_${it.id}" }) { fd ->
                        FixedDepositCard(fd = fd)
                    }
                }

                // 7. Savings Milestones & Badges (Phase 4)
                if (state.milestones.isNotEmpty()) {
                    val unlockedCount = state.milestones.count { it.isUnlocked }
                    item(key = "milestones_header") {
                        SectionHeader(title = "Milestones & Badges ($unlockedCount/${state.milestones.size} Unlocked)")
                    }
                    items(state.milestones, key = { "milestone_${it.type.name}" }) { milestone ->
                        MilestoneBadgeCard(milestone = milestone)
                    }
                }
            }
        }
    }

    if (showCreateSheet) {
        GoalEditSheet(
            availableAccounts = state.accounts,
            onDismiss = { showCreateSheet = false },
            onSave = { name, targetPaise, deadlineMs, priority, linkedAccountId ->
                viewModel.createGoal(
                    name = name,
                    targetPaise = targetPaise,
                    deadlineEpochMs = deadlineMs,
                    priority = priority,
                    linkedAccountId = linkedAccountId,
                    onSuccess = { newId ->
                        showCreateSheet = false
                        scope.launch { snackbarHostState.showSnackbar("Goal created!") }
                        onNavigateToGoalDetail(newId)
                    },
                    onError = { err ->
                        scope.launch { snackbarHostState.showSnackbar(err) }
                    }
                )
            }
        )
    }

    selectedDipDebit?.let { debit ->
        DipGuardAttributionSheet(
            transaction = debit,
            goals = state.goals.filter { it.goal.status == SavingsGoalStatus.ACTIVE },
            onDismiss = { selectedDipDebit = null },
            onConfirmAttribution = { goalId, note ->
                viewModel.attributeDebitToGoal(
                    transactionId = debit.id,
                    goalId = goalId,
                    amountPaise = debit.amountPaise,
                    note = note,
                    onSuccess = {
                        selectedDipDebit = null
                        scope.launch { snackbarHostState.showSnackbar("Attributed to goal!") }
                    },
                    onError = { err ->
                        scope.launch { snackbarHostState.showSnackbar(err) }
                    }
                )
            }
        )
    }
}

@Composable
private fun GoalCard(
    summary: SavingsGoalSummary,
    onClick: () -> Unit
) {
    val goal = summary.goal
    val percentInt = summary.progressBp / 100

    val ringColor = when (summary.trackStatus) {
        GoalTrackStatus.DONE -> IncomeGreen
        GoalTrackStatus.ON_TRACK -> IncomeGreen
        GoalTrackStatus.BEHIND -> WarningAmber
        GoalTrackStatus.OVERDUE -> ExpenseRed
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Ring Progress
            RingChartRender(
                percentageUsedBp = summary.progressBp,
                ringColor = ringColor,
                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                size = 60.dp,
                strokeWidth = 6.dp,
                centerContent = {
                    Text(
                        text = "$percentInt%",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            fontFeatureSettings = "tnum"
                        )
                    )
                }
            )

            Spacer(modifier = Modifier.width(Spacing.md))

            // Goal Info
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
                ) {
                    Text(
                        text = goal.name,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Serif,
                            fontWeight = FontWeight.Bold
                        ),
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh
                    ) {
                        Text(
                            text = "P${goal.priority}",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 9.sp
                            ),
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = "${MoneyFormatter.formatPaiseToRupees(summary.balancePaise)} of ${MoneyFormatter.formatPaiseToRupees(goal.targetPaise)}",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        fontFeatureSettings = "tnum"
                    )
                )

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = summary.paceSummary,
                    style = MaterialTheme.typography.bodySmall,
                    color = when (summary.trackStatus) {
                        GoalTrackStatus.DONE -> IncomeGreen
                        GoalTrackStatus.OVERDUE -> ExpenseRed
                        GoalTrackStatus.BEHIND -> WarningAmber
                        GoalTrackStatus.ON_TRACK -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )

                if (summary.isOverEarmarked) {
                    Text(
                        text = "⚠ Account over-earmarked",
                        style = MaterialTheme.typography.labelSmall,
                        color = WarningAmber
                    )
                }

                if (summary.balancePaise > 0L) {
                    Spacer(modifier = Modifier.height(Spacing.xs))
                    val accountName = summary.backingAccountName ?: "SBI"
                    val isFullBacked = summary.backedBp >= 10000
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (isFullBacked) "Backed by $accountName ✓" else "Backed by $accountName: ${summary.backedBp / 100}%",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Medium,
                            color = if (isFullBacked) IncomeGreen else WarningAmber
                        )
                        Text(
                            text = "${MoneyFormatter.formatPaiseToRupees(summary.backedPaise)} / ${MoneyFormatter.formatPaiseToRupees(summary.balancePaise)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    ProgressBarRender(
                        percentageBp = summary.backedBp,
                        color = if (isFullBacked) IncomeGreen else WarningAmber,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                        height = 4.dp
                    )
                }
            }
        }
    }
}

@Composable
private fun CommitmentCard(commitment: RecurringCommitmentEntity) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
                ) {
                    Text(
                        text = commitment.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh
                    ) {
                        Text(
                            text = commitment.type.name,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Due on day ${commitment.dueDay} of each month",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = MoneyFormatter.formatPaiseToRupees(commitment.amountPaise),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = IncomeGreen
            )
        }
    }
}

@Composable
private fun FixedDepositCard(fd: FixedDepositEntity) {
    val calculator = remember { FdCalculator() }
    val now = remember { System.currentTimeMillis() }
    val daysLeft = calculator.calculateDaysToMaturity(fd.maturityMs, now)
    val maturityValue = calculator.calculateMaturityValue(fd)

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.md)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = fd.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                val rateWhole = fd.rateBp / 100
                val rateDec = (fd.rateBp % 100).toString().padStart(2, '0')
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh
                ) {
                    Text(
                        text = "$rateWhole.$rateDec% p.a.",
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(Spacing.xs))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Principal: ${MoneyFormatter.formatPaiseToRupees(fd.principalPaise)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Maturity: ${MoneyFormatter.formatPaiseToRupees(maturityValue)}",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    color = IncomeGreen
                )
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = if (daysLeft > 0) "$daysLeft days to maturity" else "Matured",
                style = MaterialTheme.typography.labelSmall,
                color = if (daysLeft > 0) MaterialTheme.colorScheme.onSurfaceVariant else IncomeGreen
            )
        }
    }
}

@Composable
private fun MilestoneBadgeCard(milestone: com.tapledger.domain.model.SavingsMilestoneBadge) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (milestone.isUnlocked)
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
            else
                MaterialTheme.colorScheme.surfaceContainerLow
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.md)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
                ) {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = null,
                        tint = if (milestone.isUnlocked) IncomeGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = milestone.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (milestone.isUnlocked) IncomeGreen else MaterialTheme.colorScheme.onSurface
                    )
                }
                if (milestone.isUnlocked) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = IncomeGreenContainer
                    ) {
                        Text(
                            text = "UNLOCKED",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = IncomeGreen,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                } else {
                    Text(
                        text = "${milestone.progressBp / 100}%",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(modifier = Modifier.height(Spacing.xs))
            Text(
                text = milestone.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (!milestone.isUnlocked) {
                Spacer(modifier = Modifier.height(Spacing.xs))
                ProgressBarRender(
                    percentageBp = milestone.progressBp,
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    height = 6.dp
                )
            }
        }
    }
}

