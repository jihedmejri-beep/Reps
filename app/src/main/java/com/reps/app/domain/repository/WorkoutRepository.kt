package com.reps.app.domain.repository

import com.reps.app.domain.model.Workout
import com.reps.app.domain.model.WorkoutSession
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

interface WorkoutRepository {
    fun observeTemplates(): Flow<List<Workout>>
    fun observeTemplate(workoutId: String): Flow<Workout?>

    /** The template scheduled for [date], or null on a rest day. */
    fun observeWorkoutFor(date: LocalDate): Flow<Workout?>

    suspend fun saveTemplate(workout: Workout)
    suspend fun deleteTemplate(workoutId: String)

    fun observeSessions(): Flow<List<WorkoutSession>>
    suspend fun saveSession(session: WorkoutSession)

    /**
     * Best volume previously recorded for [exerciseId], or null if never
     * trained. PR detection compares against this.
     */
    suspend fun bestVolumeFor(exerciseId: String): Double?
}
