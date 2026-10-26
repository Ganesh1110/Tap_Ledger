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
import com.tapledger.core.security.MpinManager
import com.tapledger.ui.screens.accounts.AccountsScreen
import com.tapledger.ui.screens.backup.BackupRestoreScreen
import com.tapledger.ui.screens.categories.CategoryManagementScreen
import com.tapledger.ui.screens.dashboard.DashboardScreen
import com.tapledger.ui.screens.diagnostics.DataDiagnosticsScreen
import com.tapledger.ui.screens.insights.InsightsScreen
import com.tapledger.ui.screens.manual.ManualEntryScreen
import com.tapledger.ui.screens.monthclose.MonthCloseScreen
import com.tapledger.ui.screens.mpin.AppLockSettingsScreen
import com.tapledger.ui.screens.mpin.MpinSetupScreen
import com.tapledger.ui.screens.pending.PendingInboxScreen
import com.tapledger.ui.screens.setup.SetupScreen
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
    object MonthClose : Screen("month_close")
    object DataDiagnostics : Screen("diagnostics")
    object Insights : Screen("insights")
    object Onboarding : Screen("onboarding")
    object MpinSetup : Screen("mpin_setup")
    object AppLockSettings : Screen("app_lock_settings")
    object SavingHome : Screen("saving_home")
    object GoalDetail : Screen("goal_detail/{goalId}") {
        fun createRoute(goalId: Long) = "goal_detail/$goalId"
    }
    object SavingsGuideSettings : Screen("savings_guide_settings")
}


@Composable
fun TapLedgerNavGraph(
    mpinManager: MpinManager,
    navController: NavHostController = rememberNavController(),
    startDestination: String = Screen.Dashboard.route,
    initialDashboardScrollIndex: Int = 0,
    onShowWhatsNew: () -> Unit = {},
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
                onNavigateToMonthClose = { navController.navigate(Screen.MonthClose.route) },
                onNavigateToDiagnostics = { navController.navigate(Screen.DataDiagnostics.route) },
                onNavigateToInsights = { navController.navigate(Screen.Insights.route) },
                onNavigateToAppLockSettings = { navController.navigate(Screen.AppLockSettings.route) },
                onNavigateToSavingsGuideSettings = { navController.navigate(Screen.SavingsGuideSettings.route) },
                onNavigateToSavings = { navController.navigate(Screen.SavingHome.route) },
                onShowWhatsNew = onShowWhatsNew
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
                onNavigateBack = { navController.popBackStack() },
                onNavigateToMpinSetup = { navController.navigate(Screen.MpinSetup.route) }
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
                    if (!mpinManager.isMpinSet.value) {
                        navController.navigate(Screen.MpinSetup.route) {
                            popUpTo(Screen.Onboarding.route) { inclusive = true }
                        }
                    } else {
                        navController.navigate(Screen.Dashboard.route) {
                            popUpTo(Screen.Onboarding.route) { inclusive = true }
                        }
                    }
                },
                onNavigateToRestoreBackup = {
                    navController.navigate(Screen.BackupRestore.route)
                }
            )
        }
        composable(Screen.MpinSetup.route) {
            MpinSetupScreen(
                mpinManager = mpinManager,
                onSetupSuccess = {
                    if (navController.previousBackStackEntry == null) {
                        navController.navigate(Screen.Dashboard.route) {
                            popUpTo(0) { inclusive = true }
                        }
                    } else {
                        navController.popBackStack()
                    }
                },
                canSkip = false
            )
        }
        composable(Screen.AppLockSettings.route) {
            AppLockSettingsScreen(
                mpinManager = mpinManager,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToSetup = { navController.navigate(Screen.MpinSetup.route) }
            )
        }
        composable(Screen.SavingHome.route) {
            com.tapledger.ui.screens.saving.SavingHomeScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToSettings = { navController.navigate(Screen.SavingsGuideSettings.route) },
                onNavigateToGoalDetail = { goalId ->
                    navController.navigate(Screen.GoalDetail.createRoute(goalId))
                }
            )
        }
        composable(
            route = Screen.GoalDetail.route,
            arguments = listOf(
                androidx.navigation.navArgument("goalId") {
                    type = androidx.navigation.NavType.LongType
                }
            )
        ) { backStackEntry ->
            val goalId = backStackEntry.arguments?.getLong("goalId") ?: 0L
            com.tapledger.ui.screens.saving.GoalDetailScreen(
                goalId = goalId,
                onNavigateBack = { navController.popBackStack() }
            )
        }
        composable(Screen.SavingsGuideSettings.route) {
            com.tapledger.ui.screens.saving.SavingsGuideSettingsScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}

