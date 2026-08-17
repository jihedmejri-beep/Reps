package com.reps.app.data.exercise.db

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * The bundled, read-only exercise catalogue.
 *
 * Opened with `createFromAsset("reps_exercises.db")`: the file is built from the
 * REPS dataset by `tools/build_exercise_db.py` and shipped in the APK, so the
 * whole catalogue - names in three languages, muscles, equipment, instructions
 * and the muscle SVGs - is available with no network at all. Only the
 * demonstration photos are fetched remotely.
 *
 * There is no migration path and no version bump story on purpose: the app never
 * writes to this database, so a content refresh is a new asset file plus a
 * version increment here, not a schema migration.
 */
@Database(
    entities = [
        ExerciseEntity::class,
        ExerciseTranslationEntity::class,
        ExerciseMuscleEntity::class,
        ExerciseEquipmentEntity::class,
        ExerciseAliasEntity::class,
        ExerciseImageEntity::class,
        MuscleSvgAssetEntity::class,
        BodyDiagramEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class ExerciseCatalogDatabase : RoomDatabase() {
    abstract fun exerciseDao(): ExerciseDao

    companion object {
        const val ASSET_NAME = "reps_exercises.db"
        const val DB_NAME = "reps_exercises.db"
    }
}
