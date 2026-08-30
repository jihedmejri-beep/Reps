package com.reps.app.feature.nutrition.assistant

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.reps.app.R
import com.reps.app.domain.model.AssistantConversation
import com.reps.app.domain.model.AssistantError
import com.reps.app.domain.model.AssistantMessage
import com.reps.app.domain.model.Goal
import com.reps.app.domain.repository.AssistantConversationRepository
import com.reps.app.domain.repository.AssistantExchange
import com.reps.app.domain.repository.AssistantResult
import com.reps.app.domain.repository.NutritionAssistantRepository
import com.reps.app.domain.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

/** History rows carry a plain-text date; titles are cut rather than wrapped. */
private const val MaxTitleLength = 48

/**
 * Drives the assistant chat against the real seams.
 *
 * Replies come from [NutritionAssistantRepository] - today the Hilt-bound fake,
 * tomorrow the deployed agents - following the documented pipeline: [send]
 * calls `understand`, and when the returned draft reports ready it follows with
 * `analyseAndCoach`. Failures surface as an assistant bubble, never a crash.
 *
 * Every finished turn is written through [AssistantConversationRepository], so
 * the history screen shows what actually happened rather than seeded demo
 * content. Swapping either implementation touches only `RepositoryModule`.
 */
@HiltViewModel
class NutritionAssistantViewModel @Inject constructor(
    private val assistant: NutritionAssistantRepository,
    private val history: AssistantConversationRepository,
    private val userRepository: UserRepository,
) : ViewModel() {

    /** The conversation currently on screen. Empty means the welcome state. */
    private val transcript = MutableStateFlow<List<ChatMessage>>(emptyList())

    private val responding = MutableStateFlow(false)

    /**
     * The stored conversation turns are being appended to, or null when the
     * next send should start a fresh one ("New Chat" clears this without
     * deleting what came before).
     */
    private var conversationId: String? = null

    /** Ids for live bubbles; bumped past loaded ones so list keys never clash. */
    private var nextId = 0L

    /** Held so a pending reply can be dropped when the conversation swaps out. */
    private var replyJob: Job? = null

    val uiState: StateFlow<AssistantUiState> = combine(
        transcript,
        responding,
        history.observeConversations(),
    ) { messages, busy, conversations ->
        AssistantUiState(
            messages = messages,
            responding = busy,
            conversations = conversations,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AssistantUiState(),
    )

    /** Sends what the user typed. Blank input is ignored rather than echoed. */
    fun send(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty() || responding.value) return
        val prompt = ChatMessage(id = ++nextId, author = ChatAuthor.USER, literal = trimmed)
        transcript.update { it + prompt }
        startReply(prompt)
    }

    /**
     * Clears the transcript back to the welcome state. Saved history is kept:
     * a new chat replaces what is on screen, not what has been stored.
     */
    fun newChat() {
        replyJob?.cancel()
        responding.value = false
        conversationId = null
        transcript.value = emptyList()
    }

    /** Opens a saved conversation in the chat, replacing whatever is on screen. */
    fun openConversation(id: String) {
        replyJob?.cancel()
        viewModelScope.launch {
            val conversation = history.observeConversations().first()
                .firstOrNull { it.id == id } ?: return@launch
            responding.value = false
            conversationId = id
            nextId = maxOf(nextId, conversation.messages.maxOfOrNull { it.id } ?: 0L)
            transcript.value = conversation.messages.map { stored ->
                ChatMessage(
                    id = stored.id,
                    author = if (stored.fromUser) ChatAuthor.USER else ChatAuthor.ASSISTANT,
                    literal = stored.text,
                )
            }
        }
    }

    /** Deletes a saved conversation. If it is open on screen, close it too. */
    fun deleteConversation(id: String) {
        viewModelScope.launch { history.delete(id) }
        if (conversationId == id) {
            conversationId = null
            transcript.value = emptyList()
        }
    }

    private fun startReply(prompt: ChatMessage) {
        responding.value = true
        val exchanges = transcript.value
            .dropLast(1) // the prompt itself is passed separately
            .map { AssistantExchange(fromUser = it.author == ChatAuthor.USER, text = it.literal.orEmpty()) }
            .filter { it.text.isNotEmpty() }

        replyJob?.cancel()
        replyJob = viewModelScope.launch {
            when (val result = assistant.understand(exchanges, prompt.literal.orEmpty(), conversationId)) {
                is AssistantResult.Failure -> failWith(result.error)

                is AssistantResult.Success -> {
                    result.value.conversationId?.let { conversationId = it }
                    finishTurn(prompt.literal.orEmpty(), result.value.message)

                    // Draft ready means the understanding agent got everything
                    // it needs - hand the verified figures over to the coach.
                    val draft = result.value.draft
                    if (!draft.readyForAnalysis) return@launch

                    when (val coached = assistant.analyseAndCoach(draft, currentGoal())) {
                        is AssistantResult.Failure -> failWith(coached.error)

                        is AssistantResult.Success ->
                            finishTurn(null, coached.value.coaching)
                    }
                }
            }
            responding.value = false
        }
    }

    /**
     * Shows the assistant's line and, when the user spoke this turn, writes the
     * pair into history under the current conversation.
     */
    private suspend fun finishTurn(userText: String?, assistantText: String) {
        transcript.update {
            it + ChatMessage(id = ++nextId, author = ChatAuthor.ASSISTANT, literal = assistantText)
        }
        if (userText != null) saveToHistory(userText, assistantText)
    }

    private suspend fun saveToHistory(userText: String, assistantText: String) {
        val id = conversationId ?: UUID.randomUUID().toString().also { conversationId = it }
        val existing = history.observeConversations().first().firstOrNull { it.id == id }
        val messages = buildList {
            addAll(existing?.messages ?: emptyList())
            add(AssistantMessage(id = ++nextId, fromUser = true, text = userText))
            add(AssistantMessage(id = ++nextId, fromUser = false, text = assistantText))
        }
        history.upsert(
            AssistantConversation(
                id = id,
                title = existing?.title ?: userText.lineSequence().first().take(MaxTitleLength),
                updatedAt = System.currentTimeMillis(),
                messages = messages,
            ),
        )
    }

    /** Errors are transient UI, shown as a bubble and never written to history. */
    private fun failWith(error: AssistantError) {
        transcript.update {
            it + ChatMessage(id = ++nextId, author = ChatAuthor.ASSISTANT, textRes = error.labelRes())
        }
    }

    @StringRes
    private fun AssistantError.labelRes(): Int = when (this) {
        AssistantError.Network -> R.string.assistant_error_network
        else -> R.string.assistant_error_generic
    }

    private suspend fun currentGoal(): Goal =
        userRepository.observeUser().first()?.goal ?: Goal.MAINTAIN
}
