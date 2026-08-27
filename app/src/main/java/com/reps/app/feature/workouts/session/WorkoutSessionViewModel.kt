package com.reps.app.feature.workouts.session

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.reps.app.data.notifications.NotificationHelper
import com.reps.app.domain.model.Exercise
import com.reps.app.domain.model.ExerciseSet
import com.reps.app.domain.model.UnitSystem
import com.reps.app.domain.model.WorkoutExercise
import com.reps.app.domain.model.WorkoutSession
import com.reps.app.domain.repository.ExerciseRepository
import com.reps.app.domain.repository.UserRepository
import com.reps.app.domain.repository.WorkoutRepository
import com.reps.app.navigation.NavArgs
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
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
)

data class WorkoutSessionUiState(
    val workoutName: String = "",
    val exercises: List<SessionExerciseUi> = emptyList(),
    val expandedExerciseId: String? = null,
    val units: UnitSystem = UnitSystem.METRIC,
    val completedSetCount: Int = 0,
    val totalSetCount: Int = 0,
    val volumeKg: Double = 0.0,
    val loading: Boolean = true,
)

data class SessionCompletionResult(
    val volumeKg: Double,
    val completedSetCount: Int,
    val prExerciseIds: List<String>,
)

@HiltViewModel
class WorkoutSessionViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val workoutRepository: WorkoutRepository,
    exerciseRepository: ExerciseRepository,
    userRepository: UserRepository,
    private val notificationHelper: NotificationHelper,
) : ViewModel() {

    private val workoutId: String = checkNotNull(savedStateHandle[NavArgs.WORKOUT_ID])

    private val workingSets = MutableStateFlow<Map<String, List<ExerciseSet>>>(emptyMap())
    private val exerciseOrder = MutableStateFlow<List<String>>(emptyList())
    private val exercisesById = MutableStateFlow<Map<String, Exercise>>(emptyMap())
    private val workoutName = MutableStateFlow("")
    private val estimatedMinutes = MutableStateFlow(0)
    private val expandedExerciseId = MutableStateFlow<String?>(null)
    private val templateLoaded = MutableStateFlow(false)

    /** Bumped whenever a set flips to completed; the screen restarts the shared rest timer on it. */
    val restSignal = MutableStateFlow(0)

    val uiState: StateFlow<WorkoutSessionUiState> = combine(
        workingSets,
        exerciseOrder,
        expandedExerciseId,
        userRepository.observeUser(),
        templateLoaded,
    ) { sets, order, expanded, user, loaded ->
        val exercisesUi = order.mapNotNull { id ->
            val exercise = exercisesById.value[id] ?: return@mapNotNull null
            SessionExerciseUi(
                exercise = exercise,
                sets = (sets[id] ?: emptyList()).mapIndexed { i, s ->
                    SessionSetUi(i, s.weightKg, s.reps, s.completed)
                },
            )
        }
        val allSets = sets.values.flatten()
        WorkoutSessionUiState(
            workoutName = workoutName.value,
            exercises = exercisesUi,
            expandedExerciseId = expanded,
            units = user?.units ?: UnitSystem.METRIC,
            completedSetCount = allSets.count { it.completed },
            totalSetCount = allSets.size,
            volumeKg = allSets.filter { it.completed }.sumOf { it.volume },
            loading = !loaded,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = WorkoutSessionUiState(),
    )

    init {
        viewModelScope.launch {
            val workout = workoutRepository.observeTemplate(workoutId).first()
            if (workout != null) {
                val sorted = workout.exercises.sortedBy { it.position }
                exercisesById.value = exerciseRepository.getByIds(sorted.map { it.exerciseId })
                    .associateBy { it.id }
                exerciseOrder.value = sorted.map { it.exerciseId }
                workingSets.value = sorted.associate { it.exerciseId to it.sets }
                workoutName.value = workout.name
                estimatedMinutes.value = workout.estimatedMinutes
                expandedExerciseId.value = sorted.firstOrNull()?.exerciseId
            }
            templateLoaded.value = true
        }
    }

    fun toggleExpanded(exerciseId: String) {
        expandedExerciseId.update { if (it == exerciseId) null else exerciseId }
    }

    fun toggleSetComplete(exerciseId: String, setIndex: Int) {
        var justCompleted = false
        workingSets.update { map ->
            val list = map[exerciseId] ?: return@update map
            val updated = list.mapIndexed { i, set ->
                if (i != setIndex) return@mapIndexed set
                val nowCompleted = !set.completed
                if (nowCompleted) justCompleted = true
                set.copy(completed = nowCompleted)
            }
            map + (exerciseId to updated)
        }
        if (justCompleted) restSignal.update { it + 1 }
    }

    fun updateWeightKg(exerciseId: String, setIndex: Int, weightKg: Double) {
        workingSets.update { map ->
            val list = map[exerciseId] ?: return@update map
            map + (exerciseId to list.mapIndexed { i, s -> if (i == setIndex) s.copy(weightKg = weightKg) else s })
        }
    }

    fun updateReps(exerciseId: String, setIndex: Int, reps: Int) {
        workingSets.update { map ->
            val list = map[exerciseId] ?: return@update map
            map + (exerciseId to list.mapIndexed { i, s -> if (i == setIndex) s.copy(reps = reps) else s })
        }
    }

    fun addSet(exerciseId: String) {
        workingSets.update { map ->
            val list = map[exerciseId] ?: emptyList()
            val last = list.lastOrNull()
            val newSet = ExerciseSet(
                id = "set-${list.size}",
                weightKg = last?.weightKg ?: 20.0,
                reps = last?.reps ?: 10,
            )
            map + (exerciseId to (list + newSet))
        }
    }

    suspend fun finish(): SessionCompletionResult {
        val order = exerciseOrder.value
        val sets = workingSets.value
        val prIds = mutableListOf<String>()
        val sessionExercises = order.mapIndexed { index, exerciseId ->
            val exerciseSets = sets[exerciseId] ?: emptyList()
            val bestThisSession = exerciseSets.filter { it.completed }.maxOfOrNull { it.volume } ?: 0.0
            if (bestThisSession > 0.0) {
                val previousBest = workoutRepository.bestVolumeFor(exerciseId) ?: 0.0
                if (bestThisSession > previousBest) prIds += exerciseId
            }
            WorkoutExercise(exerciseId, index, exerciseSets)
        }
        val allSets = sessionExercises.flatMap { it.sets }
        val completedSets = allSets.count { it.completed }
        val volume = allSets.filter { it.completed }.sumOf { it.volume }
        val completionRatio = if (allSets.isEmpty()) 0f else completedSets / allSets.size.toFloat()

        workoutRepository.saveSession(
            WorkoutSession(
                id = UUID.randomUUID().toString(),
                workoutId = workoutId,
                name = workoutName.value,
                date = LocalDate.now(),
                exercises = sessionExercises,
                durationMin = maxOf(12, (estimatedMinutes.value * completionRatio).roundToInt()),
                prsHit = prIds,
            ),
        )

        for (id in prIds) {
            val name = exercisesById.value[id]?.name ?: continue
            notificationHelper.postPrNotification(name)
        }

        return SessionCompletionResult(volume, completedSets, prIds)
    }
}
