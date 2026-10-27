package com.tapledger.ui.screens.saving

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tapledger.core.model.MoneyFormatter
import com.tapledger.ui.components.SectionHeader
import com.tapledger.ui.theme.IncomeGreen
import com.tapledger.ui.theme.Spacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SavingsGuideSettingsScreen(
    viewModel: SavingsGuideSettingsViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val config = state.config

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Savings Automation Rules", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = Spacing.md),
            contentPadding = PaddingValues(top = Spacing.sm, bottom = Spacing.listBottomPadding),
            verticalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            // Explainer Card
            item(key = "philosophy_card") {
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
                            .padding(Spacing.md),
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = IncomeGreen,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                        Column {
                            Text(
                                text = "How the Guide Works",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Move 20% on payday and sweep leftovers at month-end. Calculations are conservative and never suggest moving money you still need for bills, cards, or commitments.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Section 1: Account Roles
            item(key = "account_roles_header") {
                SectionHeader(title = "Account Roles")
            }

            item(key = "spending_account_selector") {
                var expanded by remember { mutableStateOf(false) }
                val currentSpending = state.accounts.find { it.id == config.spendingAccountId }
                val spendingText = currentSpending?.name ?: "Auto-detect (Spending / Checking)"

                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = it }
                ) {
                    OutlinedTextField(
                        value = spendingText,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Spending Account (Source)") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        modifier = Modifier
                            .menuAnchor()
                            .fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Auto-detect default") },
                            onClick = {
                                viewModel.setSpendingAccount(null)
                                expanded = false
                            }
                        )
                        state.accounts.forEach { account ->
                            DropdownMenuItem(
                                text = { Text("${account.name} (..${account.identifierLast4})") },
                                onClick = {
                                    viewModel.setSpendingAccount(account.id)
                                    expanded = false
                                }
                            )
                        }
                    }
                }
            }

            item(key = "savings_account_selector") {
                var expanded by remember { mutableStateOf(false) }
                val currentSavings = state.accounts.find { it.id == config.savingsAccountId }
                val savingsText = currentSavings?.name ?: "Auto-detect (Savings Account)"

                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = it }
                ) {
                    OutlinedTextField(
                        value = savingsText,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Savings Account (Destination)") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        modifier = Modifier
                            .menuAnchor()
                            .fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Auto-detect default") },
                            onClick = {
                                viewModel.setSavingsAccount(null)
                                expanded = false
                            }
                        )
                        state.accounts.forEach { account ->
                            DropdownMenuItem(
                                text = { Text("${account.name} (..${account.identifierLast4})") },
                                onClick = {
                                    viewModel.setSavingsAccount(account.id)
                                    expanded = false
                                }
                            )
                        }
                    }
                }
            }

            // Section 2: Conservative Buffer & Thresholds
            item(key = "safety_header") {
                SectionHeader(title = "Safety & Triggers")
            }

            item(key = "buffer_setting") {
                var bufferText by remember(config.bufferPaise) {
                    mutableStateOf((config.bufferPaise / 100L).toString())
                }

                OutlinedTextField(
                    value = bufferText,
                    onValueChange = { input ->
                        val digits = input.filter { it.isDigit() }
                        bufferText = digits
                        val rupees = digits.toLongOrNull() ?: 0L
                        viewModel.setBufferRupees(rupees)
                    },
                    label = { Text("Safety Buffer (₹)") },
                    supportingText = { Text("Extra buffer kept untouched in spending account") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            item(key = "payday_threshold_setting") {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(Spacing.md)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = "Payday Trigger Threshold", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                            Text(text = "${config.paydayThresholdPct}%", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = IncomeGreen)
                        }
                        Text(
                            text = "Triggers a Payday transfer plan when income reaches this percentage of planned income.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(Spacing.sm))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = { viewModel.setPaydayThresholdPct((config.paydayThresholdPct - 5).coerceAtLeast(10)) },
                                enabled = config.paydayThresholdPct > 10
                            ) {
                                Icon(Icons.Default.Remove, contentDescription = "Decrease threshold")
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                                listOf(25, 50, 75, 100).forEach { pct ->
                                    FilterChip(
                                        selected = config.paydayThresholdPct == pct,
                                        onClick = { viewModel.setPaydayThresholdPct(pct) },
                                        label = { Text("$pct%") }
                                    )
                                }
                            }
                            IconButton(
                                onClick = { viewModel.setPaydayThresholdPct((config.paydayThresholdPct + 5).coerceAtMost(100)) },
                                enabled = config.paydayThresholdPct < 100
                            ) {
                                Icon(Icons.Default.Add, contentDescription = "Increase threshold")
                            }
                        }
                    }
                }
            }

            item(key = "sweep_window_setting") {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(Spacing.md)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = "Month-End Sweep Window", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                            Text(text = "Last ${config.sweepWindowDays} days", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        }
                        Text(
                            text = "Evaluates leftover surplus to sweep in the last N days of the period.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(Spacing.sm))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = { viewModel.setSweepWindowDays((config.sweepWindowDays - 1).coerceAtLeast(1)) },
                                enabled = config.sweepWindowDays > 1
                            ) {
                                Icon(Icons.Default.Remove, contentDescription = "Decrease sweep window")
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                                listOf(1, 2, 3, 5, 7).forEach { days ->
                                    FilterChip(
                                        selected = config.sweepWindowDays == days,
                                        onClick = { viewModel.setSweepWindowDays(days) },
                                        label = { Text("${days}d") }
                                    )
                                }
                            }
                            IconButton(
                                onClick = { viewModel.setSweepWindowDays((config.sweepWindowDays + 1).coerceAtMost(14)) },
                                enabled = config.sweepWindowDays < 14
                            ) {
                                Icon(Icons.Default.Add, contentDescription = "Increase sweep window")
                            }
                        }
                    }
                }
            }

            // Section 3: Notifications & Handoff
            item(key = "automation_header") {
                SectionHeader(title = "Automation & UPI Handoff")
            }

            item(key = "notifications_toggle") {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(Spacing.md),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = "Local Notifications", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                            Text(
                                text = "Nudge on payday, sweep window, reminders and dip alerts.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = config.notificationsEnabled,
                            onCheckedChange = { viewModel.setNotificationsEnabled(it) }
                        )
                    }
                }
            }

            item(key = "preferred_pay_app_selector") {
                var expanded by remember { mutableStateOf(false) }
                val payAppOptions = listOf(
                    null to "Default System Chooser",
                    "com.google.android.apps.nbu.paisa.user" to "Google Pay",
                    "com.phonepe.app" to "PhonePe",
                    "net.one97.paytm" to "Paytm",
                    "in.org.npci.upiapp" to "BHIM"
                )
                val currentOption = payAppOptions.find { it.first == config.preferredPayApp }?.second ?: "Default System Chooser"

                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = it }
                ) {
                    OutlinedTextField(
                        value = currentOption,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Preferred UPI App") },
                        supportingText = { Text("Opened directly when tapping 'Open payment app'") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        modifier = Modifier
                            .menuAnchor()
                            .fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        payAppOptions.forEach { (pkg, label) ->
                            DropdownMenuItem(
                                text = { Text(label) },
                                onClick = {
                                    viewModel.setPreferredPayApp(pkg)
                                    expanded = false
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
