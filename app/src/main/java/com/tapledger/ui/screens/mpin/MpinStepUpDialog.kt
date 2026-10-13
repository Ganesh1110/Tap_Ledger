package com.tapledger.ui.screens.mpin

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tapledger.core.security.MpinManager
import com.tapledger.core.security.MpinVerifyResult
import com.tapledger.ui.theme.Spacing
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MpinStepUpDialog(
    mpinManager: MpinManager,
    title: String = "Security Verification",
    description: String = "Please enter your MPIN to authorize this action",
    onVerified: () -> Unit,
    onDismiss: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val expectedLength by mpinManager.mpinLength.collectAsState()

    var enteredPin by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var shakeTrigger by remember { mutableIntStateOf(0) }

    fun submitPin(pinToVerify: String) {
        coroutineScope.launch {
            when (val result = mpinManager.verify(pinToVerify)) {
                is MpinVerifyResult.Success -> {
                    onVerified()
                }
                is MpinVerifyResult.LockedOut -> {
                    shakeTrigger++
                    enteredPin = ""
                    errorMessage = "App is temporarily locked out."
                }
                is MpinVerifyResult.WrongMpin -> {
                    shakeTrigger++
                    enteredPin = ""
                    errorMessage = if (result.attemptsRemainingBeforeLockout != null) {
                        "Incorrect MPIN (${result.attemptsRemainingBeforeLockout} attempts remaining)"
                    } else {
                        "Incorrect MPIN"
                    }
                }
            }
        }
    }

    BasicAlertDialog(
        onDismissRequest = onDismiss
    ) {
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(24.dp))
                .background(MaterialTheme.colorScheme.surface)
                .padding(Spacing.lg)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = null,
                modifier = Modifier.size(36.dp),
                tint = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(Spacing.sm))

            Text(
                text = title,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(Spacing.xs))

            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(Spacing.lg))

            MpinDots(
                enteredCount = enteredPin.length,
                totalCount = expectedLength,
                shakeTrigger = shakeTrigger,
                isError = errorMessage != null
            )

            Spacer(modifier = Modifier.height(Spacing.sm))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(28.dp),
                contentAlignment = Alignment.Center
            ) {
                if (errorMessage != null) {
                    Text(
                        text = errorMessage!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center
                    )
                }
            }

            Spacer(modifier = Modifier.height(Spacing.sm))

            MpinKeypad(
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

            Spacer(modifier = Modifier.height(Spacing.md))

            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    }
}
