package com.tapledger.ui.screens.accounts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import com.tapledger.ui.components.AccountCard
import com.tapledger.ui.components.EmptyState
import com.tapledger.ui.components.SectionHeader
import com.tapledger.ui.components.SkeletonCard
import com.tapledger.ui.components.SkeletonList
import com.tapledger.ui.screens.accounts.components.AccountCompositionCard
import com.tapledger.ui.screens.accounts.components.NetWorthCard
import com.tapledger.ui.theme.Spacing

/**
 * Accounts & Balances screen (S_Acct) per Flow.md §17.2 V9 & V10.
 * Hosts V9 Net worth line/area chart and V10 Account composition diverging bars
 * and credit-card utilization gauges, followed by asset & liability accounts lists.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountsScreen(
    viewModel: AccountsViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit = {}
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    val listState = rememberLazyListState()
    val snackbarHostState = remember { SnackbarHostState() }
    var editingAccount by remember { mutableStateOf<AccountEntity?>(null) }

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
                            AccountCard(summary = summary, onClick = { editingAccount = summary.account })
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Last reconciled with opening balance • Tap to edit starting balance",
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
                            AccountCard(summary = summary, onClick = { editingAccount = summary.account })
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Reconciled against credit limit & statement cycle • Tap to edit starting balance",
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

    editingAccount?.let { acc ->
        var openingRupees by remember {
            mutableStateOf(
                if (acc.openingBalancePaise != 0L) {
                    val whole = acc.openingBalancePaise / 100
                    val remainder = kotlin.math.abs(acc.openingBalancePaise % 100)
                    if (remainder == 0L) "$whole" else "%d.%02d".format(whole, remainder)
                } else ""
            )
        }

        AlertDialog(
            onDismissRequest = { editingAccount = null },
            title = { Text("Edit ${acc.name} Starting Balance") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Updating the starting balance recalculates your real-time account balance and total Net Worth.",
                        fontSize = 12.sp,
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
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.updateAccountOpeningBalance(acc, openingRupees)
                        editingAccount = null
                    }
                ) {
                    Text("Save Balance")
                }
            },
            dismissButton = {
                TextButton(onClick = { editingAccount = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}
