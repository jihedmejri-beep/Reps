package com.reps.app.data.user

import androidx.room.withTransaction
import com.reps.app.data.auth.UserSession
import com.reps.app.data.user.db.AssistantConversationDao
import com.reps.app.data.user.db.AssistantConversationEntity
import com.reps.app.data.user.db.AssistantMessageEntity
import com.reps.app.data.user.db.UserDataDatabase
import com.reps.app.data.user.db.toDomain
import com.reps.app.domain.model.AssistantConversation
import com.reps.app.domain.repository.AssistantConversationRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** Oldest conversations beyond this are dropped, newest first - as before, now enforced in the table too. */
private const val MaxConversations = 20

/**
 * Durable assistant chat history.
 *
 * Same contract the in-memory store had - sorted newest-first, capped at
 * [MaxConversations], upsert-by-id - except the rows now live in Room and
 * survive process death. The AI itself is untouched; this is plain storage.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class RoomAssistantConversationRepository @Inject constructor(
    private val session: UserSession,
    private val database: UserDataDatabase,
) : AssistantConversationRepository {

    private val dao: AssistantConversationDao get() = database.assistantConversationDao()

    override fun observeConversations(): Flow<List<AssistantConversation>> =
        session.uidFlow.flatMapLatest { uid ->
            if (uid == null) {
                flowOf(emptyList())
            } else {
                dao.observeConversations(uid).map { rows -> rows.map { it.toDomain() } }
            }
        }

    override suspend fun upsert(conversation: AssistantConversation) {
        val uid = session.currentUid ?: return
        val entity = AssistantConversationEntity(
            id = conversation.id,
            uid = uid,
            title = conversation.title,
            updatedAtMs = conversation.updatedAt,
        )
        val messages = conversation.messages.mapIndexed { index, message ->
            AssistantMessageEntity(
                conversationId = conversation.id,
                fromUser = message.fromUser,
                text = message.text,
                sortIndex = index.toLong(),
            )
        }
        database.withTransaction {
            // Replace-in-place; the FK cascade clears stale message rows with the old parent.
            dao.deleteConversationRow(conversation.id)
            dao.insertConversation(entity)
            if (messages.isNotEmpty()) dao.insertMessages(messages)
            // Enforce the cap inside the same transaction.
            dao.idsBeyondNewest(uid, MaxConversations).forEach { dao.deleteConversationRow(it) }
        }
    }

    override suspend fun delete(id: String) {
        val uid = session.currentUid ?: return
        dao.deleteConversationRowScoped(id, uid)
    }
}
