package com.reps.app.core.util

import com.reps.app.domain.model.Streak
import java.time.DayOfWeek
import java.time.LocalDate

/**
 * Streaks count consecutive *scheduled* days honoured, not consecutive
 * calendar days: a rest day the user planned must not break the chain, and an
 * unscheduled day with no session is not a miss either.
 *
 * The walk starts at yesterday when today has no session yet - an unfinished
 * today is never a broken streak, it just is not counted until a set is done.
 */
object StreakCalculator {

    fun compute(
        sessionDates: Set<LocalDate>,
        scheduledDays: Set<DayOfWeek>,
        today: LocalDate = LocalDate.now(),
    ): Streak {
        val lastWorkoutDate = sessionDates.maxOrNull()
        if (sessionDates.isEmpty()) return Streak(count = 0, lastWorkoutDate = null)

        var cursor = if (today in sessionDates) today else today.minusDays(1)
        val earliest = sessionDates.min()

        var count = 0
        while (!cursor.isBefore(earliest)) {
            when {
                cursor in sessionDates -> count++
                // A planned day that came and went without training breaks the chain.
                cursor.dayOfWeek in scheduledDays && cursor.isBefore(today) -> break
                // Anything else was a rest day; skip over it.
            }
            cursor = cursor.minusDays(1)
        }
        return Streak(count = count, lastWorkoutDate = lastWorkoutDate)
    }
}
