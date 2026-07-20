package com.reps.app.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

/** Distance from the top within which the bar is always shown. */
private val TopZone = 24.dp

/** Downward travel that hides the bar. */
private val HideThreshold = 16.dp

/**
 * Upward travel that brings it back. Lower than [HideThreshold] on purpose: a
 * user scrolling back up is looking for the bar, so it should meet them early,
 * whereas hiding it out from under a reader should take a deliberate gesture.
 */
private val ShowThreshold = 8.dp

/**
 * Hides the floating nav bar while the user scrolls down and restores it on the
 * way back up, matching `initScrollHideNav` in the prototype's gestures.js.
 *
 * The prototype thresholds a single scroll event's delta, which works because a
 * browser emits one coarse scroll event per frame. Compose delivers many small
 * deltas instead, so travel is accumulated per direction and reset whenever the
 * direction flips - same feel, without a slow drag failing to ever cross it.
 */
@Stable
class NavBarScrollBehavior(
    private val topZonePx: Float,
    private val hideThresholdPx: Float,
    private val showThresholdPx: Float,
) {
    var visible by mutableStateOf(true)
        private set

    /** Distance scrolled from the top of the current screen, never negative. */
    private var offset = 0f

    /** Travel since the scroll direction last changed. Negative is downward. */
    private var travel = 0f

    val connection = object : NestedScrollConnection {
        override fun onPostScroll(
            consumed: Offset,
            available: Offset,
            source: NestedScrollSource,
        ): Offset {
            val dy = consumed.y + available.y
            if (dy == 0f) return Offset.Zero

            offset = (offset - dy).coerceAtLeast(0f)

            // Near the top the bar is unconditionally visible, so a short screen
            // that cannot scroll far never strands it off-screen.
            if (offset < topZonePx) {
                travel = 0f
                visible = true
                return Offset.Zero
            }

            if ((dy < 0f) != (travel < 0f)) travel = 0f
            travel += dy

            when {
                travel <= -hideThresholdPx -> visible = false
                travel >= showThresholdPx -> visible = true
            }
            return Offset.Zero
        }
    }

    /** Called on navigation: a screen the user has just arrived at starts at the top. */
    fun reset() {
        offset = 0f
        travel = 0f
        visible = true
    }
}

@Composable
fun rememberNavBarScrollBehavior(): NavBarScrollBehavior {
    val density = LocalDensity.current
    return remember(density) {
        with(density) {
            NavBarScrollBehavior(
                topZonePx = TopZone.toPx(),
                hideThresholdPx = HideThreshold.toPx(),
                showThresholdPx = ShowThreshold.toPx(),
            )
        }
    }
}
