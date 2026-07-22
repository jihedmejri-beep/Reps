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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
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

@HiltViewModel
class WorkoutBuilderViewModel @Inject constructor(
    private val workoutRepository: WorkoutRepository,
    exerciseRepository: ExerciseRepository,
) : ViewModel() {

    private val name = MutableStateFlow("")
    private val selectedIds = MutableStateFlow<List<String>>(emptyList())
    private val pickerQuery = MutableStateFlow("")
    private val pickerFilter = MutableStateFlow<MuscleGroup?>(null)
    private val saving = MutableStateFlow(false)

    val uiState = combine(
        name,
        selectedIds,
        exerciseRepository.observeExercises(),
        pickerQuery,
        pickerFilter,
    ) { currentName, ids, allExercises, query, filter ->
        WorkoutBuilderUiState(
            name = currentName,
            selectedExerciseIds = ids,
            exercisesById = allExercises.associateBy { it.id },
            pickerResults = allExercises.filter { exercise ->
                (filter == null || exercise.muscleGroup == filter) &&
                    (query.isBlank() || exercise.name.contains(query, ignoreCase = true))
            },
            pickerQuery = query,
            pickerFilter = filter,
            saving = saving.value,
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
