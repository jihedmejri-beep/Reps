package com.reps.app.core.util

import com.reps.app.domain.model.ActivityLevel
import com.reps.app.domain.model.Goal
import com.reps.app.domain.model.Macros
import com.reps.app.domain.model.Sex
import com.reps.app.domain.model.User
import java.time.LocalDate

/**
 * Daily macro targets from the profile's own numbers: Mifflin-St Jeor BMR
 * (see [BmrCalculator]) scaled by the user's own activity level into TDEE, then
 * shaped by the goal.
 *
 *   - Calories: cut -18%, bulk +12%, maintain as-is.
 *   - Protein: grams per kg bodyweight by goal (cutting protects the most).
 *   - Fat: fixed 25% of calories.
 *   - Carbs: whatever remains, floored at zero.
 */
object NutritionTargetsCalculator {

    private const val CALORIE_CUT_FACTOR = 0.82
    private const val CALORIE_BULK_FACTOR = 1.12

    private const val PROTEIN_G_PER_KG_CUT = 2.0
    private const val PROTEIN_G_PER_KG_BULK = 1.8
    private const val PROTEIN_G_PER_KG_MAINTAIN = 1.6

    private const val FAT_CALORIE_SHARE = 0.25

    /**
     * Shown until the profile has sex, height, age, activity level and a weigh-in
     * to work from. The UI labels these as general targets, never as the user's own.
     */
    fun fallback(): Macros = Macros(calories = 2400.0, protein = 180.0, carbs = 260.0, fat = 70.0)

    /** Personal targets, or null when [user] lacks something the estimate needs. */
    fun forUser(user: User?, weightKg: Double?, today: LocalDate = LocalDate.now()): Macros? {
        val sex = user?.sex ?: return null
        val heightCm = user.heightCm ?: return null
        val age = user.ageOn(today) ?: return null
        val activity = user.activityLevel ?: return null
        return daily(sex, weightKg ?: return null, heightCm, age, user.goal, activity)
    }

    fun daily(sex: Sex, weightKg: Double, heightCm: Double, age: Int, goal: Goal, activity: ActivityLevel): Macros {
        val bmr = BmrCalculator.calculate(sex, weightKg, heightCm, age)
        val tdee = BmrCalculator.tdee(bmr, activity)
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
