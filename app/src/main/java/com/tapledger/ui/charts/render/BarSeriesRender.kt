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
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

data class BarSegment(
    val valuePaise: Long,
    val color: Color,
    val isHatched: Boolean = false,
    val isDashed: Boolean = false
)

/**
 * Render primitive for horizontal bullet bars per Flow.md §17.2 V5.
 */
@Composable
fun BulletBarRender(
    spentPaise: Long,
    adjustedBudgetPaise: Long,
    baseAllocationPaise: Long,
    advPosPaise: Long, // carried-in surplus (striped)
    advNegPaise: Long, // applied deficit (shaded)
    barColor: Color,
    trackColor: Color,
    modifier: Modifier = Modifier,
    height: Dp = 24.dp
) {
    Canvas(modifier = modifier.fillMaxWidth().height(height)) {
        val totalCapacity = maxOf(spentPaise, adjustedBudgetPaise, baseAllocationPaise, 1L)
        val widthPx = size.width
        val heightPx = size.height
        val cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())

        // 1. Background Track
        drawRoundRect(
            color = trackColor,
            topLeft = Offset(0f, 0f),
            size = Size(widthPx, heightPx),
            cornerRadius = cornerRadius
        )

        // 2. Spent Bar
        val spentFraction = (spentPaise.coerceAtLeast(0L).toFloat() / totalCapacity.toFloat()).coerceIn(0f, 1f)
        val spentWidth = widthPx * spentFraction
        if (spentWidth > 0f) {
            drawRoundRect(
                color = barColor,
                topLeft = Offset(0f, 0f),
                size = Size(spentWidth, heightPx),
                cornerRadius = cornerRadius
            )
        }

        // 3. Base Marker (vertical line at BaseAllocation)
        val baseFraction = (baseAllocationPaise.coerceAtLeast(0L).toFloat() / totalCapacity.toFloat()).coerceIn(0f, 1f)
        val baseX = widthPx * baseFraction
        drawLine(
            color = Color.Black.copy(alpha = 0.6f),
            start = Offset(baseX, 0f),
            end = Offset(baseX, heightPx),
            strokeWidth = 2.dp.toPx()
        )

        // 4. Budget Cap Line (at adjustedBudgetPaise)
        val budgetFraction = (adjustedBudgetPaise.coerceAtLeast(0L).toFloat() / totalCapacity.toFloat()).coerceIn(0f, 1f)
        val budgetX = widthPx * budgetFraction
        drawLine(
            color = Color.DarkGray,
            start = Offset(budgetX, -2.dp.toPx()),
            end = Offset(budgetX, heightPx + 2.dp.toPx()),
            strokeWidth = 3.dp.toPx()
        )
    }
}
