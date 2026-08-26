package com.reps.app.core.util

import androidx.annotation.StringRes
import com.reps.app.R

/**
 * Maps the catalogue's 15 anatomical muscle names to localized display names.
 *
 * The catalogue stores muscles once, in English - per-language muscle names
 * are not in the dataset - so the localization lives here as string resources
 * instead. Keyed by the exact `exercise_muscles.muscle_name` value, which is
 * also the overlay-artwork join key; an unmapped name falls back to the
 * catalogue's own common/anatomical label at the call site.
 */
object AnatomyNames {

    private val NAMES = mapOf(
        "Biceps brachii" to R.string.anatomy_biceps_brachii,
        "Anterior deltoid" to R.string.anatomy_anterior_deltoid,
        "Serratus anterior" to R.string.anatomy_serratus_anterior,
        "Pectoralis major" to R.string.anatomy_pectoralis_major,
        "Triceps brachii" to R.string.anatomy_triceps_brachii,
        "Rectus abdominis" to R.string.anatomy_rectus_abdominis,
        "Gastrocnemius" to R.string.anatomy_gastrocnemius,
        "Gluteus maximus" to R.string.anatomy_gluteus_maximus,
        "Trapezius" to R.string.anatomy_trapezius,
        "Quadriceps femoris" to R.string.anatomy_quadriceps_femoris,
        "Biceps femoris" to R.string.anatomy_biceps_femoris,
        "Latissimus dorsi" to R.string.anatomy_latissimus_dorsi,
        "Brachialis" to R.string.anatomy_brachialis,
        "Obliquus externus abdominis" to R.string.anatomy_obliquus_externus,
        "Soleus" to R.string.anatomy_soleus,
    )

    /** The localized-name resource for [muscleName], or null if unmapped. */
    @StringRes
    fun resOf(muscleName: String): Int? = NAMES[muscleName]
}
