package com.reps.app.data.auth

import com.reps.app.data.datastore.UserPreferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The signed-in account, in one place.
 *
 * The source of truth is the DataStore `session_uid` key, so a login survives
 * process death. [uidFlow] is that key as a flow - it stays silent until the
 * prefs file has been read, which is exactly what the splash screen wants:
 * it cannot observe "signed out" before restore has finished.
 *
 * [currentUid] is a synchronously readable mirror of the same value, kept for
 * write paths (repositories stamping rows with the owner id). It is warmed by
 * a background collector at injection and updated eagerly on every write, so
 * by the time any UI can trigger a repository write it is correct.
 */
@Singleton
class UserSession @Inject constructor(
    private val preferences: UserPreferencesDataStore,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Volatile
    private var cachedUid: String? = null

    init {
        scope.launch { preferences.sessionUid.collect { cachedUid = it } }
    }

    val uidFlow: Flow<String?> get() = preferences.sessionUid

    val currentUid: String? get() = cachedUid

    suspend fun signIn(uid: String) {
        cachedUid = uid
        preferences.setSessionUid(uid)
    }

    suspend fun signOut() {
        cachedUid = null
        preferences.setSessionUid(null)
    }
}
