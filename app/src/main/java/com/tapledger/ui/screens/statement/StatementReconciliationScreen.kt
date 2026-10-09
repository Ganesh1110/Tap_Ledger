package com.tapledger.ui.screens.statement

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tapledger.core.database.entity.AccountEntity
import com.tapledger.core.database.entity.CategoryEntity
import com.tapledger.core.database.entity.StatementImportEntity
import com.tapledger.core.database.entity.StatementLineEntity
import com.tapledger.core.model.MoneyFormatter
import com.tapledger.core.model.StatementMatchStatus
import com.tapledger.core.util.DateTimeUtils
import com.tapledger.ui.components.EmptyState
import com.tapledger.ui.components.MoneySemantic
import com.tapledger.ui.components.MoneyText
import com.tapledger.ui.components.SkeletonList
import com.tapledger.ui.theme.ExpenseRed
import com.tapledger.ui.theme.IncomeGreen
import com.tapledger.ui.theme.Spacing
import com.tapledger.ui.theme.WarningAmber

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatementReconciliationScreen(
    viewModel: StatementReconciliationViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    var showImportDialog by remember { mutableStateOf(false) }
    var lineToAddToApp by remember { mutableStateOf<StatementLineEntity?>(null) }
    var showMatchedGroup by remember { mutableStateOf(false) }
    var showMissingGroup by remember { mutableStateOf(true) }

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
                        text = "Statement Reconciliation",
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
                    if (state.imports.isNotEmpty()) {
                        IconButton(onClick = { showImportDialog = true }) {
                            Icon(Icons.Default.FileUpload, contentDescription = "Import CSV")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        if (state.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(Spacing.md)
            ) {
                SkeletonList(itemCount = 5)
            }
        } else if (state.imports.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(Spacing.lg),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Description,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.65f)
                )
                Spacer(modifier = Modifier.height(Spacing.md))
                Text(
                    text = "No Statements Imported",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(Spacing.xs))
                Text(
                    text = "Import a bank CSV statement to verify ledger entries against official statements.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(Spacing.md))

                // Short CSV format hint
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(Spacing.md)) {
                        Text(
                            text = "Supported CSV Format:",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Date (YYYY-MM-DD), Narration/Merchant, Withdrawal/Debit, Deposit/Credit",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(Spacing.lg))
                Button(
                    onClick = { showImportDialog = true },
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(Icons.Default.FileUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(Spacing.xs))
                    Text("Import CSV Statement")
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
                    .padding(horizontal = Spacing.md),
                contentPadding = PaddingValues(top = Spacing.xs, bottom = Spacing.listBottomPadding),
                verticalArrangement = Arrangement.spacedBy(Spacing.md)
            ) {
                // 1. Reconciliation Stepper (Import -> Match -> Review)
                item(key = "stepper") {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(Spacing.md),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            StepChip(number = 1, label = "Imported", isCompleted = true)
                            Text("→", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            StepChip(number = 2, label = "Matched", isCompleted = matchedCount > 0)
                            Text("→", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            StepChip(number = 3, label = "Reviewed", isCompleted = state.isArithmeticConsistent)
                        }
                    }
                }

                // 2. Import Selector Chips
                item(key = "import_chips") {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
                    ) {
                        items(state.imports, key = { it.id }) { imp ->
                            val isSel = imp.id == selected?.id
                            FilterChip(
                                selected = isSel,
                                onClick = { viewModel.selectImport(imp) },
                                label = {
                                    Text("Stmt #${imp.id} • ${DateTimeUtils.formatDate(imp.createdAt)}")
                                },
                                leadingIcon = if (isSel) {
                                    { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                                } else null
                            )
                        }
                    }
                }

                // 3. Arithmetic Verification Pass/Fail Card
                selected?.let { imp ->
                    item(key = "audit_card") {
                        val isPass = state.isArithmeticConsistent
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isPass) MaterialTheme.colorScheme.surfaceContainerLow else MaterialTheme.colorScheme.errorContainer
                            ),
                            border = BorderStroke(
                                1.dp,
                                if (isPass) IncomeGreen.copy(alpha = 0.4f) else ExpenseRed.copy(alpha = 0.5f)
                            ),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Column(modifier = Modifier.padding(Spacing.md)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = if (isPass) Icons.Default.CheckCircle else Icons.Default.Warning,
                                            contentDescription = null,
                                            tint = if (isPass) IncomeGreen else ExpenseRed
                                        )
                                        Spacer(modifier = Modifier.width(Spacing.xs))
                                        Text(
                                            text = if (isPass) "Arithmetic Verification Passed" else "Arithmetic Discrepancy",
                                            fontWeight = FontWeight.Bold,
                                            style = MaterialTheme.typography.titleMedium,
                                            color = if (isPass) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onErrorContainer
                                        )
                                    }

                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(if (isPass) IncomeGreen.copy(alpha = 0.15f) else ExpenseRed.copy(alpha = 0.2f))
                                            .padding(horizontal = 8.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = if (isPass) "PASS" else "AUDIT FAIL",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isPass) IncomeGreen else ExpenseRed
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(Spacing.xs))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "Opening: ${MoneyFormatter.formatRupees(imp.openingBalancePaise)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = "Debits: ${MoneyFormatter.formatRupees(totalDebitsPaise)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = ExpenseRed
                                    )
                                    Text(
                                        text = "Credits: ${MoneyFormatter.formatRupees(totalCreditsPaise)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = IncomeGreen
                                    )
                                }

                                Spacer(modifier = Modifier.height(Spacing.xs))
                                Text(
                                    text = "$matchedCount Matched  •  $missingCount Missing in App",
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }

                // 4. Collapsible Group: Missing in App (Actionable)
                val missingLines = lines.filter { it.matchStatus == StatementMatchStatus.MISSING_IN_APP }
                if (missingLines.isNotEmpty()) {
                    item(key = "header_missing") {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showMissingGroup = !showMissingGroup },
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(Spacing.sm),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Missing in App (${missingLines.size})",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = WarningAmber
                                )
                                Icon(
                                    imageVector = if (showMissingGroup) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                    contentDescription = null
                                )
                            }
                        }
                    }

                    if (showMissingGroup) {
                        items(missingLines, key = { it.id }) { line ->
                            StatementLineCard(
                                line = line,
                                onAddToApp = { lineToAddToApp = line },
                                onResolve = { viewModel.resolveLine(line) }
                            )
                        }
                    }
                }

                // 5. Collapsible Group: Matched Lines
                val matchedLines = lines.filter { it.matchStatus == StatementMatchStatus.MATCHED || it.matchStatus == StatementMatchStatus.RESOLVED }
                if (matchedLines.isNotEmpty()) {
                    item(key = "header_matched") {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showMatchedGroup = !showMatchedGroup },
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(Spacing.sm),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Matched Lines (${matchedLines.size})",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = IncomeGreen
                                )
                                Icon(
                                    imageVector = if (showMatchedGroup) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                    contentDescription = null
                                )
                            }
                        }
                    }

                    if (showMatchedGroup) {
                        items(matchedLines, key = { it.id }) { line ->
                            StatementLineCard(
                                line = line,
                                onAddToApp = { lineToAddToApp = line },
                                onResolve = { viewModel.resolveLine(line) }
                            )
                        }
                    }
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
private fun StepChip(number: Int, label: String, isCompleted: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(22.dp)
                .clip(CircleShape)
                .background(if (isCompleted) IncomeGreen else MaterialTheme.colorScheme.surfaceContainerHighest),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "$number",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = if (isCompleted) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = if (isCompleted) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}

@Composable
private fun StatementLineCard(
    line: StatementLineEntity,
    onAddToApp: () -> Unit,
    onResolve: () -> Unit
) {
    val isDebit = line.debitPaise > 0L
    val amountPaise = if (isDebit) line.debitPaise else line.creditPaise

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(Spacing.sm)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = line.description,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "${DateTimeUtils.formatDate(line.transactionDate)}${line.reference?.let { " • Ref: $it" } ?: ""}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                MoneyText(
                    amountPaise = if (isDebit) -amountPaise else amountPaise,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    semantic = if (isDebit) MoneySemantic.Expense else MoneySemantic.Income,
                    showSign = true
                )
            }

            if (line.matchStatus == StatementMatchStatus.MISSING_IN_APP) {
                Spacer(modifier = Modifier.height(Spacing.xs))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onResolve) {
                        Text("Mark Resolved", fontSize = 11.sp)
                    }
                    Spacer(modifier = Modifier.width(Spacing.xs))
                    Button(
                        onClick = onAddToApp,
                        contentPadding = PaddingValues(horizontal = Spacing.sm, vertical = 4.dp)
                    ) {
                        Text("Add to Ledger", fontSize = 11.sp)
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
    var csvText by remember { mutableStateOf("") }
    var openBalance by remember { mutableStateOf("") }
    var closeBalance by remember { mutableStateOf("") }
    var showAccountMenu by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Import Bank Statement CSV",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        },
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

                OutlinedTextField(
                    value = openBalance,
                    onValueChange = { openBalance = it },
                    label = { Text("Opening Balance (₹)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Decimal,
                        imeAction = ImeAction.Next
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = closeBalance,
                    onValueChange = { closeBalance = it },
                    label = { Text("Closing Balance (₹)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Decimal,
                        imeAction = ImeAction.Next
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = csvText,
                    onValueChange = { csvText = it },
                    label = { Text("Paste CSV Content") },
                    placeholder = { Text("Date,Narration,Withdrawal,Deposit\n2026-10-01,Groceries,450.00,0.00") },
                    minLines = 4,
                    maxLines = 8,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val accId = selectedAccount?.id ?: return@Button
                    onImport(accId, csvText, openBalance, closeBalance)
                }
            ) {
                Text("Import")
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

    val isDebit = line.debitPaise > 0L
    val amount = if (isDebit) line.debitPaise else line.creditPaise

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Classify Transaction") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Narration: ${line.description}", fontSize = 13.sp)
                Text(
                    text = "Amount: ${MoneyFormatter.formatRupees(amount)} (${if (isDebit) "Debit" else "Credit"})",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )

                Spacer(modifier = Modifier.height(4.dp))
                Text("Assign to Category:", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
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
                    selectedCategory?.id?.let { onConfirm(it) }
                }
            ) {
                Text("Record Entry")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
