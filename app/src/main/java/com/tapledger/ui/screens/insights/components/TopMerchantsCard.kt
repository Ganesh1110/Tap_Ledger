package com.tapledger.ui.screens.insights.components

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.tapledger.core.model.MoneyFormatter
import com.tapledger.ui.charts.model.ChartState
import com.tapledger.ui.charts.model.TopMerchantsModel
import com.tapledger.ui.charts.render.TopMerchantBarRender
import com.tapledger.ui.charts.util.ChartMath
import com.tapledger.ui.theme.ChartPalette
import com.tapledger.ui.theme.Spacing

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.material.icons.filled.CheckCircle
import com.tapledger.ui.charts.render.ChartDetailStrip
import com.tapledger.ui.charts.render.ChartTooltipItem
import com.tapledger.ui.charts.render.rememberIsCompactChart
import com.tapledger.ui.charts.util.ChartSummaryHelper
import com.tapledger.ui.charts.model.TopMerchantItem

/**
 * V8 Top Merchants Card per Flow.md §17.2 V8.
 * Top 8 merchants by CATEGORIZED expense with horizontal proportion bars and tabular toggle.
 * Adapts to Top 5 in compact mode with >=48dp touch targets and detail strip.
 */
@Composable
fun TopMerchantsCard(
    merchantsState: ChartState<TopMerchantsModel>,
    modifier: Modifier = Modifier
) {
    var showTable by remember { mutableStateOf(false) }
    var selectedMerchant by remember { mutableStateOf<TopMerchantItem?>(null) }
    val isCompact = rememberIsCompactChart()

    val summaryLine = remember(merchantsState) {
        if (merchantsState is ChartState.Data) {
            ChartSummaryHelper.summarizeV8TopMerchants(merchantsState.data)
        } else {
            "Top merchants by categorized expense"
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
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Top Merchants",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = summaryLine,
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                IconButton(onClick = { showTable = !showTable }) {
                    Icon(
                        imageVector = Icons.Default.TableChart,
                        contentDescription = if (showTable) "Hide Table" else "Show as Table",
                        tint = if (showTable) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(Spacing.sm))

            when (merchantsState) {
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
                            .padding(vertical = Spacing.xl),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = merchantsState.message,
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
                            .padding(vertical = Spacing.md),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Error: ${merchantsState.message}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Center
                        )
                    }
                }

                is ChartState.Data -> {
                    val data = merchantsState.data
                    if (data.merchants.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = Spacing.xl),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "No categorized expenses recorded",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                    } else {
                        // Total expense summary block
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.5f),
                                    shape = RoundedCornerShape(12.dp)
                                )
                                .padding(horizontal = Spacing.md, vertical = Spacing.sm),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Total Outflow Tracked",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = MoneyFormatter.formatPaiseToRupees(data.totalCategorizedExpensePaise),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        Spacer(modifier = Modifier.height(Spacing.md))

                        if (showTable) {
                            // Tabular view
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "# Merchant",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Text(
                                        text = "Txs",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.width(40.dp)
                                    )
                                    Text(
                                        text = "Share",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        textAlign = TextAlign.End,
                                        modifier = Modifier.width(55.dp)
                                    )
                                    Text(
                                        text = "Amount",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        textAlign = TextAlign.End,
                                        modifier = Modifier.width(85.dp)
                                    )
                                }
                                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                                data.merchants.forEachIndexed { index, m ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 6.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "${index + 1}. ${m.merchantName}",
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.Medium,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Text(
                                            text = "${m.transactionCount}",
                                            style = MaterialTheme.typography.bodySmall,
                                            textAlign = TextAlign.Center,
                                            modifier = Modifier.width(40.dp)
                                        )
                                        Text(
                                            text = ChartMath.formatBasisPoints(m.percentageBp),
                                            style = MaterialTheme.typography.bodySmall,
                                            textAlign = TextAlign.End,
                                            modifier = Modifier.width(55.dp)
                                        )
                                        Text(
                                            text = m.amountFormatted,
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.Bold,
                                            textAlign = TextAlign.End,
                                            modifier = Modifier.width(85.dp)
                                        )
                                    }
                                }
                            }
                        } else {
                            // Visual bar list
                            val visibleMerchants = if (isCompact) data.merchants.take(5) else data.merchants
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(Spacing.sm)
                            ) {
                                visibleMerchants.forEachIndexed { index, m ->
                                    val isSelected = selectedMerchant == m
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(
                                                if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
                                                else androidx.compose.ui.graphics.Color.Transparent
                                            )
                                            .defaultMinSize(minHeight = 48.dp)
                                            .clickable { selectedMerchant = if (isSelected) null else m }
                                            .semantics {
                                                stateDescription = "${m.amountFormatted}, ${ChartMath.formatBasisPoints(m.percentageBp)}"
                                            }
                                            .padding(vertical = 4.dp, horizontal = 4.dp)
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
                                                        .size(20.dp)
                                                        .clip(CircleShape)
                                                        .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Text(
                                                        text = "${index + 1}",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        fontWeight = FontWeight.Bold,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                                Spacer(modifier = Modifier.width(Spacing.xs))
                                                Text(
                                                    text = m.merchantName,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    fontWeight = FontWeight.SemiBold,
                                                    maxLines = 1
                                                )
                                                Spacer(modifier = Modifier.width(Spacing.xs))
                                                Text(
                                                    text = "(${m.transactionCount})",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }

                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(
                                                    text = m.amountFormatted,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    fontWeight = FontWeight.Bold
                                                )
                                                Spacer(modifier = Modifier.width(Spacing.xs))
                                                Text(
                                                    text = "(${ChartMath.formatBasisPoints(m.percentageBp)})",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(4.dp))
                                        TopMerchantBarRender(
                                            percentageBp = m.percentageBp,
                                            barColor = if (isSelected) MaterialTheme.colorScheme.primary else ChartPalette.NeedsBase,
                                            height = if (isCompact) 12.dp else 8.dp
                                        )
                                    }
                                }
                            }

                            // Detail Strip (§17.2 D10)
                            ChartDetailStrip(
                                item = selectedMerchant?.let { m ->
                                    ChartTooltipItem(
                                        id = m.merchantName,
                                        title = m.merchantName,
                                        valueFormatted = m.amountFormatted,
                                        context = "${m.transactionCount} transactions • ${ChartMath.formatBasisPoints(m.percentageBp)} of total spend",
                                        statusIcon = Icons.Default.CheckCircle,
                                        statusColor = ChartPalette.NeedsBase
                                    )
                                },
                                defaultSummary = summaryLine,
                                onClear = { selectedMerchant = null }
                            )
                        }
                    }
                }
            }
        }
    }
}
