package com.reps.app.core.util

import org.junit.Assert.assertEquals
import org.junit.Test

class BmiCalculatorTest {

    @Test
    fun `metric BMI matches the specified formula`() {
        // 80 / 1.8^2 = 24.691...
        assertEquals(24.691, BmiCalculator.calculate(weightKg = 80.0, heightCm = 180.0), 0.001)
    }

    /**
     * The brief gives both a metric and an imperial BMI formula. The code keeps
     * only the metric one and converts imperial input on the way in, which is
     * only valid if the two agree. This proves they do.
     */
    @Test
    fun `imperial formula agrees with the metric implementation`() {
        val weightKg = 80.0
        val heightCm = 180.0

        val lbs = UnitConverter.kgToLb(weightKg)
        val inches = UnitConverter.cmToInches(heightCm)
        val imperialBmi = 703 * lbs / (inches * inches)

        assertEquals(imperialBmi, BmiCalculator.calculate(weightKg, heightCm), 0.01)
    }

    @Test
    fun `category boundaries follow the brief`() {
        // Underweight <18.5 | Healthy 18.5-24.9 | Overweight 25.0-29.9 | Obese >=30
        assertEquals(BmiCategory.UNDERWEIGHT, BmiCalculator.categorise(18.49))
        assertEquals(BmiCategory.HEALTHY, BmiCalculator.categorise(18.5))
        assertEquals(BmiCategory.HEALTHY, BmiCalculator.categorise(24.9))
        assertEquals(BmiCategory.OVERWEIGHT, BmiCalculator.categorise(25.0))
        assertEquals(BmiCategory.OVERWEIGHT, BmiCalculator.categorise(29.9))
        assertEquals(BmiCategory.OBESE, BmiCalculator.categorise(30.0))
        assertEquals(BmiCategory.OBESE, BmiCalculator.categorise(41.2))
    }

    /**
     * 24.95 sits in the gap the brief's wording leaves open ("18.5-24.9" then
     * "25.0-29.9"). It must land somewhere rather than fall through, and
     * Healthy is the correct side: it is below 25.
     */
    @Test
    fun `values between the stated bands still categorise`() {
        assertEquals(BmiCategory.HEALTHY, BmiCalculator.categorise(24.95))
        assertEquals(BmiCategory.OVERWEIGHT, BmiCalculator.categorise(29.95))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `zero height is rejected rather than returning infinity`() {
        BmiCalculator.calculate(weightKg = 80.0, heightCm = 0.0)
    }
}
