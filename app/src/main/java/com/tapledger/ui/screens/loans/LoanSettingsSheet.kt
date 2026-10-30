package com.tapledger.ui.screens.loans

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tapledger.core.preferences.LoanPreferencesConfig
import com.tapledger.ui.theme.Spacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoanSettingsSheet(
    config: LoanPreferencesConfig,
    onDismissRequest: () -> Unit,
    onToggleTrackerEnabled: (Boolean) -> Unit,
    onToggleNetWorthInclusion: (Boolean) -> Unit,
    onToggleHideClosedLoans: (Boolean) -> Unit,
    onToggleAutoDetectEmiSms: (Boolean) -> Unit,
    onToggleEmiBalanceGuard: (Boolean) -> Unit,
    onSelectDaysPrior: (Int) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.md, vertical = Spacing.sm)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Loan Tracker Options",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Customize visibility, automation, and alerts",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onDismissRequest) {
                    Icon(Icons.Default.Close, contentDescription = "Close")
                }
            }

            Spacer(modifier = Modifier.height(Spacing.md))

            // 1. App Control & Visibility Options
            Text(
                text = "APP CONTROL & VISIBILITY",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                letterSpacing = 1.sp
            )
            Spacer(modifier = Modifier.height(Spacing.xs))

            LoanOptionToggleCard(
                icon = Icons.Default.AccountBalance,
                title = "Enable Loan Tracker",
                subtitle = "When disabled, hides loan tabs and menus completely, keeping the app 100% minimalist.",
                checked = config.loanTrackerEnabled,
                onCheckedChange = onToggleTrackerEnabled
            )

            Spacer(modifier = Modifier.height(Spacing.xs))

            LoanOptionToggleCard(
                icon = Icons.Default.TrendingDown,
                title = "Include in Net Worth",
                subtitle = "Subtract remaining loan balance from total Net Worth on Home & Accounts screens.",
                checked = config.includeLoansInNetWorth,
                enabled = config.loanTrackerEnabled,
                onCheckedChange = onToggleNetWorthInclusion
            )

            Spacer(modifier = Modifier.height(Spacing.xs))

            LoanOptionToggleCard(
                icon = Icons.Default.FilterAlt,
                title = "Hide Closed / Settled Loans",
                subtitle = "Automatically archive loans that reach ₹0 or get foreclosed so only active debts show.",
                checked = config.hideClosedLoans,
                enabled = config.loanTrackerEnabled,
                onCheckedChange = onToggleHideClosedLoans
            )

            Spacer(modifier = Modifier.height(Spacing.md))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            Spacer(modifier = Modifier.height(Spacing.md))

            // 2. Smart Automation Options
            Text(
                text = "SMART AUTOMATION & ALERTS",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                letterSpacing = 1.sp
            )
            Spacer(modifier = Modifier.height(Spacing.xs))

            LoanOptionToggleCard(
                icon = Icons.Default.AutoAwesome,
                title = "Auto-Detect EMI from Bank SMS",
                subtitle = "Recognize bank EMI debit alerts and suggest 1-tap loan transfers in Pending Inbox.",
                checked = config.autoDetectEmiSms,
                enabled = config.loanTrackerEnabled,
                onCheckedChange = onToggleAutoDetectEmiSms
            )

            Spacer(modifier = Modifier.height(Spacing.xs))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(Spacing.md)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Shield,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(Spacing.sm))
                            Column {
                                Text(
                                    text = "EMI Balance Guard Reminder",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "Alerts if your linked bank balance is low before EMI date to prevent NACH bounce fees.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(Spacing.xs))
                        Switch(
                            checked = config.emiBalanceGuardEnabled,
                            onCheckedChange = onToggleEmiBalanceGuard,
                            enabled = config.loanTrackerEnabled
                        )
                    }

                    if (config.emiBalanceGuardEnabled && config.loanTrackerEnabled) {
                        Spacer(modifier = Modifier.height(Spacing.sm))
                        Text(
                            text = "Remind me before EMI debit day:",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                            FilterChip(
                                selected = config.emiBalanceGuardDaysPrior == 1,
                                onClick = { onSelectDaysPrior(1) },
                                label = { Text("1 Day Prior", fontSize = 12.sp) }
                            )
                            FilterChip(
                                selected = config.emiBalanceGuardDaysPrior == 2,
                                onClick = { onSelectDaysPrior(2) },
                                label = { Text("2 Days Prior (Recommended)", fontSize = 12.sp) }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(Spacing.listBottomPadding))
        }
    }
}

@Composable
private fun LoanOptionToggleCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.md),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(Spacing.sm))
                Column {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (enabled) 1f else 0.5f)
                    )
                }
            }
            Spacer(modifier = Modifier.width(Spacing.xs))
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                enabled = enabled
            )
        }
    }
}
