package com.tapledger.ui.screens.dashboard.components

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.filled.Error
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tapledger.core.model.MoneyFormatter
import com.tapledger.ui.charts.model.ChartState
import com.tapledger.ui.charts.model.DashboardHeroRingModel
import com.tapledger.ui.charts.render.RingChartRender
import com.tapledger.ui.charts.util.ChartMath
import com.tapledger.ui.components.MoneySemantic
import com.tapledger.ui.components.MoneyText
import com.tapledger.ui.theme.ChartPalette
import com.tapledger.ui.theme.ExpenseRed
import com.tapledger.ui.theme.IncomeGreen
import com.tapledger.ui.theme.Spacing
import com.tapledger.ui.theme.WarningAmber

import androidx.compose.material.icons.filled.CheckCircle
import com.tapledger.ui.charts.render.rememberIsCompactChart
import com.tapledger.ui.charts.util.ChartSummaryHelper

/**
 * V1 Dashboard Hero Ring Card per Flow.md §17.2 V1.
 * Shows hero ring with spent vs budget, remaining in center, "X% used",
 * pace marker tick (D3), plain-language summary line, status icons, and Net Worth summary.
 */
@Composable
fun DashboardHeroRingCard(
    heroRingState: ChartState<DashboardHeroRingModel>,
    netWorthPaise: Long?,
    modifier: Modifier = Modifier
) {
    val isCompact = rememberIsCompactChart()
    val summaryLine = if (heroRingState is ChartState.Data) {
        ChartSummaryHelper.summarizeV1HeroRing(heroRingState.data)
    } else {
        "Monthly Spend vs Budget"
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {
                val nwText = if (netWorthPaise != null) ". Net worth: ${MoneyFormatter.formatPaiseToRupees(netWorthPaise)}" else ""
                contentDescription = summaryLine + nwText
            },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.lg)
        ) {
            // Summary Line & Net Worth Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Net Worth",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (netWorthPaise != null) {
                        MoneyText(
                            amountPaise = netWorthPaise,
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            semantic = if (netWorthPaise >= 0) MoneySemantic.Income else MoneySemantic.Expense
                        )
                    }
                }

                if (heroRingState is ChartState.Data) {
                    val statusIcon = when {
                        heroRingState.data.isExceeded -> Icons.Default.Error
                        heroRingState.data.isWarning -> Icons.Default.Warning
                        else -> Icons.Default.CheckCircle
                    }
                    val statusColor = when {
                        heroRingState.data.isExceeded -> ExpenseRed
                        heroRingState.data.isWarning -> WarningAmber
                        else -> IncomeGreen
                    }
                    Icon(
                        imageVector = statusIcon,
                        contentDescription = null,
                        tint = statusColor,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            if (heroRingState is ChartState.Data) {
                Spacer(modifier = Modifier.height(Spacing.xs))
                Text(
                    text = summaryLine,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.height(Spacing.md))

            when (heroRingState) {
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
                            .height(140.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = heroRingState.message,
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
                            text = heroRingState.message,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Center
                        )
                    }
                }
                is ChartState.Data -> {
                    val data = heroRingState.data
                    val ringColor = when {
                        data.isExceeded -> ExpenseRed
                        data.isWarning -> WarningAmber
                        else -> MaterialTheme.colorScheme.primary
                    }

                    val ringSize = if (isCompact) 140.dp else 150.dp
                    val ringStroke = if (isCompact) 18.dp else 14.dp

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Ring on the left
                        RingChartRender(
                            percentageUsedBp = data.percentageBp,
                            ringColor = ringColor,
                            trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                            size = ringSize,
                            strokeWidth = ringStroke,
                            paceBp = data.paceBp,
                            centerContent = {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = if (data.remainingPaise >= 0L) "Left" else "Over",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = MoneyFormatter.formatCompactIndian(data.remainingPaise),
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                        color = if (data.remainingPaise >= 0L) IncomeGreen else ExpenseRed
                                    )
                                    Text(
                                        text = "${ChartMath.formatBasisPoints(data.percentageBp)} used",
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                        color = ringColor,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        )

                        Spacer(modifier = Modifier.width(Spacing.md))

                        // Stats Breakdown on the right
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(Spacing.xs)
                        ) {
                            Column {
                                Text(
                                    text = "Spent (Needs + Wants)",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = MoneyFormatter.formatPaiseToRupees(data.spentPaise),
                                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }

                            Column {
                                Text(
                                    text = "Budget Target",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = MoneyFormatter.formatPaiseToRupees(data.budgetPaise),
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Normal),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "${ChartMath.formatBasisPoints(data.percentageBp)} used",
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                    color = ringColor
                                )
                                if (data.isExceeded) {
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Icon(
                                        imageVector = Icons.Default.Error,
                                        contentDescription = "Budget Exceeded",
                                        tint = ExpenseRed,
                                        modifier = Modifier.size(16.dp)
                                    )
                                } else if (data.isWarning) {
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Icon(
                                        imageVector = Icons.Default.Warning,
                                        contentDescription = "Budget Warning",
                                        tint = WarningAmber,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }

                            if (data.paceBp != null) {
                                Text(
                                    text = "Pace: ${ChartMath.formatBasisPoints(data.paceBp)}",
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
}
