package com.reps.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RestStateTest {

    private val t0 = 1_000_000L
    private val rest = RestState.start("bench", setIndex = 0, restSeconds = 90, nowMs = t0)

    @Test
    fun remainingIsDerivedFromTimestamps_soBackgroundTimeCounts() {
        assertEquals(90_000, rest.remainingMs(t0))
        // App backgrounded for 60 s: nothing ticked, the answer is still exact.
        assertEquals(30_000, rest.remainingMs(t0 + 60_000))
        assertTrue(rest.isFinished(t0 + 90_000))
        assertEquals(0, rest.remainingMs(t0 + 500_000))
    }

    @Test
    fun pauseFreezesRemaining_resumeContinuesFromIt() {
        val paused = rest.pause(t0 + 20_000)
        assertTrue(paused.isPaused)
        assertEquals(70_000, paused.remainingMs(t0 + 999_999))
        val resumed = paused.resume(t0 + 100_000)
        assertFalse(resumed.isPaused)
        assertEquals(70_000, resumed.remainingMs(t0 + 100_000))
        assertEquals(10_000, resumed.remainingMs(t0 + 160_000))
    }

    @Test
    fun addTime_extendsBothRemainingAndTotal() {
        val longer = rest.adjust(15_000, t0 + 30_000)
        assertEquals(75_000, longer.remainingMs(t0 + 30_000))
        assertEquals(105_000, longer.durationMs)
    }

    @Test
    fun removingMoreThanRemains_finishesTheRest() {
        assertTrue(rest.adjust(-200_000, t0).isFinished(t0))
    }

    @Test
    fun skip_finishesImmediately() {
        val skipped = rest.skip(t0 + 5_000)
        assertTrue(skipped.isFinished(t0 + 5_000))
        assertEquals(0f, skipped.progress(t0 + 5_000))
    }

    @Test
    fun finishedRest_ignoresPauseAndAdjust() {
        val done = rest.skip(t0)
        assertEquals(done, done.pause(t0 + 1))
        assertEquals(done, done.adjust(15_000, t0 + 1))
    }
}
