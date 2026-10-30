package com.tapledger.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.tapledger.ui.theme.RadiantGlassBorder
import com.tapledger.ui.theme.RadiantGlassCardBackground

/**
 * Reusable Frosted Glass Card for Gradient Glass Light design language.
 *
 * Uses translucent gradient surfaces, crisp luminous edge highlights, and
 * soft diffusion to produce a frosted glass aesthetic in Jetpack Compose.
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(18.dp),
    backgroundBrush: Brush = RadiantGlassCardBackground,
    borderBrush: Brush = RadiantGlassBorder,
    borderWidth: Dp = 1.dp,
    elevation: Dp = 2.dp,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier
            .shadow(
                elevation = elevation,
                shape = shape,
                ambientColor = Color(0xFF6366F1).copy(alpha = 0.06f),
                spotColor = Color(0xFF0F172A).copy(alpha = 0.08f)
            )
            .clip(shape)
            .background(backgroundBrush)
            .border(
                border = BorderStroke(borderWidth, borderBrush),
                shape = shape
            ),
        content = content
    )
}
