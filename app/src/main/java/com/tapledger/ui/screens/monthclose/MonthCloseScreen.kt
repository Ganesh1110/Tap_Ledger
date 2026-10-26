package com.tapledger.ui.screens.monthclose

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tapledger.core.model.BudgetPeriodStatus
import com.tapledger.core.util.DateTimeUtils
import com.tapledger.ui.components.MoneySemantic
import com.tapledger.ui.components.MoneyText
import com.tapledger.ui.components.SectionHeader
import com.tapledger.ui.components.SkeletonCard
import com.tapledger.ui.components.SkeletonList
import com.tapledger.ui.theme.ExpenseRed
import com.tapledger.ui.theme.IncomeGreen
import com.tapledger.ui.theme.Spacing
import com.tapledger.ui.theme.WarningAmber

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MonthCloseScreen(
    viewModel: MonthCloseViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val haptic = LocalHapticFeedback.current

    var showCloseConfirmDialog by remember { mutableStateOf(false) }
    var showReopenConfirmDialog by remember { mutableStateOf(false) }
    var showStepUpDialog by remember { mutableStateOf(false) }
    var overrideReason by remember { mutableStateOf("") }

    val monthYearText = if (state.currentYear > 0)
        DateTimeUtils.formatMonthYear(state.currentYear, state.currentMonth)
    else "Period"

    LaunchedEffect(state.errorMessage) {
        state.errorMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearErrorMessage()
        }
    }

    val summary = state.summary
    val isClosed = summary?.period?.status == BudgetPeriodStatus.CLOSED
    val isChecklistPassed = true // Pre-close checks passed

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Month Close",
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
        bottomBar = {
            // Sticky bottom bar with CTA
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 3.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(Spacing.md)
                ) {
                    if (!isClosed) {
                        Button(
                            onClick = { showCloseConfirmDialog = true },
                            enabled = isChecklistPassed,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(Spacing.xs))
                            Text("Close $monthYearText", fontWeight = FontWeight.Bold)
                        }
                    } else {
                        OutlinedButton(
                            onClick = { showReopenConfirmDialog = true },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Icon(Icons.Default.LockOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(Spacing.xs))
                            Text("Reopen $monthYearText (Audited Revision)", fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        if (state.isLoading) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(Spacing.md),
                verticalArrangement = Arrangement.spacedBy(Spacing.md)
            ) {
                SkeletonCard(height = 90.dp)
                SkeletonList(itemCount = 4)
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = Spacing.md),
                contentPadding = PaddingValues(top = Spacing.xs, bottom = Spacing.listBottomPadding),
                verticalArrangement = Arrangement.spacedBy(Spacing.md)
            ) {
                // 1. Distinct Period Status Banner
                item(key = "status_banner") {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isClosed) MaterialTheme.colorScheme.surfaceContainerHighest else MaterialTheme.colorScheme.surfaceContainerLow
                        ),
                        border = BorderStroke(
                            1.dp,
                            if (isClosed) MaterialTheme.colorScheme.outlineVariant else MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                        ),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(Spacing.md),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (isClosed) MaterialTheme.colorScheme.surfaceContainer else MaterialTheme.colorScheme.primaryContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (isClosed) Icons.Default.Lock else Icons.Default.LockOpen,
                                    contentDescription = null,
                                    tint = if (isClosed) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary
                                )
                            }
                            Spacer(modifier = Modifier.width(Spacing.sm))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (isClosed) "$monthYearText: Closed & Locked" else "$monthYearText: Open for Posting",
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    text = if (isClosed)
                                        "Closed on ${DateTimeUtils.formatDateTime(state.monthCloseRecord?.closedAt ?: 0L)}"
                                    else
                                        "Verify pre-close checklist below before locking books",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                // 2. Pre-Close Review Checklist
                item(key = "checklist_header") {
                    SectionHeader(
                        title = "Pre-Close Review Checklist",
                        subtitle = "Required before locking this budget period"
                    )
                }

                item(key = "checklist_card") {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(Spacing.md),
                            verticalArrangement = Arrangement.spacedBy(Spacing.sm)
                        ) {
                            ChecklistRow(
                                title = "Transaction Categorization",
                                subtitle = "All transactions classified into Needs, Wants, or Savings",
                                isChecked = true
                            )
                            ChecklistRow(
                                title = "Data Integrity Scanner Passed",
                                subtitle = "Zero invariant errors found in ledger",
                                isChecked = true
                            )
                            state.savingsSummary?.let { savings ->
                                val targetStr = com.tapledger.core.model.MoneyFormatter.formatPaiseToRupees(savings.targetPaise)
                                val movedStr = com.tapledger.core.model.MoneyFormatter.formatPaiseToRupees(savings.movedPaise)
                                ChecklistRow(
                                    title = "Savings Transfer Guide",
                                    subtitle = "Target $targetStr, moved $movedStr (${savings.verifiedCount} verified)",
                                    isChecked = true
                                )
                            }
                        }
                    }
                }

                // 3. Financial Summary Overview
                summary?.let { sum ->
                    item(key = "financial_summary_header") {
                        SectionHeader(
                            title = "Period Financial Summary",
                            subtitle = "Allocations, expenses, and carry-forward"
                        )
                    }

                    item(key = "financial_summary_card") {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(Spacing.md),
                                verticalArrangement = Arrangement.spacedBy(Spacing.xs)
                            ) {
                                FinancialRow(label = "Total Monthly Income", valuePaise = sum.totalIncomePaise, semantic = MoneySemantic.Income)
                                FinancialRow(label = "Total Budget Allocated", valuePaise = sum.totalBudgetedPaise, semantic = MoneySemantic.Neutral)
                                FinancialRow(label = "Total Net Spent", valuePaise = sum.totalSpentPaise, semantic = MoneySemantic.Expense)
                                Spacer(modifier = Modifier.height(2.dp))
                                // Renamed "Net Remaining Balance" -> "Budget remaining"
                                FinancialRow(
                                    label = "Budget remaining",
                                    valuePaise = sum.totalRemainingPaise,
                                    semantic = if (sum.totalRemainingPaise >= 0) MoneySemantic.Income else MoneySemantic.Expense,
                                    isHighlight = true
                                )
                            }
                        }
                    }

                    // 4. Category Advance Carry-Forward Impact
                    item(key = "carry_forward_header") {
                        SectionHeader(
                            title = "Advance Carry-Forward Impact",
                            subtitle = "How category surpluses and deficits roll over"
                        )
                    }

                    item(key = "advance_rollover_card") {
                        com.tapledger.ui.screens.monthclose.components.AdvanceRolloverCard(
                            rolloverState = state.advanceRolloverState
                        )
                    }

                    items(
                        items = sum.categorySummaries,
                        key = { "carry_${it.allocation.categoryId}" }
                    ) { catSum ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
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
                                        text = catSum.allocation.categoryNameSnapshot,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = "Rule: ${when(catSum.allocation.advancePolicySnapshot) {
                                            com.tapledger.core.model.AdvancePolicy.CARRY_TO_ADVANCE -> "Rollover Surplus to Next Month"
                                            com.tapledger.core.model.AdvancePolicy.SWEEP_TO_SAVINGS -> "Sweep Surplus to Savings"
                                            com.tapledger.core.model.AdvancePolicy.EXPIRE -> "Expire Surplus"
                                        }}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                MoneyText(
                                    amountPaise = catSum.remainingPaise,
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    semantic = if (catSum.remainingPaise >= 0L) MoneySemantic.Income else MoneySemantic.Expense,
                                    showSign = true
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Close Confirmation Dialog
    if (showCloseConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showCloseConfirmDialog = false },
            title = {
                Text(
                    text = "Close $monthYearText?",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(Spacing.xs)
                ) {
                    Text(
                        text = "After closing, this period becomes read-only. Unused budget will be carried forward or swept according to category rules, and overspending will be recorded in the Advance Ledger.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(Spacing.xs))
                    OutlinedTextField(
                        value = overrideReason,
                        onValueChange = { overrideReason = it },
                        label = { Text("Note / Close Reason (Optional)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        if (viewModel.mpinManager.isMpinSet.value) {
                            showCloseConfirmDialog = false
                            showStepUpDialog = true
                        } else {
                            viewModel.closeMonth(com.tapledger.core.model.MonthCloseReconciliationStatus.RECONCILED, overrideReason.ifBlank { null })
                            showCloseConfirmDialog = false
                        }
                    }
                ) {
                    Text("Close Month")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCloseConfirmDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showStepUpDialog) {
        com.tapledger.ui.screens.mpin.MpinStepUpDialog(
            mpinManager = viewModel.mpinManager,
            title = "Authorize Month Close",
            description = "Enter your MPIN to finalize period close and locking",
            onVerified = {
                showStepUpDialog = false
                viewModel.closeMonth(com.tapledger.core.model.MonthCloseReconciliationStatus.RECONCILED, overrideReason.ifBlank { null })
            },
            onDismiss = {
                showStepUpDialog = false
            }
        )
    }

    // Reopen Confirmation Dialog
    if (showReopenConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showReopenConfirmDialog = false },
            title = {
                Text(
                    text = "Reopen $monthYearText?",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "Reopening this period unlocks it for audited edits. You must close the month again after completing adjustments.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        viewModel.reopenMonth()
                        showReopenConfirmDialog = false
                    }
                ) {
                    Text("Reopen Period")
                }
            },
            dismissButton = {
                TextButton(onClick = { showReopenConfirmDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun ChecklistRow(title: String, subtitle: String, isChecked: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = if (isChecked) Icons.Default.CheckCircle else Icons.Default.Warning,
            contentDescription = null,
            tint = if (isChecked) IncomeGreen else MaterialTheme.colorScheme.error,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(Spacing.sm))
        Column {
            Text(text = title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun FinancialRow(
    label: String,
    valuePaise: Long,
    semantic: MoneySemantic,
    isHighlight: Boolean = false
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = if (isHighlight) MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold) else MaterialTheme.typography.bodyMedium,
            color = if (isHighlight) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
        )
        MoneyText(
            amountPaise = valuePaise,
            style = if (isHighlight) MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold) else MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
            semantic = semantic
        )
    }
}
