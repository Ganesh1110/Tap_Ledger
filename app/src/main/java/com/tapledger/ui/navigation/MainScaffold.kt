package com.tapledger.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController

data class BottomNavItem(
    val route: String,
    val title: String,
    val icon: ImageVector
)

val BOTTOM_NAV_ITEMS = listOf(
    BottomNavItem(Screen.Dashboard.route, "Home", Icons.Default.AccountBalanceWallet),
    BottomNavItem(Screen.Transactions.route, "Transactions", Icons.AutoMirrored.Filled.ReceiptLong),
    BottomNavItem(Screen.CategoryManagement.route, "Budget", Icons.Default.PieChart),
    BottomNavItem(Screen.Accounts.route, "Accounts", Icons.Default.AccountBalance)
)

@Composable
fun MainScaffold(
    startDestination: String = Screen.Dashboard.route,
    initialDashboardScrollIndex: Int = 0
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val isPrimaryDestination = BOTTOM_NAV_ITEMS.any { it.route == currentRoute }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        // Zero insets for outer scaffold so top status-bar insets are never double-applied to screen TopAppBars
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (isPrimaryDestination) {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                ) {
                    BOTTOM_NAV_ITEMS.forEach { item ->
                        val selected = currentRoute == item.route
                        NavigationBarItem(
                            selected = selected,
                            alwaysShowLabel = true,
                            onClick = {
                                if (currentRoute != item.route) {
                                    navController.navigate(item.route) {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            },
                            icon = { Icon(item.icon, contentDescription = item.title) },
                            label = {
                                Text(
                                    text = item.title,
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.sp)
                                )
                            }
                        )
                    }
                }
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = padding.calculateBottomPadding())
        ) {
            TapLedgerNavGraph(
                navController = navController,
                startDestination = startDestination,
                initialDashboardScrollIndex = initialDashboardScrollIndex,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}
