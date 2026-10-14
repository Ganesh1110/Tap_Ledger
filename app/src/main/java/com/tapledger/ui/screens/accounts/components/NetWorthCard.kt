package com.tapledger.ui.screens.accounts.components

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
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.tapledger.core.model.MoneyFormatter
import com.tapledger.ui.charts.builder.NetWorthRange
import com.tapledger.ui.charts.model.ChartState
import com.tapledger.ui.charts.model.NetWorthChartModel
import com.tapledger.ui.charts.model.NetWorthPoint
import com.tapledger.ui.charts.render.NetWorthChartRender
import com.tapledger.ui.components.MoneyIndicator
import com.tapledger.ui.components.MoneySemantic
import com.tapledger.ui.components.MoneyText
import com.tapledger.ui.theme.ChartPalette
import com.tapledger.ui.theme.ExpenseRed
import com.tapledger.ui.theme.IncomeGreen
import com.tapledger.ui.theme.Spacing
import com.tapledger.ui.theme.rememberAnimatedMoney

/**
 * V9 Net worth line/area card per Flow.md §17.2 V9.
 * Ranges: 3M / 6M / 1Y / All.
 * Horizontal-drag scrub shows date and ₹ value with vertical scroll pass-through.
 * Stacked Assets/Liabilities toggle, accessibility table view, and unassigned exclusion notice.
 */
@Composable
fun NetWorthCard(
    chartState: ChartState<NetWorthChartModel>,
    currentNetWorthPaise: Long,
    totalAssetsPaise: Long,
    totalLiabilitiesPaise: Long,
    selectedRange: NetWorthRange,
    isStacked: Boolean,
    showTable: Boolean,
    onSelectRange: (NetWorthRange) -> Unit,
    onToggleStacked: () -> Unit,
    onToggleShowTable: () -> Unit,
    modifier: Modifier = Modifier
) {
    val animatedNetWorth by rememberAnimatedMoney(targetPaise = currentNetWorthPaise)
    var scrubbedPoint by remember { mutableStateOf<NetWorthPoint?>(null) }

    // Dynamic values: live scrubbed point takes precedence smoothly without shifting layout
    val activeNetWorth = scrubbedPoint?.netWorthPaise ?: animatedNetWorth
    val activeAssets = scrubbedPoint?.assetsPaise ?: totalAssetsPaise
    val activeLiabilities = scrubbedPoint?.liabilitiesPaise ?: totalLiabilitiesPaise

    // Compute period trend (difference between earliest point in selected range and current Net Worth)
    val trendInfo = remember(chartState, currentNetWorthPaise) {
        if (chartState is ChartState.Data && chartState.data.points.size >= 2) {
            val firstPt = chartState.data.points.first()
            currentNetWorthPaise - firstPt.netWorthPaise
        } else null
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {
                if (chartState is ChartState.Data) {
                    contentDescription = chartState.data.contentDescription
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
            // 1. Header: Title, Active Amount, Trend/Scrub Badge, and View Toggles
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "TOTAL NET WORTH",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        if (scrubbedPoint != null) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.primaryContainer
                            ) {
                                Text(
                                    text = scrubbedPoint?.label ?: "",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        } else if (trendInfo != null) {
                            val isPos = trendInfo > 0L
                            val isNeg = trendInfo < 0L
                            val badgeColor = if (isPos) IncomeGreen else if (isNeg) ExpenseRed else MaterialTheme.colorScheme.onSurfaceVariant
                            val badgeBg = if (isPos) IncomeGreen.copy(alpha = 0.12f) else if (isNeg) ExpenseRed.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceContainerHigh
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = badgeBg
                            ) {
                                val text = if (isPos) "▲ +${MoneyFormatter.formatPaiseToRupees(trendInfo)}"
                                else if (isNeg) "▼ -${MoneyFormatter.formatPaiseToRupees(kotlin.math.abs(trendInfo))}"
                                else "— Stable"
                                Text(
                                    text = text,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = badgeColor,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    MoneyText(
                        amountPaise = activeNetWorth,
                        style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                        semantic = if (activeNetWorth >= 0) MoneySemantic.Income else MoneySemantic.Expense
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onToggleStacked) {
                        Icon(
                            imageVector = if (isStacked) Icons.AutoMirrored.Filled.ShowChart else Icons.Default.Layers,
                            contentDescription = if (isStacked) "Show Net Worth Line" else "Show Stacked Assets/Liabilities",
                            tint = if (isStacked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = onToggleShowTable) {
                        Icon(
                            imageVector = Icons.Default.TableChart,
                            contentDescription = if (showTable) "Show Chart View" else "Show Table View",
                            tint = if (showTable) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(Spacing.xs))

            // 2. Structured Breakdown: Assets & Liabilities in a Clean Balanced Capsule
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.md, vertical = Spacing.sm),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(IncomeGreen, RoundedCornerShape(4.dp))
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Column {
                            Text(
                                text = "Total Assets",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            MoneyText(
                                amountPaise = activeAssets,
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                                semantic = MoneySemantic.Income
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .height(24.dp)
                            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    )

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "Total Liabilities",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            MoneyText(
                                amountPaise = activeLiabilities,
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                                semantic = MoneySemantic.Debt,
                                indicator = MoneyIndicator.Owed
                            )
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(ExpenseRed, RoundedCornerShape(4.dp))
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(Spacing.sm))

            // 3. Range Selector Chips (3M / 6M / 1Y / All)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                NetWorthRange.values().forEach { range ->
                    val label = when (range) {
                        NetWorthRange.M3 -> "3M"
                        NetWorthRange.M6 -> "6M"
                        NetWorthRange.Y1 -> "1Y"
                        NetWorthRange.ALL -> "All"
                    }
                    FilterChip(
                        selected = selectedRange == range,
                        onClick = { onSelectRange(range) },
                        label = { Text(label, style = MaterialTheme.typography.labelSmall) }
                    )
                }
            }

            // Optional Stacked Legend Banner
            if (isStacked && !showTable) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = Spacing.xs),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(modifier = Modifier.size(8.dp).background(IncomeGreen, RoundedCornerShape(4.dp)))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Assets", style = MaterialTheme.typography.labelSmall, color = IncomeGreen, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.width(16.dp))
                    Box(modifier = Modifier.size(8.dp).background(ExpenseRed, RoundedCornerShape(4.dp)))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Liabilities", style = MaterialTheme.typography.labelSmall, color = ExpenseRed, fontWeight = FontWeight.SemiBold)
                }
            }

            Spacer(modifier = Modifier.height(Spacing.xs))

            // 4. Content Area: Table vs Chart
            when (chartState) {
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
                            text = chartState.message,
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
                            text = chartState.message,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
                is ChartState.Data -> {
                    val data = chartState.data

                    if (showTable) {
                        // Accessible Table View (§17.2 Accessibility)
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surfaceContainer)
                                .padding(Spacing.sm),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Period", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                Text("Assets", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                Text("Liabilities", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                Text("Net Worth", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                            }
                            HorizontalDivider()
                            data.tableRows.forEach { row ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(row.dateLabel, style = MaterialTheme.typography.bodySmall)
                                    Text(row.assetsFormatted, style = MaterialTheme.typography.bodySmall, color = IncomeGreen)
                                    Text(row.liabilitiesFormatted, style = MaterialTheme.typography.bodySmall, color = ExpenseRed)
                                    Text(row.netWorthFormatted, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    } else {
                        // Graphical Line / Area Chart
                        NetWorthChartRender(
                            points = data.points,
                            isStacked = isStacked,
                            netWorthColor = ChartPalette.NeedsBase,
                            assetsColor = IncomeGreen,
                            liabilitiesColor = ExpenseRed,
                            height = 205.dp,
                            onScrub = { scrubbedPoint = it }
                        )
                    }

                    // Unassigned transactions excluded notice (§17.2)
                    data.unassignedExcludedMessage?.let { msg ->
                        Spacer(modifier = Modifier.height(Spacing.xs))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(top = 4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = msg,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}
