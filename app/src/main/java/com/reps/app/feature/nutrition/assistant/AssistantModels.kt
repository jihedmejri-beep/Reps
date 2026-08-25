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

@Immutable
data class AssistantUiState(
    val messages: List<ChatMessage> = emptyList(),
    /** True while a reply is pending, which is what shows the typing dots. */
    val responding: Boolean = false,
    /** Saved chats, newest first, straight from the history repository. */
    val conversations: List<AssistantConversation> = emptyList(),
) {
    /**
     * The welcome state holds until the first message lands. It is deliberately
     * tied to the transcript being empty rather than to a separate flag, so
     * "New Chat" only has to clear the messages.
     */
    val showWelcome: Boolean get() = messages.isEmpty() && !responding
}
