package com.tapledger.ui.screens.manual

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tapledger.core.model.BudgetBucket
import com.tapledger.core.util.DateTimeUtils
import com.tapledger.core.model.TransactionKind
import com.tapledger.ui.theme.ExpenseRed
import com.tapledger.ui.theme.Spacing
import com.tapledger.ui.components.keyboardAware

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ManualEntryScreen(
    viewModel: ManualEntryViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val scrollState = rememberScrollState()

    LaunchedEffect(state.isSuccess) {
        if (state.isSuccess) {
            onNavigateBack()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Add Transaction",
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
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        bottomBar = {
            // Sticky Save button above the IME
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 3.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .imePadding()
            ) {
                Column(modifier = Modifier.padding(Spacing.md)) {
                    state.errorMessage?.let { error ->
                        Text(
                            text = error,
                            color = ExpenseRed,
                            fontWeight = FontWeight.Medium,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(bottom = Spacing.xs)
                        )
                    }
                    Button(
                        onClick = { viewModel.submitTransaction() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text(
                            text = "Save Transaction",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = Spacing.md)
                .keyboardAware(imePadding = false, imeNestedScroll = true)
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            Spacer(modifier = Modifier.height(Spacing.xxs))

            // 1. Transaction Type Segmented Selector
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

            // 2. Large Amount Hero Input with ₹ Prefix
            OutlinedTextField(
                value = state.amountRupeesText,
                onValueChange = { input ->
                    if (input.isEmpty() || input.matches(Regex("""^\d+(\.\d{0,2})?$"""))) {
                        viewModel.onAmountChanged(input)
                    }
                },
                leadingIcon = {
                    Text(
                        text = "₹",
                        style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = Spacing.sm)
                    )
                },
                placeholder = {
                    Text(
                        text = "0.00",
                        style = MaterialTheme.typography.headlineLarge.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
                        )
                    )
                },
                textStyle = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold),
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Decimal,
                    imeAction = ImeAction.Next
                ),
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow
                ),
                modifier = Modifier.fillMaxWidth()
            )

            // 3. Date & Time Display (Asia/Kolkata)
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.md, vertical = Spacing.sm),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.CalendarToday,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(Spacing.sm))
                    Column {
                        Text(
                            text = "Transaction Date",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "${DateTimeUtils.formatDate(DateTimeUtils.nowEpochMillis())} (Asia/Kolkata)",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            // 4. Account Selector (Wrapping flow chips)
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = if (state.selectedKind == TransactionKind.TRANSFER) "From Account" else "Account",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(Spacing.xs))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                    verticalArrangement = Arrangement.spacedBy(Spacing.xs)
                ) {
                    state.accounts.forEach { acc ->
                        FilterChip(
                            selected = state.selectedAccountId == acc.id,
                            onClick = { viewModel.onAccountSelected(acc.id) },
                            label = { Text(acc.name) },
                            leadingIcon = if (state.selectedAccountId == acc.id) {
                                { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                            } else null
                        )
                    }
                }
            }

            // 5. Transfer Destination Account (if Transfer)
            if (state.selectedKind == TransactionKind.TRANSFER) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "To Account",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(Spacing.xs))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                        verticalArrangement = Arrangement.spacedBy(Spacing.xs)
                    ) {
                        state.accounts.forEach { acc ->
                            FilterChip(
                                selected = state.destinationAccountId == acc.id,
                                onClick = { viewModel.onDestinationAccountSelected(acc.id) },
                                label = { Text(acc.name) },
                                leadingIcon = if (state.destinationAccountId == acc.id) {
                                    { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                                } else null
                            )
                        }
                    }
                }
            }

            // 6. Category Selector grouped by Bucket (Bucket -> Category, wrapping flow chips)
            if (state.selectedKind == TransactionKind.EXPENSE || state.selectedKind == TransactionKind.REFUND) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Category (Bucket Envelope)",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(Spacing.xs))

                    val categoriesByBucket = state.categories.groupBy { it.defaultBucket }
                    listOf(BudgetBucket.NEEDS, BudgetBucket.WANTS, BudgetBucket.SAVINGS).forEach { bucket ->
                        val bucketCats = categoriesByBucket[bucket] ?: emptyList()
                        if (bucketCats.isNotEmpty()) {
                            Text(
                                text = when (bucket) {
                                    BudgetBucket.NEEDS -> "Needs (50%)"
                                    BudgetBucket.WANTS -> "Wants (30%)"
                                    BudgetBucket.SAVINGS -> "Savings (20%)"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = Spacing.xs, bottom = 2.dp)
                            )
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                                verticalArrangement = Arrangement.spacedBy(Spacing.xs)
                            ) {
                                bucketCats.forEach { cat ->
                                    FilterChip(
                                        selected = state.selectedCategoryId == cat.id,
                                        onClick = { viewModel.onCategorySelected(cat.id) },
                                        label = { Text(cat.name) },
                                        leadingIcon = if (state.selectedCategoryId == cat.id) {
                                            { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                                        } else null
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 7. Merchant / Payee
            OutlinedTextField(
                value = state.merchantText,
                onValueChange = { viewModel.onMerchantChanged(it) },
                label = { Text("Merchant / Payee") },
                placeholder = { Text("e.g. Swiggy, DMart, Salary") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.Next
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            )

            // 8. Note (Optional)
            OutlinedTextField(
                value = state.noteText,
                onValueChange = { viewModel.onNoteChanged(it) },
                label = { Text("Note (Optional)") },
                placeholder = { Text("Add transaction details") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.Done
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(Spacing.xl))
        }
    }
}
