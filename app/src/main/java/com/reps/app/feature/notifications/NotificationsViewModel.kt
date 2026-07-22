package com.reps.app.feature.notifications

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.reps.app.domain.repository.WorkoutRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import javax.inject.Inject

data class NotificationItem(val id: String, val workoutName: String)

data class NotificationsUiState(val items: List<NotificationItem> = emptyList(), val loading: Boolean = true)

@HiltViewModel
class NotificationsViewModel @Inject constructor(
    workoutRepository: WorkoutRepository,
) : ViewModel() {

    val uiState = workoutRepository.observeWorkoutFor(LocalDate.now()).map { workout ->
        NotificationsUiState(
            items = listOfNotNull(workout?.let { NotificationItem(id = "today-${it.id}", workoutName = it.name) }),
            loading = false,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = NotificationsUiState(),
    )
}
