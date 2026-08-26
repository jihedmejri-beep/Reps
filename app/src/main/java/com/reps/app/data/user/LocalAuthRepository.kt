package com.reps.app.data.user

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import com.reps.app.R
import com.reps.app.data.auth.PasswordHasher
import com.reps.app.data.auth.UserSession
import com.reps.app.data.datastore.UserPreferencesDataStore
import com.reps.app.data.user.db.UserAccountDao
import com.reps.app.data.user.db.UserAccountEntity
import com.reps.app.data.user.db.UserProfileDao
import com.reps.app.data.user.db.UserProfileEntity
import com.reps.app.domain.model.AppLanguage
import com.reps.app.domain.repository.AuthRepository
import com.reps.app.domain.repository.AuthResult
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Real credential handling backed entirely by the device.
 *
 * Accounts live in the local `user_accounts` table with PBKDF2-hashed
 * passwords; a successful sign-in writes the account id into DataStore so the
 * session survives restarts and every other repository can stamp rows with it.
 * Email is normalised to lowercase at both ends of every lookup.
 *
 * Google sign-in and server-side password reset genuinely require a cloud
 * identity provider, so they report an honest failure rather than pretending.
 */
@Singleton
class LocalAuthRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val accountDao: UserAccountDao,
    private val profileDao: UserProfileDao,
    private val preferences: UserPreferencesDataStore,
    private val session: UserSession,
    private val passwordHasher: PasswordHasher,
) : AuthRepository {

    override fun observeAuthState(): Flow<String?> = session.uidFlow

    override val currentUid: String? get() = session.currentUid

    override suspend fun signIn(email: String, password: String): AuthResult {
        val account = accountDao.findByEmail(normalise(email))
            ?: return AuthResult.Failure(context.getString(R.string.error_auth_invalid_credentials))
        if (!passwordHasher.verify(password, account.passwordSalt, account.passwordHash)) {
            return AuthResult.Failure(context.getString(R.string.error_auth_invalid_credentials))
        }
        session.signIn(account.id)
        return AuthResult.Success
    }

    override suspend fun signUp(name: String, email: String, password: String): AuthResult {
        val normalised = normalise(email)
        if (accountDao.findByEmail(normalised) != null) {
            return AuthResult.Failure(context.getString(R.string.error_email_taken))
        }

        val uid = UUID.randomUUID().toString()
        val salt = passwordHasher.newSalt()
        val account = UserAccountEntity(
            id = uid,
            email = normalised,
            passwordHash = passwordHasher.hash(password, salt),
            passwordSalt = salt,
            createdAtMs = System.currentTimeMillis(),
        )
        try {
            accountDao.insert(account)
        } catch (_: SQLiteConstraintException) {
            // Lost a race against a concurrent sign-up with the same email.
            return AuthResult.Failure(context.getString(R.string.error_email_taken))
        }

        profileDao.upsert(
            UserProfileEntity(
                uid = uid,
                name = name.trim(),
                email = normalised,
                sexName = null,
                heightCm = null,
                age = null,
                goalName = com.reps.app.domain.model.Goal.MAINTAIN.name,
                unitsName = com.reps.app.domain.model.UnitSystem.METRIC.name,
                languageTag = AppLanguage.fromTag(currentLanguageTag()).tag,
                streakCount = 0,
                lastWorkoutDateIso = null,
            ),
        )

        session.signIn(uid)
        return AuthResult.Success
    }

    /**
     * Needs a real identity provider (Credential Manager plus a web client
     * id); there is no offline equivalent. The login screen surfaces this in
     * its existing form-error slot.
     */
    override suspend fun signInWithGoogle(idToken: String): AuthResult =
        AuthResult.Failure(context.getString(R.string.error_google_signin_unavailable))

    /** Same story as Google sign-in: reset mail needs something to send it with. */
    override suspend fun sendPasswordReset(email: String): AuthResult =
        AuthResult.Failure(context.getString(R.string.error_password_reset_unavailable))

    override suspend fun signOut() {
        session.signOut()
    }

    /** The device's active catalogue language, so a new profile starts consistent with it. */
    private suspend fun currentLanguageTag(): String = preferences.language.first().tag

    private fun normalise(email: String): String = email.trim().lowercase()
}
