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
import androidx.compose.material.icons.filled.Check
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.tapledger.core.model.BudgetBucket
import com.tapledger.core.model.MoneyFormatter
import com.tapledger.ui.charts.model.BucketRingItem
import com.tapledger.ui.charts.model.BucketRingsModel
import com.tapledger.ui.charts.model.ChartState
import com.tapledger.ui.charts.render.RingChartRender
import com.tapledger.ui.charts.util.ChartMath
import com.tapledger.ui.components.SectionHeader
import com.tapledger.ui.theme.ChartPalette
import com.tapledger.ui.theme.ExpenseRed
import com.tapledger.ui.theme.Spacing

/**
 * V3 50/30/20 Bucket Rings Card per Flow.md §17.2 V3.
 * Three rings for Needs (#0072B2), Wants (#E69F00), and Savings (#009E73).
 * Savings envelope is rendered with goal styling (check icon when achieved, never red).
 */
@Composable
fun DashboardBucketRingsCard(
    bucketRingsState: ChartState<BucketRingsModel>,
    onManageCategories: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        SectionHeader(
            title = "Budget Buckets",
            subtitle = "50/30/20 Envelope Rule",
            actionText = "Manage",
            onActionClick = onManageCategories
        )

        Spacer(modifier = Modifier.height(Spacing.xs))

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .semantics(mergeDescendants = true) {
                    if (bucketRingsState is ChartState.Data) {
                        contentDescription = bucketRingsState.data.contentDescription
                    }
                },
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
            shape = RoundedCornerShape(20.dp)
        ) {
            when (bucketRingsState) {
                is ChartState.Loading -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(160.dp),
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
                            text = bucketRingsState.message,
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
                            text = bucketRingsState.message,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Center
                        )
                    }
                }
                is ChartState.Data -> {
                    val data = bucketRingsState.data

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = Spacing.md, horizontal = Spacing.sm),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        BucketRingColumn(
                            item = data.needs,
                            ringColor = ChartPalette.LightNeeds,
                            title = "Needs (50%)"
                        )
                        BucketRingColumn(
                            item = data.wants,
                            ringColor = ChartPalette.LightWants,
                            title = "Wants (30%)"
                        )
                        BucketRingColumn(
                            item = data.savings,
                            ringColor = ChartPalette.LightSavings,
                            title = "Savings (20%)"
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BucketRingColumn(
    item: BucketRingItem,
    ringColor: Color,
    title: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.width(105.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        RingChartRender(
            percentageUsedBp = item.percentageBp,
            ringColor = if (item.isExceeded) ExpenseRed else ringColor,
            trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            size = 84.dp,
            strokeWidth = 9.dp,
            centerContent = {
                if (item.bucket == BudgetBucket.SAVINGS && item.isGoalAchieved) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Goal Achieved",
                        tint = ChartPalette.LightSavings,
                        modifier = Modifier.size(24.dp)
                    )
                } else if (item.isExceeded) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = "Exceeded",
                        tint = ExpenseRed,
                        modifier = Modifier.size(20.dp)
                    )
                } else {
                    Text(
                        text = ChartMath.formatBasisPoints(item.percentageBp),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        )

        Spacer(modifier = Modifier.height(Spacing.xs))

        Text(
            text = title,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )

        Text(
            text = MoneyFormatter.formatCompactIndian(item.spentPaise),
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            color = if (item.isExceeded) ExpenseRed else MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )

        Text(
            text = "of ${MoneyFormatter.formatCompactIndian(item.budgetPaise)}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}
