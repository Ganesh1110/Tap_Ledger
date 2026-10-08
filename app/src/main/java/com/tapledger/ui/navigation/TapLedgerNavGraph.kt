package com.tapledger.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.tapledger.ui.screens.accounts.AccountsScreen
import com.tapledger.ui.screens.backup.BackupRestoreScreen
import com.tapledger.ui.screens.categories.CategoryManagementScreen
import com.tapledger.ui.screens.dashboard.DashboardScreen
import com.tapledger.ui.screens.diagnostics.DataDiagnosticsScreen
import com.tapledger.ui.screens.manual.ManualEntryScreen
import com.tapledger.ui.screens.monthclose.MonthCloseScreen
import com.tapledger.ui.screens.pending.PendingInboxScreen
import com.tapledger.ui.screens.setup.SetupScreen
import com.tapledger.ui.screens.statement.StatementReconciliationScreen
import com.tapledger.ui.screens.transactions.TransactionsScreen

sealed class Screen(val route: String) {
    object Dashboard : Screen("dashboard")
    object Transactions : Screen("transactions")
    object ManualEntry : Screen("manual_entry")
    object PendingInbox : Screen("pending_inbox")
    object Accounts : Screen("accounts")
    object BackupRestore : Screen("backup_restore")
    object Setup : Screen("setup")
    object CategoryManagement : Screen("categories")
    object StatementReconciliation : Screen("statements")
    object MonthClose : Screen("month_close")
    object DataDiagnostics : Screen("diagnostics")
}

@Composable
fun TapLedgerNavGraph(
    navController: NavHostController = rememberNavController(),
    startDestination: String = Screen.Dashboard.route
) {
    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        composable(Screen.Dashboard.route) {
            DashboardScreen(
                onNavigateToManualEntry = { navController.navigate(Screen.ManualEntry.route) },
                onNavigateToTransactions = { navController.navigate(Screen.Transactions.route) },
                onNavigateToPending = { navController.navigate(Screen.PendingInbox.route) },
                onNavigateToAccounts = { navController.navigate(Screen.Accounts.route) },
                onNavigateToBackup = { navController.navigate(Screen.BackupRestore.route) },
                onNavigateToSetup = { navController.navigate(Screen.Setup.route) },
                onNavigateToCategories = { navController.navigate(Screen.CategoryManagement.route) },
                onNavigateToStatements = { navController.navigate(Screen.StatementReconciliation.route) },
                onNavigateToMonthClose = { navController.navigate(Screen.MonthClose.route) },
                onNavigateToDiagnostics = { navController.navigate(Screen.DataDiagnostics.route) }
            )
        }
        composable(Screen.Transactions.route) {
            TransactionsScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToManualEntry = { navController.navigate(Screen.ManualEntry.route) }
            )
        }
        composable(Screen.ManualEntry.route) {
            ManualEntryScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
        composable(Screen.PendingInbox.route) {
            PendingInboxScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
        composable(Screen.Accounts.route) {
            AccountsScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
        composable(Screen.BackupRestore.route) {
            BackupRestoreScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
        composable(Screen.Setup.route) {
            SetupScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
        composable(Screen.CategoryManagement.route) {
            CategoryManagementScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
        composable(Screen.StatementReconciliation.route) {
            StatementReconciliationScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
        composable(Screen.MonthClose.route) {
            MonthCloseScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
        composable(Screen.DataDiagnostics.route) {
            DataDiagnosticsScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}
