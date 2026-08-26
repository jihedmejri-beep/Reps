package com.reps.app.domain.model

/**
 * One muscle on the body map: the catalogue's full vocabulary is the 15 rows
 * of `exercise_muscles` collapsed by name, each with exactly one overlay
 * artwork (the primary-muscle variant) and a fixed body side.
 *
 * [name] doubles as the join key to `exercise_muscles`, so tapping a muscle
 * feeds straight into the "exercises for this muscle" query.
 */
data class BodyMuscle(
    /** Anatomical name, e.g. "Latissimus dorsi". The exercise-lookup key. */
    val name: String,
    /** Common English name, e.g. "Lats". Null for muscles wger never named. */
    val commonName: String?,
    val isFront: Boolean,
    /** Overlay asset path under APK assets/, e.g. `svg/muscles/main/muscle-4...svg`. */
    val overlayAsset: String,
) {
    /** The catalogue's own display label; screens localize over it via AnatomyNames. */
    val displayName: String get() = commonName ?: name
}

/**
 * Everything the body-map screen needs from the artwork index: the two body
 * illustrations plus the tappable muscles that belong on each.
 */
data class BodyMap(
    val frontBodyAsset: String?,
    val backBodyAsset: String?,
    val muscles: List<BodyMuscle> = emptyList(),
) {
    val frontMuscles: List<BodyMuscle> get() = muscles.filter { it.isFront }
    val backMuscles: List<BodyMuscle> get() = muscles.filterNot { it.isFront }
}
