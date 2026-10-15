package com.tapledger.ui.screens.statement.components

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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
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
import androidx.compose.ui.unit.sp
import com.tapledger.core.model.MoneyFormatter
import com.tapledger.ui.charts.model.ChartState
import com.tapledger.ui.charts.model.ReconciliationBridgeModel
import com.tapledger.ui.charts.render.WaterfallRender
import com.tapledger.ui.charts.render.WaterfallStep
import com.tapledger.ui.theme.ChartPalette
import com.tapledger.ui.theme.Spacing

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.ui.semantics.stateDescription
import com.tapledger.ui.charts.model.ReconciliationBridgeStep
import com.tapledger.ui.charts.render.ChartDetailStrip
import com.tapledger.ui.charts.render.ChartPatternInfoButton
import com.tapledger.ui.charts.render.ChartPatternLegendSheet
import com.tapledger.ui.charts.render.ChartPatternType
import com.tapledger.ui.charts.render.ChartTooltipItem
import com.tapledger.ui.charts.render.rememberIsCompactChart
import com.tapledger.ui.charts.util.ChartSummaryHelper

/**
 * V12 Reconciliation Bridge Card per Flow.md §17.2 V12.
 * Waterfall: AppBalance(end) -> opening diff -> MISSING_IN_APP -> MISSING_IN_STMT -> DISCREPANCY -> TIMING_DIFFERENCE -> StmtClosing.
 * Non-zero residual drawn as a red bar and screen must not show "Reconciled".
 * Enhanced with summary line, compact vertical list-style bridge, pattern explanations, and detail strip.
 */
@Composable
fun ReconciliationBridgeCard(
    bridgeState: ChartState<ReconciliationBridgeModel>,
    modifier: Modifier = Modifier
) {
    var showTable by remember { mutableStateOf(false) }
    var showPatternSheet by remember { mutableStateOf(false) }
    var selectedStep by remember { mutableStateOf<ReconciliationBridgeStep?>(null) }
    val isCompact = rememberIsCompactChart()

    val summaryLine = remember(bridgeState) {
        if (bridgeState is ChartState.Data) {
            ChartSummaryHelper.summarizeV12Reconciliation(bridgeState.data)
        } else {
            "Waterfall from App Balance to Statement Closing"
        }
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {
                contentDescription = summaryLine
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
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Reconciliation Bridge",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = summaryLine,
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    ChartPatternInfoButton(onClick = { showPatternSheet = true })
                    if (bridgeState is ChartState.Data) {
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
            }

            Spacer(modifier = Modifier.height(Spacing.sm))

            when (bridgeState) {
                is ChartState.Loading -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp),
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
                            text = bridgeState.message,
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
                            text = bridgeState.message,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
                is ChartState.Data -> {
                    val model = bridgeState.data

                    // Status banner
                    if (model.isReconciled) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF009E73).copy(alpha = 0.12f))
                                .padding(horizontal = Spacing.sm, vertical = Spacing.xs),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = Color(0xFF009E73),
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "Reconciled (Residual ₹0.00)",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF005E3C)
                            )
                        }
                    } else {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(ChartPalette.OverflowHatch.copy(alpha = 0.12f))
                                .padding(horizontal = Spacing.sm, vertical = Spacing.xs),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Error,
                                contentDescription = null,
                                tint = ChartPalette.OverflowHatch,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "Unreconciled Residual: ${MoneyFormatter.formatPaiseToRupees(model.residualPaise)}",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = ChartPalette.OverflowHatch
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(Spacing.sm))

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
                                Text("Step", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1.5f))
                                Text("Delta", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                                Text("Running Total", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1.2f))
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            model.tableRows.forEach { row ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 3.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1.5f)) {
                                        Text(row.stepLabel, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                                        if (row.note.isNotEmpty()) {
                                            Text(row.note, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                    Text(row.deltaFormatted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                                    Text(row.runningTotalFormatted, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1.2f))
                                }
                            }
                        }
                    } else {
                        if (isCompact) {
                            // Vertical list-style bridge for compact screens (§17.2 Task 3)
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(Spacing.xs)
                            ) {
                                model.steps.forEachIndexed { index, step ->
                                    val isSelected = selectedStep == step
                                    val isTotal = index == 0 || index == model.steps.lastIndex
                                    val stepColor = when {
                                        step.isResidual -> ChartPalette.OverflowHatch
                                        isTotal -> MaterialTheme.colorScheme.primary
                                        step.deltaPaise >= 0L -> Color(0xFF009E73)
                                        else -> Color(0xFFD55E00)
                                    }

                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(
                                                if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
                                                else Color.Transparent
                                            )
                                            .defaultMinSize(minHeight = 48.dp)
                                            .clickable { selectedStep = if (isSelected) null else step }
                                            .semantics {
                                                stateDescription = "${step.label}: ${MoneyFormatter.formatPaiseToRupees(step.runningTotalPaise)}"
                                            }
                                            .padding(horizontal = 8.dp, vertical = 6.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(8.dp)
                                                        .clip(RoundedCornerShape(2.dp))
                                                        .background(stepColor)
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(
                                                    text = step.label,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    fontWeight = if (isTotal) FontWeight.Bold else FontWeight.Medium,
                                                    color = if (step.isResidual) ChartPalette.OverflowHatch else MaterialTheme.colorScheme.onSurface
                                                )
                                            }

                                            Column(horizontalAlignment = Alignment.End) {
                                                if (!isTotal) {
                                                    val deltaStr = if (step.deltaPaise >= 0) "+${MoneyFormatter.formatPaiseToRupees(step.deltaPaise)}" else MoneyFormatter.formatPaiseToRupees(step.deltaPaise)
                                                    Text(
                                                        text = deltaStr,
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = stepColor,
                                                        fontWeight = FontWeight.SemiBold
                                                    )
                                                }
                                                Text(
                                                    text = MoneyFormatter.formatPaiseToRupees(step.runningTotalPaise),
                                                    style = MaterialTheme.typography.bodySmall,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        } else {
                            // Waterfall Visual Render
                            val totalColor = MaterialTheme.colorScheme.primary
                            val waterfallSteps = remember(model.steps, totalColor) {
                                model.steps.mapIndexed { index, step ->
                                    val isTotal = index == 0 || index == model.steps.lastIndex
                                    val color = when {
                                        step.isResidual -> ChartPalette.OverflowHatch
                                        isTotal -> totalColor
                                        step.deltaPaise >= 0L -> Color(0xFF009E73)
                                        else -> Color(0xFFD55E00)
                                    }
                                    WaterfallStep(
                                        label = step.label,
                                        deltaPaise = step.deltaPaise,
                                        runningTotalPaise = step.runningTotalPaise,
                                        color = color,
                                        isTotal = isTotal
                                    )
                                }
                            }

                            WaterfallRender(
                                steps = waterfallSteps,
                                modifier = Modifier.fillMaxWidth(),
                                height = 180.dp
                            )

                            Spacer(modifier = Modifier.height(Spacing.xs))

                            // Step Labels below waterfall
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                model.steps.forEach { step ->
                                    Text(
                                        text = step.label,
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                        color = if (step.isResidual) ChartPalette.OverflowHatch else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        // Detail Strip (§17.2 D10)
                        val detailItem = selectedStep?.let { step ->
                            val deltaStr = if (step.deltaPaise >= 0) "+${MoneyFormatter.formatPaiseToRupees(step.deltaPaise)}" else MoneyFormatter.formatPaiseToRupees(step.deltaPaise)
                            ChartTooltipItem(
                                id = step.label,
                                title = step.label,
                                valueFormatted = MoneyFormatter.formatPaiseToRupees(step.runningTotalPaise),
                                context = "Delta: $deltaStr",
                                statusIcon = if (step.isResidual) Icons.Default.Error else Icons.Default.CheckCircle,
                                statusColor = if (step.isResidual) ChartPalette.OverflowHatch else Color(0xFF009E73)
                            )
                        }
                        ChartDetailStrip(
                            item = detailItem,
                            defaultSummary = summaryLine,
                            onClear = { selectedStep = null }
                        )
                    }
                }
            }
        }
    }

    if (showPatternSheet) {
        ChartPatternLegendSheet(
            patterns = listOf(ChartPatternType.HATCHED_OVERFLOW),
            onDismissRequest = { showPatternSheet = false }
        )
    }
}
