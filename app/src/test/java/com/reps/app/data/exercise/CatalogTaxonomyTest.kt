package com.reps.app.data.exercise

import com.reps.app.domain.model.MuscleGroup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The catalogue's eight categories against the app's nine muscle groups. The
 * mismatch is real and deliberate, so it is asserted rather than left to drift.
 */
class CatalogTaxonomyTest {

    /** Exactly the categories present in `assets/reps_exercises.db`. */
    private val catalogCategories = listOf(
        "Abs", "Arms", "Back", "Calves", "Cardio", "Chest", "Legs", "Shoulders",
    )

    @Test
    fun `every catalogue category maps to its own group`() {
        val mapped = catalogCategories.associateWith(CatalogTaxonomy::muscleGroupFor)
        assertEquals(MuscleGroup.ABS, mapped["Abs"])
        assertEquals(MuscleGroup.ARMS, mapped["Arms"])
        assertEquals(MuscleGroup.BACK, mapped["Back"])
        assertEquals(MuscleGroup.CALVES, mapped["Calves"])
        assertEquals(MuscleGroup.CARDIO, mapped["Cardio"])
        assertEquals(MuscleGroup.CHEST, mapped["Chest"])
        assertEquals(MuscleGroup.LEGS, mapped["Legs"])
        assertEquals(MuscleGroup.SHOULDERS, mapped["Shoulders"])

        // No two categories collapse onto the same group, which is what makes
        // the filter chips partition the library rather than overlap.
        assertEquals(catalogCategories.size, mapped.values.toSet().size)
    }

    @Test
    fun `category matching ignores case and padding`() {
        assertEquals(MuscleGroup.CHEST, CatalogTaxonomy.muscleGroupFor(" chest "))
        assertEquals(MuscleGroup.CHEST, CatalogTaxonomy.muscleGroupFor("CHEST"))
    }

    @Test
    fun `a null or unknown category does not crash`() {
        assertNotNull(CatalogTaxonomy.muscleGroupFor(null))
        assertNotNull(CatalogTaxonomy.muscleGroupFor("Forearms"))
    }

    @Test
    fun `every filter chip resolves to a predicate the catalogue can answer`() {
        // A chip that maps to neither a category nor a muscle would silently
        // return the whole library instead of filtering it.
        MuscleGroup.entries.forEach { group ->
            val category = CatalogTaxonomy.categoryFor(group)
            val muscle = CatalogTaxonomy.primaryMuscleFor(group)
            assertNotNull("$group filters on nothing", category ?: muscle)
        }
    }

    @Test
    fun `glutes filters on muscle because the catalogue has no such category`() {
        assertNull(CatalogTaxonomy.categoryFor(MuscleGroup.GLUTES))
        assertEquals(
            CatalogTaxonomy.GLUTES_MUSCLE,
            CatalogTaxonomy.primaryMuscleFor(MuscleGroup.GLUTES),
        )
    }

    @Test
    fun `every other group filters on category alone`() {
        MuscleGroup.entries
            .filterNot { it == MuscleGroup.GLUTES }
            .forEach { group ->
                assertNotNull("$group should map to a category", CatalogTaxonomy.categoryFor(group))
                assertNull("$group should not filter by muscle", CatalogTaxonomy.primaryMuscleFor(group))
            }
    }

    @Test
    fun `category names round-trip back to their group`() {
        MuscleGroup.entries
            .mapNotNull(CatalogTaxonomy::categoryFor)
            .forEach { category ->
                assertEquals(
                    "category $category must be one the catalogue actually uses",
                    true,
                    category in catalogCategories,
                )
            }
    }
}
