package com.reps.app.data.user.db

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * The writable, per-device user database. Unlike the bundled exercise
 * catalogue this one starts empty and grows with the account: sign-ups,
 * profiles, workout templates and sessions, weigh-ins, meals, assistant chat
 * history.
 *
 * Version 1. Migrations go here from version 2 on; until then there is nothing
 * to migrate.
 */
@Database(
    entities = [
        UserAccountEntity::class,
        UserProfileEntity::class,
        WorkoutTemplateEntity::class,
        WorkoutTemplateExerciseEntity::class,
        WorkoutTemplateSetEntity::class,
        WorkoutSessionEntity::class,
        WorkoutSessionExerciseEntity::class,
        WorkoutSessionSetEntity::class,
        WeightEntryEntity::class,
        MealEntity::class,
        MealItemEntity::class,
        AssistantConversationEntity::class,
        AssistantMessageEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class UserDataDatabase : RoomDatabase() {

    abstract fun accountDao(): UserAccountDao
    abstract fun userProfileDao(): UserProfileDao
    abstract fun workoutDao(): WorkoutDao
    abstract fun weightEntryDao(): WeightEntryDao
    abstract fun mealDao(): MealDao
    abstract fun assistantConversationDao(): AssistantConversationDao

    companion object {
        const val DB_NAME = "reps_user.db"
    }
}
