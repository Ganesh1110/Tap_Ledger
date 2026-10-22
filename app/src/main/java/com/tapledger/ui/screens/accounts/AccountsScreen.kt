package com.tapledger.ui.screens.accounts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.ui.Alignment
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.sp
import com.tapledger.core.database.entity.AccountEntity
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tapledger.core.model.AccountType
import com.tapledger.core.model.MoneyFormatter
import com.tapledger.core.util.DateTimeUtils
import com.tapledger.domain.model.AccountBalanceSummary
import com.tapledger.ui.components.AccountCard
import com.tapledger.ui.components.EmptyState
import com.tapledger.ui.components.SectionHeader
import com.tapledger.ui.components.SkeletonCard
import com.tapledger.ui.components.SkeletonList
import com.tapledger.ui.screens.accounts.components.AccountCompositionCard
import com.tapledger.ui.screens.accounts.components.NetWorthCard
import com.tapledger.ui.theme.ExpenseRed
import com.tapledger.ui.theme.IncomeGreen
import com.tapledger.ui.theme.Spacing
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch

/**
 * Accounts & Balances screen (S_Acct) per Flow.md §17.2 V9 & V10.
 * Hosts V9 Net worth line/area chart and V10 Account composition diverging bars
 * and credit-card utilization gauges, followed by asset & liability accounts lists.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountsScreen(
    viewModel: AccountsViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit = {},
    onNavigateToStatements: () -> Unit = {}
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    val listState = rememberLazyListState()
    val snackbarHostState = remember { SnackbarHostState() }
    var selectedSummary by remember { mutableStateOf<AccountBalanceSummary?>(null) }
    var showActionDialog by remember { mutableStateOf(false) }
    var showCorrectBalanceDialog by remember { mutableStateOf(false) }
    var showEditStartingDialog by remember { mutableStateOf(false) }
    var isStartingLocked by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(state.errorMessage) {
        state.errorMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearErrorMessage()
        }
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = {
                    Text(
                        text = "Accounts & Balances",
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
                actions = {},
                colors = TopAppBarDefaults.largeTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer
                ),
                scrollBehavior = scrollBehavior
            )
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
                SkeletonCard(height = 200.dp)
                SkeletonCard(height = 160.dp)
                SkeletonList(itemCount = 4)
            }
        } else if (state.accountSummaries.isEmpty()) {
            EmptyState(
                title = "No accounts configured",
                subtitle = "Your bank accounts, cash wallets, and credit cards will appear here.",
                icon = Icons.Default.AccountBalance,
                modifier = Modifier.padding(padding)
            )
        } else {
            val assets = state.accountSummaries.filter { it.account.type != AccountType.CREDIT_CARD && it.account.type != AccountType.LOAN }
            val liabilities = state.accountSummaries.filter { it.account.type == AccountType.CREDIT_CARD || it.account.type == AccountType.LOAN }

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = Spacing.md),
                contentPadding = PaddingValues(top = Spacing.xs, bottom = Spacing.listBottomPadding),
                verticalArrangement = Arrangement.spacedBy(Spacing.md)
            ) {
                // 1. V9 Net Worth Card (Line/Area with scrubbing, ranges 3M/6M/1Y/All, stacked toggle, table toggle)
                item(key = "net_worth_card") {
                    NetWorthCard(
                        chartState = state.netWorthChartState,
                        currentNetWorthPaise = state.netWorthPaise,
                        totalAssetsPaise = state.totalAssetsPaise,
                        totalLiabilitiesPaise = state.totalLiabilitiesPaise,
                        selectedRange = state.selectedRange,
                        isStacked = state.isStackedView,
                        showTable = state.showTable,
                        onSelectRange = { viewModel.setRange(it) },
                        onToggleStacked = { viewModel.toggleStackedView() },
                        onToggleShowTable = { viewModel.toggleShowTable() }
                    )
                }

                // 2. V10 Account Composition Card (Diverging bars & Credit Card utilization gauges)
                item(key = "account_composition_card") {
                    AccountCompositionCard(
                        compositionState = state.accountCompositionState
                    )
                }

                // 3. Assets Group
                if (assets.isNotEmpty()) {
                    item(key = "assets_header") {
                        SectionHeader(
                            title = "Assets (${assets.size})",
                            subtitle = "Bank accounts and cash wallets"
                        )
                    }

                    items(
                        items = assets,
                        key = { "asset_${it.account.id}" }
                    ) { summary ->
                        Column {
                            AccountCard(
                                summary = summary,
                                onClick = {
                                    selectedSummary = summary
                                    coroutineScope.launch {
                                        isStartingLocked = viewModel.isOpeningBalanceLocked(summary.account)
                                    }
                                    showActionDialog = true
                                }
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Tap for options • Correct balance or edit opening balance",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                modifier = Modifier.padding(start = Spacing.sm)
                            )
                        }
                    }
                }

                // 4. Liabilities Group
                if (liabilities.isNotEmpty()) {
                    item(key = "liabilities_header") {
                        SectionHeader(
                            title = "Liabilities (${liabilities.size})",
                            subtitle = "Credit cards & outstanding dues"
                        )
                    }

                    items(
                        items = liabilities,
                        key = { "liability_${it.account.id}" }
                    ) { summary ->
                        Column {
                            AccountCard(
                                summary = summary,
                                onClick = {
                                    selectedSummary = summary
                                    coroutineScope.launch {
                                        isStartingLocked = viewModel.isOpeningBalanceLocked(summary.account)
                                    }
                                    showActionDialog = true
                                }
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Tap for options • Correct balance or edit opening balance",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                modifier = Modifier.padding(start = Spacing.sm)
                            )
                        }
                    }
                }
            }
        }
    }

    // Action Selection Dialog
    if (showActionDialog && selectedSummary != null) {
        val summary = selectedSummary!!
        AlertDialog(
            onDismissRequest = { showActionDialog = false },
            title = { Text(summary.account.name) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "Current App Balance: ${MoneyFormatter.formatPaiseToRupees(summary.currentBalancePaise)}",
                        fontWeight = FontWeight.SemiBold
                    )
                    OutlinedButton(
                        onClick = {
                            showActionDialog = false
                            showCorrectBalanceDialog = true
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Align / Correct Balance", fontWeight = FontWeight.Bold)
                            Text("Quickly match app with your actual bank balance (Recommended)", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    OutlinedButton(
                        onClick = {
                            showActionDialog = false
                            showEditStartingDialog = true
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Edit Starting Balance")
                            Text(
                                if (isStartingLocked) "Locked (period is closed)" else "Rewrites historical starting balance",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showActionDialog = false }) { Text("Close") }
            }
        )
    }

    // Correct Balance Dialog (§6.4)
    if (showCorrectBalanceDialog && selectedSummary != null) {
        val summary = selectedSummary!!
        val isCredit = summary.account.type == AccountType.CREDIT_CARD || summary.account.type == AccountType.LOAN
        var targetInput by remember(summary) {
            val initial = if (isCredit) {
                if (summary.currentBalancePaise < 0L) (-summary.currentBalancePaise / 100).toString() else "0"
            } else {
                if (summary.currentBalancePaise > 0L) (summary.currentBalancePaise / 100).toString() else "0"
            }
            mutableStateOf(initial)
        }

        val targetPaise = try {
            val parsed = MoneyFormatter.parseOpeningBalance(targetInput)
            if (isCredit) {
                if (parsed == 0L) 0L else -parsed
            } else parsed
        } catch (_: Exception) {
            summary.currentBalancePaise
        }
        val deltaPaise = targetPaise - summary.currentBalancePaise

        AlertDialog(
            onDismissRequest = { showCorrectBalanceDialog = false },
            title = { Text("Align ${summary.account.name} Balance") },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Current App Balance: ${MoneyFormatter.formatPaiseToRupees(summary.currentBalancePaise)}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    OutlinedTextField(
                        value = targetInput,
                        onValueChange = { targetInput = it },
                        label = { Text(if (isCredit) "Actual Amount Owed Right Now (₹)" else "Actual Balance Right Now (₹)") },
                        prefix = { Text("₹ ") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (deltaPaise == 0L) {
                        Text(
                            "Already matches current balance. Zero adjustments needed.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else if (deltaPaise > 0L) {
                        Text(
                            "+${MoneyFormatter.formatPaiseToRupees(deltaPaise)} adjustment (Inflow)",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = IncomeGreen
                        )
                    } else {
                        Text(
                            "-${MoneyFormatter.formatPaiseToRupees(-deltaPaise)} adjustment (Outflow)",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = ExpenseRed
                        )
                    }
                    Text(
                        text = "Creates 1 CONFIRMED ADJUSTMENT transaction. Does not affect budget spending or envelope categories.",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.correctAccountBalance(summary.account, targetPaise)
                        showCorrectBalanceDialog = false
                    },
                    enabled = deltaPaise != 0L
                ) {
                    Text("Apply Alignment")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCorrectBalanceDialog = false }) { Text("Cancel") }
            }
        )
    }

    // Edit Starting Balance Dialog (§6.1 rule 5 & §6.4)
    if (showEditStartingDialog && selectedSummary != null) {
        val summary = selectedSummary!!
        val acc = summary.account
        var openingRupees by remember(acc) {
            mutableStateOf(
                if (acc.openingBalancePaise != 0L) {
                    val whole = acc.openingBalancePaise / 100
                    val remainder = kotlin.math.abs(acc.openingBalancePaise % 100)
                    if (remainder == 0L) "$whole" else "%d.%02d".format(whole, remainder)
                } else ""
            )
        }

        AlertDialog(
            onDismissRequest = { showEditStartingDialog = false },
            title = { Text("Edit ${acc.name} Starting Balance") },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (isStartingLocked) {
                        Text(
                            text = "Opening balance is locked because this budget period is closed (§6.1 rule 5). To adjust today's balance, use Correct Balance.",
                            style = MaterialTheme.typography.bodySmall,
                            color = ExpenseRed
                        )
                    } else {
                        Text(
                            text = "Warning: This rewrites history and your net worth trend; to fix today's mismatch use Correct Balance.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.error
                        )
                        Text(
                            text = "Starting balance date: ${DateTimeUtils.formatDate(acc.openingBalanceDate)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        OutlinedTextField(
                            value = openingRupees,
                            onValueChange = { openingRupees = it },
                            label = { Text("Starting Balance (₹)") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Decimal,
                                imeAction = ImeAction.Done
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            },
            confirmButton = {
                if (!isStartingLocked) {
                    Button(
                        onClick = {
                            viewModel.updateAccountOpeningBalance(acc, openingRupees)
                            showEditStartingDialog = false
                        }
                    ) {
                        Text("Save Balance")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditStartingDialog = false }) { Text("Cancel") }
            }
        )
    }
}
