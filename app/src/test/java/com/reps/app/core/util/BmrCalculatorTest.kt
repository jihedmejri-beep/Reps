package com.reps.app.core.util

import com.reps.app.domain.model.Sex
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The coefficients are fixed by the brief, so these lock the exact arithmetic
 * rather than an approximation of it.
 */
class BmrCalculatorTest {

    @Test
    fun `male BMR matches the specified formula`() {
        // 66.5 + (13.75 x 80) + (5.003 x 180) - (6.755 x 30)
        // 66.5 + 1100 + 900.54 - 202.65 = 1864.39
        val actual = BmrCalculator.calculate(Sex.MALE, weightKg = 80.0, heightCm = 180.0, age = 30)
        assertEquals(1864.39, actual, 0.001)
    }

    @Test
    fun `female BMR matches the specified formula`() {
        // 655.1 + (9.563 x 60) + (1.850 x 165) - (4.676 x 30)
        // 655.1 + 573.78 + 305.25 - 140.28 = 1393.85
        val actual = BmrCalculator.calculate(Sex.FEMALE, weightKg = 60.0, heightCm = 165.0, age = 30)
        assertEquals(1393.85, actual, 0.001)
    }

    @Test
    fun `BMR falls as age rises, all else equal`() {
        val young = BmrCalculator.calculate(Sex.MALE, 80.0, 180.0, 20)
        val older = BmrCalculator.calculate(Sex.MALE, 80.0, 180.0, 50)
        assert(older < young) { "expected BMR to decrease with age" }
    }

    @Test
    fun `male and female formulas are genuinely different`() {
        val male = BmrCalculator.calculate(Sex.MALE, 70.0, 170.0, 30)
        val female = BmrCalculator.calculate(Sex.FEMALE, 70.0, 170.0, 30)
        assert(male != female) { "sex must select a different formula" }
    }
}
