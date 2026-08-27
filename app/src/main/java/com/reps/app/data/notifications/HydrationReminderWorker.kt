package com.reps.app.data.notifications

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.reps.app.R
import com.reps.app.data.datastore.UserPreferencesDataStore
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first

/**
 * Periodic hydration nudge — fires every ~8 hours while notifications are
 * enabled.  Uses [NotificationHelper] so channel logic and permission checks
 * live in one place.
 */
@HiltWorker
class HydrationReminderWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val notificationHelper: NotificationHelper,
    private val preferences: UserPreferencesDataStore,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        if (!preferences.notificationsEnabled.first()) return Result.success()

        notificationHelper.postHydrationReminder(
            title = applicationContext.getString(R.string.hydration_notification_title),
            body = applicationContext.getString(R.string.hydration_notification_body),
        )
        return Result.success()
    }

    companion object {
        const val UNIQUE_WORK_NAME = "hydration_reminder"
    }
}
