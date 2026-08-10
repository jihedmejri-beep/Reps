package com.reps.app.domain.repository

import com.reps.app.domain.model.Exercise
import com.reps.app.domain.model.ExerciseDetail
import com.reps.app.domain.model.MuscleGroup
import kotlinx.coroutines.flow.Flow

/**
 * The exercise catalogue.
 *
 * Filtering and search are parameters rather than something the caller does to
 * the returned list: the catalogue is 828 exercises across three languages, and
 * the implementation answers these in SQLite so a search never materialises the
 * whole library. Everything here works with no network - only the image URLs on
 * the returned models point outside the device.
 */
interface ExerciseRepository {

    /**
     * @param muscleGroup restricts to one group, or null for all.
     * @param query free text matched against name and keywords, accent- and
     *   case-insensitively. Blank matches everything.
     */
    fun observeExercises(
        muscleGroup: MuscleGroup? = null,
        query: String = "",
    ): Flow<List<Exercise>>

    fun observeExercise(exerciseId: String): Flow<Exercise?>

    /** The full record - instructions, muscles, equipment, media, aliases. */
    fun observeExerciseDetail(exerciseId: String): Flow<ExerciseDetail?>

    suspend fun getByIds(ids: List<String>): List<Exercise>
}
