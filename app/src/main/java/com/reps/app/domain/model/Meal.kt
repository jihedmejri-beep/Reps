package com.reps.app.domain.model

import java.time.LocalDate

/** Macro totals, reused for a single meal, a whole day, and daily targets. */
data class Macros(
    val calories: Double = 0.0,
    val protein: Double = 0.0,
    val carbs: Double = 0.0,
    val fat: Double = 0.0,
) {
    operator fun plus(other: Macros) = Macros(
        calories = calories + other.calories,
        protein = protein + other.protein,
        carbs = carbs + other.carbs,
        fat = fat + other.fat,
    )
}

data class Meal(
    val id: String = "",
    val name: String = "",
    val date: LocalDate = LocalDate.now(),
    val foodItems: List<FoodItem> = emptyList(),
) {
    val macros: Macros
        get() = foodItems.fold(Macros()) { acc, item ->
            acc + Macros(item.calories, item.protein, item.carbs, item.fat)
        }
}
