package com.tapledger.ui.theme

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween

object MotionSpec {
    val defaultSpring = spring<Float>(
        dampingRatio = 0.8f,
        stiffness = Spring.StiffnessMediumLow
    )

    val defaultSpringInt = spring<Int>(
        dampingRatio = 0.8f,
        stiffness = Spring.StiffnessMediumLow
    )

    val enterTween = tween<Float>(durationMillis = 220, easing = androidx.compose.animation.core.FastOutSlowInEasing)
    val exitTween = tween<Float>(durationMillis = 180, easing = androidx.compose.animation.core.FastOutSlowInEasing)
}
