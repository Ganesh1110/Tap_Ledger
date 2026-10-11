package com.tapledger.ui.screens.accounts.components

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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tapledger.core.model.MoneyFormatter
import com.tapledger.ui.charts.model.AccountCompositionModel
import com.tapledger.ui.charts.model.ChartState
import com.tapledger.ui.charts.model.UtilizationZone
import com.tapledger.ui.charts.render.CreditCardGaugeRender
import com.tapledger.ui.charts.render.DivergingBarRender
import com.tapledger.ui.charts.util.ChartMath
import com.tapledger.ui.theme.ExpenseRed
import com.tapledger.ui.theme.IncomeGreen
import com.tapledger.ui.theme.Spacing
import com.tapledger.ui.theme.WarningAmber

/**
 * V10 Account composition card per Flow.md §17.2 V10.
 * Diverging bars per account (assets right, liabilities left, sorted by |balance|)
 * and credit-card utilization gauges with <30% normal, 30-70% caution, >70% high zones.
 */
@Composable
fun AccountCompositionCard(
    compositionState: ChartState<AccountCompositionModel>,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {
                if (compositionState is ChartState.Data) {
                    contentDescription = compositionState.data.contentDescription
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
            Text(
                text = "Account Composition",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Diverging balance distribution (Assets vs Liabilities)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(Spacing.md))

            when (compositionState) {
                is ChartState.Loading -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(32.dp))
                    }
                }
                is ChartState.Empty -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(100.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = compositionState.message,
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
                            text = compositionState.message,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
                is ChartState.Data -> {
                    val data = compositionState.data
                    val maxAbs = data.accounts.maxOfOrNull { it.absBalancePaise } ?: 1L

                    // 1. Diverging Bars
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        data.accounts.forEach { acc ->
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = acc.accountName,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        text = MoneyFormatter.formatPaiseToRupees(acc.balancePaise),
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (acc.isAsset) IncomeGreen else ExpenseRed
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                DivergingBarRender(
                                    isAsset = acc.isAsset,
                                    absBalancePaise = acc.absBalancePaise,
                                    maxAbsPaise = maxAbs,
                                    assetColor = IncomeGreen,
                                    liabilityColor = ExpenseRed,
                                    height = 8.dp
                                )
                            }
                        }
                    }

                    // 2. Credit Card Utilization Section (§6.2 & §17.2 V10)
                    if (data.creditCardGauges.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(Spacing.md))
                        HorizontalDivider()
                        Spacer(modifier = Modifier.height(Spacing.sm))

                        Text(
                            text = "Credit Card Utilization",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(Spacing.xs))

                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            data.creditCardGauges.forEach { cc ->
                                val (zoneIcon, zoneLabel, zoneColor) = when (cc.zone) {
                                    UtilizationZone.NORMAL -> Triple(Icons.Default.CheckCircle, "Normal (<30%)", IncomeGreen)
                                    UtilizationZone.CAUTION -> Triple(Icons.Default.Warning, "Caution (30-70%)", WarningAmber)
                                    UtilizationZone.HIGH -> Triple(Icons.Default.Error, "High (>70%)", ExpenseRed)
                                }

                                Column(modifier = Modifier.fillMaxWidth()) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = cc.accountName,
                                                style = MaterialTheme.typography.bodySmall,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Icon(
                                                imageVector = zoneIcon,
                                                contentDescription = zoneLabel,
                                                tint = zoneColor,
                                                modifier = Modifier.size(14.dp)
                                            )
                                            Spacer(modifier = Modifier.width(3.dp))
                                            Text(
                                                text = zoneLabel,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = zoneColor
                                            )
                                        }

                                        Text(
                                            text = ChartMath.formatBasisPoints(cc.utilizationBp),
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.Bold,
                                            color = zoneColor
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(2.dp))

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = "Balance: ${MoneyFormatter.formatPaiseToRupees(cc.balancePaise)}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            text = "Limit: ${MoneyFormatter.formatPaiseToRupees(cc.creditLimitPaise)}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(4.dp))
                                    CreditCardGaugeRender(
                                        utilizationBp = cc.utilizationBp,
                                        zone = cc.zone,
                                        height = 8.dp
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
