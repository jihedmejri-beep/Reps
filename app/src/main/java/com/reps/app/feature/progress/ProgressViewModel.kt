package com.reps.app.feature.progress

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.reps.app.core.util.DateTicker
import com.reps.app.domain.model.Exercise
import com.reps.app.domain.model.Streak
import com.reps.app.domain.model.UnitSystem
import com.reps.app.domain.model.WeightEntry
import com.reps.app.domain.model.Workout
import com.reps.app.domain.model.WorkoutSession
import com.reps.app.domain.progress.AchievementId
import com.reps.app.domain.progress.AchievementProgress
import com.reps.app.domain.progress.Achievements
import com.reps.app.domain.progress.PersonalRecords
import com.reps.app.domain.progress.RecordEvent
import com.reps.app.domain.progress.TrainingTotals
import com.reps.app.domain.repository.ExerciseRepository
import com.reps.app.domain.repository.UserRepository
import com.reps.app.domain.repository.WeightRepository
import com.reps.app.domain.repository.WorkoutRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject

data class ProgressUiState(
    val today: LocalDate = LocalDate.now(),
    val sessions: List<WorkoutSession> = emptyList(),
    val templates: List<Workout> = emptyList(),
    val exercisesById: Map<String, Exercise> = emptyMap(),
    val weightEntries: List<WeightEntry> = emptyList(),
    val currentWeightKg: Double? = null,
    val units: UnitSystem = UnitSystem.METRIC,
    val streak: Streak = Streak(),
    val records: List<RecordEvent> = emptyList(),
    val totals: TrainingTotals? = null,
    val achievements: List<AchievementProgress> = emptyList(),
    /** First-unlock time (epoch ms) of every earned achievement. */
    val unlockedAt: Map<AchievementId, Long> = emptyMap(),
    val loading: Boolean = true,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ProgressViewModel @Inject constructor(
    private val weightRepository: WeightRepository,
    workoutRepository: WorkoutRepository,
    private val exerciseRepository: ExerciseRepository,
    userRepository: UserRepository,
) : ViewModel() {

    private val today = DateTicker.today()

    /** Only sessions with completed work count toward progress. */
    private val sessions = workoutRepository.observeSessions().map { all -> all.filter { it.hasWork }.sortedBy { it.date } }

    /**
     * Only the exercises the logged sessions actually reference. The muscle
     * distribution chart needs a group per exercise it has volume for, which is
     * a few dozen - not the whole 828-row catalogue.
     */
    private val sessionExercises = sessions.map { list ->
        val ids = list.flatMap { session -> session.exercises.map { it.exerciseId } }
        exerciseRepository.getByIds(ids.distinct()).associateBy { it.id }
    }

    private val streak = today.flatMapLatest { workoutRepository.observeStreak(it) }

    private val history = combine(sessions, workoutRepository.observeTemplates(), sessionExercises, ::Triple)

    private val body = combine(weightRepository.observeEntries(), userRepository.observeUser(), ::Pair)

    val uiState = combine(
        today,
        history,
        body,
        streak,
        workoutRepository.observeUnlocks(),
    ) { day, (sessionList, templates, exercisesById), (weights, user), currentStreak, unlocks ->
        val totals = TrainingTotals.from(sessionList, templates.flatMap { it.scheduledDays }.toSet(), day)
        ProgressUiState(
            today = day,
            sessions = sessionList,
            templates = templates,
            exercisesById = exercisesById,
            weightEntries = weights,
            currentWeightKg = weights.maxByOrNull { it.date }?.weightKg,
            units = user?.units ?: UnitSystem.METRIC,
            streak = currentStreak,
            records = PersonalRecords.current(sessionList),
            totals = totals,
            // An unlock is permanent, so a stored one stays unlocked even if the
            // live numbers later dip (e.g. a streak ends).
            achievements = Achievements.progress(totals).map { progress ->
                if (progress.id in unlocks) progress.copy(current = maxOf(progress.current, progress.id.target)) else progress
            },
            unlockedAt = unlocks,
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
