package com.reps.app.feature.splash

import kotlin.math.PI
import kotlin.math.sin

/**
 * The motion curve for the REPS bars, kept free of Compose so it can be tested
 * against the reference animation.
 *
 * Every constant was measured from the supplied start-animation video by
 * decoding all 120 frames and tracking each bar's pixel height per frame:
 *
 *  - the baseline never moves; only the tops travel
 *  - one full cycle takes ~1.08s
 *  - each bar swings between 0.5x and 1.5x its resting height
 *  - resting heights are in the ratio 0.426 : 0.723 : 1.0
 *  - each bar lags the one before it by ~0.12 of a cycle, which is what makes
 *    the motion read as a wave travelling across the mark rather than three
 *    bars pulsing together
 */
object RepsBarsModel {

    const val BAR_COUNT = 3
    const val CYCLE_MS = 1080

    /** Resting heights, normalised so the tallest bar is 1.0. */
    val RESTING = floatArrayOf(0.426f, 0.723f, 1.0f)

    /** Half-swing either side of the resting height. */
    const val SWING = 0.5f

    /** Lag between adjacent bars, as a fraction of one cycle. */
    const val PHASE_LAG = 0.12f

    /** Horizontal lean, matching the italic brand mark (~7.4 degrees). */
    const val SLANT = 0.13f

    /**
     * Height of [barIndex] at [phase] (0..1 through the cycle), as a fraction
     * of the tallest height the animation ever reaches.
     *
     * Dividing by (1 + SWING) is what keeps the tallest bar at its peak exactly
     * filling the canvas, so the mark never clips or leaves a gap at the top.
     */
    fun heightFraction(barIndex: Int, phase: Float): Float {
        val angle = 2f * PI.toFloat() * (phase - barIndex * PHASE_LAG)
        val scale = 1f + SWING * sin(angle)
        return RESTING[barIndex] * scale / (1f + SWING)
    }
}
