package com.tapledger.ui.screens.loans

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Percent
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tapledger.core.database.entity.LoanEntity
import com.tapledger.core.database.entity.LoanScheduleVersionEntity
import com.tapledger.core.model.MoneyFormatter
import com.tapledger.core.util.DateTimeUtils
import com.tapledger.domain.calculator.AffordabilityStatus
import com.tapledger.domain.calculator.AmortizationInstallment
import com.tapledger.domain.calculator.InterestCalculationMethod
import com.tapledger.domain.calculator.PrepaymentStrategy
import com.tapledger.ui.components.EmptyState
import com.tapledger.ui.theme.ExpenseRed
import com.tapledger.ui.theme.IncomeGreen
import com.tapledger.ui.theme.Spacing
import com.tapledger.ui.theme.WarningAmber

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoanManagementScreen(
    viewModel: LoanViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    var loanToPay by remember { mutableStateOf<LoanEntity?>(null) }
    var loanToPrepay by remember { mutableStateOf<Pair<LoanEntity, PrepaymentStrategy>?>(null) }
    var loanToForeclose by remember { mutableStateOf<LoanEntity?>(null) }
    var loanToDelete by remember { mutableStateOf<LoanEntity?>(null) }
    var selectedBankAccountId by remember { mutableStateOf<Long?>(null) }

    LaunchedEffect(state.successMessage) {
        state.successMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.dismissMessages()
        }
    }

    LaunchedEffect(state.errorMessage) {
        state.errorMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.dismissMessages()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Loans & EMIs", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.setShowSettingsSheet(true) }) {
                        Icon(imageVector = Icons.Default.Tune, contentDescription = "Loan Settings")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.90f))
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            val tabs = listOf(
                "Active Loans" to Icons.Default.Payments,
                "Setup & Affordability" to Icons.Default.Calculate,
                "Amortization" to Icons.Default.Schedule,
                "Prepayment" to Icons.AutoMirrored.Filled.TrendingDown
            )

            ScrollableTabRow(
                selectedTabIndex = state.selectedTab,
                edgePadding = Spacing.md,
                containerColor = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.90f),
                divider = {
                    androidx.compose.material3.HorizontalDivider(
                        thickness = 1.dp,
                        color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.90f)
                    )
                }
            ) {
                tabs.forEachIndexed { index, (title, icon) ->
                    Tab(
                        selected = state.selectedTab == index,
                        onClick = { viewModel.selectTab(index) },
                        text = {
                            Text(
                                text = title,
                                maxLines = 1,
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = if (state.selectedTab == index) FontWeight.Bold else FontWeight.Medium,
                                    letterSpacing = 0.3.sp
                                )
                            )
                        },
                        icon = { Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp)) }
                    )
                }
            }

            if (state.isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                when (state.selectedTab) {
                    0 -> ActiveLoansTab(
                        loans = state.activeLoans,
                        hideClosedLoans = state.preferencesConfig.hideClosedLoans,
                        onToggleHideClosedLoans = viewModel::setHideClosedLoans,
                        onPayEmi = { loan ->
                            loanToPay = loan
                            selectedBankAccountId = loan.linkedBankAccountId ?: state.bankAccounts.firstOrNull()?.id
                        },
                        onForeclose = { loan ->
                            loanToForeclose = loan
                            selectedBankAccountId = loan.linkedBankAccountId ?: state.bankAccounts.firstOrNull()?.id
                        },
                        onDeleteLoan = { loan ->
                            loanToDelete = loan
                        },
                        onSelectForSchedule = { loan ->
                            viewModel.selectLoan(loan)
                            viewModel.selectTab(2)
                        },
                        onNavigateToSetup = { viewModel.selectTab(1) }
                    )
                    1 -> LoanSetupAndAffordabilityTab(
                        state = state,
                        onFieldChange = viewModel::onSetupFieldChanged,
                        onIncomeChange = viewModel::onAffordabilityIncomeChanged,
                        onCreateLoan = viewModel::createLoan
                    )
                    2 -> AmortizationScheduleTab(
                        loans = state.activeLoans,
                        selectedLoan = state.selectedLoan,
                        schedule = state.amortizationSchedule,
                        versions = state.scheduleVersions,
                        selectedVersionId = state.selectedScheduleVersionId,
                        onSelectLoan = viewModel::selectLoan,
                        onSelectVersion = viewModel::selectScheduleVersion
                    )
                    3 -> PrepaymentSimulatorTab(
                        loans = state.activeLoans,
                        selectedLoan = state.selectedLoan,
                        prepaymentAmount = state.prepaymentAmountRupees,
                        fixedFee = state.prepaymentFixedFeeRupees,
                        percentFee = state.prepaymentPercentFeePercent,
                        result = state.prepaymentSimulationResult,
                        onSelectLoan = viewModel::selectLoan,
                        onPrepaymentAmountChange = { viewModel.onPrepaymentAmountChanged(it) },
                        onFixedFeeChange = { viewModel.onPrepaymentAmountChanged(state.prepaymentAmountRupees, fixedFeeRupees = it) },
                        onPercentFeeChange = { viewModel.onPrepaymentAmountChanged(state.prepaymentAmountRupees, percentFeePercent = it) },
                        onApplyPrepayment = { loan, strategy ->
                            loanToPrepay = loan to strategy
                            selectedBankAccountId = loan.linkedBankAccountId ?: state.bankAccounts.firstOrNull()?.id
                        }
                    )
                }
            }
        }
    }

    // One-Tap Pay EMI Dialog
    if (loanToPay != null) {
        val loan = loanToPay!!
        AlertDialog(
            onDismissRequest = { loanToPay = null },
            title = { Text("Pay EMI: ${loan.name}", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    Text(
                        text = "Monthly EMI Amount: ${MoneyFormatter.formatPaiseToRupees(loan.emiAmountPaise)}",
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "This will transfer the principal portion to reduce loan liability and record the interest portion as a Needs expense.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Select Bank Account to pay from:", fontWeight = FontWeight.Medium, fontSize = 12.sp)

                    state.bankAccounts.forEach { bank ->
                        FilterChip(
                            selected = selectedBankAccountId == bank.id,
                            onClick = { selectedBankAccountId = bank.id },
                            label = { Text(bank.name) },
                            leadingIcon = {
                                Icon(Icons.Default.AccountBalance, contentDescription = null, modifier = Modifier.size(16.dp))
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val bankId = selectedBankAccountId ?: state.bankAccounts.firstOrNull()?.id
                        if (bankId != null) {
                            viewModel.payEmi(loan, bankId)
                            loanToPay = null
                        }
                    },
                    enabled = selectedBankAccountId != null || state.bankAccounts.isNotEmpty()
                ) {
                    Text("Pay EMI Now")
                }
            },
            dismissButton = {
                TextButton(onClick = { loanToPay = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Part-Prepayment Execution Dialog
    if (loanToPrepay != null) {
        val (loan, strategy) = loanToPrepay!!
        val prepayPaise = try {
            MoneyFormatter.parseRupeesStringToPaise(state.prepaymentAmountRupees)
        } catch (e: Exception) { 0L }

        AlertDialog(
            onDismissRequest = { loanToPrepay = null },
            title = { Text("Confirm Part-Payment: ${loan.name}", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    Text(
                        text = "Payment: ${MoneyFormatter.formatPaiseToRupees(prepayPaise)}",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium,
                        color = IncomeGreen
                    )
                    Text(
                        text = "Strategy: ${if (strategy == PrepaymentStrategy.REDUCE_TENURE) "Keep EMI same, reduce tenure" else "Reduce monthly EMI, keep tenure"}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "This will atomically record fees/charges (if any), accrued interest as Needs expense, transfer principal to loan liability, and generate a new immutable schedule version.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Select Bank Account to pay from:", fontWeight = FontWeight.Medium, fontSize = 12.sp)

                    state.bankAccounts.forEach { bank ->
                        FilterChip(
                            selected = selectedBankAccountId == bank.id,
                            onClick = { selectedBankAccountId = bank.id },
                            label = { Text(bank.name) },
                            leadingIcon = {
                                Icon(Icons.Default.AccountBalance, contentDescription = null, modifier = Modifier.size(16.dp))
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val bankId = selectedBankAccountId ?: state.bankAccounts.firstOrNull()?.id
                        if (bankId != null) {
                            viewModel.applyPartPrepayment(loan, bankId, strategy)
                            loanToPrepay = null
                        }
                    },
                    enabled = selectedBankAccountId != null || state.bankAccounts.isNotEmpty()
                ) {
                    Text("Confirm Payment")
                }
            },
            dismissButton = {
                TextButton(onClick = { loanToPrepay = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Full Early Foreclosure Dialog
    if (loanToForeclose != null) {
        val loan = loanToForeclose!!
        val settlement = state.foreclosureSettlement

        AlertDialog(
            onDismissRequest = { loanToForeclose = null },
            title = { Text("Foreclose Loan: ${loan.name}", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    if (settlement != null) {
                        Text(
                            text = "Total Settlement: ${MoneyFormatter.formatPaiseToRupees(settlement.totalSettlementAmountPaise)}",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surfaceContainerHighest, RoundedCornerShape(8.dp))
                                .padding(8.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Outstanding Principal:", fontSize = 11.sp)
                                Text(MoneyFormatter.formatPaiseToRupees(settlement.outstandingPrincipalPaise), fontWeight = FontWeight.SemiBold, fontSize = 11.sp)
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Accrued Interest:", fontSize = 11.sp)
                                Text(MoneyFormatter.formatPaiseToRupees(settlement.accruedInterestPaise), fontWeight = FontWeight.SemiBold, fontSize = 11.sp)
                            }
                            if (settlement.foreclosureFeePaise > 0L) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("Foreclosure Charges:", fontSize = 11.sp)
                                    Text(MoneyFormatter.formatPaiseToRupees(settlement.foreclosureFeePaise), fontWeight = FontWeight.SemiBold, fontSize = 11.sp)
                                }
                            }
                        }
                    }

                    Text(
                        text = "Foreclosure will pay off the remaining principal plus accrued interest and close the loan liability account.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Select Bank Account to pay settlement from:", fontWeight = FontWeight.Medium, fontSize = 12.sp)

                    state.bankAccounts.forEach { bank ->
                        FilterChip(
                            selected = selectedBankAccountId == bank.id,
                            onClick = { selectedBankAccountId = bank.id },
                            label = { Text(bank.name) },
                            leadingIcon = {
                                Icon(Icons.Default.AccountBalance, contentDescription = null, modifier = Modifier.size(16.dp))
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val bankId = selectedBankAccountId ?: state.bankAccounts.firstOrNull()?.id
                        if (bankId != null) {
                            viewModel.forecloseLoan(loan, bankId)
                            loanToForeclose = null
                        }
                    },
                    enabled = selectedBankAccountId != null || state.bankAccounts.isNotEmpty()
                ) {
                    Text("Settle & Foreclose")
                }
            },
            dismissButton = {
                TextButton(onClick = { loanToForeclose = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (state.showSettingsSheet) {
        LoanSettingsSheet(
            config = state.preferencesConfig,
            onDismissRequest = { viewModel.setShowSettingsSheet(false) },
            onToggleTrackerEnabled = viewModel::setLoanTrackerEnabled,
            onToggleNetWorthInclusion = viewModel::setIncludeLoansInNetWorth,
            onToggleHideClosedLoans = viewModel::setHideClosedLoans,
            onToggleAutoDetectEmiSms = viewModel::setAutoDetectEmiSms,
            onToggleEmiBalanceGuard = viewModel::setEmiBalanceGuardEnabled,
            onSelectDaysPrior = viewModel::setEmiBalanceGuardDaysPrior
        )
    }

    if (loanToDelete != null) {
        val loan = loanToDelete!!
        AlertDialog(
            onDismissRequest = { loanToDelete = null },
            title = { Text("Delete Loan", fontWeight = FontWeight.Bold) },
            text = {
                Text("Are you sure you want to delete '${loan.name}'? This will remove the loan and its amortization schedule.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteLoan(loan)
                        loanToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.onError)
                }
            },
            dismissButton = {
                TextButton(onClick = { loanToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun ActiveLoansTab(
    loans: List<LoanEntity>,
    hideClosedLoans: Boolean,
    onToggleHideClosedLoans: (Boolean) -> Unit,
    onPayEmi: (LoanEntity) -> Unit,
    onForeclose: (LoanEntity) -> Unit,
    onDeleteLoan: (LoanEntity) -> Unit,
    onSelectForSchedule: (LoanEntity) -> Unit,
    onNavigateToSetup: () -> Unit
) {
    if (loans.isEmpty()) {
        EmptyState(
            title = "No Active Loans",
            subtitle = "Add a loan to track EMIs and amortization",
            actionLabel = "+ Setup Loan",
            onActionClick = onNavigateToSetup
        )
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${loans.size} Loans",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FilterChip(
                            selected = !hideClosedLoans,
                            onClick = { onToggleHideClosedLoans(!hideClosedLoans) },
                            label = { Text(if (hideClosedLoans) "Show Settled" else "Hide Settled", fontSize = 11.sp) }
                        )
                        FilledTonalButton(
                            onClick = onNavigateToSetup,
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Add Loan")
                        }
                    }
                }
            }

            items(loans, key = { it.id }) { loan ->
                val isSettled = !loan.isActive || loan.principalPaise == 0L
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.88f)),
                    border = BorderStroke(1.dp, androidx.compose.ui.graphics.Color.White.copy(alpha = 0.95f)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(Spacing.md)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = loan.name,
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 18.sp
                                    )
                                )
                                val ratePercent = loan.annualInterestRateBp / 100
                                val rateFrac = loan.annualInterestRateBp % 100
                                val rateFormatted = if (rateFrac == 0) "$ratePercent%" else "$ratePercent.$rateFrac%"
                                Text(
                                    text = "$rateFormatted p.a. • ${loan.tenureMonths} months tenure",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(
                                            if (isSettled) IncomeGreen.copy(alpha = 0.12f)
                                            else MaterialTheme.colorScheme.surfaceContainerHigh
                                        )
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = if (isSettled) "SETTLED" else "DUE ON ${loan.emiDay}TH",
                                        color = if (isSettled) IncomeGreen else MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 10.sp,
                                        letterSpacing = 0.5.sp
                                    )
                                }
                                IconButton(
                                    onClick = { onDeleteLoan(loan) },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Delete Loan",
                                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(Spacing.md))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(
                                    text = "REMAINING PRINCIPAL",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 9.sp,
                                    letterSpacing = 0.6.sp
                                )
                                Text(
                                    text = MoneyFormatter.formatPaiseToRupees(loan.principalPaise),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = "MONTHLY EMI",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 9.sp,
                                    letterSpacing = 0.6.sp
                                )
                                Text(
                                    text = if (isSettled) "₹0" else MoneyFormatter.formatPaiseToRupees(loan.emiAmountPaise),
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSettled) IncomeGreen else MaterialTheme.colorScheme.primary,
                                    fontSize = 16.sp
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(Spacing.md))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
                        ) {
                            OutlinedButton(
                                onClick = { onSelectForSchedule(loan) },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("Schedule")
                            }
                            if (!isSettled) {
                                OutlinedButton(
                                    onClick = { onForeclose(loan) },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text("Foreclose")
                                }
                                Button(
                                    onClick = { onPayEmi(loan) },
                                    modifier = Modifier.weight(1.2f),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text("Pay EMI")
                                }
                            }
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(Spacing.listBottomPadding))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LoanSetupAndAffordabilityTab(
    state: LoanUiState,
    onFieldChange: (name: String?, principal: String?, rate: String?, tenure: String?, emiDay: String?, calculationMethod: InterestCalculationMethod?, bankAccountId: Long?) -> Unit,
    onIncomeChange: (String) -> Unit,
    onCreateLoan: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.md)
    ) {
        Text("Loan Setup & Terms", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

        OutlinedTextField(
            value = state.setupName,
            onValueChange = { onFieldChange(it, null, null, null, null, null, null) },
            label = { Text("Loan Name (e.g. HDFC Home Loan, Car Loan)") },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = state.setupPrincipalRupees,
            onValueChange = { onFieldChange(null, it, null, null, null, null, null) },
            label = { Text("Loan Principal (₹)") },
            leadingIcon = { Text("₹", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            OutlinedTextField(
                value = state.setupAnnualRatePercent,
                onValueChange = { onFieldChange(null, null, it, null, null, null, null) },
                label = { Text("Interest Rate (%)") },
                trailingIcon = { Text("%", fontWeight = FontWeight.Bold) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.weight(1f)
            )

            OutlinedTextField(
                value = state.setupTenureMonths,
                onValueChange = { onFieldChange(null, null, null, it, null, null, null) },
                label = { Text("Tenure (Months)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.weight(1f)
            )
        }

        OutlinedTextField(
            value = state.setupEmiDay,
            onValueChange = { onFieldChange(null, null, null, null, it, null, null) },
            label = { Text("EMI Debit Day (1 - 31)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        )

        Text("Interest Calculation Method", style = MaterialTheme.typography.labelMedium)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            FilterChip(
                selected = state.setupCalculationMethod == InterestCalculationMethod.MONTHLY_REDUCING,
                onClick = { onFieldChange(null, null, null, null, null, InterestCalculationMethod.MONTHLY_REDUCING, null) },
                label = { Text("Monthly Reducing") },
                modifier = Modifier.weight(1f)
            )
            FilterChip(
                selected = state.setupCalculationMethod == InterestCalculationMethod.DAILY_REDUCING,
                onClick = { onFieldChange(null, null, null, null, null, InterestCalculationMethod.DAILY_REDUCING, null) },
                label = { Text("Daily Reducing") },
                modifier = Modifier.weight(1f)
            )
        }

        // Live Calculated EMI Hero Banner
        if (state.liveCalculatedEmiPaise > 0L) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                shape = RoundedCornerShape(14.dp)
            ) {
                Row(
                    modifier = Modifier.padding(Spacing.md),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Calculated Monthly EMI", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        Text(
                            text = MoneyFormatter.formatPaiseToRupees(state.liveCalculatedEmiPaise),
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                    Icon(
                        imageVector = Icons.Default.Calculate,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = Spacing.xs))

        // Pre-Loan Affordability Check (50/30/20 Framework)
        Text("Pre-Loan Affordability Check", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(
            text = "Evaluates whether this loan fits your 50% Needs, 30% Wants, and 20% Savings allocation.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        OutlinedTextField(
            value = state.affordabilityMonthlyIncomeRupees,
            onValueChange = onIncomeChange,
            label = { Text("Your Monthly Income (₹)") },
            leadingIcon = { Text("₹", fontWeight = FontWeight.Bold) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        )

        state.affordabilityResult?.let { aff ->
            val statusColor = when (aff.status) {
                AffordabilityStatus.SAFE -> IncomeGreen
                AffordabilityStatus.MODERATE -> WarningAmber
                AffordabilityStatus.HIGH_RISK -> ExpenseRed
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = statusColor.copy(alpha = 0.1f)),
                border = BorderStroke(1.dp, statusColor.copy(alpha = 0.6f)),
                shape = RoundedCornerShape(14.dp)
            ) {
                Column(modifier = Modifier.padding(Spacing.md)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (aff.status == AffordabilityStatus.SAFE) Icons.Default.CheckCircle else Icons.Default.Warning,
                            contentDescription = null,
                            tint = statusColor,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(Spacing.xs))
                        Text(
                            text = when (aff.status) {
                                AffordabilityStatus.SAFE -> "Comfortably Affordable"
                                AffordabilityStatus.MODERATE -> "Tight Budget Allocation"
                                AffordabilityStatus.HIGH_RISK -> "High Debt Risk!"
                            },
                            fontWeight = FontWeight.Bold,
                            color = statusColor
                        )
                    }

                    Spacer(modifier = Modifier.height(Spacing.xs))
                    Text(text = aff.explanation, style = MaterialTheme.typography.bodyMedium)

                    Spacer(modifier = Modifier.height(Spacing.sm))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("50% Needs Pool: ${MoneyFormatter.formatPaiseToRupees(aff.needs50PoolPaise)}", fontSize = 11.sp)
                        Text("30% Wants: ${MoneyFormatter.formatPaiseToRupees(aff.wants30PoolPaise)}", fontSize = 11.sp)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(Spacing.xs))

        Button(
            onClick = onCreateLoan,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(14.dp)
        ) {
            Text("Create Loan Account", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.height(Spacing.listBottomPadding))
    }
}

@Composable
private fun AmortizationScheduleTab(
    loans: List<LoanEntity>,
    selectedLoan: LoanEntity?,
    schedule: List<AmortizationInstallment>,
    versions: List<LoanScheduleVersionEntity> = emptyList(),
    selectedVersionId: Long? = null,
    onSelectLoan: (LoanEntity) -> Unit,
    onSelectVersion: (Long) -> Unit = {}
) {
    if (loans.isEmpty()) {
        EmptyState(
            title = "No Loans Added Yet",
            subtitle = "Setup a loan to view amortization schedule"
        )
    } else {
        Column(modifier = Modifier.fillMaxSize()) {
            // Loan Selector Chips (Horizontally Scrollable)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = Spacing.md, vertical = Spacing.sm),
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
            ) {
                loans.forEach { loan ->
                    FilterChip(
                        selected = selectedLoan?.id == loan.id,
                        onClick = { onSelectLoan(loan) },
                        label = { Text(loan.name) }
                    )
                }
            }

            // Schedule Version Selector Chips (if revisions exist)
            if (versions.size > 1) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = Spacing.md, vertical = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Version:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    versions.forEach { ver ->
                        FilterChip(
                            selected = selectedVersionId == ver.id,
                            onClick = { onSelectVersion(ver.id) },
                            label = {
                                Text(
                                    text = "v${ver.versionNumber}: ${ver.triggerType.replace("PART_PREPAYMENT_", "").lowercase().replaceFirstChar { it.uppercase() }}",
                                    fontSize = 11.sp
                                )
                            }
                        )
                    }
                }
            }

            if (schedule.isEmpty()) {
                EmptyState(
                    title = "No Schedule Available",
                    subtitle = "No schedule found for the selected loan"
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = Spacing.md, vertical = Spacing.xs),
                    verticalArrangement = Arrangement.spacedBy(Spacing.xs)
                ) {
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(6.dp))
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("MO", fontWeight = FontWeight.Bold, fontSize = 9.sp, letterSpacing = 0.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(28.dp))
                            Text("EMI", fontWeight = FontWeight.Bold, fontSize = 9.sp, letterSpacing = 0.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                            Text("PRINCIPAL", fontWeight = FontWeight.Bold, fontSize = 9.sp, letterSpacing = 0.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                            Text("INTEREST", fontWeight = FontWeight.Bold, fontSize = 9.sp, letterSpacing = 0.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                            Text("BALANCE", fontWeight = FontWeight.Bold, fontSize = 9.sp, letterSpacing = 0.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                        }
                    }

                    items(schedule, key = { it.monthIndex }) { inst ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 7.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("#${inst.monthIndex}", fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(28.dp))
                                Text(MoneyFormatter.formatPaiseToRupees(inst.emiPaise), fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                                Text(MoneyFormatter.formatPaiseToRupees(inst.principalPaidPaise), fontSize = 11.sp, color = IncomeGreen, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                                Text(MoneyFormatter.formatPaiseToRupees(inst.interestPaise), fontSize = 11.sp, color = ExpenseRed, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                                Text(MoneyFormatter.formatPaiseToRupees(inst.closingPrincipalPaise), fontSize = 11.sp, modifier = Modifier.weight(1f))
                            }
                        }
                    }

                    item {
                        Spacer(modifier = Modifier.height(Spacing.listBottomPadding))
                    }
                }
            }
        }
    }
}

@Composable
private fun PrepaymentSimulatorTab(
    loans: List<LoanEntity>,
    selectedLoan: LoanEntity?,
    prepaymentAmount: String,
    fixedFee: String = "0",
    percentFee: String = "0",
    result: com.tapledger.domain.calculator.PrepaymentSimulationResult?,
    onSelectLoan: (LoanEntity) -> Unit,
    onPrepaymentAmountChange: (String) -> Unit,
    onFixedFeeChange: (String) -> Unit = {},
    onPercentFeeChange: (String) -> Unit = {},
    onApplyPrepayment: (LoanEntity, PrepaymentStrategy) -> Unit = { _, _ -> }
) {
    if (loans.isEmpty()) {
        EmptyState(
            title = "No Loans Added Yet",
            subtitle = "Setup a loan to simulate part-prepayments"
        )
    } else {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            Text("Part-Prepayment Simulator & Execution", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                text = "Simulate how a lump-sum payment saves interest and cuts months off your loan, then apply the transaction directly.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Loan Selector Chips (Horizontally Scrollable)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
            ) {
                loans.forEach { loan ->
                    FilterChip(
                        selected = selectedLoan?.id == loan.id,
                        onClick = { onSelectLoan(loan) },
                        label = { Text(loan.name) }
                    )
                }
            }

            // Selected Loan Overview Context Banner
            selectedLoan?.let { loan ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
                ) {
                    Column(modifier = Modifier.padding(Spacing.md)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(loan.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            val ratePercent = loan.annualInterestRateBp / 100
                            val rateFrac = loan.annualInterestRateBp % 100
                            val rateFormatted = if (rateFrac == 0) "$ratePercent%" else "$ratePercent.$rateFrac%"
                            Text(
                                text = "$rateFormatted p.a.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        Spacer(modifier = Modifier.height(Spacing.xs))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text("Principal Amount", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(MoneyFormatter.formatPaiseToRupees(loan.principalPaise), fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("Current EMI", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(MoneyFormatter.formatPaiseToRupees(loan.emiAmountPaise), fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text("Tenure", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("${loan.tenureMonths} Mo", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            }
                        }
                    }
                }
            }

            OutlinedTextField(
                value = prepaymentAmount,
                onValueChange = onPrepaymentAmountChange,
                label = { Text("Lump-Sum Prepayment Amount (₹)") },
                leadingIcon = { Text("₹", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            )

            // Optional Prepayment Fee Fields
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                OutlinedTextField(
                    value = fixedFee,
                    onValueChange = onFixedFeeChange,
                    label = { Text("Processing Fee (₹)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f)
                )

                OutlinedTextField(
                    value = percentFee,
                    onValueChange = onPercentFeeChange,
                    label = { Text("Fee Rate (%)") },
                    trailingIcon = { Text("%", fontWeight = FontWeight.Bold) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f)
                )
            }

            result?.let { sim ->
                // Allocation Waterfall Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest)
                ) {
                    Column(modifier = Modifier.padding(Spacing.sm), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Prepayment Allocation Breakdown", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Total Cash Outflow:", fontSize = 11.sp)
                            Text(MoneyFormatter.formatPaiseToRupees(sim.prepaymentPaise), fontWeight = FontWeight.Bold, fontSize = 11.sp)
                        }
                        if (sim.feeChargesPaise > 0L) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Applicable Charges / Fees:", fontSize = 11.sp)
                                Text("- ${MoneyFormatter.formatPaiseToRupees(sim.feeChargesPaise)}", color = ExpenseRed, fontSize = 11.sp)
                            }
                        }
                        if (sim.accruedInterestPaise > 0L) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Accrued Interest Settled:", fontSize = 11.sp)
                                Text("- ${MoneyFormatter.formatPaiseToRupees(sim.accruedInterestPaise)}", color = ExpenseRed, fontSize = 11.sp)
                            }
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Net Principal Reduction:", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                            Text(MoneyFormatter.formatPaiseToRupees(sim.netPrincipalReductionPaise), fontWeight = FontWeight.Bold, color = IncomeGreen, fontSize = 11.sp)
                        }
                    }
                }

                Text("Prepayment Impact Comparison", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)

                // Option 1: Reduce Tenure (Recommended)
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.88f)),
                    border = BorderStroke(1.5.dp, IncomeGreen.copy(alpha = 0.6f)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(Spacing.md)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Option 1: Reduce Tenure",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold
                                ),
                                color = IncomeGreen,
                                modifier = Modifier.weight(1f)
                            )
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = IncomeGreen.copy(alpha = 0.15f)
                            ) {
                                Text(
                                    text = "★ Best Savings",
                                    color = IncomeGreen,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp,
                                    maxLines = 1,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(Spacing.xs))
                        Text(
                            text = "Keeps your monthly EMI at ${MoneyFormatter.formatPaiseToRupees(sim.originalEmiPaise)} and helps you become debt-free faster.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(Spacing.md))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                        ) {
                            Surface(
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(8.dp),
                                color = IncomeGreen.copy(alpha = 0.08f)
                            ) {
                                Column(
                                    modifier = Modifier.padding(Spacing.sm),
                                    horizontalAlignment = Alignment.Start
                                ) {
                                    Text(
                                        text = "TIME SAVED",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 9.sp,
                                        letterSpacing = 0.5.sp
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "${sim.tenureOptionMonthsSaved} Months",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp,
                                        color = IncomeGreen
                                    )
                                    Text(
                                        text = "New tenure: ${sim.tenureOptionNewTenureMonths} mo",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            Surface(
                                modifier = Modifier.weight(1.2f),
                                shape = RoundedCornerShape(8.dp),
                                color = IncomeGreen.copy(alpha = 0.08f)
                            ) {
                                Column(
                                    modifier = Modifier.padding(Spacing.sm),
                                    horizontalAlignment = Alignment.Start
                                ) {
                                    Text(
                                        text = "TOTAL INTEREST SAVED",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 9.sp,
                                        letterSpacing = 0.5.sp
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = MoneyFormatter.formatPaiseToRupees(sim.tenureOptionInterestSavedPaise),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp,
                                        color = IncomeGreen
                                    )
                                    Text(
                                        text = "Direct cash savings",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(Spacing.md))
                        Button(
                            onClick = { selectedLoan?.let { onApplyPrepayment(it, PrepaymentStrategy.REDUCE_TENURE) } },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Apply Strategy 1 (Reduce Tenure)")
                        }
                    }
                }

                // Option 2: Reduce Monthly EMI
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.88f)),
                    border = BorderStroke(1.dp, androidx.compose.ui.graphics.Color.White.copy(alpha = 0.95f)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(Spacing.md)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Option 2: Reduce Monthly EMI",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold
                                ),
                                modifier = Modifier.weight(1f)
                            )
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                            ) {
                                Text(
                                    text = "Cashflow Relief",
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 11.sp,
                                    maxLines = 1,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(Spacing.xs))
                        Text(
                            text = "Retains current tenure of ${sim.originalRemainingTenureMonths} months and lowers your regular monthly payout.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(Spacing.md))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                        ) {
                            Surface(
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surfaceContainer
                            ) {
                                Column(
                                    modifier = Modifier.padding(Spacing.sm),
                                    horizontalAlignment = Alignment.Start
                                ) {
                                    Text(
                                        text = "NEW MONTHLY EMI",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 9.sp,
                                        letterSpacing = 0.5.sp
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = MoneyFormatter.formatPaiseToRupees(sim.emiOptionNewEmiPaise),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = "Was ${MoneyFormatter.formatPaiseToRupees(sim.originalEmiPaise)}",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            Surface(
                                modifier = Modifier.weight(1.2f),
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surfaceContainer
                            ) {
                                Column(
                                    modifier = Modifier.padding(Spacing.sm),
                                    horizontalAlignment = Alignment.Start
                                ) {
                                    Text(
                                        text = "MONTHLY RELIEF",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 9.sp,
                                        letterSpacing = 0.5.sp
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "${MoneyFormatter.formatPaiseToRupees(sim.emiOptionMonthlySavingsPaise)}/mo",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "Saved ${MoneyFormatter.formatPaiseToRupees(sim.emiOptionInterestSavedPaise)} int.",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(Spacing.md))
                        Button(
                            onClick = { selectedLoan?.let { onApplyPrepayment(it, PrepaymentStrategy.REDUCE_EMI) } },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Apply Strategy 2 (Reduce Monthly EMI)")
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(Spacing.listBottomPadding))
        }
    }
}
