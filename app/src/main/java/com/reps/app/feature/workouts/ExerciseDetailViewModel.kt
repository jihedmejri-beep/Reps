package com.reps.app.feature.workouts

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.reps.app.domain.model.Exercise
import com.reps.app.domain.repository.ExerciseRepository
import com.reps.app.navigation.NavArgs
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class ExerciseDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    exerciseRepository: ExerciseRepository,
) : ViewModel() {

    private val exerciseId: String = checkNotNull(savedStateHandle[NavArgs.EXERCISE_ID])

    val exercise = exerciseRepository.observeExercise(exerciseId).stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = null as Exercise?,
    )
}
