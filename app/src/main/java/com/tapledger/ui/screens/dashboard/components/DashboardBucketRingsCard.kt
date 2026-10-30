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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tapledger.core.model.BudgetBucket
import com.tapledger.core.model.MoneyFormatter
import com.tapledger.ui.charts.model.BucketRingItem
import com.tapledger.ui.charts.model.BucketRingsModel
import com.tapledger.ui.charts.model.ChartState
import com.tapledger.ui.charts.render.ProgressBarRender
import com.tapledger.ui.charts.util.ChartMath
import com.tapledger.ui.charts.util.ChartSummaryHelper
import com.tapledger.ui.components.SectionHeader
import com.tapledger.ui.theme.ChartPalette
import com.tapledger.ui.theme.ExpenseRed
import com.tapledger.ui.theme.IncomeGreen
import com.tapledger.ui.theme.Spacing
import com.tapledger.ui.theme.WarningAmber

/**
 * Compact 50/30/20 Bucket Progress Bars Card per Flow.md §17.2 V3 and user design spec.
 * Three compact horizontal progress tracks for Needs (50%), Wants (30%), and Savings (20%).
 * Savings envelope is rendered with goal styling (check icon when achieved).
 */
@Composable
fun DashboardBucketRingsCard(
    bucketRingsState: ChartState<BucketRingsModel>,
    onManageCategories: () -> Unit,
    modifier: Modifier = Modifier
) {
    val summaryLine = if (bucketRingsState is ChartState.Data) {
        ChartSummaryHelper.summarizeV3BucketRings(bucketRingsState.data)
    } else {
        "50/30/20 Allocation Rule"
    }

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
                    contentDescription = summaryLine
                },
            colors = CardDefaults.cardColors(containerColor = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.88f)),
            border = BorderStroke(1.dp, androidx.compose.ui.graphics.Color.White.copy(alpha = 0.95f)),
            shape = RoundedCornerShape(18.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(Spacing.md),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                if (bucketRingsState is ChartState.Data) {
                    Text(
                        text = summaryLine,
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                when (bucketRingsState) {
                    is ChartState.Loading -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(120.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(32.dp))
                        }
                    }
                    is ChartState.Empty -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(90.dp),
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
                                .padding(Spacing.sm),
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

                        CompactBucketProgressRow(
                            item = data.needs,
                            color = ChartPalette.LightNeeds,
                            title = "Needs (50%)"
                        )

                        CompactBucketProgressRow(
                            item = data.wants,
                            color = ChartPalette.LightWants,
                            title = "Wants (30%)"
                        )

                        CompactBucketProgressRow(
                            item = data.savings,
                            color = ChartPalette.LightSavings,
                            title = "Savings (20%)"
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CompactBucketProgressRow(
    item: BucketRingItem,
    color: Color,
    title: String,
    modifier: Modifier = Modifier
) {
    val isSavings = item.bucket == BudgetBucket.SAVINGS
    val progressColor = when {
        isSavings && item.isGoalAchieved -> IncomeGreen
        item.isExceeded -> ExpenseRed
        item.isWarning -> WarningAmber
        else -> color
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.3.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )
                val (statusIcon, iconTint) = when {
                    isSavings && item.isGoalAchieved -> Icons.Default.Check to IncomeGreen
                    item.isExceeded -> Icons.Default.Error to ExpenseRed
                    item.isWarning -> Icons.Default.Warning to WarningAmber
                    else -> Icons.Default.CheckCircle to IncomeGreen
                }
                Icon(
                    imageVector = statusIcon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(13.dp)
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "${MoneyFormatter.formatCompactIndian(item.spentPaise)} / ${MoneyFormatter.formatCompactIndian(item.budgetPaise)}",
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontWeight = FontWeight.SemiBold,
                        fontFeatureSettings = "tnum"
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = ChartMath.formatBasisPoints(item.percentageBp),
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp
                    ),
                    color = progressColor
                )
            }
        }

        ProgressBarRender(
            percentageBp = item.percentageBp,
            color = progressColor,
            trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
        )
    }
}
