package com.reps.app.di

import android.content.Context
import androidx.room.Room
import com.reps.app.data.exercise.MediaUrlResolver
import com.reps.app.data.exercise.db.ExerciseCatalogDatabase
import com.reps.app.data.exercise.db.ExerciseDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object ExerciseCatalogModule {

    /**
     * Opens the catalogue shipped in `assets/reps_exercises.db`.
     *
     * `createFromAsset` copies the file into the app's database directory on
     * first launch and validates it against the entity schema; the file is
     * generated from Room's own exported DDL by `tools/build_exercise_db.py`,
     * so that check passes by construction rather than by luck.
     *
     * Destructive fallback is the correct policy for a catalogue the app never
     * writes to: a version bump means new content, and the right response is to
     * throw the old copy away and re-extract, not to migrate it.
     */
    @Provides
    @Singleton
    fun provideExerciseCatalogDatabase(
        @ApplicationContext context: Context,
    ): ExerciseCatalogDatabase = Room
        .databaseBuilder(
            context,
            ExerciseCatalogDatabase::class.java,
            ExerciseCatalogDatabase.DB_NAME,
        )
        .createFromAsset(ExerciseCatalogDatabase.ASSET_NAME)
        .fallbackToDestructiveMigration(dropAllTables = true)
        .build()

    @Provides
    fun provideExerciseDao(database: ExerciseCatalogDatabase): ExerciseDao =
        database.exerciseDao()

    @Provides
    @Singleton
    fun provideMediaUrlResolver(): MediaUrlResolver = MediaUrlResolver()
}
