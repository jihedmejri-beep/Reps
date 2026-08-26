package com.reps.app.data.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
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
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        if (!preferences.notificationsEnabled.first()) return Result.success()

        val workout = workoutRepository.observeWorkoutFor(LocalDate.now()).first()
            ?: return Result.success()

        postNotification(
            title = applicationContext.getString(R.string.reminder_notification_title),
            body = applicationContext.getString(R.string.reminder_notification_body, workout.name),
        )
        return Result.success()
    }

    private fun postNotification(title: String, body: String) {
        val context = applicationContext
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            // Permission was revoked after the toggle was switched on; stay quiet.
            return
        }

        val manager = ContextCompat.getSystemService(context, NotificationManager::class.java)
            ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    context.getString(R.string.reminder_channel_name),
                    NotificationManager.IMPORTANCE_DEFAULT,
                ),
            )
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_reps)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
    }

    companion object {
        const val UNIQUE_WORK_NAME = "daily_workout_reminder"
        private const val CHANNEL_ID = "workout_reminders"
        private const val NOTIFICATION_ID = 4001
    }
}
