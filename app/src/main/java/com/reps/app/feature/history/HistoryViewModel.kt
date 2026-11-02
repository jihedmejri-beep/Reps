package com.reps.app.feature.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.reps.app.domain.model.Exercise
import com.reps.app.domain.model.UnitSystem
import com.reps.app.domain.model.WorkoutSession
import com.reps.app.domain.repository.ExerciseRepository
import com.reps.app.domain.repository.UserRepository
import com.reps.app.domain.repository.WorkoutRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class HistoryUiState(
    /** Newest first. Sessions saved with nothing completed are left out, as everywhere else. */
    val sessions: List<WorkoutSession> = emptyList(),
    val exercisesById: Map<String, Exercise> = emptyMap(),
    val units: UnitSystem = UnitSystem.METRIC,
    val loading: Boolean = true,
)

@HiltViewModel
class HistoryViewModel @Inject constructor(
    workoutRepository: WorkoutRepository,
    exerciseRepository: ExerciseRepository,
    userRepository: UserRepository,
) : ViewModel() {

    private val sessions = workoutRepository.observeSessions().map { all ->
        all.filter { it.hasWork }.sortedWith(compareByDescending<WorkoutSession> { it.date }.thenByDescending { it.endedAtMs })
    }

    private val exercises = sessions.map { list ->
        exerciseRepository.getByIds(list.flatMap { s -> s.exercises.map { it.exerciseId } }.distinct()).associateBy { it.id }
    }

    val uiState = combine(sessions, exercises, userRepository.observeUser()) { list, byId, user ->
        HistoryUiState(sessions = list, exercisesById = byId, units = user?.units ?: UnitSystem.METRIC, loading = false)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HistoryUiState())
}
