package com.reps.app.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.Dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.reps.app.core.components.backdropSource
import com.reps.app.core.components.rememberBackdropState
import com.reps.app.core.theme.RepsTheme
import com.reps.app.feature.auth.login.LoginScreen
import com.reps.app.feature.auth.signup.SignUpScreen
import com.reps.app.feature.home.HomeScreen
import com.reps.app.feature.notifications.NotificationsScreen
import com.reps.app.feature.nutrition.NutritionScreen
import com.reps.app.feature.nutrition.assistant.ConversationHistoryScreen
import com.reps.app.feature.nutrition.assistant.NutritionAssistantScreen
import com.reps.app.feature.nutrition.assistant.NutritionAssistantViewModel
import com.reps.app.feature.onboarding.OnboardingScreen
import com.reps.app.feature.profile.ProfileScreen
import com.reps.app.feature.progress.ProgressScreen
import com.reps.app.feature.splash.SplashDestination
import com.reps.app.feature.splash.SplashScreen
import com.reps.app.feature.workouts.ExerciseDetailScreen
import com.reps.app.feature.workouts.WorkoutsScreen
import com.reps.app.feature.workouts.builder.WorkoutBuilderScreen
import com.reps.app.feature.workouts.session.WorkoutSessionScreen
import kotlinx.coroutines.launch

/** How far the outgoing screen parallaxes back on a push, as a fraction. */
private const val PushParallax = 0.28f

private const val ScreenSlideMs = 380

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

    val tabs = remember { TopLevelTab.entries }
    val pagerState = rememberPagerState(pageCount = { tabs.size })
    val scope = rememberCoroutineScope()

    // The tabs are one destination, so "which tab" is the pager's page rather
    // than the route. Off that destination there is no current tab at all,
    // which is what hides the bar on splash, auth and full-screen pushes.
    val onTabs = currentRoute == Routes.TABS
    val currentTab = if (onTabs) tabs[pagerState.currentPage] else null

    val backdrop = rememberBackdropState()
    val navBarScroll = rememberNavBarScrollBehavior()
    val tabReselect = rememberTabReselectState()

    // Arriving at a screen - or swiping to another tab - starts at the top, so
    // the pill must not still be hidden from how the previous one was left.
    LaunchedEffect(currentRoute, pagerState.currentPage) { navBarScroll.reset() }

    // Back from any tab returns to Home before it leaves the app, which is the
    // behaviour the popUpTo(HOME) back stack used to give.
    BackHandler(enabled = onTabs && pagerState.currentPage != 0) {
        scope.launch { pagerState.animateScrollToPage(0) }
    }

    val selectTab: (TopLevelTab) -> Unit = { tab ->
        val index = tabs.indexOf(tab)
        if (tab == currentTab) {
            // Re-tapping the current tab scrolls it to the top instead of
            // navigating to where you already are.
            tabReselect.signal(tab.route)
        } else if (index >= 0) {
            scope.launch { pagerState.animateScrollToPage(index) }
        }
    }

    CompositionLocalProvider(LocalTabReselect provides tabReselect) {
        Box(Modifier.fillMaxSize().background(RepsTheme.colors.background)) {
            Box(
                Modifier
                    .fillMaxSize()
                    .nestedScroll(navBarScroll.connection)
                    // Recorded so the pill can sample it as a blurred backdrop.
                    // The pill is a sibling below, never a child: recording it
                    // into its own source would blur it against itself, frame
                    // over frame. Only recorded while a pill exists to sample
                    // it - on splash, auth and full-screen pushes the capture
                    // would be pure cost.
                    .backdropSource(backdrop, enabled = currentTab != null),
            ) {
                NavHost(
                    navController = navController,
                    startDestination = Routes.SPLASH,
                    modifier = Modifier.fillMaxSize(),
                    enterTransition = {
                        slideInHorizontally(tween(ScreenSlideMs, easing = ExpoOut)) { it }
                    },
                    exitTransition = {
                        slideOutHorizontally(tween(ScreenSlideMs, easing = ExpoOut)) {
                            -(it * PushParallax).toInt()
                        } + fadeOut(tween(ScreenSlideMs), targetAlpha = 0.55f)
                    },
                    popEnterTransition = {
                        slideInHorizontally(tween(ScreenSlideMs, easing = ExpoOut)) {
                            -(it * PushParallax).toInt()
                        } + fadeIn(tween(ScreenSlideMs), initialAlpha = 0.55f)
                    },
                    popExitTransition = {
                        slideOutHorizontally(tween(ScreenSlideMs, easing = ExpoOut)) { it }
                    },
                ) {
                    repsGraph(navController, pagerState, selectTab)
                }
            }

            AnimatedVisibility(
                visible = currentTab != null,
                enter = slideInVertically { it } + fadeIn(),
                exit = slideOutVertically { it } + fadeOut(),
                modifier = Modifier.align(Alignment.BottomCenter),
            ) {
                RepsFloatingNavBar(
                    selected = currentTab ?: TopLevelTab.HOME,
                    onSelect = selectTab,
                    backdrop = backdrop,
                    hidden = !navBarScroll.visible,
                )
            }
        }
    }
}

/** Replaces the whole back stack, so back never returns to splash or auth. */
private fun NavHostController.replaceWith(route: String) {
    navigate(route) {
        popUpTo(graph.id) { inclusive = true }
        launchSingleTop = true
    }
}

/**
 * The five tabs as pages of one pager, so they can be swiped between as well as
 * tapped. Only the page in view is kept composed, so this costs one screen at a
 * time rather than five.
 */
@Composable
private fun TabPager(
    pagerState: PagerState,
    navController: NavHostController,
    onSelectTab: (TopLevelTab) -> Unit,
) {
    val tabs = remember { TopLevelTab.entries }
    HorizontalPager(
        state = pagerState,
        modifier = Modifier.fillMaxSize(),
        beyondViewportPageCount = 0,
        key = { tabs[it].route },
    ) { page ->
        when (tabs[page]) {
            TopLevelTab.HOME -> HomeScreen(
                onStartWorkout = { navController.navigate(Routes.workoutSession(it)) },
                // The weight widget deep-links into Progress; that is a tab, so
                // it moves the pager rather than pushing a second copy of a tab
                // on top of itself.
                onOpenWeight = { onSelectTab(TopLevelTab.PROGRESS) },
                onOpenNotifications = { navController.navigate(Routes.NOTIFICATIONS) },
                onOpenProfile = { onSelectTab(TopLevelTab.PROFILE) },
            )

            TopLevelTab.PROGRESS -> ProgressScreen()

            TopLevelTab.WORKOUTS -> WorkoutsScreen(
                onOpenExercise = { navController.navigate(Routes.exerciseDetail(it)) },
                onStartWorkout = { navController.navigate(Routes.workoutSession(it)) },
                onOpenBuilder = { navController.navigate(Routes.WORKOUT_BUILDER) },
            )

            TopLevelTab.NUTRITION -> NutritionScreen(
                onOpenAssistant = { navController.navigate(Routes.NUTRITION_ASSISTANT) },
            )

            TopLevelTab.PROFILE -> ProfileScreen(
                onSignedOut = { navController.replaceWith(Routes.LOGIN) },
            )
        }
    }
}

private fun NavGraphBuilder.repsGraph(
    navController: NavHostController,
    pagerState: PagerState,
    onSelectTab: (TopLevelTab) -> Unit,
) {
    composable(Routes.SPLASH) {
        SplashScreen(
            onFinished = { destination ->
                navController.replaceWith(
                    when (destination) {
                        SplashDestination.ONBOARDING -> Routes.ONBOARDING
                        SplashDestination.LOGIN -> Routes.LOGIN
                        SplashDestination.HOME -> Routes.TABS
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
            onSignedIn = { navController.replaceWith(Routes.TABS) },
            onCreateAccount = { navController.navigate(Routes.SIGN_UP) },
            onBack = { navController.popBackStack() },
        )
    }
    composable(Routes.SIGN_UP) {
        SignUpScreen(
            onSignedUp = { navController.replaceWith(Routes.TABS) },
            onSignIn = { navController.popBackStack() },
            onBack = { navController.popBackStack() },
        )
    }

    composable(Routes.TABS) {
        TabPager(
            pagerState = pagerState,
            navController = navController,
            onSelectTab = onSelectTab,
        )
    }

    composable(Routes.NUTRITION_ASSISTANT) {
        NutritionAssistantScreen(
            onBack = { navController.popBackStack() },
            onOpenHistory = { navController.navigate(Routes.NUTRITION_ASSISTANT_HISTORY) },
            viewModel = hiltViewModel(),
        )
    }
    composable(Routes.NUTRITION_ASSISTANT_HISTORY) { entry ->
        // Scoped to the assistant's entry, not this one, so picking a
        // conversation lands in the transcript the user came from. That entry is
        // still on the stack: history is only reachable from on top of it.
        val assistantEntry = remember(entry) {
            navController.getBackStackEntry(Routes.NUTRITION_ASSISTANT)
        }
        val viewModel: NutritionAssistantViewModel = hiltViewModel(assistantEntry)
        ConversationHistoryScreen(
            onBack = { navController.popBackStack() },
            onOpen = { id ->
                viewModel.openConversation(id)
                navController.popBackStack()
            },
            viewModel = viewModel,
        )
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

// Temporary: replaced by the real screen when it lands.
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
