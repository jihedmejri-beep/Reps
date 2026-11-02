package com.reps.app.feature.progress

import com.reps.app.domain.model.Exercise
import com.reps.app.domain.model.MuscleGroup
import com.reps.app.domain.model.Workout
import com.reps.app.domain.model.WorkoutSession
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.roundToInt

/**
 * Pure calculations over session/template history for the Progress screen.
 * Kept free of Compose so the numbers are unit-testable independent of UI.
 *
 * Every figure here is a direct sum or count over logged sessions. Sessions with
 * no completed set are excluded everywhere ("Finish anyway" on an empty session
 * is not a workout), and only *measured* durations are added up - sessions saved
 * before durations were measured carry an estimate and are counted separately.
 */

data class MonthlyStats(
    val workouts: Int,
    /** Sum of measured durations only. */
    val minutes: Int,
    /** Workouts this month whose duration was not measured and is left out of [minutes]. */
    val unmeasuredWorkouts: Int,
    val volumeKg: Double,
    val sets: Int,
)

fun monthlyStats(sessions: List<WorkoutSession>, month: YearMonth): MonthlyStats {
    val inMonth = sessions.filter { it.hasWork && YearMonth.from(it.date) == month }
    val measured = inMonth.filter { it.durationMeasured }
    return MonthlyStats(
        workouts = inMonth.size,
        minutes = measured.sumOf { it.durationMin },
        unmeasuredWorkouts = inMonth.size - measured.size,
        volumeKg = inMonth.flatMap { it.completedSets }.sumOf { it.volume },
        sets = inMonth.sumOf { it.completedSets.size },
    )
}

/** Sessions with work this Monday-Sunday week, against the number of distinct scheduled weekdays. */
fun weeklyCompletion(
    sessions: List<WorkoutSession>,
    templates: List<Workout>,
    today: LocalDate = LocalDate.now(),
): Pair<Int, Int> {
    val weekStart = today.with(DayOfWeek.MONDAY)
    val weekEnd = weekStart.plusDays(6)
    val scheduledDaysThisWeek = templates.flatMap { it.scheduledDays }.distinct().size
    val completed = sessions.count { it.hasWork && it.date in weekStart..weekEnd }
    return completed to scheduledDaysThisWeek
}

data class WeekBucket(val weekStart: LocalDate, val count: Int)

fun weeklyFrequency(sessions: List<WorkoutSession>, weeks: Int = 8, today: LocalDate = LocalDate.now()): List<WeekBucket> {
    val currentWeekStart = today.with(DayOfWeek.MONDAY)
    return (weeks - 1 downTo 0).map { weeksAgo ->
        val start = currentWeekStart.minusWeeks(weeksAgo.toLong())
        val end = start.plusDays(6)
        WeekBucket(weekStart = start, count = sessions.count { it.hasWork && it.date in start..end })
    }
}

data class MuscleShare(val group: MuscleGroup, val pct: Int, val sets: Int)

/**
 * Share of completed sets per muscle group, top six. Sets rather than volume:
 * volume (kg x reps) scores every bodyweight set as zero, which made push-ups and
 * pull-ups invisible here and over-weighted heavy leg work.
 */
fun muscleDistribution(sessions: List<WorkoutSession>, exercisesById: Map<String, Exercise>): List<MuscleShare> {
    val setsByGroup = mutableMapOf<MuscleGroup, Int>()
    sessions.forEach { session ->
        session.exercises.forEach { workoutExercise ->
            val group = exercisesById[workoutExercise.exerciseId]?.muscleGroup ?: return@forEach
            val done = workoutExercise.sets.count { it.completed }
            if (done > 0) setsByGroup[group] = (setsByGroup[group] ?: 0) + done
        }
    }
    val total = setsByGroup.values.sum()
    if (total <= 0) return emptyList()
    return setsByGroup.entries
        .sortedByDescending { it.value }
        .take(6)
        .map { (group, sets) -> MuscleShare(group, ((sets.toDouble() / total) * 100).roundToInt(), sets) }
}

/** One point per session: the heaviest completed set, or for bodyweight work the most reps. */
data class StrengthPoint(val date: LocalDate, val value: Double)

data class StrengthSeries(val points: List<StrengthPoint>, val byReps: Boolean)

fun strengthSeries(sessions: List<WorkoutSession>, exerciseId: String): StrengthSeries {
    val performed = sessions.sortedBy { it.date }.mapNotNull { session ->
        val sets = session.exercises.firstOrNull { it.exerciseId == exerciseId }?.sets
            ?.filter { it.completed && it.reps > 0 }
            ?.takeIf { it.isNotEmpty() }
            ?: return@mapNotNull null
        session.date to sets
    }
    val byReps = performed.all { (_, sets) -> sets.all { it.weightKg == 0.0 } }
    return StrengthSeries(
        points = performed.map { (date, sets) ->
            StrengthPoint(date, if (byReps) sets.maxOf { it.reps }.toDouble() else sets.maxOf { it.weightKg })
        },
        byReps = byReps,
    )
}
