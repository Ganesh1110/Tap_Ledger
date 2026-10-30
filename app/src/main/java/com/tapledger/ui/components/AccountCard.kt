package com.tapledger.ui.components

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
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.FilledTonalButton
import com.tapledger.core.model.AccountType
import com.tapledger.domain.model.AccountBalanceSummary
import com.tapledger.ui.theme.ExpenseRed
import com.tapledger.ui.theme.ExpenseRedContainer
import com.tapledger.ui.theme.IncomeGreen
import com.tapledger.ui.theme.Spacing

@Composable
fun AccountCard(
    summary: AccountBalanceSummary,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    onPayBill: (() -> Unit)? = null
) {
    val isCreditCard = summary.account.type == AccountType.CREDIT_CARD
    val isLoan = summary.account.type == AccountType.LOAN
    val isLiability = isCreditCard || isLoan
    val icon: ImageVector = when (summary.account.type) {
        AccountType.BANK -> Icons.Default.AccountBalance
        AccountType.CREDIT_CARD -> Icons.Default.CreditCard
        AccountType.CASH -> Icons.Default.Payments
        AccountType.INVESTMENT -> Icons.Default.AccountBalance
        AccountType.LOAN -> Icons.Default.AccountBalance
        AccountType.WALLET -> Icons.Default.Payments
    }

    val containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)

    val border = if (isCreditCard && summary.currentBalancePaise < 0) {
        BorderStroke(1.dp, ExpenseRed.copy(alpha = 0.35f))
    } else {
        BorderStroke(1.dp, androidx.compose.ui.graphics.Color.White.copy(alpha = 0.85f))
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = border,
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.md, vertical = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            if (isCreditCard) ExpenseRedContainer.copy(alpha = 0.6f)
                            else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = summary.account.type.name,
                        tint = if (isCreditCard) ExpenseRed else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Spacer(modifier = Modifier.width(Spacing.sm))

                Column {
                    Text(
                        text = summary.account.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = if (isCreditCard) {
                            "Credit Card${summary.account.identifierLast4?.let { " •••• $it" } ?: ""}"
                        } else {
                            "${summary.account.type.name.lowercase().replaceFirstChar { it.uppercase() }}${summary.account.identifierLast4?.let { " •••• $it" } ?: ""}"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Column(horizontalAlignment = Alignment.End) {
                MoneyText(
                    amountPaise = summary.currentBalancePaise,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    semantic = if (isCreditCard) MoneySemantic.Debt else MoneySemantic.Automatic,
                    indicator = if (isCreditCard) MoneyIndicator.Owed else MoneyIndicator.None
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = if (isLiability) "OUTSTANDING" else "AVAILABLE",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 9.sp,
                    letterSpacing = 0.6.sp
                )
                if (isLiability) {
                    if (summary.currentBalancePaise < 0 && onPayBill != null) {
                        Spacer(modifier = Modifier.height(4.dp))
                        FilledTonalButton(
                            onClick = onPayBill,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.height(26.dp)
                        ) {
                            Text(
                                text = if (isLoan) "Pay EMI" else "Pay Bill",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                fontSize = 11.sp
                            )
                        }
                    } else if (summary.currentBalancePaise >= 0) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = if (isLoan) "✓ Settled" else "✓ Paid in Full",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = IncomeGreen,
                            fontSize = 10.sp
                        )
                    }
                }
            }
        }
    }
}
