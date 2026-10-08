package com.tapledger.ui.screens.transactions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.tapledger.core.database.entity.TransactionEntity
import com.tapledger.core.model.TransactionKind
import com.tapledger.core.model.TransactionStatus
import com.tapledger.core.util.DateTimeUtils
import com.tapledger.ui.components.EmptyState
import com.tapledger.ui.components.MoneyText
import com.tapledger.ui.theme.AmberWarning
import com.tapledger.ui.theme.EmeraldPrimary
import com.tapledger.ui.theme.RoseError

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionsScreen(
    viewModel: TransactionsViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit,
    onNavigateToManualEntry: () -> Unit
) {
    val transactions by viewModel.transactions.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Ledger History (${transactions.size})") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onNavigateToManualEntry,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Transaction")
            }
        }
    ) { padding ->
        if (transactions.isEmpty()) {
            EmptyState(
                title = "No Transactions Recorded",
                subtitle = "Transactions captured from bank notifications or added manually will appear here in chronological order.",
                modifier = Modifier.padding(padding)
            )
        } else {
            val groupedByDate = transactions.groupBy { DateTimeUtils.formatDate(it.occurredAt) }

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp),
                contentPadding = PaddingValues(vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                groupedByDate.forEach { (dateStr, txList) ->
                    item {
                        LedgerLineHeader(dateStr = dateStr)
                    }

                    items(txList, key = { it.id }) { tx ->
                        TransactionItemCard(
                            tx = tx,
                            onVoid = { viewModel.voidTransaction(tx.id) },
                            onRestore = { viewModel.restoreTransaction(tx.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun LedgerLineHeader(dateStr: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "● $dateStr",
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.width(8.dp))
        HorizontalDivider(
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
        )
    }
}

@Composable
fun TransactionItemCard(
    tx: TransactionEntity,
    onVoid: () -> Unit,
    onRestore: () -> Unit
) {
    val isVoided = tx.status == TransactionStatus.VOIDED
    val isPending = tx.status == TransactionStatus.PENDING

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = when {
                isVoided -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                isPending -> AmberWarning.copy(alpha = 0.1f)
                else -> MaterialTheme.colorScheme.surface
            }
        )
    ) {
        Row(
            modifier = Modifier
                .padding(14.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = tx.merchantRaw ?: tx.kind.name,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp
                    )
                    Spacer(modifier = Modifier.padding(horizontal = 4.dp))
                    Text(
                        text = "[${tx.status.name}]",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = when (tx.status) {
                            TransactionStatus.PENDING -> AmberWarning
                            TransactionStatus.CATEGORIZED -> EmeraldPrimary
                            TransactionStatus.VOIDED -> RoseError
                            TransactionStatus.IGNORED -> MaterialTheme.colorScheme.secondary
                        }
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "${DateTimeUtils.formatDateTime(tx.occurredAt)} • ${tx.source.name}",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                tx.note?.let {
                    Text(
                        text = it,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                MoneyText(
                    amountPaise = if (tx.kind == TransactionKind.INCOME || tx.kind == TransactionKind.REFUND) tx.amountPaise else -tx.amountPaise,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )

                if (isVoided) {
                    IconButton(onClick = onRestore) {
                        Icon(
                            Icons.Default.Restore,
                            contentDescription = "Restore",
                            tint = EmeraldPrimary
                        )
                    }
                } else {
                    IconButton(onClick = onVoid) {
                        Icon(
                            Icons.Default.Block,
                            contentDescription = "Void",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
