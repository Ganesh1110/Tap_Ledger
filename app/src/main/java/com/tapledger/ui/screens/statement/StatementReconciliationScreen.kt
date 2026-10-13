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
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import com.tapledger.data.statement.PdfPasswordRequiredException
import com.tapledger.data.statement.StatementPdfParser
import java.io.ByteArrayInputStream
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.provider.OpenableColumns
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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

    LaunchedEffect(state.successMessage) {
        state.successMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearSuccessMessage()
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
                    IconButton(onClick = { showImportDialog = true }) {
                        Icon(Icons.Default.FileUpload, contentDescription = "Import Statement")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showImportDialog = true },
                icon = { Icon(Icons.Default.FileUpload, contentDescription = null) },
                text = { Text("Import PDF Statement") },
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
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
                    text = "Import a bank PDF or CSV statement to verify ledger entries against official statements.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(Spacing.md))

                // Format hint
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(Spacing.md)) {
                        Text(
                            text = "Supported Statement Formats:",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "• Official Bank PDF Statements (HDFC, SBI, ICICI, Axis, etc.)\n• Password-protected PDFs supported securely on-device\n• Bank CSV exports with Date, Narration, Withdrawal, Deposit",
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
                    Text("Import PDF / CSV Statement")
                }
            }
        } else {
            val selected = state.selectedImport
            val lines = state.selectedImportLines
            val matchedCount = lines.count { it.matchStatus == StatementMatchStatus.MATCHED || it.matchStatus == StatementMatchStatus.RESOLVED }
            val missingCount = lines.count { it.matchStatus == StatementMatchStatus.MISSING_IN_APP }

            val totalDebitsPaise = lines.sumOf { it.debitPaise }
            val totalCreditsPaise = lines.sumOf { it.creditPaise }

            val isFullyReviewed = lines.isNotEmpty() && missingCount == 0
            val isMatchingDone = matchedCount > 0 || isFullyReviewed

            val distinctMonthsInLines = remember(lines) {
                lines.map { DateTimeUtils.toYearMonth(it.transactionDate) }.distinct().sorted()
            }
            var selectedMonthFilter by remember(selected?.id) { mutableStateOf<java.time.YearMonth?>(null) }
            val displayLines = remember(lines, selectedMonthFilter) {
                if (selectedMonthFilter == null) lines
                else lines.filter { DateTimeUtils.toYearMonth(it.transactionDate) == selectedMonthFilter }
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = Spacing.md),
                contentPadding = PaddingValues(top = Spacing.xs, bottom = Spacing.listBottomPadding),
                verticalArrangement = Arrangement.spacedBy(Spacing.md)
            ) {
                // 1. Import Selector Chips (by Account & Month)
                item(key = "import_chips") {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
                    ) {
                        items(state.imports, key = { it.id }) { imp ->
                            val isSel = imp.id == selected?.id
                            val account = state.accounts.firstOrNull { it.id == imp.accountId }
                            val startYm = DateTimeUtils.toYearMonth(imp.periodStart)
                            val endYm = DateTimeUtils.toYearMonth(imp.periodEnd)
                            val monthLabel = if (startYm == endYm) {
                                DateTimeUtils.formatMonthYear(startYm.year, startYm.monthValue)
                            } else {
                                "${DateTimeUtils.formatDate(imp.periodStart)} – ${DateTimeUtils.formatDate(imp.periodEnd)}"
                            }
                            val accName = account?.name?.take(10) ?: "Account"
                            FilterChip(
                                selected = isSel,
                                onClick = { viewModel.selectImport(imp) },
                                label = {
                                    Text("$accName • $monthLabel")
                                },
                                leadingIcon = if (isSel) {
                                    { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                                } else null
                            )
                        }
                    }
                }

                // 2. Selected Statement Month Header Card
                selected?.let { imp ->
                    item(key = "statement_month_header") {
                        val account = state.accounts.firstOrNull { it.id == imp.accountId }
                        val startYm = DateTimeUtils.toYearMonth(imp.periodStart)
                        val endYm = DateTimeUtils.toYearMonth(imp.periodEnd)
                        val monthTitle = if (startYm == endYm) {
                            "${DateTimeUtils.formatMonthYear(startYm.year, startYm.monthValue)} Statement"
                        } else {
                            "Statement (${DateTimeUtils.formatDate(imp.periodStart)} – ${DateTimeUtils.formatDate(imp.periodEnd)})"
                        }

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainer
                            ),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(Spacing.md),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = monthTitle,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "${account?.name ?: "Account"}${account?.identifierLast4?.let { " (•••• $it)" } ?: ""} • ${imp.lineCount} transactions",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(
                                            when (imp.status) {
                                                com.tapledger.core.model.StatementImportStatus.RECONCILED -> IncomeGreen.copy(alpha = 0.15f)
                                                com.tapledger.core.model.StatementImportStatus.ARITHMETIC_ERROR -> ExpenseRed.copy(alpha = 0.2f)
                                                else -> WarningAmber.copy(alpha = 0.2f)
                                            }
                                        )
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = when (imp.status) {
                                            com.tapledger.core.model.StatementImportStatus.RECONCILED -> "RECONCILED"
                                            com.tapledger.core.model.StatementImportStatus.ARITHMETIC_ERROR -> "ARITHMETIC ERROR"
                                            else -> "IN REVIEW"
                                        },
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = when (imp.status) {
                                            com.tapledger.core.model.StatementImportStatus.RECONCILED -> IncomeGreen
                                            com.tapledger.core.model.StatementImportStatus.ARITHMETIC_ERROR -> ExpenseRed
                                            else -> WarningAmber
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                // 3. Reconciliation Stepper (Import -> Match -> Review)
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
                            StepChip(number = 1, label = "Imported", isCompleted = selected != null)
                            Text("→", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            StepChip(number = 2, label = "Matched", isCompleted = isMatchingDone)
                            Text("→", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            StepChip(number = 3, label = "Reviewed", isCompleted = isFullyReviewed)
                        }
                    }
                }

                if (isFullyReviewed) {
                    item(key = "reviewed_success_card") {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = IncomeGreen.copy(alpha = 0.12f)
                            ),
                            border = BorderStroke(1.dp, IncomeGreen.copy(alpha = 0.4f)),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(Spacing.md),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = IncomeGreen,
                                    modifier = Modifier.size(28.dp)
                                )
                                Spacer(modifier = Modifier.width(Spacing.sm))
                                Column {
                                    Text(
                                        text = "Statement Fully Reviewed & Reconciled",
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.titleSmall,
                                        color = IncomeGreen
                                    )
                                    Text(
                                        text = "All ${lines.size} transactions are accounted for in your ledger.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
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

                                if (!isPass) {
                                    val inferredOpening = lines.firstOrNull()?.let { f -> f.balanceAfterPaise?.let { it + f.debitPaise - f.creditPaise } }
                                    val inferredClosing = lines.lastOrNull()?.balanceAfterPaise
                                    if (inferredOpening != null && inferredClosing != null) {
                                        Spacer(modifier = Modifier.height(Spacing.xs))
                                        OutlinedButton(
                                            onClick = {
                                                viewModel.updateImportBalances(imp.id, inferredOpening, inferredClosing)
                                            },
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Auto-Align Balances from Statement Lines", fontSize = 12.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // 3.5 V12 Reconciliation Bridge Card
                    item(key = "reconciliation_bridge_card") {
                        com.tapledger.ui.screens.statement.components.ReconciliationBridgeCard(
                            bridgeState = state.reconciliationBridgeState
                        )
                    }
                }

                // 4. In-Statement Month Filter (if lines span multiple months)
                if (distinctMonthsInLines.size > 1) {
                    item(key = "month_filter_tabs") {
                        Column(modifier = Modifier.padding(vertical = 4.dp)) {
                            Text(
                                text = "Filter Lines by Month:",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                                item {
                                    FilterChip(
                                        selected = selectedMonthFilter == null,
                                        onClick = { selectedMonthFilter = null },
                                        label = { Text("All Months (${lines.size})") }
                                    )
                                }
                                items(distinctMonthsInLines) { ym ->
                                    val count = lines.count { DateTimeUtils.toYearMonth(it.transactionDate) == ym }
                                    FilterChip(
                                        selected = selectedMonthFilter == ym,
                                        onClick = { selectedMonthFilter = ym },
                                        label = { Text("${DateTimeUtils.formatMonthYear(ym.year, ym.monthValue)} ($count)") }
                                    )
                                }
                            }
                        }
                    }
                }

                // 5. Collapsible Group: Missing in App (Actionable)
                val missingLines = displayLines.filter { it.matchStatus == StatementMatchStatus.MISSING_IN_APP }
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

                // 6. Collapsible Group: Matched Lines
                val matchedLines = displayLines.filter { it.matchStatus == StatementMatchStatus.MATCHED || it.matchStatus == StatementMatchStatus.RESOLVED }
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
        ImportStatementDialog(
            accounts = state.accounts,
            onDismiss = { showImportDialog = false },
            onImportCsv = { accountId, csv, openRupees, closeRupees ->
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
            },
            onImportPdf = { accountId, pdfBytes, password, openRupees, closeRupees, periodStart, periodEnd ->
                viewModel.importPdfStatement(
                    accountId = accountId,
                    inputStream = ByteArrayInputStream(pdfBytes),
                    password = password,
                    openingBalanceRupees = openRupees,
                    closingBalanceRupees = closeRupees,
                    periodStart = periodStart,
                    periodEnd = periodEnd,
                    onSuccess = {
                        showImportDialog = false
                    }
                )
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportStatementDialog(
    accounts: List<AccountEntity>,
    onDismiss: () -> Unit,
    onImportCsv: (Long, String, String, String) -> Unit,
    onImportPdf: (Long, ByteArray, String?, String, String, Long?, Long?) -> Unit,
    initialTab: Int = 0
) {
    var selectedTab by remember { mutableStateOf(initialTab) } // 0: PDF, 1: CSV
    var selectedAccount by remember { mutableStateOf<AccountEntity?>(accounts.firstOrNull()) }
    var showAccountMenu by remember { mutableStateOf(false) }

    // Balances
    var openBalance by remember { mutableStateOf("") }
    var closeBalance by remember { mutableStateOf("") }

    // CSV fields
    var csvText by remember { mutableStateOf("") }

    // PDF fields
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var pdfBytes by remember { mutableStateOf<ByteArray?>(null) }
    var pdfFileName by remember { mutableStateOf<String?>(null) }
    var isEncrypted by remember { mutableStateOf(false) }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var parsedLineCount by remember { mutableStateOf<Int?>(null) }
    var parseError by remember { mutableStateOf<String?>(null) }
    var isParsingPdf by remember { mutableStateOf(false) }
    var detectedPeriodStart by remember { mutableStateOf<Long?>(null) }
    var detectedPeriodEnd by remember { mutableStateOf<Long?>(null) }
    var detectedMonths by remember { mutableStateOf<List<java.time.YearMonth>>(emptyList()) }

    fun inspectPdf(bytes: ByteArray, pwd: String?) {
        isParsingPdf = true
        coroutineScope.launch {
            try {
                val result = withContext(Dispatchers.Default) {
                    StatementPdfParser.parsePdf(
                        inputStream = ByteArrayInputStream(bytes),
                        password = pwd?.ifBlank { null }
                    )
                }
                isEncrypted = false
                parseError = null
                parsedLineCount = result.lines.size
                detectedMonths = result.detectedMonths
                if (result.detectedOpeningBalancePaise != null) {
                    openBalance = String.format(Locale.US, "%.2f", result.detectedOpeningBalancePaise / 100.0)
                }
                if (result.detectedClosingBalancePaise != null) {
                    closeBalance = String.format(Locale.US, "%.2f", result.detectedClosingBalancePaise / 100.0)
                }
                detectedPeriodStart = result.detectedPeriodStart
                detectedPeriodEnd = result.detectedPeriodEnd
            } catch (e: PdfPasswordRequiredException) {
                isEncrypted = true
                parseError = e.message
                parsedLineCount = null
                detectedMonths = emptyList()
            } catch (e: Exception) {
                parseError = e.message ?: "Failed to read or parse PDF"
                parsedLineCount = null
                detectedMonths = emptyList()
            } finally {
                isParsingPdf = false
            }
        }
    }

    val pdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            try {
                var name = "statement.pdf"
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIdx != -1 && cursor.moveToFirst()) {
                        name = cursor.getString(nameIdx)
                    }
                }
                pdfFileName = name
                val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                pdfBytes = bytes
                password = ""
                isEncrypted = false
                parseError = null
                parsedLineCount = null
                if (bytes != null) {
                    inspectPdf(bytes, null)
                }
            } catch (e: Exception) {
                parseError = e.message ?: "Failed to open PDF file"
            }
        }
    }

    var isImporting by remember { mutableStateOf(false) }

    val canConfirm = (if (selectedTab == 0) {
        selectedAccount != null && pdfBytes != null && (!isEncrypted || password.isNotBlank()) && !isParsingPdf
    } else {
        selectedAccount != null && csvText.isNotBlank()
    }) && !isImporting

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Import Bank Statement",
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
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                    ) {
                        Text("Bank PDF")
                    }
                    SegmentedButton(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                    ) {
                        Text("CSV / Text")
                    }
                }

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

                if (selectedTab == 0) {
                    if (pdfBytes == null) {
                        OutlinedButton(
                            onClick = { pdfLauncher.launch("application/pdf") },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.AttachFile, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Select Bank PDF")
                        }
                        Text(
                            text = "Supports official e-statements from HDFC, SBI, ICICI, Axis and others. PDF text extraction & decryption runs 100% offline on your device.",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Default.Description, contentDescription = null, modifier = Modifier.size(20.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = pdfFileName ?: "statement.pdf",
                                        fontWeight = FontWeight.Medium,
                                        fontSize = 13.sp,
                                        modifier = Modifier.weight(1f)
                                    )
                                    TextButton(onClick = { pdfLauncher.launch("application/pdf") }) {
                                        Text("Change", fontSize = 12.sp)
                                    }
                                }

                                if (isParsingPdf) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                        Text("Analyzing statement...", fontSize = 12.sp)
                                    }
                                }

                                if (isEncrypted) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Lock,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Text(
                                            text = "Password Protected",
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.error
                                        )
                                    }
                                    OutlinedTextField(
                                        value = password,
                                        onValueChange = { password = it },
                                        label = { Text("Statement Password") },
                                        placeholder = { Text("e.g. PAN + DOB") },
                                        singleLine = true,
                                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                        trailingIcon = {
                                            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                                Icon(
                                                    imageVector = if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                                    contentDescription = "Toggle password visibility"
                                                )
                                            }
                                        },
                                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    Button(
                                        onClick = {
                                            pdfBytes?.let { inspectPdf(it, password) }
                                        },
                                        enabled = password.isNotBlank() && !isParsingPdf,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text("Unlock & Inspect")
                                    }
                                }

                                if (parsedLineCount != null) {
                                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.CheckCircle,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Text(
                                                text = "Detected $parsedLineCount transactions",
                                                color = MaterialTheme.colorScheme.primary,
                                                fontWeight = FontWeight.Medium,
                                                fontSize = 12.sp
                                            )
                                        }

                                        if (detectedMonths.isNotEmpty()) {
                                            val monthsLabel = detectedMonths.joinToString(", ") {
                                                DateTimeUtils.formatMonthYear(it.year, it.monthValue)
                                            }
                                            if (detectedMonths.size == 1) {
                                                Text(
                                                    text = "Identified Statement Month: $monthsLabel",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )
                                            } else {
                                                Column(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .clip(RoundedCornerShape(8.dp))
                                                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f))
                                                        .padding(8.dp)
                                                ) {
                                                    Text(
                                                        text = "Multi-Month Statement (${detectedMonths.size} Months Detected)",
                                                        style = MaterialTheme.typography.labelMedium,
                                                        fontWeight = FontWeight.Bold,
                                                        color = MaterialTheme.colorScheme.primary
                                                    )
                                                    Text(
                                                        text = monthsLabel,
                                                        style = MaterialTheme.typography.bodySmall,
                                                        fontWeight = FontWeight.Medium,
                                                        color = MaterialTheme.colorScheme.onSurface
                                                    )
                                                    Spacer(modifier = Modifier.height(2.dp))
                                                    Text(
                                                        text = "TapLedger will automatically differentiate this statement into ${detectedMonths.size} monthly statements for accurate month-by-month reconciliation.",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }

                                if (parseError != null && !isEncrypted) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Warning,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Text(
                                            text = parseError ?: "Parse failed",
                                            color = MaterialTheme.colorScheme.error,
                                            fontSize = 12.sp
                                        )
                                    }
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
                                imeAction = ImeAction.Done
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                } else {
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
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val accId = selectedAccount?.id ?: return@Button
                    isImporting = true
                    if (selectedTab == 0) {
                        val bytes = pdfBytes ?: return@Button
                        onImportPdf(
                            accId,
                            bytes,
                            password.ifBlank { null },
                            openBalance,
                            closeBalance,
                            detectedPeriodStart,
                            detectedPeriodEnd
                        )
                    } else {
                        onImportCsv(accId, csvText, openBalance, closeBalance)
                    }
                },
                enabled = canConfirm
            ) {
                if (isImporting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Processing Statement...")
                } else {
                    Text(if (selectedTab == 0) "Import & Process PDF" else "Import & Process CSV")
                }
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
fun ImportStatementCsvDialog(
    accounts: List<AccountEntity>,
    onDismiss: () -> Unit,
    onImport: (Long, String, String, String) -> Unit
) {
    ImportStatementDialog(
        accounts = accounts,
        onDismiss = onDismiss,
        onImportCsv = onImport,
        onImportPdf = { _, _, _, _, _, _, _ -> },
        initialTab = 1
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
