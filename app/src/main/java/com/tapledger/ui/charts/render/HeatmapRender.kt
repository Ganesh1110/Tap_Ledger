package com.tapledger.ui.charts.render

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tapledger.ui.theme.ChartPalette

data class HeatmapDay(
    val dayOfMonth: Int,
    val dateEpochMillis: Long,
    val netSpentPaise: Long,
    val intensityLevel: Int // 0..4 (5 levels)
)

/**
 * Render primitive for V7 Spending Calendar Heatmap per Flow.md §17.2 V7.
 * Renders month grid with 5 intensity levels by integer quantile.
 */
@Composable
fun HeatmapRender(
    days: List<HeatmapDay?>, // 7 columns per week, null for padding days
    onDayClick: (HeatmapDay) -> Unit,
    modifier: Modifier = Modifier,
    baseColor: Color = ChartPalette.NeedsBase
) {
    val weekDays = listOf("M", "T", "W", "T", "F", "S", "S")

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        // Weekday header
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            weekDays.forEach { d ->
                Box(modifier = Modifier.size(36.dp), contentAlignment = Alignment.Center) {
                    Text(
                        text = d,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Chunk into 7-day rows
        days.chunked(7).forEach { week ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                week.forEach { day ->
                    if (day != null) {
                        val cellColor = when (day.intensityLevel) {
                            0 -> MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.4f)
                            1 -> baseColor.copy(alpha = 0.25f)
                            2 -> baseColor.copy(alpha = 0.50f)
                            3 -> baseColor.copy(alpha = 0.75f)
                            else -> baseColor
                        }

                        val textColor = if (day.intensityLevel >= 3) Color.White else MaterialTheme.colorScheme.onSurface

                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(cellColor)
                                .clickable { onDayClick(day) },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = day.dayOfMonth.toString(),
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                                color = textColor
                            )
                        }
                    } else {
                        Box(modifier = Modifier.size(36.dp))
                    }
                }
                // Fill remaining empty cells in partial week
                repeat(7 - week.size) {
                    Box(modifier = Modifier.size(36.dp))
                }
            }
        }
    }
}
