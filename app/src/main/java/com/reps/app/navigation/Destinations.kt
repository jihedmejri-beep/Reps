package com.reps.app.navigation

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.reps.app.R

object Routes {
    const val SPLASH = "splash"
    const val ONBOARDING = "onboarding"
    const val LOGIN = "login"
    const val SIGN_UP = "signup"

    const val HOME = "home"
    const val PROGRESS = "progress"
    const val WORKOUTS = "workouts"
    const val NUTRITION = "nutrition"
    const val PROFILE = "profile"

    const val EXERCISE_DETAIL = "exercise/{exerciseId}"
    const val WORKOUT_BUILDER = "workout/builder"
    const val WORKOUT_SESSION = "workout/session/{workoutId}"
    const val TIMER = "timer"
    const val NOTIFICATIONS = "notifications"

    fun exerciseDetail(exerciseId: String) = "exercise/$exerciseId"
    fun workoutSession(workoutId: String) = "workout/session/$workoutId"
}

/**
 * Optional argument on [Routes.PROGRESS] and [Routes.NUTRITION]. The Home quick
 * actions deep-link into a tab with an input already open, so the tab needs to
 * know it was entered that way.
 */
object NavArgs {
    const val OPEN_ADD_WEIGHT = "openAddWeight"
    const val OPEN_ADD_MEAL = "openAddMeal"
    const val EXERCISE_ID = "exerciseId"
    const val WORKOUT_ID = "workoutId"
}

/**
 * The five bottom-bar tabs, in the order fixed by the brief:
 * Home, Progress, Workouts, Nutrition, Profile.
 */
enum class TopLevelTab(
    val route: String,
    @param:StringRes val labelRes: Int,
    @param:DrawableRes val iconRes: Int,
) {
    HOME(Routes.HOME, R.string.nav_home, R.drawable.ic_home),
    PROGRESS(Routes.PROGRESS, R.string.nav_progress, R.drawable.ic_progress),
    WORKOUTS(Routes.WORKOUTS, R.string.nav_workouts, R.drawable.ic_workouts),
    NUTRITION(Routes.NUTRITION, R.string.nav_nutrition, R.drawable.ic_nutrition),
    PROFILE(Routes.PROFILE, R.string.nav_profile, R.drawable.ic_profile),
}
