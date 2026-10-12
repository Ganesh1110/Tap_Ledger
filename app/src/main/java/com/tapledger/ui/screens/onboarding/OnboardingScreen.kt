package com.tapledger.ui.screens.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tapledger.ui.components.keyboardAware
import androidx.hilt.navigation.compose.hiltViewModel
import com.tapledger.core.model.AccountType
import com.tapledger.core.model.BudgetBucket
import com.tapledger.core.model.MoneyFormatter
import com.tapledger.core.util.PermissionUtils
import com.tapledger.domain.onboarding.OnboardingDistribution
import com.tapledger.ui.theme.ExpenseRed
import com.tapledger.ui.theme.IncomeGreen
import com.tapledger.ui.theme.Spacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnboardingScreen(
    onOnboardingComplete: () -> Unit,
    onNavigateToRestoreBackup: () -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(state.isCommitted) {
        if (state.isCommitted) {
            onOnboardingComplete()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = when (state.step) {
                            OnboardingStep.O1_WELCOME -> "Welcome"
                            OnboardingStep.O2_INCOME -> "Step 1 of 5: Income"
                            OnboardingStep.O3_ACCOUNTS -> "Step 2 of 5: Accounts"
                            OnboardingStep.O4_BUDGET -> "Step 3 of 5: Envelopes"
                            OnboardingStep.O5_RELIABILITY -> "Step 4 of 5: Permissions"
                            OnboardingStep.O6_REVIEW -> "Step 5 of 5: Review"
                        },
                        style = MaterialTheme.typography.titleMedium
                    )
                },
                navigationIcon = {
                    if (state.step != OnboardingStep.O1_WELCOME) {
                        IconButton(onClick = { viewModel.previousStep() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .keyboardAware()
        ) {
            when (state.step) {
                OnboardingStep.O1_WELCOME -> {
                    StepWelcome(
                        onGetStarted = { viewModel.nextStep() },
                        onRestoreBackup = onNavigateToRestoreBackup
                    )
                }
                OnboardingStep.O2_INCOME -> {
                    StepIncome(
                        incomeInput = state.incomeInput,
                        incomePaise = state.incomePaise,
                        error = state.incomeError,
                        onIncomeChanged = { viewModel.setIncomeInput(it) },
                        onContinue = { viewModel.nextStep() }
                    )
                }
                OnboardingStep.O3_ACCOUNTS -> {
                    StepAccounts(
                        state = state,
                        onAddBank = { viewModel.addBankAccount() },
                        onUpdateBank = { idx, acc -> viewModel.updateBankAccount(idx, acc) },
                        onRemoveBank = { idx -> viewModel.removeBankAccount(idx) },
                        onCashChanged = { viewModel.setCashBalanceInput(it) },
                        onAddCard = { viewModel.addCreditCard() },
                        onUpdateCard = { idx, card -> viewModel.updateCreditCard(idx, card) },
                        onRemoveCard = { idx -> viewModel.removeCreditCard(idx) },
                        onContinue = { viewModel.nextStep() }
                    )
                }
                OnboardingStep.O4_BUDGET -> {
                    StepBudget(
                        state = state,
                        onAllocationChanged = { id, amt -> viewModel.setCategoryAllocation(id, amt) },
                        onResetDefaults = { viewModel.resetAllocationsToDefault() },
                        onContinue = { viewModel.nextStep() }
                    )
                }
                OnboardingStep.O5_RELIABILITY -> {
                    StepReliability(
                        onContinue = { viewModel.nextStep() }
                    )
                }
                OnboardingStep.O6_REVIEW -> {
                    StepReview(
                        state = state,
                        netWorth = viewModel.getNetWorthPreview(),
                        onEditAccounts = { viewModel.setStep(OnboardingStep.O3_ACCOUNTS) },
                        onEditBudget = { viewModel.setStep(OnboardingStep.O4_BUDGET) },
                        onCommit = { viewModel.commit() }
                    )
                }
            }
        }
    }
}

@Composable
private fun StepWelcome(
    onGetStarted: () -> Unit,
    onRestoreBackup: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(Spacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(top = Spacing.xl)
        ) {
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.AccountBalance,
                    contentDescription = "App Logo",
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.height(Spacing.lg))

            Text(
                text = "TapLedger",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(Spacing.sm))

            Text(
                text = "Privacy-First Offline Envelope Budgeting",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(Spacing.xl))

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
            ) {
                Column(modifier = Modifier.padding(Spacing.md)) {
                    FeatureRow(Icons.Default.Lock, "100% Offline & Private", "Your data never leaves your device. No cloud, no analytics, no ads.")
                    Spacer(modifier = Modifier.height(Spacing.md))
                    FeatureRow(Icons.Default.Payments, "Zero-Based Envelopes", "Give every rupee a job with 50/30/20 monthly targets.")
                    Spacer(modifier = Modifier.height(Spacing.md))
                    FeatureRow(Icons.Default.Security, "Double-Entry Precision", "All math uses signed integer paise. Zero rounding errors.")
                }
            }
        }

        Column(modifier = Modifier.fillMaxWidth()) {
            Button(
                onClick = onGetStarted,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
            ) {
                Text("Get Started", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            }

            Spacer(modifier = Modifier.height(Spacing.md))

            OutlinedButton(
                onClick = onRestoreBackup,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
            ) {
                Text("Restore Existing Backup", fontSize = 15.sp)
            }
        }
    }
}

@Composable
private fun FeatureRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String) {
    Row(verticalAlignment = Alignment.Top) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
        Spacer(modifier = Modifier.width(Spacing.md))
        Column {
            Text(text = title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun StepIncome(
    incomeInput: String,
    incomePaise: Long,
    error: String?,
    onIncomeChanged: (String) -> Unit,
    onContinue: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(Spacing.lg)
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                text = "What is your monthly take-home income?",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(Spacing.sm))

            Text(
                text = "The amount that reaches your bank account each month after deductions.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(Spacing.xl))

            OutlinedTextField(
                value = incomeInput,
                onValueChange = onIncomeChanged,
                label = { Text("Monthly Net Income") },
                prefix = { Text("₹ ") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Done
                ),
                isError = error != null,
                supportingText = {
                    if (error != null) {
                        Text(error, color = ExpenseRed)
                    } else if (incomePaise > 0L) {
                        Text("Target: ${MoneyFormatter.formatPaiseToRupees(incomePaise)}", color = IncomeGreen)
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )
        }

        Spacer(modifier = Modifier.height(Spacing.md))

        Button(
            onClick = onContinue,
            enabled = incomePaise > 0L,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
        ) {
            Text("Continue", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun StepAccounts(
    state: OnboardingUiState,
    onAddBank: () -> Unit,
    onUpdateBank: (Int, StagedAccountUi) -> Unit,
    onRemoveBank: (Int) -> Unit,
    onCashChanged: (String) -> Unit,
    onAddCard: () -> Unit,
    onUpdateCard: (Int, StagedCreditCardUi) -> Unit,
    onRemoveCard: (Int) -> Unit,
    onContinue: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(Spacing.lg)
    ) {
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            item {
                Text(
                    text = "Bank Accounts & Cash",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Enter current starting balances. They won't count as income or expense.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(Spacing.sm))
            }

            // Bank Accounts
            itemsIndexed(state.bankAccounts) { idx, acc ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
                ) {
                    Column(modifier = Modifier.padding(Spacing.md)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Account #${idx + 1}", fontWeight = FontWeight.Bold)
                            if (state.bankAccounts.size > 1) {
                                IconButton(onClick = { onRemoveBank(idx) }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = ExpenseRed)
                                }
                            }
                        }

                        OutlinedTextField(
                            value = acc.name,
                            onValueChange = { onUpdateBank(idx, acc.copy(name = it)) },
                            label = { Text("Account Name") },
                            isError = acc.error != null,
                            supportingText = acc.error?.let { { Text(it, color = ExpenseRed) } },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(Spacing.sm))

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                            FilterChip(
                                selected = acc.type == AccountType.BANK,
                                onClick = { onUpdateBank(idx, acc.copy(type = AccountType.BANK)) },
                                label = { Text("Bank") }
                            )
                            FilterChip(
                                selected = acc.type == AccountType.WALLET,
                                onClick = { onUpdateBank(idx, acc.copy(type = AccountType.WALLET)) },
                                label = { Text("Wallet") }
                            )
                        }

                        Spacer(modifier = Modifier.height(Spacing.sm))

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                            OutlinedTextField(
                                value = acc.balanceInput,
                                onValueChange = { onUpdateBank(idx, acc.copy(balanceInput = it)) },
                                label = { Text("Current Balance") },
                                prefix = { Text("₹ ") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.weight(1.4f)
                            )
                            OutlinedTextField(
                                value = acc.identifierLast4,
                                onValueChange = { if (it.length <= 4) onUpdateBank(idx, acc.copy(identifierLast4 = it)) },
                                label = { Text("Last 4 (Opt)") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Spacer(modifier = Modifier.height(Spacing.sm))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Savings account (transfers count as savings)", style = MaterialTheme.typography.bodySmall)
                            Switch(
                                checked = acc.isSavings,
                                onCheckedChange = { onUpdateBank(idx, acc.copy(isSavings = it)) }
                            )
                        }
                    }
                }
            }

            item {
                TextButton(onClick = onAddBank) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(Spacing.sm))
                    Text("Add Another Bank / Wallet")
                }
            }

            // Cash in Hand
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
                ) {
                    Column(modifier = Modifier.padding(Spacing.md)) {
                        Text("Cash in Hand", fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(Spacing.sm))
                        OutlinedTextField(
                            value = state.cashBalanceInput,
                            onValueChange = onCashChanged,
                            label = { Text("Cash Balance") },
                            prefix = { Text("₹ ") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            isError = state.cashError != null,
                            supportingText = state.cashError?.let { { Text(it, color = ExpenseRed) } },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            // Credit Cards
            item {
                Spacer(modifier = Modifier.height(Spacing.sm))
                Text(
                    text = "Credit Cards (Optional)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Enter current outstanding dues (entered positive, stored as liabilities).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            itemsIndexed(state.creditCards) { idx, card ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
                ) {
                    Column(modifier = Modifier.padding(Spacing.md)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Credit Card #${idx + 1}", fontWeight = FontWeight.Bold)
                            IconButton(onClick = { onRemoveCard(idx) }) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = ExpenseRed)
                            }
                        }

                        OutlinedTextField(
                            value = card.name,
                            onValueChange = { onUpdateCard(idx, card.copy(name = it)) },
                            label = { Text("Card Name") },
                            isError = card.error != null,
                            supportingText = card.error?.let { { Text(it, color = ExpenseRed) } },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(Spacing.sm))

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                            OutlinedTextField(
                                value = card.outstandingInput,
                                onValueChange = { onUpdateCard(idx, card.copy(outstandingInput = it)) },
                                label = { Text("Current Dues Owed") },
                                prefix = { Text("₹ ") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.weight(1.4f)
                            )
                            OutlinedTextField(
                                value = card.identifierLast4,
                                onValueChange = { if (it.length <= 4) onUpdateCard(idx, card.copy(identifierLast4 = it)) },
                                label = { Text("Last 4") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Spacer(modifier = Modifier.height(Spacing.sm))

                        OutlinedTextField(
                            value = card.creditLimitInput,
                            onValueChange = { onUpdateCard(idx, card.copy(creditLimitInput = it)) },
                            label = { Text("Credit Limit (Optional)") },
                            prefix = { Text("₹ ") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            item {
                TextButton(onClick = onAddCard) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(Spacing.sm))
                    Text("Add Credit Card")
                }
            }
        }

        Spacer(modifier = Modifier.height(Spacing.md))

        Button(
            onClick = onContinue,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
        ) {
            Text("Continue", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun StepBudget(
    state: OnboardingUiState,
    onAllocationChanged: (Long, Long) -> Unit,
    onResetDefaults: () -> Unit,
    onContinue: () -> Unit
) {
    val totalAllocated = state.categoryAllocations.values.sum()
    val remainingToAssign = state.incomePaise - totalAllocated
    val isBalanced = remainingToAssign == 0L

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(Spacing.lg)
    ) {
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            item {
                Text(
                    text = "50 / 30 / 20 Budget Targets",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Rule B3: Total envelope allocations must equal planned monthly income.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(Spacing.sm))
            }

            // Status Banner
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = when {
                            isBalanced -> IncomeGreen.copy(alpha = 0.15f)
                            remainingToAssign > 0L -> MaterialTheme.colorScheme.primaryContainer
                            else -> ExpenseRed.copy(alpha = 0.15f)
                        }
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(Spacing.md),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (isBalanced) Icons.Default.CheckCircle else Icons.Default.Warning,
                            contentDescription = null,
                            tint = when {
                                isBalanced -> IncomeGreen
                                remainingToAssign > 0L -> MaterialTheme.colorScheme.primary
                                else -> ExpenseRed
                            }
                        )
                        Spacer(modifier = Modifier.width(Spacing.md))
                        Column {
                            Text(
                                text = when {
                                    isBalanced -> "All ₹${MoneyFormatter.formatPaiseToRupees(state.incomePaise)} Assigned!"
                                    remainingToAssign > 0L -> "${MoneyFormatter.formatPaiseToRupees(remainingToAssign)} Left to Assign"
                                    else -> "${MoneyFormatter.formatPaiseToRupees(-remainingToAssign)} Over Planned Income!"
                                },
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Planned Income: ${MoneyFormatter.formatPaiseToRupees(state.incomePaise)}",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }

            // Buckets Breakdown
            val (needsTarget, wantsTarget, savingsTarget) = OnboardingDistribution.calculateBucketTargets(state.incomePaise)
            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    BucketTargetCard("Needs (50%)", needsTarget, Modifier.weight(1f))
                    BucketTargetCard("Wants (30%)", wantsTarget, Modifier.weight(1f))
                    BucketTargetCard("Savings (20%)", savingsTarget, Modifier.weight(1f))
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Category Allocations", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    TextButton(onClick = onResetDefaults) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Reset Defaults", fontSize = 12.sp)
                    }
                }
            }

            // Categories
            val categoriesByBucket = state.categories.groupBy { it.defaultBucket }
            for (bucket in listOf(BudgetBucket.NEEDS, BudgetBucket.WANTS, BudgetBucket.SAVINGS)) {
                val cats = categoriesByBucket[bucket] ?: emptyList()
                if (cats.isNotEmpty()) {
                    item {
                        Text(
                            text = bucket.name,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                    itemsIndexed(cats) { _, cat ->
                        val currentAlloc = state.categoryAllocations[cat.id] ?: 0L
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(cat.name, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                            OutlinedTextField(
                                value = if (currentAlloc > 0L) (currentAlloc / 100L).toString() else "0",
                                onValueChange = { input ->
                                    val paise = try {
                                        MoneyFormatter.parseOpeningBalance(input)
                                    } catch (_: Exception) {
                                        0L
                                    }
                                    onAllocationChanged(cat.id, paise)
                                },
                                prefix = { Text("₹ ") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.width(140.dp)
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(Spacing.md))

        Button(
            onClick = onContinue,
            enabled = isBalanced,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
        ) {
            Text("Continue", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun BucketTargetCard(title: String, targetPaise: Long, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Column(modifier = Modifier.padding(Spacing.sm), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(title, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(2.dp))
            Text(MoneyFormatter.formatPaiseToRupees(targetPaise), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun StepReliability(
    onContinue: () -> Unit
) {
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(Spacing.lg),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            Text(
                text = "Reliability & Permissions",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(Spacing.sm))

            Text(
                text = "TapLedger requires zero network access. Optional notifications enable instant payment logging.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(Spacing.lg))

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
            ) {
                Column(modifier = Modifier.padding(Spacing.md)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.NotificationsActive, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(Spacing.md))
                        Column {
                            Text("Automatic UPI Ingestion", fontWeight = FontWeight.Bold)
                            Text("Reads local notification text from GPay, PhonePe, Paytm, and bank apps.", style = MaterialTheme.typography.bodySmall)
                        }
                    }

                    Spacer(modifier = Modifier.height(Spacing.md))

                    OutlinedButton(
                        onClick = {
                            context.startActivity(PermissionUtils.createNotificationListenerSettingsIntent())
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Open Notification Settings")
                    }
                }
            }

            Spacer(modifier = Modifier.height(Spacing.md))

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
            ) {
                Column(modifier = Modifier.padding(Spacing.md)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Security, contentDescription = null, tint = IncomeGreen)
                        Spacer(modifier = Modifier.width(Spacing.md))
                        Column {
                            Text("Local Daily Backups", fontWeight = FontWeight.Bold)
                            Text("Automated backups are stored safely in app storage. You can export anytime in Settings.", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }

        Button(
            onClick = onContinue,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
        ) {
            Text("Review Setup", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun StepReview(
    state: OnboardingUiState,
    netWorth: com.tapledger.domain.model.NetWorthSummary,
    onEditAccounts: () -> Unit,
    onEditBudget: () -> Unit,
    onCommit: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(Spacing.lg),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            item {
                Text(
                    text = "Review & Get Started",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "All accounts and envelopes will be committed atomically into your offline database.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Net Worth Summary Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Column(modifier = Modifier.padding(Spacing.md)) {
                        Text("Net Worth Preview", style = MaterialTheme.typography.labelMedium)
                        Text(
                            text = MoneyFormatter.formatPaiseToRupees(netWorth.netWorthPaise),
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(Spacing.sm))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Assets: ${MoneyFormatter.formatPaiseToRupees(netWorth.totalAssetsPaise)}", color = IncomeGreen, fontWeight = FontWeight.SemiBold)
                            Text("Liabilities: ${MoneyFormatter.formatPaiseToRupees(netWorth.totalLiabilitiesPaise)}", color = ExpenseRed, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }

            // Income Summary
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
                ) {
                    Row(
                        modifier = Modifier.padding(Spacing.md),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Planned Monthly Income", style = MaterialTheme.typography.labelSmall)
                            Text(MoneyFormatter.formatPaiseToRupees(state.incomePaise), fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // Accounts Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
                ) {
                    Column(modifier = Modifier.padding(Spacing.md)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Accounts (${state.bankAccounts.size + 1 + state.creditCards.size})", fontWeight = FontWeight.Bold)
                            TextButton(onClick = onEditAccounts) { Text("Edit") }
                        }
                        for (acc in state.bankAccounts) {
                            val bal = try { MoneyFormatter.parseOpeningBalance(acc.balanceInput) } catch (_: Exception) { 0L }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(acc.name)
                                Text(MoneyFormatter.formatPaiseToRupees(bal))
                            }
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Cash")
                            Text(MoneyFormatter.formatPaiseToRupees(state.cashBalancePaise))
                        }
                        for (card in state.creditCards) {
                            val owed = try { MoneyFormatter.parseOpeningBalance(card.outstandingInput) } catch (_: Exception) { 0L }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(card.name)
                                Text("-${MoneyFormatter.formatPaiseToRupees(owed)}", color = ExpenseRed)
                            }
                        }
                    }
                }
            }

            // Envelopes Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
                ) {
                    Column(modifier = Modifier.padding(Spacing.md)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Envelopes Budgeted", fontWeight = FontWeight.Bold)
                            TextButton(onClick = onEditBudget) { Text("Edit") }
                        }
                        Text(
                            "Total: ${MoneyFormatter.formatPaiseToRupees(state.categoryAllocations.values.sum())} across ${state.categoryAllocations.size} categories",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }

            state.commitError?.let { err ->
                item {
                    Text(err, color = ExpenseRed, style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        Spacer(modifier = Modifier.height(Spacing.md))

        Button(
            onClick = onCommit,
            enabled = !state.isCommitting,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
        ) {
            if (state.isCommitting) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary)
            } else {
                Text("Finish Setup & Start Budgeting", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
