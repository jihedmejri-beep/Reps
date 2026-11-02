package com.reps.app.core.util

import com.reps.app.domain.model.ActivityLevel
import com.reps.app.domain.model.Sex
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Mifflin-St Jeor, as the brief specifies. These lock the exact arithmetic
 * rather than an approximation of it.
 */
class BmrCalculatorTest {

    @Test
    fun `male BMR matches the specified formula`() {
        // 10 x 80 + 6.25 x 180 - 5 x 30 + 5 = 800 + 1125 - 150 + 5 = 1780
        val actual = BmrCalculator.calculate(Sex.MALE, weightKg = 80.0, heightCm = 180.0, age = 30)
        assertEquals(1780.0, actual, 0.001)
    }

    @Test
    fun `female BMR matches the specified formula`() {
        // 10 x 60 + 6.25 x 165 - 5 x 30 - 161 = 600 + 1031.25 - 150 - 161 = 1320.25
        val actual = BmrCalculator.calculate(Sex.FEMALE, weightKg = 60.0, heightCm = 165.0, age = 30)
        assertEquals(1320.25, actual, 0.001)
    }

    @Test
    fun `BMR falls as age rises, all else equal`() {
        val young = BmrCalculator.calculate(Sex.MALE, 80.0, 180.0, 20)
        val older = BmrCalculator.calculate(Sex.MALE, 80.0, 180.0, 50)
        assert(older < young) { "expected BMR to decrease with age" }
    }

    @Test
    fun `TDEE scales BMR by the activity factor`() {
        assertEquals(1780.0 * 1.55, BmrCalculator.tdee(1780.0, ActivityLevel.MODERATE), 0.001)
        assertEquals(1780.0 * 1.2, BmrCalculator.tdee(1780.0, ActivityLevel.SEDENTARY), 0.001)
    }

    @Test
    fun `male and female formulas are genuinely different`() {
        val male = BmrCalculator.calculate(Sex.MALE, 70.0, 170.0, 30)
        val female = BmrCalculator.calculate(Sex.FEMALE, 70.0, 170.0, 30)
        assert(male != female) { "sex must select a different formula" }
    }
}
