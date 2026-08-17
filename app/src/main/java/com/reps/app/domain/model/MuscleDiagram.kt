package com.reps.app.domain.model

/**
 * The SVG assets needed to draw one exercise's muscle targeting.
 *
 * Paths are relative to the APK's `assets/` directory. They are split by body
 * side because the catalogue's overlays are drawn onto one of two illustrations
 * and stacking a back muscle onto the front diagram would put it in the wrong
 * place; and by role because primary and secondary muscles are tinted
 * differently.
 */
data class MuscleDiagram(
    val frontBodyAsset: String? = null,
    val backBodyAsset: String? = null,
    val frontPrimary: List<String> = emptyList(),
    val frontSecondary: List<String> = emptyList(),
    val backPrimary: List<String> = emptyList(),
    val backSecondary: List<String> = emptyList(),
) {
    /** True when the exercise records no muscles, or none of them have artwork. */
    val isEmpty: Boolean
        get() = frontPrimary.isEmpty() && frontSecondary.isEmpty() &&
            backPrimary.isEmpty() && backSecondary.isEmpty()
}
