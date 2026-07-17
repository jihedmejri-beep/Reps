package com.reps.app.domain.model

/**
 * One ingredient in a meal. The user enters [grams]; the macros are computed
 * from per-100g reference values, which is how nutrition data is published.
 */
data class FoodItem(
    val id: String = "",
    val name: String = "",
    val grams: Double = 0.0,
    val caloriesPer100g: Double = 0.0,
    val proteinPer100g: Double = 0.0,
    val carbsPer100g: Double = 0.0,
    val fatPer100g: Double = 0.0,
) {
    private val factor: Double get() = grams / 100.0

    val calories: Double get() = caloriesPer100g * factor
    val protein: Double get() = proteinPer100g * factor
    val carbs: Double get() = carbsPer100g * factor
    val fat: Double get() = fatPer100g * factor
}
