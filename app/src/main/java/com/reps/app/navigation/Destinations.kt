package com.reps.app.navigation

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.reps.app.R

object Routes {
    const val SPLASH = "splash"
    const val ONBOARDING = "onboarding"
    const val LOGIN = "login"
    const val SIGN_UP = "signup"

    /**
     * The five tabs live behind a single destination, because they are pages of
     * one pager rather than separate entries on the back stack - that is what
     * lets a swipe carry between them. The per-tab constants below are still
     * used as stable identifiers (pager keys, tab-reselect signals), just no
     * longer as navigation routes.
     */
    const val TABS = "tabs"

    const val HOME = "home"
    const val PROGRESS = "progress"
    const val WORKOUTS = "workouts"
    const val NUTRITION = "nutrition"
    const val PROFILE = "profile"

    /**
     * The nutrition assistant, pushed from the Nutrition tab. History is a
     * separate destination on top of it rather than a sheet, so the two share
     * one ViewModel through the assistant's back stack entry.
     */
    const val NUTRITION_ASSISTANT = "nutrition/assistant"
    const val NUTRITION_ASSISTANT_HISTORY = "nutrition/assistant/history"

    const val EXERCISE_DETAIL = "exercise/{exerciseId}"
    const val WORKOUT_BUILDER = "workout/builder"
    const val WORKOUT_SESSION = "workout/session/{workoutId}"
    const val NOTIFICATIONS = "notifications"

    fun exerciseDetail(exerciseId: String) = "exercise/$exerciseId"
    fun workoutSession(workoutId: String) = "workout/session/$workoutId"
}

/**
 * Argument on the exercise-detail and workout-session routes.
 */
object NavArgs {
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
