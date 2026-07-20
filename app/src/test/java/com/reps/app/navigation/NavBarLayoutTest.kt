package com.reps.app.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The nav pill has to survive every phone width the app ships to, every system
 * font scale, and the longest translated label - Arabic "الملف الشخصي" for
 * Profile is roughly a third wider than the English.
 *
 * These are the numbers the bar is actually built from, so a regression here is
 * a regression on a real device.
 */
class NavBarLayoutTest {

    private companion object {
        const val TAB_COUNT = 5
        const val ICON = 21f
        const val REF_PADDING = 14f
        const val REF_GAP = 7f
        const val MIN_PADDING = 6f
        const val MIN_GAP = 3f

        /** Pill padding, both sides. */
        const val PILL_PADDING = 12f

        /** 2dp between each pair of tabs. */
        const val TAB_GAPS = 8f

        /** Screen margin, both sides. */
        const val SIDE_MARGIN = 28f

        /** "Workouts" and "Nutrition" at 11sp Poppins ExtraBold. */
        const val EN_WIDEST = 56f
    }

    private fun availableFor(screenWidthDp: Float) =
        screenWidthDp - SIDE_MARGIN - PILL_PADDING - TAB_GAPS

    private fun solveFor(screenWidthDp: Float, widestLabel: Float = EN_WIDEST) =
        NavBarLayout.solve(
            availableWidth = availableFor(screenWidthDp),
            tabCount = TAB_COUNT,
            iconSize = ICON,
            widestLabel = widestLabel,
            referencePadding = REF_PADDING,
            referenceGap = REF_GAP,
            minPadding = MIN_PADDING,
            minGap = MIN_GAP,
        )

    /** Total width the resolved geometry actually consumes with a label shown. */
    private fun consumed(geometry: NavBarGeometry, label: Float) =
        geometry.inactiveTabWidth(ICON) * TAB_COUNT + minOf(label, geometry.labelCap)

    @Test
    fun `the bug this fixes - a 360dp screen shows Workouts in full`() {
        val geometry = solveFor(360f)
        assertTrue(
            "label capped at ${geometry.labelCap}dp, needs ${EN_WIDEST}dp",
            geometry.labelCap >= EN_WIDEST,
        )
    }

    @Test
    fun `tabs never overflow the pill at any phone width`() {
        var width = 280f
        while (width <= 900f) {
            val geometry = solveFor(width)
            val available = availableFor(width)
            assertTrue(
                "overflow at ${width}dp: used ${consumed(geometry, EN_WIDEST)} of $available",
                consumed(geometry, EN_WIDEST) <= available + 0.01f,
            )
            width += 1f
        }
    }

    @Test
    fun `labels stay whole at every width down to 300dp`() {
        var width = 300f
        while (width <= 900f) {
            val geometry = solveFor(width)
            assertTrue(
                "clipped at ${width}dp: cap ${geometry.labelCap}dp",
                geometry.labelCap >= EN_WIDEST,
            )
            width += 1f
        }
    }

    @Test
    fun `padding relaxes back to the reference value once there is room`() {
        assertEquals(REF_PADDING, solveFor(411f).tabPadding, 0.01f)
        assertEquals(REF_GAP, solveFor(411f).iconGap, 0.01f)
    }

    @Test
    fun `padding is spent before the label is clipped`() {
        // 360dp cannot afford the reference padding, but must not cost a letter.
        val geometry = solveFor(360f)
        assertTrue("padding did not tighten", geometry.tabPadding < REF_PADDING)
        assertTrue("padding fell below its floor", geometry.tabPadding >= MIN_PADDING)
        assertTrue("label was clipped anyway", geometry.labelCap >= EN_WIDEST)
    }

    @Test
    fun `the long Arabic Profile label still fits a 360dp screen`() {
        // "الملف الشخصي" measures about 70dp at the nav label's size.
        val geometry = solveFor(360f, widestLabel = 70f)
        assertTrue("Arabic label clipped: cap ${geometry.labelCap}dp", geometry.labelCap >= 70f)
    }

    @Test
    fun `a doubled system font scale still fits a 360dp screen`() {
        // Accessibility text doubles the label's width along with everything else.
        val geometry = solveFor(360f, widestLabel = EN_WIDEST * 2f)
        assertTrue(
            "clipped at 2x font scale: cap ${geometry.labelCap}dp",
            geometry.labelCap >= EN_WIDEST * 2f,
        )
    }

    @Test
    fun `geometry never goes negative on an absurdly narrow screen`() {
        val geometry = solveFor(200f)
        assertTrue(geometry.tabPadding >= MIN_PADDING)
        assertTrue(geometry.iconGap >= MIN_GAP)
        assertTrue(geometry.labelCap >= 0f)
    }

    @Test
    fun `padding and gap never exceed their reference values`() {
        var width = 280f
        while (width <= 900f) {
            val geometry = solveFor(width)
            assertTrue(geometry.tabPadding <= REF_PADDING + 0.01f)
            assertTrue(geometry.iconGap <= REF_GAP + 0.01f)
            width += 7f
        }
    }
}
