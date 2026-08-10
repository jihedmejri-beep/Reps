package com.reps.app.feature.nutrition.assistant

import com.reps.app.R

/**
 * Stand-in content for the assistant interface.
 *
 * Everything here is presentation-layer demo copy with no nutrition logic behind
 * it: no lookup, no calculation, no network. It exists so the chat can be built
 * and reviewed end to end, and it is the single place to delete once a real
 * responder is wired in behind the ViewModel.
 */
object MockAssistantData {

    /** The prompts offered on the welcome state, each with the reply it draws. */
    val suggestions = listOf(
        Suggestion(R.string.assistant_suggestion_meal, R.string.assistant_reply_meal),
        Suggestion(R.string.assistant_suggestion_calories, R.string.assistant_reply_calories),
        Suggestion(R.string.assistant_suggestion_protein, R.string.assistant_reply_protein),
        Suggestion(R.string.assistant_suggestion_eat, R.string.assistant_reply_eat),
    )

    /**
     * Replies for anything typed by hand. Cycled by turn rather than matched on
     * the text, because keyword matching would only fire in English and would
     * read as broken in Arabic and French.
     */
    private val typedReplies = listOf(
        R.string.assistant_reply_meal_estimate,
        R.string.assistant_reply_balance,
        R.string.assistant_reply_more_detail,
    )

    /**
     * Ids for the seeded history count down from -1 so they can never collide
     * with the ids the ViewModel hands out for live messages, which count up
     * from 1. That keeps `key = { it.id }` in the transcript unambiguous when a
     * past conversation is reopened.
     */
    private var seedId = 0L
    private fun seed(author: ChatAuthor, textRes: Int) =
        ChatMessage(id = --seedId, author = author, textRes = textRes)

    /**
     * The history the screen opens with. Held in memory only - swapping this for
     * a stored list is a change to the ViewModel's source, not to the UI.
     */
    fun conversations(): List<Conversation> = listOf(
        Conversation(
            id = "breakfast",
            titleRes = R.string.assistant_conv_breakfast,
            timeRes = R.string.assistant_time_today,
            messages = listOf(
                seed(ChatAuthor.USER, R.string.assistant_conv_breakfast_q),
                seed(ChatAuthor.ASSISTANT, R.string.assistant_conv_breakfast_a),
            ),
        ),
        Conversation(
            id = "protein",
            titleRes = R.string.assistant_conv_protein,
            timeRes = R.string.assistant_time_yesterday,
            messages = listOf(
                seed(ChatAuthor.USER, R.string.assistant_suggestion_protein),
                seed(ChatAuthor.ASSISTANT, R.string.assistant_reply_protein),
            ),
        ),
        Conversation(
            id = "dinner",
            titleRes = R.string.assistant_conv_dinner,
            timeRes = R.string.assistant_time_this_week,
            messages = listOf(
                seed(ChatAuthor.USER, R.string.assistant_suggestion_eat),
                seed(ChatAuthor.ASSISTANT, R.string.assistant_reply_eat),
            ),
        ),
        Conversation(
            id = "post-workout",
            titleRes = R.string.assistant_conv_postworkout,
            timeRes = R.string.assistant_time_last_week,
            messages = listOf(
                seed(ChatAuthor.USER, R.string.assistant_conv_postworkout_q),
                seed(ChatAuthor.ASSISTANT, R.string.assistant_conv_postworkout_a),
            ),
        ),
    )

    /**
     * The reply a prompt draws: a tapped suggestion answers itself, anything
     * typed falls to the rotating pool. [turn] is the number of prompts already
     * sent, which is what keeps successive replies from repeating.
     */
    fun replyFor(prompt: ChatMessage, turn: Int): Int =
        suggestions.firstOrNull { it.labelRes == prompt.textRes }?.replyRes
            ?: typedReplies[turn.mod(typedReplies.size)]
}
