package com.tapledger.ui.screens.mpin

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tapledger.core.security.GracePeriod
import com.tapledger.core.security.MpinManager
import com.tapledger.core.security.PeriodicReverification
import com.tapledger.ui.theme.Spacing
import kotlinx.coroutines.launch

@Composable
fun AppLockSettingsCard(
    mpinManager: MpinManager,
    onNavigateToSetup: () -> Unit,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    val isMpinSet by mpinManager.isMpinSet.collectAsState()
    val gracePeriod by mpinManager.gracePeriod.collectAsState()
    val reverification by mpinManager.periodicReverification.collectAsState()

    var showGraceDropdown by remember { mutableStateOf(false) }
    var showReverificationDropdown by remember { mutableStateOf(false) }

    // Step-up authentication states
    var pendingAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    var stepUpTitle by remember { mutableStateOf("Authorize Setting Change") }

    fun requestStepUp(title: String, action: () -> Unit) {
        if (!isMpinSet) {
            action()
        } else {
            stepUpTitle = title
            pendingAction = action
        }
    }

    if (pendingAction != null) {
        MpinStepUpDialog(
            mpinManager = mpinManager,
            title = stepUpTitle,
            onVerified = {
                val action = pendingAction
                pendingAction = null
                action?.invoke()
            },
            onDismiss = {
                pendingAction = null
            }
        )
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Column(modifier = Modifier.padding(Spacing.md)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Column {
                        Text(
                            text = "App Lock (MPIN)",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (isMpinSet) "Active (Argon2id + Keystore)" else "Disabled",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Switch(
                    checked = isMpinSet,
                    onCheckedChange = { enable ->
                        if (enable) {
                            onNavigateToSetup()
                        } else {
                            requestStepUp("Disable App Lock") {
                                // To disable, MpinStepUpDialog verified the current MPIN
                                coroutineScope.launch {
                                    // Use clear or disable - we just verified it
                                    // We can invoke disable with verified pin or clear security data
                                    mpinManager.clearAllSecurityData()
                                }
                            }
                        }
                    }
                )
            }

            AnimatedVisibility(visible = isMpinSet) {
                Column {
                    Spacer(modifier = Modifier.height(Spacing.sm))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Spacer(modifier = Modifier.height(Spacing.sm))

                    // Change MPIN Button
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                requestStepUp("Change MPIN") {
                                    onNavigateToSetup()
                                }
                            }
                            .padding(vertical = Spacing.xs),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Change MPIN",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Spacer(modifier = Modifier.height(Spacing.xs))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Spacer(modifier = Modifier.height(Spacing.xs))

                    // Auto-lock grace period dropdown
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                requestStepUp("Change Auto-Lock Delay") {
                                    showGraceDropdown = true
                                }
                            }
                            .padding(vertical = Spacing.xs),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Auto-Lock Delay",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                text = gracePeriod.label,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        DropdownMenu(
                            expanded = showGraceDropdown,
                            onDismissRequest = { showGraceDropdown = false }
                        ) {
                            GracePeriod.entries.forEach { option ->
                                DropdownMenuItem(
                                    text = { Text(option.label) },
                                    onClick = {
                                        showGraceDropdown = false
                                        coroutineScope.launch {
                                            mpinManager.updateGracePeriod(option)
                                        }
                                    }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(Spacing.xs))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Spacer(modifier = Modifier.height(Spacing.xs))

                    // Periodic Re-verification dropdown
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                requestStepUp("Change Re-verification Interval") {
                                    showReverificationDropdown = true
                                }
                            }
                            .padding(vertical = Spacing.xs),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Mandatory Re-verification",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                text = reverification.label,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        DropdownMenu(
                            expanded = showReverificationDropdown,
                            onDismissRequest = { showReverificationDropdown = false }
                        ) {
                            PeriodicReverification.entries.forEach { option ->
                                DropdownMenuItem(
                                    text = { Text(option.label) },
                                    onClick = {
                                        showReverificationDropdown = false
                                        coroutineScope.launch {
                                            mpinManager.updatePeriodicReverification(option)
                                        }
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
