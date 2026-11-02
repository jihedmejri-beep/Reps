package com.reps.app.feature.workouts.session

import androidx.lifecycle.SavedStateHandle
import com.reps.app.data.notifications.SessionAlerts
import com.reps.app.domain.model.Exercise
import com.reps.app.domain.model.ExerciseDetail
import com.reps.app.domain.model.ExerciseSet
import com.reps.app.domain.model.MuscleGroup
import com.reps.app.domain.model.SessionDraft
import com.reps.app.domain.model.Streak
import com.reps.app.domain.model.User
import com.reps.app.domain.model.Workout
import com.reps.app.domain.model.WorkoutExercise
import com.reps.app.domain.model.WorkoutSession
import com.reps.app.domain.progress.AchievementId
import com.reps.app.domain.progress.PersonalRecords
import com.reps.app.domain.repository.ExerciseRepository
import com.reps.app.domain.repository.UserRepository
import com.reps.app.domain.repository.WorkoutRepository
import com.reps.app.navigation.NavArgs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class WorkoutSessionViewModelTest {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private var now = 1_800_000_000_000L

    @After
    fun tearDown() = scope.cancel()

    private val template = Workout(
        id = "push",
        name = "Push",
        exercises = listOf(
            WorkoutExercise("bench", 0, List(2) { ExerciseSet(id = "s$it", weightKg = 60.0, reps = 8) }, restSeconds = 120),
            WorkoutExercise("dips", 1, List(1) { ExerciseSet(id = "d$it", weightKg = 0.0, reps = 10) }),
        ),
    )

    /** In-memory repository; only what a session touches is meaningful. */
    private class FakeWorkouts(private val templates: List<Workout>) : WorkoutRepository {
        val draft = MutableStateFlow<SessionDraft?>(null)
        val saved = mutableListOf<WorkoutSession>()
        var draftWrites = 0

        override fun observeTemplates(): Flow<List<Workout>> = flowOf(templates)
        override fun observeTemplate(workoutId: String): Flow<Workout?> = flowOf(templates.firstOrNull { it.id == workoutId })
        override fun observeWorkoutFor(date: LocalDate): Flow<Workout?> = flowOf(null)
        override suspend fun saveTemplate(workout: Workout) = Unit
        override suspend fun deleteTemplate(workoutId: String) = Unit
        override fun observeSessions(): Flow<List<WorkoutSession>> = flowOf(saved.toList())
        override fun observeSession(sessionId: String): Flow<WorkoutSession?> = flowOf(saved.firstOrNull { it.id == sessionId })
        override suspend fun saveSession(session: WorkoutSession): List<AchievementId> {
            saved += session
            draft.value = null
            return if (saved.size == 1) listOf(AchievementId.FIRST_WORKOUT) else emptyList()
        }
        override suspend fun bestSetsFor(exerciseIds: Collection<String>): Map<String, ExerciseSet> =
            exerciseIds.mapNotNull { id ->
                PersonalRecords.bestOf(saved.flatMap { s -> s.exercises.filter { it.exerciseId == id }.flatMap { it.sets } })?.let { id to it }
            }.toMap()
        override fun observeStreak(today: LocalDate): Flow<Streak> = flowOf(Streak())
        override fun observeUnlocks(): Flow<Map<AchievementId, Long>> = flowOf(emptyMap())
        override fun observeDraft(): Flow<SessionDraft?> = draft
        override suspend fun loadDraft(): SessionDraft? = draft.value
        override suspend fun saveDraft(draft: SessionDraft) {
            draftWrites++
            this.draft.value = draft
        }
        override suspend fun clearDraft() {
            draft.value = null
        }
    }

    private object Catalogue : ExerciseRepository {
        private val all = listOf(
            Exercise(id = "bench", name = "Bench Press", muscleGroup = MuscleGroup.CHEST),
            Exercise(id = "dips", name = "Dips", muscleGroup = MuscleGroup.ARMS),
        )
        override fun observeExercises(muscleGroup: MuscleGroup?, query: String): Flow<List<Exercise>> = flowOf(all)
        override fun observeExercise(exerciseId: String): Flow<Exercise?> = flowOf(all.firstOrNull { it.id == exerciseId })
        override fun observeExercisesByMuscle(muscleName: String): Flow<List<Exercise>> = flowOf(emptyList())
        override fun observeExerciseDetail(exerciseId: String): Flow<ExerciseDetail?> = flowOf(null)
        override suspend fun getByIds(ids: List<String>): List<Exercise> = all.filter { it.id in ids }
    }

    private object NoUser : UserRepository {
        override fun observeUser(): Flow<User?> = flowOf(null)
        override suspend fun updateUser(user: User) = Unit
    }

    private class RecordingAlerts : SessionAlerts {
        val prs = mutableListOf<String>()
        var backgroundRests = 0
        var cancels = 0
        override fun personalRecord(exerciseName: String) {
            prs += exerciseName
        }
        override fun restInBackground(endsAtMs: Long, runningTitle: String, runningBody: String, doneTitle: String, doneBody: String) {
            backgroundRests++
        }
        override fun cancelRest() {
            cancels++
        }
    }

    private val workouts = FakeWorkouts(listOf(template, template.copy(id = "legs", name = "Legs")))
    private val alerts = RecordingAlerts()

    private fun viewModel(workoutId: String = "push") = WorkoutSessionViewModel(
        SavedStateHandle(mapOf(NavArgs.WORKOUT_ID to workoutId)),
        workouts,
        Catalogue,
        NoUser,
        alerts,
        scope,
        clock = { now },
    ).also { vm -> scope.launch { vm.uiState.collect {} } }

    private val WorkoutSessionViewModel.state get() = uiState.value

    @Test
    fun startsFromTheTemplate_withNothingCompleted_andPersistsADraft() {
        val vm = viewModel()
        assertEquals(3, vm.state.totalSetCount)
        assertEquals(0, vm.state.completedSetCount)
        assertNull(vm.state.rest)
        assertNotNull(workouts.draft.value)
        assertEquals(now, vm.state.startedAtMs)
    }

    @Test
    fun completingASet_startsThatExercisesRest_withItsDuration() {
        val vm = viewModel()
        vm.toggleSetComplete("bench", 0)
        val rest = vm.state.rest!!
        assertEquals("bench", rest.state.exerciseId)
        assertEquals(120_000, rest.state.remainingMs(now))
        assertEquals(2, rest.nextSetNumber)
        assertEquals("Bench Press", rest.nextExerciseName)
    }

    @Test
    fun editingWithoutCompleting_neverStartsRest() {
        val vm = viewModel()
        vm.updateWeightKg("bench", 0, 70.0)
        vm.updateReps("bench", 0, 6)
        assertNull(vm.state.rest)
    }

    @Test
    fun undoingTheSet_cancelsItsRest() {
        val vm = viewModel()
        vm.toggleSetComplete("bench", 0)
        vm.toggleSetComplete("bench", 0)
        assertNull(vm.state.rest)
    }

    @Test
    fun lastSetOfTheExercise_restPointsAtTheNextExercise() {
        val vm = viewModel()
        vm.toggleSetComplete("bench", 0)
        vm.toggleSetComplete("bench", 1)
        assertEquals("Dips", vm.state.rest!!.nextExerciseName)
        assertEquals("dips", vm.state.expandedExerciseId)
    }

    @Test
    fun finalSetOfTheWorkout_startsNoRest() {
        val vm = viewModel()
        vm.toggleSetComplete("bench", 0)
        vm.toggleSetComplete("bench", 1)
        vm.toggleSetComplete("dips", 0)
        assertNull(vm.state.rest)
    }

    @Test
    fun restSurvivesTimeAndPause() {
        val vm = viewModel()
        vm.toggleSetComplete("bench", 0)
        now += 30_000
        vm.pauseRest()
        now += 600_000
        assertEquals(90_000, vm.state.rest!!.state.remainingMs(now))
        vm.resumeRest()
        vm.adjustRest(15)
        assertEquals(105_000, vm.state.rest!!.state.remainingMs(now))
        vm.skipRest()
        assertTrue(vm.state.rest!!.state.isFinished(now))
        vm.dismissRest()
        assertNull(vm.state.rest)
    }

    @Test
    fun backgroundHandoff_onlyWhileARestIsRunning() {
        val vm = viewModel()
        val text = RestNotificationText("a", "b", "c", "d")
        vm.onBackgrounded(text)
        assertEquals(0, alerts.backgroundRests)
        vm.toggleSetComplete("bench", 0)
        vm.onBackgrounded(text)
        assertEquals(1, alerts.backgroundRests)
    }

    @Test
    fun aNewViewModel_resumesTheSavedDraftExactly() {
        val first = viewModel()
        first.toggleSetComplete("bench", 0)
        first.updateWeightKg("bench", 1, 62.5)
        val startedAt = first.state.startedAtMs

        now += 5 * 60_000 // process death and a return five minutes later
        val second = viewModel()
        assertEquals(1, second.state.completedSetCount)
        assertEquals(62.5, second.state.exercises.first().sets[1].weightKg, 1e-9)
        assertEquals(startedAt, second.state.startedAtMs)
        assertNotNull(second.state.rest)
    }

    @Test
    fun aDraftFromAnotherWorkout_isOfferedNotOverwritten() {
        viewModel("legs").toggleSetComplete("bench", 0)
        val vm = viewModel("push")
        assertEquals("Legs", vm.state.conflictingDraftName)
        assertEquals("legs", workouts.draft.value!!.templateId)

        vm.resolveConflict(resumeOther = false)
        assertNull(vm.state.conflictingDraftName)
        assertEquals("push", workouts.draft.value!!.templateId)
        assertEquals(0, vm.state.completedSetCount)
    }

    @Test
    fun finish_savesOnce_withMeasuredDuration_andClearsTheDraft() = runBlocking {
        val vm = viewModel()
        vm.toggleSetComplete("bench", 0)
        now += 42 * 60_000

        val result = vm.finish()
        assertNotNull(result)
        assertNull(vm.finish()) // a second tap writes nothing
        assertEquals(1, workouts.saved.size)

        val session = workouts.saved.single()
        assertEquals(42, session.durationMin)
        assertTrue(session.durationMeasured)
        assertEquals(42, result!!.durationMin)
        assertEquals(listOf(AchievementId.FIRST_WORKOUT), result.unlocked)
        assertNull(workouts.draft.value)
    }

    @Test
    fun firstEverSession_setsNoRecord_andABetterSecondOneDoes() = runBlocking {
        val first = viewModel()
        first.toggleSetComplete("bench", 0)
        first.finish()
        assertTrue(workouts.saved.single().prsHit.isEmpty())
        assertTrue(alerts.prs.isEmpty())

        val second = viewModel()
        second.updateWeightKg("bench", 0, 65.0)
        second.toggleSetComplete("bench", 0)
        second.finish()
        assertEquals(listOf("bench"), workouts.saved.last().prsHit)
        assertEquals(listOf("Bench Press"), alerts.prs)
    }

    @Test
    fun discard_dropsTheDraft() {
        val vm = viewModel()
        vm.toggleSetComplete("bench", 0)
        var done = false
        vm.discard { done = true }
        assertTrue(done)
        assertNull(workouts.draft.value)
        assertFalse(workouts.saved.isNotEmpty())
    }
}
