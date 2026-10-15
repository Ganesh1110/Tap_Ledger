package com.tapledger.ui.screens.transactions.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tapledger.core.model.MoneyFormatter
import com.tapledger.core.util.DateTimeUtils
import com.tapledger.ui.charts.model.ChartState
import com.tapledger.ui.charts.model.SpendingHeatmapModel
import com.tapledger.ui.charts.render.HeatmapDay
import com.tapledger.ui.charts.render.HeatmapRender
import com.tapledger.ui.theme.ChartPalette
import com.tapledger.ui.theme.Spacing
import java.time.Instant
import java.time.YearMonth

import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import com.tapledger.ui.charts.render.ChartDetailStrip
import com.tapledger.ui.charts.render.ChartTooltipItem
import com.tapledger.ui.charts.render.rememberIsCompactChart
import com.tapledger.ui.charts.util.ChartSummaryHelper
import com.tapledger.ui.theme.WarningAmber

/**
 * V7 Spending Calendar Heatmap Card per Flow.md §17.2 V7.
 * Daily Needs+Wants NetSpent in 5 intensity levels by integer quantile.
 * Tapping a day applies/clears date filter, displays detail strip, and adapts to compact screens.
 */
@Composable
fun SpendingHeatmapCard(
    heatmapState: ChartState<SpendingHeatmapModel>,
    selectedDateEpoch: Long?,
    onSelectDay: (Long) -> Unit,
    onClearDateFilter: () -> Unit,
    modifier: Modifier = Modifier,
    selectedYear: Int? = null,
    selectedMonth: Int? = null,
    onPreviousMonth: (() -> Unit)? = null,
    onNextMonth: (() -> Unit)? = null,
    onJumpToCurrentMonth: (() -> Unit)? = null
) {
    val isCompact = rememberIsCompactChart()
    val summaryLine = remember(heatmapState) {
        if (heatmapState is ChartState.Data) {
            ChartSummaryHelper.summarizeV7Heatmap(heatmapState.data)
        } else {
            "Needs + Wants daily intensity"
        }
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {
                contentDescription = summaryLine
            },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.md)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Spending Calendar",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = summaryLine,
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                        color = MaterialTheme.colorScheme.primary
                    )
                    if (selectedYear != null && selectedMonth != null && onPreviousMonth != null && onNextMonth != null) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            IconButton(
                                onClick = onPreviousMonth,
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Previous Month",
                                    modifier = Modifier.size(16.dp)
                                )
                            }

                            Text(
                                text = DateTimeUtils.formatMonthYear(selectedYear, selectedMonth),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )

                            IconButton(
                                onClick = onNextMonth,
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                    contentDescription = "Next Month",
                                    modifier = Modifier.size(16.dp)
                                )
                            }

                            val currentYm = remember { YearMonth.now(DateTimeUtils.IST_ZONE) }
                            val isCurrentMonth = selectedYear == currentYm.year && selectedMonth == currentYm.monthValue
                            if (!isCurrentMonth && onJumpToCurrentMonth != null) {
                                Spacer(modifier = Modifier.width(4.dp))
                                AssistChip(
                                    onClick = onJumpToCurrentMonth,
                                    label = { Text("This Month", fontSize = 11.sp) },
                                    modifier = Modifier.height(26.dp)
                                )
                            }
                        }
                    }
                }

                Column(horizontalAlignment = Alignment.End) {
                    if (heatmapState is ChartState.Data) {
                        Text(
                            text = MoneyFormatter.formatPaiseToRupees(heatmapState.data.totalSpentPaise),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Text(
                        text = "Needs + Wants",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(Spacing.sm))

            when (heatmapState) {
                is ChartState.Loading -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(32.dp))
                    }
                }
                is ChartState.Empty -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(100.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = heatmapState.message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                is ChartState.Error -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(Spacing.md),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = heatmapState.message,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
                is ChartState.Data -> {
                    val data = heatmapState.data

                    // Align to calendar week with leading padding nulls
                    val gridDays = remember(data.cells) {
                        val firstEpoch = data.cells.firstOrNull()?.epochMillis ?: 0L
                        val dayOfWeek = if (firstEpoch > 0L) {
                            Instant.ofEpochMilli(firstEpoch).atZone(DateTimeUtils.IST_ZONE).dayOfWeek.value // 1..7 (Mon=1)
                        } else 1
                        val paddingCount = dayOfWeek - 1

                        val result = mutableListOf<HeatmapDay?>()
                        repeat(paddingCount) { result.add(null) }
                        data.cells.forEach { cell ->
                            result.add(
                                HeatmapDay(
                                    dayOfMonth = cell.dayOfMonth,
                                    dateEpochMillis = cell.epochMillis,
                                    netSpentPaise = cell.netSpentPaise,
                                    intensityLevel = cell.intensityLevel
                                )
                            )
                        }
                        result
                    }

                    HeatmapRender(
                        days = gridDays,
                        onDayClick = { onSelectDay(it.dateEpochMillis) },
                        baseColor = ChartPalette.NeedsBase
                    )

                    Spacer(modifier = Modifier.height(Spacing.sm))

                    // Active date filter indicator chip
                    if (selectedDateEpoch != null) {
                        val selectedCell = data.cells.find { it.epochMillis == selectedDateEpoch }
                        val dayStr = if (selectedCell != null) "Day ${selectedCell.dayOfMonth}" else "Selected Day"
                        val amountStr = if (selectedCell != null) MoneyFormatter.formatPaiseToRupees(selectedCell.netSpentPaise) else ""

                        AssistChip(
                            onClick = onClearDateFilter,
                            label = { Text("Filtered: $dayStr ($amountStr)") },
                            trailingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Clear date filter",
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        )
                    } else {
                        // Intensity Legend: 0 to 4
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Less",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            listOf(0, 1, 2, 3, 4).forEach { level ->
                                val color = when (level) {
                                    0 -> MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f)
                                    1 -> ChartPalette.NeedsBase.copy(alpha = 0.25f)
                                    2 -> ChartPalette.NeedsBase.copy(alpha = 0.50f)
                                    3 -> ChartPalette.NeedsBase.copy(alpha = 0.75f)
                                    else -> ChartPalette.NeedsBase
                                }
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .clip(RoundedCornerShape(2.dp))
                                        .background(color)
                                )
                                Spacer(modifier = Modifier.width(2.dp))
                            }
                            Spacer(modifier = Modifier.width(2.dp))
                            Text(
                                text = "More",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Detail Strip (§17.2 D10)
                    val selectedCell = selectedDateEpoch?.let { epoch -> data.cells.find { it.epochMillis == epoch } }
                    val detailItem = selectedCell?.let { cell ->
                        ChartTooltipItem(
                            id = cell.dayOfMonth.toString(),
                            title = "Day ${cell.dayOfMonth}",
                            valueFormatted = MoneyFormatter.formatPaiseToRupees(cell.netSpentPaise),
                            context = "Intensity level ${cell.intensityLevel} of 4 • Tap cell to clear",
                            statusIcon = if (cell.intensityLevel >= 3) Icons.Default.Warning else Icons.Default.CheckCircle,
                            statusColor = if (cell.intensityLevel >= 3) WarningAmber else ChartPalette.NeedsBase
                        )
                    }
                    ChartDetailStrip(
                        item = detailItem,
                        defaultSummary = summaryLine,
                        onClear = onClearDateFilter
                    )
                }
            }
        }
    }
}
