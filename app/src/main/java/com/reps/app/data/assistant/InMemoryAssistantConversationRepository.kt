package com.reps.app.data.assistant

import com.reps.app.domain.model.AssistantConversation
import com.reps.app.domain.repository.AssistantConversationRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

/** Oldest conversations beyond this are dropped, newest first. */
private const val MaxConversations = 20

/**
 * Process-lifetime history store: real conversations saved as they happen,
 * held in memory like every other user-data store in the app while the backend
 * is being built.
 *
 * It already behaves like a durable one from the caller's side - sorted,
 * capped, upsert-by-id - so swapping in Room or Firestore later is an
 * implementation change only.
 */
@Singleton
class InMemoryAssistantConversationRepository @Inject constructor() :
    AssistantConversationRepository {

    private val conversations = MutableStateFlow<List<AssistantConversation>>(emptyList())

    override fun observeConversations(): Flow<List<AssistantConversation>> =
        conversations.asStateFlow()

    override suspend fun upsert(conversation: AssistantConversation) {
        conversations.update { current ->
            (listOf(conversation) + current.filterNot { it.id == conversation.id })
                .sortedByDescending { it.updatedAt }
                .take(MaxConversations)
        }
    }

    override suspend fun delete(id: String) {
        conversations.update { current -> current.filterNot { it.id == id } }
    }
}
