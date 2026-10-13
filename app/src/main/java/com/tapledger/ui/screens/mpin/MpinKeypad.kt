package com.tapledger.ui.screens.mpin

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tapledger.ui.theme.Spacing

@Composable
fun MpinKeypad(
    onDigitClick: (Char) -> Unit,
    onBackspaceClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val haptic = LocalHapticFeedback.current

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Rows: 1-3, 4-6, 7-9, [blank, 0, backspace]
        val rows = listOf(
            listOf('1', '2', '3'),
            listOf('4', '5', '6'),
            listOf('7', '8', '9')
        )

        for (row in rows) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                for (digit in row) {
                    KeypadDigitButton(
                        digit = digit,
                        enabled = enabled,
                        onClick = {
                            if (enabled) {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onDigitClick(digit)
                            }
                        }
                    )
                }
            }
        }

        // Bottom row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Empty space for balance
            Box(modifier = Modifier.size(72.dp))

            KeypadDigitButton(
                digit = '0',
                enabled = enabled,
                onClick = {
                    if (enabled) {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onDigitClick('0')
                    }
                }
            )

            KeypadIconButton(
                enabled = enabled,
                contentDescription = "Backspace",
                onClick = {
                    if (enabled) {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onBackspaceClick()
                    }
                }
            )
        }
    }
}

@Composable
private fun KeypadDigitButton(
    digit: Char,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }

    Box(
        modifier = Modifier
            .size(72.dp)
            .clip(CircleShape)
            .alpha(if (enabled) 1f else 0.38f)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(bounded = true, radius = 36.dp),
                enabled = enabled,
                onClick = onClick
            )
            .semantics {
                role = Role.Button
                contentDescription = "Digit $digit"
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = digit.toString(),
            fontSize = 26.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun KeypadIconButton(
    enabled: Boolean,
    contentDescription: String,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }

    Box(
        modifier = Modifier
            .size(72.dp)
            .clip(CircleShape)
            .alpha(if (enabled) 1f else 0.38f)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(bounded = true, radius = 36.dp),
                enabled = enabled,
                onClick = onClick
            )
            .semantics {
                role = Role.Button
                this.contentDescription = contentDescription
            },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.Backspace,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(24.dp)
        )
    }
}
