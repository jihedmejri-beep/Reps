package com.reps.app.feature.nutrition

import androidx.annotation.StringRes
import com.reps.app.R
import com.reps.app.core.util.InputLimits
import com.reps.app.core.util.NumberInput

/**
 * The per-100 g values for one ingredient, as typed. There is no food database
 * in the app, so these come from the user (usually the product label); nothing
 * is filled in on their behalf. Earlier builds stored a flat 165 kcal / 12 P /
 * 14 C / 5 F for every food, which made every total meaningless.
 */
data class MacroInputText(
    val kcal: String = "",
    val protein: String = "",
    val carbs: String = "",
    val fat: String = "",
) {
    companion object
}

data class Per100g(val kcal: Double, val protein: Double, val carbs: Double, val fat: Double)

sealed interface MacroValidation {
    data class Valid(val value: Per100g) : MacroValidation
    data class Invalid(@param:StringRes val messageRes: Int) : MacroValidation
}

/** Energy implied by the macros (Atwater 4/4/9), used to catch an obvious typo in kcal. */
private fun Per100g.atwaterKcal(): Double = protein * 4 + carbs * 4 + fat * 9

/** Allowed gap between typed kcal and Atwater kcal; labels round, fibre and alcohol skew it. */
private const val KcalTolerance = 0.35
private const val KcalToleranceFloor = 40.0

fun validateMacros(input: MacroInputText): MacroValidation {
    val kcal = NumberInput.parseDecimalIn(input.kcal, InputLimits.KCAL_PER_100G)
    val protein = NumberInput.parseDecimalIn(input.protein, InputLimits.MACRO_PER_100G)
    val carbs = NumberInput.parseDecimalIn(input.carbs, InputLimits.MACRO_PER_100G)
    val fat = NumberInput.parseDecimalIn(input.fat, InputLimits.MACRO_PER_100G)
    if (kcal == null || protein == null || carbs == null || fat == null) {
        return MacroValidation.Invalid(R.string.nutrition_macros_required)
    }
    if (protein + carbs + fat > 100.0) return MacroValidation.Invalid(R.string.nutrition_macros_over_100)
    val value = Per100g(kcal, protein, carbs, fat)
    val implied = value.atwaterKcal()
    if (kotlin.math.abs(implied - kcal) > maxOf(KcalToleranceFloor, implied * KcalTolerance)) {
        return MacroValidation.Invalid(R.string.nutrition_macros_kcal_mismatch)
    }
    return MacroValidation.Valid(value)
}

/** Pre-fills the fields from a stored item, e.g. when editing an ingredient. */
fun MacroInputText.Companion.of(kcal: Double, protein: Double, carbs: Double, fat: Double) = MacroInputText(
    kcal = kcal.formatInput(),
    protein = protein.formatInput(),
    carbs = carbs.formatInput(),
    fat = fat.formatInput(),
)

private fun Double.formatInput(): String =
    if (this % 1.0 == 0.0) toLong().toString() else String.format(java.util.Locale.US, "%.1f", this)
