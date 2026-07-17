package com.reps.app.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.reps.app.core.theme.RepsNearBlack
import com.reps.app.feature.home.HomeScreen

@Composable
fun RepsApp(navController: NavHostController = rememberNavController()) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    val currentTab = TopLevelTab.entries.firstOrNull { it.route == currentRoute }

    Scaffold(
        containerColor = RepsNearBlack,
        bottomBar = {
            // The bar belongs to the tabs only: splash, auth and full-screen
            // flows like an active session must not show it.
            AnimatedVisibility(
                visible = currentTab != null,
                enter = slideInVertically { it },
                exit = slideOutVertically { it },
            ) {
                RepsBottomBar(
                    selected = currentTab ?: TopLevelTab.HOME,
                    onSelect = { tab -> navController.navigateToTab(tab) },
                )
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Routes.SPLASH,
            modifier = Modifier.fillMaxSize().padding(innerPadding),
        ) {
            repsGraph(navController)
        }
    }
}

private fun NavHostController.navigateToTab(tab: TopLevelTab) {
    navigate(tab.route) {
        // Tabs are siblings, not a stack: re-selecting one must not pile up
        // copies, and back from any tab returns to Home.
        popUpTo(Routes.HOME) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

private fun NavGraphBuilder.repsGraph(navController: NavHostController) {
    composable(Routes.SPLASH) { Placeholder("Splash") }
    composable(Routes.ONBOARDING) { Placeholder("Onboarding") }
    composable(Routes.LOGIN) { Placeholder("Login") }
    composable(Routes.SIGN_UP) { Placeholder("Sign Up") }

    composable(Routes.HOME) {
        HomeScreen(
            onStartWorkout = { navController.navigate(Routes.workoutSession(it)) },
            onOpenWeight = { navController.navigate(Routes.PROGRESS) },
            onOpenMeal = { navController.navigate(Routes.NUTRITION) },
            onOpenTimer = { navController.navigate(Routes.TIMER) },
            onOpenNotifications = { navController.navigate(Routes.NOTIFICATIONS) },
            onOpenProfile = { navController.navigate(Routes.PROFILE) },
        )
    }
    composable(Routes.PROGRESS) { Placeholder("Progress") }
    composable(Routes.WORKOUTS) { Placeholder("Workouts") }
    composable(Routes.NUTRITION) { Placeholder("Nutrition") }
    composable(Routes.PROFILE) { Placeholder("Profile") }

    composable(Routes.EXERCISE_DETAIL) { Placeholder("Exercise Detail") }
    composable(Routes.WORKOUT_BUILDER) { Placeholder("Workout Builder") }
    composable(Routes.WORKOUT_SESSION) { Placeholder("Workout Session") }
    composable(Routes.TIMER) { Placeholder("Timer") }
    composable(Routes.NOTIFICATIONS) { Placeholder("Notifications") }
}

// Temporary: each of these is replaced by its real screen as it lands.
@Composable
private fun Placeholder(name: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(name, color = Color.White)
    }
}
