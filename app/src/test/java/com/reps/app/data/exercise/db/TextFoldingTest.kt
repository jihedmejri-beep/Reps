package com.reps.app.data.exercise.db

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the search-folding contract.
 *
 * These cases exist because the folded values are written into the catalogue by
 * `tools/build_exercise_db.py` and compared against at query time by
 * [TextFolding]. The two implementations live in different languages, and if
 * they disagree search silently returns nothing rather than failing loudly -
 * so the shape of the agreement is asserted here.
 */
class TextFoldingTest {

    @Test
    fun `folds case`() {
        assertEquals("bench press", TextFolding.fold("Bench Press"))
    }

    @Test
    fun `strips french accents so an unaccented query still matches`() {
        // The catalogue stores "Développé couché" folded; a user types ASCII.
        assertEquals("developpe couche", TextFolding.fold("Développé couché"))
        assertEquals(TextFolding.fold("Développé"), TextFolding.fold("developpe"))
    }

    @Test
    fun `strips arabic diacritics`() {
        // "ضَغْط" (with tashkeel) and "ضغط" (without) are the same word.
        assertEquals(TextFolding.fold("ضغط"), TextFolding.fold("ضَغْط"))
    }

    @Test
    fun `normalises interchangeable arabic letter forms`() {
        // Users type bare alef where the text has a hamza form, and vice versa.
        assertEquals(TextFolding.fold("امام"), TextFolding.fold("أمام"))
        assertEquals(TextFolding.fold("إمام"), TextFolding.fold("أمام"))
        // Teh marbuta vs heh, and alef maksura vs yeh.
        assertEquals(TextFolding.fold("حركه"), TextFolding.fold("حركة"))
        assertEquals(TextFolding.fold("علي"), TextFolding.fold("على"))
    }

    @Test
    fun `removes tatweel`() {
        assertEquals(TextFolding.fold("ضغط"), TextFolding.fold("ضـغـط"))
    }

    @Test
    fun `collapses whitespace runs and trims`() {
        assertEquals("bench press", TextFolding.fold("  Bench   press  "))
        assertEquals("bench press", TextFolding.fold("Bench\tpress"))
    }

    @Test
    fun `is idempotent`() {
        // The stored side is folded once at build time and never re-folded; the
        // query side may be folded from already-plain input. Both must land in
        // the same place.
        val samples = listOf("Développé couché", "ضَغْط الصدر", "Bench  Press", "")
        samples.forEach { sample ->
            val once = TextFolding.fold(sample)
            assertEquals(once, TextFolding.fold(once))
        }
    }

    @Test
    fun `empty input stays empty`() {
        assertEquals("", TextFolding.fold(""))
        assertEquals("", TextFolding.fold("   "))
    }

    @Test
    fun `keeps digits and hyphens that appear in exercise names`() {
        // e.g. "4-Count Burpees", "L-sit", "Push-Ups | Decline"
        assertEquals("4-count burpees", TextFolding.fold("4-Count Burpees"))
        assertTrue(TextFolding.fold("Push-Ups | Decline").contains("push-ups"))
    }
}
