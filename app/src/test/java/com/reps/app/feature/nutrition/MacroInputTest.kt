package com.reps.app.feature.nutrition

import com.reps.app.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MacroInputTest {

    private fun invalid(input: MacroInputText) = (validateMacros(input) as MacroValidation.Invalid).messageRes

    @Test
    fun chickenBreastLabel_isValid() {
        val result = validateMacros(MacroInputText(kcal = "165", protein = "31", carbs = "0", fat = "3,6"))
        assertTrue(result is MacroValidation.Valid)
        assertEquals(3.6, (result as MacroValidation.Valid).value.fat, 1e-9)
    }

    @Test
    fun oliveOil_isValid() {
        assertTrue(validateMacros(MacroInputText("884", "0", "0", "100")) is MacroValidation.Valid)
    }

    @Test
    fun missingField_isRequired() {
        assertEquals(R.string.nutrition_macros_required, invalid(MacroInputText("165", "31", "", "3")))
    }

    @Test
    fun macrosOver100g_areRejected() {
        assertEquals(R.string.nutrition_macros_over_100, invalid(MacroInputText("500", "60", "50", "10")))
    }

    @Test
    fun kcalFarFromMacros_isRejected() {
        // 31 g protein + 3.6 g fat implies ~156 kcal; 600 is a typo.
        assertEquals(R.string.nutrition_macros_kcal_mismatch, invalid(MacroInputText("600", "31", "0", "3.6")))
    }

    @Test
    fun kcalAbovePureFat_isOutOfRange() {
        assertEquals(R.string.nutrition_macros_required, invalid(MacroInputText("1650", "31", "0", "3.6")))
    }

    @Test
    fun zeroCalorieDrink_isValid() {
        assertTrue(validateMacros(MacroInputText("0", "0", "0", "0")) is MacroValidation.Valid)
    }
}
