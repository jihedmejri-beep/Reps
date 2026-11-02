package com.reps.app.data.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.reps.app.R
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Shared notification posting logic — handles permission checks, channel
 * creation, and vibration. All notification types route through here so
 * channel IDs and IDs stay in one place.
 */
@Singleton
class NotificationHelper @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    fun ensureChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_WORKOUT,
                context.getString(R.string.reminder_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT,
            ),
        )
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_HYDRATION,
                context.getString(R.string.hydration_channel_name),
                NotificationManager.IMPORTANCE_LOW,
            ),
        )
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_PR,
                context.getString(R.string.pr_channel_name),
                NotificationManager.IMPORTANCE_HIGH,
            ),
        )
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_REST_RUNNING,
                context.getString(R.string.rest_channel_running),
                NotificationManager.IMPORTANCE_LOW,
            ).apply { setShowBadge(false) },
        )
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_REST_DONE,
                context.getString(R.string.rest_channel_done),
                NotificationManager.IMPORTANCE_HIGH,
            ),
        )
    }

    /**
     * The rest countdown while the app is in the background. The system draws the
     * chronometer itself, counting down to [endsAtMs], so it stays exact without
     * the app running; it removes itself when the rest is over.
     */
    fun showRestRunning(title: String, body: String, endsAtMs: Long) {
        if (!hasPermission()) return
        val remaining = (endsAtMs - System.currentTimeMillis()).coerceAtLeast(0)
        val notification = NotificationCompat.Builder(context, CHANNEL_REST_RUNNING)
            .setSmallIcon(R.drawable.ic_stat_reps)
            .setContentTitle(title)
            .setContentText(body)
            .setWhen(endsAtMs)
            .setShowWhen(true)
            .setUsesChronometer(true)
            .setChronometerCountDown(true)
            .setTimeoutAfter(remaining)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_STOPWATCH)
            .setContentIntent(openAppIntent())
            .build()
        NotificationManagerCompat.from(context).notify(NOTIF_ID_REST, notification)
    }

    /** "Rest over" - posted by [RestDoneReceiver] when the alarm fires. */
    fun postRestDone(title: String, body: String) {
        if (!hasPermission()) return
        val notification = NotificationCompat.Builder(context, CHANNEL_REST_DONE)
            .setSmallIcon(R.drawable.ic_stat_reps)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(openAppIntent())
            .build()
        NotificationManagerCompat.from(context).notify(NOTIF_ID_REST, notification)
    }

    fun cancelRest() {
        NotificationManagerCompat.from(context).cancel(NOTIF_ID_REST)
    }

    private fun openAppIntent(): PendingIntent? {
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return null
        launch.flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        return PendingIntent.getActivity(context, 0, launch, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    fun postWorkoutReminder(title: String, body: String) {
        if (!hasPermission()) return
        val notification = NotificationCompat.Builder(context, CHANNEL_WORKOUT)
            .setSmallIcon(R.drawable.ic_stat_reps)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(NOTIF_ID_WORKOUT, notification)
    }

    fun postHydrationReminder(title: String, body: String) {
        if (!hasPermission()) return
        val notification = NotificationCompat.Builder(context, CHANNEL_HYDRATION)
            .setSmallIcon(R.drawable.ic_stat_reps)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(NOTIF_ID_HYDRATION, notification)
    }

    fun postPrNotification(exerciseName: String) {
        if (!hasPermission()) return
        vibratePr()
        val title = context.getString(R.string.pr_notification_title)
        val body = context.getString(R.string.pr_notification_body, exerciseName)
        val notification = NotificationCompat.Builder(context, CHANNEL_PR)
            .setSmallIcon(R.drawable.ic_stat_reps)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setVibrate(PR_VIBRATION_PATTERN)
            .build()
        NotificationManagerCompat.from(context).notify(NOTIF_ID_PR, notification)
    }

    private fun vibratePr() {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vm = context.getSystemService(VibratorManager::class.java)
            vm?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        } ?: return
        vibrator.vibrate(VibrationEffect.createWaveform(PR_VIBRATION_PATTERN, -1))
    }

    private fun hasPermission(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(
            context, Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    }

    companion object {
        const val CHANNEL_WORKOUT = "workout_reminders"
        const val CHANNEL_HYDRATION = "hydration_reminders"
        const val CHANNEL_PR = "pr_celebrations"
        const val CHANNEL_REST_RUNNING = "rest_timer_running"
        const val CHANNEL_REST_DONE = "rest_timer_done"

        private const val NOTIF_ID_WORKOUT = 4001
        private const val NOTIF_ID_HYDRATION = 4002
        private const val NOTIF_ID_PR = 4003
        private const val NOTIF_ID_REST = 4004

        /** Short buzz-pause-buzz pattern for PR celebrations. */
        private val PR_VIBRATION_PATTERN = longArrayOf(0, 200, 100, 200, 100, 400)
    }
}
