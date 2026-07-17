package com.reps.app.domain.model

import java.time.DayOfWeek
import java.time.LocalDate

/**
 * A saved, reusable workout template, e.g. "Push Day".
 *
 * [scheduledDays] is what drives both the Home "today's workout" card and the
 * reminder notifications, so a template with no days is valid but silent.
 */
data class Workout(
    val id: String = "",
    val name: String = "",
    val exercises: List<WorkoutExercise> = emptyList(),
    val scheduledDays: Set<DayOfWeek> = emptySet(),
    val difficulty: Difficulty = Difficulty.INTERMEDIATE,
    /** Estimated minutes, shown on the Home card before the session starts. */
    val estimatedMinutes: Int = 0,
)

/** A performed workout, as opposed to the [Workout] template it came from. */
data class WorkoutSession(
    val id: String = "",
    val workoutId: String = "",
    val name: String = "",
    val date: LocalDate = LocalDate.now(),
    val exercises: List<WorkoutExercise> = emptyList(),
    val durationMin: Int = 0,
    /** Exercise ids that produced a personal record in this session. */
    val prsHit: List<String> = emptyList(),
)
