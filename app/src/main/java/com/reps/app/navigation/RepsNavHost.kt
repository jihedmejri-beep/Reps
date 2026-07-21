package com.reps.app.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.reps.app.core.components.backdropSource
import com.reps.app.core.components.rememberBackdropState
import com.reps.app.core.theme.RepsNearBlack
import com.reps.app.core.theme.RepsTheme
import com.reps.app.feature.auth.login.LoginScreen
import com.reps.app.feature.auth.signup.SignUpScreen
import com.reps.app.feature.home.HomeScreen
import com.reps.app.feature.notifications.NotificationsScreen
import com.reps.app.feature.nutrition.NutritionScreen
import com.reps.app.feature.onboarding.OnboardingScreen
import com.reps.app.feature.profile.ProfileScreen
import com.reps.app.feature.progress.ProgressScreen
import com.reps.app.feature.splash.SplashDestination
import com.reps.app.feature.splash.SplashScreen
import com.reps.app.feature.workouts.ExerciseDetailScreen
import com.reps.app.feature.workouts.WorkoutsScreen
import com.reps.app.feature.workouts.builder.WorkoutBuilderScreen
import com.reps.app.feature.workouts.session.WorkoutSessionScreen

/** How far a tab screen offsets before sliding into place. */
private val TabSlide = 28.dp

/** How far the outgoing screen parallaxes back on a push, as a fraction. */
private const val PushParallax = 0.28f

private const val ScreenSlideMs = 380
private const val ScreenFadeMs = 280

/**
 * Bottom padding a scrolling tab screen needs so its content can clear the
 * floating nav pill, system inset included.
 */
@Composable
fun navBarClearance(): Dp =
    RepsTheme.dimens.navClearance +
        WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

@Composable
fun RepsApp(navController: NavHostController = rememberNavController()) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val currentTab = TopLevelTab.entries.firstOrNull { it.route == currentRoute }

    val backdrop = rememberBackdropState()
    val navBarScroll = rememberNavBarScrollBehavior()
    val tabReselect = rememberTabReselectState()
    val density = LocalDensity.current
    val tabSlidePx = with(density) { TabSlide.roundToPx() }

    // A screen the user has just arrived at starts scrolled to the top, so the
    // pill must not still be hidden from how they left the previous one.
    LaunchedEffect(currentRoute) { navBarScroll.reset() }

    CompositionLocalProvider(LocalTabReselect provides tabReselect) {
        Box(Modifier.fillMaxSize().background(RepsNearBlack)) {
            Box(
                Modifier
                    .fillMaxSize()
                    .nestedScroll(navBarScroll.connection)
                    // Recorded so the pill can sample it as a blurred backdrop.
                    // The pill is a sibling below, never a child: recording it
                    // into its own source would blur it against itself, frame
                    // over frame.
                    .backdropSource(backdrop),
            ) {
                NavHost(
                    navController = navController,
                    startDestination = Routes.SPLASH,
                    modifier = Modifier.fillMaxSize(),
                    enterTransition = { tabAwareEnter(tabSlidePx) },
                    exitTransition = { tabAwareExit() },
                    popEnterTransition = { popEnter() },
                    popExitTransition = { popExit() },
                ) {
                    repsGraph(navController)
                }
            }

            // The bar belongs to the tabs only: splash, auth and full-screen
            // flows like an active session must not show it.
            AnimatedVisibility(
                visible = currentTab != null,
                enter = slideInVertically { it } + fadeIn(),
                exit = slideOutVertically { it } + fadeOut(),
                modifier = Modifier.align(Alignment.BottomCenter),
            ) {
                RepsFloatingNavBar(
                    selected = currentTab ?: TopLevelTab.HOME,
                    onSelect = { tab ->
                        // Re-tapping the current tab scrolls it back to the top
                        // instead of navigating to where you already are.
                        if (tab == currentTab) {
                            tabReselect.signal(tab.route)
                        } else {
                            navController.navigateToTab(tab)
                        }
                    },
                    backdrop = backdrop,
                    hidden = !navBarScroll.visible,
                )
            }
        }
    }
}

private fun NavBackStackEntry.tabIndex(): Int =
    TopLevelTab.entries.indexOfFirst { it.route == destination.route }

/**
 * Tab switches cross-slide in the direction of travel; anything else is a stack
 * push and slides in from the end edge.
 */
private fun AnimatedContentTransitionScope<NavBackStackEntry>.tabAwareEnter(
    slidePx: Int,
): EnterTransition {
    val from = initialState.tabIndex()
    val to = targetState.tabIndex()
    return if (from >= 0 && to >= 0) {
        val direction = if (to > from) 1 else -1
        slideInHorizontally(tween(ScreenSlideMs, easing = ExpoOut)) { slidePx * direction } +
            fadeIn(tween(ScreenFadeMs))
    } else {
        slideInHorizontally(tween(ScreenSlideMs, easing = ExpoOut)) { it }
    }
}

private fun AnimatedContentTransitionScope<NavBackStackEntry>.tabAwareExit(): ExitTransition {
    val from = initialState.tabIndex()
    val to = targetState.tabIndex()
    // Between tabs the outgoing screen only fades - sliding both directions at
    // once reads as the whole app lurching sideways.
    return if (from >= 0 && to >= 0) {
        fadeOut(tween(ScreenFadeMs))
    } else {
        slideOutHorizontally(tween(ScreenSlideMs, easing = ExpoOut)) {
            -(it * PushParallax).toInt()
        } + fadeOut(tween(ScreenSlideMs), targetAlpha = 0.55f)
    }
}

private fun AnimatedContentTransitionScope<NavBackStackEntry>.popEnter(): EnterTransition =
    slideInHorizontally(tween(ScreenSlideMs, easing = ExpoOut)) {
        -(it * PushParallax).toInt()
    } + fadeIn(tween(ScreenSlideMs), initialAlpha = 0.55f)

private fun AnimatedContentTransitionScope<NavBackStackEntry>.popExit(): ExitTransition =
    slideOutHorizontally(tween(ScreenSlideMs, easing = ExpoOut)) { it }

private fun NavHostController.navigateToTab(tab: TopLevelTab) {
    navigate(tab.route) {
        // Tabs are siblings, not a stack: re-selecting one must not pile up
        // copies, and back from any tab returns to Home.
        popUpTo(Routes.HOME) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

/** Replaces the whole back stack, so back never returns to splash or auth. */
private fun NavHostController.replaceWith(route: String) {
    navigate(route) {
        popUpTo(graph.id) { inclusive = true }
        launchSingleTop = true
    }
}

private fun NavGraphBuilder.repsGraph(navController: NavHostController) {
    composable(Routes.SPLASH) {
        SplashScreen(
            onFinished = { destination ->
                navController.replaceWith(
                    when (destination) {
                        SplashDestination.ONBOARDING -> Routes.ONBOARDING
                        SplashDestination.LOGIN -> Routes.LOGIN
                        SplashDestination.HOME -> Routes.HOME
                    },
                )
            },
        )
    }

    // Built to the written brief's single screen, not the three-page carousel
    // the start-animation video happens to show; the blueprint is authoritative.
    composable(Routes.ONBOARDING) {
        OnboardingScreen(
            onGetStarted = { navController.replaceWith(Routes.SIGN_UP) },
            onHaveAccount = { navController.replaceWith(Routes.LOGIN) },
        )
    }

    composable(Routes.LOGIN) {
        LoginScreen(
            onSignedIn = { navController.replaceWith(Routes.HOME) },
            onCreateAccount = { navController.navigate(Routes.SIGN_UP) },
            onBack = { navController.popBackStack() },
        )
    }
    composable(Routes.SIGN_UP) {
        SignUpScreen(
            onSignedUp = { navController.replaceWith(Routes.HOME) },
            onSignIn = { navController.popBackStack() },
            onBack = { navController.popBackStack() },
        )
    }

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
    composable(Routes.PROGRESS) { ProgressScreen() }
    composable(Routes.WORKOUTS) {
        WorkoutsScreen(
            onOpenExercise = { exerciseId -> navController.navigate(Routes.exerciseDetail(exerciseId)) },
            onStartWorkout = { workoutId -> navController.navigate(Routes.workoutSession(workoutId)) },
            onOpenBuilder = { navController.navigate(Routes.WORKOUT_BUILDER) },
        )
    }
    composable(Routes.NUTRITION) { NutritionScreen() }
    composable(Routes.PROFILE) {
        ProfileScreen(onSignedOut = { navController.replaceWith(Routes.LOGIN) })
    }

    composable(Routes.EXERCISE_DETAIL) {
        ExerciseDetailScreen(onBack = { navController.popBackStack() })
    }
    composable(Routes.WORKOUT_BUILDER) {
        WorkoutBuilderScreen(
            onBack = { navController.popBackStack() },
            onSaved = { navController.popBackStack() },
        )
    }
    composable(Routes.WORKOUT_SESSION) {
        WorkoutSessionScreen(
            onFinished = { navController.popBackStack() },
            onBack = { navController.popBackStack() },
        )
    }
    composable(Routes.TIMER) { Placeholder("Timer") }
    composable(Routes.NOTIFICATIONS) {
        NotificationsScreen(onBack = { navController.popBackStack() })
    }
}

// Temporary: each of these is replaced by its real screen as it lands.
@Composable
private fun Placeholder(name: String, onClick: (() -> Unit)? = null) {
    Box(
        Modifier
            .fillMaxSize()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Text(if (onClick == null) name else "$name\n(tap to continue)", color = Color.White)
    }
}
