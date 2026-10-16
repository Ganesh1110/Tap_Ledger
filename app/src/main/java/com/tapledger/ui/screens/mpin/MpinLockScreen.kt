package com.tapledger.ui.screens.mpin

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tapledger.core.model.AppMode
import com.tapledger.core.security.MpinLockoutState
import com.tapledger.core.security.MpinManager
import com.tapledger.core.security.MpinVerifyResult
import com.tapledger.ui.theme.Spacing
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

@Composable
fun MpinLockScreen(
    mpinManager: MpinManager,
    onUnlocked: () -> Unit,
    modifier: Modifier = Modifier,
    customMessage: String? = null,
    initialAppMode: AppMode = AppMode.MANAGER,
    onUnlockedWithMode: (AppMode) -> Unit = {},
    onNavigateToRestoreBackup: () -> Unit = {},
    onEraseAllData: () -> Unit = {}
) {
    val coroutineScope = rememberCoroutineScope()
    val expectedLength by mpinManager.mpinLength.collectAsState()
    val lockoutState by mpinManager.lockoutState.collectAsState()

    var selectedMode by remember { mutableStateOf(initialAppMode) }
    var enteredPin by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var shakeTrigger by remember { mutableIntStateOf(0) }
    var showForgotDialog by remember { mutableStateOf(false) }
    var showConfirmEraseDialog by remember { mutableStateOf(false) }

    // Live countdown timer state
    var countdownSeconds by remember { mutableLongStateOf(lockoutState.remainingLockoutSeconds) }

    LaunchedEffect(lockoutState.isLockedOut, lockoutState.remainingLockoutSeconds) {
        countdownSeconds = mpinManager.getLockoutCountdownSeconds()
        while (countdownSeconds > 0) {
            delay(1000)
            countdownSeconds = mpinManager.getLockoutCountdownSeconds()
        }
        if (countdownSeconds <= 0L && errorMessage?.contains("Locked out") == true) {
            errorMessage = null
        }
    }

    val isLockedOut = countdownSeconds > 0

    fun submitPin(pinToVerify: String) {
        coroutineScope.launch {
            when (val result = mpinManager.verify(pinToVerify)) {
                is MpinVerifyResult.Success -> {
                    errorMessage = null
                    enteredPin = ""
                    onUnlockedWithMode(selectedMode)
                    onUnlocked()
                }
                is MpinVerifyResult.LockedOut -> {
                    shakeTrigger++
                    enteredPin = ""
                    countdownSeconds = result.lockoutSeconds
                    errorMessage = "Too many failed attempts. Try again in %02d:%02d".format(
                        Locale.US,
                        countdownSeconds / 60,
                        countdownSeconds % 60
                    )
                }
                is MpinVerifyResult.WrongMpin -> {
                    shakeTrigger++
                    enteredPin = ""
                    if (result.lockoutSeconds > 0) {
                        countdownSeconds = result.lockoutSeconds
                        errorMessage = "Too many failed attempts. Try again in %02d:%02d".format(
                            Locale.US,
                            countdownSeconds / 60,
                            countdownSeconds % 60
                        )
                    } else if (result.attemptsRemainingBeforeLockout != null) {
                        errorMessage = "Incorrect MPIN. ${result.attemptsRemainingBeforeLockout} attempt${if (result.attemptsRemainingBeforeLockout > 1) "s" else ""} remaining before lockout."
                    } else {
                        errorMessage = "Incorrect MPIN. Please try again."
                    }
                }
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(Spacing.lg),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(Spacing.md))

            Text(
                text = "TapLedger",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )

            Spacer(modifier = Modifier.height(Spacing.xs))

            Text(
                text = customMessage ?: "Enter your MPIN to unlock",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(Spacing.md))

            // Mode Selector: Manager vs Saving (Flow.md §18, D012)
            TabRow(
                selectedTabIndex = if (selectedMode == AppMode.MANAGER) 0 else 1,
                modifier = Modifier
                    .fillMaxWidth(0.9f)
                    .padding(bottom = Spacing.md),
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = MaterialTheme.colorScheme.primary
            ) {
                Tab(
                    selected = selectedMode == AppMode.MANAGER,
                    onClick = { selectedMode = AppMode.MANAGER },
                    text = { Text("Manager", fontWeight = if (selectedMode == AppMode.MANAGER) FontWeight.Bold else FontWeight.Normal) },
                    icon = { Icon(Icons.Default.AccountBalanceWallet, contentDescription = "Manager Mode") }
                )
                Tab(
                    selected = selectedMode == AppMode.SAVING,
                    onClick = { selectedMode = AppMode.SAVING },
                    text = { Text("Saving", fontWeight = if (selectedMode == AppMode.SAVING) FontWeight.Bold else FontWeight.Normal) },
                    icon = { Icon(Icons.Default.Savings, contentDescription = "Saving Mode") }
                )
            }

            Spacer(modifier = Modifier.height(Spacing.md))

            // PIN Dots Indicator
            MpinDots(
                enteredCount = enteredPin.length,
                totalCount = expectedLength,
                shakeTrigger = shakeTrigger,
                isError = errorMessage != null
            )

            Spacer(modifier = Modifier.height(Spacing.md))

            // Error or Lockout message
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp),
                contentAlignment = Alignment.Center
            ) {
                if (isLockedOut) {
                    Text(
                        text = "Locked out. Try again in %02d:%02d".format(
                            Locale.US,
                            countdownSeconds / 60,
                            countdownSeconds % 60
                        ),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center
                    )
                } else if (errorMessage != null) {
                    Text(
                        text = errorMessage!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center
                    )
                }
            }

            Spacer(modifier = Modifier.height(Spacing.lg))

            // Keypad
            MpinKeypad(
                enabled = !isLockedOut,
                onDigitClick = { digit ->
                    if (enteredPin.length < expectedLength) {
                        errorMessage = null
                        val updated = enteredPin + digit
                        enteredPin = updated
                        if (updated.length == expectedLength) {
                            submitPin(updated)
                        }
                    }
                },
                onBackspaceClick = {
                    if (enteredPin.isNotEmpty()) {
                        enteredPin = enteredPin.dropLast(1)
                        errorMessage = null
                    }
                }
            )

            Spacer(modifier = Modifier.height(Spacing.xl))

            TextButton(onClick = { showForgotDialog = true }) {
                Text(
                    text = "Forgot MPIN?",
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }

    // Forgot MPIN Information Modal
    if (showForgotDialog) {
        AlertDialog(
            onDismissRequest = { showForgotDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            },
            title = {
                Text(
                    text = "Forgot MPIN?",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    Text(
                        text = "TapLedger is 100% offline and stores no cryptographic keys on external servers. There is no backdoor or online password reset.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "To recover access to your financial records:",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "1. Restore from an encrypted backup file (requires your backup password).\n2. Erase all data and restart setup from scratch.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showForgotDialog = false
                        onNavigateToRestoreBackup()
                    }
                ) {
                    Text("Restore Backup")
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = {
                        showForgotDialog = false
                        showConfirmEraseDialog = true
                    },
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Erase Data")
                }
            }
        )
    }

    // Confirm Erase Modal
    if (showConfirmEraseDialog) {
        AlertDialog(
            onDismissRequest = { showConfirmEraseDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
            },
            title = {
                Text(
                    text = "Erase All Data?",
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.error
                )
            },
            text = {
                Text(
                    text = "This will permanently wipe all accounts, transactions, envelopes, and reset the app. This cannot be undone.",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showConfirmEraseDialog = false
                        onEraseAllData()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                ) {
                    Text("Permanently Erase")
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmEraseDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
