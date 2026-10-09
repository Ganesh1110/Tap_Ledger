package com.tapledger.ui.theme

import android.animation.ValueAnimator
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember

/**
 * Standard MotionSpec for tapLedger.
 * Governs all transitions, springs, tweens, and Long-safe financial interpolation.
 */
object MotionSpec {

    // Position and size transitions
    val positionSpring: AnimationSpec<Float> = spring(
        dampingRatio = 0.8f,
        stiffness = Spring.StiffnessMediumLow
    )

    val positionSpringInt: AnimationSpec<Int> = spring(
        dampingRatio = 0.8f,
        stiffness = Spring.StiffnessMediumLow
    )

    // Fades and scales
    const val EnterDurationMillis = 220
    const val ExitDurationMillis = 180

    val enterTween: AnimationSpec<Float> = tween(
        durationMillis = EnterDurationMillis,
        easing = FastOutSlowInEasing
    )

    val exitTween: AnimationSpec<Float> = tween(
        durationMillis = ExitDurationMillis,
        easing = FastOutSlowInEasing
    )

    val contentFadeTween: AnimationSpec<Float> = tween(
        durationMillis = 200,
        easing = LinearOutSlowInEasing
    )

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
 * Long-safe financial money counter.
 * Interpolates from previous to target amount using a progress fraction,
 * guaranteeing exact Long value at completion without floating-point financial drift.
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

        val animatable = Animatable(0f)
        animatable.animateTo(
            targetValue = 1f,
            animationSpec = tween(
                durationMillis = 400,
                easing = FastOutSlowInEasing
            )
        ) {
            val progress = this.value
            val delta = targetPaise - startPaise
            state.longValue = startPaise + (delta * progress).toLong()
        }
        // Guarantee final value equals target exactly
        state.longValue = targetPaise
    }

    return state
}

/**
 * Smoothly animates a progress fraction (0f to 1f) for budget and stat bars.
 */
@Composable
fun rememberAnimatedProgress(targetFraction: Float): State<Float> {
    val state = remember { mutableFloatStateOf(0f) }

    LaunchedEffect(targetFraction) {
        if (!MotionSpec.areAnimationsEnabled()) {
            state.floatValue = targetFraction
            return@LaunchedEffect
        }
        val animatable = Animatable(state.floatValue)
        animatable.animateTo(
            targetValue = targetFraction.coerceIn(0f, 1f),
            animationSpec = tween(
                durationMillis = 450,
                easing = FastOutSlowInEasing
            )
        ) {
            state.floatValue = this.value
        }
    }

    return state
}
