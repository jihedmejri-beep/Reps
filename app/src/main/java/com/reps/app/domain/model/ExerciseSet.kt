package com.reps.app.domain.model

/**
 * One logged set. Weight is stored in kilograms regardless of the user's unit
 * preference; imperial is a display conversion.
 *
 * [volume] is what PR detection compares. Weight alone would ignore a set that
 * matched the load for more reps, which is still a better set.
 */
data class ExerciseSet(
    val id: String = "",
    val weightKg: Double = 0.0,
    val reps: Int = 0,
    val completed: Boolean = false,
) {
    val volume: Double get() = weightKg * reps
}
