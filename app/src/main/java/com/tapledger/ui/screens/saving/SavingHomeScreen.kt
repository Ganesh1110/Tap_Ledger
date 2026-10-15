package com.tapledger.ui.screens.saving

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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import com.tapledger.ui.components.StatusBadge
import com.tapledger.ui.theme.ExpenseRed
import com.tapledger.ui.theme.IncomeGreen
import com.tapledger.ui.theme.Spacing
import com.tapledger.ui.theme.WarningAmber
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SavingHomeScreen(
    viewModel: SavingViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit = {},
    onNavigateToGoalDetail: (goalId: Long) -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var showCreateSheet by remember { mutableStateOf(false) }

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
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        shape = RoundedCornerShape(14.dp)
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
                size = 64.dp,
                strokeWidth = 6.dp,
                centerContent = {
                    Text(
                        text = "$percentInt%",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
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
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh
                    ) {
                        Text(
                            text = "P${goal.priority}",
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = "${MoneyFormatter.formatPaiseToRupees(summary.balancePaise)} / ${MoneyFormatter.formatPaiseToRupees(goal.targetPaise)}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
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

            }
        }
    }
}
