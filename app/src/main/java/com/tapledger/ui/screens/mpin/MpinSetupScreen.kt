package com.tapledger.ui.screens.mpin

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import com.tapledger.core.security.MpinManager
import com.tapledger.core.security.MpinValidationResult
import com.tapledger.core.security.MpinValidator
import com.tapledger.ui.theme.IncomeGreen
import com.tapledger.ui.theme.Spacing
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class SetupStep {
    ENTER_FIRST,
    CONFIRM,
    SUCCESS
}

@Composable
fun MpinSetupScreen(
    mpinManager: MpinManager,
    onSetupSuccess: () -> Unit,
    modifier: Modifier = Modifier,
    canSkip: Boolean = false,
    onSkip: () -> Unit = {}
) {
    val coroutineScope = rememberCoroutineScope()

    var selectedLength by remember { mutableIntStateOf(4) }
    var currentStep by remember { mutableStateOf(SetupStep.ENTER_FIRST) }
    var firstPin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var shakeTrigger by remember { mutableIntStateOf(0) }

    val currentEntered = if (currentStep == SetupStep.ENTER_FIRST) firstPin else confirmPin

    fun handleDigit(digit: Char) {
        if (currentStep == SetupStep.ENTER_FIRST) {
            if (firstPin.length < selectedLength) {
                errorMessage = null
                val updated = firstPin + digit
                firstPin = updated
                if (updated.length == selectedLength) {
                    when (val valResult = MpinValidator.validate(updated)) {
                        is MpinValidationResult.Invalid -> {
                            shakeTrigger++
                            firstPin = ""
                            errorMessage = valResult.message
                        }
                        is MpinValidationResult.Valid -> {
                            currentStep = SetupStep.CONFIRM
                            errorMessage = null
                        }
                    }
                }
            }
        } else if (currentStep == SetupStep.CONFIRM) {
            if (confirmPin.length < selectedLength) {
                errorMessage = null
                val updated = confirmPin + digit
                confirmPin = updated
                if (updated.length == selectedLength) {
                    if (updated == firstPin) {
                        coroutineScope.launch {
                            val setResult = mpinManager.setMpin(firstPin, updated)
                            if (setResult.isSuccess) {
                                currentStep = SetupStep.SUCCESS
                                delay(900)
                                onSetupSuccess()
                            } else {
                                shakeTrigger++
                                confirmPin = ""
                                firstPin = ""
                                currentStep = SetupStep.ENTER_FIRST
                                errorMessage = setResult.exceptionOrNull()?.message ?: "Failed to set MPIN"
                            }
                        }
                    } else {
                        shakeTrigger++
                        confirmPin = ""
                        errorMessage = "MPINs do not match. Please re-enter."
                    }
                }
            }
        }
    }

    fun handleBackspace() {
        if (currentStep == SetupStep.ENTER_FIRST && firstPin.isNotEmpty()) {
            firstPin = firstPin.dropLast(1)
            errorMessage = null
        } else if (currentStep == SetupStep.CONFIRM) {
            if (confirmPin.isNotEmpty()) {
                confirmPin = confirmPin.dropLast(1)
                errorMessage = null
            } else {
                // If backspace at 0 digits in confirm step, return to enter step
                currentStep = SetupStep.ENTER_FIRST
                firstPin = ""
                errorMessage = null
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
        if (currentStep == SetupStep.SUCCESS) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    modifier = Modifier.size(72.dp),
                    tint = IncomeGreen
                )
                Spacer(modifier = Modifier.height(Spacing.md))
                Text(
                    text = "MPIN Setup Complete!",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(modifier = Modifier.height(Spacing.xs))
                Text(
                    text = "Your financial ledger is now secured.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Shield,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.primary
                )

                Spacer(modifier = Modifier.height(Spacing.md))

                Text(
                    text = if (currentStep == SetupStep.ENTER_FIRST) "Create MPIN" else "Confirm MPIN",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )

                Spacer(modifier = Modifier.height(Spacing.xs))

                Text(
                    text = if (currentStep == SetupStep.ENTER_FIRST) {
                        "Set a ${selectedLength}-digit security MPIN to protect your financial ledger"
                    } else {
                        "Re-enter your ${selectedLength}-digit MPIN to confirm"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(Spacing.sm))

                // Length toggle (4 or 6 digits) only visible during initial entry
                AnimatedVisibility(visible = currentStep == SetupStep.ENTER_FIRST) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                        modifier = Modifier.padding(vertical = Spacing.xs)
                    ) {
                        FilterChip(
                            selected = selectedLength == 4,
                            onClick = {
                                selectedLength = 4
                                firstPin = ""
                                errorMessage = null
                            },
                            label = { Text("4 Digits") }
                        )
                        FilterChip(
                            selected = selectedLength == 6,
                            onClick = {
                                selectedLength = 6
                                firstPin = ""
                                errorMessage = null
                            },
                            label = { Text("6 Digits") }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(Spacing.md))

                MpinDots(
                    enteredCount = currentEntered.length,
                    totalCount = selectedLength,
                    shakeTrigger = shakeTrigger,
                    isError = errorMessage != null
                )

                Spacer(modifier = Modifier.height(Spacing.md))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(36.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (errorMessage != null) {
                        Text(
                            text = errorMessage!!,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center
                        )
                    }
                }

                Spacer(modifier = Modifier.height(Spacing.md))

                MpinKeypad(
                    onDigitClick = ::handleDigit,
                    onBackspaceClick = ::handleBackspace
                )

                if (canSkip && currentStep == SetupStep.ENTER_FIRST) {
                    Spacer(modifier = Modifier.height(Spacing.lg))
                    TextButton(onClick = onSkip) {
                        Text(
                            text = "Skip for now",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
            }
        }
    }
}
