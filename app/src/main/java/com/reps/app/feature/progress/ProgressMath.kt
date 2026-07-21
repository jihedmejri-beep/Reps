package com.reps.app.feature.progress

import com.reps.app.domain.model.Exercise
import com.reps.app.domain.model.ExerciseSet
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
 */

data class MonthlyStats(val workouts: Int, val minutes: Int, val volumeKg: Double)

fun monthlyStats(sessions: List<WorkoutSession>, month: YearMonth): MonthlyStats {
    val inMonth = sessions.filter { YearMonth.from(it.date) == month }
    return MonthlyStats(
        workouts = inMonth.size,
        minutes = inMonth.sumOf { it.durationMin },
        volumeKg = inMonth.flatMap { it.exercises }.flatMap { it.sets }
            .filter { it.completed }.sumOf { it.volume },
    )
}

/** completedThisWeek to scheduledDaysThisWeek, e.g. 2 to 6. */
fun weeklyCompletion(
    sessions: List<WorkoutSession>,
    templates: List<Workout>,
    today: LocalDate = LocalDate.now(),
): Pair<Int, Int> {
    val weekStart = today.with(DayOfWeek.MONDAY)
    val weekEnd = weekStart.plusDays(6)
    val scheduledDaysThisWeek = templates.flatMap { it.scheduledDays }.distinct().size
    val completed = sessions.count { it.date in weekStart..weekEnd }
    return completed to scheduledDaysThisWeek
}

data class WeekBucket(val weekStart: LocalDate, val label: String, val count: Int)

fun weeklyFrequency(sessions: List<WorkoutSession>, weeks: Int = 8, today: LocalDate = LocalDate.now()): List<WeekBucket> {
    val currentWeekStart = today.with(DayOfWeek.MONDAY)
    return (weeks - 1 downTo 0).map { weeksAgo ->
        val start = currentWeekStart.minusWeeks(weeksAgo.toLong())
        val end = start.plusDays(6)
        WeekBucket(
            weekStart = start,
            label = "${start.monthValue}/${start.dayOfMonth}",
            count = sessions.count { it.date in start..end },
        )
    }
}

data class MuscleShare(val group: MuscleGroup, val pct: Int)

fun muscleDistribution(sessions: List<WorkoutSession>, exercisesById: Map<String, Exercise>): List<MuscleShare> {
    val volumeByGroup = mutableMapOf<MuscleGroup, Double>()
    sessions.forEach { session ->
        session.exercises.forEach { workoutExercise ->
            val group = exercisesById[workoutExercise.exerciseId]?.muscleGroup ?: return@forEach
            val volume = workoutExercise.sets.filter { it.completed }.sumOf { it.volume }
            volumeByGroup[group] = (volumeByGroup[group] ?: 0.0) + volume
        }
    }
    val total = volumeByGroup.values.sum()
    if (total <= 0.0) return emptyList()
    return volumeByGroup.entries
        .sortedByDescending { it.value }
        .take(6)
        .map { (group, volume) -> MuscleShare(group, ((volume / total) * 100).roundToInt()) }
}

data class PersonalRecord(
    val exerciseId: String,
    val weightKg: Double,
    val reps: Int,
    val date: LocalDate,
    /** null when this is the first completed set ever logged for the exercise - nothing to beat yet. */
    val deltaKg: Double?,
)

/** The current PR per exercise, most recently set first. */
fun personalRecords(sessions: List<WorkoutSession>): List<PersonalRecord> {
    val bestByExercise = mutableMapOf<String, ExerciseSet>()
    val currentPr = mutableMapOf<String, PersonalRecord>()
    sessions.sortedBy { it.date }.forEach { session ->
        session.exercises.forEach { workoutExercise ->
            val bestThisSession = workoutExercise.sets.filter { it.completed }.maxByOrNull { it.volume }
                ?: return@forEach
            val previousBest = bestByExercise[workoutExercise.exerciseId]
            if (previousBest == null || bestThisSession.volume > previousBest.volume) {
                currentPr[workoutExercise.exerciseId] = PersonalRecord(
                    exerciseId = workoutExercise.exerciseId,
                    weightKg = bestThisSession.weightKg,
                    reps = bestThisSession.reps,
                    date = session.date,
                    deltaKg = previousBest?.let { bestThisSession.weightKg - it.weightKg },
                )
                bestByExercise[workoutExercise.exerciseId] = bestThisSession
            }
        }
    }
    return currentPr.values.sortedByDescending { it.date }
}

data class StrengthPoint(val date: LocalDate, val weightKg: Double)

fun strengthSeries(sessions: List<WorkoutSession>, exerciseId: String): List<StrengthPoint> =
    sessions.sortedBy { it.date }.mapNotNull { session ->
        val workoutExercise = session.exercises.firstOrNull { it.exerciseId == exerciseId } ?: return@mapNotNull null
        val best = workoutExercise.sets.filter { it.completed }.maxByOrNull { it.weightKg } ?: return@mapNotNull null
        StrengthPoint(session.date, best.weightKg)
    }

enum class AchievementId { FIRST_WORKOUT, STREAK_7, TEN_WORKOUTS, FIRST_PR }

data class AchievementState(val id: AchievementId, val unlocked: Boolean, val progress: Float)

fun computeAchievements(sessionCount: Int, streakCount: Int, prCount: Int): List<AchievementState> = listOf(
    AchievementState(AchievementId.FIRST_WORKOUT, sessionCount >= 1, sessionCount.coerceAtMost(1) / 1f),
    AchievementState(AchievementId.STREAK_7, streakCount >= 7, (streakCount / 7f).coerceIn(0f, 1f)),
    AchievementState(AchievementId.TEN_WORKOUTS, sessionCount >= 10, (sessionCount / 10f).coerceIn(0f, 1f)),
    AchievementState(AchievementId.FIRST_PR, prCount >= 1, prCount.coerceAtMost(1) / 1f),
)
