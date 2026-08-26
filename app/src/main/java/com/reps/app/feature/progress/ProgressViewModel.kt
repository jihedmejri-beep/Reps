package com.reps.app.feature.progress

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.reps.app.core.content.MotivationQuotes
import com.reps.app.domain.model.Exercise
import com.reps.app.domain.model.UnitSystem
import com.reps.app.domain.model.WeightEntry
import com.reps.app.domain.model.Workout
import com.reps.app.domain.model.WorkoutSession
import com.reps.app.domain.repository.ExerciseRepository
import com.reps.app.domain.repository.UserRepository
import com.reps.app.domain.repository.WeightRepository
import com.reps.app.domain.repository.WorkoutRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject

data class ProgressUiState(
    val sessions: List<WorkoutSession> = emptyList(),
    val templates: List<Workout> = emptyList(),
    val exercisesById: Map<String, Exercise> = emptyMap(),
    val weightEntries: List<WeightEntry> = emptyList(),
    val currentWeightKg: Double? = null,
    val units: UnitSystem = UnitSystem.METRIC,
    val streakCount: Int = 0,
    val quote: String = "",
    val loading: Boolean = true,
)

@HiltViewModel
class ProgressViewModel @Inject constructor(
    private val weightRepository: WeightRepository,
    workoutRepository: WorkoutRepository,
    private val exerciseRepository: ExerciseRepository,
    userRepository: UserRepository,
) : ViewModel() {

    /**
     * Only the exercises the logged sessions actually reference. The muscle
     * distribution chart needs a group per exercise it has volume for, which is
     * a few dozen - not the whole 828-row catalogue.
     */
    private val sessionExercises = workoutRepository.observeSessions().map { sessions ->
        val ids = sessions.flatMap { session -> session.exercises.map { it.exerciseId } }
        exerciseRepository.getByIds(ids.distinct()).associateBy { it.id }
    }

    val uiState = combine(
        workoutRepository.observeSessions(),
        workoutRepository.observeTemplates(),
        sessionExercises,
        weightRepository.observeEntries(),
        userRepository.observeUser(),
    ) { sessions, templates, exercisesById, weights, user ->
        ProgressUiState(
            sessions = sessions.sortedBy { it.date },
            templates = templates,
            exercisesById = exercisesById,
            weightEntries = weights,
            currentWeightKg = weights.maxByOrNull { it.date }?.weightKg,
            units = user?.units ?: UnitSystem.METRIC,
            streakCount = user?.streakCount ?: 0,
            quote = MotivationQuotes.forToday(),
            loading = false,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ProgressUiState(),
    )

    fun addWeight(kg: Double) {
        viewModelScope.launch {
            weightRepository.logWeight(WeightEntry(id = UUID.randomUUID().toString(), date = LocalDate.now(), weightKg = kg))
        }
    }
}
