package com.tapledger.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.tapledger.core.model.AppMode
import com.tapledger.ui.components.isKeyboardVisible
import com.tapledger.ui.screens.mpin.MpinLockScreen
import com.tapledger.ui.screens.mpin.MpinSetupScreen
import com.tapledger.ui.screens.whatsnew.WhatsNewDialog

data class BottomNavItem(
    val route: String,
    val title: String,
    val icon: ImageVector
)

val BOTTOM_NAV_ITEMS = listOf(
    BottomNavItem(Screen.Dashboard.route, "Home", Icons.Default.AccountBalanceWallet),
    BottomNavItem(Screen.Transactions.route, "Transactions", Icons.AutoMirrored.Filled.ReceiptLong),
    BottomNavItem(Screen.Accounts.route, "Accounts", Icons.Default.AccountBalance),
    BottomNavItem(Screen.Loans.route, "Loans", Icons.Default.Payments),
    BottomNavItem(Screen.Insights.route, "Insights", Icons.Default.PieChart)
)

@Composable
fun MainScaffold(
    startDestination: String = Screen.Dashboard.route,
    initialDashboardScrollIndex: Int = 0,
    viewModel: MainViewModel = hiltViewModel()
) {
    val isOnboarded by viewModel.isOnboarded.collectAsState()
    val isLocked by viewModel.isAppLocked.collectAsState()
    val lockMessage by viewModel.lockMessage.collectAsState()
    val shouldShowUpgradePrompt by viewModel.shouldShowUpgradePrompt.collectAsState()
    val shouldShowWhatsNew by viewModel.shouldShowWhatsNew.collectAsState()
    val appMode by viewModel.appMode.collectAsState()

    val lifecycleOwner = LocalLifecycleOwner.current

    // Observe application background/foreground transitions for app lock grace period & re-verification
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> viewModel.onAppBackgrounded()
                Lifecycle.Event.ON_START -> viewModel.onAppForegrounded()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    if (isOnboarded == null) {
        return
    }

    // 1. GATE ENTIRE UI WITH MPIN LOCK SCREEN
    // Rendered before NavGraph so ZERO financial data is composed or visible behind it.
    if (isLocked) {
        MpinLockScreen(
            mpinManager = viewModel.mpinManager,
            customMessage = lockMessage,
            initialAppMode = appMode,
            onUnlockedWithMode = { unlockedMode -> viewModel.unlock(unlockedMode) },
            onUnlocked = { viewModel.unlock() },
            onNavigateToRestoreBackup = {
                viewModel.unlock()
            },
            onEraseAllData = {
                viewModel.eraseAllData()
            }
        )
        return
    }

    // 2. EXISTING INSTALL UPGRADE PROMPT
    // First launch after update prompt to setup MPIN for existing users
    if (shouldShowUpgradePrompt) {
        MpinSetupScreen(
            mpinManager = viewModel.mpinManager,
            canSkip = true,
            onSkip = { viewModel.dismissUpgradePrompt() },
            onSetupSuccess = { viewModel.dismissUpgradePrompt() }
        )
        return
    }

    // 3. SAVING MODE (Flow.md §18, D012)
    // When in Saving mode, render dedicated SavingNavGraph without manager navigation or screens.
    // Sensitive Manager actions (accounts, budget periods, backup/restore) are strictly gated.
    if (appMode == AppMode.SAVING) {
        SavingNavGraph(
            onSwitchToManager = {
                viewModel.lockForModeSwitch(AppMode.MANAGER)
            }
        )
        return
    }

    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val effectiveStartDestination = if (isOnboarded == false) Screen.Onboarding.route else startDestination

    LaunchedEffect(isOnboarded) {
        if (isOnboarded == true && currentRoute == Screen.Onboarding.route) {
            val destination = if (viewModel.mpinManager.isMpinSet.value) Screen.Dashboard.route else Screen.MpinSetup.route
            navController.navigate(destination) {
                popUpTo(Screen.Onboarding.route) { inclusive = true }
            }
        } else if (isOnboarded == false && currentRoute != null && currentRoute != Screen.Onboarding.route) {
            navController.navigate(Screen.Onboarding.route) {
                popUpTo(0) { inclusive = true }
            }
        }
    }

    val isPrimaryDestination = BOTTOM_NAV_ITEMS.any { it.route == currentRoute }
    val isKeyboardOpen = isKeyboardVisible()

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .background(com.tapledger.ui.theme.RadiantAppCanvasGradient),
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (isPrimaryDestination && !isKeyboardOpen) {
                Column {
                    HorizontalDivider(
                        thickness = 1.dp,
                        color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.9f)
                    )
                    NavigationBar(
                        containerColor = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.92f)
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
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = com.tapledger.ui.theme.GlassPrimary,
                                    selectedTextColor = com.tapledger.ui.theme.GlassPrimary,
                                    indicatorColor = com.tapledger.ui.theme.GlassPrimaryContainer.copy(alpha = 0.85f),
                                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                                ),
                                icon = { Icon(item.icon, contentDescription = item.title) },
                                label = {
                                    Text(
                                        text = item.title,
                                        maxLines = 1,
                                        softWrap = false,
                                        overflow = TextOverflow.Ellipsis,
                                        style = MaterialTheme.typography.labelMedium.copy(
                                            fontSize = 10.sp,
                                            fontWeight = if (selected) androidx.compose.ui.text.font.FontWeight.Bold else androidx.compose.ui.text.font.FontWeight.Medium,
                                            letterSpacing = 0.2.sp
                                        )
                                    )
                                }
                            )
                        }
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
                mpinManager = viewModel.mpinManager,
                navController = navController,
                startDestination = effectiveStartDestination,
                initialDashboardScrollIndex = initialDashboardScrollIndex,
                onShowWhatsNew = { viewModel.showWhatsNewManually() },
                modifier = Modifier.fillMaxSize()
            )

            if (shouldShowWhatsNew) {
                WhatsNewDialog(
                    releaseInfo = viewModel.currentReleaseNotes,
                    allReleases = viewModel.allReleaseNotes,
                    databaseSchemaVersion = viewModel.appVersionManager.databaseSchemaVersion,
                    onDismiss = { viewModel.dismissWhatsNew() }
                )
            }
        }
    }
}
