package com.reps.app.core.util

import com.reps.app.domain.model.ActivityLevel
import com.reps.app.domain.model.Sex

/**
 * Mifflin-St Jeor (1990), the equation the product brief specifies and the one
 * generally found most accurate for adults today. Inputs are metric:
 *
 *   Men:   10 x kg + 6.25 x cm - 5 x age + 5
 *   Women: 10 x kg + 6.25 x cm - 5 x age - 161
 *
 * BMR is energy at complete rest. It is an estimate, not a measurement, and is
 * never a medical figure; the UI says so next to it. Total daily expenditure is
 * a separate number, [tdee].
 *
 * Earlier builds used the revised Harris-Benedict equation here.
 */
object BmrCalculator {

    fun calculate(sex: Sex, weightKg: Double, heightCm: Double, age: Int): Double {
        val base = 10.0 * weightKg + 6.25 * heightCm - 5.0 * age
        return when (sex) {
            Sex.MALE -> base + 5.0
            Sex.FEMALE -> base - 161.0
        }
    }

    /** Estimated total daily energy expenditure: BMR scaled by everyday activity. */
    fun tdee(bmr: Double, activity: ActivityLevel): Double = bmr * activity.factor
}
