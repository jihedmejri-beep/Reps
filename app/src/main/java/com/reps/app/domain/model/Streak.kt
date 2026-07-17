package com.reps.app.domain.model

import java.time.LocalDate

/**
 * A streak counts consecutive *scheduled* days honoured, not consecutive
 * calendar days: a rest day the user planned must not break it.
 */
data class Streak(
    val count: Int = 0,
    val lastWorkoutDate: LocalDate? = null,
) {
    val isActive: Boolean get() = count > 0

    /** The number the nudge copy dangles: "train today to hit N". */
    val nextMilestone: Int get() = count + 1
}
