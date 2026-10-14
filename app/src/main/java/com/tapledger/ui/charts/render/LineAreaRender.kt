package com.tapledger.ui.charts.render

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
                if (onScrub != null && points.isNotEmpty()) {
                    detectHorizontalDragGestures(
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
                        onHorizontalDrag = { change, _ ->
                            change.consume()
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

/**
 * Compact helper to format Paise values into human-readable Indian rupee notation (e.g., ₹24.6k, ₹1.5L, ₹0).
 */
private fun formatCompactRupees(paise: Long): String {
    val rupees = paise / 100L
    val absRupees = kotlin.math.abs(rupees)
    val sign = if (rupees < 0) "-" else ""
    return when {
        absRupees >= 1_00_00_000L -> {
            val cr = absRupees / 1_00_00_000L
            val frac = (absRupees % 1_00_00_000L) / 10_00_000L
            if (frac > 0) "${sign}₹$cr.${frac}Cr" else "${sign}₹${cr}Cr"
        }
        absRupees >= 100_000L -> {
            val l = absRupees / 100_000L
            val frac = (absRupees % 100_000L) / 10_000L
            if (frac > 0) "${sign}₹$l.${frac}L" else "${sign}₹${l}L"
        }
        absRupees >= 1_000L -> {
            val k = absRupees / 1_000L
            val frac = (absRupees % 1_000L) / 100L
            if (frac > 0) "${sign}₹$k.${frac}k" else "${sign}₹${k}k"
        }
        else -> "${sign}₹$absRupees"
    }
}

/**
 * Format raw month string (e.g. "9/26", "10/26", "Now") into friendly short label ("Sep", "Oct", "Now").
 */
private fun formatMonthOnly(raw: String): String {
    if (raw.equals("Now", ignoreCase = true)) return "Now"
    val parts = raw.split("/")
    if (parts.size == 2) {
        val monthNum = parts[0].toIntOrNull()
        if (monthNum != null && monthNum in 1..12) {
            val names = arrayOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
            return names[monthNum - 1]
        }
    }
    return raw
}

/**
 * Render primitive for V9 Net worth line/area chart per Flow.md §17.2 V9.
 * Supports:
 * - Single Net Worth line with smooth cubic bezier curve, area fill, and timeline labels
 * - Subtle dashed reference gridlines with compact ₹ scale markers
 * - Stacked Assets & Liabilities view with dual spline curves
 * - Horizontal drag scrub with vertical scroll pass-through via detectHorizontalDragGestures
 */
@Composable
fun NetWorthChartRender(
    points: List<com.tapledger.ui.charts.model.NetWorthPoint>,
    modifier: Modifier = Modifier,
    height: Dp = 200.dp,
    isStacked: Boolean = false,
    netWorthColor: Color = Color(0xFF0072B2),
    assetsColor: Color = Color(0xFF009E73),
    liabilitiesColor: Color = Color(0xFFD55E00),
    onScrub: ((com.tapledger.ui.charts.model.NetWorthPoint?) -> Unit)? = null
) {
    if (points.isEmpty()) return

    var scrubIndex by remember { mutableStateOf<Int?>(null) }

    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
    val labelColorArgb = labelColor.toArgb()
    val gridLineColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
    val baselineColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f)
    val scrubLineColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)

    val textPaint = remember(labelColorArgb) {
        Paint().apply {
            color = labelColorArgb
            textSize = 28f
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        }
    }

    val rightAxisPaint = remember(labelColorArgb) {
        Paint().apply {
            color = labelColorArgb
            textSize = 24f
            textAlign = Paint.Align.LEFT
            isAntiAlias = true
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        }
    }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .pointerInput(points) {
                if (onScrub != null && points.isNotEmpty()) {
                    detectHorizontalDragGestures(
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
                        onHorizontalDrag = { change, _ ->
                            change.consume()
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

        val leftPaddingPx = 18.dp.toPx()
        val rightPaddingPx = 52.dp.toPx()
        val topPaddingPx = 18.dp.toPx()
        val bottomPaddingPx = 28.dp.toPx()

        val plotWidth = (widthPx - leftPaddingPx - rightPaddingPx).coerceAtLeast(1f)
        val plotHeight = (heightPx - topPaddingPx - bottomPaddingPx).coerceAtLeast(1f)

        fun toX(i: Int): Float {
            return if (points.size <= 1) {
                leftPaddingPx + (plotWidth / 2f)
            } else {
                leftPaddingPx + (i.toFloat() / (points.size - 1).toFloat()) * plotWidth
            }
        }

        if (!isStacked) {
            val allValues = points.map { it.netWorthPaise } + listOf(0L)
            val minVal = allValues.minOrNull() ?: 0L
            val maxVal = maxOf(allValues.maxOrNull() ?: 1L, 1L)
            val valRange = (maxVal - minVal).coerceAtLeast(100_00L).toFloat()

            fun toY(value: Long): Float {
                val fraction = (value - minVal).toFloat() / valRange
                return (topPaddingPx + plotHeight) - (fraction * plotHeight)
            }

            val maxValY = toY(maxVal)
            val zeroY = toY(0L).coerceIn(topPaddingPx, topPaddingPx + plotHeight)
            val midVal = (maxVal + minVal) / 2L
            val midValY = toY(midVal)

            drawLine(
                color = gridLineColor,
                start = Offset(leftPaddingPx, maxValY),
                end = Offset(leftPaddingPx + plotWidth, maxValY),
                strokeWidth = 1.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f), 0f)
            )
            drawContext.canvas.nativeCanvas.drawText(
                formatCompactRupees(maxVal),
                leftPaddingPx + plotWidth + 6.dp.toPx(),
                maxValY + 8f,
                rightAxisPaint
            )

            if (kotlin.math.abs(maxValY - midValY) > 24.dp.toPx() && kotlin.math.abs(midValY - zeroY) > 24.dp.toPx()) {
                drawLine(
                    color = gridLineColor.copy(alpha = 0.2f),
                    start = Offset(leftPaddingPx, midValY),
                    end = Offset(leftPaddingPx + plotWidth, midValY),
                    strokeWidth = 1.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f)
                )
                drawContext.canvas.nativeCanvas.drawText(
                    formatCompactRupees(midVal),
                    leftPaddingPx + plotWidth + 6.dp.toPx(),
                    midValY + 8f,
                    rightAxisPaint
                )
            }

            drawLine(
                color = baselineColor,
                start = Offset(leftPaddingPx, zeroY),
                end = Offset(leftPaddingPx + plotWidth, zeroY),
                strokeWidth = 1.dp.toPx()
            )
            drawContext.canvas.nativeCanvas.drawText(
                "₹0",
                leftPaddingPx + plotWidth + 6.dp.toPx(),
                zeroY + 8f,
                rightAxisPaint
            )

            if (points.size > 1) {
                val areaPath = Path().apply {
                    moveTo(toX(0), zeroY)
                    lineTo(toX(0), toY(points[0].netWorthPaise))
                    for (i in 1 until points.size) {
                        val prevX = toX(i - 1)
                        val prevY = toY(points[i - 1].netWorthPaise)
                        val curX = toX(i)
                        val curY = toY(points[i].netWorthPaise)
                        val cp1X = prevX + (curX - prevX) * 0.45f
                        val cp1Y = prevY
                        val cp2X = prevX + (curX - prevX) * 0.55f
                        val cp2Y = curY
                        cubicTo(cp1X, cp1Y, cp2X, cp2Y, curX, curY)
                    }
                    lineTo(toX(points.size - 1), zeroY)
                    close()
                }
                drawPath(
                    path = areaPath,
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            netWorthColor.copy(alpha = 0.28f),
                            netWorthColor.copy(alpha = 0.06f),
                            Color.Transparent
                        ),
                        startY = topPaddingPx,
                        endY = topPaddingPx + plotHeight
                    )
                )

                val linePath = Path().apply {
                    moveTo(toX(0), toY(points[0].netWorthPaise))
                    for (i in 1 until points.size) {
                        val prevX = toX(i - 1)
                        val prevY = toY(points[i - 1].netWorthPaise)
                        val curX = toX(i)
                        val curY = toY(points[i].netWorthPaise)
                        val cp1X = prevX + (curX - prevX) * 0.45f
                        val cp1Y = prevY
                        val cp2X = prevX + (curX - prevX) * 0.55f
                        val cp2Y = curY
                        cubicTo(cp1X, cp1Y, cp2X, cp2Y, curX, curY)
                    }
                }
                drawPath(
                    path = linePath,
                    color = netWorthColor,
                    style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                )
            }

            points.forEachIndexed { i, pt ->
                val x = toX(i)
                val y = toY(pt.netWorthPaise)
                if (i == points.size - 1) {
                    drawCircle(color = netWorthColor.copy(alpha = 0.25f), radius = 7.5.dp.toPx(), center = Offset(x, y))
                    drawCircle(color = netWorthColor, radius = 4.5.dp.toPx(), center = Offset(x, y))
                    drawCircle(color = Color.White, radius = 2.dp.toPx(), center = Offset(x, y))
                } else {
                    drawCircle(color = netWorthColor, radius = 3.5.dp.toPx(), center = Offset(x, y))
                }
            }

            val step = if (points.size > 8) 2 else 1
            points.forEachIndexed { i, pt ->
                if (i == points.size - 1 || i % step == 0) {
                    val labelText = formatMonthOnly(pt.label)
                    val x = toX(i)
                    val y = heightPx - 6.dp.toPx()
                    drawContext.canvas.nativeCanvas.drawText(labelText, x, y, textPaint)
                }
            }

            scrubIndex?.let { idx ->
                if (idx in points.indices) {
                    val pt = points[idx]
                    val scrubX = toX(idx)
                    val scrubY = toY(pt.netWorthPaise)

                    drawLine(
                        color = scrubLineColor,
                        start = Offset(scrubX, topPaddingPx),
                        end = Offset(scrubX, topPaddingPx + plotHeight),
                        strokeWidth = 1.5.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f)
                    )
                    drawCircle(
                        color = netWorthColor.copy(alpha = 0.35f),
                        radius = 8.5.dp.toPx(),
                        center = Offset(scrubX, scrubY)
                    )
                    drawCircle(
                        color = netWorthColor,
                        radius = 5.dp.toPx(),
                        center = Offset(scrubX, scrubY)
                    )
                    drawCircle(
                        color = Color.White,
                        radius = 2.dp.toPx(),
                        center = Offset(scrubX, scrubY)
                    )
                }
            }
        } else {
            val maxAsset = points.maxOfOrNull { it.assetsPaise } ?: 1L
            val maxLiab = points.maxOfOrNull { it.liabilitiesPaise } ?: 1L
            val maxVal = maxOf(maxAsset, maxLiab, 1L).coerceAtLeast(100_00L).toFloat()

            val zeroY = topPaddingPx + (plotHeight * 0.52f)

            fun toYAsset(value: Long): Float {
                val fraction = value.toFloat() / maxVal
                return zeroY - (fraction * (zeroY - topPaddingPx - 6.dp.toPx()))
            }

            fun toYLiab(value: Long): Float {
                val fraction = value.toFloat() / maxVal
                return zeroY + (fraction * (topPaddingPx + plotHeight - zeroY - 6.dp.toPx()))
            }

            drawLine(
                color = baselineColor,
                start = Offset(leftPaddingPx, zeroY),
                end = Offset(leftPaddingPx + plotWidth, zeroY),
                strokeWidth = 1.dp.toPx()
            )
            drawContext.canvas.nativeCanvas.drawText(
                "₹0",
                leftPaddingPx + plotWidth + 6.dp.toPx(),
                zeroY + 8f,
                rightAxisPaint
            )

            val maxAssetY = toYAsset(maxAsset)
            drawLine(
                color = assetsColor.copy(alpha = 0.25f),
                start = Offset(leftPaddingPx, maxAssetY),
                end = Offset(leftPaddingPx + plotWidth, maxAssetY),
                strokeWidth = 1.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f)
            )
            drawContext.canvas.nativeCanvas.drawText(
                formatCompactRupees(maxAsset),
                leftPaddingPx + plotWidth + 6.dp.toPx(),
                maxAssetY + 8f,
                rightAxisPaint
            )

            if (points.size > 1) {
                val assetArea = Path().apply {
                    moveTo(toX(0), zeroY)
                    lineTo(toX(0), toYAsset(points[0].assetsPaise))
                    for (i in 1 until points.size) {
                        val prevX = toX(i - 1)
                        val prevY = toYAsset(points[i - 1].assetsPaise)
                        val curX = toX(i)
                        val curY = toYAsset(points[i].assetsPaise)
                        val cp1X = prevX + (curX - prevX) * 0.45f
                        val cp1Y = prevY
                        val cp2X = prevX + (curX - prevX) * 0.55f
                        val cp2Y = curY
                        cubicTo(cp1X, cp1Y, cp2X, cp2Y, curX, curY)
                    }
                    lineTo(toX(points.size - 1), zeroY)
                    close()
                }
                drawPath(
                    path = assetArea,
                    brush = Brush.verticalGradient(
                        colors = listOf(assetsColor.copy(alpha = 0.25f), Color.Transparent),
                        startY = topPaddingPx,
                        endY = zeroY
                    )
                )

                val assetLine = Path().apply {
                    moveTo(toX(0), toYAsset(points[0].assetsPaise))
                    for (i in 1 until points.size) {
                        val prevX = toX(i - 1)
                        val prevY = toYAsset(points[i - 1].assetsPaise)
                        val curX = toX(i)
                        val curY = toYAsset(points[i].assetsPaise)
                        val cp1X = prevX + (curX - prevX) * 0.45f
                        val cp1Y = prevY
                        val cp2X = prevX + (curX - prevX) * 0.55f
                        val cp2Y = curY
                        cubicTo(cp1X, cp1Y, cp2X, cp2Y, curX, curY)
                    }
                }
                drawPath(path = assetLine, color = assetsColor, style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round))

                val liabArea = Path().apply {
                    moveTo(toX(0), zeroY)
                    lineTo(toX(0), toYLiab(points[0].liabilitiesPaise))
                    for (i in 1 until points.size) {
                        val prevX = toX(i - 1)
                        val prevY = toYLiab(points[i - 1].liabilitiesPaise)
                        val curX = toX(i)
                        val curY = toYLiab(points[i].liabilitiesPaise)
                        val cp1X = prevX + (curX - prevX) * 0.45f
                        val cp1Y = prevY
                        val cp2X = prevX + (curX - prevX) * 0.55f
                        val cp2Y = curY
                        cubicTo(cp1X, cp1Y, cp2X, cp2Y, curX, curY)
                    }
                    lineTo(toX(points.size - 1), zeroY)
                    close()
                }
                drawPath(
                    path = liabArea,
                    brush = Brush.verticalGradient(
                        colors = listOf(Color.Transparent, liabilitiesColor.copy(alpha = 0.25f)),
                        startY = zeroY,
                        endY = topPaddingPx + plotHeight
                    )
                )

                val liabLine = Path().apply {
                    moveTo(toX(0), toYLiab(points[0].liabilitiesPaise))
                    for (i in 1 until points.size) {
                        val prevX = toX(i - 1)
                        val prevY = toYLiab(points[i - 1].liabilitiesPaise)
                        val curX = toX(i)
                        val curY = toYLiab(points[i].liabilitiesPaise)
                        val cp1X = prevX + (curX - prevX) * 0.45f
                        val cp1Y = prevY
                        val cp2X = prevX + (curX - prevX) * 0.55f
                        val cp2Y = curY
                        cubicTo(cp1X, cp1Y, cp2X, cp2Y, curX, curY)
                    }
                }
                drawPath(path = liabLine, color = liabilitiesColor, style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round))
            }

            points.forEachIndexed { i, pt ->
                drawCircle(color = assetsColor, radius = 3.dp.toPx(), center = Offset(toX(i), toYAsset(pt.assetsPaise)))
                drawCircle(color = liabilitiesColor, radius = 3.dp.toPx(), center = Offset(toX(i), toYLiab(pt.liabilitiesPaise)))
            }

            val step = if (points.size > 8) 2 else 1
            points.forEachIndexed { i, pt ->
                if (i == points.size - 1 || i % step == 0) {
                    val labelText = formatMonthOnly(pt.label)
                    val x = toX(i)
                    val y = heightPx - 6.dp.toPx()
                    drawContext.canvas.nativeCanvas.drawText(labelText, x, y, textPaint)
                }
            }

            scrubIndex?.let { idx ->
                if (idx in points.indices) {
                    val pt = points[idx]
                    val scrubX = toX(idx)
                    val assetY = toYAsset(pt.assetsPaise)
                    val liabY = toYLiab(pt.liabilitiesPaise)

                    drawLine(
                        color = scrubLineColor,
                        start = Offset(scrubX, topPaddingPx),
                        end = Offset(scrubX, topPaddingPx + plotHeight),
                        strokeWidth = 1.5.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f)
                    )
                    drawCircle(color = assetsColor, radius = 5.5.dp.toPx(), center = Offset(scrubX, assetY))
                    drawCircle(color = liabilitiesColor, radius = 5.5.dp.toPx(), center = Offset(scrubX, liabY))
                }
            }
        }
    }
}
