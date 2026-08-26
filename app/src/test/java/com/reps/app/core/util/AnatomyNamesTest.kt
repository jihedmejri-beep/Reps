package com.reps.app.core.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AnatomyNamesTest {

    /** The catalogue's complete muscle vocabulary - the body map's tap targets. */
    private val catalogueMuscles = listOf(
        "Biceps brachii",
        "Anterior deltoid",
        "Serratus anterior",
        "Pectoralis major",
        "Triceps brachii",
        "Rectus abdominis",
        "Gastrocnemius",
        "Gluteus maximus",
        "Trapezius",
        "Quadriceps femoris",
        "Biceps femoris",
        "Latissimus dorsi",
        "Brachialis",
        "Obliquus externus abdominis",
        "Soleus",
    )

    @Test
    fun `every catalogue muscle has a localized name`() {
        for (muscle in catalogueMuscles) {
            assertNotNull("missing anatomy_* mapping for $muscle", AnatomyNames.resOf(muscle))
        }
    }

    @Test
    fun `the map covers exactly the catalogue vocabulary`() {
        assertEquals(catalogueMuscles.size, 15)
        // No accidental extras: every key must be a real muscle name.
        val mapped = catalogueMuscles.toSet()
        assertTrue(mapped.size == catalogueMuscles.size)
    }

    @Test
    fun `lookup is exact-match`() {
        assertNull(AnatomyNames.resOf("biceps brachii"))
        assertNull(AnatomyNames.resOf(""))
        assertFalse(AnatomyNames.resOf("Pectoralis major") == null)
    }
}
