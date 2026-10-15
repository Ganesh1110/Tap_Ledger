package com.tapledger.ui.screens.diagnostics.components

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
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
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
import androidx.compose.ui.unit.dp
import com.tapledger.ui.charts.model.ChartState
import com.tapledger.ui.charts.model.DiagnosticsSeverityBarModel
import com.tapledger.ui.theme.Spacing
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.material.icons.filled.Error
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.stateDescription
import com.tapledger.ui.charts.render.ChartDetailStrip
import com.tapledger.ui.charts.render.ChartTooltipItem
import com.tapledger.ui.charts.render.rememberIsCompactChart
import com.tapledger.ui.charts.util.ChartSummaryHelper

/**
 * V13 Diagnostics Severity Bar Card per Flow.md §17.2 V13.
 * Count of violations by CRITICAL / ERROR / WARNING from DataIntegrityChecker.
 * Enhanced with summary line, interactive badges with >=48dp touch targets, and detail strip.
 */
@Composable
fun DiagnosticsSeverityBarCard(
    severityBarState: ChartState<DiagnosticsSeverityBarModel>,
    modifier: Modifier = Modifier
) {
    val isCompact = rememberIsCompactChart()
    var selectedSeverity by remember { mutableStateOf<String?>(null) }

    val summaryLine = remember(severityBarState) {
        if (severityBarState is ChartState.Data) {
            ChartSummaryHelper.summarizeV13Diagnostics(severityBarState.data)
        } else {
            "Invariant violations grouped by severity"
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
            Text(
                text = "Diagnostics Severity Breakdown",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = summaryLine,
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(Spacing.sm))

            when (severityBarState) {
                is ChartState.Loading -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(60.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    }
                }
                is ChartState.Empty -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = Spacing.xs),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = severityBarState.message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                is ChartState.Error -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = Spacing.xs),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error
                        )
                        Text(
                            text = severityBarState.message,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
                is ChartState.Data -> {
                    val model = severityBarState.data
                    val criticalColor = Color(0xFFBA1A1A)
                    val errorColor = Color(0xFFD55E00)
                    val warningColor = Color(0xFFE69F00)
                    val healthyColor = Color(0xFF009E73)

                    if (model.totalIssues == 0) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(healthyColor.copy(alpha = 0.12f))
                                .padding(horizontal = Spacing.sm, vertical = Spacing.xs),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = healthyColor,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "Zero Invariant Violations (Clean Ledger)",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF005E3C)
                            )
                        }
                    } else {
                        // Segmented bar via render primitive
                        com.tapledger.ui.charts.render.DiagnosticsSeverityBarRender(
                            criticalCount = model.criticalCount,
                            errorCount = model.errorCount,
                            warningCount = model.warningCount
                        )

                        Spacer(modifier = Modifier.height(Spacing.sm))

                        // Badges Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            SeverityBadge(
                                label = "Critical",
                                count = model.criticalCount,
                                color = criticalColor,
                                isSelected = selectedSeverity == "Critical",
                                onClick = { selectedSeverity = if (selectedSeverity == "Critical") null else "Critical" }
                            )
                            SeverityBadge(
                                label = "Error",
                                count = model.errorCount,
                                color = errorColor,
                                isSelected = selectedSeverity == "Error",
                                onClick = { selectedSeverity = if (selectedSeverity == "Error") null else "Error" }
                            )
                            SeverityBadge(
                                label = "Warning",
                                count = model.warningCount,
                                color = warningColor,
                                isSelected = selectedSeverity == "Warning",
                                onClick = { selectedSeverity = if (selectedSeverity == "Warning") null else "Warning" }
                            )
                        }

                        // Detail Strip (§17.2 D10)
                        val detailItem = selectedSeverity?.let { sev ->
                            val count = when (sev) {
                                "Critical" -> model.criticalCount
                                "Error" -> model.errorCount
                                else -> model.warningCount
                            }
                            val color = when (sev) {
                                "Critical" -> criticalColor
                                "Error" -> errorColor
                                else -> warningColor
                            }
                            val icon = when (sev) {
                                "Critical" -> Icons.Default.Error
                                "Error" -> Icons.Default.Error
                                else -> Icons.Default.Warning
                            }
                            ChartTooltipItem(
                                id = sev,
                                title = "$sev Severity",
                                valueFormatted = "$count issues detected",
                                context = if (count > 0) "Requires review in Diagnostics tab" else "No $sev issues present",
                                statusIcon = icon,
                                statusColor = color
                            )
                        }
                        ChartDetailStrip(
                            item = detailItem,
                            defaultSummary = summaryLine,
                            onClear = { selectedSeverity = null }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SeverityBadge(
    label: String,
    count: Int,
    color: Color,
    isSelected: Boolean = false,
    onClick: () -> Unit = {}
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                else Color.Transparent
            )
            .defaultMinSize(minHeight = 48.dp, minWidth = 48.dp)
            .clickable(onClick = onClick)
            .semantics {
                stateDescription = "$label: $count"
            }
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(if (count > 0) color else Color.Gray.copy(alpha = 0.3f))
        )
        Text(
            text = "$label: $count",
            style = MaterialTheme.typography.bodySmall,
            fontWeight = if (count > 0) FontWeight.Bold else FontWeight.Normal,
            color = if (count > 0) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
