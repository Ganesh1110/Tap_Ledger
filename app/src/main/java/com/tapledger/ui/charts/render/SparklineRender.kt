package com.tapledger.ui.charts.render

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Minimal compact sparkline trendline.
 */
@Composable
fun SparklineRender(
    valuesPaise: List<Long>,
    lineColor: Color,
    modifier: Modifier = Modifier,
    height: Dp = 32.dp
) {
    if (valuesPaise.size < 2) return

    Canvas(modifier = modifier.fillMaxWidth().height(height)) {
        val widthPx = size.width
        val heightPx = size.height

        val minVal = valuesPaise.minOrNull() ?: 0L
        val maxVal = valuesPaise.maxOrNull() ?: 1L
        val range = (maxVal - minVal).coerceAtLeast(1L).toFloat()

        val path = Path()
        valuesPaise.forEachIndexed { index, value ->
            val x = (index.toFloat() / (valuesPaise.size - 1).toFloat()) * widthPx
            val y = heightPx - (((value - minVal).toFloat() / range) * heightPx)
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }

        drawPath(
            path = path,
            color = lineColor,
            style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
        )
    }
}
