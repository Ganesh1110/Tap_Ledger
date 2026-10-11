package com.tapledger.ui.charts.render

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
import androidx.compose.runtime.remember
import com.tapledger.ui.theme.MotionSpec

val positionSpring: AnimationSpec<Float> = spring(
    dampingRatio = 0.8f,
    stiffness = Spring.StiffnessMediumLow
)

val enterTween: AnimationSpec<Float> = tween(
    durationMillis = MotionSpec.EnterDurationMillis,
    easing = FastOutSlowInEasing
)

val exitTween: AnimationSpec<Float> = tween(
    durationMillis = MotionSpec.ExitDurationMillis,
    easing = FastOutSlowInEasing
)

val contentFadeTween: AnimationSpec<Float> = tween(
    durationMillis = 200,
    easing = LinearOutSlowInEasing
)

@Composable
fun rememberAnimatedProgress(targetFraction: Float): State<Float> {
    val state = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(targetFraction) {
        if (!MotionSpec.areAnimationsEnabled()) {
            state.floatValue = targetFraction
            return@LaunchedEffect
        }
        val anim = Animatable(state.floatValue)
        anim.animateTo(targetFraction, animationSpec = tween(durationMillis = 500, easing = FastOutSlowInEasing))
        state.floatValue = anim.value
    }
    return state
}
