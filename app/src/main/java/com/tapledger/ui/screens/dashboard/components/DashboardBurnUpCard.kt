package com.tapledger.ui.screens.dashboard.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.tapledger.core.model.MoneyFormatter
import com.tapledger.ui.charts.model.BurnUpChartModel
import com.tapledger.ui.charts.model.BurnUpPoint
import com.tapledger.ui.charts.model.ChartState
import com.tapledger.ui.charts.render.BurnUpChartRender
import com.tapledger.ui.theme.ChartPalette
import com.tapledger.ui.theme.Spacing

/**
 * V2 Spend Burn-Up Line Card per Flow.md §17.2 V2.
 * Displays cumulative daily Needs+Wants NetSpent vs linear pace vs budget cap,
 * plus D4 projection. Supports horizontal scrub inspection and "Show as table" tabular toggle.
 */
@Composable
fun DashboardBurnUpCard(
    burnUpState: ChartState<BurnUpChartModel>,
    modifier: Modifier = Modifier,
    initiallyExpanded: Boolean = false
) {
    var isExpanded by remember { mutableStateOf(initiallyExpanded) }
    var showTable by remember { mutableStateOf(false) }
    var scrubbedPoint by remember { mutableStateOf<BurnUpPoint?>(null) }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {
                if (burnUpState is ChartState.Data) {
                    contentDescription = burnUpState.data.contentDescription
                }
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
            // Header with toggle
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isExpanded = !isExpanded },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Spend Burn-Up",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Cumulative spend vs pace & cap",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { isExpanded = !isExpanded }) {
                        Icon(
                            imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                            contentDescription = if (isExpanded) "Collapse Burn-Up" else "Expand Burn-Up"
                        )
                    }
                }
            }

            AnimatedVisibility(visible = isExpanded) {
                Column(modifier = Modifier.fillMaxWidth().padding(top = Spacing.sm)) {
                    when (burnUpState) {
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
                                    text = burnUpState.message,
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
                                    text = burnUpState.message,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                        is ChartState.Data -> {
                            val data = burnUpState.data

                            // Scrub status line
                            if (scrubbedPoint != null) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "Day ${scrubbedPoint!!.dayIndex + 1}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = MoneyFormatter.formatPaiseToRupees(scrubbedPoint!!.netSpentPaise),
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = ChartPalette.LightNeeds
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                            }

                            if (!showTable) {
                                // Graphical Burn-Up Chart
                                BurnUpChartRender(
                                    model = data,
                                    lineColor = ChartPalette.LightNeeds,
                                    paceColor = ChartPalette.LightPaceLine,
                                    budgetCapColor = ChartPalette.OverflowHatch,
                                    height = 180.dp,
                                    onScrub = { pt -> scrubbedPoint = pt }
                                )

                                Spacer(modifier = Modifier.height(Spacing.xs))

                                // Projection note if available
                                if (data.projectionPaise != null) {
                                    Text(
                                        text = "Projection: ${MoneyFormatter.formatPaiseToRupees(data.projectionPaise)} (estimate, not a ledger value)",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                // Legend
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(top = Spacing.xs),
                                    horizontalArrangement = Arrangement.spacedBy(Spacing.md)
                                ) {
                                    Text("— Cumulative Spend", style = MaterialTheme.typography.labelSmall, color = ChartPalette.LightNeeds)
                                    Text("-- Expected Pace", style = MaterialTheme.typography.labelSmall, color = ChartPalette.LightPaceLine)
                                    Text("-- Budget Cap", style = MaterialTheme.typography.labelSmall, color = ChartPalette.OverflowHatch)
                                }
                            } else {
                                // Accessible Table View (§17.3)
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text("Date", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                                        Text("Net Spent", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), textAlign = TextAlign.End)
                                        Text("Pace", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), textAlign = TextAlign.End)
                                        Text("Cap", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), textAlign = TextAlign.End)
                                    }
                                    HorizontalDivider()
                                    data.tableRows.takeLast(7).forEach { row ->
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(row.dateLabel, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                                            Text(row.netSpentFormatted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f), textAlign = TextAlign.End)
                                            Text(row.paceFormatted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f), textAlign = TextAlign.End)
                                            Text(row.budgetCapFormatted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f), textAlign = TextAlign.End)
                                        }
                                    }
                                }
                            }

                            // Tabular Toggle Action Button
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {
                                TextButton(onClick = { showTable = !showTable }) {
                                    Icon(
                                        imageVector = Icons.Default.TableChart,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.size(4.dp))
                                    Text(if (showTable) "Show Chart" else "Show as Table")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
