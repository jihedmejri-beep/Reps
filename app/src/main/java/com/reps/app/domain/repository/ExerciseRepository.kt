package com.reps.app.domain.repository

import com.reps.app.domain.model.Exercise
import com.reps.app.domain.model.MuscleGroup
import kotlinx.coroutines.flow.Flow

/**
 * Kept deliberately free of Firestore types: the exercise content source is
 * still an open decision (self-curated vs. imported), and only the
 * implementation behind this interface should have to change.
 */
interface ExerciseRepository {
    fun observeExercises(muscleGroup: MuscleGroup? = null): Flow<List<Exercise>>
    fun observeExercise(exerciseId: String): Flow<Exercise?>
    suspend fun getByIds(ids: List<String>): List<Exercise>
}
