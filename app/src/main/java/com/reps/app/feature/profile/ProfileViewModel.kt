package com.reps.app.feature.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.reps.app.domain.model.AppLanguage
import com.reps.app.domain.model.Goal
import com.reps.app.domain.model.Sex
import com.reps.app.domain.model.UnitSystem
import com.reps.app.domain.model.User
import com.reps.app.domain.model.WeightEntry
import com.reps.app.domain.repository.AuthRepository
import com.reps.app.domain.repository.UserRepository
import com.reps.app.domain.repository.WeightRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject

data class ProfileUiState(
    val user: User? = null,
    val currentWeightKg: Double? = null,
    val notificationsEnabled: Boolean = true,
    val loading: Boolean = true,
)

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val userRepository: UserRepository,
    private val weightRepository: WeightRepository,
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val notificationsEnabled = MutableStateFlow(true)

    val uiState = combine(
        userRepository.observeUser(),
        weightRepository.observeEntries(),
        notificationsEnabled,
    ) { user, weights, notifsOn ->
        ProfileUiState(
            user = user,
            currentWeightKg = weights.maxByOrNull { it.date }?.weightKg,
            notificationsEnabled = notifsOn,
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
    fun updateLanguage(language: AppLanguage) = updateUser { it.copy(language = language) }

    fun toggleNotifications() {
        notificationsEnabled.update { !it }
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
