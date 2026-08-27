package com.reps.app.data.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
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

        private const val NOTIF_ID_WORKOUT = 4001
        private const val NOTIF_ID_HYDRATION = 4002
        private const val NOTIF_ID_PR = 4003

        /** Short buzz-pause-buzz pattern for PR celebrations. */
        private val PR_VIBRATION_PATTERN = longArrayOf(0, 200, 100, 200, 100, 400)
    }
}
