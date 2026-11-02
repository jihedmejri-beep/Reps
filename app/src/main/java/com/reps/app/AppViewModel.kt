package com.reps.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.reps.app.data.datastore.UserPreferencesDataStore
import com.reps.app.domain.model.AppLanguage
import com.reps.app.domain.model.ThemeMode
import com.reps.app.domain.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Root-level state read before any screen: the theme mode, which the activity
 * needs to pick the palette before the first frame, and the language sync that
 * keeps exercise content in the same language the UI is rendering in.
 */
@HiltViewModel
class AppViewModel @Inject constructor(
    private val preferences: UserPreferencesDataStore,
    private val userRepository: UserRepository,
) : ViewModel() {

    val themeMode: StateFlow<ThemeMode> = preferences.themeMode.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = ThemeMode.SYSTEM,
    )

    /**
     * The UI language is decided by the platform (device language, or the per-app
     * choice); the catalogue and the profile follow it. Without this a French
     * phone showed French screens with English exercise names until the user
     * found the picker.
     */
    fun syncLanguage(uiLanguage: AppLanguage) {
        viewModelScope.launch {
            if (preferences.language.first() != uiLanguage) preferences.setLanguage(uiLanguage)
            val user = userRepository.observeUser().first() ?: return@launch
            if (user.language != uiLanguage) userRepository.updateUser(user.copy(language = uiLanguage))
        }
    }
}
