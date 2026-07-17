package com.reps.app.domain.repository

import kotlinx.coroutines.flow.Flow

sealed interface AuthResult {
    data object Success : AuthResult
    data class Failure(val message: String?) : AuthResult
}

interface AuthRepository {
    /** Emits the signed-in uid, or null when signed out. */
    fun observeAuthState(): Flow<String?>
    val currentUid: String?

    suspend fun signIn(email: String, password: String): AuthResult
    suspend fun signUp(name: String, email: String, password: String): AuthResult
    suspend fun signInWithGoogle(idToken: String): AuthResult
    suspend fun sendPasswordReset(email: String): AuthResult
    suspend fun signOut()
}
