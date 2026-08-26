package com.reps.app.feature.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.reps.app.data.datastore.UserPreferencesDataStore
import com.reps.app.data.notifications.WorkoutReminderScheduler
import com.reps.app.domain.model.AppLanguage
import com.reps.app.domain.model.Goal
import com.reps.app.domain.model.Sex
import com.reps.app.domain.model.ThemeMode
import com.reps.app.domain.model.UnitSystem
import com.reps.app.domain.model.User
import com.reps.app.domain.model.WeightEntry
import com.reps.app.domain.repository.AuthRepository
import com.reps.app.domain.repository.UserRepository
import com.reps.app.domain.repository.WeightRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject

data class ProfileUiState(
    val user: User? = null,
    val currentWeightKg: Double? = null,
    val notificationsEnabled: Boolean = true,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val loading: Boolean = true,
)

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val userRepository: UserRepository,
    private val weightRepository: WeightRepository,
    private val authRepository: AuthRepository,
    private val preferences: UserPreferencesDataStore,
    private val reminderScheduler: WorkoutReminderScheduler,
) : ViewModel() {

    val uiState = combine(
        userRepository.observeUser(),
        weightRepository.observeEntries(),
        preferences.notificationsEnabled,
        preferences.themeMode,
    ) { user, weights, notifsOn, themeMode ->
        ProfileUiState(
            user = user,
            currentWeightKg = weights.maxByOrNull { it.date }?.weightKg,
            notificationsEnabled = notifsOn,
            themeMode = themeMode,
            loading = false,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ProfileUiState(),
    )

    fun updateHeightCm(cm: Double) = updateUser { it.copy(heightCm = cm) }
    fun updateAge(age: Int) = updateUser { it.copy(age = age) }
    fun updateSex(sex: Sex) = updateUser { it.copy(sex = sex) }
    fun updateGoal(goal: Goal) = updateUser { it.copy(goal = goal) }
    fun updateUnits(units: UnitSystem) = updateUser { it.copy(units = units) }

    /**
     * Language is written to both the profile and DataStore: the catalogue
     * reads its language from the latter, so switching re-localises exercise
     * content immediately.
     */
    fun updateLanguage(language: AppLanguage) = updateUser { it.copy(language = language) }

    /** Persists the toggle and arms/cancels the daily reminder work to match. */
    fun toggleNotifications() {
        viewModelScope.launch {
            val newValue = !preferences.notificationsEnabled.first()
            preferences.setNotificationsEnabled(newValue)
            reminderScheduler.setEnabled(newValue)
        }
    }

    fun updateThemeMode(mode: ThemeMode) {
        viewModelScope.launch { preferences.setThemeMode(mode) }
    }

    fun addWeight(kg: Double) {
        viewModelScope.launch {
            weightRepository.logWeight(WeightEntry(id = UUID.randomUUID().toString(), date = LocalDate.now(), weightKg = kg))
        }
    }

    private fun updateUser(transform: (User) -> User) {
        viewModelScope.launch {
            val current = userRepository.observeUser().first() ?: return@launch
            userRepository.updateUser(transform(current))
        }
    }

    fun signOut() {
        viewModelScope.launch { authRepository.signOut() }
    }
}
