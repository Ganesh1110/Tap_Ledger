package com.tapledger.ui.charts.render

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tapledger.R
import com.tapledger.ui.theme.ChartPalette

enum class ChartPatternType {
    STRIPED_ADVANCE,
    SHADED_DEFICIT,
    HATCHED_OVERFLOW,
    DASHED_IN_PROGRESS
}

@Composable
fun ChartPatternInfoButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    IconButton(
        onClick = onClick,
        modifier = modifier.size(32.dp)
    ) {
        Icon(
            imageVector = Icons.Outlined.Info,
            contentDescription = "Pattern explanations",
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun PatternSwatch(type: ChartPatternType, modifier: Modifier = Modifier) {
    val outlineColor = MaterialTheme.colorScheme.outlineVariant
    val surfaceColor = MaterialTheme.colorScheme.surfaceVariant
    val primaryContainer = MaterialTheme.colorScheme.primaryContainer
    val primaryColor = MaterialTheme.colorScheme.primary

    Box(
        modifier = modifier
            .size(36.dp, 24.dp)
            .border(1.dp, outlineColor, RoundedCornerShape(4.dp))
            .background(surfaceColor, RoundedCornerShape(4.dp))
    ) {
        Canvas(modifier = Modifier.matchParentSize()) {
            val w = size.width
            val h = size.height

            when (type) {
                ChartPatternType.STRIPED_ADVANCE -> {
                    val stripeColor = ChartPalette.LightSavings
                    val strokeW = 2.dp.toPx()
                    var x = -h
                    while (x < w + h) {
                        drawLine(
                            color = stripeColor,
                            start = Offset(x, h),
                            end = Offset(x + h, 0f),
                            strokeWidth = strokeW
                        )
                        x += 8.dp.toPx()
                    }
                }
                ChartPatternType.SHADED_DEFICIT -> {
                    drawRect(color = ChartPalette.LightWants.copy(alpha = 0.35f))
                    val strokeW = 1.5.dp.toPx()
                    var x = -h
                    while (x < w + h) {
                        drawLine(
                            color = ChartPalette.LightWants,
                            start = Offset(x, h),
                            end = Offset(x + h, 0f),
                            strokeWidth = strokeW
                        )
                        x += 10.dp.toPx()
                    }
                }
                ChartPatternType.HATCHED_OVERFLOW -> {
                    drawRect(color = ChartPalette.OverflowHatch.copy(alpha = 0.3f))
                    val strokeW = 2.dp.toPx()
                    var x = -h
                    while (x < w + h) {
                        drawLine(
                            color = ChartPalette.OverflowHatch,
                            start = Offset(x, h),
                            end = Offset(x + h, 0f),
                            strokeWidth = strokeW
                        )
                        x += 6.dp.toPx()
                    }
                }
                ChartPatternType.DASHED_IN_PROGRESS -> {
                    drawRect(color = primaryContainer.copy(alpha = 0.4f))
                    val dashEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx()), 0f)
                    drawLine(
                        color = primaryColor,
                        start = Offset(0f, h / 2f),
                        end = Offset(w, h / 2f),
                        strokeWidth = 3.dp.toPx(),
                        pathEffect = dashEffect
                    )
                }
            }
        }
    }
}

@Composable
private fun PatternRow(
    type: ChartPatternType,
    title: String,
    description: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        PatternSwatch(type = type)
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChartPatternLegendSheet(
    patterns: List<ChartPatternType>,
    onDismissRequest: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            Text(
                text = stringResource(R.string.chart_pattern_legend_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = stringResource(R.string.chart_pattern_legend_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(16.dp))

            patterns.forEach { pattern ->
                when (pattern) {
                    ChartPatternType.STRIPED_ADVANCE -> {
                        PatternRow(
                            type = pattern,
                            title = stringResource(R.string.chart_pattern_striped_title),
                            description = stringResource(R.string.chart_pattern_striped_desc)
                        )
                    }
                    ChartPatternType.SHADED_DEFICIT -> {
                        PatternRow(
                            type = pattern,
                            title = stringResource(R.string.chart_pattern_shaded_title),
                            description = stringResource(R.string.chart_pattern_shaded_desc)
                        )
                    }
                    ChartPatternType.HATCHED_OVERFLOW -> {
                        PatternRow(
                            type = pattern,
                            title = stringResource(R.string.chart_pattern_hatched_title),
                            description = stringResource(R.string.chart_pattern_hatched_desc)
                        )
                    }
                    ChartPatternType.DASHED_IN_PROGRESS -> {
                        PatternRow(
                            type = pattern,
                            title = stringResource(R.string.chart_pattern_dashed_title),
                            description = stringResource(R.string.chart_pattern_dashed_desc)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
