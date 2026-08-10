package com.reps.app.data.fake

import com.reps.app.domain.model.Difficulty
import com.reps.app.domain.model.ExerciseSet
import com.reps.app.domain.model.FoodItem
import com.reps.app.domain.model.Goal
import com.reps.app.domain.model.Meal
import com.reps.app.domain.model.Sex
import com.reps.app.domain.model.UnitSystem
import com.reps.app.domain.model.User
import com.reps.app.domain.model.WeightEntry
import com.reps.app.domain.model.Workout
import com.reps.app.domain.model.WorkoutExercise
import com.reps.app.domain.model.WorkoutSession
import java.time.DayOfWeek
import java.time.LocalDate
import kotlin.math.sin
import kotlin.random.Random

/**
 * Stand-in *user* content for building the UI before Firestore is wired: the
 * profile, the workout templates, the weight history, the meals.
 *
 * The exercise catalogue is no longer part of this - it is real, and lives in
 * `assets/reps_exercises.db`. What remains here references it by real id (see
 * [SampleData.CatalogIds]) rather than inventing exercises of its own.
 *
 * The figures mirror the reference mockups (Alex Rivera, a 12-day streak, Push
 * Day at 78.4 kg) so the screens can be compared against them directly.
 * Everything here is replaced by real data and this file deleted.
 */
object SampleData {

    val user = User(
        uid = "sample-uid",
        name = "Alex Rivera",
        email = "alex@reps.app",
        sex = Sex.MALE,
        heightCm = 180.0,
        age = 28,
        goal = Goal.CUT,
        units = UnitSystem.METRIC,
        streakCount = 12,
        lastWorkoutDate = LocalDate.now().minusDays(1),
    )

    /**
     * Catalogue ids for the demo templates below.
     *
     * These are real `exercises.id` values from `assets/reps_exercises.db`, not
     * invented slugs: the templates have to resolve against the same catalogue
     * the rest of the app reads, or Home shows a workout whose exercises do not
     * exist. The comment on each is its English name and wger id, so a lookup
     * is a grep away.
     */
    private object CatalogIds {
        const val BENCH_PRESS = "3717d144-7815-4a97-9a56-956fb889c996"        // Bench Press (73)
        const val INCLINE_DB_PRESS = "57e17672-52b9-43cf-8d0d-4b3f06a0c0d0"   // Incline Bench Press - Dumbbell (537)
        const val CABLE_FLY = "07c5b9f4-2be5-4a3d-b6d2-16235da1ae3a"          // Fly With Cable (237)
        const val SHOULDER_PRESS = "8b0a0371-c0a9-42a7-aab7-68d520542fb2"     // Shoulder Press, Barbell (566)
        const val LATERAL_RAISE = "63375f5b-2d81-471c-bea4-fc3d207e96cb"      // Lateral Raises (348)
        const val TRICEPS_PUSHDOWN = "6ebb138e-bb0a-402e-84e5-68fe0896e897"   // Triceps Pushdown (1185)
        const val DEADLIFT = "ee8e8db4-2d82-49e1-ab7f-891e9a354934"           // Deadlifts (184)
        const val PULL_UP = "8e420408-0682-4ab6-89f5-2681e54c7ce0"            // Pull-ups (475)
        const val BARBELL_ROW = "4af6dbd9-8991-484b-9810-68f117c21edf"        // Bent Over Rowing (83)
        const val BACK_SQUAT = "5d0e0a8b-1940-4034-b4ae-b965859f1ff0"         // Barbell Full Squat (1801)
        const val ROMANIAN_DEADLIFT = "2e7ffff9-e603-4b28-98c8-31d1a6ce8cd9"  // Romanian Deadlift (507)
        const val LEG_PRESS = "66a42396-c207-44da-bc75-758a89d32404"          // Leg Press (371)
        const val HIP_THRUST = "19a289c0-33af-4055-bb34-3570c2975d3d"         // Hip Thrust (294)
    }

    val pushDay = Workout(
        id = "push-day",
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

    val pullDay = Workout(
        id = "pull-day",
        name = "Pull Day",
        difficulty = Difficulty.INTERMEDIATE,
        estimatedMinutes = 48,
        scheduledDays = setOf(DayOfWeek.TUESDAY, DayOfWeek.FRIDAY),
        exercises = listOf(
            WorkoutExercise(CatalogIds.DEADLIFT, 0, defaultSets(100.0, 5)),
            WorkoutExercise(CatalogIds.PULL_UP, 1, defaultSets(0.0, 8)),
            WorkoutExercise(CatalogIds.BARBELL_ROW, 2, defaultSets(60.0, 8)),
        ),
    )

    val legDay = Workout(
        id = "leg-day",
        name = "Leg Day",
        difficulty = Difficulty.ADVANCED,
        estimatedMinutes = 58,
        scheduledDays = setOf(DayOfWeek.WEDNESDAY, DayOfWeek.SATURDAY),
        exercises = listOf(
            WorkoutExercise(CatalogIds.BACK_SQUAT, 0, defaultSets(90.0, 6)),
            WorkoutExercise(CatalogIds.ROMANIAN_DEADLIFT, 1, defaultSets(70.0, 8)),
            WorkoutExercise(CatalogIds.LEG_PRESS, 2, defaultSets(140.0, 10)),
            WorkoutExercise(CatalogIds.HIP_THRUST, 3, defaultSets(80.0, 10)),
        ),
    )

    val workouts = listOf(pushDay, pullDay, legDay)

    private fun defaultSets(weightKg: Double, reps: Int) =
        List(3) { ExerciseSet(id = "set-$it", weightKg = weightKg, reps = reps) }

    /**
     * ~90 days of weight ending at 78.4 kg today, cutting at a realistic
     * ~0.6 kg/week. Seeded, so it is stable across launches.
     *
     * The day-to-day wobble is deliberately kept well under the weekly trend:
     * noise larger than the signal would make the Home widget report a gain
     * during a cut, which is a misleading thing to show a user.
     */
    val weightEntries: List<WeightEntry> = run {
        val random = Random(seed = 42)
        val today = LocalDate.now()
        val kgLostPerDay = 0.6 / 7.0
        (0..89).map { daysAgo ->
            val date = today.minusDays(daysAgo.toLong())
            val trend = 78.4 + (daysAgo * kgLostPerDay)
            val wobble = sin(daysAgo * 0.7) * 0.12 + random.nextDouble(-0.06, 0.06)
            WeightEntry(
                id = "w-$daysAgo",
                date = date,
                weightKg = Math.round((trend + wobble) * 10) / 10.0,
            )
        }.sortedBy { it.date }
    }

    val meals: List<Meal> = listOf(
        Meal(
            id = "meal-1",
            name = "Breakfast",
            date = LocalDate.now(),
            foodItems = listOf(
                FoodItem("f1", "Rolled oats", 80.0, 389.0, 16.9, 66.3, 6.9),
                FoodItem("f2", "Whole milk", 200.0, 61.0, 3.2, 4.8, 3.3),
                FoodItem("f3", "Banana", 120.0, 89.0, 1.1, 22.8, 0.3),
            ),
        ),
        Meal(
            id = "meal-2",
            name = "Lunch",
            date = LocalDate.now(),
            foodItems = listOf(
                FoodItem("f4", "Chicken breast", 200.0, 165.0, 31.0, 0.0, 3.6),
                FoodItem("f5", "White rice", 180.0, 130.0, 2.7, 28.2, 0.3),
                FoodItem("f6", "Broccoli", 100.0, 34.0, 2.8, 6.6, 0.4),
            ),
        ),
    )

    /**
     * ~9 weeks of completed sessions against [pushDay]/[pullDay]/[legDay]'s own
     * schedule, so Progress has real history to chart on first launch instead of
     * an empty state. Weight trends up toward today by a modest 12%, and
     * [WorkoutSession.prsHit] is computed the same way
     * [com.reps.app.domain.repository.WorkoutRepository.bestVolumeFor] would -
     * whichever exercise's best completed-set volume this session beat.
     */
    val workoutSessions: List<WorkoutSession> = run {
        val random = Random(seed = 7)
        val today = LocalDate.now()
        val templates = listOf(pushDay, pullDay, legDay)
        val bestVolumeSoFar = mutableMapOf<String, Double>()
        val sessions = mutableListOf<WorkoutSession>()

        for (daysAgo in 63 downTo 1) {
            val date = today.minusDays(daysAgo.toLong())
            val template = templates.firstOrNull { date.dayOfWeek in it.scheduledDays } ?: continue
            val strengthGain = 1f + (1f - daysAgo / 63f) * 0.12f

            val prsThisSession = mutableListOf<String>()
            val exercises = template.exercises.map { workoutExercise ->
                val sets = workoutExercise.sets.map { set ->
                    set.copy(
                        weightKg = Math.round(set.weightKg * strengthGain * 2) / 2.0,
                        completed = true,
                    )
                }
                val bestThisSession = sets.maxOfOrNull { it.volume } ?: 0.0
                val previousBest = bestVolumeSoFar[workoutExercise.exerciseId]
                if (previousBest != null && bestThisSession > previousBest) {
                    prsThisSession += workoutExercise.exerciseId
                }
                bestVolumeSoFar[workoutExercise.exerciseId] = maxOf(previousBest ?: 0.0, bestThisSession)
                WorkoutExercise(workoutExercise.exerciseId, workoutExercise.position, sets)
            }

            sessions += WorkoutSession(
                id = "sess-$daysAgo",
                workoutId = template.id,
                name = template.name,
                date = date,
                exercises = exercises,
                durationMin = template.estimatedMinutes + random.nextInt(-6, 7),
                prsHit = prsThisSession,
            )
        }
        sessions.sortedBy { it.date }
    }

    /** Rotates daily, stable within a day: [motivationQuoteForToday]. */
    val motivationQuotes: List<String> = listOf(
        "Every rep counts.\nEvery set matters.",
        "Show up.\nGet stronger.",
        "Small wins.\nBig results.",
        "Discipline beats\nmotivation.",
        "The work you skip\nis the progress you lose.",
        "Trust the process.\nCount the reps.",
        "Make today count.\nNothing else does.",
    )

    fun motivationQuoteForToday(today: LocalDate = LocalDate.now()): String {
        val index = (today.toEpochDay() % motivationQuotes.size).toInt()
        return motivationQuotes[index]
    }
}
