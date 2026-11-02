package com.reps.app.core.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NumberInputTest {

    @Test
    fun acceptsDotAndCommaDecimals() {
        assertEquals(72.5, NumberInput.parseDecimal("72.5")!!, 1e-9)
        assertEquals(72.5, NumberInput.parseDecimal("72,5")!!, 1e-9)
        assertEquals(72.5, NumberInput.parseDecimal(" 72,5 ")!!, 1e-9)
    }

    @Test
    fun acceptsArabicIndicDigitsAndSeparator() {
        assertEquals(72.5, NumberInput.parseDecimal("٧٢٫٥")!!, 1e-9)
        assertEquals(180, NumberInput.parseInt("١٨٠"))
        assertEquals(1500.0, NumberInput.parseDecimal("١٬٥٠٠")!!, 1e-9)
    }

    @Test
    fun rejectsGarbage() {
        assertNull(NumberInput.parseDecimal(""))
        assertNull(NumberInput.parseDecimal("1.2.3"))
        assertNull(NumberInput.parseDecimal("abc"))
        assertNull(NumberInput.parseDecimal("NaN"))
        assertNull(NumberInput.parseInt("12.5"))
    }

    @Test
    fun rangesRejectImplausibleValues() {
        assertNull(NumberInput.parseDecimalIn("5000", InputLimits.BODY_WEIGHT_KG))
        assertEquals(80.0, NumberInput.parseDecimalIn("80", InputLimits.BODY_WEIGHT_KG)!!, 1e-9)
        assertNull(NumberInput.parseIntIn("7", InputLimits.AGE_YEARS))
    }
}
