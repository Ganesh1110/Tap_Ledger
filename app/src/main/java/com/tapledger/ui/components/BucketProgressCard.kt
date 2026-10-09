package com.tapledger.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tapledger.core.model.BudgetBucket
import com.tapledger.ui.theme.ExpenseRed
import com.tapledger.ui.theme.IncomeGreen
import com.tapledger.ui.theme.Spacing
import com.tapledger.ui.theme.WarningAmber
import com.tapledger.ui.theme.rememberAnimatedProgress

@Composable
fun BucketProgressCard(
    bucket: BudgetBucket,
    allocatedPaise: Long,
    spentPaise: Long,
    modifier: Modifier = Modifier,
    percentageBudget: Int? = null // e.g. 50, 30, 20
) {
    val remainingPaise = allocatedPaise - spentPaise
    val rawFraction = if (allocatedPaise > 0L) spentPaise.toFloat() / allocatedPaise.toFloat() else 0f
    val animatedProgress by rememberAnimatedProgress(targetFraction = rawFraction.coerceIn(0f, 1f))

    val isOverBudget = spentPaise > allocatedPaise && allocatedPaise > 0L
    val isWarning = rawFraction >= 0.8f && !isOverBudget

    val progressColor = when {
        isOverBudget -> ExpenseRed
        isWarning -> WarningAmber
        else -> MaterialTheme.colorScheme.primary
    }

    val statusIcon = when {
        isOverBudget -> Icons.Default.Error
        isWarning -> Icons.Default.Warning
        else -> Icons.Default.CheckCircle
    }

    val statusText = when {
        isOverBudget -> "Over budget"
        isWarning -> "≥ 80% used"
        else -> "On track"
    }

    val bucketTitle = when (bucket) {
        BudgetBucket.NEEDS -> "Needs (50%)"
        BudgetBucket.WANTS -> "Wants (30%)"
        BudgetBucket.SAVINGS -> "Savings (20%)"
        BudgetBucket.UNALLOCATED -> "Unallocated Pool"
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {
                contentDescription = "$bucketTitle: spent ${spentPaise / 100} of ${allocatedPaise / 100} rupees, status: $statusText"
            },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        border = BorderStroke(
            1.dp,
            if (isOverBudget) ExpenseRed.copy(alpha = 0.4f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.md)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = bucketTitle,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = statusIcon,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = progressColor
                    )
                    Text(
                        text = statusText,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = progressColor
                    )
                }
            }

            Spacer(modifier = Modifier.height(Spacing.xs))

            // Progress track: single continuous bar with rounded caps
            LinearProgressIndicator(
                progress = { animatedProgress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = progressColor,
                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                strokeCap = StrokeCap.Round
            )

            Spacer(modifier = Modifier.height(Spacing.sm))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Spent: ",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    MoneyText(
                        amountPaise = spentPaise,
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                        semantic = MoneySemantic.Expense
                    )
                    Text(
                        text = " of ",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    MoneyText(
                        amountPaise = allocatedPaise,
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                        semantic = MoneySemantic.Neutral
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = if (remainingPaise >= 0) "Left: " else "Exceeded: ",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    MoneyText(
                        amountPaise = kotlin.math.abs(remainingPaise),
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                        semantic = if (remainingPaise >= 0) MoneySemantic.Income else MoneySemantic.Expense
                    )
                }
            }
        }
    }
}
