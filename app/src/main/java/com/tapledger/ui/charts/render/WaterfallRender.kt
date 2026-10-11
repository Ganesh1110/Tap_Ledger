package com.tapledger.ui.charts.render

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

data class WaterfallStep(
    val label: String,
    val deltaPaise: Long, // positive (increase), negative (decrease), or 0
    val runningTotalPaise: Long,
    val color: Color,
    val isTotal: Boolean = false
)

/**
 * Render primitive for Waterfall charts per Flow.md §17.2 V12.
 */
@Composable
fun WaterfallRender(
    steps: List<WaterfallStep>,
    modifier: Modifier = Modifier,
    height: Dp = 220.dp
) {
    if (steps.isEmpty()) return

    Canvas(modifier = modifier.fillMaxWidth().height(height)) {
        val widthPx = size.width
        val heightPx = size.height

        val totals = steps.map { it.runningTotalPaise } + steps.map { it.runningTotalPaise - it.deltaPaise } + 0L
        val minVal = totals.minOrNull() ?: 0L
        val maxVal = maxOf(totals.maxOrNull() ?: 1L, 1L)
        val range = (maxVal - minVal).coerceAtLeast(1L).toFloat()

        fun toY(value: Long): Float {
            val fraction = (value - minVal).toFloat() / range
            return heightPx - (fraction * heightPx)
        }

        val stepWidth = widthPx / steps.size.toFloat()
        val barWidth = stepWidth * 0.7f
        val barMargin = stepWidth * 0.15f

        steps.forEachIndexed { i, step ->
            val x = i * stepWidth + barMargin
            val (topVal, bottomVal) = if (step.isTotal) {
                Pair(maxOf(step.runningTotalPaise, 0L), minOf(step.runningTotalPaise, 0L))
            } else {
                val prev = step.runningTotalPaise - step.deltaPaise
                Pair(maxOf(prev, step.runningTotalPaise), minOf(prev, step.runningTotalPaise))
            }

            val topY = toY(topVal)
            val bottomY = toY(bottomVal)
            val barHeight = (bottomY - topY).coerceAtLeast(2.dp.toPx())

            drawRoundRect(
                color = step.color,
                topLeft = Offset(x, topY),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(3.dp.toPx(), 3.dp.toPx())
            )

            // Connector dashed line to next step
            if (i < steps.size - 1 && !step.isTotal) {
                val nextX = (i + 1) * stepWidth + barMargin
                val connY = toY(step.runningTotalPaise)
                drawLine(
                    color = Color.Gray.copy(alpha = 0.5f),
                    start = Offset(x + barWidth, connY),
                    end = Offset(nextX, connY),
                    strokeWidth = 1.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 4f), 0f)
                )
            }
        }
    }
}
