package com.reps.app.feature.workouts.builder

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.reps.app.core.constants.AppConstants
import com.reps.app.domain.model.Difficulty
import com.reps.app.domain.model.Exercise
import com.reps.app.domain.model.ExerciseSet
import com.reps.app.domain.model.MuscleGroup
import com.reps.app.domain.model.Workout
import com.reps.app.domain.model.WorkoutExercise
import com.reps.app.domain.repository.ExerciseRepository
import com.reps.app.domain.repository.WorkoutRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import java.util.UUID
import javax.inject.Inject

data class WorkoutBuilderUiState(
    val name: String = "",
    val selectedExerciseIds: List<String> = emptyList(),
    val exercisesById: Map<String, Exercise> = emptyMap(),
    val pickerResults: List<Exercise> = emptyList(),
    val pickerQuery: String = "",
    val pickerFilter: MuscleGroup? = null,
    val saving: Boolean = false,
) {
    val selectedExercises: List<Exercise> get() = selectedExerciseIds.mapNotNull { exercisesById[it] }
    val isAtHardCap: Boolean get() = selectedExerciseIds.size >= AppConstants.EXERCISE_HARD_CAP
    val isOverWarnThreshold: Boolean get() = selectedExerciseIds.size > AppConstants.EXERCISE_WARN_THRESHOLD
    val canSave: Boolean get() = name.isNotBlank() && selectedExerciseIds.isNotEmpty() && !saving
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class WorkoutBuilderViewModel @Inject constructor(
    private val workoutRepository: WorkoutRepository,
    private val exerciseRepository: ExerciseRepository,
) : ViewModel() {

    private val name = MutableStateFlow("")
    private val selectedIds = MutableStateFlow<List<String>>(emptyList())
    private val pickerQuery = MutableStateFlow("")
    private val pickerFilter = MutableStateFlow<MuscleGroup?>(null)
    private val saving = MutableStateFlow(false)

    /** The picker's own search, answered by the catalogue rather than in memory. */
    private val pickerResults = combine(pickerQuery, pickerFilter, ::Pair)
        .flatMapLatest { (query, filter) ->
            exerciseRepository.observeExercises(filter, query)
        }

    /**
     * Only the handful of exercises actually picked, looked up by id. The
     * selected chips used to be resolved out of the full library, which meant
     * holding all 828 rows in memory to render at most twenty of them.
     */
    private val selectedExercises = selectedIds.map { ids ->
        exerciseRepository.getByIds(ids).associateBy { it.id }
    }

    val uiState = combine(
        name,
        selectedIds,
        selectedExercises,
        pickerResults,
        combine(pickerQuery, pickerFilter, saving, ::Triple),
    ) { currentName, ids, byId, results, (query, filter, isSaving) ->
        WorkoutBuilderUiState(
            name = currentName,
            selectedExerciseIds = ids,
            exercisesById = byId,
            pickerResults = results,
            pickerQuery = query,
            pickerFilter = filter,
            saving = isSaving,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = WorkoutBuilderUiState(),
    )

    fun onNameChange(value: String) {
        name.value = value
    }

    fun onPickerQueryChange(value: String) {
        pickerQuery.value = value
    }

    fun onPickerFilterChange(group: MuscleGroup?) {
        pickerFilter.value = group
    }

    fun toggleExercise(exerciseId: String) {
        selectedIds.update { current ->
            when {
                exerciseId in current -> current - exerciseId
                current.size >= AppConstants.EXERCISE_HARD_CAP -> current
                else -> current + exerciseId
            }
        }
    }

    fun removeExercise(exerciseId: String) {
        selectedIds.update { it - exerciseId }
    }

    /** Returns true once the template is persisted, false if the form isn't valid yet. */
    suspend fun save(): Boolean {
        val state = uiState.value
        if (!state.canSave) return false
        saving.value = true
        val exerciseIds = state.selectedExerciseIds
        workoutRepository.saveTemplate(
            Workout(
                id = UUID.randomUUID().toString(),
                name = state.name.trim(),
                difficulty = Difficulty.INTERMEDIATE,
                estimatedMinutes = exerciseIds.size * 9 + 8,
                exercises = exerciseIds.mapIndexed { index, exerciseId ->
                    WorkoutExercise(
                        exerciseId = exerciseId,
                        position = index,
                        sets = List(3) { setIndex ->
                            ExerciseSet(id = "set-$setIndex", weightKg = 20.0, reps = 10)
                        },
                    )
                },
            ),
        )
        saving.value = false
        return true
    }
}
