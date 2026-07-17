package com.reps.app.core.util

import com.reps.app.domain.model.UnitSystem
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Everything is persisted metric; these convert only for display and input.
 */
object UnitConverter {

    private const val KG_PER_LB = 0.45359237
    private const val CM_PER_INCH = 2.54

    fun lbToKg(lb: Double) = lb * KG_PER_LB
    fun kgToLb(kg: Double) = kg / KG_PER_LB
    fun inchesToCm(inches: Double) = inches * CM_PER_INCH
    fun cmToInches(cm: Double) = cm / CM_PER_INCH

    /** Weight in the user's unit, without a unit suffix. */
    fun displayWeight(kg: Double, units: UnitSystem): Double = when (units) {
        UnitSystem.METRIC -> kg
        UnitSystem.IMPERIAL -> kgToLb(kg)
    }

    /** Turns a value typed in the user's unit back into storage units. */
    fun weightToKg(value: Double, units: UnitSystem): Double = when (units) {
        UnitSystem.METRIC -> value
        UnitSystem.IMPERIAL -> lbToKg(value)
    }

    fun formatWeight(kg: Double, units: UnitSystem, decimals: Int = 1): String =
        String.format(Locale.US, "%.${decimals}f", displayWeight(kg, units))

    /**
     * Height reads naturally as 5'11" in imperial rather than as raw inches.
     */
    fun formatHeight(cm: Double, units: UnitSystem): String = when (units) {
        UnitSystem.METRIC -> "${cm.roundToInt()} cm"
        UnitSystem.IMPERIAL -> {
            val totalInches = cmToInches(cm).roundToInt()
            "${totalInches / 12}' ${totalInches % 12}\""
        }
    }
}
