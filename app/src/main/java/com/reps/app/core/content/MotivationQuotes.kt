package com.reps.app.core.content

import java.time.LocalDate

/**
 * Which daily motivation line to show. The lines themselves are the translated
 * `motivation_quotes` string array; this only picks the index, so every language
 * shows the same line on the same day.
 */
object MotivationQuotes {

    /** Rotates daily, stable within a day. */
    fun indexFor(today: LocalDate, count: Int): Int =
        if (count <= 0) 0 else Math.floorMod(today.toEpochDay(), count.toLong()).toInt()
}
