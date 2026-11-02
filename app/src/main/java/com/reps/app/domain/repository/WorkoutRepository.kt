package com.reps.app.domain.repository

import com.reps.app.domain.model.ExerciseSet
import com.reps.app.domain.model.SessionDraft
import com.reps.app.domain.model.Streak
import com.reps.app.domain.model.Workout
import com.reps.app.domain.model.WorkoutSession
import com.reps.app.domain.progress.AchievementId
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
    fun observeSession(sessionId: String): Flow<WorkoutSession?>

    /**
     * Persists a finished session, clears the in-progress draft and records any
     * achievements it earned, all in one transaction. Returns those achievements.
     */
    suspend fun saveSession(session: WorkoutSession): List<AchievementId>

    /**
     * Best completed set per exercise across every saved session, by weight then
     * reps (see [com.reps.app.domain.progress.PersonalRecords]). Exercises never
     * trained are absent. PR detection compares against this.
     */
    suspend fun bestSetsFor(exerciseIds: Collection<String>): Map<String, ExerciseSet>

    /** The streak as of [today], recomputed from history so it can never be stale. */
    fun observeStreak(today: LocalDate): Flow<Streak>

    /** Earned achievements with the epoch-ms time each was first unlocked. */
    fun observeUnlocks(): Flow<Map<AchievementId, Long>>

    fun observeDraft(): Flow<SessionDraft?>
    suspend fun loadDraft(): SessionDraft?
    suspend fun saveDraft(draft: SessionDraft)
    suspend fun clearDraft()
}
