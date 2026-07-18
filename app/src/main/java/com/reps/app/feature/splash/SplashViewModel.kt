package com.reps.app.feature.splash

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.reps.app.data.datastore.UserPreferencesDataStore
import com.reps.app.domain.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** Where the splash hands off to once its animation has played. */
enum class SplashDestination { ONBOARDING, LOGIN, HOME }

@HiltViewModel
class SplashViewModel @Inject constructor(
    preferences: UserPreferencesDataStore,
    authRepository: AuthRepository,
) : ViewModel() {

    /**
     * Null until both sources have reported. The splash animation runs for a
     * fixed time regardless, so this is normally settled well before it is
     * needed.
     */
    val destination = combine(
        preferences.onboardingSeen,
        authRepository.observeAuthState(),
    ) { seen, uid ->
        when {
            !seen -> SplashDestination.ONBOARDING
            uid == null -> SplashDestination.LOGIN
            else -> SplashDestination.HOME
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = null,
    )
}
