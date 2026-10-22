package com.tapledger.ui.screens.categories

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tapledger.core.database.entity.CategoryEntity
import com.tapledger.core.model.AdvancePolicy
import com.tapledger.core.model.BudgetBucket
import com.tapledger.core.model.MoneyFormatter
import com.tapledger.domain.calculator.BudgetCalculator
import com.tapledger.ui.components.ConfirmDialog
import com.tapledger.ui.components.EmptyState
import com.tapledger.ui.components.MoneySemantic
import com.tapledger.ui.components.MoneyText
import com.tapledger.ui.components.SectionHeader
import com.tapledger.ui.components.SkeletonList
import com.tapledger.ui.charts.model.ChartState
import com.tapledger.ui.screens.categories.components.CategoryDonutCard
import com.tapledger.ui.screens.categories.components.EnvelopeBulletBarRow
import com.tapledger.ui.charts.render.ChartPatternInfoButton
import com.tapledger.ui.charts.render.ChartPatternLegendSheet
import com.tapledger.ui.charts.render.ChartPatternType
import com.tapledger.ui.charts.util.ChartSummaryHelper
import com.tapledger.ui.theme.ExpenseRed
import com.tapledger.ui.theme.IncomeGreen
import com.tapledger.ui.theme.Spacing
import com.tapledger.ui.theme.WarningAmber
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryManagementScreen(
    viewModel: CategoryManagementViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val isExpandedFab by remember {
        derivedStateOf { listState.firstVisibleItemIndex == 0 }
    }

    var showAddDialog by remember { mutableStateOf(false) }
    var showTransferDialog by remember { mutableStateOf(false) }
    var showPatternSheet by remember { mutableStateOf(false) }
    var editingCategoryItem by remember { mutableStateOf<CategoryItemState?>(null) }
    var categoryToDelete by remember { mutableStateOf<CategoryEntity?>(null) }
    var bucketToRebalance by remember { mutableStateOf<BudgetBucket?>(null) }

    LaunchedEffect(state.errorMessage) {
        state.errorMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearErrorMessage()
        }
    }

    LaunchedEffect(state.infoMessage) {
        state.infoMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearInfoMessage()
        }
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = {
                    Text(
                        text = "Categories & Budget",
                        fontWeight = FontWeight.Bold
                    )
                },
                actions = {
                    IconButton(onClick = { showTransferDialog = true }) {
                        Icon(
                            imageVector = Icons.Default.SwapHoriz,
                            contentDescription = "Transfer Budget"
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
            ExtendedFloatingActionButton(
                text = { Text("New Category") },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                expanded = isExpandedFab,
                onClick = { showAddDialog = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            )
        }
    ) { padding ->
        if (state.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(Spacing.md)
            ) {
                SkeletonList(itemCount = 6)
            }
        } else if (state.categories.isEmpty()) {
            EmptyState(
                title = "No categories yet",
                subtitle = "Set up your 50/30/20 budget envelopes by creating your first category.",
                icon = Icons.Default.PieChart,
                actionLabel = "New Category",
                onActionClick = { showAddDialog = true },
                modifier = Modifier.padding(padding)
            )
        } else {
            val categoriesByBucket = state.categories.groupBy { it.category.defaultBucket }
            val bulletItemsMap = (state.bulletBarsState as? ChartState.Data)?.data?.envelopes?.associateBy { it.categoryId } ?: emptyMap()
            val bucketTargets = remember(state.plannedIncomePaise) {
                BudgetCalculator.calculate503020Targets(state.plannedIncomePaise)
            }

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = Spacing.md),
                contentPadding = PaddingValues(top = Spacing.xs, bottom = Spacing.listBottomPadding),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                // View Mode Toggle (Flow.md §17.2 V6 S_Budget toggle)
                item(key = "budget_view_mode_toggle") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = Spacing.xs),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
                    ) {
                        FilterChip(
                            selected = state.viewMode == BudgetViewMode.ENVELOPES,
                            onClick = { viewModel.setViewMode(BudgetViewMode.ENVELOPES) },
                            label = { Text("Envelopes") },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ReceiptLong,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        )
                        FilterChip(
                            selected = state.viewMode == BudgetViewMode.DONUT,
                            onClick = { viewModel.setViewMode(BudgetViewMode.DONUT) },
                            label = { Text("Spending Donut") },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.PieChart,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        )
                    }
                }

                if (state.viewMode == BudgetViewMode.DONUT) {
                    item(key = "category_donut_card") {
                        CategoryDonutCard(
                            donutState = state.categoryDonutState,
                            selectedSlice = state.selectedDonutSlice,
                            onSelectSlice = { viewModel.selectDonutSlice(it) }
                        )
                    }
                } else {
                    item(key = "bullet_summary_bar") {
                        val bulletBars = state.bulletBarsState
                        val bulletSummary = remember(bulletBars) {
                            if (bulletBars is ChartState.Data) {
                                ChartSummaryHelper.summarizeV5BulletBars(bulletBars.data)
                            } else null
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = Spacing.md, vertical = Spacing.xs),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = bulletSummary ?: "Category envelopes vs monthly pace",
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.weight(1f)
                            )
                            ChartPatternInfoButton(onClick = { showPatternSheet = true })
                        }
                    }

                    val targets = bucketTargets

                    listOf(BudgetBucket.NEEDS, BudgetBucket.WANTS, BudgetBucket.SAVINGS).forEach { bucket ->
                        val bucketItems = categoriesByBucket[bucket] ?: emptyList()
                        if (bucketItems.isNotEmpty()) {
                            val totalAllocated = bucketItems.sumOf { it.currentAllocationPaise }
                            val totalSpent = bucketItems.sumOf { it.spentPaise }
                            val totalRemaining = totalAllocated - totalSpent
                            val targetPaise = when (bucket) {
                                BudgetBucket.NEEDS -> targets.needsPaise
                                BudgetBucket.WANTS -> targets.wantsPaise
                                BudgetBucket.SAVINGS -> targets.savingsPaise
                            }

                            item(key = "header_${bucket.name}") {
                                BucketSectionHeader(
                                    bucket = bucket,
                                    totalAllocated = totalAllocated,
                                    totalSpent = totalSpent,
                                    totalRemaining = totalRemaining,
                                    targetPaise = targetPaise,
                                    onRebalance = if (state.plannedIncomePaise > 0L) {
                                        { bucketToRebalance = bucket }
                                    } else null
                                )
                            }

                            items(
                                items = bucketItems,
                                key = { it.category.id }
                            ) { itemState ->
                                val bulletItem = bulletItemsMap[itemState.category.id]
                                if (bulletItem != null) {
                                    EnvelopeBulletBarRow(
                                        item = bulletItem,
                                        isPinned = itemState.category.isPinned,
                                        onTogglePin = { viewModel.togglePinCategory(itemState.category.id) },
                                        onClick = { editingCategoryItem = itemState }
                                    )
                                } else {
                                    CategoryManageRow(
                                        itemState = itemState,
                                        onTogglePin = { viewModel.togglePinCategory(itemState.category.id) },
                                        onEdit = { editingCategoryItem = itemState }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showPatternSheet) {
        ChartPatternLegendSheet(
            patterns = listOf(ChartPatternType.STRIPED_ADVANCE, ChartPatternType.SHADED_DEFICIT),
            onDismissRequest = { showPatternSheet = false }
        )
    }

    // Add Category Dialog
    if (showAddDialog) {
        AddEditCategoryDialog(
            title = "New Budget Category",
            initialName = "",
            initialBucket = BudgetBucket.NEEDS,
            initialAdvancePolicy = AdvancePolicy.CARRY_TO_ADVANCE,
            initialBudgetRupees = "",
            initialIsPinned = false,
            plannedIncomePaise = state.plannedIncomePaise,
            existingCategories = state.categories,
            onDismiss = { showAddDialog = false },
            onSave = { name, bucket, advancePolicy, budgetRupees, autoAdjust, isPinned ->
                viewModel.addCategory(name, bucket, advancePolicy, budgetRupees, autoAdjust, isPinned)
                showAddDialog = false
            }
        )
    }

    // Edit Allocation Dialog
    editingCategoryItem?.let { itemState ->
        AddEditCategoryDialog(
            title = "Edit ${itemState.category.name}",
            initialName = itemState.category.name,
            initialBucket = itemState.category.defaultBucket,
            initialAdvancePolicy = itemState.category.defaultAdvancePolicy,
            initialBudgetRupees = (itemState.currentAllocationPaise / 100).toString(),
            initialIsPinned = itemState.category.isPinned,
            editingCategoryId = itemState.category.id,
            plannedIncomePaise = state.plannedIncomePaise,
            existingCategories = state.categories,
            onDismiss = { editingCategoryItem = null },
            onDelete = if (itemState.category.systemKey == null) {
                {
                    categoryToDelete = itemState.category
                    editingCategoryItem = null
                }
            } else null,
            onSave = { name, bucket, advancePolicy, budgetRupees, autoAdjust, isPinned ->
                viewModel.updateCategory(
                    categoryId = itemState.category.id,
                    name = name,
                    bucket = bucket,
                    advancePolicy = advancePolicy,
                    newBudgetRupees = budgetRupees,
                    autoAdjustOthers = autoAdjust,
                    isPinned = isPinned
                )
                editingCategoryItem = null
            }
        )
    }

    categoryToDelete?.let { cat ->
        ConfirmDialog(
            title = "Delete ${cat.name}?",
            text = "Are you sure you want to remove '${cat.name}' from your active budget envelopes? Historical transaction logs will be preserved.",
            confirmText = "Delete Category",
            isDestructive = true,
            onConfirm = {
                viewModel.deactivateCategory(cat.id)
                categoryToDelete = null
            },
            onDismiss = { categoryToDelete = null }
        )
    }

    // Rebalance Confirmation Dialog
    bucketToRebalance?.let { bucket ->
        val targets = BudgetCalculator.calculate503020Targets(state.plannedIncomePaise)
        val (percent, targetPaise) = when (bucket) {
            BudgetBucket.NEEDS -> "50%" to targets.needsPaise
            BudgetBucket.WANTS -> "30%" to targets.wantsPaise
            BudgetBucket.SAVINGS -> "20%" to targets.savingsPaise
        }
        val formattedTarget = MoneyFormatter.formatRupees(targetPaise, includeSymbol = true)
        val bucketItems = state.categories.filter { it.category.defaultBucket == bucket }
        val pinnedCount = bucketItems.count { it.category.isPinned }
        val flexibleCount = bucketItems.count { !it.category.isPinned }

        val detailsText = buildString {
            append("This will rebalance the ${bucket.name.lowercase()} bucket to exactly match your $percent target ($formattedTarget).\n\n")
            if (pinnedCount > 0) {
                append("• $pinnedCount fixed/pinned bill${if (pinnedCount > 1) "s" else ""} will remain strictly unchanged.\n")
            }
            append("• $flexibleCount flexible categor${if (flexibleCount == 1) "y" else "ies"} will be adjusted proportionally.")
        }

        ConfirmDialog(
            title = "Align ${bucket.name} to $percent ($formattedTarget)?",
            text = detailsText,
            confirmText = "Align to $percent",
            isDestructive = false,
            onConfirm = {
                viewModel.rebalanceBucket(bucket)
                bucketToRebalance = null
            },
            onDismiss = { bucketToRebalance = null }
        )
    }

    // Transfer Budget Dialog
    if (showTransferDialog) {
        val activeCategories = state.categories.map { it.category }
        TransferBudgetDialog(
            categories = activeCategories,
            onDismiss = { showTransferDialog = false },
            onTransfer = { fromId, toId, amountRupees, reason ->
                viewModel.transferBudget(fromId, toId, amountRupees, reason)
                showTransferDialog = false
            }
        )
    }
}

@Composable
private fun BucketSectionHeader(
    bucket: BudgetBucket,
    totalAllocated: Long,
    totalSpent: Long,
    totalRemaining: Long,
    targetPaise: Long = 0L,
    onRebalance: (() -> Unit)? = null
) {
    val (bucketPercent, bucketTitle) = when (bucket) {
        BudgetBucket.NEEDS -> "50%" to "Needs (50%)"
        BudgetBucket.WANTS -> "30%" to "Wants (30%)"
        BudgetBucket.SAVINGS -> "20%" to "Savings (20%)"
    }

    val isBalanced = targetPaise > 0L && totalAllocated == targetPaise

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = Spacing.sm, bottom = Spacing.xxs)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
            ) {
                Text(
                    text = bucketTitle,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                if (targetPaise > 0L && onRebalance != null) {
                    if (isBalanced) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "✓ Balanced",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(MaterialTheme.colorScheme.secondaryContainer)
                                .clickable(onClick = onRebalance)
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = "⚖️ Align to $bucketPercent",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Spent ",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                MoneyText(
                    amountPaise = totalSpent,
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                    semantic = MoneySemantic.Expense
                )
                Text(
                    text = " / ",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                MoneyText(
                    amountPaise = totalAllocated,
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                    semantic = MoneySemantic.Neutral
                )
            }
        }
    }
}

@Composable
private fun CategoryManageRow(
    itemState: CategoryItemState,
    onEdit: () -> Unit,
    onTogglePin: (() -> Unit)? = null
) {
    val totalBudget = itemState.currentAllocationPaise
    val spent = itemState.spentPaise
    val remaining = itemState.remainingPaise

    val percentageBp = com.tapledger.ui.charts.util.ChartMath.computeBasisPoints(spent, totalBudget)

    // Default policies per bucket
    val bucketDefaultPolicy = when (itemState.category.defaultBucket) {
        BudgetBucket.NEEDS -> AdvancePolicy.CARRY_TO_ADVANCE
        BudgetBucket.WANTS -> AdvancePolicy.EXPIRE
        BudgetBucket.SAVINGS -> AdvancePolicy.SWEEP_TO_SAVINGS
    }
    val hasCustomPolicy = itemState.category.defaultAdvancePolicy != bucketDefaultPolicy

    val isOverBudget = spent > totalBudget && totalBudget > 0L
    val progressColor = when {
        isOverBudget -> ExpenseRed
        percentageBp >= 8000 -> WarningAmber
        else -> IncomeGreen
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onEdit)
            .semantics(mergeDescendants = true) {
                contentDescription = "${itemState.category.name}: allocated ${totalBudget / 100} rupees, spent ${spent / 100} rupees, remaining ${remaining / 100} rupees"
            },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.md, vertical = Spacing.sm)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
                ) {
                    Text(
                        text = itemState.category.name,
                        style = MaterialTheme.typography.titleMedium.copy(fontSize = 15.sp),
                        fontWeight = FontWeight.SemiBold
                    )

                    if (itemState.category.isPinned) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(MaterialTheme.colorScheme.primaryContainer)
                                .padding(horizontal = 5.dp, vertical = 1.dp)
                        ) {
                            Text(
                                text = "📌 Fixed",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }

                    // Show Policy badge ONLY when it differs from bucket default
                    if (hasCustomPolicy) {
                        val policyLabel = when (itemState.category.defaultAdvancePolicy) {
                            AdvancePolicy.CARRY_TO_ADVANCE -> "Carry"
                            AdvancePolicy.SWEEP_TO_SAVINGS -> "Sweep"
                            AdvancePolicy.EXPIRE -> "Expire"
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "Policy: $policyLabel",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (onTogglePin != null) {
                        IconButton(
                            onClick = onTogglePin,
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = if (itemState.category.isPinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                                contentDescription = if (itemState.category.isPinned) "Unpin category" else "Pin category as fixed bill",
                                tint = if (itemState.category.isPinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                    }
                    MoneyText(
                        amountPaise = totalBudget,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        semantic = MoneySemantic.Neutral
                    )
                }
            }

            Spacer(modifier = Modifier.height(Spacing.xs))

            // Single continuous progress track
            com.tapledger.ui.charts.render.ProgressBarRender(
                percentageBp = percentageBp,
                color = progressColor,
                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
            )

            Spacer(modifier = Modifier.height(Spacing.xs))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Spent: ",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    MoneyText(
                        amountPaise = spent,
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                        semantic = MoneySemantic.Expense
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = if (remaining >= 0) "Left: " else "Exceeded: ",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    MoneyText(
                        amountPaise = kotlin.math.abs(remaining),
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                        semantic = if (remaining >= 0) MoneySemantic.Income else MoneySemantic.Expense
                    )
                }
            }
        }
    }
}

@Composable
fun AddEditCategoryDialog(
    title: String,
    initialName: String,
    initialBucket: BudgetBucket,
    initialAdvancePolicy: AdvancePolicy,
    initialBudgetRupees: String,
    initialIsPinned: Boolean = false,
    editingCategoryId: Long? = null,
    plannedIncomePaise: Long = 0L,
    existingCategories: List<CategoryItemState> = emptyList(),
    onDismiss: () -> Unit,
    onDelete: (() -> Unit)? = null,
    onSave: (String, BudgetBucket, AdvancePolicy, String, Boolean, Boolean) -> Unit
) {
    var name by remember { mutableStateOf(initialName) }
    var selectedBucket by remember { mutableStateOf(initialBucket) }
    var selectedAdvancePolicy by remember { mutableStateOf(initialAdvancePolicy) }
    var budgetRupees by remember { mutableStateOf(initialBudgetRupees) }
    var autoAdjustOthers by remember { mutableStateOf(false) }
    var isPinned by remember { mutableStateOf(initialIsPinned) }

    val otherCategoriesCount = remember(selectedBucket, existingCategories, editingCategoryId) {
        existingCategories.count {
            it.category.defaultBucket == selectedBucket && it.category.id != (editingCategoryId ?: -1L)
        }
    }

    val targets = remember(plannedIncomePaise) {
        BudgetCalculator.calculate503020Targets(plannedIncomePaise)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = title,
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
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Category Name") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Text,
                        imeAction = ImeAction.Next
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = budgetRupees,
                    onValueChange = { budgetRupees = it },
                    label = { Text("Monthly Budget (₹)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Decimal,
                        imeAction = ImeAction.Done
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Text("Bucket Tier:", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    listOf(BudgetBucket.NEEDS, BudgetBucket.WANTS, BudgetBucket.SAVINGS).forEach { bucket ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedBucket = bucket }
                                .padding(vertical = 2.dp)
                        ) {
                            RadioButton(
                                selected = selectedBucket == bucket,
                                onClick = { selectedBucket = bucket }
                            )
                            Text(
                                text = when (bucket) {
                                    BudgetBucket.NEEDS -> "Needs (Essential Living - 50%)"
                                    BudgetBucket.WANTS -> "Wants (Discretionary - 30%)"
                                    BudgetBucket.SAVINGS -> "Savings (Investments & Goals - 20%)"
                                    else -> bucket.name
                                },
                                fontSize = 13.sp
                            )
                        }
                    }
                }

                if (otherCategoriesCount > 0 && plannedIncomePaise > 0L) {
                    val (bucketPercent, bucketTargetPaise) = when (selectedBucket) {
                        BudgetBucket.NEEDS -> "50%" to targets.needsPaise
                        BudgetBucket.WANTS -> "30%" to targets.wantsPaise
                        BudgetBucket.SAVINGS -> "20%" to targets.savingsPaise
                    }
                    val formattedTarget = MoneyFormatter.formatRupees(bucketTargetPaise, includeSymbol = true)

                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { autoAdjustOthers = !autoAdjustOthers }
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Fit within $bucketPercent limit ($formattedTarget)",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Automatically adjust other $otherCategoriesCount categories in $bucketPercent proportionally",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Switch(
                                checked = autoAdjustOthers,
                                onCheckedChange = { autoAdjustOthers = it }
                            )
                        }
                    }
                }

                // 📌 Pin / Lock Fixed Category
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = if (isPinned) {
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        }
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { isPinned = !isPinned }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "📌 Fixed / Pinned Bill",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Never auto-reduce this allocation (e.g., Rent, EMI) during bucket rebalancing",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Switch(
                            checked = isPinned,
                            onCheckedChange = { isPinned = it }
                        )
                    }
                }

                Text("Advance Carry Rule:", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { selectedAdvancePolicy = AdvancePolicy.CARRY_TO_ADVANCE }
                    ) {
                        RadioButton(
                            selected = selectedAdvancePolicy == AdvancePolicy.CARRY_TO_ADVANCE,
                            onClick = { selectedAdvancePolicy = AdvancePolicy.CARRY_TO_ADVANCE }
                        )
                        Text("Carry Overspend to Advance Ledger", fontSize = 12.sp)
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { selectedAdvancePolicy = AdvancePolicy.SWEEP_TO_SAVINGS }
                    ) {
                        RadioButton(
                            selected = selectedAdvancePolicy == AdvancePolicy.SWEEP_TO_SAVINGS,
                            onClick = { selectedAdvancePolicy = AdvancePolicy.SWEEP_TO_SAVINGS }
                        )
                        Text("Sweep Balance to Savings", fontSize = 12.sp)
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { selectedAdvancePolicy = AdvancePolicy.EXPIRE }
                    ) {
                        RadioButton(
                            selected = selectedAdvancePolicy == AdvancePolicy.EXPIRE,
                            onClick = { selectedAdvancePolicy = AdvancePolicy.EXPIRE }
                        )
                        Text("Expire Balance at Month End", fontSize = 12.sp)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(name, selectedBucket, selectedAdvancePolicy, budgetRupees, autoAdjustOthers, isPinned) }
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
            ) {
                if (onDelete != null) {
                    TextButton(
                        onClick = onDelete,
                        colors = ButtonDefaults.textButtonColors(contentColor = ExpenseRed)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete Category",
                            tint = ExpenseRed,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Delete", color = ExpenseRed)
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
            }
        }
    )
}

@Composable
fun TransferBudgetDialog(
    categories: List<CategoryEntity>,
    onDismiss: () -> Unit,
    onTransfer: (Long, Long, String, String?) -> Unit
) {
    var fromCategory by remember { mutableStateOf<CategoryEntity?>(categories.firstOrNull()) }
    var toCategory by remember { mutableStateOf<CategoryEntity?>(categories.getOrNull(1) ?: categories.firstOrNull()) }
    var amountRupees by remember { mutableStateOf("") }
    var reason by remember { mutableStateOf("") }

    var showFromMenu by remember { mutableStateOf(false) }
    var showToMenu by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Transfer Intra-Month Budget",
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
                Text("From Category:", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                Box {
                    OutlinedButton(
                        onClick = { showFromMenu = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(fromCategory?.name ?: "Select Category")
                    }
                    DropdownMenu(
                        expanded = showFromMenu,
                        onDismissRequest = { showFromMenu = false }
                    ) {
                        categories.forEach { cat ->
                            DropdownMenuItem(
                                text = { Text(cat.name) },
                                onClick = {
                                    fromCategory = cat
                                    showFromMenu = false
                                }
                            )
                        }
                    }
                }

                Text("To Category:", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                Box {
                    OutlinedButton(
                        onClick = { showToMenu = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(toCategory?.name ?: "Select Category")
                    }
                    DropdownMenu(
                        expanded = showToMenu,
                        onDismissRequest = { showToMenu = false }
                    ) {
                        categories.forEach { cat ->
                            DropdownMenuItem(
                                text = { Text(cat.name) },
                                onClick = {
                                    toCategory = cat
                                    showToMenu = false
                                }
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = amountRupees,
                    onValueChange = { amountRupees = it },
                    label = { Text("Amount (₹)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Decimal,
                        imeAction = ImeAction.Next
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it },
                    label = { Text("Reason / Note (Optional)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Text,
                        imeAction = ImeAction.Done
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val fromId = fromCategory?.id ?: return@Button
                    val toId = toCategory?.id ?: return@Button
                    onTransfer(fromId, toId, amountRupees, reason)
                }
            ) {
                Text("Transfer")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
