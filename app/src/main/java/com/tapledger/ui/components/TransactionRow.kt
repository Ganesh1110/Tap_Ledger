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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.RemoveCircleOutline
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tapledger.core.database.entity.TransactionEntity
import com.tapledger.core.util.DateTimeUtils
import com.tapledger.core.model.TransactionKind
import com.tapledger.core.model.TransactionStatus
import com.tapledger.ui.theme.Spacing

@Composable
fun TransactionRow(
    tx: TransactionEntity,
    modifier: Modifier = Modifier,
    categoryName: String? = null,
    bucketName: String? = null,
    accountName: String? = null,
    onClick: (() -> Unit)? = null,
    onVoid: (() -> Unit)? = null,
    onIgnore: (() -> Unit)? = null,
    onRestore: (() -> Unit)? = null
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val isVoided = tx.status == TransactionStatus.VOIDED
    val isIncome = tx.kind == TransactionKind.INCOME || tx.kind == TransactionKind.REFUND

    val displayAmountPaise = if (isIncome) tx.amountPaise else -tx.amountPaise

    val containerColor = when (tx.status) {
        TransactionStatus.VOIDED -> MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.5f)
        TransactionStatus.PENDING -> MaterialTheme.colorScheme.surfaceContainerLow
        else -> MaterialTheme.colorScheme.surface
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)),
        shape = RoundedCornerShape(10.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.md, vertical = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
                ) {
                    Text(
                        text = tx.merchantRaw?.takeIf { it.isNotBlank() } ?: tx.kind.name.lowercase().replaceFirstChar { it.uppercase() },
                        style = MaterialTheme.typography.titleMedium.copy(fontSize = 15.sp),
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )

                    StatusBadge(status = tx.status)
                }

                Spacer(modifier = Modifier.height(2.dp))

                // Category · Bucket & Account
                val subline = buildList {
                    if (categoryName != null) {
                        if (bucketName != null) add("$categoryName • $bucketName") else add(categoryName)
                    }
                    if (accountName != null) add(accountName)
                }.joinToString("  |  ")

                if (subline.isNotEmpty()) {
                    Text(
                        text = subline,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.height(2.dp))

                // Time and Source Chip
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
                ) {
                    Text(
                        text = DateTimeUtils.formatDateTime(tx.occurredAt),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp
                    )

                    // Small source chip that never wraps
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                            .padding(horizontal = 4.dp, vertical = 1.dp)
                    ) {
                        Text(
                            text = tx.source.name,
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(Spacing.sm))

            Column(horizontalAlignment = Alignment.End) {
                MoneyText(
                    amountPaise = displayAmountPaise,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    semantic = if (isIncome) MoneySemantic.Income else MoneySemantic.Expense,
                    showSign = true
                )

                // Overflow Menu
                Box {
                    IconButton(
                        onClick = { menuExpanded = true },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "Actions for transaction",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false }
                    ) {
                        if (isVoided) {
                            if (onRestore != null) {
                                DropdownMenuItem(
                                    text = { Text("Restore Transaction") },
                                    leadingIcon = { Icon(Icons.Default.Restore, contentDescription = null) },
                                    onClick = {
                                        menuExpanded = false
                                        onRestore()
                                    }
                                )
                            }
                        } else {
                            if (onVoid != null) {
                                DropdownMenuItem(
                                    text = { Text("Void Transaction") },
                                    leadingIcon = { Icon(Icons.Default.Block, contentDescription = null) },
                                    onClick = {
                                        menuExpanded = false
                                        onVoid()
                                    }
                                )
                            }
                            if (onIgnore != null) {
                                DropdownMenuItem(
                                    text = { Text("Ignore (Non-Financial)") },
                                    leadingIcon = { Icon(Icons.Default.RemoveCircleOutline, contentDescription = null) },
                                    onClick = {
                                        menuExpanded = false
                                        onIgnore()
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
