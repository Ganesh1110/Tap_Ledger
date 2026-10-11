package com.tapledger.ui.screens.monthclose.components

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
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tapledger.core.model.MoneyFormatter
import com.tapledger.ui.charts.model.AdvanceRolloverChartModel
import com.tapledger.ui.charts.model.ChartState
import com.tapledger.ui.charts.render.AdvanceRolloverBarRender
import com.tapledger.ui.theme.ChartPalette
import com.tapledger.ui.theme.Spacing

/**
 * V11 Advance Rollover Card per Flow.md §17.2 V11.
 * Shows diverging bars for carry (positive), sweep, applied deficit (negative), and uncovered pool (hatched).
 * Includes toggle to show data as an accessible table.
 */
@Composable
fun AdvanceRolloverCard(
    rolloverState: ChartState<AdvanceRolloverChartModel>,
    modifier: Modifier = Modifier
) {
    var showTable by remember { mutableStateOf(false) }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {
                if (rolloverState is ChartState.Data) {
                    contentDescription = rolloverState.data.contentDescription
                }
            },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
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
                Column {
                    Text(
                        text = "Advance Rollover Preview",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Deficit absorption & surplus carry to next month",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (rolloverState is ChartState.Data) {
                    TextButton(onClick = { showTable = !showTable }) {
                        Icon(
                            imageVector = Icons.Default.TableChart,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(if (showTable) "Chart" else "Table")
                    }
                }
            }

            Spacer(modifier = Modifier.height(Spacing.sm))

            when (rolloverState) {
                is ChartState.Loading -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    }
                }
                is ChartState.Empty -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = Spacing.md),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = rolloverState.message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                is ChartState.Error -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = Spacing.md),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error
                        )
                        Text(
                            text = rolloverState.message,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
                is ChartState.Data -> {
                    val model = rolloverState.data

                    // Legend
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = Spacing.sm),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(Color(0xFFD55E00))
                            )
                            Text("Deficit", style = MaterialTheme.typography.labelSmall)

                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(ChartPalette.OverflowHatch)
                            )
                            Text("Uncovered", style = MaterialTheme.typography.labelSmall)
                        }

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(Color(0xFF009E73))
                            )
                            Text("Carry Forward", style = MaterialTheme.typography.labelSmall)
                        }
                    }

                    if (showTable) {
                        // Accessible Table View
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Category", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1.5f))
                                Text("Carry", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                                Text("Deficit", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                                Text("Uncovered", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            model.tableRows.forEach { row ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 3.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(row.categoryName, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1.5f))
                                    Text(row.carryFormatted, style = MaterialTheme.typography.bodySmall, color = Color(0xFF009E73), modifier = Modifier.weight(1f))
                                    Text(row.appliedDeficitFormatted, style = MaterialTheme.typography.bodySmall, color = Color(0xFFD55E00), modifier = Modifier.weight(1f))
                                    Text(row.uncoveredPoolFormatted, style = MaterialTheme.typography.bodySmall, color = ChartPalette.OverflowHatch, modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    } else {
                        // Diverging Bars View
                        val maxPaise = remember(model.rows) {
                            model.rows.maxOfOrNull {
                                maxOf(
                                    it.carryPaise + it.sweepPaise,
                                    it.appliedDeficitPaise + it.uncoveredPoolPaise
                                )
                            } ?: 1L
                        }

                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(Spacing.sm)
                        ) {
                            model.rows.forEach { item ->
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = item.categoryName,
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.Medium
                                        )
                                        val netText = when {
                                            item.uncoveredPoolPaise > 0L -> "-${MoneyFormatter.formatPaiseToRupees(item.appliedDeficitPaise + item.uncoveredPoolPaise)} (uncovered)"
                                            item.appliedDeficitPaise > 0L -> "-${MoneyFormatter.formatPaiseToRupees(item.appliedDeficitPaise)}"
                                            item.carryPaise > 0L -> "+${MoneyFormatter.formatPaiseToRupees(item.carryPaise)}"
                                            else -> "₹0"
                                        }
                                        Text(
                                            text = netText,
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.SemiBold,
                                            color = when {
                                                item.uncoveredPoolPaise > 0L -> ChartPalette.OverflowHatch
                                                item.appliedDeficitPaise > 0L -> Color(0xFFD55E00)
                                                item.carryPaise > 0L -> Color(0xFF009E73)
                                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                                            }
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(3.dp))

                                    AdvanceRolloverBarRender(
                                        carryPaise = item.carryPaise,
                                        sweepPaise = item.sweepPaise,
                                        appliedDeficitPaise = item.appliedDeficitPaise,
                                        uncoveredPoolPaise = item.uncoveredPoolPaise,
                                        maxPaise = maxPaise
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
