package com.reps.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Guards the string resources against the double-encoding that corrupted them
 * twice (UTF-8 read as cp1252, saved back as UTF-8), and against a locale
 * drifting out of step with the default file.
 */
class StringResourceEncodingTest {

    private val resDir = File("src/main/res")

    /** Byte pairs that only appear when UTF-8 Arabic/accents were re-encoded. */
    private val mojibake = Regex("Ã[\\u0080-\\u00BF]|Ø[\\u0080-\\u00BF\\u0152-\\u2122]|Ù[\\u0080-\\u00BF\\u0152-\\u2122]|â€|\\uFFFD")

    private val stringName = Regex("<string name=\"([^\"]+)\"")
    private val untranslatable = Regex("<string name=\"([^\"]+)\"[^>]*translatable=\"false\"")

    private fun strings(locale: String) = File(resDir, "$locale/strings.xml").readText(Charsets.UTF_8)

    @Test
    fun noLocaleContainsMojibake() {
        for (locale in listOf("values", "values-fr", "values-ar")) {
            val hits = strings(locale).lines().withIndex()
                .filter { (_, line) -> mojibake.containsMatchIn(line) }
                .map { (i, line) -> "$locale:${i + 1}: ${line.trim()}" }
            assertEquals("Corrupted encoding:\n${hits.joinToString("\n")}", emptyList<String>(), hits)
        }
    }

    @Test
    fun arabicFileIsActuallyArabic() {
        val arabicLetters = strings("values-ar").count { it in '؀'..'ۿ' }
        assertTrue("values-ar has only $arabicLetters Arabic letters", arabicLetters > 3_000)
    }

    @Test
    fun everyTranslatableStringExistsInFrenchAndArabic() {
        val default = strings("values")
        val skip = untranslatable.findAll(default).map { it.groupValues[1] }.toSet() +
            setOf("app_name", "language_english", "language_french", "language_arabic")
        val required = stringName.findAll(default).map { it.groupValues[1] }.toSet() - skip
        for (locale in listOf("values-fr", "values-ar")) {
            val present = stringName.findAll(strings(locale)).map { it.groupValues[1] }.toSet()
            assertEquals("Missing in $locale", emptySet<String>(), required - present)
        }
    }
}
