package com.reps.app.domain.repository

import com.reps.app.domain.model.User
import kotlinx.coroutines.flow.Flow

interface UserRepository {
    fun observeUser(): Flow<User?>
    suspend fun updateUser(user: User)
}
