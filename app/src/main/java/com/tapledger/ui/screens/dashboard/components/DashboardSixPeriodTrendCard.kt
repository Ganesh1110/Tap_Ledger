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
import com.tapledger.ui.charts.model.ChartState
import com.tapledger.ui.charts.model.SixPeriodTrendModel
import com.tapledger.ui.charts.render.ClusteredBarChartRender
import com.tapledger.ui.theme.ChartPalette
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Surface
import com.tapledger.core.model.MoneyFormatter
import com.tapledger.ui.components.MoneySemantic
import com.tapledger.ui.components.MoneyText
import com.tapledger.ui.theme.ExpenseRed
import com.tapledger.ui.theme.IncomeGreen
import com.tapledger.ui.theme.Spacing

/**
 * V4 Six-Period Trend Card per Flow.md §17.2 V4.
 * Clustered bars for actual income, spent, and saved across the last 6 periods,
 * plus a savings-rate line. OPEN period is rendered hatched and labeled "In progress".
 * Supports "Show as table" tabular toggle.
 */
@Composable
fun DashboardSixPeriodTrendCard(
    trendState: ChartState<SixPeriodTrendModel>,
    modifier: Modifier = Modifier,
    initiallyExpanded: Boolean = true,
    title: String = "Cash Flow Trend",
    subtitle: String = "6-period income, spent, & saved"
) {
    var isExpanded by remember { mutableStateOf(initiallyExpanded) }
    var showTable by remember { mutableStateOf(false) }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {
                if (trendState is ChartState.Data) {
                    contentDescription = trendState.data.contentDescription
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
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { isExpanded = !isExpanded }) {
                        Icon(
                            imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                            contentDescription = if (isExpanded) "Collapse Trend" else "Expand Trend"
                        )
                    }
                }
            }

            AnimatedVisibility(visible = isExpanded) {
                Column(modifier = Modifier.fillMaxWidth().padding(top = Spacing.sm)) {
                    when (trendState) {
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
                                    text = trendState.message,
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
                                    text = trendState.message,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                        is ChartState.Data -> {
                            val data = trendState.data
                            var selectedIndex by remember(data.periods) { mutableStateOf(data.periods.size - 1) }
                            val activePeriod = data.periods.getOrNull(selectedIndex) ?: data.periods.lastOrNull()

                            if (!showTable) {
                                // 1. Active Period Structured Breakdown Capsule
                                activePeriod?.let { p ->
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = MaterialTheme.colorScheme.surfaceContainer,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(bottom = Spacing.sm)
                                    ) {
                                        Column(modifier = Modifier.padding(Spacing.sm)) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Text(
                                                        text = formatPeriodLabel(p.label),
                                                        style = MaterialTheme.typography.titleSmall,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                    if (p.isInProgress) {
                                                        Spacer(modifier = Modifier.width(6.dp))
                                                        Surface(
                                                            shape = RoundedCornerShape(4.dp),
                                                            color = ChartPalette.LightWants.copy(alpha = 0.15f)
                                                        ) {
                                                            Text(
                                                                text = "In progress",
                                                                style = MaterialTheme.typography.labelSmall,
                                                                color = ChartPalette.LightWants,
                                                                fontWeight = FontWeight.Bold,
                                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                            )
                                                        }
                                                    }
                                                }

                                                p.savingsRateBp?.let { bp ->
                                                    Surface(
                                                        shape = RoundedCornerShape(4.dp),
                                                        color = ChartPalette.LightWants.copy(alpha = 0.15f)
                                                    ) {
                                                        Text(
                                                            text = "Rate: ${bp / 100}%",
                                                            style = MaterialTheme.typography.labelSmall,
                                                            color = ChartPalette.LightWants,
                                                            fontWeight = FontWeight.Bold,
                                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                        )
                                                    }
                                                }
                                            }

                                            Spacer(modifier = Modifier.height(Spacing.xs))

                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                // Income
                                                Column {
                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                        Box(modifier = Modifier.size(6.dp).background(IncomeGreen, RoundedCornerShape(2.dp)))
                                                        Spacer(modifier = Modifier.width(4.dp))
                                                        Text("Income", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                    }
                                                    MoneyText(
                                                        amountPaise = p.incomePaise,
                                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                                        semantic = MoneySemantic.Income
                                                    )
                                                }

                                                // Spent
                                                Column {
                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                        Box(modifier = Modifier.size(6.dp).background(ExpenseRed, RoundedCornerShape(2.dp)))
                                                        Spacer(modifier = Modifier.width(4.dp))
                                                        Text("Spent", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                    }
                                                    MoneyText(
                                                        amountPaise = p.spentPaise,
                                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                                        semantic = MoneySemantic.Expense
                                                    )
                                                }

                                                // Saved
                                                Column(horizontalAlignment = Alignment.End) {
                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                        Box(modifier = Modifier.size(6.dp).background(ChartPalette.LightSavings, RoundedCornerShape(2.dp)))
                                                        Spacer(modifier = Modifier.width(4.dp))
                                                        Text("Saved", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                    }
                                                    MoneyText(
                                                        amountPaise = p.savedPaise,
                                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                                        semantic = if (p.savedPaise >= 0) MoneySemantic.Income else MoneySemantic.Expense
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }

                                // 2. Clustered Bars Render
                                ClusteredBarChartRender(
                                    periods = data.periods,
                                    incomeColor = IncomeGreen,
                                    spentColor = ExpenseRed,
                                    savedColor = ChartPalette.LightSavings,
                                    savingsLineColor = ChartPalette.LightWants,
                                    selectedIndex = selectedIndex,
                                    onSelectIndex = { selectedIndex = it },
                                    height = 190.dp
                                )

                                Spacer(modifier = Modifier.height(Spacing.xs))

                                // 3. Period Labels Row
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceAround
                                ) {
                                    data.periods.forEachIndexed { idx, p ->
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            modifier = Modifier.clickable { selectedIndex = idx }
                                        ) {
                                            Text(
                                                text = p.label,
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = if (selectedIndex == idx) FontWeight.Bold else FontWeight.Medium,
                                                color = if (selectedIndex == idx) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            if (p.isInProgress) {
                                                Text(
                                                    text = "In progress",
                                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = androidx.compose.ui.unit.TextUnit(9f, androidx.compose.ui.unit.TextUnitType.Sp)),
                                                    color = ChartPalette.LightWants,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(Spacing.xs))

                                // 4. Legend
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = Spacing.xs),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(modifier = Modifier.size(6.dp).background(IncomeGreen, RoundedCornerShape(2.dp)))
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text("Income", style = MaterialTheme.typography.labelSmall, color = IncomeGreen, fontWeight = FontWeight.SemiBold)
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Box(modifier = Modifier.size(6.dp).background(ExpenseRed, RoundedCornerShape(2.dp)))
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text("Spent", style = MaterialTheme.typography.labelSmall, color = ExpenseRed, fontWeight = FontWeight.SemiBold)
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Box(modifier = Modifier.size(6.dp).background(ChartPalette.LightSavings, RoundedCornerShape(2.dp)))
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text("Saved", style = MaterialTheme.typography.labelSmall, color = ChartPalette.LightSavings, fontWeight = FontWeight.SemiBold)
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Box(modifier = Modifier.size(6.dp).background(ChartPalette.LightWants, RoundedCornerShape(3.dp)))
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text("Savings Rate", style = MaterialTheme.typography.labelSmall, color = ChartPalette.LightWants, fontWeight = FontWeight.SemiBold)
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
                                        Text("Month", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                                        Text("Income", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), textAlign = TextAlign.End)
                                        Text("Spent", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), textAlign = TextAlign.End)
                                        Text("Saved", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), textAlign = TextAlign.End)
                                        Text("Rate", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(0.8f), textAlign = TextAlign.End)
                                    }
                                    HorizontalDivider()
                                    data.tableRows.forEach { row ->
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(row.periodLabel, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                                            Text(row.incomeFormatted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f), textAlign = TextAlign.End)
                                            Text(row.spentFormatted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f), textAlign = TextAlign.End)
                                            Text(row.savedFormatted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f), textAlign = TextAlign.End)
                                            Text(row.savingsRateFormatted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(0.8f), textAlign = TextAlign.End)
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

private fun formatPeriodLabel(raw: String): String {
    val parts = raw.split("/")
    if (parts.size == 2) {
        val monthNum = parts[0].toIntOrNull()
        if (monthNum != null && monthNum in 1..12) {
            val names = arrayOf("January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December")
            return "${names[monthNum - 1]} 20${parts[1]}"
        }
    }
    return raw
}
