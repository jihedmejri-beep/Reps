package com.reps.app.feature.nutrition.assistant

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * How long the typing dots run before the reply lands. Long enough to read as
 * the assistant thinking, short enough not to feel like a stall.
 */
private const val ThinkingDelayMs = 900L

/**
 * Holds the conversation.
 *
 * Everything is in memory and the replies come from [MockAssistantData] - there
 * is no repository, no network and no nutrition logic behind this. The seam for
 * the real assistant is [reply]: give this class a repository, make that
 * function suspend on it, and nothing above changes.
 */
@HiltViewModel
class NutritionAssistantViewModel @Inject constructor() : ViewModel() {

    private val _uiState = MutableStateFlow(
        AssistantUiState(conversations = MockAssistantData.conversations()),
    )
    val uiState = _uiState.asStateFlow()

    /** Live messages count up; the seeded history counts down. See MockAssistantData. */
    private var nextId = 0L

    /** Held so a pending reply can be dropped when the conversation is swapped out. */
    private var replyJob: Job? = null

    /** Sends what the user typed. Blank input is ignored rather than echoed. */
    fun send(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        submit(ChatMessage(id = ++nextId, author = ChatAuthor.USER, literal = trimmed))
    }

    /** Sends a tapped welcome suggestion as though the user had typed it. */
    fun send(suggestion: Suggestion) {
        submit(ChatMessage(id = ++nextId, author = ChatAuthor.USER, textRes = suggestion.labelRes))
    }

    /**
     * Clears the transcript back to the welcome state. The history is kept: a
     * new chat replaces what is on screen, not what has been saved.
     */
    fun newChat() {
        replyJob?.cancel()
        _uiState.update { it.copy(messages = emptyList(), responding = false) }
    }

    /** Opens a past conversation in the chat, replacing whatever is on screen. */
    fun openConversation(id: String) {
        val conversation = _uiState.value.conversations.firstOrNull { it.id == id } ?: return
        replyJob?.cancel()
        _uiState.update { it.copy(messages = conversation.messages, responding = false) }
    }

    private fun submit(prompt: ChatMessage) {
        if (_uiState.value.responding) return
        val turn = _uiState.value.messages.count { it.author == ChatAuthor.USER }
        _uiState.update { it.copy(messages = it.messages + prompt, responding = true) }
        replyJob?.cancel()
        replyJob = viewModelScope.launch { reply(prompt, turn) }
    }

    private suspend fun reply(prompt: ChatMessage, turn: Int) {
        delay(ThinkingDelayMs)
        val reply = ChatMessage(
            id = ++nextId,
            author = ChatAuthor.ASSISTANT,
            textRes = MockAssistantData.replyFor(prompt, turn),
        )
        _uiState.update { it.copy(messages = it.messages + reply, responding = false) }
    }
}
