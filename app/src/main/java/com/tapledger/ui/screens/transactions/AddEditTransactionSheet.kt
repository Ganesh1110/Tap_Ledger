package com.tapledger.ui.screens.transactions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tapledger.core.database.entity.AccountEntity
import com.tapledger.core.database.entity.CategoryEntity
import com.tapledger.core.database.entity.TransactionEntity
import com.tapledger.core.model.MoneyFormatter
import com.tapledger.core.model.TransactionKind
import com.tapledger.ui.theme.Spacing

/**
 * Bottom sheet for adding or editing transactions with strict numeric field masking
 * restricting amount input to valid decimal syntax (\d+(\.\d{0,2})?).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditTransactionSheet(
    transaction: TransactionEntity? = null,
    accounts: List<AccountEntity> = emptyList(),
    categories: List<CategoryEntity> = emptyList(),
    onDismiss: () -> Unit,
    onSave: (
        amountPaise: Long,
        accountId: Long,
        categoryId: Long?,
        merchant: String,
        note: String,
        kind: TransactionKind
    ) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var amountRupeesText by remember(transaction) {
        mutableStateOf(
            if (transaction != null && transaction.amountPaise > 0L) {
                val whole = transaction.amountPaise / 100L
                val frac = transaction.amountPaise % 100L
                if (frac == 0L) whole.toString() else String.format(java.util.Locale.ROOT, "%d.%02d", whole, frac)
            } else ""
        )
    }

    var merchantText by remember(transaction) {
        mutableStateOf(transaction?.merchantRaw ?: "")
    }

    var noteText by remember(transaction) {
        mutableStateOf(transaction?.notes ?: "")
    }

    var selectedAccountId by remember(transaction, accounts) {
        mutableStateOf(transaction?.accountId ?: accounts.firstOrNull()?.id ?: 0L)
    }

    var selectedCategoryId by remember(transaction, categories) {
        mutableStateOf(categories.firstOrNull()?.id)
    }

    var selectedKind by remember(transaction) {
        mutableStateOf(transaction?.kind ?: TransactionKind.EXPENSE)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            Text(
                text = if (transaction == null) "Add Transaction" else "Edit Transaction",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )

            // Amount Input Field with decimal masking (\d+(\.\d{0,2})?)
            OutlinedTextField(
                value = amountRupeesText,
                onValueChange = { input ->
                    if (input.isEmpty() || input.matches(Regex("""^\d+(\.\d{0,2})?$"""))) {
                        amountRupeesText = input
                    }
                },
                label = { Text("Amount") },
                leadingIcon = {
                    Text(
                        text = "₹",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary
                    )
                },
                placeholder = { Text("0.00") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Decimal,
                    imeAction = ImeAction.Next
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = merchantText,
                onValueChange = { merchantText = it },
                label = { Text("Merchant / Payee") },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = noteText,
                onValueChange = { noteText = it },
                label = { Text("Note (Optional)") },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(Spacing.xs))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(onClick = onDismiss) {
                    Text("Cancel")
                }
                Spacer(modifier = Modifier.width(Spacing.sm))
                Button(
                    onClick = {
                        val paise = try {
                            MoneyFormatter.toPaise(amountRupeesText)
                        } catch (_: Exception) {
                            0L
                        }
                        if (paise > 0L && selectedAccountId > 0L) {
                            onSave(
                                paise,
                                selectedAccountId,
                                selectedCategoryId,
                                merchantText,
                                noteText,
                                selectedKind
                            )
                            onDismiss()
                        }
                    },
                    enabled = amountRupeesText.isNotBlank()
                ) {
                    Text("Save")
                }
            }
        }
    }
}
