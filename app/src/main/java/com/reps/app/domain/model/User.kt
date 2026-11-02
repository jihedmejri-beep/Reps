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
 * Daily activity outside logged training, as the standard TDEE multipliers
 * (sedentary 1.2 ... very active 1.9). TDEE = BMR x [factor].
 */
enum class ActivityLevel(val factor: Double) {
    SEDENTARY(1.2),
    LIGHT(1.375),
    MODERATE(1.55),
    ACTIVE(1.725),
    VERY_ACTIVE(1.9),
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
    /**
     * An age typed by a pre-v2 build. Kept so those users keep their BMR, but it
     * goes stale; [birthDate] supersedes it as soon as one is entered.
     */
    val age: Int? = null,
    val birthDate: LocalDate? = null,
    val activityLevel: ActivityLevel? = null,
    val goal: Goal = Goal.MAINTAIN,
    val units: UnitSystem = UnitSystem.METRIC,
    val language: AppLanguage = AppLanguage.ENGLISH,
    val streakCount: Int = 0,
    val lastWorkoutDate: LocalDate? = null,
) {
    /** Whole years on [today]: from the birth date when known, else the legacy typed age. */
    fun ageOn(today: LocalDate = LocalDate.now()): Int? =
        birthDate?.let { java.time.Period.between(it, today).years } ?: age

    /** BMR and BMI need all three; the cards stay hidden until they exist. */
    val hasBodyMetrics: Boolean
        get() = sex != null && heightCm != null && ageOn() != null
}
