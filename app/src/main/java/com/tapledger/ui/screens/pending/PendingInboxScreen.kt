package com.tapledger.ui.screens.pending

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
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
import com.tapledger.core.database.entity.CategoryEntity
import com.tapledger.core.database.entity.TransactionEntity
import com.tapledger.core.model.BudgetBucket
import com.tapledger.core.model.MoneyFormatter
import com.tapledger.core.util.DateTimeUtils
import com.tapledger.ui.components.EmptyState
import com.tapledger.ui.components.MoneyText

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PendingInboxScreen(
    viewModel: PendingInboxViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit
) {
    val pendingTxs by viewModel.pendingTransactions.collectAsState()
    val categories by viewModel.categories.collectAsState()
    val suggestedMap by viewModel.suggestedCategoryMap.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Pending Inbox (${pendingTxs.size})") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        if (pendingTxs.isEmpty()) {
            EmptyState(
                title = "Inbox Zero!",
                subtitle = "All captured transactions have been classified into budget categories.",
                modifier = Modifier.padding(padding)
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp),
                contentPadding = PaddingValues(vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                items(pendingTxs, key = { it.id }) { tx ->
                    val suggestedCat = categories.find { it.id == suggestedMap[tx.id] }
                    PendingTransactionCard(
                        tx = tx,
                        categories = categories,
                        suggestedCategory = suggestedCat,
                        onCategorize = { catId ->
                            viewModel.categorizeTransaction(tx.id, catId, tx.amountPaise)
                        },
                        onVoid = {
                            viewModel.voidTransaction(tx.id)
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun PendingTransactionCard(
    tx: TransactionEntity,
    categories: List<CategoryEntity>,
    suggestedCategory: CategoryEntity? = null,
    onCategorize: (Long) -> Unit,
    onVoid: () -> Unit
) {
    var showCategoryMenu by remember { mutableStateOf(false) }
    var selectedBucketFilter by remember { mutableStateOf<BudgetBucket?>(null) }

    val filteredCategories = if (selectedBucketFilter != null) {
        categories.filter { it.defaultBucket == selectedBucketFilter }
    } else {
        categories
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = tx.merchantRaw ?: "Transaction",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                    Text(
                        text = DateTimeUtils.formatDateTime(tx.occurredAt),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                MoneyText(
                    amountPaise = tx.amountPaise,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary
                )
            }

            if (suggestedCategory != null) {
                Spacer(modifier = Modifier.height(10.dp))
                Button(
                    onClick = { onCategorize(suggestedCategory.id) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                ) {
                    Text("✨ Suggested: ${suggestedCategory.name} (1-Tap Apply)", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Assign to bucket:",
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(6.dp))

            fun onBucketSelected(bucket: BudgetBucket) {
                val matching = categories.filter { it.defaultBucket == bucket }
                if (matching.size == 1) {
                    onCategorize(matching.first().id)
                } else {
                    selectedBucketFilter = bucket
                    showCategoryMenu = true
                }
            }

            // Tier 1 Bucket Buttons (Section 41)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { onBucketSelected(BudgetBucket.NEEDS) },
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp)
                ) {
                    Text("Needs", fontSize = 12.sp)
                }
                Button(
                    onClick = { onBucketSelected(BudgetBucket.WANTS) },
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp)
                ) {
                    Text("Wants", fontSize = 12.sp)
                }
                Button(
                    onClick = { onBucketSelected(BudgetBucket.SAVINGS) },
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp)
                ) {
                    Text("Savings", fontSize = 12.sp)
                }
            }

            DropdownMenu(
                expanded = showCategoryMenu,
                onDismissRequest = { showCategoryMenu = false }
            ) {
                filteredCategories.forEach { cat ->
                    DropdownMenuItem(
                        text = { Text(cat.name) },
                        onClick = {
                            showCategoryMenu = false
                            onCategorize(cat.id)
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Action row (Skip / Void / Ignore)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                Button(
                    onClick = onVoid,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text("Ignore / Void", fontSize = 12.sp)
                }
            }
        }
    }
}
