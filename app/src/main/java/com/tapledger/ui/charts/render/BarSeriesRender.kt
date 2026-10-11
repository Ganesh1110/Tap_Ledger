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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.tapledger.ui.theme.ChartPalette

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
    advPosPaise: Long = 0L, // carried-in surplus (striped)
    advNegPaise: Long = 0L, // applied deficit (shaded)
    barColor: Color,
    trackColor: Color,
    modifier: Modifier = Modifier,
    height: Dp = 20.dp,
    isExceeded: Boolean = false,
    isGoalAchieved: Boolean = false
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

        // 2. Spent Bar (capped visually at adjusted budget if exceeded, overflow drawn separately per D8)
        val cappedSpent = if (isExceeded) adjustedBudgetPaise else spentPaise
        val spentFraction = (cappedSpent.coerceAtLeast(0L).toFloat() / totalCapacity.toFloat()).coerceIn(0f, 1f)
        val spentWidth = widthPx * spentFraction
        if (spentWidth > 0f) {
            drawRoundRect(
                color = barColor,
                topLeft = Offset(0f, 0f),
                size = Size(spentWidth, heightPx),
                cornerRadius = cornerRadius
            )
        }

        // 3. D8 Hatched Overflow Segment when isExceeded
        if (isExceeded && spentPaise > adjustedBudgetPaise) {
            val overflowPaise = spentPaise - adjustedBudgetPaise
            val overflowFraction = (overflowPaise.toFloat() / totalCapacity.toFloat()).coerceIn(0f, 1f)
            val overflowWidth = widthPx * overflowFraction
            if (overflowWidth > 0f) {
                drawRoundRect(
                    color = ChartPalette.OverflowHatch.copy(alpha = 0.4f),
                    topLeft = Offset(spentWidth, 0f),
                    size = Size(overflowWidth, heightPx),
                    cornerRadius = cornerRadius
                )
                drawRoundRect(
                    color = ChartPalette.OverflowHatch,
                    topLeft = Offset(spentWidth, 0f),
                    size = Size(overflowWidth, heightPx),
                    cornerRadius = cornerRadius,
                    style = Stroke(
                        width = 1.5.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 4f), 0f)
                    )
                )
            }
        }

        // 4. Carried-in segment (AdvPos, striped)
        if (advPosPaise > 0L) {
            val baseFraction = (baseAllocationPaise.coerceAtLeast(0L).toFloat() / totalCapacity.toFloat()).coerceIn(0f, 1f)
            val advPosFraction = (advPosPaise.toFloat() / totalCapacity.toFloat()).coerceIn(0f, 1f)
            val startX = widthPx * baseFraction
            val advWidth = widthPx * advPosFraction
            if (advWidth > 0f) {
                drawRoundRect(
                    color = Color.Green.copy(alpha = 0.25f),
                    topLeft = Offset(startX, 0f),
                    size = Size(advWidth, heightPx),
                    style = Stroke(width = 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 4f), 0f))
                )
            }
        }

        // 5. Applied deficit segment (AdvNeg, shaded)
        if (advNegPaise > 0L) {
            val adjFraction = (adjustedBudgetPaise.coerceAtLeast(0L).toFloat() / totalCapacity.toFloat()).coerceIn(0f, 1f)
            val advNegFraction = (advNegPaise.toFloat() / totalCapacity.toFloat()).coerceIn(0f, 1f)
            val startX = widthPx * adjFraction
            val advWidth = widthPx * advNegFraction
            if (advWidth > 0f) {
                drawRoundRect(
                    color = Color.Red.copy(alpha = 0.25f),
                    topLeft = Offset(startX, 0f),
                    size = Size(advWidth, heightPx)
                )
            }
        }

        // 6. Base Marker (vertical line at BaseAllocation)
        val baseFraction = (baseAllocationPaise.coerceAtLeast(0L).toFloat() / totalCapacity.toFloat()).coerceIn(0f, 1f)
        val baseX = widthPx * baseFraction
        drawLine(
            color = Color.Black.copy(alpha = 0.7f),
            start = Offset(baseX, 0f),
            end = Offset(baseX, heightPx),
            strokeWidth = 2.dp.toPx()
        )

        // 7. Budget Cap Line (at adjustedBudgetPaise)
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

/**
 * Render primitive for V4 Six-Period Trend clustered bars per Flow.md §17.2 V4.
 * Clustered bars for actual income, spent, and saved, plus a savings rate line.
 * The current OPEN period is rendered with hatching / dashed styling and labeled "In progress".
 */
@Composable
fun ClusteredBarChartRender(
    periods: List<com.tapledger.ui.charts.model.PeriodTrendBar>,
    modifier: Modifier = Modifier,
    height: Dp = 180.dp,
    incomeColor: Color = Color(0xFF009E73),
    spentColor: Color = Color(0xFFD55E00),
    savedColor: Color = Color(0xFF0072B2),
    savingsLineColor: Color = Color(0xFFE69F00)
) {
    if (periods.isEmpty()) return

    Canvas(modifier = modifier.fillMaxWidth().height(height)) {
        val widthPx = size.width
        val heightPx = size.height
        val labelMarginPx = 24.dp.toPx()
        val chartHeightPx = heightPx - labelMarginPx

        val maxPaise = periods.maxOfOrNull {
            maxOf(it.incomePaise, it.spentPaise, it.savedPaise)
        }?.coerceAtLeast(1L) ?: 1L

        val n = periods.size
        val clusterWidth = widthPx / n.toFloat()
        val barWidth = (clusterWidth * 0.22f).coerceAtMost(16.dp.toPx()).coerceAtLeast(4.dp.toPx())
        val cornerRadius = CornerRadius(3.dp.toPx(), 3.dp.toPx())

        // Draw horizontal zero baseline
        drawLine(
            color = Color.Gray.copy(alpha = 0.3f),
            start = Offset(0f, chartHeightPx),
            end = Offset(widthPx, chartHeightPx),
            strokeWidth = 1.dp.toPx()
        )

        val savingsPoints = mutableListOf<Offset>()

        periods.forEachIndexed { i, p ->
            val clusterCenterX = (i + 0.5f) * clusterWidth

            val incomeH = (p.incomePaise.coerceAtLeast(0L).toFloat() / maxPaise.toFloat()) * chartHeightPx
            val spentH = (p.spentPaise.coerceAtLeast(0L).toFloat() / maxPaise.toFloat()) * chartHeightPx
            val savedH = (p.savedPaise.coerceAtLeast(0L).toFloat() / maxPaise.toFloat()) * chartHeightPx

            val xIncome = clusterCenterX - barWidth * 1.5f - 2.dp.toPx()
            val xSpent = clusterCenterX - barWidth * 0.5f
            val xSaved = clusterCenterX + barWidth * 0.5f + 2.dp.toPx()

            // Bar drawing helper
            fun drawBar(x: Float, h: Float, color: Color) {
                if (h <= 0f) return
                val topY = chartHeightPx - h
                if (p.isInProgress) {
                    // Hatched / dashed stroke for open period in progress (§17.2 V4)
                    drawRoundRect(
                        color = color.copy(alpha = 0.4f),
                        topLeft = Offset(x, topY),
                        size = Size(barWidth, h),
                        cornerRadius = cornerRadius
                    )
                    drawRoundRect(
                        color = color,
                        topLeft = Offset(x, topY),
                        size = Size(barWidth, h),
                        cornerRadius = cornerRadius,
                        style = Stroke(
                            width = 1.5.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 4f), 0f)
                        )
                    )
                } else {
                    drawRoundRect(
                        color = color,
                        topLeft = Offset(x, topY),
                        size = Size(barWidth, h),
                        cornerRadius = cornerRadius
                    )
                }
            }

            drawBar(xIncome, incomeH, incomeColor)
            drawBar(xSpent, spentH, spentColor)
            drawBar(xSaved, savedH, savedColor)

            // Savings rate point (0..10,000 bp = 0..100%)
            p.savingsRateBp?.let { bp ->
                val rateFraction = (bp.toFloat() / 10_000f).coerceIn(0f, 1f)
                val ptY = chartHeightPx - (rateFraction * (chartHeightPx * 0.9f))
                savingsPoints.add(Offset(clusterCenterX, ptY))
            }
        }

        // Draw Savings Rate Line connecting points
        if (savingsPoints.size > 1) {
            val linePath = androidx.compose.ui.graphics.Path().apply {
                moveTo(savingsPoints[0].x, savingsPoints[0].y)
                for (j in 1 until savingsPoints.size) {
                    lineTo(savingsPoints[j].x, savingsPoints[j].y)
                }
            }
            drawPath(
                path = linePath,
                color = savingsLineColor,
                style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
            )
        }
        savingsPoints.forEach { pt ->
            drawCircle(
                color = savingsLineColor,
                radius = 3.5.dp.toPx(),
                center = pt
            )
        }
    }
}
