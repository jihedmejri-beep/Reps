package com.reps.app.data.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.reps.app.domain.model.AppLanguage
import com.reps.app.domain.model.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "reps_prefs")

/**
 * Device-local preferences: language, units and whether onboarding has been
 * seen. These are deliberately not in Firestore - they decide what to show
 * before there is a signed-in user to read from.
 *
 * Also home to the signed-in account id (`session_uid`), which is what makes a
 * login survive an app restart, and the daily water count, which is per-day
 * device state rather than account history.
 */
@Singleton
class UserPreferencesDataStore @Inject constructor(
    private val context: Context,
) {
    private object Keys {
        val ONBOARDING_SEEN = booleanPreferencesKey("onboarding_seen")
        val LANGUAGE = stringPreferencesKey("language")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val SESSION_UID = stringPreferencesKey("session_uid")
        val NOTIFICATIONS_ENABLED = booleanPreferencesKey("notifications_enabled")

        fun waterFor(date: LocalDate) = intPreferencesKey("water_${date}")
    }

    val onboardingSeen: Flow<Boolean> =
        context.dataStore.data.map { it[Keys.ONBOARDING_SEEN] ?: false }

    val language: Flow<AppLanguage> =
        context.dataStore.data.map { AppLanguage.fromTag(it[Keys.LANGUAGE] ?: "en") }

    val themeMode: Flow<ThemeMode> =
        context.dataStore.data.map { ThemeMode.fromName(it[Keys.THEME_MODE]) }

    /**
     * The signed-in account id. The flow emits nothing until the (tiny) prefs
     * file has been read off disk, so anything combining it naturally waits for
     * the session to be restored instead of seeing a false "signed out".
     */
    val sessionUid: Flow<String?> =
        context.dataStore.data.map { it[Keys.SESSION_UID] }

    val notificationsEnabled: Flow<Boolean> =
        context.dataStore.data.map { it[Keys.NOTIFICATIONS_ENABLED] ?: true }

    /** Water glasses logged today; resets by construction because the key is the date. */
    fun waterGlasses(date: LocalDate): Flow<Int> =
        context.dataStore.data.map { it[Keys.waterFor(date)] ?: 0 }

    suspend fun setOnboardingSeen(seen: Boolean) {
        context.dataStore.edit { it[Keys.ONBOARDING_SEEN] = seen }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.dataStore.edit { it[Keys.THEME_MODE] = mode.name }
    }

    suspend fun setLanguage(language: AppLanguage) {
        context.dataStore.edit { it[Keys.LANGUAGE] = language.tag }
    }

    suspend fun setSessionUid(uid: String?) {
        context.dataStore.edit { prefs ->
            if (uid == null) prefs.remove(Keys.SESSION_UID) else prefs[Keys.SESSION_UID] = uid
        }
    }

    suspend fun setNotificationsEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.NOTIFICATIONS_ENABLED] = enabled }
    }

    suspend fun setWaterGlasses(date: LocalDate, glasses: Int) {
        context.dataStore.edit { it[Keys.waterFor(date)] = glasses.coerceAtLeast(0) }
    }
}
