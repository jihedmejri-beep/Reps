package com.reps.app.domain.model

/**
 * One line of an assistant conversation, as stored in history.
 *
 * Text is always a literal string here - the user's own words or whatever the
 * agent replied. Localised content never reaches storage, so a conversation
 * reads back exactly as it was written regardless of the active language.
 */
data class AssistantMessage(
    val id: Long,
    val fromUser: Boolean,
    val text: String,
)

/**
 * A saved assistant chat.
 *
 * [title] is derived from the first thing the user sent; [updatedAt] is a
 * wall-clock epoch-milli value used only for ordering and labelling history
 * rows, never for logic.
 */
data class AssistantConversation(
    val id: String,
    val title: String,
    val updatedAt: Long,
    val messages: List<AssistantMessage>,
)
