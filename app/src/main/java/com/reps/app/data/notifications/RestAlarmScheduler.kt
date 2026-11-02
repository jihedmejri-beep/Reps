package com.reps.app.data.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Hands the rest countdown to the system while the app is in the background:
 * a live chronometer notification for the remaining time, and an alarm that
 * posts "rest over" when it ends.
 *
 * The alarm is exact where the platform allows it without an extra permission;
 * otherwise Android may deliver it a little late in Doze. The chronometer in the
 * running notification is drawn by the system and is always exact.
 */
@Singleton
class RestAlarmScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val notifications: NotificationHelper,
) {

    fun scheduleBackground(endsAtMs: Long, runningTitle: String, runningBody: String, doneTitle: String, doneBody: String) {
        if (endsAtMs <= System.currentTimeMillis()) return
        notifications.showRestRunning(runningTitle, runningBody, endsAtMs)
        val alarm = context.getSystemService(AlarmManager::class.java) ?: return
        val intent = pendingIntent(doneTitle, doneBody)
        val canExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarm.canScheduleExactAlarms()
        if (canExact) {
            alarm.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, endsAtMs, intent)
        } else {
            alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, endsAtMs, intent)
        }
    }

    /** Back in the app (or the rest ended, was skipped or the session closed): the screen takes over. */
    fun cancel() {
        context.getSystemService(AlarmManager::class.java)?.cancel(pendingIntent("", ""))
        notifications.cancelRest()
    }

    private fun pendingIntent(title: String, body: String): PendingIntent = PendingIntent.getBroadcast(
        context,
        REQUEST_CODE,
        Intent(context, RestDoneReceiver::class.java)
            .putExtra(EXTRA_TITLE, title)
            .putExtra(EXTRA_BODY, body),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    internal companion object {
        const val REQUEST_CODE = 5001
        const val EXTRA_TITLE = "title"
        const val EXTRA_BODY = "body"
    }
}

/** Fires when a background rest ends. Text is resolved when scheduled, in the app's language. */
class RestDoneReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val title = intent.getStringExtra(RestAlarmScheduler.EXTRA_TITLE).orEmpty()
        val body = intent.getStringExtra(RestAlarmScheduler.EXTRA_BODY).orEmpty()
        if (title.isEmpty()) return
        NotificationHelper(context.applicationContext).postRestDone(title, body)
    }
}
