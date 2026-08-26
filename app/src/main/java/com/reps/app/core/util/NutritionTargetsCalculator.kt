package com.reps.app.core.util

import com.reps.app.domain.model.Goal
import com.reps.app.domain.model.Macros
import com.reps.app.domain.model.Sex

/**
 * Daily macro targets from the profile's own numbers: Harris-Benedict BMR
 * (the brief's formula, see [BmrCalculator]) scaled by a moderate activity
 * factor, then shaped by the goal.
 *
 *   - Calories: cut -18%, bulk +12%, maintain as-is.
 *   - Protein: grams per kg bodyweight by goal (cutting protects the most).
 *   - Fat: fixed 25% of calories.
 *   - Carbs: whatever remains, floored at zero.
 */
object NutritionTargetsCalculator {

    /** 3-5 sessions a week; there is no per-user activity input yet. */
    private const val ACTIVITY_FACTOR = 1.45

    private const val CALORIE_CUT_FACTOR = 0.82
    private const val CALORIE_BULK_FACTOR = 1.12

    private const val PROTEIN_G_PER_KG_CUT = 2.0
    private const val PROTEIN_G_PER_KG_BULK = 1.8
    private const val PROTEIN_G_PER_KG_MAINTAIN = 1.6

    private const val FAT_CALORIE_SHARE = 0.25

    /** Shown until the profile has sex, height, age and a weigh-in to work from. */
    fun fallback(): Macros = Macros(calories = 2400.0, protein = 180.0, carbs = 260.0, fat = 70.0)

    fun daily(sex: Sex, weightKg: Double, heightCm: Double, age: Int, goal: Goal): Macros {
        val bmr = BmrCalculator.calculate(sex, weightKg, heightCm, age)
        val tdee = bmr * ACTIVITY_FACTOR
        val calories = when (goal) {
            Goal.CUT -> tdee * CALORIE_CUT_FACTOR
            Goal.BULK -> tdee * CALORIE_BULK_FACTOR
            Goal.MAINTAIN -> tdee
        }
        val proteinPerKg = when (goal) {
            Goal.CUT -> PROTEIN_G_PER_KG_CUT
            Goal.BULK -> PROTEIN_G_PER_KG_BULK
            Goal.MAINTAIN -> PROTEIN_G_PER_KG_MAINTAIN
        }
        val protein = weightKg * proteinPerKg
        val fat = calories * FAT_CALORIE_SHARE / 9.0
        val remainingCalories = (calories - protein * 4.0 - fat * 9.0).coerceAtLeast(0.0)
        return Macros(
            calories = calories.roundToWhole(),
            protein = protein.roundToWhole(),
            carbs = (remainingCalories / 4.0).roundToWhole(),
            fat = fat.roundToWhole(),
        )
    }

    private fun Double.roundToWhole(): Double = Math.round(this).toDouble()
}
