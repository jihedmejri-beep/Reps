package com.reps.app.core.content

import java.time.LocalDate

/**
 * The daily motivation lines. Plain content with no backend behind it, kept
 * out of the feature code so both Home and Progress rotate identically.
 */
object MotivationQuotes {

    private val quotes: List<String> = listOf(
        "Every rep counts.\nEvery set matters.",
        "Show up.\nGet stronger.",
        "Small wins.\nBig results.",
        "Discipline beats\nmotivation.",
        "The work you skip\nis the progress you lose.",
        "Trust the process.\nCount the reps.",
        "Make today count.\nNothing else does.",
    )

    /** Rotates daily, stable within a day. */
    fun forToday(today: LocalDate = LocalDate.now()): String =
        quotes[(today.toEpochDay() % quotes.size).toInt()]
}
