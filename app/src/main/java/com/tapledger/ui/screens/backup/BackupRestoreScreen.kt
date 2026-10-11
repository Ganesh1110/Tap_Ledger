package com.tapledger.ui.screens.backup

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tapledger.core.util.DateTimeUtils
import com.tapledger.ui.components.ConfirmDialog
import com.tapledger.ui.components.EmptyState
import com.tapledger.ui.components.SectionHeader
import com.tapledger.ui.theme.ExpenseRed
import com.tapledger.ui.theme.IncomeGreen
import com.tapledger.ui.theme.Spacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupRestoreScreen(
    viewModel: BackupRestoreViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var backupToRestore by remember { mutableStateOf<BackupFileInfo?>(null) }
    var showEraseDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Backup & Restore",
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = Spacing.md),
            contentPadding = PaddingValues(top = Spacing.xs, bottom = Spacing.listBottomPadding),
            verticalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            // 1. Offline Vault Overview Card
            item(key = "vault_info") {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(Spacing.md)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Shield,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(Spacing.xs))
                            Text(
                                text = "Offline Vault & Data Protection",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(Spacing.xs))
                        Text(
                            text = "TapLedger is 100% offline. Financial records never leave your local storage. Backups are created atomically via SQLite VACUUM INTO.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        if (state.lastBackupDate != null) {
                            Spacer(modifier = Modifier.height(Spacing.sm))
                            val sizeKb = (state.lastBackupSizeBytes ?: 0L) / 1024
                            Text(
                                text = "Last backup: ${DateTimeUtils.formatDateTime(state.lastBackupDate ?: 0L)} (${sizeKb} KB)",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

            // 2. Action Buttons (Save/Archive icon, Neutral integrity check)
            item(key = "actions") {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    Button(
                        onClick = { viewModel.triggerBackup() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        enabled = !state.isBackingUp,
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        if (state.isBackingUp) {
                            CircularProgressIndicator(
                                color = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(Spacing.xs))
                            Text("Create Backup Snapshot", fontWeight = FontWeight.Bold)
                        }
                    }

                    OutlinedButton(
                        onClick = { viewModel.runIntegrityCheck() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        enabled = !state.isChecking,
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        if (state.isChecking) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            // Starts neutral and only shows check after a pass
                            val (icon, tint) = when (state.integrityVerified) {
                                true -> Icons.Default.CheckCircle to IncomeGreen
                                false -> Icons.Default.Error to ExpenseRed
                                null -> Icons.Default.Security to MaterialTheme.colorScheme.onSurfaceVariant
                            }
                            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(Spacing.xs))
                            Text("Verify Database Integrity", fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }

            // 3. Status/Message Card (if present)
            state.message?.let { msg ->
                item(key = "status_msg") {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = if (state.isError) ExpenseRed.copy(alpha = 0.12f) else IncomeGreen.copy(alpha = 0.12f)
                        ),
                        border = BorderStroke(
                            1.dp,
                            if (state.isError) ExpenseRed.copy(alpha = 0.3f) else IncomeGreen.copy(alpha = 0.3f)
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(Spacing.md),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (state.isError) Icons.Default.Error else Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = if (state.isError) ExpenseRed else IncomeGreen,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(Spacing.xs))
                            Text(
                                text = msg,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                color = if (state.isError) ExpenseRed else IncomeGreen
                            )
                        }
                    }
                }
            }

            // 4. Backup Snapshots List
            item(key = "list_header") {
                SectionHeader(
                    title = "Available Snapshots (${state.availableBackups.size})",
                    subtitle = "Stored safely in offline app storage"
                )
            }

            if (state.availableBackups.isEmpty()) {
                item(key = "empty_backups") {
                    Text(
                        text = "No backup files found yet. Tap 'Create Backup Snapshot' above to create one.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = Spacing.sm)
                    )
                }
            } else {
                items(
                    items = state.availableBackups,
                    key = { it.name }
                ) { backup ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(Spacing.md),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = backup.name,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "${DateTimeUtils.formatDateTime(backup.modifiedAt)} • ${backup.sizeBytes / 1024} KB",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            OutlinedButton(
                                onClick = { backupToRestore = backup },
                                contentPadding = PaddingValues(horizontal = Spacing.sm, vertical = 4.dp)
                            ) {
                                Icon(Icons.Default.Restore, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Restore", fontSize = 12.sp)
                            }
                        }
                    }
                }
            }

            // Danger Zone: Erase All Data & Start Over
            item {
                Spacer(modifier = Modifier.height(Spacing.lg))
                SectionHeader(
                    title = "Danger Zone",
                    subtitle = "Irreversible data erasure & first-run reset"
                )
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = ExpenseRed.copy(alpha = 0.08f)),
                    border = BorderStroke(1.dp, ExpenseRed.copy(alpha = 0.35f)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(Spacing.md)) {
                        Text(
                            text = "Erase All Data & Start Over",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = ExpenseRed
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Permanently deletes all accounts, transactions, and envelopes. Creates an automatic safety backup and resets the app to initial onboarding.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(Spacing.md))
                        Button(
                            onClick = { showEraseDialog = true },
                            colors = ButtonDefaults.buttonColors(containerColor = ExpenseRed),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.DeleteForever, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(Spacing.xs))
                            Text("Erase All Data & Start Over")
                        }
                    }
                }
            }
        }
    }

    // Safety Restore Confirmation Dialog
    backupToRestore?.let { backup ->
        ConfirmDialog(
            title = "Restore from Snapshot?",
            text = "Restoring from '${backup.name}' will create an automatic pre-restore safety backup of your current database first. Are you sure you wish to continue?",
            confirmText = "Proceed with Restore",
            dismissText = "Cancel",
            onConfirm = {
                val file = backup.file
                backupToRestore = null
                viewModel.restoreBackup(file)
            },
            onDismiss = { backupToRestore = null }
        )
    }

    // Erase All Data Dialog (§15.1)
    if (showEraseDialog) {
        var eraseConfirmationText by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showEraseDialog = false },
            title = { Text("Erase All Data?", fontWeight = FontWeight.Bold, color = ExpenseRed) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "This will erase all accounts, transactions, and envelopes. A safety backup will be created in your app storage before erasing. To proceed, please type ERASE below.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    OutlinedTextField(
                        value = eraseConfirmationText,
                        onValueChange = { eraseConfirmationText = it },
                        label = { Text("Type ERASE to confirm") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showEraseDialog = false
                        viewModel.eraseAllData()
                    },
                    enabled = eraseConfirmationText.trim() == "ERASE",
                    colors = ButtonDefaults.buttonColors(containerColor = ExpenseRed)
                ) {
                    Text("Permanently Erase")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEraseDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
