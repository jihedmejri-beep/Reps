package com.reps.app.data.notifications

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.reps.app.R
import com.reps.app.data.datastore.UserPreferencesDataStore
import com.reps.app.domain.repository.WorkoutRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import java.time.LocalDate

/**
 * The daily nudge: once a day, check whether a workout is scheduled for today
 * and post a notification when there is one (and the user still wants nudges).
 *
 * Runs as a periodic WorkManager job, so it survives reboots and Doze without
 * exact-alarm permissions.
 */
@HiltWorker
class WorkoutReminderWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val workoutRepository: WorkoutRepository,
    private val preferences: UserPreferencesDataStore,
    private val notificationHelper: NotificationHelper,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        if (!preferences.notificationsEnabled.first()) return Result.success()

        val workout = workoutRepository.observeWorkoutFor(LocalDate.now()).first()
            ?: return Result.success()

        notificationHelper.postWorkoutReminder(
            title = applicationContext.getString(R.string.reminder_notification_title),
            body = applicationContext.getString(R.string.reminder_notification_body, workout.name),
        )
        return Result.success()
    }

    companion object {
        const val UNIQUE_WORK_NAME = "daily_workout_reminder"
    }
}
