package com.reps.app.feature.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.reps.app.data.datastore.UserPreferencesDataStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val preferences: UserPreferencesDataStore,
) : ViewModel() {

    /**
     * Onboarding is first-install only, so leaving by either route marks it
     * seen. [onDone] runs after the flag is written, so a user who kills the
     * app mid-navigation still does not see onboarding twice.
     */
    fun completeOnboarding(onDone: () -> Unit) {
        viewModelScope.launch {
            preferences.setOnboardingSeen(true)
            onDone()
        }
    }
}
