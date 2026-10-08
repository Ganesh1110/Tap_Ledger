package com.tapledger.ui.screens.manual

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.tapledger.core.model.TransactionKind
import com.tapledger.ui.theme.RoseError

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManualEntryScreen(
    viewModel: ManualEntryViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(state.isSuccess) {
        if (state.isSuccess) {
            onNavigateBack()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Add Transaction") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Transaction Kind Selector
            Text("Type", fontWeight = FontWeight.SemiBold)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(
                    TransactionKind.EXPENSE,
                    TransactionKind.INCOME,
                    TransactionKind.TRANSFER,
                    TransactionKind.REFUND
                ).forEach { kind ->
                    FilterChip(
                        selected = state.selectedKind == kind,
                        onClick = { viewModel.onKindSelected(kind) },
                        label = { Text(kind.name) }
                    )
                }
            }

            // 2. Amount Input
            OutlinedTextField(
                value = state.amountRupeesText,
                onValueChange = { viewModel.onAmountChanged(it) },
                label = { Text("Amount (₹)") },
                placeholder = { Text("e.g. 850 or 1500.50") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            // 3. Account Selector
            Text(
                if (state.selectedKind == TransactionKind.TRANSFER) "From Account" else "Account",
                fontWeight = FontWeight.SemiBold
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                state.accounts.forEach { acc ->
                    FilterChip(
                        selected = state.selectedAccountId == acc.id,
                        onClick = { viewModel.onAccountSelected(acc.id) },
                        label = { Text(acc.name) }
                    )
                }
            }

            // 4. Transfer Destination Account
            if (state.selectedKind == TransactionKind.TRANSFER) {
                Text("To Account", fontWeight = FontWeight.SemiBold)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    state.accounts.forEach { acc ->
                        FilterChip(
                            selected = state.destinationAccountId == acc.id,
                            onClick = { viewModel.onDestinationAccountSelected(acc.id) },
                            label = { Text(acc.name) }
                        )
                    }
                }
            }

            // 5. Category Selector (for Expense & Refund)
            if (state.selectedKind == TransactionKind.EXPENSE || state.selectedKind == TransactionKind.REFUND) {
                Text("Category", fontWeight = FontWeight.SemiBold)
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    state.categories.forEach { cat ->
                        FilterChip(
                            selected = state.selectedCategoryId == cat.id,
                            onClick = { viewModel.onCategorySelected(cat.id) },
                            label = { Text("${cat.name} (${cat.defaultBucket})") },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            // 6. Merchant / Payee
            OutlinedTextField(
                value = state.merchantText,
                onValueChange = { viewModel.onMerchantChanged(it) },
                label = { Text("Merchant / Description") },
                placeholder = { Text("e.g. Swiggy, DMart, Salary") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            // 7. Note
            OutlinedTextField(
                value = state.noteText,
                onValueChange = { viewModel.onNoteChanged(it) },
                label = { Text("Note (optional)") },
                modifier = Modifier.fillMaxWidth()
            )

            // Error display
            state.errorMessage?.let { error ->
                Text(text = error, color = RoseError, fontWeight = FontWeight.Medium)
            }

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = { viewModel.submitTransaction() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Save Transaction")
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
