package com.reps.app.feature.progress

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.outlined.MilitaryTech
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.Scale
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Whatshot
import androidx.compose.ui.graphics.vector.ImageVector
import com.reps.app.R
import com.reps.app.domain.progress.AchievementId

/** How an achievement looks and reads; the rule itself lives in the domain layer. */
data class AchievementPresentation(
    val icon: ImageVector,
    @param:StringRes val titleRes: Int,
    @param:StringRes val descriptionRes: Int,
)

fun AchievementId.presentation(): AchievementPresentation = when (this) {
    AchievementId.FIRST_WORKOUT -> AchievementPresentation(Icons.Outlined.FitnessCenter, R.string.ach_first_workout_title, R.string.ach_first_workout_desc)
    AchievementId.TEN_WORKOUTS -> AchievementPresentation(Icons.Outlined.Schedule, R.string.ach_ten_workouts_title, R.string.ach_ten_workouts_desc)
    AchievementId.FIFTY_WORKOUTS -> AchievementPresentation(Icons.Outlined.MilitaryTech, R.string.ach_fifty_workouts_title, R.string.ach_fifty_workouts_desc)
    AchievementId.STREAK_7 -> AchievementPresentation(Icons.Outlined.Whatshot, R.string.ach_streak7_title, R.string.ach_streak7_desc)
    AchievementId.WEEKS_4 -> AchievementPresentation(Icons.Outlined.CalendarMonth, R.string.ach_weeks4_title, R.string.ach_weeks4_desc)
    AchievementId.FIRST_PR -> AchievementPresentation(Icons.Outlined.EmojiEvents, R.string.ach_first_pr_title, R.string.ach_first_pr_desc)
    AchievementId.VOLUME_10T -> AchievementPresentation(Icons.Outlined.Scale, R.string.ach_volume10t_title, R.string.ach_volume10t_desc)
    AchievementId.REPS_1000 -> AchievementPresentation(Icons.Outlined.Repeat, R.string.ach_reps1000_title, R.string.ach_reps1000_desc)
}
