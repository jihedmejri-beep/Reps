package com.reps.app.core.util

import com.reps.app.domain.model.UnitSystem
import org.junit.Assert.assertEquals
import org.junit.Test

class UnitConverterTest {

    @Test
    fun `kg to lb uses the exact international pound`() {
        assertEquals(220.462, UnitConverter.kgToLb(100.0), 0.001)
    }

    /**
     * Weight is stored metric and shown in the user's unit. A value that
     * survives a display-then-input round trip is what keeps switching units in
     * Profile from drifting the stored history.
     */
    @Test
    fun `imperial round trip preserves the stored kilograms`() {
        val original = 78.4
        val shown = UnitConverter.displayWeight(original, UnitSystem.IMPERIAL)
        val stored = UnitConverter.weightToKg(shown, UnitSystem.IMPERIAL)
        assertEquals(original, stored, 0.0001)
    }

    @Test
    fun `metric display and storage are identity`() {
        assertEquals(78.4, UnitConverter.displayWeight(78.4, UnitSystem.METRIC), 0.0)
        assertEquals(78.4, UnitConverter.weightToKg(78.4, UnitSystem.METRIC), 0.0)
    }

    @Test
    fun `weight formats to one decimal in both systems`() {
        assertEquals("78.4", UnitConverter.formatWeight(78.4, UnitSystem.METRIC))
        assertEquals("172.8", UnitConverter.formatWeight(78.4, UnitSystem.IMPERIAL))
    }

    /** Imperial height should read as feet and inches, not raw inches. */
    @Test
    fun `height formats per unit system`() {
        assertEquals("180 cm", UnitConverter.formatHeight(180.0, UnitSystem.METRIC))
        assertEquals("5' 11\"", UnitConverter.formatHeight(180.0, UnitSystem.IMPERIAL))
    }

    @Test
    fun `height rolling to a whole foot does not render as 12 inches`() {
        // 182.88 cm is exactly 6 ft.
        assertEquals("6' 0\"", UnitConverter.formatHeight(182.88, UnitSystem.IMPERIAL))
    }
}
