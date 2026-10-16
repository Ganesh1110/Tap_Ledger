package com.tapledger.ui.screens.dashboard.components

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tapledger.core.model.TransferPlanKind
import com.tapledger.core.model.TransferPlanStatus
import com.tapledger.domain.calculator.SpareCalculationResult
import com.tapledger.ui.components.MoneySemantic
import com.tapledger.ui.components.MoneyText
import com.tapledger.ui.theme.IncomeGreen
import com.tapledger.ui.theme.Spacing
import com.tapledger.ui.theme.WarningAmber

data class TransferPlanDashboardState(
    val hasPlan: Boolean = false,
    val planId: Long? = null,
    val kind: TransferPlanKind? = null,
    val status: TransferPlanStatus? = null,
    val sourceAccountName: String = "HDFC",
    val destAccountName: String = "SBI",
    val suggestedPaise: Long = 0L,
    val movedPaise: Long? = null,
    val spareCalculation: SpareCalculationResult? = null,
    val isSafetyGateActive: Boolean = false,
    val safetyGateReason: String? = null
)

@Composable
fun TransferPlanCard(
    state: TransferPlanDashboardState,
    onMoveNowClick: () -> Unit,
    onReviewClick: () -> Unit,
    onAllocateGoalsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        border = BorderStroke(
            width = 1.dp,
            color = when (state.status) {
                TransferPlanStatus.VERIFIED -> IncomeGreen.copy(alpha = 0.5f)
                TransferPlanStatus.MARKED_MOVED -> WarningAmber.copy(alpha = 0.5f)
                TransferPlanStatus.SUGGESTED -> MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                else -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
            }
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.md)
        ) {
            // Header: Title & Status Chip
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Savings,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(Spacing.xs))
                    Text(
                        text = if (state.kind == TransferPlanKind.SWEEP) "Month-End Sweep" else "Savings Transfer Guide",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                StatusChip(status = state.status, hasSafeSpare = (state.spareCalculation?.sparePaise ?: 0L) > 0L)
            }

            Spacer(modifier = Modifier.height(Spacing.sm))

            // Safety Gate Banner
            if (state.isSafetyGateActive) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(Spacing.sm),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(Spacing.xs))
                        Text(
                            text = state.safetyGateReason ?: "Balance may be out of date, Correct Balance first",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
                Spacer(modifier = Modifier.height(Spacing.xs))
            }

            // Amount Display & Context
            when {
                state.status == TransferPlanStatus.VERIFIED -> {
                    Text(
                        text = "Transferred to Savings",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    MoneyText(
                        amountPaise = state.movedPaise ?: state.suggestedPaise,
                        style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                        semantic = MoneySemantic.Income
                    )
                    Spacer(modifier = Modifier.height(Spacing.xs))
                    Text(
                        text = "✓ Verified by ${state.destAccountName} credit alert. Funds secured.",
                        style = MaterialTheme.typography.bodySmall,
                        color = IncomeGreen
                    )
                }

                state.status == TransferPlanStatus.MARKED_MOVED -> {
                    Text(
                        text = "Marked Moved",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    MoneyText(
                        amountPaise = state.movedPaise ?: state.suggestedPaise,
                        style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                        semantic = MoneySemantic.Income
                    )
                    Spacer(modifier = Modifier.height(Spacing.xs))
                    Text(
                        text = "Awaiting ${state.destAccountName} bank alert confirmation. Ledger legs updated.",
                        style = MaterialTheme.typography.bodySmall,
                        color = WarningAmber
                    )
                }

                state.suggestedPaise > 0L && !state.isSafetyGateActive -> {
                    Text(
                        text = "Recommended to Move (${state.sourceAccountName} → ${state.destAccountName})",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    MoneyText(
                        amountPaise = state.suggestedPaise,
                        style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                        semantic = MoneySemantic.Income
                    )
                    Spacer(modifier = Modifier.height(Spacing.xs))
                    val spare = state.spareCalculation?.sparePaise ?: 0L
                    Text(
                        text = "Safe spare: ₹${spare / 100} • Card dues & envelope buffers reserved.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                else -> {
                    Text(
                        text = "Nothing safe to move this month. You're on track.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = Spacing.xs)
                    )
                }
            }

            // Action Buttons
            if (!state.isSafetyGateActive) {
                Spacer(modifier = Modifier.height(Spacing.sm))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    when (state.status) {
                        TransferPlanStatus.SUGGESTED -> {
                            OutlinedButton(
                                onClick = onReviewClick,
                                contentPadding = ButtonDefaults.TextButtonContentPadding
                            ) {
                                Text("Math Breakdown")
                            }
                            Spacer(modifier = Modifier.width(Spacing.xs))
                            Button(
                                onClick = onMoveNowClick
                            ) {
                                Text("Move Now")
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }

                        TransferPlanStatus.MARKED_MOVED -> {
                            OutlinedButton(onClick = onReviewClick) {
                                Text("View Details")
                            }
                            Spacer(modifier = Modifier.width(Spacing.xs))
                            Button(
                                onClick = onAllocateGoalsClick,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.secondary
                                )
                            ) {
                                Text("Allocate to Goals")
                            }
                        }

                        TransferPlanStatus.VERIFIED -> {
                            OutlinedButton(onClick = onReviewClick) {
                                Text("View Details")
                            }
                        }

                        else -> {
                            if (state.suggestedPaise > 0L) {
                                Button(onClick = onMoveNowClick) {
                                    Text("Move Now")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusChip(
    status: TransferPlanStatus?,
    hasSafeSpare: Boolean,
    modifier: Modifier = Modifier
) {
    val (label, bg, fg) = when (status) {
        TransferPlanStatus.VERIFIED -> Triple(
            "Verified ✓",
            IncomeGreen.copy(alpha = 0.15f),
            IncomeGreen
        )
        TransferPlanStatus.MARKED_MOVED -> Triple(
            "Moved",
            WarningAmber.copy(alpha = 0.15f),
            WarningAmber
        )
        TransferPlanStatus.SUGGESTED -> Triple(
            "Suggested",
            MaterialTheme.colorScheme.primaryContainer,
            MaterialTheme.colorScheme.onPrimaryContainer
        )
        TransferPlanStatus.DISMISSED -> Triple(
            "Dismissed",
            MaterialTheme.colorScheme.surfaceContainerHigh,
            MaterialTheme.colorScheme.onSurfaceVariant
        )
        TransferPlanStatus.EXPIRED -> Triple(
            "Expired",
            MaterialTheme.colorScheme.surfaceContainerHigh,
            MaterialTheme.colorScheme.onSurfaceVariant
        )
        null -> {
            if (hasSafeSpare) {
                Triple(
                    "Ready",
                    MaterialTheme.colorScheme.primaryContainer,
                    MaterialTheme.colorScheme.onPrimaryContainer
                )
            } else {
                Triple(
                    "On Track",
                    MaterialTheme.colorScheme.surfaceContainerHigh,
                    MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = bg,
        modifier = modifier
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = fg,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}
