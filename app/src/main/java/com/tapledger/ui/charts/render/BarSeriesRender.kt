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

/**
 * Render primitive for V10 Account composition diverging bar per Flow.md §17.2 V10.
 * Left = Liabilities (debt), Center = zero baseline, Right = Assets.
 */
@Composable
fun DivergingBarRender(
    isAsset: Boolean,
    absBalancePaise: Long,
    maxAbsPaise: Long,
    modifier: Modifier = Modifier,
    height: Dp = 8.dp,
    assetColor: Color = Color(0xFF009E73),
    liabilityColor: Color = Color(0xFFD55E00),
    trackColor: Color = Color(0xFFE5E5EA)
) {
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
    ) {
        val widthPx = size.width
        val heightPx = size.height
        val centerX = widthPx / 2f
        val radius = CornerRadius(heightPx / 2f, heightPx / 2f)

        // Draw background track
        drawRoundRect(
            color = trackColor.copy(alpha = 0.5f),
            size = Size(widthPx, heightPx),
            cornerRadius = radius
        )

        // Draw center zero divider
        drawLine(
            color = Color.Gray.copy(alpha = 0.6f),
            start = Offset(centerX, 0f),
            end = Offset(centerX, heightPx),
            strokeWidth = 1.5.dp.toPx()
        )

        if (maxAbsPaise > 0L && absBalancePaise > 0L) {
            val maxHalfWidth = centerX - 2.dp.toPx()
            val barW = ((absBalancePaise.toFloat() / maxAbsPaise.toFloat()) * maxHalfWidth).coerceIn(2.dp.toPx(), maxHalfWidth)

            if (isAsset) {
                // Right side
                drawRoundRect(
                    color = assetColor,
                    topLeft = Offset(centerX, 0f),
                    size = Size(barW, heightPx),
                    cornerRadius = radius
                )
            } else {
                // Left side
                drawRoundRect(
                    color = liabilityColor,
                    topLeft = Offset(centerX - barW, 0f),
                    size = Size(barW, heightPx),
                    cornerRadius = radius
                )
            }
        }
    }
}

/**
 * Render primitive for V10 Credit Card utilization gauge per Flow.md §17.2 V10.
 * Visual zones: NORMAL (<30%), CAUTION (30-70%), HIGH (>70%).
 * Supports D8 hatched overflow segment if utilizationBp > 10,000 (over limit).
 */
@Composable
fun CreditCardGaugeRender(
    utilizationBp: Int,
    zone: com.tapledger.ui.charts.model.UtilizationZone,
    modifier: Modifier = Modifier,
    height: Dp = 8.dp,
    trackColor: Color = Color(0xFFE5E5EA)
) {
    val zoneColor = when (zone) {
        com.tapledger.ui.charts.model.UtilizationZone.NORMAL -> Color(0xFF009E73)
        com.tapledger.ui.charts.model.UtilizationZone.CAUTION -> Color(0xFFE69F00)
        com.tapledger.ui.charts.model.UtilizationZone.HIGH -> Color(0xFFBA1A1A)
    }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
    ) {
        val widthPx = size.width
        val heightPx = size.height
        val radius = CornerRadius(heightPx / 2f, heightPx / 2f)

        // 1. Background track
        drawRoundRect(
            color = trackColor.copy(alpha = 0.5f),
            size = Size(widthPx, heightPx),
            cornerRadius = radius
        )

        // 2. Zone threshold markers at 30% and 70%
        val x30 = widthPx * 0.30f
        val x70 = widthPx * 0.70f
        drawLine(
            color = Color.Gray.copy(alpha = 0.4f),
            start = Offset(x30, 0f),
            end = Offset(x30, heightPx),
            strokeWidth = 1.dp.toPx()
        )
        drawLine(
            color = Color.Gray.copy(alpha = 0.4f),
            start = Offset(x70, 0f),
            end = Offset(x70, heightPx),
            strokeWidth = 1.dp.toPx()
        )

        // 3. Filled utilization progress
        if (utilizationBp > 0) {
            val fillFraction = (minOf(utilizationBp, 10_000).toFloat() / 10_000f)
            val fillW = widthPx * fillFraction
            drawRoundRect(
                color = zoneColor,
                size = Size(fillW, heightPx),
                cornerRadius = radius
            )

            // D8 Hatched overflow segment if over limit (> 10,000 bp)
            if (utilizationBp > 10_000) {
                drawRoundRect(
                    color = ChartPalette.OverflowHatch,
                    size = Size(widthPx, heightPx),
                    cornerRadius = radius,
                    style = Stroke(
                        width = 1.5.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 4f), 0f)
                    )
                )
            }
        }
    }
}

/**
 * Render primitive for V11 Advance Rollover diverging bar per Flow.md §17.2 V11.
 * Left = Deficits (applied deficit solid, uncovered pool hatched),
 * Right = Positive Rollovers (carry solid, sweep).
 */
@Composable
fun AdvanceRolloverBarRender(
    carryPaise: Long,
    sweepPaise: Long,
    appliedDeficitPaise: Long,
    uncoveredPoolPaise: Long,
    maxPaise: Long,
    modifier: Modifier = Modifier,
    height: Dp = 10.dp,
    carryColor: Color = Color(0xFF009E73),
    deficitColor: Color = Color(0xFFD55E00),
    poolColor: Color = ChartPalette.OverflowHatch,
    trackColor: Color = Color(0xFFE5E5EA)
) {
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
    ) {
        val widthPx = size.width
        val heightPx = size.height
        val centerX = widthPx / 2f
        val radius = CornerRadius(heightPx / 2f, heightPx / 2f)

        // Background track
        drawRoundRect(
            color = trackColor.copy(alpha = 0.5f),
            size = Size(widthPx, heightPx),
            cornerRadius = radius
        )

        // Center zero line
        drawLine(
            color = Color.Gray.copy(alpha = 0.6f),
            start = Offset(centerX, 0f),
            end = Offset(centerX, heightPx),
            strokeWidth = 1.5.dp.toPx()
        )

        val maxSafe = maxOf(maxPaise, 1L)
        val maxHalfWidth = centerX - 2.dp.toPx()

        // 1. Right side: Carry / positive surplus
        val rightVal = carryPaise + sweepPaise
        if (rightVal > 0L) {
            val rightFraction = (rightVal.toFloat() / maxSafe.toFloat()).coerceIn(0f, 1f)
            val rightW = (rightFraction * maxHalfWidth).coerceIn(2.dp.toPx(), maxHalfWidth)
            drawRoundRect(
                color = carryColor,
                topLeft = Offset(centerX, 0f),
                size = Size(rightW, heightPx),
                cornerRadius = radius
            )
        }

        // 2. Left side: Deficits (applied deficit solid + uncovered pool hatched)
        val deficitW = if (appliedDeficitPaise > 0L) {
            val frac = (appliedDeficitPaise.toFloat() / maxSafe.toFloat()).coerceIn(0f, 1f)
            (frac * maxHalfWidth).coerceIn(2.dp.toPx(), maxHalfWidth)
        } else 0f

        if (deficitW > 0f) {
            drawRoundRect(
                color = deficitColor,
                topLeft = Offset(centerX - deficitW, 0f),
                size = Size(deficitW, heightPx),
                cornerRadius = radius
            )
        }

        if (uncoveredPoolPaise > 0L) {
            val poolFrac = (uncoveredPoolPaise.toFloat() / maxSafe.toFloat()).coerceIn(0f, 1f)
            val poolW = (poolFrac * maxHalfWidth).coerceIn(2.dp.toPx(), (maxHalfWidth - deficitW).coerceAtLeast(0f))
            if (poolW > 0f) {
                val startX = centerX - deficitW - poolW
                drawRoundRect(
                    color = poolColor.copy(alpha = 0.4f),
                    topLeft = Offset(startX, 0f),
                    size = Size(poolW, heightPx),
                    cornerRadius = radius
                )
                drawRoundRect(
                    color = poolColor,
                    topLeft = Offset(startX, 0f),
                    size = Size(poolW, heightPx),
                    cornerRadius = radius,
                    style = Stroke(
                        width = 1.5.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 3f), 0f)
                    )
                )
            }
        }
    }
}

/**
 * Render primitive for V13 Diagnostics severity segmented bar per Flow.md §17.2 V13.
 * Draws segmented bar with CRITICAL (crimson), ERROR (vermilion), and WARNING (amber).
 */
@Composable
fun DiagnosticsSeverityBarRender(
    criticalCount: Int,
    errorCount: Int,
    warningCount: Int,
    modifier: Modifier = Modifier,
    height: Dp = 12.dp,
    criticalColor: Color = Color(0xFFBA1A1A),
    errorColor: Color = Color(0xFFD55E00),
    warningColor: Color = Color(0xFFE69F00),
    trackColor: Color = Color(0xFFE5E5EA)
) {
    val total = criticalCount + errorCount + warningCount
    if (total == 0) return

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
    ) {
        val widthPx = size.width
        val heightPx = size.height
        val radius = CornerRadius(heightPx / 2f, heightPx / 2f)

        // Draw track
        drawRoundRect(
            color = trackColor.copy(alpha = 0.5f),
            size = Size(widthPx, heightPx),
            cornerRadius = radius
        )

        val totalF = total.toFloat()
        var currentX = 0f

        if (criticalCount > 0) {
            val w = widthPx * (criticalCount.toFloat() / totalF)
            drawRoundRect(
                color = criticalColor,
                topLeft = Offset(currentX, 0f),
                size = Size(w, heightPx),
                cornerRadius = radius
            )
            currentX += w
        }

        if (errorCount > 0) {
            val w = widthPx * (errorCount.toFloat() / totalF)
            drawRoundRect(
                color = errorColor,
                topLeft = Offset(currentX, 0f),
                size = Size(w, heightPx),
                cornerRadius = radius
            )
            currentX += w
        }

        if (warningCount > 0) {
            val w = widthPx * (warningCount.toFloat() / totalF)
            drawRoundRect(
                color = warningColor,
                topLeft = Offset(currentX, 0f),
                size = Size(w, heightPx),
                cornerRadius = radius
            )
        }
    }
}


