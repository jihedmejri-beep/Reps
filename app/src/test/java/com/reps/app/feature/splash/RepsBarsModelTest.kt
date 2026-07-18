package com.reps.app.feature.splash

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Checks the splash animation against the start-animation video the client
 * supplied, rather than against how it looks by eye.
 *
 * [VIDEO_SAMPLES] are real measurements: all 120 frames were decoded and each
 * bar's green pixel height recorded per frame. Heights are in source pixels,
 * where the tallest bar ever reaches 71px - so [FULL_SCALE] converts the
 * model's 0..1 output into the same units.
 */
class RepsBarsModelTest {

    private companion object {
        const val FULL_SCALE = 71.0f

        /** Where the video sits in the cycle at t=0, fitted to the samples. */
        const val TIME_ORIGIN_S = 0.335f
        const val CYCLE_S = RepsBarsModel.CYCLE_MS / 1000f

        /** time (s) to the three measured bar heights (px). */
        val VIDEO_SAMPLES = listOf(
            floatArrayOf(0.40f, 27f, 32f, 27f),
            floatArrayOf(0.50f, 29f, 40f, 35f),
            floatArrayOf(0.60f, 31f, 46f, 47f),
            floatArrayOf(0.70f, 27f, 50f, 63f),
            floatArrayOf(0.80f, 23f, 48f, 69f),
            floatArrayOf(0.90f, 15f, 40f, 71f),
            floatArrayOf(1.00f, 11f, 32f, 65f),
            floatArrayOf(1.10f, 9f, 24f, 55f),
            floatArrayOf(1.20f, 11f, 18f, 37f),
            floatArrayOf(1.30f, 15f, 18f, 29f),
            floatArrayOf(1.40f, 21f, 22f, 23f),
            floatArrayOf(1.50f, 27f, 30f, 27f),
            floatArrayOf(1.60f, 29f, 38f, 33f),
            floatArrayOf(1.70f, 31f, 48f, 51f),
            floatArrayOf(1.80f, 27f, 50f, 61f),
            floatArrayOf(1.90f, 21f, 48f, 71f),
            floatArrayOf(2.00f, 15f, 42f, 71f),
            floatArrayOf(2.10f, 11f, 34f, 67f),
            floatArrayOf(2.20f, 9f, 22f, 51f),
            floatArrayOf(2.30f, 11f, 18f, 39f),
        )
    }

    private fun phaseAt(timeSeconds: Float): Float {
        val raw = (timeSeconds - TIME_ORIGIN_S) / CYCLE_S
        return raw - kotlin.math.floor(raw)
    }

    private fun modelPx(barIndex: Int, timeSeconds: Float) =
        RepsBarsModel.heightFraction(barIndex, phaseAt(timeSeconds)) * FULL_SCALE

    @Test
    fun `every sampled frame matches the reference video`() {
        // The samples carry a pixel or two of antialiasing noise, and the model
        // is a clean sinusoid through them, so an exact match is not the goal -
        // staying visually indistinguishable is.
        val tolerancePx = 0.09f * FULL_SCALE
        VIDEO_SAMPLES.forEach { sample ->
            val t = sample[0]
            repeat(RepsBarsModel.BAR_COUNT) { bar ->
                val expected = sample[bar + 1]
                val actual = modelPx(bar, t)
                assertEquals(
                    "bar $bar at t=${t}s deviates from the reference video",
                    expected.toDouble(),
                    actual.toDouble(),
                    tolerancePx.toDouble(),
                )
            }
        }
    }

    @Test
    fun `overall fit stays within three percent of the reference`() {
        var sumSq = 0.0
        var n = 0
        VIDEO_SAMPLES.forEach { sample ->
            repeat(RepsBarsModel.BAR_COUNT) { bar ->
                val d = modelPx(bar, sample[0]) - sample[bar + 1]
                sumSq += d * d
                n++
            }
        }
        val rms = sqrt(sumSq / n)
        assertTrue("RMS error ${"%.2f".format(rms)}px is too high", rms < 0.03f * FULL_SCALE)
    }

    @Test
    fun `bars swing between half and one and a half times resting height`() {
        repeat(RepsBarsModel.BAR_COUNT) { bar ->
            val samples = (0..999).map { RepsBarsModel.heightFraction(bar, it / 1000f) }
            val resting = RepsBarsModel.RESTING[bar] / (1f + RepsBarsModel.SWING)
            assertEquals(resting * 1.5f, samples.max(), 0.001f)
            assertEquals(resting * 0.5f, samples.min(), 0.001f)
        }
    }

    /** The tallest bar at its peak must exactly fill the canvas: no clip, no gap. */
    @Test
    fun `tallest bar peaks at exactly full height`() {
        val peak = (0..999).maxOf { RepsBarsModel.heightFraction(2, it / 1000f) }
        assertEquals(1.0f, peak, 0.001f)
    }

    /** No bar may ever invert through the baseline. */
    @Test
    fun `heights are always positive`() {
        repeat(RepsBarsModel.BAR_COUNT) { bar ->
            (0..999).forEach {
                assertTrue(RepsBarsModel.heightFraction(bar, it / 1000f) > 0f)
            }
        }
    }

    /**
     * The bars must not move in lockstep - the lag is what makes the mark read
     * as a wave travelling across it rather than three bars pulsing together.
     */
    @Test
    fun `each bar lags the one before it`() {
        fun peakPhase(bar: Int) =
            (0..9999).maxBy { RepsBarsModel.heightFraction(bar, it / 10000f) } / 10000f

        val lag01 = peakPhase(1) - peakPhase(0)
        val lag12 = peakPhase(2) - peakPhase(1)
        assertEquals(RepsBarsModel.PHASE_LAG, lag01, 0.005f)
        assertEquals(RepsBarsModel.PHASE_LAG, lag12, 0.005f)
        assertTrue("bars must not be in phase", abs(lag01) > 0.01f)
    }
}
