package com.reps.app.domain.model

/**
 * One entry in the assistant conversation.
 *
 * [Coaching] carries prose and a reference to the analysis it is interpreting.
 * The two are kept apart on purpose: every figure the UI renders comes from
 * [Coaching.analysis], never from parsing [Coaching.message]. The coach can
 * therefore describe the numbers but has no path to change them.
 */
sealed interface AssistantTurn {

    val id: String

    data class User(
        override val id: String,
        val text: String,
    ) : AssistantTurn

    /** A question or acknowledgement from the Meal Understanding Agent. */
    data class Understanding(
        override val id: String,
        val message: String,
        val draft: MealDraft,
    ) : AssistantTurn

    /** Verified nutrition plus the coach's reading of it. */
    data class Coaching(
        override val id: String,
        val message: String,
        val analysis: NutritionAnalysis,
    ) : AssistantTurn

    data class Failed(
        override val id: String,
        val reason: AssistantError,
    ) : AssistantTurn
}

/**
 * Failures the UI needs to tell apart. The assistant depends on two external
 * services, and "USDA is down" deserves a different message from "you are
 * offline" - retrying only helps for some of these.
 */
enum class AssistantError {
    Network,
    NotSignedIn,
    RateLimited,
    ModelUnavailable,
    FoodDatabaseUnavailable,
    Unknown,
    ;

    val isRetryable: Boolean
        get() = this != NotSignedIn
}
