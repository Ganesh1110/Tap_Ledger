package com.tapledger.ui.theme

import android.animation.ValueAnimator
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import com.tapledger.ui.charts.util.ChartMath

/**
 * Standard MotionSpec for TapLedger.
 * Governs transitions and integer-safe financial money counter per Flow.md §17.1 D7.
 * Strictly zero floating-point arithmetic (uses integer permille 0..1000).
 */
object MotionSpec {

    const val EnterDurationMillis = 220
    const val ExitDurationMillis = 180

    /**
     * Checks if system animator duration scale is set to 0 (accessibility / reduced motion).
     */
    fun areAnimationsEnabled(): Boolean {
        return try {
            ValueAnimator.areAnimatorsEnabled()
        } catch (e: Exception) {
            true
        }
    }
}

/**
 * D7 Money animation: interpolate Long as start + (end - start) * permille / 1000
 * Permille is an Int 0..1000. Never Float*Long.
 */
@Composable
fun rememberAnimatedMoney(targetPaise: Long): State<Long> {
    val state = remember { mutableLongStateOf(targetPaise) }

    LaunchedEffect(targetPaise) {
        if (!MotionSpec.areAnimationsEnabled()) {
            state.longValue = targetPaise
            return@LaunchedEffect
        }

        val startPaise = state.longValue
        if (startPaise == targetPaise) return@LaunchedEffect

        val animatable = Animatable(0, Int.VectorConverter)
        animatable.animateTo(
            targetValue = 1000,
            animationSpec = tween(
                durationMillis = 400,
                easing = FastOutSlowInEasing
            )
        ) {
            val permille = this.value
            state.longValue = ChartMath.interpolateMoney(startPaise, targetPaise, permille)
        }
        state.longValue = targetPaise
    }

    return state
}
