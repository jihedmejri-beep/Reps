package com.reps.app.domain.model

/**
 * What the Meal Understanding Agent extracted from the conversation so far.
 *
 * Deliberately carries no nutrition figures. The agent normalises names and
 * quantities; the backend is the only thing that turns them into calories, so
 * there is no field here for a calorie value to be smuggled into. That is the
 * boundary the two-agent split exists to enforce, expressed as a type rather
 * than as a line in a prompt.
 */
data class MealDraft(
    val mealName: String? = null,
    val ingredients: List<DraftIngredient> = emptyList(),
    /** The agent's own call on whether the backend has enough to work with. */
    val readyForAnalysis: Boolean = false,
    val confidence: Double = 0.0,
    /** Empty once [readyForAnalysis] is true; otherwise what still needs asking. */
    val followUpQuestions: List<String> = emptyList(),
    /** Things the agent found genuinely ambiguous, surfaced rather than guessed. */
    val ambiguities: List<String> = emptyList(),
)

data class DraftIngredient(
    /** Normalised toward USDA vocabulary, e.g. "Chicken, breast, roasted". */
    val name: String = "",
    /** What the user actually typed, kept so the UI can show their own words. */
    val rawText: String = "",
    /**
     * Null when the user never said how much. The agent is not permitted to
     * invent a portion - it asks instead.
     */
    val grams: Double? = null,
    /** The user's own phrasing of the amount, e.g. "two cups". */
    val quantityText: String? = null,
    val cookingMethod: String? = null,
) {
    val hasQuantity: Boolean get() = grams != null && grams > 0.0
}
