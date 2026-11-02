package com.reps.app.core.util

import com.reps.app.domain.model.Streak
import com.reps.app.domain.model.StreakUnit
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Two kinds of streak, picked by whether the user has a schedule:
 *
 * - **Scheduled days** (any template has weekdays): consecutive planned days
 *   honoured. A planned day that passed with no session breaks it; unplanned days
 *   never do, and a session on an unplanned day still counts.
 * - **Weeks** (no schedule): consecutive Monday-Sunday weeks with at least one
 *   session. Without a plan there is no single day to miss, but a whole empty
 *   week is a real break.
 *
 * Earlier builds only had the first rule, and with no way to set a schedule every
 * gap counted as a rest day, so the "streak" was simply days ever trained.
 *
 * Today is never a miss: an unfinished today or this week is not counted against
 * the user until it is over.
 */
object StreakCalculator {

    fun compute(
        sessionDates: Set<LocalDate>,
        scheduledDays: Set<DayOfWeek>,
        today: LocalDate = LocalDate.now(),
    ): Streak = if (scheduledDays.isEmpty()) {
        weekly(sessionDates, today)
    } else {
        scheduled(sessionDates, scheduledDays, today)
    }

    fun scheduled(sessionDates: Set<LocalDate>, scheduledDays: Set<DayOfWeek>, today: LocalDate): Streak {
        val last = sessionDates.filter { !it.isAfter(today) }.maxOrNull()
            ?: return Streak(0, null, StreakUnit.SCHEDULED_DAYS)
        val earliest = sessionDates.min()
        var cursor = if (today in sessionDates) today else today.minusDays(1)
        var count = 0
        while (!cursor.isBefore(earliest)) {
            when {
                cursor in sessionDates -> count++
                cursor.dayOfWeek in scheduledDays -> break
            }
            cursor = cursor.minusDays(1)
        }
        return Streak(count, last, StreakUnit.SCHEDULED_DAYS)
    }

    fun weekly(sessionDates: Set<LocalDate>, today: LocalDate): Streak {
        val last = sessionDates.filter { !it.isAfter(today) }.maxOrNull()
            ?: return Streak(0, null, StreakUnit.WEEKS)
        val weeks = sessionDates.map { it.with(DayOfWeek.MONDAY) }.toSet()
        val thisWeek = today.with(DayOfWeek.MONDAY)
        var cursor = if (thisWeek in weeks) thisWeek else thisWeek.minusWeeks(1)
        var count = 0
        while (cursor in weeks) {
            count++
            cursor = cursor.minusWeeks(1)
        }
        return Streak(count, last, StreakUnit.WEEKS)
    }

    /** Consecutive weeks with a session regardless of schedule; achievements use this. */
    fun weeksInARow(sessionDates: Set<LocalDate>, today: LocalDate): Int = weekly(sessionDates, today).count

    /** Days since the last session, or null with no history. Used for gentle "welcome back" copy. */
    fun daysSinceLast(sessionDates: Set<LocalDate>, today: LocalDate): Long? =
        sessionDates.filter { !it.isAfter(today) }.maxOrNull()?.let { ChronoUnit.DAYS.between(it, today) }
}
