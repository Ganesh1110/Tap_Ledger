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
import com.tapledger.core.model.MoneyFormatter
import com.tapledger.core.model.TransactionKind
import com.tapledger.core.model.TransactionStatus
import com.tapledger.core.util.DateTimeUtils
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
                title = { Text("Transactions (${transactions.size})") },
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
                Icon(Icons.Default.Add, contentDescription = "Add")
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(transactions, key = { it.id }) { tx ->
                TransactionItemCard(
                    tx = tx,
                    onVoid = { viewModel.voidTransaction(tx.id) },
                    onRestore = { viewModel.restoreTransaction(tx.id) }
                )
            }
        }
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
                Text(
                    text = MoneyFormatter.formatRupees(
                        paise = tx.amountPaise,
                        showSign = true
                    ),
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = when (tx.kind) {
                        TransactionKind.INCOME,
                        TransactionKind.REFUND -> EmeraldPrimary
                        else -> MaterialTheme.colorScheme.onSurface
                    }
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
