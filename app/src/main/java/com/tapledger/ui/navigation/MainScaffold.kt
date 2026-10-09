package com.tapledger.ui.navigation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material3.FloatingActionButton
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
    BottomNavItem(Screen.Transactions.route, "Transactions", Icons.Default.ReceiptLong),
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
        bottomBar = {
            if (isPrimaryDestination) {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface
                ) {
                    BOTTOM_NAV_ITEMS.forEach { item ->
                        val selected = currentRoute == item.route
                        NavigationBarItem(
                            selected = selected,
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
                            label = { Text(item.title) }
                        )
                    }
                }
            }
        },
        floatingActionButton = {
            if (isPrimaryDestination) {
                FloatingActionButton(
                    onClick = { navController.navigate(Screen.ManualEntry.route) },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add Transaction")
                }
            }
        }
    ) { padding ->
        TapLedgerNavGraph(
            navController = navController,
            startDestination = startDestination,
            initialDashboardScrollIndex = initialDashboardScrollIndex,
            modifier = Modifier.padding(padding)
        )
    }
}
