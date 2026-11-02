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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
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
class NutritionAssistantViewModel(
    private val assistant: NutritionAssistantRepository,
    private val history: AssistantConversationRepository,
    private val userRepository: UserRepository,
    /** Becomes `viewModelScope`; tests pass an unconfined one to drive turns deterministically. */
    scope: CoroutineScope,
) : ViewModel(scope) {

    /** The same scope `viewModelScope` would create by default; cancelled when the ViewModel is cleared. */
    @Inject constructor(
        assistant: NutritionAssistantRepository,
        history: AssistantConversationRepository,
        userRepository: UserRepository,
    ) : this(assistant, history, userRepository, CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate))

    /** The conversation currently on screen. Empty means the welcome state. */
    private val transcript = MutableStateFlow<List<ChatMessage>>(emptyList())

    private val phase = MutableStateFlow(ReplyPhase.IDLE)

    private val restoredDraft = MutableStateFlow<RestoredDraft?>(null)

    /**
     * Which turn currently owns [phase]. A turn only writes its outcome back while
     * it is still the active one, so an abandoned reply (New Chat, opening a saved
     * chat) can never flip a newer turn's state. A counter rather than a Job
     * comparison because `viewModelScope` dispatches immediately: a reply that
     * completes without suspending finishes before `launch` even returns.
     */
    private var activeTurn = 0L

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
        phase,
        history.observeConversations(),
        restoredDraft,
    ) { messages, currentPhase, conversations, draft ->
        AssistantUiState(
            messages = messages,
            phase = currentPhase,
            conversations = conversations,
            restoredDraft = draft,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AssistantUiState(),
    )

    /**
     * Sends what the user typed. Returns whether it was accepted, so the composer
     * only clears text that actually went out: blank input, or a tap while a
     * reply is still pending, leaves the field as it was.
     */
    fun send(text: String): Boolean {
        val trimmed = text.trim()
        if (trimmed.isEmpty() || phase.value == ReplyPhase.SENDING) return false
        val prompt = ChatMessage(id = ++nextId, author = ChatAuthor.USER, literal = trimmed)
        transcript.update { it + prompt }
        startReply(prompt)
        return true
    }

    /** The composer has taken the restored text; stop offering it. */
    fun consumeRestoredDraft(id: Long) {
        restoredDraft.update { if (it?.id == id) null else it }
    }

    /**
     * Clears the transcript back to the welcome state. Saved history is kept:
     * a new chat replaces what is on screen, not what has been stored.
     */
    fun newChat() {
        abandonTurn()
        conversationId = null
        transcript.value = emptyList()
    }

    /** Opens a saved conversation in the chat, replacing whatever is on screen. */
    fun openConversation(id: String) {
        abandonTurn()
        viewModelScope.launch {
            val conversation = history.observeConversations().first()
                .firstOrNull { it.id == id } ?: return@launch
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
            abandonTurn()
            conversationId = null
            transcript.value = emptyList()
        }
    }

    /** Drops any pending reply and hands the composer back immediately. */
    private fun abandonTurn() {
        activeTurn++
        replyJob?.cancel()
        replyJob = null
        phase.value = ReplyPhase.IDLE
        restoredDraft.value = null
    }

    /**
     * Runs one turn. The phase is released in `finally`, so every way out - a
     * reply, an error result, an exception, a cancellation - hands the composer
     * back. (Previously a successful reply returned early past the reset, which
     * left Send disabled for good after the first answer.)
     */
    private fun startReply(prompt: ChatMessage) {
        val promptText = prompt.literal.orEmpty()
        val exchanges = transcript.value
            .dropLast(1) // the prompt itself is passed separately
            .map { AssistantExchange(fromUser = it.author == ChatAuthor.USER, text = it.literal.orEmpty()) }
            .filter { it.text.isNotEmpty() }

        replyJob?.cancel()
        val turn = ++activeTurn
        phase.value = ReplyPhase.SENDING
        restoredDraft.value = null
        replyJob = viewModelScope.launch {
            var failed = false
            var answered = false
            try {
                when (val result = assistant.understand(exchanges, promptText, conversationId)) {
                    is AssistantResult.Failure -> {
                        failed = true
                        failWith(result.error, promptText)
                    }

                    is AssistantResult.Success -> {
                        answered = true
                        result.value.conversationId?.let { conversationId = it }
                        finishTurn(promptText, result.value.message)

                        // Draft ready means the understanding agent got everything
                        // it needs - hand the verified figures over to the coach.
                        val draft = result.value.draft
                        if (draft.readyForAnalysis) {
                            when (val coached = assistant.analyseAndCoach(draft, currentGoal())) {
                                is AssistantResult.Failure -> failWith(coached.error, retryText = null)
                                is AssistantResult.Success -> finishTurn(null, coached.value.coaching)
                            }
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                failed = true
                if (turn == activeTurn) failWith(AssistantError.Unknown, retryText = promptText.takeUnless { answered })
            } finally {
                if (turn == activeTurn) {
                    phase.value = if (failed) ReplyPhase.FAILED else ReplyPhase.IDLE
                    replyJob = null
                }
            }
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

    /**
     * Errors are transient UI, shown as a bubble and never written to history.
     * When the turn's own question failed, its text goes back to the composer so
     * one tap retries it ([retryText] null for a follow-up stage that failed after
     * the question was already answered).
     */
    private fun failWith(error: AssistantError, retryText: String?) {
        transcript.update {
            it + ChatMessage(id = ++nextId, author = ChatAuthor.ASSISTANT, textRes = error.labelRes())
        }
        if (retryText != null && error.isRetryable) {
            restoredDraft.value = RestoredDraft(id = ++nextId, text = retryText)
        }
    }

    @StringRes
    private fun AssistantError.labelRes(): Int = when (this) {
        AssistantError.Network -> R.string.assistant_error_network
        AssistantError.Timeout -> R.string.assistant_error_timeout
        AssistantError.RateLimited -> R.string.assistant_error_rate_limited
        AssistantError.ModelUnavailable -> R.string.assistant_error_unavailable
        AssistantError.NotConfigured -> R.string.assistant_error_not_configured
        AssistantError.MessageTooLong -> R.string.assistant_error_too_long
        else -> R.string.assistant_error_generic
    }

    private suspend fun currentGoal(): Goal =
        userRepository.observeUser().first()?.goal ?: Goal.MAINTAIN
}
