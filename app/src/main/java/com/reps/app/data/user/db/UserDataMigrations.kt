package com.reps.app.data.user.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Hand-written migrations for [UserDataDatabase]. Each statement must leave the
 * table exactly as Room's exported schema describes it (`app/schemas/.../N.json`),
 * because Room validates the result on open. `tools/validate_user_db_migration.py`
 * replays every migration over the previous schema and compares them.
 */
object UserDataMigrations {

    /** The SQL is public so the validator and tests replay exactly what ships. */
    val MIGRATION_1_2_SQL: List<String> = listOf(
        "ALTER TABLE `user_profiles` ADD COLUMN `birthDateIso` TEXT",
        "ALTER TABLE `user_profiles` ADD COLUMN `activityLevelName` TEXT",
        "ALTER TABLE `workout_template_exercises` ADD COLUMN `restSeconds` INTEGER NOT NULL DEFAULT 90",
        // 0 marks a pre-v2 session: its durationMin was estimated, not measured.
        "ALTER TABLE `workout_sessions` ADD COLUMN `startedAtMs` INTEGER NOT NULL DEFAULT 0",
        "ALTER TABLE `workout_sessions` ADD COLUMN `endedAtMs` INTEGER NOT NULL DEFAULT 0",
        "CREATE TABLE IF NOT EXISTS `active_session_drafts` (`uid` TEXT NOT NULL, `templateId` TEXT NOT NULL, " +
            "`startedAtMs` INTEGER NOT NULL, `updatedAtMs` INTEGER NOT NULL, `payloadJson` TEXT NOT NULL, " +
            "PRIMARY KEY(`uid`))",
        "CREATE TABLE IF NOT EXISTS `achievement_unlocks` (`uid` TEXT NOT NULL, `achievementId` TEXT NOT NULL, " +
            "`unlockedAtMs` INTEGER NOT NULL, `sessionId` TEXT, PRIMARY KEY(`uid`, `achievementId`))",
    )

    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            MIGRATION_1_2_SQL.forEach(db::execSQL)
        }
    }

    val ALL: Array<Migration> = arrayOf(MIGRATION_1_2)
}
