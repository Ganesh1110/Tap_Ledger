package com.tapledger.ui.screens.accounts

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tapledger.core.model.AccountType
import com.tapledger.ui.components.AccountCard
import com.tapledger.ui.components.EmptyState
import com.tapledger.ui.components.MoneyIndicator
import com.tapledger.ui.components.MoneySemantic
import com.tapledger.ui.components.MoneyText
import com.tapledger.ui.components.SectionHeader
import com.tapledger.ui.components.SkeletonCard
import com.tapledger.ui.components.SkeletonList
import com.tapledger.ui.screens.dashboard.DashboardViewModel
import com.tapledger.ui.theme.Spacing
import com.tapledger.ui.theme.rememberAnimatedMoney

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountsScreen(
    dashboardViewModel: DashboardViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit
) {
    val state by dashboardViewModel.uiState.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    val listState = rememberLazyListState()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = {
                    Text(
                        text = "Accounts & Balances",
                        fontWeight = FontWeight.Bold
                    )
                },
                colors = TopAppBarDefaults.largeTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer
                ),
                scrollBehavior = scrollBehavior
            )
        }
    ) { padding ->
        if (state.isLoading) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(Spacing.md),
                verticalArrangement = Arrangement.spacedBy(Spacing.md)
            ) {
                SkeletonCard(height = 110.dp)
                SkeletonList(itemCount = 4)
            }
        } else if (state.accountSummaries.isEmpty()) {
            EmptyState(
                title = "No accounts configured",
                subtitle = "Your bank accounts, cash wallets, and credit cards will appear here.",
                icon = Icons.Default.AccountBalance,
                modifier = Modifier.padding(padding)
            )
        } else {
            val assets = state.accountSummaries.filter { it.account.type != AccountType.CREDIT_CARD }
            val liabilities = state.accountSummaries.filter { it.account.type == AccountType.CREDIT_CARD }

            val totalAssetsPaise = assets.sumOf { it.currentBalancePaise }
            val totalLiabilitiesPaise = liabilities.sumOf { it.currentBalancePaise }

            val netWorthPaise = state.netWorth?.netWorthPaise ?: 0L
            val animatedNetWorth by rememberAnimatedMoney(targetPaise = netWorthPaise)

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = Spacing.md),
                contentPadding = PaddingValues(top = Spacing.xs, bottom = Spacing.listBottomPadding),
                verticalArrangement = Arrangement.spacedBy(Spacing.md)
            ) {
                // 1. Net Worth Card with Long-safe animated money count-up
                if (state.netWorth != null) {
                    item(key = "net_worth_hero") {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .semantics(mergeDescendants = true) {
                                    contentDescription = "Total Net Worth: ${animatedNetWorth / 100} rupees"
                                },
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                            ),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                            shape = RoundedCornerShape(20.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(Spacing.lg)
                            ) {
                                Text(
                                    text = "Total Net Worth",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(Spacing.xxs))
                                MoneyText(
                                    amountPaise = animatedNetWorth,
                                    style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold),
                                    semantic = if (animatedNetWorth >= 0) MoneySemantic.Income else MoneySemantic.Expense
                                )

                                Spacer(modifier = Modifier.height(Spacing.sm))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column {
                                        Text(
                                            text = "Total Assets",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        MoneyText(
                                            amountPaise = totalAssetsPaise,
                                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                                            semantic = MoneySemantic.Income
                                        )
                                    }

                                    Column(horizontalAlignment = Alignment.End) {
                                        Text(
                                            text = "Total Liabilities",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        MoneyText(
                                            amountPaise = totalLiabilitiesPaise,
                                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                                            semantic = MoneySemantic.Debt,
                                            indicator = MoneyIndicator.Owed
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // 2. Assets Group
                if (assets.isNotEmpty()) {
                    item(key = "assets_header") {
                        SectionHeader(
                            title = "Assets (${assets.size})",
                            subtitle = "Bank accounts and cash wallets"
                        )
                    }

                    items(
                        items = assets,
                        key = { "asset_${it.account.id}" }
                    ) { summary ->
                        Column {
                            AccountCard(summary = summary)
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Last reconciled with opening balance",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                modifier = Modifier.padding(start = Spacing.sm)
                            )
                        }
                    }
                }

                // 3. Liabilities Group
                if (liabilities.isNotEmpty()) {
                    item(key = "liabilities_header") {
                        SectionHeader(
                            title = "Liabilities (${liabilities.size})",
                            subtitle = "Credit cards & outstanding dues"
                        )
                    }

                    items(
                        items = liabilities,
                        key = { "liability_${it.account.id}" }
                    ) { summary ->
                        Column {
                            AccountCard(summary = summary)
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Reconciled against credit limit & statement cycle",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                modifier = Modifier.padding(start = Spacing.sm)
                            )
                        }
                    }
                }
            }
        }
    }
}
