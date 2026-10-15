package com.tapledger.ui.screens.saving

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tapledger.core.database.entity.SavingsContributionEntity
import com.tapledger.core.model.GoalTrackStatus
import com.tapledger.core.model.MoneyFormatter
import com.tapledger.core.model.SavingsGoalStatus
import com.tapledger.core.util.DateTimeUtils
import com.tapledger.ui.charts.render.RingChartRender
import com.tapledger.ui.components.EmptyState
import com.tapledger.ui.components.SectionHeader
import com.tapledger.ui.components.StatusBadge
import com.tapledger.ui.theme.ChartPalette
import com.tapledger.ui.theme.ExpenseRed
import com.tapledger.ui.theme.IncomeGreen
import com.tapledger.ui.theme.Spacing
import com.tapledger.ui.theme.WarningAmber
import kotlinx.coroutines.launch


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GoalDetailScreen(
    goalId: Long,
    viewModel: SavingViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit
) {
    val stateFlow = remember(goalId) { viewModel.getGoalDetailFlow(goalId) }
    val state by stateFlow.collectAsStateWithLifecycle()
    val homeState by viewModel.uiState.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var showEditSheet by remember { mutableStateOf(false) }
    var showDepositSheet by remember { mutableStateOf(false) }
    var showWithdrawSheet by remember { mutableStateOf(false) }

    val goalSummary = state.goalSummary

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = goalSummary?.goal?.name ?: "Goal Detail",
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
                actions = {
                    if (goalSummary != null && goalSummary.goal.status != SavingsGoalStatus.ARCHIVED) {
                        IconButton(onClick = { showEditSheet = true }) {
                            Icon(imageVector = Icons.Default.Edit, contentDescription = "Edit Goal")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        if (state.isLoading || goalSummary == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            val goal = goalSummary.goal

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = Spacing.md),
                contentPadding = PaddingValues(top = Spacing.sm, bottom = Spacing.xl),
                verticalArrangement = Arrangement.spacedBy(Spacing.md)
            ) {
                // 1. Progress Ring & Central Stat
                item(key = "ring_hero") {
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
                                .padding(vertical = Spacing.lg, horizontal = Spacing.md),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            val percentInt = goalSummary.progressBp / 100
                            val ringColor = when (goalSummary.trackStatus) {
                                GoalTrackStatus.DONE -> IncomeGreen
                                GoalTrackStatus.ON_TRACK -> IncomeGreen
                                GoalTrackStatus.BEHIND -> WarningAmber
                                GoalTrackStatus.OVERDUE -> ExpenseRed
                            }

                            RingChartRender(
                                percentageUsedBp = goalSummary.progressBp,
                                ringColor = ringColor,
                                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                                size = 160.dp,
                                strokeWidth = 14.dp,
                                centerContent = {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(
                                            text = "$percentInt%",
                                            style = MaterialTheme.typography.headlineMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "Saved",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            )

                            Spacer(modifier = Modifier.height(Spacing.md))

                            // Plain-language pace summary
                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                modifier = Modifier.padding(horizontal = Spacing.sm)
                            ) {
                                Text(
                                    text = goalSummary.paceSummary,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.xs)
                                )
                            }
                        }
                    }
                }

                // 2. Soft Earmark Warning (D011)
                if (state.isOverEarmarked) {
                    item(key = "earmark_warning") {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(Spacing.md),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = WarningAmber,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(Spacing.sm))
                                Column {
                                    Text(
                                        text = "Account Over-Earmarked",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "Total earmarked to goals linked to '${state.linkedAccount?.name}' exceeds its live balance (Warning only, D011).",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }

                // 3. Financial Metrics Grid
                item(key = "metrics_grid") {
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
                                .padding(Spacing.md),
                            verticalArrangement = Arrangement.spacedBy(Spacing.sm)
                        ) {
                            MetricRow("Current Balance", MoneyFormatter.formatPaiseToRupees(goalSummary.balancePaise), isBold = true)
                            MetricRow("Target Amount", MoneyFormatter.formatPaiseToRupees(goal.targetPaise))
                            MetricRow("Remaining Amount", MoneyFormatter.formatPaiseToRupees(goalSummary.remainingPaise))
                            if (goal.deadlineEpochMs != null) {
                                MetricRow("Target Deadline", DateTimeUtils.formatDate(goal.deadlineEpochMs))
                                MetricRow("Months Left", "${goalSummary.monthsLeft} mo")
                                MetricRow("Required Monthly Pace", MoneyFormatter.formatPaiseToRupees(goalSummary.requiredMonthlyPaise))
                            }
                            MetricRow("Linked Account", state.linkedAccount?.name ?: "None (Unlinked)")
                            MetricRow("Priority", "P${goal.priority}")
                            MetricRow("Status", goal.status.name)
                        }
                    }
                }

                // 4. Action Buttons
                item(key = "action_buttons") {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(Spacing.sm)
                    ) {
                        if (goal.status != SavingsGoalStatus.ARCHIVED) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                            ) {
                                Button(
                                    onClick = { showDepositSheet = true },
                                    colors = ButtonDefaults.buttonColors(containerColor = IncomeGreen),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(imageVector = Icons.Default.ArrowUpward, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(Spacing.xs))
                                    Text("Deposit")
                                }
                                Button(
                                    onClick = { showWithdrawSheet = true },
                                    colors = ButtonDefaults.buttonColors(containerColor = ExpenseRed),
                                    modifier = Modifier.weight(1f),
                                    enabled = goalSummary.balancePaise > 0L
                                ) {
                                    Icon(imageVector = Icons.Default.ArrowDownward, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(Spacing.xs))
                                    Text("Withdraw")
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                            ) {
                                if (goal.status == SavingsGoalStatus.ACTIVE) {
                                    OutlinedButton(
                                        onClick = {
                                            viewModel.pauseGoal(goal.id, onSuccess = {
                                                scope.launch { snackbarHostState.showSnackbar("Goal paused") }
                                            })
                                        },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(imageVector = Icons.Default.Pause, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(Spacing.xs))
                                        Text("Pause")
                                    }
                                } else if (goal.status == SavingsGoalStatus.PAUSED) {
                                    OutlinedButton(
                                        onClick = {
                                            viewModel.resumeGoal(goal.id, onSuccess = {
                                                scope.launch { snackbarHostState.showSnackbar("Goal resumed") }
                                            })
                                        },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(Spacing.xs))
                                        Text("Resume")
                                    }
                                }

                                OutlinedButton(
                                    onClick = {
                                        viewModel.archiveGoal(goal.id, onSuccess = {
                                            scope.launch { snackbarHostState.showSnackbar("Goal archived") }
                                            onNavigateBack()
                                        })
                                    },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(imageVector = Icons.Default.Archive, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(Spacing.xs))
                                    Text("Archive")
                                }
                            }
                        }
                    }
                }

                // 5. Contribution History Header
                item(key = "history_header") {
                    SectionHeader(title = "Contribution History")
                }

                // 6. Contribution History Items
                if (state.contributions.isEmpty()) {
                    item(key = "history_empty") {
                        EmptyState(
                            title = "No contributions yet",
                            subtitle = "Tap Deposit above to add your first earmarked savings towards this goal."
                        )
                    }
                } else {
                    items(state.contributions, key = { it.id }) { contribution ->
                        ContributionRow(contribution)
                    }
                }
            }
        }
    }

    // Sheets
    if (showEditSheet && goalSummary != null) {
        GoalEditSheet(
            existingGoal = goalSummary.goal,
            availableAccounts = homeState.accounts,
            onDismiss = { showEditSheet = false },
            onSave = { name, targetPaise, deadlineMs, priority, linkedAccountId ->
                viewModel.updateGoal(
                    id = goalSummary.goal.id,
                    name = name,
                    targetPaise = targetPaise,
                    deadlineEpochMs = deadlineMs,
                    priority = priority,
                    linkedAccountId = linkedAccountId,
                    onSuccess = {
                        showEditSheet = false
                        scope.launch { snackbarHostState.showSnackbar("Goal updated") }
                    },
                    onError = { err ->
                        scope.launch { snackbarHostState.showSnackbar(err) }
                    }
                )
            }
        )
    }

    if (showDepositSheet && goalSummary != null) {
        GoalDepositSheet(
            goal = goalSummary.goal,
            currentBalancePaise = goalSummary.balancePaise,
            onDismiss = { showDepositSheet = false },
            onConfirmDeposit = { amountPaise, note ->
                viewModel.deposit(
                    goalId = goalSummary.goal.id,
                    amountPaise = amountPaise,
                    note = note,
                    onSuccess = {
                        showDepositSheet = false
                        scope.launch { snackbarHostState.showSnackbar("Deposit recorded") }
                    },
                    onError = { err ->
                        scope.launch { snackbarHostState.showSnackbar(err) }
                    }
                )
            }
        )
    }

    if (showWithdrawSheet && goalSummary != null) {
        GoalWithdrawSheet(
            goal = goalSummary.goal,
            currentBalancePaise = goalSummary.balancePaise,
            onDismiss = { showWithdrawSheet = false },
            onConfirmWithdrawal = { amountPaise, note ->
                viewModel.withdraw(
                    goalId = goalSummary.goal.id,
                    amountPaise = amountPaise,
                    note = note,
                    onSuccess = {
                        showWithdrawSheet = false
                        scope.launch { snackbarHostState.showSnackbar("Withdrawal recorded") }
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
private fun MetricRow(label: String, value: String, isBold: Boolean = false) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (isBold) FontWeight.Bold else FontWeight.Normal
        )
    }
}

@Composable
private fun ContributionRow(contribution: SavingsContributionEntity) {
    val isDeposit = contribution.amountPaise >= 0L
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
                .padding(horizontal = Spacing.md, vertical = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(
                        if (isDeposit) IncomeGreen.copy(alpha = 0.15f) else ExpenseRed.copy(alpha = 0.15f)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isDeposit) Icons.Default.ArrowUpward else Icons.Default.ArrowDownward,
                    contentDescription = null,
                    tint = if (isDeposit) IncomeGreen else ExpenseRed,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(Spacing.md))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = contribution.note ?: if (isDeposit) "Deposit" else "Withdrawal",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "${DateTimeUtils.formatDateTime(contribution.createdAt)} • ${contribution.source.name}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Text(
                text = MoneyFormatter.formatPaiseToRupees(contribution.amountPaise, showSign = true),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = if (isDeposit) IncomeGreen else ExpenseRed
            )
        }
    }
}
