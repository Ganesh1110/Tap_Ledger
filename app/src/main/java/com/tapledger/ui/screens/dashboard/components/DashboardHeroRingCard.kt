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

/**
 * V1 Dashboard Hero Ring Card per Flow.md §17.2 V1.
 * Shows hero ring with spent vs budget, remaining in center, pace marker tick (D3),
 * and Net Worth summary.
 */
@Composable
fun DashboardHeroRingCard(
    heroRingState: ChartState<DashboardHeroRingModel>,
    netWorthPaise: Long?,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {
                if (heroRingState is ChartState.Data) {
                    contentDescription = heroRingState.data.contentDescription
                }
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
            // Net Worth Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
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
                            size = 150.dp,
                            strokeWidth = 14.dp,
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
