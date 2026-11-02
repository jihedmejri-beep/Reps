package com.reps.app.data.user.db

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * The writable, per-device user database. Unlike the bundled exercise
 * catalogue this one starts empty and grows with the account: sign-ups,
 * profiles, workout templates and sessions, weigh-ins, meals, assistant chat
 * history.
 *
 * Version 2 adds measured session times, per-exercise rest, birth date and
 * activity level, the in-progress session draft and achievement unlocks; see
 * [UserDataMigrations]. Every version bump ships a real migration - this data is
 * the user's, there is no destructive fallback.
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
        ActiveSessionDraftEntity::class,
        AchievementUnlockEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
abstract class UserDataDatabase : RoomDatabase() {

    abstract fun accountDao(): UserAccountDao
    abstract fun userProfileDao(): UserProfileDao
    abstract fun workoutDao(): WorkoutDao
    abstract fun weightEntryDao(): WeightEntryDao
    abstract fun mealDao(): MealDao
    abstract fun assistantConversationDao(): AssistantConversationDao
    abstract fun activeSessionDraftDao(): ActiveSessionDraftDao
    abstract fun achievementUnlockDao(): AchievementUnlockDao

    companion object {
        const val DB_NAME = "reps_user.db"
    }
}
