package com.tapledger.ui.screens.dashboard.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.TrendingUp
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tapledger.core.model.MoneyFormatter
import com.tapledger.domain.calculator.GoalApportionmentCalculator
import com.tapledger.domain.calculator.GoalApportionmentItem
import com.tapledger.ui.components.MoneySemantic
import com.tapledger.ui.components.MoneyText
import com.tapledger.ui.theme.ExpenseRed
import com.tapledger.ui.theme.IncomeGreen
import com.tapledger.ui.theme.Spacing

private enum class AllocationStrategy {
    PRIORITY,
    PROPORTIONAL,
    CUSTOM
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GoalAllocationBottomSheet(
    movedPaise: Long,
    goals: List<GoalApportionmentItem>,
    calculator: GoalApportionmentCalculator,
    onConfirmAllocation: (allocations: Map<Long, Long>) -> Unit,
    onDismissRequest: () -> Unit,
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
) {
    var strategy by remember { mutableStateOf(AllocationStrategy.PRIORITY) }
    val allocations = remember { mutableStateMapOf<Long, Long>() }

    // Initialize allocations using priority-first strategy
    LaunchedEffect(goals, movedPaise) {
        val result = calculator.apportionByPriority(movedPaise, goals)
        allocations.clear()
        allocations.putAll(result.allocations)
        strategy = AllocationStrategy.PRIORITY
    }

    val totalAllocatedPaise = allocations.values.sum()
    val unallocatedPaise = movedPaise - totalAllocatedPaise
    val isOverAllocated = totalAllocatedPaise > movedPaise

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.lg)
                .padding(bottom = Spacing.xl)
                .verticalScroll(rememberScrollState())
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .background(
                                MaterialTheme.colorScheme.primaryContainer,
                                RoundedCornerShape(12.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Savings,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(Spacing.md))
                    Column {
                        Text(
                            text = "Allocate to Goals",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Split moved funds across your targets",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                IconButton(onClick = onDismissRequest) {
                    Icon(imageVector = Icons.Default.Close, contentDescription = "Close")
                }
            }

            Spacer(modifier = Modifier.height(Spacing.md))

            // Summary Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
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
                            text = "Total Moved to Savings",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        MoneyText(
                            amountPaise = movedPaise,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            semantic = MoneySemantic.Income
                        )
                    }

                    Spacer(modifier = Modifier.height(Spacing.xs))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                    Spacer(modifier = Modifier.height(Spacing.xs))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Allocated to Goals",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isOverAllocated) ExpenseRed else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        MoneyText(
                            amountPaise = totalAllocatedPaise,
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                            color = if (isOverAllocated) ExpenseRed else MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Spacer(modifier = Modifier.height(2.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "General Savings (Unallocated)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        MoneyText(
                            amountPaise = maxOf(0L, unallocatedPaise),
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                            semantic = MoneySemantic.Neutral
                        )
                    }

                    if (isOverAllocated) {
                        Spacer(modifier = Modifier.height(Spacing.xs))
                        Text(
                            text = "Allocations exceed moved amount by ${MoneyFormatter.formatRupees(totalAllocatedPaise - movedPaise)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = ExpenseRed,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(Spacing.md))

            // Strategy Presets
            Text(
                text = "Preset Split",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(Spacing.xs))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
            ) {
                FilterChip(
                    selected = strategy == AllocationStrategy.PRIORITY,
                    onClick = {
                        val result = calculator.apportionByPriority(movedPaise, goals)
                        allocations.clear()
                        allocations.putAll(result.allocations)
                        strategy = AllocationStrategy.PRIORITY
                    },
                    label = { Text("Priority First") },
                    leadingIcon = if (strategy == AllocationStrategy.PRIORITY) {
                        { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                    } else null
                )
                FilterChip(
                    selected = strategy == AllocationStrategy.PROPORTIONAL,
                    onClick = {
                        val result = calculator.apportionProportionally(movedPaise, goals)
                        allocations.clear()
                        allocations.putAll(result.allocations)
                        strategy = AllocationStrategy.PROPORTIONAL
                    },
                    label = { Text("Proportional") },
                    leadingIcon = if (strategy == AllocationStrategy.PROPORTIONAL) {
                        { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                    } else null
                )
                FilterChip(
                    selected = strategy == AllocationStrategy.CUSTOM && totalAllocatedPaise == 0L,
                    onClick = {
                        allocations.clear()
                        goals.forEach { allocations[it.goalId] = 0L }
                        strategy = AllocationStrategy.CUSTOM
                    },
                    label = { Text("Reset to 0") }
                )
            }

            Spacer(modifier = Modifier.height(Spacing.md))

            if (goals.isEmpty()) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh
                ) {
                    Text(
                        text = "No active savings goals found. All funds will remain in general savings.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(Spacing.md)
                    )
                }
            } else {
                Text(
                    text = "Goals Breakdown",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(Spacing.xs))

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm)
                ) {
                    goals.forEach { goal ->
                        val currentAllocated = allocations[goal.goalId] ?: 0L
                        GoalAllocationRow(
                            goal = goal,
                            allocatedPaise = currentAllocated,
                            availableToAllocate = maxOf(0L, unallocatedPaise),
                            onAmountChanged = { newAmount ->
                                allocations[goal.goalId] = newAmount
                                strategy = AllocationStrategy.CUSTOM
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(Spacing.lg))

            // Action Buttons
            Button(
                onClick = {
                    onConfirmAllocation(allocations.filterValues { it > 0L })
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(12.dp),
                enabled = !isOverAllocated
            ) {
                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(Spacing.xs))
                Text("Confirm Allocation", fontWeight = FontWeight.SemiBold)
            }

            Spacer(modifier = Modifier.height(Spacing.xs))

            OutlinedButton(
                onClick = {
                    // Skip allocating to specific goals; all remains unallocated savings
                    onConfirmAllocation(emptyMap())
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Keep All in General Savings")
            }
        }
    }
}

@Composable
private fun GoalAllocationRow(
    goal: GoalApportionmentItem,
    allocatedPaise: Long,
    availableToAllocate: Long,
    onAmountChanged: (Long) -> Unit
) {
    var rawText by remember(allocatedPaise) {
        val wholeRupees = allocatedPaise / 100L
        mutableStateOf(if (allocatedPaise > 0L) wholeRupees.toString() else "")
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLowest
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.sm)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer
                    ) {
                        Text(
                            text = "P${goal.priority}",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(Spacing.xs))
                    Text(
                        text = goal.name,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Text(
                    text = "Needed: ${MoneyFormatter.formatRupees(goal.remainingNeededPaise)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(Spacing.xs))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                OutlinedTextField(
                    value = rawText,
                    onValueChange = { input ->
                        val cleaned = input.filter { it.isDigit() }
                        rawText = cleaned
                        val paise = (cleaned.toLongOrNull() ?: 0L) * 100L
                        onAmountChanged(paise)
                    },
                    modifier = Modifier.width(140.dp),
                    prefix = { Text("₹", style = MaterialTheme.typography.bodyMedium) },
                    placeholder = { Text("0") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium
                )

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(
                        onClick = {
                            val target = minOf(goal.remainingNeededPaise, allocatedPaise + availableToAllocate)
                            onAmountChanged(target)
                        },
                        enabled = goal.remainingNeededPaise > 0L && (availableToAllocate > 0L || allocatedPaise < goal.remainingNeededPaise)
                    ) {
                        Text("Fill Needed", fontSize = 12.sp)
                    }
                    TextButton(
                        onClick = { onAmountChanged(0L) },
                        enabled = allocatedPaise > 0L
                    ) {
                        Text("Clear", fontSize = 12.sp)
                    }
                }
            }
        }
    }
}
