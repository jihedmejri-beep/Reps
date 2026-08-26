package com.reps.app.data.user

import com.reps.app.data.auth.UserSession
import com.reps.app.data.datastore.UserPreferencesDataStore
import com.reps.app.data.user.db.UserProfileDao
import com.reps.app.data.user.db.toDomain
import com.reps.app.data.user.db.toProfileEntity
import com.reps.app.domain.model.User
import com.reps.app.domain.repository.UserRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The signed-in user's profile, read and written against the local database.
 *
 * The profile is the single home of streak state; the workout repository owns
 * recomputing it, this one just persists whatever it hands over. Language is
 * mirrored into DataStore on every save because the exercise catalogue reads
 * its language from there - one preference, two consumers.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class LocalUserRepository @Inject constructor(
    private val session: UserSession,
    private val profileDao: UserProfileDao,
    private val preferences: UserPreferencesDataStore,
) : UserRepository {

    override fun observeUser(): Flow<User?> =
        session.uidFlow.flatMapLatest { uid ->
            if (uid == null) flowOf(null) else profileDao.observe(uid).map { it?.toDomain() }
        }

    override suspend fun updateUser(user: User) {
        val uid = session.currentUid ?: return
        if (user.uid != uid) return
        profileDao.upsert(user.toProfileEntity())
        preferences.setLanguage(user.language)
    }
}
