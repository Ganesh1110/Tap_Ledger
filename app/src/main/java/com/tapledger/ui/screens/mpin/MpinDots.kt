package com.tapledger.ui.screens.mpin

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.keyframes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.tapledger.ui.theme.Spacing
import kotlin.math.roundToInt

@Composable
fun MpinDots(
    enteredCount: Int,
    totalCount: Int,
    modifier: Modifier = Modifier,
    shakeTrigger: Int = 0,
    isError: Boolean = false
) {
    val haptic = LocalHapticFeedback.current
    val shakeOffset = remember { Animatable(0f) }

    LaunchedEffect(shakeTrigger) {
        if (shakeTrigger > 0) {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            shakeOffset.animateTo(
                targetValue = 0f,
                animationSpec = keyframes {
                    durationMillis = 400
                    0f at 0
                    -24f at 50
                    24f at 100
                    -16f at 150
                    16f at 200
                    -8f at 250
                    8f at 300
                    0f at 400
                }
            )
        }
    }

    val errorColor = MaterialTheme.colorScheme.error
    val primaryColor = MaterialTheme.colorScheme.primary
    val outlineColor = MaterialTheme.colorScheme.outline

    Row(
        modifier = modifier.offset { IntOffset(shakeOffset.value.roundToInt(), 0) },
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        verticalAlignment = Alignment.CenterVertically
    ) {
        for (i in 0 until totalCount) {
            val isFilled = i < enteredCount
            val dotColor = when {
                isError -> errorColor
                isFilled -> primaryColor
                else -> outlineColor
            }

            if (isFilled) {
                Box(
                    modifier = Modifier
                        .size(18.dp)
                        .clip(CircleShape)
                        .background(dotColor)
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(18.dp)
                        .border(width = 2.dp, color = dotColor, shape = CircleShape)
                )
            }
        }
    }
}
