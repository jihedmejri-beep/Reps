package com.reps.app.domain.model

/**
 * An exercise as it sits inside a workout: the user's chosen position and the
 * sets logged against it. [position] is explicit because the brief requires the
 * session to play back in exactly the order the user built.
 */
data class WorkoutExercise(
    val exerciseId: String = "",
    val position: Int = 0,
    val sets: List<ExerciseSet> = emptyList(),
)
