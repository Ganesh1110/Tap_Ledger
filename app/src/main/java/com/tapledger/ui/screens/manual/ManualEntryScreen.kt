package com.tapledger.ui.screens.manual

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.tapledger.core.model.TransactionKind
import com.tapledger.ui.theme.ExpenseRed

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
            // 1. Transaction Kind Segmented Selector
            Text("Transaction Type", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            val kinds = listOf(
                TransactionKind.EXPENSE,
                TransactionKind.INCOME,
                TransactionKind.TRANSFER,
                TransactionKind.REFUND
            )
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                kinds.forEachIndexed { index, kind ->
                    SegmentedButton(
                        selected = state.selectedKind == kind,
                        onClick = { viewModel.onKindSelected(kind) },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = kinds.size)
                    ) {
                        Text(
                            text = when (kind) {
                                TransactionKind.EXPENSE -> "Expense"
                                TransactionKind.INCOME -> "Income"
                                TransactionKind.TRANSFER -> "Transfer"
                                TransactionKind.REFUND -> "Refund"
                                else -> kind.name
                            },
                            fontSize = 12.sp,
                            maxLines = 1
                        )
                    }
                }
            }

            // 2. Amount Input
            OutlinedTextField(
                value = state.amountRupeesText,
                onValueChange = { viewModel.onAmountChanged(it) },
                label = { Text("Amount (₹)") },
                placeholder = { Text("e.g. 850 or 1500.50") },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Decimal,
                    imeAction = ImeAction.Next
                ),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            // 3. Account Selector
            Text(
                if (state.selectedKind == TransactionKind.TRANSFER) "From Account" else "Account",
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp
            )
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(state.accounts, key = { it.id }) { acc ->
                    FilterChip(
                        selected = state.selectedAccountId == acc.id,
                        onClick = { viewModel.onAccountSelected(acc.id) },
                        label = { Text(acc.name) }
                    )
                }
            }

            // 4. Transfer Destination Account
            if (state.selectedKind == TransactionKind.TRANSFER) {
                Text("To Account", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(state.accounts, key = { it.id }) { acc ->
                        FilterChip(
                            selected = state.destinationAccountId == acc.id,
                            onClick = { viewModel.onDestinationAccountSelected(acc.id) },
                            label = { Text(acc.name) }
                        )
                    }
                }
            }

            // 5. Category Selector
            if (state.selectedKind == TransactionKind.EXPENSE || state.selectedKind == TransactionKind.REFUND) {
                Text("Category", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(state.categories, key = { it.id }) { cat ->
                        FilterChip(
                            selected = state.selectedCategoryId == cat.id,
                            onClick = { viewModel.onCategorySelected(cat.id) },
                            label = { Text(cat.name) }
                        )
                    }
                }
            }

            // 6. Merchant / Payee
            OutlinedTextField(
                value = state.merchantText,
                onValueChange = { viewModel.onMerchantChanged(it) },
                label = { Text("Merchant / Payee") },
                placeholder = { Text("e.g. Swiggy, Amazon, Salary") },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.Next
                ),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            // 7. Note
            OutlinedTextField(
                value = state.noteText,
                onValueChange = { viewModel.onNoteChanged(it) },
                label = { Text("Note (optional)") },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.Done
                ),
                modifier = Modifier.fillMaxWidth()
            )

            // Error display
            state.errorMessage?.let { error ->
                Text(text = error, color = ExpenseRed, fontWeight = FontWeight.Medium, fontSize = 13.sp)
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
