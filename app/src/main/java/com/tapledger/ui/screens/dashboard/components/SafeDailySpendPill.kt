package com.tapledger.ui.screens.dashboard.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import com.tapledger.ui.charts.render.ProgressBarRender
import com.tapledger.ui.charts.util.ChartMath
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tapledger.core.model.MoneyFormatter
import com.tapledger.domain.calculator.SafeDailySpendResult
import com.tapledger.ui.theme.ExpenseRed
import com.tapledger.ui.theme.IncomeGreen
import com.tapledger.ui.theme.Spacing
import com.tapledger.ui.theme.WarningAmber

/**
 * 🟢 "Safe to Spend Today" Pill on Dashboard.
 * Displays today's safe discretionary spend allowance at a glance without mental math.
 * Tapping opens an educational breakdown dialog.
 */
@Composable
fun SafeDailySpendPill(
    safeSpend: SafeDailySpendResult,
    modifier: Modifier = Modifier,
    pendingSpendPaise: Long = 0L
) {
    var showDialog by remember { mutableStateOf(false) }

    val isOverBudget = safeSpend.isOverBudget
    val isTight = !isOverBudget && safeSpend.isPeriodActive && safeSpend.safeDailyPaise > 0L && safeSpend.safeDailyPaise < 150_00L
    val isClosed = safeSpend.isPeriodClosed || !safeSpend.isPeriodActive

    val statusColor: Color = when {
        isOverBudget -> ExpenseRed
        isTight -> WarningAmber
        isClosed -> MaterialTheme.colorScheme.onSurfaceVariant
        else -> IncomeGreen
    }

    val containerColor: Color = when {
        isOverBudget -> ExpenseRed.copy(alpha = 0.12f)
        isTight -> WarningAmber.copy(alpha = 0.12f)
        isClosed -> MaterialTheme.colorScheme.surfaceContainerLow
        else -> IncomeGreen.copy(alpha = 0.12f)
    }

    val borderColor: Color = when {
        isOverBudget -> ExpenseRed.copy(alpha = 0.4f)
        isTight -> WarningAmber.copy(alpha = 0.4f)
        isClosed -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
        else -> IncomeGreen.copy(alpha = 0.4f)
    }

    val statusIcon = when {
        isOverBudget -> Icons.Default.Error
        isTight -> Icons.Default.Warning
        isClosed -> Icons.Default.CalendarToday
        else -> Icons.Default.CheckCircle
    }

    val mainText = when {
        isClosed -> "Month Closed"
        isOverBudget -> "Today's Safe Spend: ₹0"
        else -> "Today's Safe Spend: ${MoneyFormatter.formatPaiseToRupees(safeSpend.safeDailyPaise)}"
    }

    val subText = when {
        isClosed -> "Archived Period"
        isOverBudget -> "Over budget"
        safeSpend.todaySpentPaise > 0L -> "₹${MoneyFormatter.formatPaiseToRupees(safeSpend.todayRemainingPaise).replace("₹", "")} left today"
        else -> "${safeSpend.daysRemaining} days left"
    } + if (pendingSpendPaise > 0L && !isClosed) " • ${MoneyFormatter.formatPaiseToRupees(pendingSpendPaise)} pending" else ""

    Surface(
        onClick = { showDialog = true },
        modifier = modifier
            .semantics(mergeDescendants = true) {
                contentDescription = "$mainText, $subText. Tap for breakdown."
            },
        shape = RoundedCornerShape(16.dp),
        color = containerColor,
        border = BorderStroke(1.dp, borderColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.md, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = statusIcon,
                    contentDescription = null,
                    tint = statusColor,
                    modifier = Modifier.size(20.dp)
                )

                Spacer(modifier = Modifier.width(Spacing.sm))

                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = if (isOverBudget || isClosed) mainText else "Today's Safe Spend: ",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (!isOverBudget && !isClosed) {
                            Text(
                                text = MoneyFormatter.formatPaiseToRupees(safeSpend.safeDailyPaise),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = statusColor
                            )
                        }
                    }

                    Text(
                        text = if (!isClosed && !isOverBudget && safeSpend.todaySpentPaise > 0L) {
                            "${safeSpend.daysRemaining} days left • $subText"
                        } else {
                            subText
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = "Details",
                tint = statusColor.copy(alpha = 0.7f),
                modifier = Modifier.size(18.dp)
            )
        }
    }

    if (showDialog) {
        SafeDailySpendDialog(
            safeSpend = safeSpend,
            pendingSpendPaise = pendingSpendPaise,
            onDismiss = { showDialog = false }
        )
    }
}

@Composable
fun SafeDailySpendDialog(
    safeSpend: SafeDailySpendResult,
    pendingSpendPaise: Long = 0L,
    onDismiss: () -> Unit
) {
    val isOverBudget = safeSpend.isOverBudget
    val isClosed = safeSpend.isPeriodClosed || !safeSpend.isPeriodActive

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = when {
                        isOverBudget -> Icons.Default.Error
                        isClosed -> Icons.Default.CalendarToday
                        else -> Icons.Default.CheckCircle
                    },
                    contentDescription = null,
                    tint = when {
                        isOverBudget -> ExpenseRed
                        isClosed -> MaterialTheme.colorScheme.onSurfaceVariant
                        else -> IncomeGreen
                    },
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(Spacing.xs))
                Text(
                    text = "Daily Safe Spend",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                // Headline Allowance Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isOverBudget) ExpenseRed.copy(alpha = 0.1f) else IncomeGreen.copy(alpha = 0.1f)
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(Spacing.md)) {
                        Text(
                            text = if (isClosed) "Daily Allowance" else "Safe Allowance for Today",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = MoneyFormatter.formatPaiseToRupees(safeSpend.safeDailyPaise),
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (isOverBudget) ExpenseRed else IncomeGreen
                        )

                        if (!isClosed && !isOverBudget && safeSpend.todaySpentPaise > 0L) {
                            Spacer(modifier = Modifier.height(Spacing.xs))
                            val bp = ChartMath.computeBasisPoints(safeSpend.todaySpentPaise, safeSpend.safeDailyPaise)
                            ProgressBarRender(
                                percentageBp = bp,
                                color = if (bp >= 10_000) ExpenseRed else IncomeGreen,
                                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier.clip(RoundedCornerShape(3.dp)),
                                height = 6.dp
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Spent: ${MoneyFormatter.formatPaiseToRupees(safeSpend.todaySpentPaise)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "Left: ${MoneyFormatter.formatPaiseToRupees(safeSpend.todayRemainingPaise)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (safeSpend.todayRemainingPaise > 0L) IncomeGreen else ExpenseRed
                                )
                            }
                        }
                    }
                }

                if (pendingSpendPaise > 0L && !isClosed) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = WarningAmber.copy(alpha = 0.12f)),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = Spacing.sm, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = WarningAmber,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(Spacing.xs))
                            Text(
                                text = "${MoneyFormatter.formatPaiseToRupees(pendingSpendPaise)} unreviewed spend pending in inbox (not counted in budget)",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))

                // Breakdown items
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Spendable Budget Left", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        text = MoneyFormatter.formatPaiseToRupees(safeSpend.remainingBudgetPaise),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = if (safeSpend.remainingBudgetPaise >= 0L) MaterialTheme.colorScheme.onSurface else ExpenseRed
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Days Left (inc. today)", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        text = "${safeSpend.daysRemaining} of ${safeSpend.totalDaysInPeriod} days",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Total Month Spend", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        text = "${MoneyFormatter.formatPaiseToRupees(safeSpend.totalHeroSpentPaise)} / ${MoneyFormatter.formatPaiseToRupees(safeSpend.totalHeroBudgetPaise)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))

                // Explanation tip
                Text(
                    text = if (isOverBudget) {
                        "You have exceeded your Needs & Wants budget for this month. Consider adjusting allocations or cutting discretionary spending."
                    } else if (isClosed) {
                        "This period is closed. Safe spend guidance applies to the active monthly budget."
                    } else {
                        "Formula: Remaining spendable budget ÷ ${safeSpend.daysRemaining} days left = ${MoneyFormatter.formatPaiseToRupees(safeSpend.safeDailyPaise)} / day.\n\nStaying under this allowance every day keeps your budget balanced effortlessly."
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("Got It")
            }
        }
    )
}
