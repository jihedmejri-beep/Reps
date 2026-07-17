package com.reps.app.domain.model

import java.time.LocalDate

enum class Sex { MALE, FEMALE }

enum class UnitSystem { METRIC, IMPERIAL }

enum class Goal { BULK, CUT, MAINTAIN }

enum class AppLanguage(val tag: String) {
    ENGLISH("en"),
    ARABIC("ar"),
    FRENCH("fr"),
    ;

    companion object {
        fun fromTag(tag: String): AppLanguage =
            entries.firstOrNull { it.tag == tag.take(2) } ?: ENGLISH
    }
}

/**
 * Body measurements are always stored metric. The unit preference is a display
 * concern only, converted at the edges, so a user switching units never
 * rewrites their history.
 */
data class User(
    val uid: String = "",
    val name: String = "",
    val email: String = "",
    val sex: Sex? = null,
    val heightCm: Double? = null,
    val age: Int? = null,
    val goal: Goal = Goal.MAINTAIN,
    val units: UnitSystem = UnitSystem.METRIC,
    val language: AppLanguage = AppLanguage.ENGLISH,
    val streakCount: Int = 0,
    val lastWorkoutDate: LocalDate? = null,
) {
    /** BMR and BMI need all three; the cards stay hidden until they exist. */
    val hasBodyMetrics: Boolean
        get() = sex != null && heightCm != null && age != null
}
