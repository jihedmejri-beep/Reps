package com.reps.app.domain.model

import java.time.LocalDate

/**
 * What a streak counts depends on whether the user planned their week.
 *
 * With scheduled days, it is consecutive *planned* days honoured: a rest day the
 * user planned never breaks it, a planned day skipped does. Without a schedule
 * there is nothing to honour day by day, so it counts consecutive calendar weeks
 * with at least one session - earlier builds counted "days ever trained" here,
 * which could never break.
 */
enum class StreakUnit { SCHEDULED_DAYS, WEEKS }

data class Streak(
    val count: Int = 0,
    val lastWorkoutDate: LocalDate? = null,
    val unit: StreakUnit = StreakUnit.WEEKS,
) {
    val isActive: Boolean get() = count > 0

    /** The number the nudge copy dangles: "train today to hit N". */
    val nextMilestone: Int get() = count + 1
}
