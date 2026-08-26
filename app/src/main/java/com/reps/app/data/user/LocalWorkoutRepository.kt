package com.reps.app.data.user

import androidx.room.withTransaction
import com.reps.app.core.util.StreakCalculator
import com.reps.app.data.auth.UserSession
import com.reps.app.data.user.db.UserDataDatabase
import com.reps.app.data.user.db.WorkoutSessionEntity
import com.reps.app.data.user.db.WorkoutSessionExerciseEntity
import com.reps.app.data.user.db.WorkoutSessionSetEntity
import com.reps.app.data.user.db.WorkoutTemplateEntity
import com.reps.app.data.user.db.WorkoutTemplateExerciseEntity
import com.reps.app.data.user.db.WorkoutTemplateSetEntity
import com.reps.app.data.user.db.daysToCsv
import com.reps.app.data.user.db.toDomain
import com.reps.app.domain.model.Workout
import com.reps.app.domain.model.WorkoutSession
import com.reps.app.domain.repository.WorkoutRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import java.time.DayOfWeek
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Templates and sessions, persisted as proper trees in Room.
 *
 * Writes replace a whole tree inside one transaction (delete the old rows,
 * insert the new ones), which keeps exercises and sets from drifting out of
 * sync with their parent - the same upsert-by-id behaviour the ViewModels were
 * built against. Saving a session also recomputes the account's streak, since
 * that is the only event that can change it.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class LocalWorkoutRepository @Inject constructor(
    private val session: UserSession,
    private val database: UserDataDatabase,
) : WorkoutRepository {

    private val dao get() = database.workoutDao()
    private val profileDao get() = database.userProfileDao()

    override fun observeTemplates(): Flow<List<Workout>> =
        session.uidFlow.flatMapLatest { uid ->
            if (uid == null) {
                flowOf(emptyList<Workout>())
            } else {
                dao.observeTemplates(uid).map { rows -> rows.map { it.toDomain() } }
            }
        }

    override fun observeTemplate(workoutId: String): Flow<Workout?> =
        session.uidFlow.flatMapLatest { uid ->
            if (uid == null) flowOf(null) else dao.observeTemplate(uid, workoutId).map { it?.toDomain() }
        }

    override fun observeWorkoutFor(date: LocalDate): Flow<Workout?> =
        observeTemplates().map { list -> list.firstOrNull { date.dayOfWeek in it.scheduledDays } }

    override suspend fun saveTemplate(workout: Workout) {
        val uid = session.currentUid ?: return
        val template = WorkoutTemplateEntity(
            id = workout.id,
            uid = uid,
            name = workout.name,
            scheduledDaysCsv = daysToCsv(workout.scheduledDays),
            difficultyName = workout.difficulty.name,
            estimatedMinutes = workout.estimatedMinutes,
            createdAtMs = System.currentTimeMillis(),
        )
        // Child rows carry a provisional parent key equal to the exercise's
        // index; real auto-generated ids are swapped in after insertion.
        val exerciseRows = mutableListOf<WorkoutTemplateExerciseEntity>()
        val setRows = mutableListOf<Pair<Int, WorkoutTemplateSetEntity>>()
        workout.exercises.sortedBy { it.position }.forEachIndexed { index, we ->
            exerciseRows += WorkoutTemplateExerciseEntity(
                templateId = workout.id,
                exerciseId = we.exerciseId,
                position = we.position,
            )
            we.sets.forEachIndexed { setIndex, s ->
                setRows += index to WorkoutTemplateSetEntity(
                    templateExerciseId = 0,
                    position = setIndex,
                    weightKg = s.weightKg,
                    reps = s.reps,
                )
            }
        }

        database.withTransaction {
            dao.deleteTemplateExercises(workout.id)
            dao.deleteTemplateRow(workout.id)
            dao.insertTemplate(template)
            val parentIds = dao.insertTemplateExercises(exerciseRows)
            if (setRows.isNotEmpty()) {
                dao.insertTemplateSets(setRows.map { (parentIndex, row) ->
                    row.copy(templateExerciseId = parentIds[parentIndex])
                })
            }
        }
    }

    override suspend fun deleteTemplate(workoutId: String) {
        val uid = session.currentUid ?: return
        database.withTransaction {
            dao.deleteTemplateExercisesScoped(workoutId, uid)
            dao.deleteTemplateRowScoped(workoutId, uid)
        }
    }

    override fun observeSessions(): Flow<List<WorkoutSession>> =
        session.uidFlow.flatMapLatest { uid ->
            if (uid == null) {
                flowOf(emptyList<WorkoutSession>())
            } else {
                dao.observeSessions(uid).map { rows -> rows.map { it.toDomain() } }
            }
        }

    override suspend fun saveSession(sessionResult: WorkoutSession) {
        val uid = session.currentUid ?: return
        val entity = WorkoutSessionEntity(
            id = sessionResult.id,
            uid = uid,
            templateId = sessionResult.workoutId,
            name = sessionResult.name,
            dateIso = sessionResult.date.toString(),
            durationMin = sessionResult.durationMin,
            prsHitCsv = sessionResult.prsHit.joinToString(","),
        )
        val exerciseRows = mutableListOf<WorkoutSessionExerciseEntity>()
        val setRows = mutableListOf<Pair<Int, WorkoutSessionSetEntity>>()
        sessionResult.exercises.sortedBy { it.position }.forEachIndexed { index, we ->
            exerciseRows += WorkoutSessionExerciseEntity(
                sessionId = sessionResult.id,
                exerciseId = we.exerciseId,
                position = we.position,
            )
            we.sets.forEachIndexed { setIndex, s ->
                setRows += index to WorkoutSessionSetEntity(
                    sessionExerciseId = 0,
                    position = setIndex,
                    weightKg = s.weightKg,
                    reps = s.reps,
                    completed = s.completed,
                )
            }
        }

        database.withTransaction {
            dao.insertSession(entity)
            val parentIds = dao.insertSessionExercises(exerciseRows)
            if (setRows.isNotEmpty()) {
                dao.insertSessionSets(setRows.map { (parentIndex, row) ->
                    row.copy(sessionExerciseId = parentIds[parentIndex])
                })
            }
            // Inside the transaction, so streak dates already include this save.
            recomputeStreakLocked(uid)
        }
    }

    override suspend fun bestVolumeFor(exerciseId: String): Double? {
        val uid = session.currentUid ?: return null
        return dao.bestVolume(exerciseId, uid)
    }

    /**
     * Streak recompute: walk the account's full session history against every
     * template's schedule and store the result on the profile.
     */
    private suspend fun recomputeStreakLocked(uid: String) {
        val profile = profileDao.get(uid) ?: return
        val dates = dao.sessionDates(uid)
            .mapNotNull { iso -> runCatching { LocalDate.parse(iso) }.getOrNull() }
            .toSet()
        val scheduled = dao.allScheduledDaysCsv(uid)
            .flatMap { csv -> csv.split(',').filter { it.isNotBlank() } }
            .mapNotNull { name -> DayOfWeek.entries.firstOrNull { it.name == name } }
            .toSet()
        val streak = StreakCalculator.compute(dates, scheduled)
        if (streak.count != profile.streakCount ||
            streak.lastWorkoutDate?.toString() != profile.lastWorkoutDateIso
        ) {
            profileDao.upsert(
                profile.copy(
                    streakCount = streak.count,
                    lastWorkoutDateIso = streak.lastWorkoutDate?.toString(),
                ),
            )
        }
    }
}
