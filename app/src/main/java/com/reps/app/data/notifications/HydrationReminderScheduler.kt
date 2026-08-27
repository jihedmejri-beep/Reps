package com.reps.app.data.notifications

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Duration
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Schedules the every-8-hour hydration reminder as a periodic WorkManager job.
 * Survives reboots and Doze without exact-alarm permissions.
 */
@Singleton
class HydrationReminderScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    fun setEnabled(enabled: Boolean) {
        val manager = WorkManager.getInstance(context)
        if (!enabled) {
            manager.cancelUniqueWork(HydrationReminderWorker.UNIQUE_WORK_NAME)
            return
        }
        val request = PeriodicWorkRequestBuilder<HydrationReminderWorker>(Duration.ofHours(8))
            .build()
        manager.enqueueUniquePeriodicWork(
            HydrationReminderWorker.UNIQUE_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }
}
