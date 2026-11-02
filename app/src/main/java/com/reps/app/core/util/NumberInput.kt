package com.reps.app.core.util

/**
 * Parses what a user typed into a numeric field, whatever keyboard they used.
 *
 * `toDoubleOrNull()` alone rejected "72,5" (French and Arabic keyboards put a
 * comma on the decimal key) and "٧٢" (Arabic-Indic digits), so those users could
 * not enter a decimal weight at all. Values are normalised to ASCII first.
 */
object NumberInput {

    fun parseDecimal(text: String): Double? {
        val normalised = normalise(text)
        if (normalised.isEmpty() || normalised.count { it == '.' } > 1) return null
        return normalised.toDoubleOrNull()?.takeIf { it.isFinite() }
    }

    fun parseInt(text: String): Int? = normalise(text).takeIf { it.none { c -> c == '.' } }?.toIntOrNull()

    /** [parseDecimal] constrained to [range]; out-of-range input counts as invalid. */
    fun parseDecimalIn(text: String, range: ClosedFloatingPointRange<Double>): Double? =
        parseDecimal(text)?.takeIf { it in range }

    fun parseIntIn(text: String, range: IntRange): Int? = parseInt(text)?.takeIf { it in range }

    private fun normalise(text: String): String = buildString {
        for (c in text.trim()) {
            when (c) {
                in '٠'..'٩' -> append('0' + (c - '٠')) // Arabic-Indic
                in '۰'..'۹' -> append('0' + (c - '۰')) // Extended (Persian/Urdu)
                ',', '٫' -> append('.') // comma, Arabic decimal separator
                '٬', ' ', ' ', ' ' -> Unit // grouping separators and spaces
                else -> append(c)
            }
        }
    }
}

/** Sanity bounds for body and food input; anything outside is a typo, not a measurement. */
object InputLimits {
    val BODY_WEIGHT_KG = 20.0..400.0
    val HEIGHT_CM = 100.0..250.0
    val AGE_YEARS = 13..100
    val FOOD_GRAMS = 0.1..5_000.0
    val KCAL_PER_100G = 0.0..900.0
    val MACRO_PER_100G = 0.0..100.0
    val SET_WEIGHT_KG = 0.0..1_000.0
    val SET_REPS = 0..1_000
}
