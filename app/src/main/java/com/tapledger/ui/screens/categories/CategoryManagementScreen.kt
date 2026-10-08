package com.tapledger.ui.screens.categories

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.tapledger.core.database.entity.CategoryEntity
import com.tapledger.core.model.AdvancePolicy
import com.tapledger.core.model.BudgetBucket
import com.tapledger.core.model.MoneyFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryManagementScreen(
    viewModel: CategoryManagementViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    var showAddDialog by remember { mutableStateOf(false) }
    var editingCategory by remember { mutableStateOf<CategoryItemState?>(null) }
    var showTransferDialog by remember { mutableStateOf(false) }

    LaunchedEffect(state.errorMessage) {
        state.errorMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearErrorMessage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Categories & Budget") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showAddDialog = true }) {
                        Icon(Icons.Default.Add, contentDescription = "Add Category")
                    }
                    IconButton(onClick = { showTransferDialog = true }) {
                        Icon(Icons.Default.SwapHoriz, contentDescription = "Transfer Budget")
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
            val categoriesByBucket = state.categories.groupBy { it.category.defaultBucket }

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp),
                contentPadding = PaddingValues(vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Monthly Budget Allocations",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp
                                )
                                Text(
                                    text = "Manage bucket categories, default advance rules, and intra-month budget transfers.",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            OutlinedButton(
                                onClick = { showTransferDialog = true },
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.SwapHoriz, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Transfer", fontSize = 12.sp)
                            }
                        }
                    }
                }

                BudgetBucket.entries.forEach { bucket ->
                    val bucketItems = categoriesByBucket[bucket].orEmpty()
                    item {
                        Text(
                            text = bucket.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    if (bucketItems.isEmpty()) {
                        item {
                            Text(
                                text = "No categories in ${bucket.name}",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        items(bucketItems, key = { it.category.id }) { itemState ->
                            CategoryCard(
                                itemState = itemState,
                                onEdit = { editingCategory = itemState },
                                onDelete = { viewModel.deactivateCategory(itemState.category.id) }
                            )
                        }
                    }
                }

                if (state.adjustments.isNotEmpty()) {
                    item {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Recent Budget Reallocations",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    items(state.adjustments, key = { it.id }) { adj ->
                        val fromName = state.categories.find { it.category.id == adj.fromCategoryId }?.category?.name ?: "Category ${adj.fromCategoryId}"
                        val toName = state.categories.find { it.category.id == adj.toCategoryId }?.category?.name ?: "Category ${adj.toCategoryId}"

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Row(
                                modifier = Modifier
                                    .padding(12.dp)
                                    .fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "$fromName → $toName",
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 14.sp
                                    )
                                    adj.reason?.let { r ->
                                        if (r.isNotBlank()) {
                                            Text(
                                                text = "Reason: $r",
                                                fontSize = 12.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                                Text(
                                    text = MoneyFormatter.formatRupees(adj.amountPaise),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AddEditCategoryDialog(
            title = "Add New Category",
            initialName = "",
            initialBucket = BudgetBucket.NEEDS,
            initialAdvancePolicy = AdvancePolicy.CARRY_TO_ADVANCE,
            initialBudgetRupees = "",
            onDismiss = { showAddDialog = false },
            onSave = { name, bucket, advancePolicy, budgetRupees ->
                viewModel.addCategory(name, bucket, advancePolicy, budgetRupees)
                showAddDialog = false
            }
        )
    }

    editingCategory?.let { itemState ->
        AddEditCategoryDialog(
            title = "Edit Category",
            initialName = itemState.category.name,
            initialBucket = itemState.category.defaultBucket,
            initialAdvancePolicy = itemState.category.defaultAdvancePolicy,
            initialBudgetRupees = (itemState.currentAllocationPaise / 100.0).let { if (it > 0) "%.2f".format(it) else "" },
            onDismiss = { editingCategory = null },
            onSave = { name, bucket, advancePolicy, budgetRupees ->
                viewModel.updateCategory(
                    categoryId = itemState.category.id,
                    name = name,
                    bucket = bucket,
                    advancePolicy = advancePolicy,
                    newBudgetRupees = budgetRupees
                )
                editingCategory = null
            }
        )
    }

    if (showTransferDialog) {
        TransferBudgetDialog(
            categories = state.categories.map { it.category },
            onDismiss = { showTransferDialog = false },
            onTransfer = { fromId, toId, amountRupees, reason ->
                viewModel.transferBudget(fromId, toId, amountRupees, reason)
                showTransferDialog = false
            }
        )
    }
}

@Composable
fun CategoryCard(
    itemState: CategoryItemState,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = itemState.category.name,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Policy: ${when(itemState.category.defaultAdvancePolicy) {
                        AdvancePolicy.CARRY_TO_ADVANCE -> "Carry Forward"
                        AdvancePolicy.SWEEP_TO_SAVINGS -> "Sweep to Savings"
                        AdvancePolicy.EXPIRE -> "Expire"
                    }}",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = MoneyFormatter.formatRupees(itemState.currentAllocationPaise),
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(onClick = onEdit) {
                    Icon(Icons.Default.Edit, contentDescription = "Edit Category", tint = MaterialTheme.colorScheme.primary)
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "Deactivate Category", tint = MaterialTheme.colorScheme.error)
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
    onDismiss: () -> Unit,
    onSave: (String, BudgetBucket, AdvancePolicy, String) -> Unit
) {
    var name by remember { mutableStateOf(initialName) }
    var selectedBucket by remember { mutableStateOf(initialBucket) }
    var selectedAdvancePolicy by remember { mutableStateOf(initialAdvancePolicy) }
    var budgetRupees by remember { mutableStateOf(initialBudgetRupees) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
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
                                    BudgetBucket.NEEDS -> "Needs (Essential Living)"
                                    BudgetBucket.WANTS -> "Wants (Discretionary)"
                                    BudgetBucket.SAVINGS -> "Savings (Investments & Goals)"
                                    else -> bucket.name
                                },
                                fontSize = 13.sp
                            )
                        }
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
                onClick = { onSave(name, selectedBucket, selectedAdvancePolicy, budgetRupees) }
            ) {
                Text("Save")
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
        title = { Text("Transfer Intra-Month Budget") },
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
