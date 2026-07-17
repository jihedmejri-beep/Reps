package com.reps.app.data.fake

import com.reps.app.domain.model.Difficulty
import com.reps.app.domain.model.Exercise
import com.reps.app.domain.model.ExerciseSet
import com.reps.app.domain.model.FoodItem
import com.reps.app.domain.model.Goal
import com.reps.app.domain.model.Meal
import com.reps.app.domain.model.MuscleGroup
import com.reps.app.domain.model.Sex
import com.reps.app.domain.model.UnitSystem
import com.reps.app.domain.model.User
import com.reps.app.domain.model.WeightEntry
import com.reps.app.domain.model.Workout
import com.reps.app.domain.model.WorkoutExercise
import java.time.DayOfWeek
import java.time.LocalDate
import kotlin.math.sin
import kotlin.random.Random

/**
 * Stand-in content for building the UI before Firestore is wired.
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

    val exercises: List<Exercise> = listOf(
        Exercise(
            id = "bench-press",
            name = "Barbell Bench Press",
            muscleGroup = MuscleGroup.CHEST,
            equipment = "Barbell",
            description = "Lie flat, grip just wider than shoulder width, lower the bar to " +
                "mid-chest under control, then drive it back up without bouncing.",
            mistakes = listOf(
                "Flaring the elbows to 90 degrees, which strains the shoulder joint.",
                "Bouncing the bar off the chest instead of pausing under control.",
                "Lifting the hips off the bench to force the last rep.",
            ),
            difficulty = Difficulty.INTERMEDIATE,
        ),
        Exercise(
            id = "incline-db-press",
            name = "Incline Dumbbell Press",
            muscleGroup = MuscleGroup.CHEST,
            equipment = "Dumbbells",
            description = "Set the bench to roughly 30 degrees and press the dumbbells from " +
                "the outer chest to directly over the collarbone.",
            mistakes = listOf(
                "Setting the incline too steep, turning it into a shoulder press.",
                "Clashing the dumbbells together at the top and losing tension.",
            ),
            difficulty = Difficulty.BEGINNER,
        ),
        Exercise(
            id = "cable-fly",
            name = "Cable Fly",
            muscleGroup = MuscleGroup.CHEST,
            equipment = "Cable machine",
            description = "With a slight bend in the elbows, bring both handles together in " +
                "front of the sternum and squeeze before returning slowly.",
            mistakes = listOf(
                "Bending the elbows through the rep, which turns it into a press.",
                "Going so heavy the range of motion collapses.",
            ),
            difficulty = Difficulty.BEGINNER,
        ),
        Exercise(
            id = "overhead-press",
            name = "Overhead Press",
            muscleGroup = MuscleGroup.SHOULDERS,
            equipment = "Barbell",
            description = "Press the bar from the front rack to lockout overhead, moving the " +
                "head back through as the bar passes the face.",
            mistakes = listOf(
                "Leaning back excessively and turning it into an incline press.",
                "Stopping short of full lockout.",
            ),
            difficulty = Difficulty.INTERMEDIATE,
        ),
        Exercise(
            id = "lateral-raise",
            name = "Lateral Raise",
            muscleGroup = MuscleGroup.SHOULDERS,
            equipment = "Dumbbells",
            description = "Raise the dumbbells out to the sides to shoulder height, leading " +
                "with the elbows.",
            mistakes = listOf(
                "Swinging the weight up with momentum from the hips.",
                "Raising above shoulder height, which shifts the work to the traps.",
            ),
            difficulty = Difficulty.BEGINNER,
        ),
        Exercise(
            id = "triceps-pushdown",
            name = "Triceps Pushdown",
            muscleGroup = MuscleGroup.ARMS,
            equipment = "Cable machine",
            description = "Keep the elbows pinned to the ribs and extend the forearms down " +
                "until the arms are straight.",
            mistakes = listOf(
                "Letting the elbows drift forward and away from the body.",
                "Leaning over the bar to push with bodyweight.",
            ),
            difficulty = Difficulty.BEGINNER,
        ),
        Exercise(
            id = "deadlift",
            name = "Conventional Deadlift",
            muscleGroup = MuscleGroup.BACK,
            equipment = "Barbell",
            description = "Hinge at the hips, keep the bar against the legs and stand up by " +
                "driving the floor away.",
            mistakes = listOf(
                "Letting the hips shoot up first, turning it into a stiff-leg pull.",
                "Rounding the lower back under load.",
                "Jerking the bar off the floor instead of taking the slack out.",
            ),
            difficulty = Difficulty.ADVANCED,
        ),
        Exercise(
            id = "pull-up",
            name = "Pull-Up",
            muscleGroup = MuscleGroup.BACK,
            equipment = "Bodyweight",
            description = "Hang at full stretch and pull until the chin clears the bar, " +
                "driving the elbows down and back.",
            mistakes = listOf(
                "Kipping when the goal is a strict rep.",
                "Cutting the bottom range and never fully extending.",
            ),
            difficulty = Difficulty.INTERMEDIATE,
        ),
        Exercise(
            id = "barbell-row",
            name = "Barbell Row",
            muscleGroup = MuscleGroup.BACK,
            equipment = "Barbell",
            description = "Hinge to roughly 45 degrees and row the bar to the lower ribs.",
            mistakes = listOf(
                "Standing up progressively through the set.",
                "Using so much momentum the lats stop working.",
            ),
            difficulty = Difficulty.INTERMEDIATE,
        ),
        Exercise(
            id = "back-squat",
            name = "Barbell Back Squat",
            muscleGroup = MuscleGroup.LEGS,
            equipment = "Barbell",
            description = "Brace, break at the hips and knees together, and descend until the " +
                "hip crease passes the knee.",
            mistakes = listOf(
                "Knees caving inward out of the hole.",
                "Rising hips first, which dumps the load onto the lower back.",
                "Cutting depth as the weight climbs.",
            ),
            difficulty = Difficulty.INTERMEDIATE,
        ),
        Exercise(
            id = "romanian-deadlift",
            name = "Romanian Deadlift",
            muscleGroup = MuscleGroup.LEGS,
            equipment = "Barbell",
            description = "Push the hips back with a near-straight leg until the hamstrings " +
                "stretch, then drive the hips forward.",
            mistakes = listOf(
                "Squatting the weight down instead of hinging.",
                "Chasing depth past the point the back rounds.",
            ),
            difficulty = Difficulty.INTERMEDIATE,
        ),
        Exercise(
            id = "leg-press",
            name = "Leg Press",
            muscleGroup = MuscleGroup.LEGS,
            equipment = "Machine",
            description = "Lower the sled until the knees reach roughly 90 degrees, then press " +
                "without locking out hard.",
            mistakes = listOf(
                "Letting the lower back round off the pad at the bottom.",
                "Snapping the knees into full lockout.",
            ),
            difficulty = Difficulty.BEGINNER,
        ),
        Exercise(
            id = "hip-thrust",
            name = "Barbell Hip Thrust",
            muscleGroup = MuscleGroup.GLUTES,
            equipment = "Barbell",
            description = "With the shoulder blades on a bench, drive the hips to full " +
                "extension and squeeze at the top.",
            mistakes = listOf(
                "Hyperextending the lower back instead of finishing with the glutes.",
                "Letting the chin drift up and the ribs flare.",
            ),
            difficulty = Difficulty.BEGINNER,
        ),
        Exercise(
            id = "plank",
            name = "Plank",
            muscleGroup = MuscleGroup.ABS,
            equipment = "Bodyweight",
            description = "Hold a straight line from heel to head on the forearms, ribs down " +
                "and glutes braced.",
            mistakes = listOf(
                "Letting the hips sag toward the floor.",
                "Piking the hips up to make the hold easier.",
            ),
            difficulty = Difficulty.BEGINNER,
        ),
        Exercise(
            id = "hanging-leg-raise",
            name = "Hanging Leg Raise",
            muscleGroup = MuscleGroup.ABS,
            equipment = "Bodyweight",
            description = "From a dead hang, raise the legs to hip height or above without " +
                "swinging.",
            mistakes = listOf(
                "Swinging between reps and using momentum.",
                "Only moving the hips while the lower back stays arched.",
            ),
            difficulty = Difficulty.ADVANCED,
        ),
        Exercise(
            id = "rowing-machine",
            name = "Rowing Machine",
            muscleGroup = MuscleGroup.CARDIO,
            equipment = "Rower",
            description = "Drive with the legs, then swing the torso back, then pull the " +
                "handle to the ribs. Reverse that order on the recovery.",
            mistakes = listOf(
                "Pulling with the arms before the legs have driven.",
                "Rounding the back at the catch.",
            ),
            difficulty = Difficulty.BEGINNER,
        ),
    )

    val pushDay = Workout(
        id = "push-day",
        name = "Push Day",
        difficulty = Difficulty.INTERMEDIATE,
        estimatedMinutes = 52,
        scheduledDays = setOf(DayOfWeek.MONDAY, DayOfWeek.THURSDAY),
        exercises = listOf(
            WorkoutExercise("bench-press", 0, defaultSets(60.0, 8)),
            WorkoutExercise("incline-db-press", 1, defaultSets(24.0, 10)),
            WorkoutExercise("cable-fly", 2, defaultSets(15.0, 12)),
            WorkoutExercise("overhead-press", 3, defaultSets(40.0, 8)),
            WorkoutExercise("lateral-raise", 4, defaultSets(10.0, 14)),
            WorkoutExercise("triceps-pushdown", 5, defaultSets(30.0, 12)),
        ),
    )

    val pullDay = Workout(
        id = "pull-day",
        name = "Pull Day",
        difficulty = Difficulty.INTERMEDIATE,
        estimatedMinutes = 48,
        scheduledDays = setOf(DayOfWeek.TUESDAY, DayOfWeek.FRIDAY),
        exercises = listOf(
            WorkoutExercise("deadlift", 0, defaultSets(100.0, 5)),
            WorkoutExercise("pull-up", 1, defaultSets(0.0, 8)),
            WorkoutExercise("barbell-row", 2, defaultSets(60.0, 8)),
        ),
    )

    val legDay = Workout(
        id = "leg-day",
        name = "Leg Day",
        difficulty = Difficulty.ADVANCED,
        estimatedMinutes = 58,
        scheduledDays = setOf(DayOfWeek.WEDNESDAY, DayOfWeek.SATURDAY),
        exercises = listOf(
            WorkoutExercise("back-squat", 0, defaultSets(90.0, 6)),
            WorkoutExercise("romanian-deadlift", 1, defaultSets(70.0, 8)),
            WorkoutExercise("leg-press", 2, defaultSets(140.0, 10)),
            WorkoutExercise("hip-thrust", 3, defaultSets(80.0, 10)),
        ),
    )

    val workouts = listOf(pushDay, pullDay, legDay)

    private fun defaultSets(weightKg: Double, reps: Int) =
        List(3) { ExerciseSet(id = "set-$it", weightKg = weightKg, reps = reps) }

    /**
     * ~90 days of weight trending gently down from 80 kg, with the small
     * day-to-day noise real scales produce, so the chart has something
     * believable to render. Seeded, so it is stable across launches.
     */
    val weightEntries: List<WeightEntry> = run {
        val random = Random(seed = 42)
        val today = LocalDate.now()
        (0..89).map { daysAgo ->
            val date = today.minusDays(daysAgo.toLong())
            val trend = 78.4 + (daysAgo * 0.018)
            val wobble = sin(daysAgo * 0.7) * 0.25 + random.nextDouble(-0.2, 0.2)
            WeightEntry(
                id = "w-$daysAgo",
                date = date,
                weightKg = ((trend + wobble) * 10).toInt() / 10.0,
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

    /** Rotates daily on the Home tab. */
    val motivationQuotes: List<String> = listOf(
        "Every rep counts.\nEvery set matters.",
        "Show up.\nGet stronger.",
        "Small wins.\nBig results.",
        "Discipline beats\nmotivation.",
        "The work you skip\nis the progress you lose.",
        "Trust the process.\nCount the reps.",
        "Make today count.\nNothing else does.",
    )
}
