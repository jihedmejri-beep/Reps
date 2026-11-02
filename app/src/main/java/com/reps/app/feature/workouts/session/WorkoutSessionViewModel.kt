package com.reps.app.feature.workouts.session

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.reps.app.data.notifications.SessionAlerts
import com.reps.app.domain.model.Exercise
import com.reps.app.domain.model.ExerciseSet
import com.reps.app.domain.model.RestState
import com.reps.app.domain.model.SessionDraft
import com.reps.app.domain.model.UnitSystem
import com.reps.app.domain.model.WorkoutExercise
import com.reps.app.domain.model.WorkoutSession
import com.reps.app.domain.progress.AchievementId
import com.reps.app.domain.progress.PersonalRecords
import com.reps.app.domain.repository.ExerciseRepository
import com.reps.app.domain.repository.UserRepository
import com.reps.app.domain.repository.WorkoutRepository
import com.reps.app.navigation.NavArgs
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.util.UUID
import javax.inject.Inject
import kotlin.math.roundToInt

data class SessionSetUi(
    val index: Int,
    val weightKg: Double,
    val reps: Int,
    val completed: Boolean,
)

data class SessionExerciseUi(
    val exercise: Exercise,
    val sets: List<SessionSetUi>,
    val restSeconds: Int,
    /** Best completed set ever logged for this exercise, shown as the number to beat. */
    val bestSet: ExerciseSet?,
) {
    val completedCount: Int get() = sets.count { it.completed }
    val isComplete: Boolean get() = sets.isNotEmpty() && sets.all { it.completed }
}

/** The rest between sets as the timer block shows it. */
data class RestUi(
    val state: RestState,
    /** The exercise whose set was just completed. */
    val exerciseName: String,
    /** What comes after the rest: an exercise and its 1-based set number out of its total. */
    val nextExerciseName: String,
    val nextSetNumber: Int,
    val nextTotalSets: Int,
)

/**
 * The first open set at or after the rested exercise, wrapping to earlier
 * exercises the user skipped. Null when nothing is left.
 */
private fun SessionDraft.nextUp(rest: RestState): Pair<WorkoutExercise, Int>? {
    val start = exercises.indexOfFirst { it.exerciseId == rest.exerciseId }.coerceAtLeast(0)
    val order = exercises.drop(start) + exercises.take(start)
    for (exercise in order) {
        val open = exercise.sets.indexOfFirst { !it.completed }
        if (open >= 0) return exercise to open
    }
    return null
}

data class WorkoutSessionUiState(
    val workoutName: String = "",
    val exercises: List<SessionExerciseUi> = emptyList(),
    val expandedExerciseId: String? = null,
    val units: UnitSystem = UnitSystem.METRIC,
    val completedSetCount: Int = 0,
    val totalSetCount: Int = 0,
    val volumeKg: Double = 0.0,
    val startedAtMs: Long = 0,
    val rest: RestUi? = null,
    /** Name of an unfinished session from another workout, awaiting resume/discard. */
    val conflictingDraftName: String? = null,
    val finishing: Boolean = false,
    val loading: Boolean = true,
)

data class SessionCompletionResult(
    val volumeKg: Double,
    val completedSetCount: Int,
    val durationMin: Int,
    val prExerciseNames: List<String>,
    val unlocked: List<AchievementId>,
)

/** Notification copy for a background rest, resolved by the screen in the app's language. */
data class RestNotificationText(
    val runningTitle: String,
    val runningBody: String,
    val doneTitle: String,
    val doneBody: String,
)

/**
 * A live workout. Everything the user logs is mirrored into a persisted
 * [SessionDraft] as it changes, so leaving the screen, process death or a
 * swiped-away task resumes exactly where they were; finishing or discarding
 * deletes it.
 *
 * The rest timer lives here, not in the composable: it is a [RestState] of
 * timestamps, so it survives recreation, keeps time in the background and never
 * drifts. It starts only when a set is ticked complete.
 */
@HiltViewModel
class WorkoutSessionViewModel(
    savedStateHandle: SavedStateHandle,
    private val workoutRepository: WorkoutRepository,
    private val exerciseRepository: ExerciseRepository,
    userRepository: UserRepository,
    private val alerts: SessionAlerts,
    /** Becomes `viewModelScope`; tests pass an unconfined one. */
    scope: CoroutineScope,
    /** Wall clock in epoch ms; tests control it to check durations and rests. */
    private val clock: () -> Long,
) : ViewModel(scope) {

    @Inject constructor(
        savedStateHandle: SavedStateHandle,
        workoutRepository: WorkoutRepository,
        exerciseRepository: ExerciseRepository,
        userRepository: UserRepository,
        alerts: SessionAlerts,
    ) : this(
        savedStateHandle,
        workoutRepository,
        exerciseRepository,
        userRepository,
        alerts,
        CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
        System::currentTimeMillis,
    )

    private val requestedWorkoutId: String = checkNotNull(savedStateHandle[NavArgs.WORKOUT_ID])

    /** The session itself; null until loaded (or while a conflicting draft is unresolved). */
    private val draft = MutableStateFlow<SessionDraft?>(null)
    private val exercisesById = MutableStateFlow<Map<String, Exercise>>(emptyMap())
    private val bestSets = MutableStateFlow<Map<String, ExerciseSet>>(emptyMap())
    private val expandedExerciseId = MutableStateFlow<String?>(null)
    private val conflict = MutableStateFlow<SessionDraft?>(null)
    private val finishing = MutableStateFlow(false)
    private val loaded = MutableStateFlow(false)

    private var persistJob: Job? = null

    val uiState: StateFlow<WorkoutSessionUiState> = combine(
        combine(draft, exercisesById, bestSets, ::Triple),
        expandedExerciseId,
        userRepository.observeUser(),
        combine(conflict, finishing, ::Pair),
        loaded,
    ) { (current, byId, best), expanded, user, (conflicting, isFinishing), isLoaded ->
        val exercisesUi = current?.exercises.orEmpty().mapNotNull { we ->
            val exercise = byId[we.exerciseId] ?: return@mapNotNull null
            SessionExerciseUi(
                exercise = exercise,
                sets = we.sets.mapIndexed { i, s -> SessionSetUi(i, s.weightKg, s.reps, s.completed) },
                restSeconds = we.restSeconds,
                bestSet = best[we.exerciseId],
            )
        }
        val allSets = current?.exercises.orEmpty().flatMap { it.sets }
        WorkoutSessionUiState(
            workoutName = current?.name.orEmpty(),
            exercises = exercisesUi,
            expandedExerciseId = expanded,
            units = user?.units ?: UnitSystem.METRIC,
            completedSetCount = allSets.count { it.completed },
            totalSetCount = allSets.size,
            volumeKg = allSets.filter { it.completed }.sumOf { it.volume },
            startedAtMs = current?.startedAtMs ?: 0,
            rest = current?.rest?.let { rest ->
                val next = current.nextUp(rest)
                RestUi(
                    state = rest,
                    exerciseName = byId[rest.exerciseId]?.name.orEmpty(),
                    nextExerciseName = next?.first?.let { byId[it.exerciseId]?.name }.orEmpty(),
                    nextSetNumber = (next?.second ?: 0) + 1,
                    nextTotalSets = next?.first?.sets?.size ?: 0,
                )
            },
            conflictingDraftName = conflicting?.name,
            finishing = isFinishing,
            loading = !isLoaded,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = WorkoutSessionUiState(),
    )

    init {
        viewModelScope.launch {
            val saved = workoutRepository.loadDraft()
            when {
                saved != null && saved.templateId == requestedWorkoutId -> adopt(saved)
                saved != null -> conflict.value = saved
                else -> startFresh()
            }
            loaded.value = true
        }
    }

    /** The user chose what to do with an unfinished session from another workout. */
    fun resolveConflict(resumeOther: Boolean) {
        val other = conflict.value ?: return
        conflict.value = null
        viewModelScope.launch {
            if (resumeOther) {
                adopt(other)
            } else {
                workoutRepository.clearDraft()
                startFresh()
            }
        }
    }

    private suspend fun startFresh() {
        val template = workoutRepository.observeTemplate(requestedWorkoutId).first() ?: return
        adopt(
            SessionDraft(
                templateId = template.id,
                name = template.name,
                startedAtMs = clock(),
                exercises = template.exercises.sortedBy { it.position }
                    .map { it.copy(sets = it.sets.map { s -> s.copy(completed = false) }) },
            ),
        )
    }

    private suspend fun adopt(session: SessionDraft) {
        val ids = session.exercises.map { it.exerciseId }
        exercisesById.value = exerciseRepository.getByIds(ids).associateBy { it.id }
        bestSets.value = workoutRepository.bestSetsFor(ids)
        expandedExerciseId.value = session.exercises.firstOrNull { we -> we.sets.any { !it.completed } }?.exerciseId
            ?: session.exercises.firstOrNull()?.exerciseId
        draft.value = session
        startPersisting()
    }

    /** Writes every change through, newest wins; a burst of keystrokes collapses to the latest. */
    private fun startPersisting() {
        persistJob?.cancel()
        persistJob = viewModelScope.launch {
            workoutRepository.saveDraft(draft.value ?: return@launch)
            draft.drop(1).filterNotNull().conflate().collect { workoutRepository.saveDraft(it) }
        }
    }

    private fun mutate(transform: (SessionDraft) -> SessionDraft) {
        draft.update { it?.let(transform) }
    }

    private fun SessionDraft.mapExercise(exerciseId: String, transform: (WorkoutExercise) -> WorkoutExercise) =
        copy(exercises = exercises.map { if (it.exerciseId == exerciseId) transform(it) else it })

    private fun WorkoutExercise.mapSet(index: Int, transform: (ExerciseSet) -> ExerciseSet) =
        copy(sets = sets.mapIndexed { i, s -> if (i == index) transform(s) else s })

    fun toggleExpanded(exerciseId: String) {
        expandedExerciseId.update { if (it == exerciseId) null else exerciseId }
    }

    /**
     * Ticking a set complete starts that exercise's rest - unless it was the last
     * open set of the whole workout. Un-ticking the set a rest belongs to cancels it.
     */
    fun toggleSetComplete(exerciseId: String, setIndex: Int) {
        val now = clock()
        mutate { session ->
            val exercise = session.exercises.firstOrNull { it.exerciseId == exerciseId } ?: return@mutate session
            val set = exercise.sets.getOrNull(setIndex) ?: return@mutate session
            val nowCompleted = !set.completed
            val updated = session.mapExercise(exerciseId) { it.mapSet(setIndex) { s -> s.copy(completed = nowCompleted) } }
            val anyOpen = updated.exercises.any { we -> we.sets.any { !it.completed } }
            val rest = when {
                nowCompleted && anyOpen -> RestState.start(exerciseId, setIndex, exercise.restSeconds, now)
                nowCompleted -> null
                session.rest?.exerciseId == exerciseId && session.rest.setIndex == setIndex -> null
                else -> session.rest
            }
            updated.copy(rest = rest)
        }
        if (draft.value?.rest == null) alerts.cancelRest()
        val session = draft.value ?: return
        val exercise = session.exercises.firstOrNull { it.exerciseId == exerciseId }
        if (exercise != null && exercise.sets.all { it.completed }) {
            // Move the user on to the next exercise that still has work.
            session.exercises.firstOrNull { we -> we.sets.any { !it.completed } }?.let { expandedExerciseId.value = it.exerciseId }
        }
    }

    fun updateWeightKg(exerciseId: String, setIndex: Int, weightKg: Double) =
        mutate { it.mapExercise(exerciseId) { we -> we.mapSet(setIndex) { s -> s.copy(weightKg = weightKg) } } }

    fun updateReps(exerciseId: String, setIndex: Int, reps: Int) =
        mutate { it.mapExercise(exerciseId) { we -> we.mapSet(setIndex) { s -> s.copy(reps = reps) } } }

    /** A new set copies the previous one, which is almost always what comes next. */
    fun addSet(exerciseId: String) = mutate { session ->
        session.mapExercise(exerciseId) { we ->
            val last = we.sets.lastOrNull()
            we.copy(sets = we.sets + ExerciseSet(id = "set-${we.sets.size}", weightKg = last?.weightKg ?: 0.0, reps = last?.reps ?: 8))
        }
    }

    fun removeSet(exerciseId: String, setIndex: Int) = mutate { session ->
        val rest = session.rest?.takeUnless { it.exerciseId == exerciseId && it.setIndex >= setIndex }
        session.mapExercise(exerciseId) { we ->
            we.copy(sets = we.sets.filterIndexed { i, _ -> i != setIndex })
        }.copy(rest = rest)
    }

    fun setRestSeconds(exerciseId: String, seconds: Int) = mutate {
        it.mapExercise(exerciseId) { we -> we.copy(restSeconds = seconds.coerceIn(WorkoutExercise.REST_RANGE_SECONDS)) }
    }

    fun pauseRest() = mutate { it.copy(rest = it.rest?.pause(clock())) }
    fun resumeRest() = mutate { it.copy(rest = it.rest?.resume(clock())) }
    fun adjustRest(deltaSeconds: Int) = mutate { it.copy(rest = it.rest?.adjust(deltaSeconds * 1_000L, clock())) }
    fun skipRest() = mutate { it.copy(rest = it.rest?.skip(clock())) }

    /** Clears a finished rest once the user has seen the "go" state. */
    fun dismissRest() {
        mutate { it.copy(rest = null) }
        alerts.cancelRest()
    }

    /** The app left the foreground with a rest still running: let the system keep time and alert. */
    fun onBackgrounded(text: RestNotificationText) {
        val rest = draft.value?.rest ?: return
        val endsAt = rest.endsAtMs ?: return
        if (rest.isFinished(clock())) return
        alerts.restInBackground(endsAt, text.runningTitle, text.runningBody, text.doneTitle, text.doneBody)
    }

    fun onForegrounded() = alerts.cancelRest()

    /** Throws the session away (after the screen confirmed it). */
    fun discard(onDone: () -> Unit) {
        persistJob?.cancel()
        alerts.cancelRest()
        viewModelScope.launch {
            workoutRepository.clearDraft()
            draft.value = null
            onDone()
        }
    }

    /**
     * Saves the session once. A second tap while saving returns null instead of
     * writing a duplicate.
     */
    suspend fun finish(): SessionCompletionResult? {
        if (!finishing.compareAndSet(expect = false, update = true)) return null
        val session = draft.value ?: run { finishing.value = false; return null }
        persistJob?.cancel()
        alerts.cancelRest()

        val endedAt = clock()
        val exercises = session.exercises.mapIndexed { index, we -> we.copy(position = index) }
        val completed = WorkoutSession(
            id = UUID.randomUUID().toString(),
            workoutId = session.templateId,
            name = session.name,
            date = Instant.ofEpochMilli(session.startedAtMs).atZone(ZoneId.systemDefault()).toLocalDate(),
            exercises = exercises,
            durationMin = ((endedAt - session.startedAtMs) / 60_000.0).roundToInt().coerceAtLeast(1),
            startedAtMs = session.startedAtMs,
            endedAtMs = endedAt,
        )
        val previousBest = workoutRepository.bestSetsFor(exercises.map { it.exerciseId })
        val prIds = PersonalRecords.recordsIn(completed, previousBest)
        val unlocked = workoutRepository.saveSession(completed.copy(prsHit = prIds))
        draft.value = null

        val prNames = prIds.mapNotNull { exercisesById.value[it]?.name }
        prNames.forEach(alerts::personalRecord)

        return SessionCompletionResult(
            volumeKg = completed.completedSets.sumOf { it.volume },
            completedSetCount = completed.completedSets.size,
            durationMin = completed.durationMin,
            prExerciseNames = prNames,
            unlocked = unlocked,
        )
    }
}
