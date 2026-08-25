package com.reps.app.domain.repository

import com.reps.app.domain.model.AssistantConversation
import kotlinx.coroutines.flow.Flow

/**
 * Where past assistant chats live.
 *
 * Deliberately separate from [NutritionAssistantRepository]: that one is the
 * AI agent and gets swapped when a real backend lands, while this one is plain
 * storage the app owns today. The in-memory implementation ships with the app;
 * making history durable later (Room, Firestore) is a new implementation bound
 * in [com.reps.app.di.RepositoryModule] - no ViewModel or screen changes.
 */
interface AssistantConversationRepository {

    /** Saved conversations, newest first. */
    fun observeConversations(): Flow<List<AssistantConversation>>

    /** Inserts or replaces a conversation with the same [AssistantConversation.id]. */
    suspend fun upsert(conversation: AssistantConversation)

    suspend fun delete(id: String)
}
