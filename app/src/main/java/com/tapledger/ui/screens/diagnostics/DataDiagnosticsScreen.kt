package com.tapledger.ui.screens.diagnostics

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tapledger.BuildConfig
import com.tapledger.core.database.TapLedgerDatabase
import com.tapledger.core.database.entity.AuditLogEntity
import com.tapledger.core.util.DateTimeUtils
import com.tapledger.domain.model.IntegrityIssue
import com.tapledger.domain.model.IntegritySeverity
import com.tapledger.ui.components.EmptyState
import com.tapledger.ui.components.SectionHeader
import com.tapledger.ui.components.SkeletonList
import com.tapledger.ui.theme.ExpenseRed
import com.tapledger.ui.theme.IncomeGreen
import com.tapledger.ui.theme.Spacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DataDiagnosticsScreen(
    viewModel: DataDiagnosticsViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var selectedTab by remember { mutableIntStateOf(0) }

    LaunchedEffect(state.errorMessage) {
        state.errorMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearErrorMessage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Data Health & Integrity",
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
                // Removed duplicate refresh icon from top bar
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // 1. Scan Action on its own row so it never squeezes text
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.md, vertical = Spacing.xs)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Ledger Invariant Scanner",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Engine v${BuildConfig.VERSION_NAME} (Build ${BuildConfig.VERSION_CODE}) • Schema v${TapLedgerDatabase.DATABASE_VERSION}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = state.lastScannedAt?.let { "Last scanned: ${DateTimeUtils.formatDateTime(it)}" }
                                ?: "Scans for imbalances, orphaned records & math issues",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.sp
                        )
                    }

                    Button(
                        onClick = { viewModel.runIntegrityAudit() },
                        enabled = !state.isAuditRunning,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        if (state.isAuditRunning) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Scan", fontSize = 12.sp)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(Spacing.xxs))

            // 2. Full-width tabs without clipping: "Data Issues" and "Audit Timeline"
            PrimaryTabRow(
                selectedTabIndex = selectedTab,
                modifier = Modifier.fillMaxWidth()
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = {
                        Text(
                            text = "Data Issues (${state.issues.size})",
                            maxLines = 1,
                            style = MaterialTheme.typography.titleSmall
                        )
                    }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = {
                        Text(
                            text = "Audit Timeline (${state.auditLogs.size})",
                            maxLines = 1,
                            style = MaterialTheme.typography.titleSmall
                        )
                    }
                )
            }

            if (state.isLoading || state.isAuditRunning) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(Spacing.md)
                ) {
                    SkeletonList(itemCount = 4)
                }
            } else if (selectedTab == 0) {
                // Tab 1: Data Issues Tab
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = Spacing.md),
                    contentPadding = PaddingValues(top = Spacing.sm, bottom = Spacing.listBottomPadding),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm)
                ) {
                    item(key = "status_card") {
                        val hasNoIssues = state.issues.isEmpty()
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = if (hasNoIssues) MaterialTheme.colorScheme.surfaceContainerLow else MaterialTheme.colorScheme.errorContainer
                            ),
                            border = BorderStroke(
                                1.dp,
                                if (hasNoIssues) IncomeGreen.copy(alpha = 0.4f) else ExpenseRed.copy(alpha = 0.5f)
                            ),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(Spacing.md),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = if (hasNoIssues) Icons.Default.CheckCircle else Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = if (hasNoIssues) IncomeGreen else ExpenseRed,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(Spacing.sm))
                                Column {
                                    Text(
                                        text = if (hasNoIssues) "All Ledger Invariants Intact" else "Data Invariant Issues Detected",
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.titleSmall,
                                        color = if (hasNoIssues) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onErrorContainer
                                    )
                                    Text(
                                        text = if (hasNoIssues)
                                            "Double-entry balances, category allocations, and period dates are 100% consistent."
                                        else
                                            "${state.issues.size} issues found. Review the discrepancies below.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (hasNoIssues) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onErrorContainer
                                    )
                                }
                            }
                        }
                    }

                    // V13 Diagnostics Severity Bar Card
                    item(key = "severity_bar_card") {
                        com.tapledger.ui.screens.diagnostics.components.DiagnosticsSeverityBarCard(
                            severityBarState = state.severityBarState
                        )
                    }

                    if (state.issues.isNotEmpty()) {
                        items(state.issues, key = { "${it.ruleId}_${it.entityType}_${it.entityId}_${it.message}" }) { issue ->
                            IntegrityIssueCard(issue = issue)
                        }
                    }
                }
            } else {
                // Tab 2: Audit Timeline Tab
                if (state.auditLogs.isEmpty()) {
                    EmptyState(
                        title = "No audit log events",
                        subtitle = "Audited changes (voids, adjustments, month closures) will appear here.",
                        icon = Icons.Default.Security
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = Spacing.md),
                        contentPadding = PaddingValues(top = Spacing.sm, bottom = Spacing.listBottomPadding),
                        verticalArrangement = Arrangement.spacedBy(Spacing.xs)
                    ) {
                        items(state.auditLogs, key = { it.id }) { log ->
                            AuditLogRow(log = log)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun IntegrityIssueCard(issue: IntegrityIssue) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        border = BorderStroke(1.dp, ExpenseRed.copy(alpha = 0.4f)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(Spacing.sm)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = issue.ruleId,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = when (issue.severity) {
                        IntegritySeverity.CRITICAL, IntegritySeverity.ERROR -> ExpenseRed
                        IntegritySeverity.WARNING -> MaterialTheme.colorScheme.secondary
                    }
                )
                Text(
                    text = "${issue.entityType} #${issue.entityId}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = issue.message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun AuditLogRow(log: AuditLogEntity) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.sm),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "${log.action.name}: ${log.reason ?: "System execution"}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = DateTimeUtils.formatDateTime(log.createdAt),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = "ID #${log.id}",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
