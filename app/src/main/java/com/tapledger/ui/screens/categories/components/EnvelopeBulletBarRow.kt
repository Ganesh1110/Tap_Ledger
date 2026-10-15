package com.tapledger.ui.screens.categories.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tapledger.core.model.BudgetBucket
import com.tapledger.core.model.MoneyFormatter
import com.tapledger.ui.charts.model.EnvelopeBulletBarItem
import com.tapledger.ui.charts.render.BulletBarRender
import com.tapledger.ui.charts.util.ChartMath
import com.tapledger.ui.theme.ChartPalette
import com.tapledger.ui.theme.ExpenseRed
import com.tapledger.ui.theme.IncomeGreen
import com.tapledger.ui.theme.Spacing
import com.tapledger.ui.theme.WarningAmber
import androidx.compose.foundation.layout.defaultMinSize
import com.tapledger.ui.charts.render.rememberIsCompactChart

/**
 * V5 Envelope Bullet Bar Row per Flow.md §17.2 V5.
 * Displays spent vs AdjustedBudget with base marker, carried-in segment (AdvPos),
 * applied-deficit segment (AdvNeg), and D8 overflow. Savings uses goal styling.
 */
@Composable
fun EnvelopeBulletBarRow(
    item: EnvelopeBulletBarItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isCompact = rememberIsCompactChart()
    val barColor = when {
        item.bucket == BudgetBucket.SAVINGS -> ChartPalette.LightSavings
        item.isExceeded -> ExpenseRed
        item.isWarning -> WarningAmber
        item.bucket == BudgetBucket.NEEDS -> ChartPalette.LightNeeds
        else -> ChartPalette.LightWants
    }

    val contentDesc = buildString {
        append(item.categoryName)
        append(": spent ")
        append(MoneyFormatter.formatPaiseToRupees(item.spentPaise))
        append(" of ")
        append(MoneyFormatter.formatPaiseToRupees(item.adjustedBudgetPaise))
        append(" (")
        append(ChartMath.formatBasisPoints(item.percentageBp))
        append(")")
        if (item.isExceeded) append(", Exceeded")
        if (item.isWarning) append(", Warning")
        if (item.isGoalAchieved) append(", Goal Achieved")
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 48.dp)
            .clickable(onClick = onClick)
            .semantics(mergeDescendants = true) {
                contentDescription = contentDesc
                stateDescription = "${MoneyFormatter.formatPaiseToRupees(item.spentPaise)} of ${MoneyFormatter.formatPaiseToRupees(item.adjustedBudgetPaise)}, ${ChartMath.formatBasisPoints(item.percentageBp)}"
            },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        border = BorderStroke(
            1.dp,
            if (item.isExceeded) ExpenseRed.copy(alpha = 0.4f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
        ),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.md, vertical = if (isCompact) Spacing.md else Spacing.sm)
        ) {
            // Header: Category Name, Status Icon, and Rupee Values
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = item.categoryName,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    if (item.bucket == BudgetBucket.SAVINGS && item.isGoalAchieved) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Goal Achieved",
                            tint = ChartPalette.LightSavings,
                            modifier = Modifier.size(16.dp)
                        )
                    } else if (item.isExceeded) {
                        Icon(
                            imageVector = Icons.Default.Error,
                            contentDescription = "Budget Exceeded",
                            tint = ExpenseRed,
                            modifier = Modifier.size(16.dp)
                        )
                    } else if (item.isWarning) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = "Warning",
                            tint = WarningAmber,
                            modifier = Modifier.size(16.dp)
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "On Track",
                            tint = ChartPalette.LightNeeds,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = MoneyFormatter.formatPaiseToRupees(item.spentPaise),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = if (item.isExceeded) ExpenseRed else MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = " / ${MoneyFormatter.formatPaiseToRupees(item.adjustedBudgetPaise)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(Spacing.xs))

            // Bullet Bar Visualization
            BulletBarRender(
                spentPaise = item.spentPaise,
                adjustedBudgetPaise = item.adjustedBudgetPaise,
                baseAllocationPaise = item.baseAllocationPaise,
                advPosPaise = item.advPosPaise,
                advNegPaise = item.advNegPaise,
                barColor = barColor,
                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                isExceeded = item.isExceeded,
                isGoalAchieved = item.isGoalAchieved,
                height = if (isCompact) 18.dp else 14.dp
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Footer Details: % used, carried or deficit note if non-zero
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${ChartMath.formatBasisPoints(item.percentageBp)} used",
                    style = MaterialTheme.typography.labelSmall,
                    color = barColor,
                    fontWeight = FontWeight.Medium
                )

                if (item.advPosPaise > 0L) {
                    Text(
                        text = "+${MoneyFormatter.formatCompactIndian(item.advPosPaise)} carry",
                        style = MaterialTheme.typography.labelSmall,
                        color = IncomeGreen
                    )
                } else if (item.advNegPaise > 0L) {
                    Text(
                        text = "-${MoneyFormatter.formatCompactIndian(item.advNegPaise)} deficit",
                        style = MaterialTheme.typography.labelSmall,
                        color = ExpenseRed
                    )
                }
            }
        }
    }
}
