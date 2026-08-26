package com.reps.app.data.user.db

import com.reps.app.domain.model.AppLanguage
import com.reps.app.domain.model.AssistantConversation
import com.reps.app.domain.model.AssistantMessage
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

/**
 * Entity <-> domain mapping. All parsing is defensive: a corrupt enum name or
 * date falls back to the model's default rather than throwing, because one bad
 * row must never take down a whole screen.
 */

private fun LocalDate?.toIso(): String? = this?.toString()

private fun String.toLocalDateOrNull(): LocalDate? =
    runCatching { LocalDate.parse(this) }.getOrNull()

// --- profile ---

fun UserProfileEntity.toDomain(): User = User(
    uid = uid,
    name = name,
    email = email,
    sex = sexName?.let { runCatching { Sex.valueOf(it) }.getOrNull() },
    heightCm = heightCm,
    age = age,
    goal = runCatching { Goal.valueOf(goalName) }.getOrDefault(Goal.MAINTAIN),
    units = runCatching { UnitSystem.valueOf(unitsName) }.getOrDefault(UnitSystem.METRIC),
    language = AppLanguage.fromTag(languageTag),
    streakCount = streakCount,
    lastWorkoutDate = lastWorkoutDateIso?.toLocalDateOrNull(),
)

fun User.toProfileEntity(): UserProfileEntity = UserProfileEntity(
    uid = uid,
    name = name,
    email = email,
    sexName = sex?.name,
    heightCm = heightCm,
    age = age,
    goalName = goal.name,
    unitsName = units.name,
    languageTag = language.tag,
    streakCount = streakCount,
    lastWorkoutDateIso = lastWorkoutDate.toIso(),
)

// --- workouts: read ---

fun daysFromCsv(csv: String): Set<DayOfWeek> = csv
    .split(',')
    .mapNotNull { token ->
        token.trim().takeIf { it.isNotEmpty() }
            ?.let { name -> DayOfWeek.entries.firstOrNull { it.name == name } }
    }
    .toSet()

fun daysToCsv(days: Set<DayOfWeek>): String = days.joinToString(",") { it.name }

fun TemplateWithExercises.toDomain(): Workout {
    val ordered = exercises.sortedBy { it.exercise.position }
    return Workout(
        id = template.id,
        name = template.name,
        scheduledDays = daysFromCsv(template.scheduledDaysCsv),
        difficulty = runCatching { Difficulty.valueOf(template.difficultyName) }
            .getOrDefault(Difficulty.INTERMEDIATE),
        estimatedMinutes = template.estimatedMinutes,
        exercises = ordered.map { withSets ->
            val sets = withSets.sets.sortedBy { it.position }
            WorkoutExercise(
                exerciseId = withSets.exercise.exerciseId,
                position = withSets.exercise.position,
                sets = sets.mapIndexed { index, s ->
                    ExerciseSet(
                        id = s.rowKey(index),
                        weightKg = s.weightKg,
                        reps = s.reps,
                        completed = false,
                    )
                },
            )
        },
    )
}

/** Stable string key for a stored set row, matching the app's `set-N` convention. */
private fun WorkoutTemplateSetEntity.rowKey(index: Int): String = if (id != 0L) "s$id" else "set-$index"

private fun WorkoutSessionSetEntity.rowKey(index: Int): String = if (id != 0L) "s$id" else "set-$index"

fun SessionWithExercises.toDomain(): WorkoutSession {
    val ordered = exercises.sortedBy { it.exercise.position }
    return WorkoutSession(
        id = session.id,
        workoutId = session.templateId,
        name = session.name,
        date = session.dateIso.toLocalDateOrNull() ?: LocalDate.now(),
        durationMin = session.durationMin,
        prsHit = session.prsHitCsv.split(',').filter { it.isNotBlank() },
        exercises = ordered.map { withSets ->
            val sets = withSets.sets.sortedBy { it.position }
            WorkoutExercise(
                exerciseId = withSets.exercise.exerciseId,
                position = withSets.exercise.position,
                sets = sets.mapIndexed { index, s ->
                    ExerciseSet(
                        id = s.rowKey(index),
                        weightKg = s.weightKg,
                        reps = s.reps,
                        completed = s.completed,
                    )
                },
            )
        },
    )
}

// --- meals & weight: read ---

fun MealWithItems.toDomain(): Meal = Meal(
    id = meal.id,
    name = meal.name,
    date = meal.dateIso.toLocalDateOrNull() ?: LocalDate.now(),
    foodItems = items.map { row ->
        FoodItem(
            id = "f${row.id}",
            name = row.name,
            grams = row.grams,
            caloriesPer100g = row.caloriesPer100g,
            proteinPer100g = row.proteinPer100g,
            carbsPer100g = row.carbsPer100g,
            fatPer100g = row.fatPer100g,
        )
    },
)

fun WeightEntryEntity.toDomain(): WeightEntry = WeightEntry(
    id = id,
    date = dateIso.toLocalDateOrNull() ?: LocalDate.now(),
    weightKg = weightKg,
)

// --- assistant history: read ---

fun ConversationWithMessages.toDomain(): AssistantConversation = AssistantConversation(
    id = conversation.id,
    title = conversation.title,
    updatedAt = conversation.updatedAtMs,
    messages = messages
        .sortedBy { it.sortIndex }
        .map { AssistantMessage(id = it.id, fromUser = it.fromUser, text = it.text) },
)
