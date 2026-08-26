package com.reps.app.data.fake

import com.reps.app.domain.model.Difficulty
import com.reps.app.domain.model.ExerciseSet
import com.reps.app.domain.model.Workout
import com.reps.app.domain.model.WorkoutExercise
import java.time.DayOfWeek

/**
 * Preview content for Compose `@Preview` composables only - never runtime
 * data. All user data is real now: accounts, workouts, weight, meals and chat
 * history persist in the local Room database (`data.user`).
 *
 * The exercise ids below are real `exercises.id` values from
 * `assets/reps_exercises.db`, so previews resolve against the same catalogue
 * the app reads.
 */
object SampleData {

    private object CatalogIds {
        const val BENCH_PRESS = "3717d144-7815-4a97-9a56-956fb889c996"        // Bench Press (73)
        const val INCLINE_DB_PRESS = "57e17672-52b9-43cf-8d0d-4b3f06a0c0d0"   // Incline Bench Press - Dumbbell (537)
        const val CABLE_FLY = "07c5b9f4-2be5-4a3d-b6d2-16235da1ae3a"          // Fly With Cable (237)
        const val SHOULDER_PRESS = "8b0a0371-c0a9-42a7-aab7-68d520542fb2"     // Shoulder Press, Barbell (566)
        const val LATERAL_RAISE = "63375f5b-2d81-471c-bea4-fc3d207e96cb"      // Lateral Raises (348)
        const val TRICEPS_PUSHDOWN = "6ebb138e-bb0a-402e-84e5-68fe0896e897"   // Triceps Pushdown (1185)
    }

    val pushDay = Workout(
        id = "preview-push-day",
        name = "Push Day",
        difficulty = Difficulty.INTERMEDIATE,
        estimatedMinutes = 52,
        scheduledDays = setOf(DayOfWeek.MONDAY, DayOfWeek.THURSDAY),
        exercises = listOf(
            WorkoutExercise(CatalogIds.BENCH_PRESS, 0, defaultSets(60.0, 8)),
            WorkoutExercise(CatalogIds.INCLINE_DB_PRESS, 1, defaultSets(24.0, 10)),
            WorkoutExercise(CatalogIds.CABLE_FLY, 2, defaultSets(15.0, 12)),
            WorkoutExercise(CatalogIds.SHOULDER_PRESS, 3, defaultSets(40.0, 8)),
            WorkoutExercise(CatalogIds.LATERAL_RAISE, 4, defaultSets(10.0, 14)),
            WorkoutExercise(CatalogIds.TRICEPS_PUSHDOWN, 5, defaultSets(30.0, 12)),
        ),
    )

    private fun defaultSets(weightKg: Double, reps: Int) =
        List(3) { ExerciseSet(id = "set-$it", weightKg = weightKg, reps = reps) }
}
