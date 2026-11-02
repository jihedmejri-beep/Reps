package com.reps.app.data.user

import androidx.room.withTransaction
import com.google.gson.Gson
import com.reps.app.core.util.StreakCalculator
import com.reps.app.data.auth.UserSession
import com.reps.app.data.user.db.AchievementUnlockEntity
import com.reps.app.data.user.db.ActiveSessionDraftEntity
import com.reps.app.data.user.db.UserDataDatabase
import com.reps.app.data.user.db.WorkoutSessionEntity
import com.reps.app.data.user.db.WorkoutSessionExerciseEntity
import com.reps.app.data.user.db.WorkoutSessionSetEntity
import com.reps.app.data.user.db.WorkoutTemplateEntity
import com.reps.app.data.user.db.WorkoutTemplateExerciseEntity
import com.reps.app.data.user.db.WorkoutTemplateSetEntity
import com.reps.app.data.user.db.daysToCsv
import com.reps.app.data.user.db.toDomain
import com.reps.app.domain.model.ExerciseSet
import com.reps.app.domain.model.SessionDraft
import com.reps.app.domain.model.Streak
import com.reps.app.domain.model.Workout
import com.reps.app.domain.model.WorkoutSession
import com.reps.app.domain.progress.AchievementId
import com.reps.app.domain.progress.Achievements
import com.reps.app.domain.progress.TrainingTotals
import com.reps.app.domain.repository.WorkoutRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Templates and sessions, persisted as proper trees in Room.
 *
 * Writes replace a whole tree inside one transaction (delete the old rows,
 * insert the new ones), which keeps exercises and sets from drifting out of
 * sync with their parent. Saving a session also clears the in-progress draft,
 * records new achievements and refreshes the stored streak, in that same
 * transaction, so a crash can never leave half of them applied.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class LocalWorkoutRepository @Inject constructor(
    private val session: UserSession,
    private val database: UserDataDatabase,
) : WorkoutRepository {

    private val dao get() = database.workoutDao()
    private val profileDao get() = database.userProfileDao()
    private val draftDao get() = database.activeSessionDraftDao()
    private val unlockDao get() = database.achievementUnlockDao()
    private val gson = Gson()

    private fun <T> perAccount(empty: T, block: (String) -> Flow<T>): Flow<T> =
        session.uidFlow.flatMapLatest { uid -> if (uid == null) flowOf(empty) else block(uid) }

    override fun observeTemplates(): Flow<List<Workout>> =
        perAccount(emptyList()) { uid -> dao.observeTemplates(uid).map { rows -> rows.map { it.toDomain() } } }

    override fun observeTemplate(workoutId: String): Flow<Workout?> =
        perAccount(null) { uid -> dao.observeTemplate(uid, workoutId).map { it?.toDomain() } }

    override fun observeWorkoutFor(date: LocalDate): Flow<Workout?> =
        observeTemplates().map { list -> list.firstOrNull { date.dayOfWeek in it.scheduledDays } }

    override suspend fun saveTemplate(workout: Workout) {
        val uid = session.currentUid ?: return
        val existingCreatedAt = dao.templateCreatedAt(workout.id, uid)
        val template = WorkoutTemplateEntity(
            id = workout.id,
            uid = uid,
            name = workout.name,
            scheduledDaysCsv = daysToCsv(workout.scheduledDays),
            difficultyName = workout.difficulty.name,
            estimatedMinutes = workout.estimatedMinutes,
            // Editing keeps the template's place in the list.
            createdAtMs = existingCreatedAt ?: System.currentTimeMillis(),
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
                restSeconds = we.restSeconds,
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
            // The schedule feeds the streak, so a schedule change refreshes it.
            refreshStoredStreak(uid)
        }
    }

    override suspend fun deleteTemplate(workoutId: String) {
        val uid = session.currentUid ?: return
        database.withTransaction {
            dao.deleteTemplateExercisesScoped(workoutId, uid)
            dao.deleteTemplateRowScoped(workoutId, uid)
            refreshStoredStreak(uid)
        }
    }

    override fun observeSessions(): Flow<List<WorkoutSession>> =
        perAccount(emptyList()) { uid -> dao.observeSessions(uid).map { rows -> rows.map { it.toDomain() } } }

    override fun observeSession(sessionId: String): Flow<WorkoutSession?> =
        perAccount(null) { uid -> dao.observeSession(uid, sessionId).map { it?.toDomain() } }

    override suspend fun saveSession(session: WorkoutSession): List<AchievementId> {
        val uid = this.session.currentUid ?: return emptyList()
        val entity = WorkoutSessionEntity(
            id = session.id,
            uid = uid,
            templateId = session.workoutId,
            name = session.name,
            dateIso = session.date.toString(),
            durationMin = session.durationMin,
            prsHitCsv = session.prsHit.joinToString(","),
            startedAtMs = session.startedAtMs,
            endedAtMs = session.endedAtMs,
        )
        val exerciseRows = mutableListOf<WorkoutSessionExerciseEntity>()
        val setRows = mutableListOf<Pair<Int, WorkoutSessionSetEntity>>()
        session.exercises.sortedBy { it.position }.forEachIndexed { index, we ->
            exerciseRows += WorkoutSessionExerciseEntity(
                sessionId = session.id,
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

        return database.withTransaction {
            dao.insertSession(entity)
            val parentIds = dao.insertSessionExercises(exerciseRows)
            if (setRows.isNotEmpty()) {
                dao.insertSessionSets(setRows.map { (parentIndex, row) ->
                    row.copy(sessionExerciseId = parentIds[parentIndex])
                })
            }
            draftDao.delete(uid)
            refreshStoredStreak(uid)
            recordAchievements(uid, session.id)
        }
    }

    override suspend fun bestSetsFor(exerciseIds: Collection<String>): Map<String, ExerciseSet> {
        val uid = session.currentUid ?: return emptyMap()
        return exerciseIds.distinct().mapNotNull { id ->
            dao.bestSet(id, uid)?.let { id to ExerciseSet(weightKg = it.weightKg, reps = it.reps, completed = true) }
        }.toMap()
    }

    override fun observeStreak(today: LocalDate): Flow<Streak> =
        combine(observeSessions(), observeTemplates()) { sessions, templates ->
            StreakCalculator.compute(
                sessionDates = sessions.filter { it.hasWork }.map { it.date }.toSet(),
                scheduledDays = templates.flatMap { it.scheduledDays }.toSet(),
                today = today,
            )
        }

    override fun observeUnlocks(): Flow<Map<AchievementId, Long>> =
        perAccount(emptyMap()) { uid ->
            unlockDao.observe(uid).map { rows ->
                rows.mapNotNull { row ->
                    AchievementId.entries.firstOrNull { it.name == row.achievementId }?.let { it to row.unlockedAtMs }
                }.toMap()
            }
        }

    override fun observeDraft(): Flow<SessionDraft?> =
        perAccount(null) { uid -> draftDao.observe(uid).map { it?.decode() } }

    override suspend fun loadDraft(): SessionDraft? {
        val uid = session.currentUid ?: return null
        return draftDao.get(uid)?.decode()
    }

    override suspend fun saveDraft(draft: SessionDraft) {
        val uid = session.currentUid ?: return
        draftDao.upsert(
            ActiveSessionDraftEntity(
                uid = uid,
                templateId = draft.templateId,
                startedAtMs = draft.startedAtMs,
                updatedAtMs = System.currentTimeMillis(),
                payloadJson = gson.toJson(draft),
            ),
        )
    }

    override suspend fun clearDraft() {
        val uid = session.currentUid ?: return
        draftDao.delete(uid)
    }

    /** A draft that no longer parses (e.g. written by a future build) is dropped, not crashed on. */
    private fun ActiveSessionDraftEntity.decode(): SessionDraft? =
        runCatching { gson.fromJson(payloadJson, SessionDraft::class.java) }.getOrNull()
            ?.takeIf { (it.exercises as List<*>?) != null }

    private suspend fun accountSessions(uid: String): List<WorkoutSession> = dao.sessions(uid).map { it.toDomain() }

    private suspend fun scheduledDays(uid: String) = dao.allScheduledDaysCsv(uid)
        .flatMap { csv -> com.reps.app.data.user.db.daysFromCsv(csv) }
        .toSet()

    /** Keeps the profile's cached streak in step for anything still reading it. */
    private suspend fun refreshStoredStreak(uid: String) {
        val profile = profileDao.get(uid) ?: return
        val streak = StreakCalculator.compute(
            accountSessions(uid).filter { it.hasWork }.map { it.date }.toSet(),
            scheduledDays(uid),
        )
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

    private suspend fun recordAchievements(uid: String, sessionId: String): List<AchievementId> {
        val already = unlockDao.unlockedIds(uid)
            .mapNotNull { name -> AchievementId.entries.firstOrNull { it.name == name } }
            .toSet()
        val earned = Achievements.newlyEarned(TrainingTotals.from(accountSessions(uid), scheduledDays(uid)), already)
        if (earned.isEmpty()) return emptyList()
        val now = System.currentTimeMillis()
        unlockDao.insertAll(earned.map { AchievementUnlockEntity(uid, it.name, now, sessionId) })
        return earned
    }
}
