package com.tapledger.ui.screens.dashboard.components

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SheetState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tapledger.core.model.MoneyFormatter
import com.tapledger.core.model.TransferPlanStatus
import com.tapledger.domain.calculator.SpareCalculationResult
import com.tapledger.ui.components.MoneySemantic
import com.tapledger.ui.components.MoneyText
import com.tapledger.ui.theme.ExpenseRed
import com.tapledger.ui.theme.IncomeGreen
import com.tapledger.ui.theme.Spacing
import com.tapledger.ui.theme.WarningAmber

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransferPlanBottomSheet(
    state: TransferPlanDashboardState,
    onDismissRequest: () -> Unit,
    onMarkAsMoved: (movedPaise: Long) -> Unit,
    onDismissPlan: () -> Unit,
    preferredPayAppPackage: String? = null,
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val spareCalc = state.spareCalculation

    val initialAmountRupees = remember(state.suggestedPaise) {
        val paise = if (state.suggestedPaise > 0L) state.suggestedPaise else (spareCalc?.paydaySuggestionPaise ?: 0L)
        (paise / 100L).toString()
    }
    var amountInput by remember { mutableStateOf(initialAmountRupees) }

    val parsedPaise: Long = remember(amountInput) {
        try {
            val clean = amountInput.trim().replace(",", "")
            if (clean.isBlank()) 0L else (clean.toLong() * 100L)
        } catch (e: Exception) {
            0L
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.md)
                .padding(bottom = Spacing.xl)
                .verticalScroll(rememberScrollState())
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Savings Transfer Guide",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${state.sourceAccountName}  ➔  ${state.destAccountName}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium
                    )
                }

                IconButton(onClick = onDismissRequest) {
                    Icon(imageVector = Icons.Default.Close, contentDescription = "Close")
                }
            }

            Spacer(modifier = Modifier.height(Spacing.md))

            // Conservative Math Breakdown Card
            if (spareCalc != null) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.5f)
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(Spacing.sm)
                    ) {
                        Text(
                            text = "Conservative Math Breakdown",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(Spacing.xs))

                        MathRow(label = "${state.sourceAccountName} Balance", amountPaise = spareCalc.hdfcBalancePaise, semantic = MoneySemantic.Neutral)
                        MathRow(label = "Reserved for Needs + Wants", amountPaise = -spareCalc.remainingNeedsWantsPaise, semantic = MoneySemantic.Expense)
                        if (spareCalc.cardDuesPaise > 0L) {
                            MathRow(label = "Credit Card Dues", amountPaise = -spareCalc.cardDuesPaise, semantic = MoneySemantic.Expense)
                        }
                        if (spareCalc.commitmentsPaise > 0L) {
                            MathRow(label = "SIP / Recurring Commitments", amountPaise = -spareCalc.commitmentsPaise, semantic = MoneySemantic.Expense)
                        }
                        if (spareCalc.bufferPaise > 0L) {
                            MathRow(label = "Reserve Safety Buffer", amountPaise = -spareCalc.bufferPaise, semantic = MoneySemantic.Expense)
                        }

                        HorizontalDivider(modifier = Modifier.padding(vertical = Spacing.xs))

                        MathRow(
                            label = "Available Safe Spare",
                            amountPaise = spareCalc.sparePaise,
                            semantic = MoneySemantic.Income,
                            isBold = true
                        )
                        MathRow(
                            label = "Target Monthly Savings Gap",
                            amountPaise = spareCalc.savingsGapPaise,
                            semantic = MoneySemantic.Neutral
                        )
                    }
                }

                Spacer(modifier = Modifier.height(Spacing.md))
            }

            // Editable Transfer Amount
            Text(
                text = "Transfer Amount (₹)",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(Spacing.xxs))

            OutlinedTextField(
                value = amountInput,
                onValueChange = { amountInput = it.filter { char -> char.isDigit() } },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                prefix = { Text("₹", fontWeight = FontWeight.Bold) },
                supportingText = {
                    Text("You can adjust the amount freely before marking as moved.")
                }
            )

            // Quick Chips
            if (spareCalc != null) {
                Spacer(modifier = Modifier.height(Spacing.xs))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
                ) {
                    val suggestedRupees = (state.suggestedPaise / 100L).toString()
                    val spareRupees = (spareCalc.sparePaise / 100L).toString()

                    if (state.suggestedPaise > 0L) {
                        FilterChip(
                            selected = amountInput == suggestedRupees,
                            onClick = { amountInput = suggestedRupees },
                            label = { Text("Suggested (₹$suggestedRupees)") }
                        )
                    }

                    if (spareCalc.sparePaise > 0L && spareCalc.sparePaise != state.suggestedPaise) {
                        FilterChip(
                            selected = amountInput == spareRupees,
                            onClick = { amountInput = spareRupees },
                            label = { Text("Full Spare (₹$spareRupees)") }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(Spacing.md))

            // Action 1: Open Payment App & Copy Amount Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
            ) {
                OutlinedButton(
                    onClick = {
                        launchPaymentApp(context, parsedPaise, preferredPayAppPackage)
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(Spacing.xxs))
                    Text("Payment App")
                }

                OutlinedButton(
                    onClick = {
                        val amountText = (parsedPaise / 100L).toString()
                        clipboardManager.setText(AnnotatedString(amountText))
                        Toast.makeText(context, "Copied ₹$amountText to clipboard", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(Spacing.xxs))
                    Text("Copy Amount")
                }
            }

            Spacer(modifier = Modifier.height(Spacing.xs))

            // Action 2: Primary "I've moved it" Button
            Button(
                onClick = {
                    if (parsedPaise > 0L) {
                        onMarkAsMoved(parsedPaise)
                    }
                },
                enabled = parsedPaise > 0L,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(Spacing.xs))
                Text("I've moved it", fontWeight = FontWeight.Bold)
            }

            // Disclaimer & NetBanking notice
            Spacer(modifier = Modifier.height(Spacing.xs))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(Spacing.xxs))
                Text(
                    text = "Above your UPI limit? Use NetBanking or IMPS.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }

            // Dismiss option
            if (state.status == TransferPlanStatus.SUGGESTED) {
                Spacer(modifier = Modifier.height(Spacing.xs))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    TextButton(onClick = onDismissPlan) {
                        Text(
                            text = "Skip for this period",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MathRow(
    label: String,
    amountPaise: Long,
    semantic: MoneySemantic,
    isBold: Boolean = false
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = if (isBold) FontWeight.Bold else FontWeight.Normal,
            color = if (isBold) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
        )
        MoneyText(
            amountPaise = amountPaise,
            style = MaterialTheme.typography.bodySmall.copy(fontWeight = if (isBold) FontWeight.Bold else FontWeight.Normal),
            semantic = semantic,
            showSign = true
        )
    }
}

private fun launchPaymentApp(
    context: Context,
    amountPaise: Long,
    preferredPackage: String?
) {
    try {
        val rupees = amountPaise / 100L
        val paiseRem = amountPaise % 100L
        val amountStr = "$rupees.${if (paiseRem < 10) "0$paiseRem" else "$paiseRem"}"

        val uri = Uri.parse("upi://pay?pn=Savings%20Account&am=$amountStr&cu=INR")
        val intent = Intent(Intent.ACTION_VIEW, uri)

        if (!preferredPackage.isNullOrBlank()) {
            intent.setPackage(preferredPackage)
        }

        val activities = context.packageManager.queryIntentActivities(intent, 0)
        if (activities.isNotEmpty()) {
            context.startActivity(intent)
        } else {
            // If specific package not found, launch generic chooser
            val chooser = Intent.createChooser(Intent(Intent.ACTION_VIEW, uri), "Open UPI App")
            context.startActivity(chooser)
        }
    } catch (e: Exception) {
        Toast.makeText(context, "Could not open UPI app: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}
