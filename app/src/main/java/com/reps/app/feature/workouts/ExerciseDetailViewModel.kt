package com.reps.app.feature.workouts

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.reps.app.domain.model.ExerciseDetail
import com.reps.app.domain.model.MuscleDiagram
import com.reps.app.domain.repository.ExerciseRepository
import com.reps.app.domain.repository.MuscleSvgRepository
import com.reps.app.navigation.NavArgs
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class ExerciseDetailUiState(
    val detail: ExerciseDetail? = null,
    val diagram: MuscleDiagram = MuscleDiagram(),
    val loading: Boolean = true,
) {
    /** The id resolved to nothing - a stale link, or a catalogue that failed to open. */
    val notFound: Boolean get() = !loading && detail == null
}

@HiltViewModel
class ExerciseDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    exerciseRepository: ExerciseRepository,
    private val muscleSvgRepository: MuscleSvgRepository,
) : ViewModel() {

    private val exerciseId: String = checkNotNull(savedStateHandle[NavArgs.EXERCISE_ID])

    /**
     * The body diagram is resolved alongside the exercise rather than by the
     * screen, so the UI is handed asset paths and never has to know that muscle
     * artwork is a database lookup.
     */
    val uiState = exerciseRepository.observeExerciseDetail(exerciseId)
        .map { detail ->
            ExerciseDetailUiState(
                detail = detail,
                diagram = detail?.let { muscleSvgRepository.diagramFor(it.allMuscles) }
                    ?: MuscleDiagram(),
                loading = false,
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = ExerciseDetailUiState(),
        )
}
