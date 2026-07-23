package com.reps.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.reps.app.data.datastore.UserPreferencesDataStore
import com.reps.app.domain.model.ThemeMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * Root-level state read before any screen: currently just the theme mode, which
 * the activity needs to pick the palette before the first frame.
 */
@HiltViewModel
class AppViewModel @Inject constructor(
    preferences: UserPreferencesDataStore,
) : ViewModel() {

    val themeMode: StateFlow<ThemeMode> = preferences.themeMode.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = ThemeMode.SYSTEM,
    )
}
