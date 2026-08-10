package com.reps.app.feature.nutrition.assistant

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.res.stringResource

/** Who wrote a line of the transcript. */
enum class ChatAuthor { USER, ASSISTANT }

/**
 * One line of the transcript.
 *
 * Text reaches a message one of two ways, so the model carries both: whatever
 * the user typed arrives as a [literal] string, while every canned line is a
 * string resource and gets translated with the rest of the app. Holding the
 * resource id here rather than resolving it up front is what keeps the
 * ViewModel free of a Context - the UI resolves it at draw time.
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
 * A tap-to-send prompt on the welcome screen. Each one is paired with the reply
 * it produces, so the demo answers a tapped suggestion sensibly rather than
 * keyword-matching the text - matching would only ever work in English, and the
 * real assistant replaces this wholesale anyway.
 */
@Immutable
data class Suggestion(
    @param:StringRes val labelRes: Int,
    @param:StringRes val replyRes: Int,
)

/** A past conversation, as shown in the history list and reopened in the chat. */
@Immutable
data class Conversation(
    val id: String,
    @param:StringRes val titleRes: Int,
    @param:StringRes val timeRes: Int,
    val messages: List<ChatMessage>,
)

@Immutable
data class AssistantUiState(
    val messages: List<ChatMessage> = emptyList(),
    /** True while the mock reply is pending, which is what shows the typing dots. */
    val responding: Boolean = false,
    val conversations: List<Conversation> = emptyList(),
) {
    /**
     * The welcome state holds until the first message lands. It is deliberately
     * tied to the transcript being empty rather than to a separate flag, so
     * "New Chat" only has to clear the messages.
     */
    val showWelcome: Boolean get() = messages.isEmpty() && !responding
}
