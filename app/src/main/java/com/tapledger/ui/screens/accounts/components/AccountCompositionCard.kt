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
import androidx.compose.ui.draw.clip
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

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.tapledger.ui.charts.model.AccountCompositionBarItem
import com.tapledger.ui.charts.model.CreditCardUtilizationGaugeItem
import com.tapledger.ui.charts.render.ChartDetailStrip
import com.tapledger.ui.charts.render.ChartTooltipItem
import com.tapledger.ui.charts.render.rememberIsCompactChart
import com.tapledger.ui.charts.util.ChartSummaryHelper

/**
 * V10 Account composition card per Flow.md §17.2 V10.
 * Diverging bars per account (assets right, liabilities left, sorted by |balance|)
 * and credit-card utilization gauges with <30% normal, 30-70% caution, >70% high zones.
 * Enhanced with plain-language summary line, compact scaling, and detail strip.
 */
@Composable
fun AccountCompositionCard(
    compositionState: ChartState<AccountCompositionModel>,
    modifier: Modifier = Modifier
) {
    val isCompact = rememberIsCompactChart()
    var selectedAccount by remember { mutableStateOf<AccountCompositionBarItem?>(null) }
    var selectedCc by remember { mutableStateOf<CreditCardUtilizationGaugeItem?>(null) }

    val summaryLine = remember(compositionState) {
        if (compositionState is ChartState.Data) {
            ChartSummaryHelper.summarizeV10AccountComposition(compositionState.data)
        } else {
            "Diverging balance distribution (Assets vs Liabilities)"
        }
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {
                contentDescription = summaryLine
            },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.md)
        ) {
            Text(
                text = "Account Composition",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Serif,
                    fontWeight = FontWeight.Bold
                )
            )
            Text(
                text = summaryLine,
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.primary
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
                            val isSelected = selectedAccount == acc
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(
                                        if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
                                        else androidx.compose.ui.graphics.Color.Transparent
                                    )
                                    .defaultMinSize(minHeight = 48.dp)
                                    .clickable {
                                        selectedAccount = if (isSelected) null else acc
                                        selectedCc = null
                                    }
                                    .semantics {
                                        stateDescription = "${acc.accountName}: ${MoneyFormatter.formatPaiseToRupees(acc.balancePaise)}"
                                    }
                                    .padding(vertical = 4.dp, horizontal = 4.dp)
                            ) {
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
                                    height = if (isCompact) 12.dp else 8.dp
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
                                val isSelected = selectedCc == cc

                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(
                                            if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
                                            else androidx.compose.ui.graphics.Color.Transparent
                                        )
                                        .defaultMinSize(minHeight = 48.dp)
                                        .clickable {
                                            selectedCc = if (isSelected) null else cc
                                            selectedAccount = null
                                        }
                                        .semantics {
                                            stateDescription = "${cc.accountName}: ${ChartMath.formatBasisPoints(cc.utilizationBp)}, $zoneLabel"
                                        }
                                        .padding(vertical = 4.dp, horizontal = 4.dp)
                                ) {
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
                                        height = if (isCompact) 12.dp else 8.dp
                                    )
                                }
                            }
                        }
                    }

                    // Detail Strip (§17.2 D10)
                    val detailItem = selectedAccount?.let { acc ->
                        ChartTooltipItem(
                            id = acc.accountName,
                            title = acc.accountName,
                            valueFormatted = MoneyFormatter.formatPaiseToRupees(acc.balancePaise),
                            context = if (acc.isAsset) "Asset account" else "Liability account",
                            statusIcon = if (acc.isAsset) Icons.Default.CheckCircle else Icons.Default.Warning,
                            statusColor = if (acc.isAsset) IncomeGreen else ExpenseRed
                        )
                    } ?: selectedCc?.let { cc ->
                        val zoneIcon = when (cc.zone) {
                            UtilizationZone.NORMAL -> Icons.Default.CheckCircle
                            UtilizationZone.CAUTION -> Icons.Default.Warning
                            UtilizationZone.HIGH -> Icons.Default.Error
                        }
                        val zoneLabel = when (cc.zone) {
                            UtilizationZone.NORMAL -> "Normal (<30%)"
                            UtilizationZone.CAUTION -> "Caution (30-70%)"
                            UtilizationZone.HIGH -> "High (>70%)"
                        }
                        val zoneColor = when (cc.zone) {
                            UtilizationZone.NORMAL -> IncomeGreen
                            UtilizationZone.CAUTION -> WarningAmber
                            UtilizationZone.HIGH -> ExpenseRed
                        }
                        ChartTooltipItem(
                            id = cc.accountName,
                            title = cc.accountName,
                            valueFormatted = "${MoneyFormatter.formatPaiseToRupees(cc.balancePaise)} of ${MoneyFormatter.formatPaiseToRupees(cc.creditLimitPaise)}",
                            context = "Utilization ${ChartMath.formatBasisPoints(cc.utilizationBp)} • $zoneLabel",
                            statusIcon = zoneIcon,
                            statusColor = zoneColor
                        )
                    }
                    ChartDetailStrip(
                        item = detailItem,
                        defaultSummary = summaryLine,
                        onClear = {
                            selectedAccount = null
                            selectedCc = null
                        }
                    )
                }
            }
        }
    }
}
