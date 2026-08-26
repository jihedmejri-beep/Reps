package com.reps.app.data.user.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Schema for the writable user database (`reps_user.db`).
 *
 * Everything here is per-account: rows carry a `uid` matching the signed-in
 * account id, so several accounts can coexist on one device without seeing
 * each other's workouts, weight, meals or chats.
 *
 * Dates are ISO-8601 local dates ("2026-08-25") and enums their `.name`, both
 * sortable strings; epoch millis appear only where wall-clock ordering is
 * wanted (created-at stamps, chat history). Weights stay kilograms everywhere,
 * mirroring the domain models.
 */

/** A sign-up credential set. Emails are normalised to lowercase before storage. */
@Entity(
    tableName = "user_accounts",
    indices = [Index(value = ["email"], unique = true)],
)
data class UserAccountEntity(
    @PrimaryKey val id: String,
    val email: String,
    /** PBKDF2-HMAC-SHA256 hex digest; never a plaintext password. */
    val passwordHash: String,
    /** Random per-account hex salt fed to the KDF. */
    val passwordSalt: String,
    val createdAtMs: Long,
)

/** The profile side of an account: body metrics, preferences and streak state. */
@Entity(tableName = "user_profiles")
data class UserProfileEntity(
    @PrimaryKey val uid: String,
    val name: String,
    val email: String,
    /** Sex.name, or null when not disclosed. */
    val sexName: String?,
    val heightCm: Double?,
    val age: Int?,
    /** Goal.name. */
    val goalName: String,
    /** UnitSystem.name. */
    val unitsName: String,
    /** AppLanguage tag ("en" / "ar" / "fr"); mirrored into DataStore. */
    val languageTag: String,
    val streakCount: Int,
    /** ISO local date of the most recent logged session, or null. */
    val lastWorkoutDateIso: String?,
)

@Entity(
    tableName = "workout_templates",
    indices = [Index("uid")],
)
data class WorkoutTemplateEntity(
    @PrimaryKey val id: String,
    val uid: String,
    val name: String,
    /** Comma-joined DayOfWeek names, empty string when nothing is scheduled. */
    val scheduledDaysCsv: String,
    /** Difficulty.name. */
    val difficultyName: String,
    val estimatedMinutes: Int,
    val createdAtMs: Long,
)

@Entity(
    tableName = "workout_template_exercises",
    indices = [Index("templateId"), Index("exerciseId")],
    foreignKeys = [
        ForeignKey(
            entity = WorkoutTemplateEntity::class,
            parentColumns = ["id"],
            childColumns = ["templateId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class WorkoutTemplateExerciseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val templateId: String,
    /** Catalogue id from assets/reps_exercises.db. */
    val exerciseId: String,
    /** Zero-based playback order inside the template. */
    val position: Int,
)

@Entity(
    tableName = "workout_template_sets",
    indices = [Index("templateExerciseId")],
    foreignKeys = [
        ForeignKey(
            entity = WorkoutTemplateExerciseEntity::class,
            parentColumns = ["id"],
            childColumns = ["templateExerciseId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class WorkoutTemplateSetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val templateExerciseId: Long,
    val position: Int,
    val weightKg: Double,
    val reps: Int,
)

@Entity(
    tableName = "workout_sessions",
    indices = [Index("uid"), Index("dateIso")],
)
data class WorkoutSessionEntity(
    @PrimaryKey val id: String,
    val uid: String,
    /** The template it was run from, if any; not a foreign key so deleting a template never rewrites history. */
    val templateId: String,
    val name: String,
    val dateIso: String,
    val durationMin: Int,
    /** Comma-joined catalogue ids that produced a PR this session. */
    val prsHitCsv: String,
)

@Entity(
    tableName = "workout_session_exercises",
    indices = [Index("sessionId"), Index("exerciseId")],
    foreignKeys = [
        ForeignKey(
            entity = WorkoutSessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class WorkoutSessionExerciseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: String,
    val exerciseId: String,
    val position: Int,
)

@Entity(
    tableName = "workout_session_sets",
    indices = [Index("sessionExerciseId")],
    foreignKeys = [
        ForeignKey(
            entity = WorkoutSessionExerciseEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionExerciseId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class WorkoutSessionSetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionExerciseId: Long,
    val position: Int,
    val weightKg: Double,
    val reps: Int,
    val completed: Boolean,
)

/** One weigh-in per account per day; the (uid, dateIso) pair is unique. */
@Entity(
    tableName = "weight_entries",
    indices = [Index(value = ["uid", "dateIso"], unique = true)],
)
data class WeightEntryEntity(
    @PrimaryKey val id: String,
    val uid: String,
    val dateIso: String,
    val weightKg: Double,
)

@Entity(
    tableName = "meals",
    indices = [Index("uid"), Index("dateIso")],
)
data class MealEntity(
    @PrimaryKey val id: String,
    val uid: String,
    val name: String,
    val dateIso: String,
    val createdAtMs: Long,
)

@Entity(
    tableName = "meal_items",
    indices = [Index("mealId")],
    foreignKeys = [
        ForeignKey(
            entity = MealEntity::class,
            parentColumns = ["id"],
            childColumns = ["mealId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class MealItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val mealId: String,
    val name: String,
    val grams: Double,
    val caloriesPer100g: Double,
    val proteinPer100g: Double,
    val carbsPer100g: Double,
    val fatPer100g: Double,
)

@Entity(
    tableName = "assistant_conversations",
    indices = [Index("uid"), Index("updatedAtMs")],
)
data class AssistantConversationEntity(
    @PrimaryKey val id: String,
    val uid: String,
    val title: String,
    val updatedAtMs: Long,
)

@Entity(
    tableName = "assistant_messages",
    indices = [Index("conversationId")],
    foreignKeys = [
        ForeignKey(
            entity = AssistantConversationEntity::class,
            parentColumns = ["id"],
            childColumns = ["conversationId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class AssistantMessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val conversationId: String,
    val fromUser: Boolean,
    val text: String,
    /** Order of the line inside its conversation. */
    val sortIndex: Long,
)
