package com.tapledger.ui.navigation

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.tapledger.ui.screens.saving.GoalDetailScreen
import com.tapledger.ui.screens.saving.SavingHomeScreen
import com.tapledger.ui.theme.MotionSpec

@Composable
fun SavingNavGraph(
    onSwitchToManager: () -> Unit,
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController()
) {
    NavHost(
        navController = navController,
        startDestination = Screen.SavingHome.route,
        modifier = modifier.fillMaxSize(),
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
        composable(Screen.SavingHome.route) {
            SavingHomeScreen(
                canNavigateBack = false,
                onNavigateBack = {},
                onSwitchToManager = onSwitchToManager,
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
            GoalDetailScreen(
                goalId = goalId,
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}
