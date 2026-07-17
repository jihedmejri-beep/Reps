package com.reps.app.core.constants

object AppConstants {

    /** Past this the builder warns; the user may still continue. */
    const val EXERCISE_WARN_THRESHOLD = 10

    /** Hard cap. The builder blocks additions beyond this. */
    const val EXERCISE_HARD_CAP = 20

    const val MIN_PASSWORD_LENGTH = 8

    /** Firestore collection names. */
    object Collections {
        const val USERS = "users"
        const val EXERCISES = "exercises"
        const val TEMPLATES = "templates"
        const val SESSIONS = "sessions"
        const val ENTRIES = "entries"
        const val WORKOUT_TEMPLATES = "workoutTemplates"
        const val WORKOUT_SESSIONS = "workoutSessions"
        const val WEIGHT_LOGS = "weightLogs"
        const val MEALS = "meals"
    }

    object Notifications {
        const val CHANNEL_REMINDERS = "reps_reminders"
        const val CHANNEL_TIMER = "reps_timer"
        const val TIMER_NOTIFICATION_ID = 1001
    }
}
