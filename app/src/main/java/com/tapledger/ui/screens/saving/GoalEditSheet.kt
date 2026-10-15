package com.tapledger.ui.screens.saving

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.tapledger.core.database.entity.AccountEntity
import com.tapledger.core.database.entity.SavingsGoalEntity
import com.tapledger.core.model.MoneyFormatter
import com.tapledger.core.util.DateTimeUtils
import com.tapledger.ui.theme.Spacing

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun GoalEditSheet(
    existingGoal: SavingsGoalEntity? = null,
    availableAccounts: List<AccountEntity>,
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    onDismiss: () -> Unit,
    onSave: (name: String, targetPaise: Long, deadlineMs: Long?, priority: Int, linkedAccountId: Long?) -> Unit
) {
    var name by remember { mutableStateOf(existingGoal?.name ?: "") }
    var targetInput by remember {
        mutableStateOf(
            if (existingGoal != null) (existingGoal.targetPaise / 100L).toString() else ""
        )
    }
    var selectedDeadlineMs by remember { mutableStateOf(existingGoal?.deadlineEpochMs) }
    var priority by remember { mutableIntStateOf(existingGoal?.priority ?: 1) }
    var selectedAccountId by remember { mutableStateOf(existingGoal?.linkedAccountId) }
    var accountMenuExpanded by remember { mutableStateOf(false) }

    var showDatePicker by remember { mutableStateOf(false) }
    var validationError by remember { mutableStateOf<String?>(null) }

    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = selectedDeadlineMs ?: (DateTimeUtils.nowEpochMillis() + 90L * 86400000L)
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.lg)
                .padding(bottom = Spacing.xl)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (existingGoal != null) "Edit Savings Goal" else "Create Savings Goal",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = onDismiss) {
                    Icon(imageVector = Icons.Default.Close, contentDescription = "Close")
                }
            }

            // Name
            OutlinedTextField(
                value = name,
                onValueChange = {
                    name = it
                    validationError = null
                },
                label = { Text("Goal Name") },
                placeholder = { Text("e.g. Emergency Fund, New Bike") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            // Target Amount
            OutlinedTextField(
                value = targetInput,
                onValueChange = {
                    targetInput = it
                    validationError = null
                },
                label = { Text("Target Amount (₹)") },
                placeholder = { Text("e.g. 50000") },
                leadingIcon = { Text("₹", fontWeight = FontWeight.Bold) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Done
                ),
                modifier = Modifier.fillMaxWidth()
            )

            // Deadline Section
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Target Deadline (Optional)",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(Spacing.xs))

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showDatePicker = true },
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
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(Spacing.sm))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = selectedDeadlineMs?.let { DateTimeUtils.formatDate(it) } ?: "No deadline set",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Tap to choose target date",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (selectedDeadlineMs != null) {
                            IconButton(onClick = { selectedDeadlineMs = null }) {
                                Icon(imageVector = Icons.Default.Close, contentDescription = "Clear deadline")
                            }
                        }
                    }
                }
            }

            // Priority Selector
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Priority",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(Spacing.xs))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
                ) {
                    listOf(
                        1 to "P1 (High)",
                        2 to "P2 (Medium)",
                        3 to "P3 (Normal)",
                        4 to "P4 (Low)"
                    ).forEach { (p, label) ->
                        FilterChip(
                            selected = priority == p,
                            onClick = { priority = p },
                            label = { Text(label) },
                            leadingIcon = if (priority == p) {
                                { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                            } else null
                        )
                    }
                }
            }

            // Linked Account Selector (Optional earmarking link)
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Linked Account (Earmarking)",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "Earmarks funds from this account. Does not move money (D011).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(Spacing.xs))

                ExposedDropdownMenuBox(
                    expanded = accountMenuExpanded,
                    onExpandedChange = { accountMenuExpanded = it }
                ) {
                    val selectedAccountName = availableAccounts.find { it.id == selectedAccountId }?.name ?: "None (Unlinked)"
                    OutlinedTextField(
                        value = selectedAccountName,
                        onValueChange = {},
                        readOnly = true,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = accountMenuExpanded) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor()
                    )
                    ExposedDropdownMenu(
                        expanded = accountMenuExpanded,
                        onDismissRequest = { accountMenuExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("None (Unlinked)") },
                            onClick = {
                                selectedAccountId = null
                                accountMenuExpanded = false
                            }
                        )
                        availableAccounts.forEach { account ->
                            DropdownMenuItem(
                                text = { Text("${account.name} (${account.type.name})") },
                                onClick = {
                                    selectedAccountId = account.id
                                    accountMenuExpanded = false
                                }
                            )
                        }
                    }
                }
            }

            // Validation Error message
            if (validationError != null) {
                Text(
                    text = validationError!!,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            Spacer(modifier = Modifier.height(Spacing.xs))

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Cancel")
                }
                Button(
                    onClick = {
                        val trimmedName = name.trim()
                        if (trimmedName.isEmpty()) {
                            validationError = "Please enter a goal name"
                            return@Button
                        }
                        val paise = try {
                            MoneyFormatter.parseRupeesStringToPaise(targetInput)
                        } catch (e: Exception) {
                            validationError = "Invalid target amount: ${e.message}"
                            return@Button
                        }
                        if (paise <= 0L) {
                            validationError = "Target amount must be strictly greater than 0"
                            return@Button
                        }
                        onSave(trimmedName, paise, selectedDeadlineMs, priority, selectedAccountId)
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Save Goal")
                }
            }
        }
    }

    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        selectedDeadlineMs = datePickerState.selectedDateMillis
                        showDatePicker = false
                    }
                ) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text("Cancel")
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}
