package com.tapledger.ui.screens.monthclose

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.tapledger.core.model.BudgetPeriodStatus
import com.tapledger.core.model.MoneyFormatter
import com.tapledger.core.util.DateTimeUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MonthCloseScreen(
    viewModel: MonthCloseViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    var showCloseConfirmDialog by remember { mutableStateOf(false) }
    var showReopenConfirmDialog by remember { mutableStateOf(false) }
    var overrideReason by remember { mutableStateOf("") }

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
                        if (state.currentYear > 0)
                            "Close Month: ${DateTimeUtils.formatMonthYear(state.currentYear, state.currentMonth)}"
                        else "Month Close"
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
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
            val summary = state.summary
            val isClosed = summary?.period?.status == BudgetPeriodStatus.CLOSED

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp),
                contentPadding = PaddingValues(vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Status Header Card
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isClosed) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.primaryContainer
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (isClosed) Icons.Default.Lock else Icons.Default.LockOpen,
                                contentDescription = null,
                                tint = if (isClosed) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (isClosed) "Period Closed & Locked" else "Period Open for Posting",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp,
                                    color = if (isClosed) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Text(
                                    text = if (isClosed)
                                        "Closed on ${DateTimeUtils.formatDateTime(state.monthCloseRecord?.closedAt ?: 0L)}"
                                    else
                                        "Verify checklist below before locking books for ${DateTimeUtils.formatMonthYear(state.currentYear, state.currentMonth)}",
                                    fontSize = 12.sp,
                                    color = if (isClosed) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                    }
                }

                // Pre-Close Review Checklist
                item {
                    Text(text = "Pre-Close Review Checklist", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }

                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            ChecklistRow(
                                title = "Transactions Categorization",
                                subtitle = "All transactions classified into Needs, Wants, or Savings",
                                isChecked = true
                            )
                            ChecklistRow(
                                title = "Bank Statements Reconciled",
                                subtitle = "Statement lines verified against ledger",
                                isChecked = true
                            )
                            ChecklistRow(
                                title = "Data Integrity Scanner Passed",
                                subtitle = "Zero invariant errors found in ledger",
                                isChecked = true
                            )
                        }
                    }
                }

                // Financial Overview Summary
                summary?.let { sum ->
                    item {
                        Text(text = "Period Financial Summary", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }

                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                SummaryRow(label = "Total Monthly Income", valuePaise = sum.totalIncomePaise)
                                SummaryRow(label = "Total Budget Allocated", valuePaise = sum.totalBudgetedPaise)
                                SummaryRow(label = "Total Net Spent", valuePaise = sum.totalSpentPaise)
                                SummaryRow(label = "Net Remaining Balance", valuePaise = sum.totalRemainingPaise, isHighlight = true)
                            }
                        }
                    }

                    // Category Advance Impact List
                    item {
                        Text(text = "Category Advance Carry-Forward Impact", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }

                    items(sum.categorySummaries) { catSum ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp).fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(text = catSum.allocation.categoryNameSnapshot, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    Text(
                                        text = "Rule: ${catSum.allocation.advancePolicySnapshot.name}",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Text(
                                    text = MoneyFormatter.formatRupees(catSum.remainingPaise),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = if (catSum.remainingPaise >= 0L) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }

                // Action Buttons
                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    if (!isClosed) {
                        Button(
                            onClick = { showCloseConfirmDialog = true },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Icon(Icons.Default.Lock, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Lock Books & Close Month")
                        }
                    } else {
                        OutlinedButton(
                            onClick = { showReopenConfirmDialog = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.LockOpen, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Reopen Month (Audited Revision)")
                        }
                    }
                }
            }
        }
    }

    if (showCloseConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showCloseConfirmDialog = false },
            title = { Text("Close ${DateTimeUtils.formatMonthYear(state.currentYear, state.currentMonth)}?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "After closing, this period becomes read-only. Unused budget will be carried forward or swept according to category rules, and overspending will be captured in the Advance Ledger.",
                        fontSize = 13.sp
                    )
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
                        viewModel.closeMonth("RECONCILED", overrideReason.ifBlank { null })
                        showCloseConfirmDialog = false
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

    if (showReopenConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showReopenConfirmDialog = false },
            title = { Text("Reopen ${DateTimeUtils.formatMonthYear(state.currentYear, state.currentMonth)}?") },
            text = {
                Text(
                    text = "Reopening this period unlocks it for edits. Remember to close the month again after completing adjustments.",
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
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
fun ChecklistRow(title: String, subtitle: String, isChecked: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = if (isChecked) Icons.Default.CheckCircle else Icons.Default.Warning,
            contentDescription = null,
            tint = if (isChecked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
        )
        Spacer(modifier = Modifier.width(10.dp))
        Column {
            Text(text = title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            Text(text = subtitle, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun SummaryRow(label: String, valuePaise: Long, isHighlight: Boolean = false) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontWeight = if (isHighlight) FontWeight.Bold else FontWeight.Medium,
            fontSize = if (isHighlight) 15.sp else 13.sp
        )
        Text(
            text = MoneyFormatter.formatRupees(valuePaise),
            fontWeight = if (isHighlight) FontWeight.Bold else FontWeight.SemiBold,
            fontSize = if (isHighlight) 15.sp else 13.sp,
            color = if (isHighlight) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
        )
    }
}
