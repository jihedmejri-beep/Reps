package com.reps.app.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.reps.app.data.fake.SampleData
import com.reps.app.domain.model.MuscleGroup
import com.reps.app.domain.model.Streak
import com.reps.app.domain.model.UnitSystem
import com.reps.app.domain.model.WeightEntry
import com.reps.app.domain.model.Workout
import com.reps.app.domain.repository.ExerciseRepository
import com.reps.app.domain.repository.UserRepository
import com.reps.app.domain.repository.WeightRepository
import com.reps.app.domain.repository.WorkoutRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import javax.inject.Inject
import kotlin.math.abs

/** One line in the Today card's exercise breakdown: "Bench Press · 3 × 8". */
data class TodayExercise(
    val name: String,
    val setCount: Int,
    val reps: Int,
)

data class HomeUiState(
    val userName: String = "",
    val streak: Streak = Streak(),
    val todayWorkout: Workout? = null,
    /** Resolved to localised labels by the screen, not stringified here. */
    val todayMuscleGroups: List<MuscleGroup> = emptyList(),
    /** The workout's exercises in order, for the Today card breakdown. */
    val todayExercises: List<TodayExercise> = emptyList(),
    /** Sets across the whole workout, shown as a headline count. */
    val todaySetCount: Int = 0,
    val currentWeightKg: Double? = null,
    val weeklyDeltaKg: Double? = null,
    val units: UnitSystem = UnitSystem.METRIC,
    val quote: String = "",
    val loading: Boolean = true,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HomeViewModel @Inject constructor(
    userRepository: UserRepository,
    weightRepository: WeightRepository,
    private val workoutRepository: WorkoutRepository,
    private val exerciseRepository: ExerciseRepository,
) : ViewModel() {

    private val today = LocalDate.now()

    /** Everything the Today card needs, resolved once the exercises are loaded. */
    private data class TodayWorkout(
        val workout: Workout? = null,
        val muscles: List<MuscleGroup> = emptyList(),
        val exercises: List<TodayExercise> = emptyList(),
        val setCount: Int = 0,
    )

    private val todayWorkoutFlow: Flow<TodayWorkout> =
        workoutRepository.observeWorkoutFor(today).flatMapLatest { workout ->
            if (workout == null) {
                flowOf(TodayWorkout())
            } else {
                flow {
                    val ordered = workout.exercises.sortedBy { it.position }
                    val byId = exerciseRepository.getByIds(ordered.map { it.exerciseId })
                        .associateBy { it.id }
                    val exercises = ordered.mapNotNull { we ->
                        byId[we.exerciseId]?.let { ex ->
                            TodayExercise(
                                name = ex.name,
                                setCount = we.sets.size,
                                // Templates use one rep target per exercise; the
                                // first set is representative.
                                reps = we.sets.firstOrNull()?.reps ?: 0,
                            )
                        }
                    }
                    emit(
                        TodayWorkout(
                            workout = workout,
                            // Distinct, in workout order: "Chest & Shoulders", not a set.
                            muscles = ordered.mapNotNull { byId[it.exerciseId]?.muscleGroup }.distinct(),
                            exercises = exercises,
                            setCount = ordered.sumOf { it.sets.size },
                        ),
                    )
                }
            }
        }

    val uiState = combine(
        userRepository.observeUser(),
        weightRepository.observeEntries(),
        todayWorkoutFlow,
    ) { user, weights, todayData ->
        HomeUiState(
            userName = user?.name.orEmpty(),
            streak = Streak(
                count = user?.streakCount ?: 0,
                lastWorkoutDate = user?.lastWorkoutDate,
            ),
            todayWorkout = todayData.workout,
            todayMuscleGroups = todayData.muscles,
            todayExercises = todayData.exercises,
            todaySetCount = todayData.setCount,
            currentWeightKg = weights.maxByOrNull { it.date }?.weightKg,
            weeklyDeltaKg = weeklyDelta(weights),
            units = user?.units ?: UnitSystem.METRIC,
            quote = SampleData.motivationQuoteForToday(today),
            loading = false,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = HomeUiState(),
    )

    /**
     * Change against the reading closest to seven days ago. Falling back to the
     * oldest entry would call a brand-new user's first week a "weekly" change,
     * so anything without a reading at least 3 days old reports nothing.
     */
    private fun weeklyDelta(entries: List<WeightEntry>): Double? {
        val latest = entries.maxByOrNull { it.date } ?: return null
        val target = latest.date.minusDays(7)
        val reference = entries
            .filter { it.date < latest.date }
            .minByOrNull { abs(java.time.temporal.ChronoUnit.DAYS.between(it.date, target)) }
            ?: return null
        val gap = java.time.temporal.ChronoUnit.DAYS.between(reference.date, latest.date)
        if (gap < 3) return null
        return latest.weightKg - reference.weightKg
    }
}
