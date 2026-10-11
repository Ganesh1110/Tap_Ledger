package com.tapledger.ui.charts.render

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.tapledger.ui.theme.ChartPalette
import kotlin.math.cos
import kotlin.math.sin

/**
 * Render primitive for Donut and Progress Rings per Flow.md §17.1 D8 and §17.2 V1, V3.
 * Floats are permitted strictly in this render package for Compose Canvas drawing.
 */
@Composable
fun RingChartRender(
    percentageUsedBp: Int, // basis points (10,000 = 100%)
    ringColor: Color,
    trackColor: Color,
    modifier: Modifier = Modifier,
    size: Dp = 160.dp,
    strokeWidth: Dp = 16.dp,
    paceBp: Int? = null, // basis points for linear pace tick marker
    centerContent: @Composable () -> Unit = {}
) {
    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(size)) {
            val strokePx = strokeWidth.toPx()
            val arcSize = Size(this.size.width - strokePx, this.size.height - strokePx)
            val topLeft = Offset(strokePx / 2f, strokePx / 2f)

            // 1. Background Track
            drawArc(
                color = trackColor,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokePx, cap = StrokeCap.Round)
            )

            // 2. Main Progress Arc (Capped at 100% / 10,000 bp visually per D8)
            val clampedBp = percentageUsedBp.coerceIn(0, 10_000)
            val sweepAngle = (clampedBp.toFloat() / 10_000f) * 360f

            if (sweepAngle > 0f) {
                drawArc(
                    color = ringColor,
                    startAngle = -90f,
                    sweepAngle = sweepAngle,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokePx, cap = StrokeCap.Round)
                )
            }

            // 3. D8 Hatched Overflow Arc for over 100% (Needs / Wants only)
            if (percentageUsedBp > 10_000) {
                val overflowBp = (percentageUsedBp - 10_000).coerceAtMost(5_000) // cap overflow visual
                val overflowSweep = (overflowBp.toFloat() / 10_000f) * 360f
                drawArc(
                    color = ChartPalette.OverflowHatch,
                    startAngle = -90f,
                    sweepAngle = overflowSweep,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(
                        width = strokePx,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f), 0f),
                        cap = StrokeCap.Butt
                    )
                )
            }

            // 4. D3 Linear Pace Marker Tick
            if (paceBp != null && paceBp in 1..10_000) {
                val paceAngleDeg = -90f + (paceBp.toFloat() / 10_000f) * 360f
                val paceAngleRad = paceAngleDeg * (Math.PI.toFloat() / 180f)

                val center = Offset(this.size.width / 2f, this.size.height / 2f)
                val radiusInner = (this.size.width - strokePx * 2f) / 2f
                val radiusOuter = this.size.width / 2f

                val p1 = Offset(
                    center.x + radiusInner * cos(paceAngleRad),
                    center.y + radiusInner * sin(paceAngleRad)
                )
                val p2 = Offset(
                    center.x + radiusOuter * cos(paceAngleRad),
                    center.y + radiusOuter * sin(paceAngleRad)
                )

                drawLine(
                    color = ChartPalette.LightPaceLine,
                    start = p1,
                    end = p2,
                    strokeWidth = 3.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }
        }

        centerContent()
    }
}
