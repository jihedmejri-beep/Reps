package com.reps.app.data.notifications

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Turns the profile's notifications toggle into real scheduled work.
 *
 * One unique periodic job runs roughly every day at [FIRE_TIME]; enabling the
 * toggle enqueues it (replacing any stale schedule), disabling cancels it.
 * WorkManager persists the schedule, so nothing needs re-arming at boot.
 */
@Singleton
class WorkoutReminderScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    suspend fun setEnabled(enabled: Boolean) = withContext(Dispatchers.Default) {
        val manager = WorkManager.getInstance(context)
        if (!enabled) {
            manager.cancelUniqueWork(WorkoutReminderWorker.UNIQUE_WORK_NAME)
            return@withContext
        }
        val request = PeriodicWorkRequestBuilder<WorkoutReminderWorker>(Duration.ofHours(24))
            .setInitialDelay(delayUntilFireTime())
            .build()
        manager.enqueueUniquePeriodicWork(
            WorkoutReminderWorker.UNIQUE_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }

    /** First run lands at today's fire time if it is still ahead, else tomorrow's. */
    private fun delayUntilFireTime(): Duration {
        val now = LocalDateTime.now()
        val next = if (now.toLocalTime() < FIRE_TIME) {
            now.with(FIRE_TIME)
        } else {
            now.plusDays(1).with(FIRE_TIME)
        }
        return Duration.between(now, next)
    }

    private companion object {
        /** Early evening: close enough to plan around, not so late it nags. */
        val FIRE_TIME: LocalTime = LocalTime.of(18, 0)
    }
}
