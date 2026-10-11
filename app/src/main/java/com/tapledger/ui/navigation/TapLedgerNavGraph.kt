package com.tapledger.ui.navigation

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.tapledger.ui.screens.accounts.AccountsScreen
import com.tapledger.ui.screens.backup.BackupRestoreScreen
import com.tapledger.ui.screens.categories.CategoryManagementScreen
import com.tapledger.ui.screens.dashboard.DashboardScreen
import com.tapledger.ui.screens.diagnostics.DataDiagnosticsScreen
import com.tapledger.ui.screens.insights.InsightsScreen
import com.tapledger.ui.screens.manual.ManualEntryScreen
import com.tapledger.ui.screens.monthclose.MonthCloseScreen
import com.tapledger.ui.screens.pending.PendingInboxScreen
import com.tapledger.ui.screens.setup.SetupScreen
import com.tapledger.ui.screens.statement.StatementReconciliationScreen
import com.tapledger.ui.screens.transactions.TransactionsScreen
import com.tapledger.ui.theme.MotionSpec

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
    object Insights : Screen("insights")
    object Onboarding : Screen("onboarding")
}

@Composable
fun TapLedgerNavGraph(
    navController: NavHostController = rememberNavController(),
    startDestination: String = Screen.Dashboard.route,
    initialDashboardScrollIndex: Int = 0,
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier,
        enterTransition = {
            fadeIn(animationSpec = tween(MotionSpec.EnterDurationMillis, easing = FastOutSlowInEasing)) +
                    scaleIn(initialScale = 0.97f, animationSpec = tween(MotionSpec.EnterDurationMillis, easing = FastOutSlowInEasing))
        },
        exitTransition = {
            fadeOut(animationSpec = tween(MotionSpec.ExitDurationMillis, easing = FastOutSlowInEasing))
        },
        popEnterTransition = {
            fadeIn(animationSpec = tween(MotionSpec.EnterDurationMillis, easing = FastOutSlowInEasing)) +
                    scaleIn(initialScale = 0.97f, animationSpec = tween(MotionSpec.EnterDurationMillis, easing = FastOutSlowInEasing))
        },
        popExitTransition = {
            fadeOut(animationSpec = tween(MotionSpec.ExitDurationMillis, easing = FastOutSlowInEasing))
        }
    ) {
        composable(Screen.Dashboard.route) {
            DashboardScreen(
                initialScrollIndex = initialDashboardScrollIndex,
                onNavigateToManualEntry = { navController.navigate(Screen.ManualEntry.route) },
                onNavigateToTransactions = { navController.navigate(Screen.Transactions.route) },
                onNavigateToPending = { navController.navigate(Screen.PendingInbox.route) },
                onNavigateToAccounts = { navController.navigate(Screen.Accounts.route) },
                onNavigateToBackup = { navController.navigate(Screen.BackupRestore.route) },
                onNavigateToSetup = { navController.navigate(Screen.Setup.route) },
                onNavigateToCategories = { navController.navigate(Screen.CategoryManagement.route) },
                onNavigateToStatements = { navController.navigate(Screen.StatementReconciliation.route) },
                onNavigateToMonthClose = { navController.navigate(Screen.MonthClose.route) },
                onNavigateToDiagnostics = { navController.navigate(Screen.DataDiagnostics.route) },
                onNavigateToInsights = { navController.navigate(Screen.Insights.route) }
            )
        }
        composable(Screen.Transactions.route) {
            TransactionsScreen(
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
                onNavigateBack = { navController.popBackStack() },
                onNavigateToSetup = { navController.navigate(Screen.Setup.route) }
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
            CategoryManagementScreen()
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
        composable(Screen.Insights.route) {
            InsightsScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
        composable(Screen.Onboarding.route) {
            com.tapledger.ui.screens.onboarding.OnboardingScreen(
                onOnboardingComplete = {
                    navController.navigate(Screen.Dashboard.route) {
                        popUpTo(Screen.Onboarding.route) { inclusive = true }
                    }
                },
                onNavigateToRestoreBackup = {
                    navController.navigate(Screen.BackupRestore.route)
                }
            )
        }
    }
}
