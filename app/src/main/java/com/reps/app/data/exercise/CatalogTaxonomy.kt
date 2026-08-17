package com.reps.app.data.exercise

import com.reps.app.domain.model.MuscleGroup

/**
 * Bridges the catalogue's own vocabulary to the app's [MuscleGroup] enum.
 *
 * The dataset groups exercises into eight categories - Abs, Arms, Back, Calves,
 * Cardio, Chest, Legs, Shoulders - which line up with the app's groups except
 * for Glutes: there is no "Glutes" category upstream, but "Gluteus maximus" is
 * the recorded primary muscle for 128 exercises. So that one group filters on
 * muscle rather than category, and everything else filters on category.
 *
 * Category strings are matched case-insensitively but are otherwise the
 * catalogue's, untranslated - the user-facing label comes from
 * [MuscleGroup.labelRes], which is localised in all three shipped languages.
 */
internal object CatalogTaxonomy {

    /** The anatomical name the catalogue uses for the glutes. */
    const val GLUTES_MUSCLE = "Gluteus maximus"

    /** What a library row is labelled with. */
    fun muscleGroupFor(category: String?): MuscleGroup =
        when (category?.trim()?.lowercase()) {
            "chest" -> MuscleGroup.CHEST
            "back" -> MuscleGroup.BACK
            "legs" -> MuscleGroup.LEGS
            "shoulders" -> MuscleGroup.SHOULDERS
            "arms" -> MuscleGroup.ARMS
            "abs" -> MuscleGroup.ABS
            "calves" -> MuscleGroup.CALVES
            "cardio" -> MuscleGroup.CARDIO
            // Unreachable for the shipped catalogue - all eight of its
            // categories are listed above. A category added upstream lands
            // here, and CARDIO is the least wrong bucket because it is the one
            // group that does not claim a specific muscle.
            else -> MuscleGroup.CARDIO
        }

    /** The catalogue category a filter chip maps to, or null if it filters by muscle. */
    fun categoryFor(group: MuscleGroup): String? = when (group) {
        MuscleGroup.CHEST -> "Chest"
        MuscleGroup.BACK -> "Back"
        MuscleGroup.LEGS -> "Legs"
        MuscleGroup.SHOULDERS -> "Shoulders"
        MuscleGroup.ARMS -> "Arms"
        MuscleGroup.ABS -> "Abs"
        MuscleGroup.CALVES -> "Calves"
        MuscleGroup.CARDIO -> "Cardio"
        MuscleGroup.GLUTES -> null
    }

    /** The primary muscle a filter chip maps to, or null if it filters by category. */
    fun primaryMuscleFor(group: MuscleGroup): String? =
        if (group == MuscleGroup.GLUTES) GLUTES_MUSCLE else null
}
