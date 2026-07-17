package com.reps.app.core.util

import androidx.annotation.StringRes
import com.reps.app.R

enum class BmiCategory(@param:StringRes val labelRes: Int) {
    UNDERWEIGHT(R.string.progress_bmi_underweight),
    HEALTHY(R.string.progress_bmi_healthy),
    OVERWEIGHT(R.string.progress_bmi_overweight),
    OBESE(R.string.progress_bmi_obese),
}

/**
 * BMI is computed from metric values only. The imperial formula
 * (703 x lb / in^2) is mathematically the same figure, so rather than carry two
 * code paths the UI converts imperial input to metric on the way in and this
 * stays single-source.
 */
object BmiCalculator {

    fun calculate(weightKg: Double, heightCm: Double): Double {
        require(heightCm > 0) { "heightCm must be positive" }
        val heightM = heightCm / 100.0
        return weightKg / (heightM * heightM)
    }

    /** Underweight <18.5 | Healthy 18.5-24.9 | Overweight 25.0-29.9 | Obese >=30. */
    fun categorise(bmi: Double): BmiCategory = when {
        bmi < 18.5 -> BmiCategory.UNDERWEIGHT
        bmi < 25.0 -> BmiCategory.HEALTHY
        bmi < 30.0 -> BmiCategory.OVERWEIGHT
        else -> BmiCategory.OBESE
    }
}
