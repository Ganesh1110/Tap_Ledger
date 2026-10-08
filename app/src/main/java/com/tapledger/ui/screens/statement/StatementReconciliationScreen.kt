package com.tapledger.ui.screens.statement

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
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
import com.tapledger.core.database.entity.AccountEntity
import com.tapledger.core.database.entity.CategoryEntity
import com.tapledger.core.database.entity.StatementLineEntity
import com.tapledger.core.model.MoneyFormatter
import com.tapledger.core.model.StatementImportStatus
import com.tapledger.core.model.StatementMatchStatus
import com.tapledger.core.util.DateTimeUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatementReconciliationScreen(
    viewModel: StatementReconciliationViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    var showImportDialog by remember { mutableStateOf(false) }
    var lineToAddToApp by remember { mutableStateOf<StatementLineEntity?>(null) }

    LaunchedEffect(state.errorMessage) {
        state.errorMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearErrorMessage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Statement Reconciliation") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showImportDialog = true }) {
                        Icon(Icons.Default.FileUpload, contentDescription = "Import Statement")
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
        } else if (state.imports.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "No Bank Statements Imported",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Import a CSV statement to reconcile bank lines against recorded app transactions.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = { showImportDialog = true }) {
                        Icon(Icons.Default.FileUpload, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Import CSV Statement")
                    }
                }
            }
        } else {
            val selected = state.selectedImport
            val lines = state.selectedImportLines

            val matchedCount = lines.count { it.matchStatus == StatementMatchStatus.MATCHED || it.matchStatus == StatementMatchStatus.RESOLVED }
            val missingCount = lines.count { it.matchStatus == StatementMatchStatus.MISSING_IN_APP }

            val totalDebitsPaise = lines.sumOf { it.debitPaise }
            val totalCreditsPaise = lines.sumOf { it.creditPaise }

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp),
                contentPadding = PaddingValues(vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Statement Selector Card
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(text = "Select Statement Import:", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                            Spacer(modifier = Modifier.height(6.dp))
                            var showImportDropdown by remember { mutableStateOf(false) }
                            val accountName = state.accounts.find { it.id == selected?.accountId }?.name ?: "Account ${selected?.accountId}"

                            Box {
                                OutlinedButton(
                                    onClick = { showImportDropdown = true },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("$accountName (${DateTimeUtils.formatDate(selected?.periodStart ?: 0L)} - ${DateTimeUtils.formatDate(selected?.periodEnd ?: 0L)})")
                                }
                                DropdownMenu(
                                    expanded = showImportDropdown,
                                    onDismissRequest = { showImportDropdown = false }
                                ) {
                                    state.imports.forEach { imp ->
                                        val acc = state.accounts.find { it.id == imp.accountId }?.name ?: "Account ${imp.accountId}"
                                        DropdownMenuItem(
                                            text = { Text("$acc (${DateTimeUtils.formatDate(imp.periodStart)} - ${DateTimeUtils.formatDate(imp.periodEnd)})") },
                                            onClick = {
                                                viewModel.selectImport(imp)
                                                showImportDropdown = false
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Arithmetic & Reconciliation Overview
                selected?.let { imp ->
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = if (state.isArithmeticConsistent) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.errorContainer
                            )
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = if (state.isArithmeticConsistent) Icons.Default.CheckCircle else Icons.Default.Warning,
                                            contentDescription = null,
                                            tint = if (state.isArithmeticConsistent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onErrorContainer
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = if (state.isArithmeticConsistent) "Arithmetic Audit Consistent" else "Statement Discrepancy",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 15.sp,
                                            color = if (state.isArithmeticConsistent) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onErrorContainer
                                        )
                                    }
                                    Text(
                                        text = imp.status.name,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.secondary
                                    )
                                }

                                Spacer(modifier = Modifier.height(10.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Opening: ${MoneyFormatter.formatRupees(imp.openingBalancePaise)}", fontSize = 12.sp)
                                    Text("Debits: ${MoneyFormatter.formatRupees(totalDebitsPaise)}", fontSize = 12.sp)
                                    Text("Credits: ${MoneyFormatter.formatRupees(totalCreditsPaise)}", fontSize = 12.sp)
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Reconciliation: $matchedCount Matched / $missingCount Missing in App",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }

                // Statement Lines
                item {
                    Text(
                        text = "Statement Line Items (${lines.size})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                items(lines, key = { it.id }) { line ->
                    StatementLineCard(
                        line = line,
                        onAddToApp = { lineToAddToApp = line },
                        onResolve = { viewModel.resolveLine(line) }
                    )
                }
            }
        }
    }

    if (showImportDialog) {
        ImportStatementCsvDialog(
            accounts = state.accounts,
            onDismiss = { showImportDialog = false },
            onImport = { accountId, csv, openRupees, closeRupees ->
                val now = DateTimeUtils.nowEpochMillis()
                viewModel.importCsvStatement(
                    accountId = accountId,
                    csvContent = csv,
                    openingBalanceRupees = openRupees,
                    closingBalanceRupees = closeRupees,
                    periodStart = now - (86400000L * 30),
                    periodEnd = now
                )
                showImportDialog = false
            }
        )
    }

    lineToAddToApp?.let { line ->
        AddToAppCategoryDialog(
            line = line,
            categories = state.categories,
            onDismiss = { lineToAddToApp = null },
            onConfirm = { catId ->
                viewModel.createAppTransactionFromLine(line, catId)
                lineToAddToApp = null
            }
        )
    }
}

@Composable
fun StatementLineCard(
    line: StatementLineEntity,
    onAddToApp: () -> Unit,
    onResolve: () -> Unit
) {
    val isDebit = line.debitPaise > 0
    val amountPaise = if (isDebit) line.debitPaise else line.creditPaise

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
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = line.description, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Text(
                        text = "${DateTimeUtils.formatDate(line.transactionDate)}${line.reference?.let { " • Ref: $it" } ?: ""}",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = "${if (isDebit) "-" else "+"}${MoneyFormatter.formatRupees(amountPaise)}",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = if (isDebit) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = line.matchStatus.name,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = when (line.matchStatus) {
                        StatementMatchStatus.MATCHED -> MaterialTheme.colorScheme.primary
                        StatementMatchStatus.RESOLVED -> MaterialTheme.colorScheme.secondary
                        else -> MaterialTheme.colorScheme.error
                    }
                )

                if (line.matchStatus == StatementMatchStatus.MISSING_IN_APP) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedButton(
                            onClick = onResolve,
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Ignore", fontSize = 11.sp)
                        }
                        Button(
                            onClick = onAddToApp,
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Add to App", fontSize = 11.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ImportStatementCsvDialog(
    accounts: List<AccountEntity>,
    onDismiss: () -> Unit,
    onImport: (Long, String, String, String) -> Unit
) {
    var selectedAccount by remember { mutableStateOf<AccountEntity?>(accounts.firstOrNull()) }
    var showAccountMenu by remember { mutableStateOf(false) }
    var openingRupees by remember { mutableStateOf("") }
    var closingRupees by remember { mutableStateOf("") }
    var csvContent by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Import Bank Statement (CSV)") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("Select Account:", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                Box {
                    OutlinedButton(
                        onClick = { showAccountMenu = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(selectedAccount?.name ?: "Select Account")
                    }
                    DropdownMenu(
                        expanded = showAccountMenu,
                        onDismissRequest = { showAccountMenu = false }
                    ) {
                        accounts.forEach { acc ->
                            DropdownMenuItem(
                                text = { Text(acc.name) },
                                onClick = {
                                    selectedAccount = acc
                                    showAccountMenu = false
                                }
                            )
                        }
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = openingRupees,
                        onValueChange = { openingRupees = it },
                        label = { Text("Opening (₹)") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = closingRupees,
                        onValueChange = { closingRupees = it },
                        label = { Text("Closing (₹)") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                OutlinedTextField(
                    value = csvContent,
                    onValueChange = { csvContent = it },
                    label = { Text("Paste Statement CSV Lines") },
                    placeholder = { Text("Date, Description, Ref, Debit, Credit, Balance\n08-10-2026, SWIGGY, 4281928, 850, 0, 49150") },
                    minLines = 4,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val accId = selectedAccount?.id ?: return@Button
                    onImport(accId, csvContent, openingRupees, closingRupees)
                }
            ) {
                Text("Import & Reconcile")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun AddToAppCategoryDialog(
    line: StatementLineEntity,
    categories: List<CategoryEntity>,
    onDismiss: () -> Unit,
    onConfirm: (Long) -> Unit
) {
    var selectedCategory by remember { mutableStateOf<CategoryEntity?>(categories.firstOrNull()) }
    var showCategoryMenu by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Statement Line to App") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("Description: ${line.description}", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                val amount = if (line.debitPaise > 0) line.debitPaise else line.creditPaise
                Text("Amount: ${MoneyFormatter.formatRupees(amount)}", fontSize = 14.sp)

                Spacer(modifier = Modifier.height(6.dp))
                Text("Select Category:", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                Box {
                    OutlinedButton(
                        onClick = { showCategoryMenu = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(selectedCategory?.name ?: "Select Category")
                    }
                    DropdownMenu(
                        expanded = showCategoryMenu,
                        onDismissRequest = { showCategoryMenu = false }
                    ) {
                        categories.forEach { cat ->
                            DropdownMenuItem(
                                text = { Text(cat.name) },
                                onClick = {
                                    selectedCategory = cat
                                    showCategoryMenu = false
                                }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val catId = selectedCategory?.id ?: return@Button
                    onConfirm(catId)
                }
            ) {
                Text("Add Transaction")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
