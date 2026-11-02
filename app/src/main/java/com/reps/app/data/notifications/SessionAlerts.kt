package com.reps.app.data.notifications

import javax.inject.Inject
import javax.inject.Singleton

/**
 * What a live workout needs from the notification system: PR alerts and the
 * background rest hand-off. An interface so the session logic can be tested
 * without Android.
 */
interface SessionAlerts {
    fun personalRecord(exerciseName: String)
    fun restInBackground(endsAtMs: Long, runningTitle: String, runningBody: String, doneTitle: String, doneBody: String)
    fun cancelRest()
}

@Singleton
class SystemSessionAlerts @Inject constructor(
    private val notifications: NotificationHelper,
    private val restAlarms: RestAlarmScheduler,
) : SessionAlerts {
    override fun personalRecord(exerciseName: String) = notifications.postPrNotification(exerciseName)

    override fun restInBackground(endsAtMs: Long, runningTitle: String, runningBody: String, doneTitle: String, doneBody: String) =
        restAlarms.scheduleBackground(endsAtMs, runningTitle, runningBody, doneTitle, doneBody)

    override fun cancelRest() = restAlarms.cancel()
}
