package com.reps.app.domain.model

import androidx.annotation.StringRes
import com.reps.app.R

enum class MuscleGroup(@param:StringRes val labelRes: Int) {
    CHEST(R.string.muscle_chest),
    BACK(R.string.muscle_back),
    LEGS(R.string.muscle_legs),
    SHOULDERS(R.string.muscle_shoulders),
    ARMS(R.string.muscle_arms),
    ABS(R.string.muscle_abs),
    GLUTES(R.string.muscle_glutes),
    CALVES(R.string.muscle_calves),
    CARDIO(R.string.muscle_cardio),
}

enum class Difficulty(@param:StringRes val labelRes: Int) {
    BEGINNER(R.string.difficulty_beginner),
    INTERMEDIATE(R.string.difficulty_intermediate),
    ADVANCED(R.string.difficulty_advanced),
}

/**
 * A library row. Deliberately shallow: this is what the exercise list, the
 * builder picker and a running session need, and loading a full instruction set
 * for 828 of them to draw a handful of rows is exactly the cost worth avoiding.
 * The detail screen asks for [ExerciseDetail] instead.
 */
data class Exercise(
    val id: String = "",
    val name: String = "",
    val muscleGroup: MuscleGroup = MuscleGroup.CHEST,
    /** Comma-joined, already localised by the catalogue. May be blank. */
    val equipment: String = "",
    /** Full-size demonstration image or GIF. Remote, and often absent. */
    val mediaUrl: String = "",
    /** Smaller render for list rows. Falls back to [mediaUrl] when absent. */
    val thumbnailUrl: String = "",
    /**
     * The catalogue's own category string, e.g. "Calves". Kept alongside
     * [muscleGroup] because the enum is the app's coarser grouping and this is
     * what the data actually says.
     */
    val category: String = "",
    /**
     * Null means "the catalogue does not say", not "beginner". The REPS dataset
     * carries no difficulty field for any of its 828 exercises, so this is null
     * for everything sourced from it; the UI hides the label rather than
     * inventing a value.
     */
    val difficulty: Difficulty? = null,
)

/** One muscle an exercise works, as the catalogue records it. */
data class MuscleTarget(
    /** Anatomical name, e.g. "Latissimus dorsi". Also the muscle-SVG join key. */
    val name: String,
    /** Common name, e.g. "Lats". Null for the muscles wger never gave one. */
    val commonName: String?,
    /** Which body diagram this muscle belongs on. Null when unrecorded. */
    val isFront: Boolean?,
    val isPrimary: Boolean,
) {
    /** What to show a user: the common name when there is one. */
    val displayName: String get() = commonName ?: name
}

/** A demonstration photo or GIF. Always remote - never packaged in the APK. */
data class ExerciseMedia(
    val id: String,
    val fullUrl: String,
    /** A ~400px render where the catalogue has one, else [fullUrl]. */
    val thumbnailUrl: String,
    val isMain: Boolean,
)

/**
 * Everything the catalogue knows about one exercise, in the user's language.
 *
 * Every list here can legitimately be empty: 122 of the dataset's exercises
 * record no muscles, 181 no equipment and 564 no image. The detail screen omits
 * the corresponding section rather than showing an empty heading.
 */
data class ExerciseDetail(
    val exercise: Exercise,
    /** The upstream (wger) numeric id. Useful for support and asset paths. */
    val externalId: String = "",
    val summary: String = "",
    val startingPosition: String = "",
    val steps: List<String> = emptyList(),
    val tips: List<String> = emptyList(),
    val notes: List<String> = emptyList(),
    val primaryMuscles: List<MuscleTarget> = emptyList(),
    val secondaryMuscles: List<MuscleTarget> = emptyList(),
    val equipment: List<String> = emptyList(),
    val aliases: List<String> = emptyList(),
    val media: List<ExerciseMedia> = emptyList(),
    val licenseName: String = "",
    val licenseUrl: String = "",
) {
    val id: String get() = exercise.id
    val name: String get() = exercise.name

    /** Primary first, then secondary - the order the body diagram draws them in. */
    val allMuscles: List<MuscleTarget> get() = primaryMuscles + secondaryMuscles

    val hasInstructions: Boolean
        get() = steps.isNotEmpty() || startingPosition.isNotBlank() || summary.isNotBlank()
}
