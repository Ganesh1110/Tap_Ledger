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
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import com.tapledger.ui.charts.model.CategoryDonutSlice
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

/**
 * Render primitive for V6 Category Donut chart per Flow.md §17.2 V6.
 * Multi-slice donut chart using D2 percentages. Supports slice selection on tap.
 */
@Composable
fun DonutChartRender(
    slices: List<CategoryDonutSlice>,
    modifier: Modifier = Modifier,
    diameter: Dp = 180.dp,
    strokeWidth: Dp = 22.dp,
    selectedCategoryId: Long? = null,
    onSliceClick: ((CategoryDonutSlice) -> Unit)? = null,
    centerContent: @Composable () -> Unit = {}
) {
    Box(
        modifier = modifier.size(diameter),
        contentAlignment = Alignment.Center
    ) {
        Canvas(
            modifier = Modifier
                .size(diameter)
                .then(
                    if (onSliceClick != null && slices.isNotEmpty()) {
                        Modifier.pointerInput(slices) {
                            detectTapGestures { offset ->
                                val centerX = size.width.toFloat() / 2f
                                val centerY = size.height.toFloat() / 2f
                                val dx = offset.x - centerX
                                val dy = offset.y - centerY
                                var angleDeg = (kotlin.math.atan2(dy, dx) * 180f / Math.PI.toFloat())
                                if (angleDeg < -90f) angleDeg += 360f

                                var currentAngle = -90f
                                for (slice in slices) {
                                    val sweep = (slice.percentageBp.toFloat() / 10_000f) * 360f
                                    if (angleDeg >= currentAngle && angleDeg <= (currentAngle + sweep)) {
                                        onSliceClick(slice)
                                        break
                                    }
                                    currentAngle += sweep
                                }
                            }
                        }
                    } else Modifier
                )
        ) {
            val strokePx = strokeWidth.toPx()
            val arcSize = Size(size.width - strokePx, size.height - strokePx)
            val topLeft = Offset(strokePx / 2f, strokePx / 2f)

            if (slices.isEmpty()) {
                // Empty track
                drawArc(
                    color = Color.LightGray.copy(alpha = 0.3f),
                    startAngle = -90f,
                    sweepAngle = 360f,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokePx, cap = StrokeCap.Round)
                )
            } else {
                var startAngle = -90f
                slices.forEachIndexed { index, slice ->
                    val sweepAngle = (slice.percentageBp.toFloat() / 10_000f) * 360f
                    val sliceColor = ChartPalette.sliceColor(index)
                    val isSelected = selectedCategoryId == slice.categoryId
                    val currentStroke = if (isSelected) strokePx + 4.dp.toPx() else strokePx

                    if (sweepAngle > 0f) {
                        drawArc(
                            color = sliceColor,
                            startAngle = startAngle,
                            sweepAngle = sweepAngle,
                            useCenter = false,
                            topLeft = topLeft,
                            size = arcSize,
                            style = Stroke(width = currentStroke, cap = StrokeCap.Butt)
                        )
                    }
                    startAngle += sweepAngle
                }
            }
        }

        centerContent()
    }
}
