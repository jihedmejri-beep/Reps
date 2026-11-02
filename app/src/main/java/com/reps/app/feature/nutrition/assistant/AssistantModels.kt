package com.reps.app.feature.nutrition.assistant

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.res.stringResource
import com.reps.app.R
import com.reps.app.domain.model.AssistantConversation

/** Who wrote a line of the transcript. */
enum class ChatAuthor { USER, ASSISTANT }

/**
 * One line of the transcript.
 *
 * Text reaches a message one of two ways, so the model carries both: whatever
 * the user typed and whatever the agent replied arrive as [literal] strings,
 * while an error bubble is a string resource resolved at draw time - the
 * ViewModel stays free of a Context.
 */
@Immutable
data class ChatMessage(
    val id: Long,
    val author: ChatAuthor,
    val literal: String? = null,
    @param:StringRes val textRes: Int? = null,
)

/** Resolves whichever of the two text sources this message carries. */
@Composable
fun ChatMessage.text(): String = literal ?: textRes?.let { stringResource(it) }.orEmpty()

/**
 * A tap-to-send prompt on the welcome screen. The label is what gets sent -
 * the ViewModel treats it exactly like typed text, so no reply is coupled to
 * it here.
 */
@Immutable
data class Suggestion(
    @param:StringRes val labelRes: Int,
)

/** The prompts offered on the welcome state. */
val AssistantSuggestions = listOf(
    Suggestion(R.string.assistant_suggestion_meal),
    Suggestion(R.string.assistant_suggestion_calories),
    Suggestion(R.string.assistant_suggestion_protein),
    Suggestion(R.string.assistant_suggestion_eat),
)

/**
 * Where the current turn stands. Only [SENDING] blocks the composer; a turn
 * always leaves it - to [IDLE] on a reply, [FAILED] on an error - including
 * when the request throws, times out or is cancelled.
 */
enum class ReplyPhase { IDLE, SENDING, FAILED }

/**
 * Text handed back to the composer after a failed turn, so the user can retry
 * without retyping. [id] makes two identical failures distinct events.
 */
@Immutable
data class RestoredDraft(val id: Long, val text: String)

@Immutable
data class AssistantUiState(
    val messages: List<ChatMessage> = emptyList(),
    val phase: ReplyPhase = ReplyPhase.IDLE,
    /** Saved chats, newest first, straight from the history repository. */
    val conversations: List<AssistantConversation> = emptyList(),
    val restoredDraft: RestoredDraft? = null,
) {
    /** True while a reply is pending, which is what shows the typing dots and holds Send. */
    val responding: Boolean get() = phase == ReplyPhase.SENDING

    /**
     * The welcome state holds until the first message lands. It is deliberately
     * tied to the transcript being empty rather than to a separate flag, so
     * "New Chat" only has to clear the messages.
     */
    val showWelcome: Boolean get() = messages.isEmpty() && !responding
}
