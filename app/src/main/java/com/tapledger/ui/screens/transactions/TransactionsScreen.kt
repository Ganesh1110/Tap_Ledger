package com.tapledger.ui.screens.transactions

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import com.tapledger.ui.components.isKeyboardVisible
import com.tapledger.ui.components.keyboardAware
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tapledger.core.database.entity.TransactionEntity
import com.tapledger.core.util.DateTimeUtils
import com.tapledger.core.model.TransactionKind
import com.tapledger.core.model.TransactionStatus
import com.tapledger.ui.components.EmptyState
import com.tapledger.ui.components.MoneySemantic
import com.tapledger.ui.components.MoneyText
import com.tapledger.ui.components.SkeletonList
import com.tapledger.ui.components.StatusBadge
import com.tapledger.ui.components.TransactionRow
import com.tapledger.ui.theme.Spacing
import kotlinx.coroutines.launch

import com.tapledger.ui.screens.transactions.components.SpendingHeatmapCard

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun TransactionsScreen(
    viewModel: TransactionsViewModel = hiltViewModel(),
    onNavigateToManualEntry: () -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val isExpandedFab by remember {
        derivedStateOf { listState.firstVisibleItemIndex == 0 }
    }

    var selectedTxForDetails by remember { mutableStateOf<TransactionEntity?>(null) }
    var isSearchVisible by remember { mutableStateOf(false) }

    // Group filtered transactions by date
    val groupedByDate = remember(state.filteredTransactions) {
        state.filteredTransactions.groupBy { tx ->
            DateTimeUtils.formatDate(tx.occurredAt)
        }
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = {
                    Text(
                        text = "Transactions",
                        fontWeight = FontWeight.Bold
                    )
                },
                actions = {
                    IconButton(onClick = { isSearchVisible = !isSearchVisible }) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search Transactions"
                        )
                    }
                },
                colors = TopAppBarDefaults.largeTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer
                ),
                scrollBehavior = scrollBehavior
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            if (!isKeyboardVisible()) {
                ExtendedFloatingActionButton(
                    text = { Text("Add Transaction") },
                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                    expanded = isExpandedFab,
                    onClick = onNavigateToManualEntry,
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .keyboardAware()
        ) {
            // Search Input & Filter Chips Header
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.md)
            ) {
                AnimatedVisibility(visible = isSearchVisible) {
                    OutlinedTextField(
                        value = state.searchQuery,
                        onValueChange = { viewModel.onSearchQueryChanged(it) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = Spacing.xs),
                        placeholder = { Text("Search merchant, note, or amount...") },
                        leadingIcon = {
                            Icon(Icons.Default.Search, contentDescription = null)
                        },
                        trailingIcon = {
                            if (state.searchQuery.isNotEmpty()) {
                                IconButton(onClick = { viewModel.onSearchQueryChanged("") }) {
                                    Icon(Icons.Default.Clear, contentDescription = "Clear")
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow
                        )
                    )
                }

                // Filter Chips Row (All, Pending, Categorized, Voided)
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                    contentPadding = PaddingValues(vertical = Spacing.xxs)
                ) {
                    item {
                        FilterChip(
                            selected = state.selectedStatus == null,
                            onClick = { viewModel.onStatusSelected(null) },
                            label = { Text("All (${state.transactions.size})") }
                        )
                    }
                    item {
                        FilterChip(
                            selected = state.selectedStatus == TransactionStatus.PENDING,
                            onClick = {
                                viewModel.onStatusSelected(
                                    if (state.selectedStatus == TransactionStatus.PENDING) null else TransactionStatus.PENDING
                                )
                            },
                            label = { Text("Pending") }
                        )
                    }
                    item {
                        FilterChip(
                            selected = state.selectedStatus == TransactionStatus.CATEGORIZED,
                            onClick = {
                                viewModel.onStatusSelected(
                                    if (state.selectedStatus == TransactionStatus.CATEGORIZED) null else TransactionStatus.CATEGORIZED
                                )
                            },
                            label = { Text("Categorized") }
                        )
                    }
                    item {
                        FilterChip(
                            selected = state.selectedStatus == TransactionStatus.VOIDED,
                            onClick = {
                                viewModel.onStatusSelected(
                                    if (state.selectedStatus == TransactionStatus.VOIDED) null else TransactionStatus.VOIDED
                                )
                            },
                            label = { Text("Voided") }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(Spacing.xs))

            if (state.isLoading) {
                Box(modifier = Modifier.padding(Spacing.md)) {
                    SkeletonList(itemCount = 5)
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = Spacing.md,
                        end = Spacing.md,
                        top = Spacing.xs,
                        bottom = Spacing.listBottomPadding
                    ),
                    verticalArrangement = Arrangement.spacedBy(Spacing.xs)
                ) {
                    item(key = "spending_heatmap") {
                        SpendingHeatmapCard(
                            heatmapState = state.heatmapState,
                            selectedDateEpoch = state.selectedDateFilterEpoch,
                            onSelectDay = viewModel::onSelectHeatmapDay,
                            onClearDateFilter = viewModel::clearDateFilter,
                            modifier = Modifier.padding(bottom = Spacing.xs)
                        )
                    }

                    if (state.filteredTransactions.isEmpty()) {
                        item(key = "empty_state") {
                            EmptyState(
                                title = if (state.searchQuery.isNotEmpty() || state.selectedStatus != null || state.selectedDateFilterEpoch != null) "No matching transactions" else "No transactions yet",
                                subtitle = if (state.searchQuery.isNotEmpty() || state.selectedDateFilterEpoch != null) "Try adjusting your search or date filter." else "Add your first transaction or wait for SMS notifications.",
                                icon = Icons.AutoMirrored.Filled.ReceiptLong,
                                actionLabel = if (state.searchQuery.isNotEmpty() || state.selectedStatus != null || state.selectedDateFilterEpoch != null) "Clear Filters" else "Add Transaction",
                                onActionClick = {
                                    if (state.searchQuery.isNotEmpty() || state.selectedStatus != null || state.selectedDateFilterEpoch != null) {
                                        viewModel.onSearchQueryChanged("")
                                        viewModel.onStatusSelected(null)
                                        viewModel.clearDateFilter()
                                    } else {
                                        onNavigateToManualEntry()
                                    }
                                }
                            )
                        }
                    } else {
                    for ((dateStr, txsOnDate) in groupedByDate) {
                        stickyHeader(key = "header_$dateStr") {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(MaterialTheme.colorScheme.background)
                                    .padding(vertical = Spacing.xxs)
                            ) {
                                Text(
                                    text = dateStr,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }

                        items(
                            items = txsOnDate,
                            key = { it.id }
                        ) { tx ->
                            val account = state.accountsMap[tx.accountId]
                            TransactionRow(
                                tx = tx,
                                accountName = account?.name,
                                onClick = { selectedTxForDetails = tx },
                                onVoid = {
                                    viewModel.voidTransaction(tx.id)
                                    coroutineScope.launch {
                                        val result = snackbarHostState.showSnackbar(
                                            message = "Transaction voided",
                                            actionLabel = "Undo",
                                            duration = SnackbarDuration.Short
                                        )
                                        if (result == SnackbarResult.ActionPerformed) {
                                            viewModel.restoreTransaction(tx.id)
                                        }
                                    }
                                },
                                onRestore = {
                                    viewModel.restoreTransaction(tx.id)
                                    coroutineScope.launch {
                                        snackbarHostState.showSnackbar(
                                            message = "Transaction restored",
                                            duration = SnackbarDuration.Short
                                        )
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

    // Transaction Details Bottom Sheet
    selectedTxForDetails?.let { tx ->
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        val account = state.accountsMap[tx.accountId]

        ModalBottomSheet(
            onDismissRequest = { selectedTxForDetails = null },
            sheetState = sheetState
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(Spacing.lg)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Transaction Details",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    StatusBadge(status = tx.status)
                }

                Spacer(modifier = Modifier.height(Spacing.md))

                MoneyText(
                    amountPaise = tx.amountPaise,
                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                    semantic = if (tx.kind == TransactionKind.INCOME || tx.kind == TransactionKind.REFUND) MoneySemantic.Income else MoneySemantic.Expense,
                    showSign = true
                )

                Spacer(modifier = Modifier.height(Spacing.md))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                Spacer(modifier = Modifier.height(Spacing.sm))

                DetailItem(label = "Merchant / Payee", value = tx.merchantRaw ?: "Not specified")
                DetailItem(label = "Date & Time", value = DateTimeUtils.formatDateTime(tx.occurredAt))
                DetailItem(label = "Account", value = account?.name ?: "Account #${tx.accountId}")
                DetailItem(label = "Type", value = tx.kind.name)
                DetailItem(label = "Source", value = tx.source.name)
                tx.upiReference?.let { DetailItem(label = "UPI Reference", value = it) }
                tx.notes?.let { DetailItem(label = "Note", value = it) }

                Spacer(modifier = Modifier.height(Spacing.lg))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = { selectedTxForDetails = null }) {
                        Text("Close")
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailItem(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold
        )
    }
}
