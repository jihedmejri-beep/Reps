package com.reps.app.domain.model

/**
 * Verified nutrition for one meal, computed by the backend from USDA
 * FoodData Central. This is the single source of truth for every number the
 * app displays - the Nutrition Coach Agent interprets it and never returns it.
 */
data class NutritionAnalysis(
    val mealName: String = "",
    val items: List<AnalysedItem> = emptyList(),
    val micros: Micros = Micros(),
    /** Ingredients USDA had no usable match for, so the totals exclude them. */
    val unmatched: List<String> = emptyList(),
    /** True when this meal was served from the backend's cache, not a fresh lookup. */
    val fromCache: Boolean = false,
) {
    val macros: Macros
        get() = items.fold(Macros()) { acc, item -> acc + item.macros }

    /**
     * Totals cover only what USDA matched. A partial result is still worth
     * showing, but the UI has to be able to say so.
     */
    val isPartial: Boolean get() = unmatched.isNotEmpty()
}

/**
 * One ingredient after the engine matched it and scaled it to the logged
 * portion. [foodItem] is the existing per-100g domain model, so an analysed
 * meal drops straight into [Meal] without a second representation.
 */
data class AnalysedItem(
    val foodItem: FoodItem = FoodItem(),
    /** USDA FoodData Central id, so a figure can be traced back to its source. */
    val fdcId: Long? = null,
    /** The USDA description that was matched, which may differ from the request. */
    val matchedDescription: String = "",
) {
    val macros: Macros
        get() = Macros(foodItem.calories, foodItem.protein, foodItem.carbs, foodItem.fat)
}

/** Micronutrient totals for a meal. Null means USDA reported no value. */
data class Micros(
    val fiber: Double? = null,
    val sugar: Double? = null,
    val saturatedFat: Double? = null,
    val sodiumMg: Double? = null,
    val potassiumMg: Double? = null,
    val calciumMg: Double? = null,
    val ironMg: Double? = null,
    val vitaminCMg: Double? = null,
    val cholesterolMg: Double? = null,
)
