package com.tapledger.ui.charts.render

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Linear progress bar render primitive accepting integer basis points (bp, 10,000 = 100%).
 * Converts to Float at the last step inside this render package.
 */
@Composable
fun ProgressBarRender(
    percentageBp: Int,
    color: Color,
    trackColor: Color,
    modifier: Modifier = Modifier,
    height: Dp = 8.dp
) {
    val progress = (percentageBp.coerceIn(0, 10_000).toFloat() / 10_000f)
    LinearProgressIndicator(
        progress = { progress },
        modifier = modifier.fillMaxWidth().height(height),
        color = color,
        trackColor = trackColor,
        strokeCap = StrokeCap.Round
    )
}
