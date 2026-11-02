package com.reps.app.core.content

import com.reps.app.core.util.StreakCalculator
import com.reps.app.domain.model.Workout
import com.reps.app.domain.model.WorkoutSession
import java.time.DayOfWeek
import java.time.LocalDate

/**
 * One short line about the user's own training, shown under the daily quote.
 *
 * Every remark is a plain reading of logged data - nothing is inferred or
 * flattered - and none of them scolds: a gap gets a way back, not a guilt trip.
 * When there is nothing true and useful to say, there is no remark.
 */
sealed interface Remark {
    data object FirstWorkout : Remark
    data class NewBest(val exerciseName: String) : Remark
    data class LoggedToday(val workoutName: String) : Remark
    data class PlannedToday(val workoutName: String) : Remark
    data class WelcomeBack(val daysSince: Int) : Remark
    data class AheadOfLastWeek(val thisWeek: Int, val lastWeek: Int) : Remark
    data class WeekStreak(val weeks: Int) : Remark
}

object DailyRemark {

    /** Days without a session before the gentle "welcome back" line appears. */
    private const val WELCOME_BACK_AFTER_DAYS = 4

    fun pick(
        sessions: List<WorkoutSession>,
        templates: List<Workout>,
        today: LocalDate,
        exerciseName: (String) -> String?,
    ): Remark? {
        val worked = sessions.filter { it.hasWork && !it.date.isAfter(today) }
        if (worked.isEmpty()) return Remark.FirstWorkout

        val todays = worked.filter { it.date == today }
        todays.flatMap { it.prsHit }.firstNotNullOfOrNull(exerciseName)?.let { return Remark.NewBest(it) }
        todays.lastOrNull()?.let { return Remark.LoggedToday(it.name) }

        templates.firstOrNull { today.dayOfWeek in it.scheduledDays }?.let { return Remark.PlannedToday(it.name) }

        val dates = worked.map { it.date }.toSet()
        val since = StreakCalculator.daysSinceLast(dates, today)?.toInt() ?: 0
        if (since >= WELCOME_BACK_AFTER_DAYS) return Remark.WelcomeBack(since)

        val weekStart = today.with(DayOfWeek.MONDAY)
        val thisWeek = worked.count { !it.date.isBefore(weekStart) }
        val lastWeek = worked.count { it.date >= weekStart.minusWeeks(1) && it.date < weekStart }
        if (thisWeek > lastWeek && thisWeek > 0) return Remark.AheadOfLastWeek(thisWeek, lastWeek)

        val weeks = StreakCalculator.weeksInARow(dates, today)
        if (weeks >= 2) return Remark.WeekStreak(weeks)

        return null
    }
}
