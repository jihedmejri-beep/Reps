package com.reps.app.feature.workouts.builder

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.reps.app.core.constants.AppConstants
import com.reps.app.domain.model.Difficulty
import com.reps.app.domain.model.Exercise
import com.reps.app.domain.model.ExerciseSet
import com.reps.app.domain.model.MuscleGroup
import com.reps.app.domain.model.UnitSystem
import com.reps.app.domain.model.Workout
import com.reps.app.domain.model.WorkoutExercise
import com.reps.app.domain.repository.ExerciseRepository
import com.reps.app.domain.repository.UserRepository
import com.reps.app.domain.repository.WorkoutRepository
import com.reps.app.navigation.NavArgs
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.util.UUID
import javax.inject.Inject
import kotlin.math.roundToInt

/**
 * One exercise as planned in a template: a uniform sets x reps @ weight scheme
 * plus its rest. The session lets each set diverge; the plan stays simple.
 */
data class PlannedExercise(
    val exerciseId: String,
    val sets: Int = DEFAULT_SETS,
    val reps: Int = DEFAULT_REPS,
    val weightKg: Double = 0.0,
    val restSeconds: Int = WorkoutExercise.DEFAULT_REST_SECONDS,
) {
    companion object {
        const val DEFAULT_SETS = 3
        const val DEFAULT_REPS = 10
        val SETS_RANGE = 1..10
    }
}

data class WorkoutBuilderUiState(
    val editing: Boolean = false,
    val name: String = "",
    val scheduledDays: Set<DayOfWeek> = emptySet(),
    val planned: List<PlannedExercise> = emptyList(),
    val exercisesById: Map<String, Exercise> = emptyMap(),
    val pickerResults: List<Exercise> = emptyList(),
    val pickerQuery: String = "",
    val pickerFilter: MuscleGroup? = null,
    val saving: Boolean = false,
    val loading: Boolean = false,
    val units: UnitSystem = UnitSystem.METRIC,
) {
    val selectedExerciseIds: List<String> get() = planned.map { it.exerciseId }
    val isAtHardCap: Boolean get() = planned.size >= AppConstants.EXERCISE_HARD_CAP
    val isOverWarnThreshold: Boolean get() = planned.size > AppConstants.EXERCISE_WARN_THRESHOLD
    val canSave: Boolean get() = name.isNotBlank() && planned.isNotEmpty() && !saving && !loading

    /** An estimate - shown with "≈" - from the plan: ~45 s per set plus its rest. */
    val estimatedMinutes: Int get() = estimateMinutes(planned)
}

internal fun estimateMinutes(planned: List<PlannedExercise>): Int {
    val seconds = planned.sumOf { it.sets * (SECONDS_PER_SET + it.restSeconds) }
    return (seconds / 60.0).roundToInt().coerceAtLeast(if (planned.isEmpty()) 0 else 5)
}

private const val SECONDS_PER_SET = 45

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class WorkoutBuilderViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val workoutRepository: WorkoutRepository,
    private val exerciseRepository: ExerciseRepository,
    userRepository: UserRepository,
) : ViewModel() {

    /** Present when editing an existing template; absent for a new one. */
    private val editingId: String? = savedStateHandle.get<String>(NavArgs.WORKOUT_ID)
    private var original: Workout? = null

    private val name = MutableStateFlow("")
    private val scheduledDays = MutableStateFlow<Set<DayOfWeek>>(emptySet())
    private val planned = MutableStateFlow<List<PlannedExercise>>(emptyList())
    private val pickerQuery = MutableStateFlow("")
    private val pickerFilter = MutableStateFlow<MuscleGroup?>(null)
    private val saving = MutableStateFlow(false)
    private val loading = MutableStateFlow(editingId != null)

    /** The picker's own search, answered by the catalogue rather than in memory. */
    private val pickerResults = combine(pickerQuery, pickerFilter, ::Pair)
        .flatMapLatest { (query, filter) -> exerciseRepository.observeExercises(filter, query) }

    /** Only the exercises actually picked, looked up by id - never the whole library. */
    private val selectedExercises = planned.map { list ->
        exerciseRepository.getByIds(list.map { it.exerciseId }).associateBy { it.id }
    }

    val uiState = combine(
        combine(name, scheduledDays, planned, ::Triple),
        selectedExercises,
        pickerResults,
        combine(pickerQuery, pickerFilter, ::Pair),
        combine(saving, loading, userRepository.observeUser(), ::Triple),
    ) { (currentName, days, plan), byId, results, (query, filter), (isSaving, isLoading, user) ->
        WorkoutBuilderUiState(
            editing = editingId != null,
            name = currentName,
            scheduledDays = days,
            planned = plan,
            exercisesById = byId,
            pickerResults = results,
            pickerQuery = query,
            pickerFilter = filter,
            saving = isSaving,
            loading = isLoading,
            units = user?.units ?: UnitSystem.METRIC,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = WorkoutBuilderUiState(editing = editingId != null, loading = editingId != null),
    )

    init {
        if (editingId != null) {
            viewModelScope.launch {
                workoutRepository.observeTemplate(editingId).first()?.let { workout ->
                    original = workout
                    name.value = workout.name
                    scheduledDays.value = workout.scheduledDays
                    planned.value = workout.exercises.sortedBy { it.position }.map { we ->
                        val first = we.sets.firstOrNull()
                        PlannedExercise(
                            exerciseId = we.exerciseId,
                            sets = we.sets.size.coerceIn(PlannedExercise.SETS_RANGE),
                            reps = first?.reps ?: PlannedExercise.DEFAULT_REPS,
                            weightKg = first?.weightKg ?: 0.0,
                            restSeconds = we.restSeconds,
                        )
                    }
                }
                loading.value = false
            }
        }
    }

    fun onNameChange(value: String) {
        name.value = value
    }

    fun toggleDay(day: DayOfWeek) {
        scheduledDays.update { if (day in it) it - day else it + day }
    }

    fun onPickerQueryChange(value: String) {
        pickerQuery.value = value
    }

    fun onPickerFilterChange(group: MuscleGroup?) {
        pickerFilter.value = group
    }

    fun toggleExercise(exerciseId: String) {
        planned.update { current ->
            when {
                current.any { it.exerciseId == exerciseId } -> current.filterNot { it.exerciseId == exerciseId }
                current.size >= AppConstants.EXERCISE_HARD_CAP -> current
                else -> current + PlannedExercise(exerciseId)
            }
        }
    }

    fun removeExercise(exerciseId: String) {
        planned.update { list -> list.filterNot { it.exerciseId == exerciseId } }
    }

    /** Moves an exercise one place; the session plays back in exactly this order. */
    fun move(exerciseId: String, delta: Int) {
        planned.update { list ->
            val from = list.indexOfFirst { it.exerciseId == exerciseId }
            val to = from + delta
            if (from < 0 || to !in list.indices) return@update list
            list.toMutableList().apply { add(to, removeAt(from)) }
        }
    }

    fun updatePlan(exerciseId: String, transform: (PlannedExercise) -> PlannedExercise) {
        planned.update { list -> list.map { if (it.exerciseId == exerciseId) transform(it) else it } }
    }

    /** Returns true once the template is persisted, false if the form isn't valid yet. */
    suspend fun save(): Boolean {
        val state = uiState.value
        if (!state.canSave) return false
        saving.value = true
        workoutRepository.saveTemplate(
            Workout(
                id = editingId ?: UUID.randomUUID().toString(),
                name = state.name.trim(),
                scheduledDays = state.scheduledDays,
                difficulty = original?.difficulty ?: Difficulty.INTERMEDIATE,
                estimatedMinutes = state.estimatedMinutes,
                exercises = state.planned.mapIndexed { index, plan ->
                    WorkoutExercise(
                        exerciseId = plan.exerciseId,
                        position = index,
                        restSeconds = plan.restSeconds,
                        sets = List(plan.sets) { setIndex ->
                            ExerciseSet(id = "set-$setIndex", weightKg = plan.weightKg, reps = plan.reps)
                        },
                    )
                },
            ),
        )
        saving.value = false
        return true
    }
}
