package com.tapledger.ui.screens.pending

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SyncProblem
import androidx.compose.material.icons.filled.Warning
import com.tapledger.service.FinancialNotificationListenerService
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberSwipeToDismissBoxState
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tapledger.core.database.entity.AccountEntity
import com.tapledger.core.database.entity.CategoryEntity
import com.tapledger.core.database.entity.TransactionEntity
import com.tapledger.core.model.BudgetBucket
import com.tapledger.core.util.DateTimeUtils
import com.tapledger.core.util.PermissionUtils
import com.tapledger.ui.components.EmptyState
import com.tapledger.ui.components.MoneySemantic
import com.tapledger.ui.components.MoneyText
import com.tapledger.ui.theme.ExpenseRed
import com.tapledger.ui.theme.IncomeGreen
import com.tapledger.ui.theme.Spacing
import com.tapledger.ui.theme.WarningAmber
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun PendingInboxScreen(
    viewModel: PendingInboxViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit,
    onNavigateToSetup: () -> Unit = {}
) {
    val pendingTxs by viewModel.pendingTransactions.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val accounts by viewModel.accounts.collectAsStateWithLifecycle()
    val suggestedMap by viewModel.suggestedCategoryMap.collectAsStateWithLifecycle()
    val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    var unassignedTxToAssign by remember { mutableStateOf<TransactionEntity?>(null) }
    var unassignedTxToCategorize by remember { mutableStateOf<Pair<TransactionEntity, Long>?>(null) }

    LaunchedEffect(errorMessage) {
        errorMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearErrorMessage()
        }
    }

    val isListenerActive = PermissionUtils.rememberNotificationListenerGranted()

    val isServiceConnected by FinancialNotificationListenerService.connectedState.collectAsStateWithLifecycle(
        initialValue = FinancialNotificationListenerService.isConnected
    )

    val listenerStatusText = when {
        !isListenerActive -> "Listener access inactive in Settings — tap to fix"
        !isServiceConnected -> "Listener disconnected by OS — tap to reconnect"
        else -> "Financial Notification Listener active & connected"
    }
    val listenerStatusColor = when {
        !isListenerActive -> MaterialTheme.colorScheme.error
        !isServiceConnected -> MaterialTheme.colorScheme.tertiary
        else -> IncomeGreen
    }
    val listenerBgColor = when {
        !isListenerActive -> MaterialTheme.colorScheme.errorContainer
        !isServiceConnected -> MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f)
        else -> MaterialTheme.colorScheme.surfaceContainerLow
    }
    val listenerIcon = when {
        !isListenerActive -> Icons.Default.Warning
        !isServiceConnected -> Icons.Default.SyncProblem
        else -> Icons.Default.HealthAndSafety
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Pending Inbox (${pendingTxs.size})",
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
                    IconButton(onClick = onNavigateToSetup) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Auto-Capture Setup"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        if (pendingTxs.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                EmptyState(
                    title = "Inbox Zero",
                    subtitle = "All captured SMS and UPI transactions are classified into budget envelopes.",
                    icon = Icons.Default.CheckCircle,
                    actionLabel = if (!isListenerActive) "Enable Listener Setup" else null,
                    onActionClick = if (!isListenerActive) onNavigateToSetup else null
                )

                Spacer(modifier = Modifier.height(Spacing.sm))

                // Listener health indicator with 1-tap rebind
                Card(
                    modifier = Modifier
                        .padding(horizontal = Spacing.lg)
                        .clickable {
                            if (!isListenerActive) {
                                onNavigateToSetup()
                            } else if (!isServiceConnected) {
                                FinancialNotificationListenerService.requestRebindService(context, forceToggleComponent = true)
                                coroutineScope.launch {
                                    snackbarHostState.showSnackbar("Rebinding notification listener...")
                                }
                            }
                        },
                    colors = CardDefaults.cardColors(containerColor = listenerBgColor),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = listenerIcon,
                            contentDescription = null,
                            tint = listenerStatusColor,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(Spacing.xs))
                        Text(
                            text = listenerStatusText,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            color = if (isListenerActive && isServiceConnected) MaterialTheme.colorScheme.onSurfaceVariant else listenerStatusColor
                        )
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = Spacing.md),
                contentPadding = PaddingValues(top = Spacing.xs, bottom = Spacing.listBottomPadding),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                // If listener is disconnected or inactive, alert user at top of pending list
                if (!isListenerActive || !isServiceConnected) {
                    item(key = "listener_warning_header") {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (!isListenerActive) {
                                        onNavigateToSetup()
                                    } else {
                                        FinancialNotificationListenerService.requestRebindService(context, forceToggleComponent = true)
                                        coroutineScope.launch {
                                            snackbarHostState.showSnackbar("Rebinding notification listener...")
                                        }
                                    }
                                },
                            colors = CardDefaults.cardColors(containerColor = listenerBgColor),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = listenerIcon,
                                    contentDescription = null,
                                    tint = listenerStatusColor,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(Spacing.xs))
                                Text(
                                    text = listenerStatusText,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Medium,
                                    color = listenerStatusColor
                                )
                            }
                        }
                    }
                }
                items(
                    items = pendingTxs,
                    key = { it.id }
                ) { tx ->
                    val suggestedCat = categories.find { it.id == suggestedMap[tx.id] }
                    val isIncome = tx.kind == com.tapledger.core.model.TransactionKind.INCOME ||
                        tx.kind == com.tapledger.core.model.TransactionKind.INTEREST_EARNED ||
                        tx.direction == com.tapledger.core.model.TransactionDirection.IN

                    val dismissState = rememberSwipeToDismissBoxState(
                        confirmValueChange = { dismissValue ->
                            when (dismissValue) {
                                SwipeToDismissBoxValue.StartToEnd -> {
                                    if (isIncome) {
                                        if (tx.accountId == null) {
                                            unassignedTxToAssign = tx
                                        } else {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            viewModel.confirmTransaction(tx.id)
                                        }
                                    } else {
                                        val targetCatId = suggestedCat?.id ?: categories.firstOrNull()?.id
                                        if (targetCatId != null) {
                                            if (tx.accountId == null) {
                                                unassignedTxToCategorize = tx to targetCatId
                                            } else {
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                viewModel.categorizeTransaction(tx.id, targetCatId, tx.amountPaise)
                                            }
                                        }
                                    }
                                    true
                                }
                                SwipeToDismissBoxValue.EndToStart -> {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    viewModel.voidTransaction(tx.id)
                                    coroutineScope.launch {
                                        val result = snackbarHostState.showSnackbar(
                                            message = "Transaction ignored",
                                            actionLabel = "Undo",
                                            duration = SnackbarDuration.Short
                                        )
                                        if (result == SnackbarResult.ActionPerformed) {
                                            viewModel.restoreTransaction(tx.id)
                                        }
                                    }
                                    true
                                }
                                else -> false
                            }
                        }
                    )

                    SwipeToDismissBox(
                        state = dismissState,
                        modifier = Modifier.animateItem(),
                        backgroundContent = {
                            val color = when (dismissState.targetValue) {
                                SwipeToDismissBoxValue.StartToEnd -> IncomeGreen.copy(alpha = 0.2f)
                                SwipeToDismissBoxValue.EndToStart -> ExpenseRed.copy(alpha = 0.2f)
                                else -> MaterialTheme.colorScheme.surfaceContainer
                            }
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(color)
                                    .padding(horizontal = Spacing.lg),
                                contentAlignment = if (dismissState.dismissDirection == SwipeToDismissBoxValue.StartToEnd)
                                    Alignment.CenterStart else Alignment.CenterEnd
                            ) {
                                if (dismissState.dismissDirection == SwipeToDismissBoxValue.StartToEnd) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.Done,
                                            contentDescription = if (isIncome) "Confirm Income" else "Categorize",
                                            tint = IncomeGreen
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = if (isIncome) "Confirm Income" else "Apply Suggestion",
                                            fontWeight = FontWeight.Bold,
                                            color = IncomeGreen
                                        )
                                    }
                                } else {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text("Ignore", fontWeight = FontWeight.Bold, color = ExpenseRed)
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Icon(Icons.Default.DeleteSweep, contentDescription = "Ignore", tint = ExpenseRed)
                                    }
                                }
                            }
                        }
                    ) {
                        PendingTransactionCard(
                            tx = tx,
                            categories = categories,
                            accounts = accounts,
                            suggestedCategory = suggestedCat,
                            onAssignAccount = { accountId ->
                                viewModel.assignAccount(tx.id, accountId)
                            },
                            onRequestAssignAccount = {
                                unassignedTxToAssign = tx
                            },
                            onConfirm = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                viewModel.confirmTransaction(tx.id)
                            },
                            onCategorize = { catId ->
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                if (tx.accountId == null) {
                                    unassignedTxToCategorize = tx to catId
                                } else {
                                    viewModel.categorizeTransaction(tx.id, catId, tx.amountPaise)
                                }
                            },
                            onVoid = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                viewModel.voidTransaction(tx.id)
                                coroutineScope.launch {
                                    val result = snackbarHostState.showSnackbar(
                                        message = "Transaction ignored",
                                        actionLabel = "Undo",
                                        duration = SnackbarDuration.Short
                                    )
                                    if (result == SnackbarResult.ActionPerformed) {
                                        viewModel.restoreTransaction(tx.id)
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }

        // Account Selection Dialog for Unassigned Transaction (assign only)
        unassignedTxToAssign?.let { tx ->
            AlertDialog(
                onDismissRequest = { unassignedTxToAssign = null },
                title = { Text("Assign Account") },
                text = {
                    Column {
                        Text(
                            text = "Select the account used for this transaction (${tx.merchantRaw ?: "Expense"} • ₹${tx.amountPaise / 100}):",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(modifier = Modifier.height(Spacing.sm))
                        if (accounts.isEmpty()) {
                            Text(
                                text = "No active accounts found. Please add an account first.",
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall
                            )
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                                accounts.forEach { acc ->
                                    Card(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                viewModel.assignAccount(tx.id, acc.id)
                                                unassignedTxToAssign = null
                                            },
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(Spacing.sm),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(Icons.Default.AccountBalance, contentDescription = null, modifier = Modifier.size(20.dp))
                                            Spacer(modifier = Modifier.width(Spacing.xs))
                                            Text(acc.name, fontWeight = FontWeight.Medium)
                                        }
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = {},
                dismissButton = {
                    TextButton(onClick = { unassignedTxToAssign = null }) {
                        Text("Cancel")
                    }
                }
            )
        }

        // Account Selection Dialog for Unassigned Transaction (assign + categorize)
        unassignedTxToCategorize?.let { (tx, catId) ->
            val catName = categories.find { it.id == catId }?.name ?: "Selected Category"
            AlertDialog(
                onDismissRequest = { unassignedTxToCategorize = null },
                title = { Text("Assign Account & Categorize") },
                text = {
                    Column {
                        Text(
                            text = "Assign account to categorize as '$catName':",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(modifier = Modifier.height(Spacing.sm))
                        if (accounts.isEmpty()) {
                            Text(
                                text = "No active accounts found. Please add an account first.",
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall
                            )
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                                accounts.forEach { acc ->
                                    Card(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                viewModel.categorizeTransaction(tx.id, catId, tx.amountPaise, acc.id)
                                                unassignedTxToCategorize = null
                                            },
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(Spacing.sm),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(Icons.Default.AccountBalance, contentDescription = null, modifier = Modifier.size(20.dp))
                                            Spacer(modifier = Modifier.width(Spacing.xs))
                                            Text(acc.name, fontWeight = FontWeight.Medium)
                                        }
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = {},
                dismissButton = {
                    TextButton(onClick = { unassignedTxToCategorize = null }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PendingTransactionCard(
    tx: TransactionEntity,
    categories: List<CategoryEntity>,
    accounts: List<AccountEntity>,
    suggestedCategory: CategoryEntity? = null,
    onAssignAccount: (Long) -> Unit,
    onRequestAssignAccount: () -> Unit,
    onConfirm: () -> Unit,
    onCategorize: (Long) -> Unit,
    onVoid: () -> Unit
) {
    var expandedBucket by remember { mutableStateOf<BudgetBucket?>(null) }
    val assignedAccount = accounts.find { it.id == tx.accountId }
    val isIncome = tx.kind == com.tapledger.core.model.TransactionKind.INCOME ||
        tx.kind == com.tapledger.core.model.TransactionKind.INTEREST_EARNED ||
        tx.direction == com.tapledger.core.model.TransactionDirection.IN

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {
                contentDescription = "Pending transaction: ${tx.merchantRaw ?: "Transaction"} of ${tx.amountPaise / 100} rupees"
            },
        colors = CardDefaults.cardColors(containerColor = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.88f)),
        border = BorderStroke(1.dp, androidx.compose.ui.graphics.Color.White.copy(alpha = 0.95f)),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.md)
        ) {
            // Header Row: Merchant & Amount
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = tx.merchantRaw ?: if (isIncome) "Captured Income" else "Captured Expense",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold
                        )
                    )
                    Text(
                        text = "${DateTimeUtils.formatDateTime(tx.occurredAt)} • ${tx.source.name}" +
                                if (assignedAccount != null) " • ${assignedAccount.name}" else "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                MoneyText(
                    amountPaise = tx.amountPaise,
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    semantic = if (isIncome) MoneySemantic.Income else MoneySemantic.Expense
                )
            }

            // Flow.md §3.1: Amber Warning Banner for Unassigned Account
            if (tx.accountId == null) {
                Spacer(modifier = Modifier.height(Spacing.xs))
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onRequestAssignAccount() },
                    colors = CardDefaults.cardColors(containerColor = WarningAmber.copy(alpha = 0.15f)),
                    border = BorderStroke(1.dp, WarningAmber.copy(alpha = 0.8f)),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = Spacing.sm, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = WarningAmber,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(Spacing.xs))
                        Text(
                            text = "Unassigned Account — Tap to resolve",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                // Inline 1-tap Account Chips
                if (accounts.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Assign:",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        accounts.forEach { acc ->
                            FilterChip(
                                selected = false,
                                onClick = { onAssignAccount(acc.id) },
                                label = { Text(acc.name, fontSize = 11.sp) }
                            )
                        }
                    }
                }
            }

            if (isIncome) {
                Spacer(modifier = Modifier.height(Spacing.xs))
                if (tx.accountId != null) {
                    Button(
                        onClick = onConfirm,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = IncomeGreen,
                            contentColor = androidx.compose.ui.graphics.Color.White
                        )
                    ) {
                        Icon(Icons.Default.Done, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(Spacing.xs))
                        Text(
                            text = "1-Tap Confirm Income",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                } else {
                    Text(
                        text = "Assign an account above to confirm this income.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                val isLoanEmiDebit = tx.notes?.contains("Loan EMI") == true
                if (isLoanEmiDebit) {
                    Spacer(modifier = Modifier.height(Spacing.xs))
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f)),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.5f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = Spacing.sm, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.AccountBalance,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(Spacing.xs))
                            Text(
                                text = "Auto-Detected Loan EMI Debit (Suggested: Loan Transfer)",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                }

                // 1-Tap Suggested Category Apply Button (if suggestion exists)
                if (suggestedCategory != null) {
                    Spacer(modifier = Modifier.height(Spacing.xs))
                    Button(
                        onClick = { onCategorize(suggestedCategory.id) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    ) {
                        Icon(Icons.Default.Done, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(Spacing.xs))
                        Text(
                            text = "1-Tap Apply: ${suggestedCategory.name} (${suggestedCategory.defaultBucket.name})",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(Spacing.xs))

                // Bucket Chips (<= 2 taps categorization)
                Text(
                    text = "Assign Bucket:",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(2.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
                ) {
                    listOf(
                        BudgetBucket.NEEDS to "Needs",
                        BudgetBucket.WANTS to "Wants",
                        BudgetBucket.SAVINGS to "Savings"
                    ).forEach { (bucket, label) ->
                        val isSelected = expandedBucket == bucket
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                val bucketCats = categories.filter { it.defaultBucket == bucket }
                                if (bucketCats.size == 1) {
                                    // Exactly 1 category in this bucket: 1-tap categorization!
                                    onCategorize(bucketCats.first().id)
                                } else {
                                    // Toggle bucket subcategory selection
                                    expandedBucket = if (isSelected) null else bucket
                                }
                            },
                            label = { Text(label, fontSize = 12.sp) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                // Expanded Sub-categories under the selected bucket (2nd tap!)
                AnimatedVisibility(visible = expandedBucket != null) {
                    val bucketCats = categories.filter { it.defaultBucket == expandedBucket }
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = Spacing.xs)
                    ) {
                        Text(
                            text = "Select Category in ${expandedBucket?.name}:",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                            verticalArrangement = Arrangement.spacedBy(Spacing.xs)
                        ) {
                            bucketCats.forEach { cat ->
                                AssistChip(
                                    onClick = { onCategorize(cat.id) },
                                    label = { Text(cat.name, fontSize = 11.sp) }
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(Spacing.xs))

            // Ignore action
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                OutlinedButton(
                    onClick = onVoid,
                    contentPadding = PaddingValues(horizontal = Spacing.sm, vertical = 2.dp)
                ) {
                    Text("Ignore", fontSize = 11.sp)
                }
            }
        }
    }
}
