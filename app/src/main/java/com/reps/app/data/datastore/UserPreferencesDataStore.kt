package com.reps.app.data.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.reps.app.domain.model.AppLanguage
import com.reps.app.domain.model.ThemeMode
import com.reps.app.domain.model.UnitSystem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "reps_prefs")

/**
 * Device-local preferences: language, units and whether onboarding has been
 * seen. These are deliberately not in Firestore - they decide what to show
 * before there is a signed-in user to read from.
 */
@Singleton
class UserPreferencesDataStore @Inject constructor(
    private val context: Context,
) {
    private object Keys {
        val ONBOARDING_SEEN = booleanPreferencesKey("onboarding_seen")
        val LANGUAGE = stringPreferencesKey("language")
        val UNITS = stringPreferencesKey("units")
        val THEME_MODE = stringPreferencesKey("theme_mode")
    }

    val onboardingSeen: Flow<Boolean> =
        context.dataStore.data.map { it[Keys.ONBOARDING_SEEN] ?: false }

    val language: Flow<AppLanguage> =
        context.dataStore.data.map { AppLanguage.fromTag(it[Keys.LANGUAGE] ?: "en") }

    val units: Flow<UnitSystem> = context.dataStore.data.map {
        runCatching { UnitSystem.valueOf(it[Keys.UNITS] ?: UnitSystem.METRIC.name) }
            .getOrDefault(UnitSystem.METRIC)
    }

    val themeMode: Flow<ThemeMode> =
        context.dataStore.data.map { ThemeMode.fromName(it[Keys.THEME_MODE]) }

    suspend fun setOnboardingSeen(seen: Boolean) {
        context.dataStore.edit { it[Keys.ONBOARDING_SEEN] = seen }
    }

    suspend fun setLanguage(language: AppLanguage) {
        context.dataStore.edit { it[Keys.LANGUAGE] = language.tag }
    }

    suspend fun setUnits(units: UnitSystem) {
        context.dataStore.edit { it[Keys.UNITS] = units.name }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.dataStore.edit { it[Keys.THEME_MODE] = mode.name }
    }
}
