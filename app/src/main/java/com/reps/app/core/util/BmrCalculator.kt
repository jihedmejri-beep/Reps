package com.reps.app.core.util

import com.reps.app.domain.model.Sex

/**
 * Revised Harris-Benedict, using the coefficients fixed by the brief.
 *
 *   Men:   66.5  + (13.75 x kg) + (5.003 x cm) - (6.755 x age)
 *   Women: 655.1 + (9.563 x kg) + (1.850 x cm) - (4.676 x age)
 */
object BmrCalculator {

    fun calculate(sex: Sex, weightKg: Double, heightCm: Double, age: Int): Double = when (sex) {
        Sex.MALE -> 66.5 + (13.75 * weightKg) + (5.003 * heightCm) - (6.755 * age)
        Sex.FEMALE -> 655.1 + (9.563 * weightKg) + (1.850 * heightCm) - (4.676 * age)
    }
}
