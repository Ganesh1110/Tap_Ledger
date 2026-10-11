package com.tapledger.ui.charts.render

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

data class ChartPoint(
    val xLabel: String,
    val valuePaise: Long,
    val secondaryValuePaise: Long? = null
)

/**
 * Render primitive for Line and Filled Area charts per Flow.md §17.2 V2, V9.
 * Supports dashed projections, gradient fills, and horizontal drag scrubbing.
 */
@Composable
fun LineAreaRender(
    points: List<ChartPoint>,
    lineColor: Color,
    areaGradientColor: Color? = null,
    modifier: Modifier = Modifier,
    height: Dp = 200.dp,
    projectionPoints: List<ChartPoint> = emptyList(),
    referenceCapPaise: Long? = null,
    onScrub: ((ChartPoint?) -> Unit)? = null
) {
    if (points.isEmpty()) return

    var scrubIndex by remember { mutableStateOf<Int?>(null) }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .pointerInput(points) {
                if (onScrub != null) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            val idx = ((offset.x / size.width.toFloat()) * (points.size - 1))
                                .toInt()
                                .coerceIn(0, points.size - 1)
                            scrubIndex = idx
                            onScrub(points[idx])
                        },
                        onDragEnd = {
                            scrubIndex = null
                            onScrub(null)
                        },
                        onDragCancel = {
                            scrubIndex = null
                            onScrub(null)
                        },
                        onDrag = { change, _ ->
                            val idx = ((change.position.x / size.width.toFloat()) * (points.size - 1))
                                .toInt()
                                .coerceIn(0, points.size - 1)
                            scrubIndex = idx
                            onScrub(points[idx])
                        }
                    )
                }
            }
    ) {
        val widthPx = size.width
        val heightPx = size.height

        val allValues = (points + projectionPoints).map { it.valuePaise } + listOfNotNull(referenceCapPaise, 0L)
        val minVal = allValues.minOrNull() ?: 0L
        val maxVal = maxOf(allValues.maxOrNull() ?: 1L, 1L)
        val valRange = (maxVal - minVal).coerceAtLeast(1L).toFloat()

        fun toX(index: Int, total: Int): Float {
            return if (total <= 1) widthPx / 2f else (index.toFloat() / (total - 1).toFloat()) * widthPx
        }

        fun toY(value: Long): Float {
            val fraction = (value - minVal).toFloat() / valRange
            return heightPx - (fraction * heightPx)
        }

        // 1. Draw Reference Cap Line if present
        if (referenceCapPaise != null) {
            val capY = toY(referenceCapPaise)
            drawLine(
                color = Color.Gray.copy(alpha = 0.5f),
                start = Offset(0f, capY),
                end = Offset(widthPx, capY),
                strokeWidth = 1.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
            )
        }

        // 2. Draw Area Gradient
        if (areaGradientColor != null && points.size > 1) {
            val areaPath = Path().apply {
                moveTo(toX(0, points.size), heightPx)
                points.forEachIndexed { i, pt ->
                    lineTo(toX(i, points.size), toY(pt.valuePaise))
                }
                lineTo(toX(points.size - 1, points.size), heightPx)
                close()
            }
            drawPath(
                path = areaPath,
                brush = Brush.verticalGradient(
                    colors = listOf(areaGradientColor.copy(alpha = 0.35f), Color.Transparent),
                    startY = 0f,
                    endY = heightPx
                )
            )
        }

        // 3. Draw Main Line
        if (points.size > 1) {
            val linePath = Path().apply {
                moveTo(toX(0, points.size), toY(points[0].valuePaise))
                for (i in 1 until points.size) {
                    lineTo(toX(i, points.size), toY(points[i].valuePaise))
                }
            }
            drawPath(
                path = linePath,
                color = lineColor,
                style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
            )
        }

        // 4. Draw D4 Dashed Projection Line
        if (projectionPoints.isNotEmpty() && points.isNotEmpty()) {
            val projPath = Path().apply {
                moveTo(toX(points.size - 1, points.size), toY(points.last().valuePaise))
                val totalCombined = points.size + projectionPoints.size - 1
                projectionPoints.forEachIndexed { i, pt ->
                    val combinedIdx = points.size + i
                    lineTo(toX(combinedIdx, totalCombined), toY(pt.valuePaise))
                }
            }
            drawPath(
                path = projPath,
                color = lineColor.copy(alpha = 0.7f),
                style = Stroke(
                    width = 2.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f), 0f),
                    cap = StrokeCap.Round
                )
            )
        }

        // 5. Scrub Indicator
        scrubIndex?.let { idx ->
            if (idx in points.indices) {
                val scrubX = toX(idx, points.size)
                val scrubY = toY(points[idx].valuePaise)

                drawLine(
                    color = Color.Black.copy(alpha = 0.6f),
                    start = Offset(scrubX, 0f),
                    end = Offset(scrubX, heightPx),
                    strokeWidth = 1.dp.toPx()
                )
                drawCircle(
                    color = lineColor,
                    radius = 5.dp.toPx(),
                    center = Offset(scrubX, scrubY)
                )
            }
        }
    }
}

/**
 * Render primitive for V2 Burn-Up line chart per Flow.md §17.2 V2.
 * Displays cumulative daily spend vs linear pace vs budget cap line, plus D4 projection.
 */
@Composable
fun BurnUpChartRender(
    model: com.tapledger.ui.charts.model.BurnUpChartModel,
    lineColor: Color = Color(0xFF0072B2),
    paceColor: Color = Color(0xFF7F7F7F),
    budgetCapColor: Color = Color(0xFFD55E00),
    modifier: Modifier = Modifier,
    height: Dp = 180.dp,
    onScrub: ((com.tapledger.ui.charts.model.BurnUpPoint?) -> Unit)? = null
) {
    if (model.cumulativePoints.isEmpty() && model.pacePoints.isEmpty()) return

    var scrubIndex by remember { mutableStateOf<Int?>(null) }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .pointerInput(model.cumulativePoints) {
                if (onScrub != null && model.cumulativePoints.isNotEmpty()) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            val idx = ((offset.x / size.width.toFloat()) * (model.cumulativePoints.size - 1))
                                .toInt()
                                .coerceIn(0, model.cumulativePoints.size - 1)
                            scrubIndex = idx
                            onScrub(model.cumulativePoints[idx])
                        },
                        onDragEnd = {
                            scrubIndex = null
                            onScrub(null)
                        },
                        onDragCancel = {
                            scrubIndex = null
                            onScrub(null)
                        },
                        onDrag = { change, _ ->
                            val idx = ((change.position.x / size.width.toFloat()) * (model.cumulativePoints.size - 1))
                                .toInt()
                                .coerceIn(0, model.cumulativePoints.size - 1)
                            scrubIndex = idx
                            onScrub(model.cumulativePoints[idx])
                        }
                    )
                }
            }
    ) {
        val widthPx = size.width
        val heightPx = size.height

        val allValues = model.cumulativePoints.map { it.netSpentPaise } +
                model.pacePoints.map { it.netSpentPaise } +
                listOfNotNull(model.budgetCapPaise, model.projectionPaise, 0L)
        val minVal = 0L
        val maxVal = maxOf(allValues.maxOrNull() ?: 1L, 1L)
        val valRange = (maxVal - minVal).coerceAtLeast(1L).toFloat()

        val totalDays = maxOf(model.cumulativePoints.size, model.pacePoints.size, 1)

        fun toX(dayIdx: Int, total: Int): Float {
            return if (total <= 1) widthPx / 2f else (dayIdx.toFloat() / (total - 1).toFloat()) * widthPx
        }

        fun toY(value: Long): Float {
            val fraction = (value - minVal).toFloat() / valRange
            return heightPx - (fraction * heightPx)
        }

        // 1. Budget Cap Horizontal Line
        if (model.budgetCapPaise > 0L) {
            val capY = toY(model.budgetCapPaise)
            drawLine(
                color = budgetCapColor.copy(alpha = 0.7f),
                start = Offset(0f, capY),
                end = Offset(widthPx, capY),
                strokeWidth = 1.5.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f), 0f)
            )
        }

        // 2. Linear Pace Guideline Line
        if (model.pacePoints.size > 1) {
            val pacePath = Path().apply {
                moveTo(toX(0, model.pacePoints.size), toY(model.pacePoints[0].netSpentPaise))
                for (i in 1 until model.pacePoints.size) {
                    lineTo(toX(i, model.pacePoints.size), toY(model.pacePoints[i].netSpentPaise))
                }
            }
            drawPath(
                path = pacePath,
                color = paceColor.copy(alpha = 0.6f),
                style = Stroke(
                    width = 1.5.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f),
                    cap = StrokeCap.Round
                )
            )
        }

        // 3. Cumulative Spend Fill Area & Line
        if (model.cumulativePoints.size > 1) {
            val areaPath = Path().apply {
                moveTo(toX(0, totalDays), heightPx)
                model.cumulativePoints.forEachIndexed { i, pt ->
                    lineTo(toX(i, totalDays), toY(pt.netSpentPaise))
                }
                lineTo(toX(model.cumulativePoints.size - 1, totalDays), heightPx)
                close()
            }
            drawPath(
                path = areaPath,
                brush = Brush.verticalGradient(
                    colors = listOf(lineColor.copy(alpha = 0.25f), Color.Transparent),
                    startY = 0f,
                    endY = heightPx
                )
            )

            val linePath = Path().apply {
                moveTo(toX(0, totalDays), toY(model.cumulativePoints[0].netSpentPaise))
                for (i in 1 until model.cumulativePoints.size) {
                    lineTo(toX(i, totalDays), toY(model.cumulativePoints[i].netSpentPaise))
                }
            }
            drawPath(
                path = linePath,
                color = lineColor,
                style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
            )
        }

        // 4. D4 Projection Dashed Line
        if (model.projectionPoint != null && model.cumulativePoints.isNotEmpty()) {
            val lastPoint = model.cumulativePoints.last()
            val startX = toX(model.cumulativePoints.size - 1, totalDays)
            val startY = toY(lastPoint.netSpentPaise)
            val endX = toX(totalDays - 1, totalDays)
            val endY = toY(model.projectionPoint.netSpentPaise)

            val projPath = Path().apply {
                moveTo(startX, startY)
                lineTo(endX, endY)
            }
            drawPath(
                path = projPath,
                color = lineColor.copy(alpha = 0.75f),
                style = Stroke(
                    width = 2.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 6f), 0f),
                    cap = StrokeCap.Round
                )
            )
        }

        // 5. Scrub Indicator
        scrubIndex?.let { idx ->
            if (idx in model.cumulativePoints.indices) {
                val pt = model.cumulativePoints[idx]
                val scrubX = toX(idx, totalDays)
                val scrubY = toY(pt.netSpentPaise)

                drawLine(
                    color = Color.Black.copy(alpha = 0.5f),
                    start = Offset(scrubX, 0f),
                    end = Offset(scrubX, heightPx),
                    strokeWidth = 1.dp.toPx()
                )
                drawCircle(
                    color = lineColor,
                    radius = 5.dp.toPx(),
                    center = Offset(scrubX, scrubY)
                )
            }
        }
    }
}
