package com.reps.app.core.util

import androidx.annotation.StringRes
import com.reps.app.R
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth

object DateUtils {

    /** Greeting bands for the Home header. */
    @StringRes
    fun greetingFor(time: LocalTime = LocalTime.now()): Int = when (time.hour) {
        in 5..11 -> R.string.home_greeting_morning
        in 12..17 -> R.string.home_greeting_afternoon
        else -> R.string.home_greeting_evening
    }

    fun daysInMonth(month: YearMonth): List<LocalDate> =
        (1..month.lengthOfMonth()).map { month.atDay(it) }

    fun isSameMonth(date: LocalDate, month: YearMonth): Boolean =
        YearMonth.from(date) == month
}
