package com.tapledger.ui.screens.categories.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.tapledger.core.model.MoneyFormatter
import com.tapledger.ui.charts.model.CategoryDonutModel
import com.tapledger.ui.charts.model.CategoryDonutSlice
import com.tapledger.ui.charts.model.ChartState
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.runtime.remember
import com.tapledger.ui.charts.render.ChartDetailStrip
import com.tapledger.ui.charts.render.ChartTooltipItem
import com.tapledger.ui.charts.render.DonutChartRender
import com.tapledger.ui.charts.render.rememberIsCompactChart
import com.tapledger.ui.charts.util.ChartMath
import com.tapledger.ui.charts.util.ChartSummaryHelper
import com.tapledger.ui.theme.ChartPalette
import com.tapledger.ui.theme.IncomeGreen
import com.tapledger.ui.theme.Spacing

/**
 * V6 Category Donut Card per Flow.md §17.2 V6.
 * Multi-slice donut chart for Needs+Wants categories with NetSpent > 0 (top 6 + Other, or top 4 + Other on compact),
 * D2 percentages, plain-language summary line, and interactive tap selection card/detail strip.
 * Net-negative (refund-heavy) categories are excluded from donut slices and listed in the legend as "Net refund".
 */
@Composable
fun CategoryDonutCard(
    donutState: ChartState<CategoryDonutModel>,
    selectedSlice: CategoryDonutSlice?,
    onSelectSlice: (CategoryDonutSlice?) -> Unit,
    modifier: Modifier = Modifier
) {
    val isCompact = rememberIsCompactChart()
    val summaryLine = remember(donutState) {
        if (donutState is ChartState.Data) {
            ChartSummaryHelper.summarizeV6Donut(donutState.data)
        } else {
            "Needs + Wants category apportionment"
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
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.md)
        ) {
            Text(
                text = "Spending Breakdown",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = summaryLine,
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(Spacing.md))

            when (donutState) {
                is ChartState.Loading -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(36.dp))
                    }
                }
                is ChartState.Empty -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = donutState.message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
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
                            text = donutState.message,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Center
                        )
                    }
                }
                is ChartState.Data -> {
                    val rawData = donutState.data
                    val data = remember(rawData, isCompact) {
                        if (isCompact && rawData.slices.size > 5) {
                            val top4 = rawData.slices.take(4)
                            val rest = rawData.slices.drop(4)
                            val restPaise = rest.sumOf { it.amountPaise }
                            val restBp = rest.sumOf { it.percentageBp }
                            val restSlice = CategoryDonutSlice(
                                categoryId = -1L,
                                categoryName = "Other",
                                amountPaise = restPaise,
                                percentageBp = restBp,
                                percentageFormatted = ChartMath.formatBasisPoints(restBp)
                            )
                            rawData.copy(slices = top4 + restSlice)
                        } else {
                            rawData
                        }
                    }

                    if (data.slices.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(120.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "No spending recorded in this period",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        val donutDiameter = if (isCompact) 160.dp else 180.dp
                        val donutStroke = if (isCompact) 26.dp else 22.dp

                        // Donut Chart Centered
                        Box(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            DonutChartRender(
                                slices = data.slices,
                                diameter = donutDiameter,
                                strokeWidth = donutStroke,
                                selectedCategoryId = selectedSlice?.categoryId,
                                onSliceClick = { slice ->
                                    if (selectedSlice?.categoryId == slice.categoryId) {
                                        onSelectSlice(null) // toggle off
                                    } else {
                                        onSelectSlice(slice)
                                    }
                                },
                                centerContent = {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        if (selectedSlice != null) {
                                            Text(
                                                text = selectedSlice.categoryName,
                                                style = MaterialTheme.typography.labelSmall,
                                                maxLines = 1,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Text(
                                                text = selectedSlice.percentageFormatted,
                                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        } else {
                                            Text(
                                                text = "Total Spent",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Text(
                                                text = MoneyFormatter.formatCompactIndian(data.totalSpentPaise),
                                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                    }
                                }
                            )
                        }

                        // Detail Strip for non-technical users and small phones
                        val detailItem = selectedSlice?.let { slice ->
                            val sliceIdx = data.slices.indexOfFirst { it.categoryId == slice.categoryId }.coerceAtLeast(0)
                            ChartTooltipItem(
                                id = slice.categoryId.toString(),
                                title = slice.categoryName,
                                valueFormatted = MoneyFormatter.formatPaiseToRupees(slice.amountPaise),
                                context = "${slice.percentageFormatted} of spend",
                                statusIcon = Icons.Default.PieChart,
                                statusColor = ChartPalette.sliceColor(sliceIdx)
                            )
                        }
                        ChartDetailStrip(
                            item = detailItem,
                            defaultSummary = summaryLine,
                            onClear = { onSelectSlice(null) }
                        )

                        Spacer(modifier = Modifier.height(Spacing.md))

                        // Slices Legend List (with >= 48dp touch targets and clear selection borders)
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            data.slices.forEachIndexed { index, slice ->
                                val sliceColor = ChartPalette.sliceColor(index)
                                val isSelected = selectedSlice?.categoryId == slice.categoryId

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 48.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(
                                            if (isSelected) sliceColor.copy(alpha = 0.12f) else Color.Transparent
                                        )
                                        .then(
                                            if (isSelected) Modifier.border(1.5.dp, sliceColor, RoundedCornerShape(8.dp)) else Modifier
                                        )
                                        .clickable {
                                            onSelectSlice(if (isSelected) null else slice)
                                        }
                                        .padding(horizontal = 10.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                        Box(
                                            modifier = Modifier
                                                .size(12.dp)
                                                .clip(CircleShape)
                                                .background(sliceColor)
                                        )
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Text(
                                            text = slice.categoryName,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                        )
                                    }

                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = MoneyFormatter.formatPaiseToRupees(slice.amountPaise),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = slice.percentageFormatted,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.SemiBold,
                                            color = sliceColor
                                        )
                                    }
                                }
                            }
                        }

                        // Refund-heavy Categories Legend (§17.2 V6)
                        if (data.refundCategories.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(Spacing.sm))
                            HorizontalDivider()
                            Spacer(modifier = Modifier.height(Spacing.xs))
                            Text(
                                text = "Excluded (Net Refund)",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = IncomeGreen
                            )
                            data.refundCategories.forEach { refund ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 2.dp, horizontal = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "${refund.categoryName} (Net refund)",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = IncomeGreen
                                    )
                                    Text(
                                        text = "+${refund.refundFormatted}",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = IncomeGreen
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
