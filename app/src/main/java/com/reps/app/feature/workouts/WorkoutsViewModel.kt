package com.reps.app.feature.workouts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.reps.app.domain.model.Exercise
import com.reps.app.domain.model.MuscleGroup
import com.reps.app.domain.model.Workout
import com.reps.app.domain.repository.ExerciseRepository
import com.reps.app.domain.repository.WorkoutRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class WorkoutsUiState(
    val myWorkouts: List<Workout> = emptyList(),
    val exercises: List<Exercise> = emptyList(),
    val query: String = "",
    val activeFilter: MuscleGroup? = null,
    val loading: Boolean = true,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class WorkoutsViewModel @Inject constructor(
    workoutRepository: WorkoutRepository,
    exerciseRepository: ExerciseRepository,
) : ViewModel() {

    private val query = MutableStateFlow("")
    private val filter = MutableStateFlow<MuscleGroup?>(null)

    /**
     * Search and filter are handed to the catalogue rather than applied to a
     * loaded list: it holds 828 exercises in three languages, and matching in
     * SQLite against an index means a keystroke reads only the rows it matches.
     * `flatMapLatest` drops the in-flight query when the next keystroke lands.
     */
    private val results = combine(query, filter, ::Pair)
        .flatMapLatest { (currentQuery, currentFilter) ->
            exerciseRepository.observeExercises(currentFilter, currentQuery)
        }

    val uiState = combine(
        workoutRepository.observeTemplates(),
        results,
        query,
        filter,
    ) { myWorkouts, exercises, currentQuery, currentFilter ->
        WorkoutsUiState(
            myWorkouts = myWorkouts,
            exercises = exercises,
            query = currentQuery,
            activeFilter = currentFilter,
            loading = false,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = WorkoutsUiState(),
    )

    fun onQueryChange(value: String) {
        query.value = value
    }

    fun onFilterChange(group: MuscleGroup?) {
        filter.value = group
    }
}
