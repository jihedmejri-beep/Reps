package com.reps.app.core.util

import androidx.annotation.StringRes
import com.reps.app.R
import java.time.LocalTime
import java.util.Locale

object DateUtils {

    /** Greeting bands for the Home header. */
    @StringRes
    fun greetingFor(time: LocalTime = LocalTime.now()): Int = when (time.hour) {
        in 5..11 -> R.string.home_greeting_morning
        in 12..17 -> R.string.home_greeting_afternoon
        else -> R.string.home_greeting_evening
    }

    /** `m:ss`, e.g. 90 -> "1:30". Locale-fixed so digits never localise to non-Latin numerals. */
    fun formatClock(totalSeconds: Int): String {
        val clamped = totalSeconds.coerceAtLeast(0)
        return String.format(Locale.US, "%d:%02d", clamped / 60, clamped % 60)
    }

    /** Workout elapsed time: `m:ss` under an hour, `h:mm:ss` after. Same digit policy as [formatClock]. */
    fun formatElapsed(totalSeconds: Long): String {
        val s = totalSeconds.coerceAtLeast(0)
        return if (s < 3_600) {
            String.format(Locale.US, "%d:%02d", s / 60, s % 60)
        } else {
            String.format(Locale.US, "%d:%02d:%02d", s / 3_600, (s % 3_600) / 60, s % 60)
        }
    }
}
