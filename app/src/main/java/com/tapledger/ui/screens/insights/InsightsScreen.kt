package com.tapledger.ui.screens.insights

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Store
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tapledger.core.database.entity.BudgetPeriodEntity
import com.tapledger.core.model.MoneyFormatter
import com.tapledger.core.util.DateTimeUtils
import com.tapledger.ui.screens.categories.components.CategoryDonutCard
import com.tapledger.ui.screens.dashboard.components.DashboardSixPeriodTrendCard
import com.tapledger.ui.screens.insights.components.TopMerchantsCard
import com.tapledger.ui.screens.transactions.components.SpendingHeatmapCard
import com.tapledger.ui.theme.Spacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InsightsScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: InsightsViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Financial Insights",
                        style = MaterialTheme.typography.titleLarge,
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
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        modifier = modifier
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // TabRow with 4 tabs
            val tabs = listOf(
                "Cash Flow" to Icons.Default.TrendingUp,
                "Calendar" to Icons.Default.CalendarMonth,
                "Merchants" to Icons.Default.Store,
                "Categories" to Icons.Default.Category
            )

            SecondaryTabRow(
                selectedTabIndex = state.selectedTab,
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                tabs.forEachIndexed { index, (title, icon) ->
                    Tab(
                        selected = state.selectedTab == index,
                        onClick = { viewModel.selectTab(index) },
                        text = { Text(title) },
                        icon = { Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp)) }
                    )
                }
            }

            if (state.isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(48.dp))
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(Spacing.md)
                ) {
                    when (state.selectedTab) {
                        0 -> {
                            // Tab 0: Cash Flow Trend (12 periods)
                            DashboardSixPeriodTrendCard(
                                trendState = state.cashFlowTrendState,
                                title = "12-Period Cash Flow",
                                subtitle = "Income, spent, & saved across up to 12 periods"
                            )
                        }

                        1 -> {
                            // Tab 1: Spending Calendar Heatmap (V7)
                            PeriodSelectorRow(
                                periods = state.allPeriods,
                                selectedPeriod = state.selectedPeriod,
                                onSelectPeriod = { viewModel.selectPeriod(it) }
                            )

                            Spacer(modifier = Modifier.height(Spacing.sm))

                            SpendingHeatmapCard(
                                heatmapState = state.calendarHeatmapState,
                                selectedDateEpoch = state.selectedHeatmapDayEpoch,
                                onSelectDay = { viewModel.selectHeatmapDay(it) },
                                onClearDateFilter = { viewModel.clearHeatmapDay() }
                            )

                            // Selected day detail card
                            AnimatedVisibility(visible = state.selectedHeatmapDayEpoch != null) {
                                state.selectedHeatmapDayEpoch?.let { epoch ->
                                    Card(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = Spacing.md),
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                                        shape = RoundedCornerShape(16.dp),
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
                                                    text = DateTimeUtils.formatDate(epoch),
                                                    style = MaterialTheme.typography.titleMedium,
                                                    fontWeight = FontWeight.Bold
                                                )
                                                IconButton(onClick = { viewModel.clearHeatmapDay() }) {
                                                    Icon(Icons.Default.Close, contentDescription = "Clear day selection")
                                                }
                                            }

                                            val txs = state.selectedHeatmapDayTxs
                                            if (txs.isEmpty()) {
                                                Text(
                                                    text = "No categorized expense transactions on this day",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            } else {
                                                Text(
                                                    text = "${txs.size} categorized transactions",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.primary,
                                                    fontWeight = FontWeight.SemiBold
                                                )
                                                Spacer(modifier = Modifier.height(Spacing.xs))
                                                txs.forEach { tx ->
                                                    Row(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .padding(vertical = 4.dp),
                                                        horizontalArrangement = Arrangement.SpaceBetween,
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Text(
                                                            text = tx.merchantNormalized ?: tx.merchantRaw ?: "Unknown Merchant",
                                                            style = MaterialTheme.typography.bodyMedium,
                                                            modifier = Modifier.weight(1f)
                                                        )
                                                        Text(
                                                            text = MoneyFormatter.formatPaiseToRupees(tx.amountPaise),
                                                            style = MaterialTheme.typography.bodyMedium,
                                                            fontWeight = FontWeight.Bold
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        2 -> {
                            // Tab 2: Top Merchants (V8)
                            TopMerchantsCard(
                                merchantsState = state.topMerchantsState
                            )
                        }

                        3 -> {
                            // Tab 3: Categories (V6 Category Donut with period selector)
                            PeriodSelectorRow(
                                periods = state.allPeriods,
                                selectedPeriod = state.selectedPeriod,
                                onSelectPeriod = { viewModel.selectPeriod(it) }
                            )

                            Spacer(modifier = Modifier.height(Spacing.sm))

                            CategoryDonutCard(
                                donutState = state.categoryDonutState,
                                selectedSlice = state.selectedDonutSlice,
                                onSelectSlice = { viewModel.selectDonutSlice(it) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PeriodSelectorRow(
    periods: List<BudgetPeriodEntity>,
    selectedPeriod: BudgetPeriodEntity?,
    onSelectPeriod: (BudgetPeriodEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    if (periods.isEmpty()) return

    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(vertical = Spacing.xs),
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        periods.forEach { period ->
            val isSelected = selectedPeriod?.id == period.id
            val label = DateTimeUtils.formatMonthYear(period.year, period.month)

            FilterChip(
                selected = isSelected,
                onClick = { onSelectPeriod(period) },
                label = { Text(label, style = MaterialTheme.typography.labelMedium) }
            )
        }
    }
}
