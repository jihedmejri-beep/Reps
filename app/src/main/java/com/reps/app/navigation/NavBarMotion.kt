package com.reps.app.navigation

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

/**
 * Motion and geometry constants for the floating nav bar, lifted from the web
 * prototype's css/nav.css so the two stay comparable side by side.
 */

/** `border-radius: 999px` - fully round ends at any height. */
internal val PillShape = RoundedCornerShape(percent = 50)

/** `.reps-nav__pill { gap: 2px }` */
internal val TabGap = 2.dp

/** `.reps-nav__tab { gap: 7px }` between the icon and its label. */
internal val IconLabelGap = 7.dp

/**
 * Floors for the two things a narrow screen is allowed to eat into before the
 * label itself gets clipped. Below these the pill stops reading as a row of
 * separate tabs and starts looking like one crowded strip of icons.
 */
internal val MinTabPadding = 6.dp
internal val MinIconLabelGap = 3.dp

/** How far the label starts translated before it slides into place. */
internal val LabelSlide = 4.dp

/** `cubic-bezier(.22, 1, .36, 1)` - the prototype's standard ease-out. */
internal val ExpoOut = CubicBezierEasing(0.22f, 1f, 0.36f, 1f)

/** `cubic-bezier(.34, 1.56, .64, 1)` - overshoots, used for the icon pop. */
internal val BackOut = CubicBezierEasing(0.34f, 1.56f, 0.64f, 1f)

internal const val LabelRevealMs = 380
internal const val LabelFadeMs = 220
internal const val LabelSlideMs = 320
internal const val IconScaleMs = 380
internal const val TintMs = 250
internal const val NavSlideMs = 420
internal const val NavFadeMs = 300

/** `.reps-nav__tab.is-active .reps-nav__tab-icon { transform: scale(1.12) }` */
internal const val ActiveIconScale = 1.12f

/**
 * The prototype drives the indicator with a hand-rolled spring at
 * `stiffness: 240, damping: 21` (x) and `damping: 24` (width), mass 1. Compose
 * takes a damping *ratio* instead, which for those values is
 * `damping / (2 * sqrt(stiffness * mass))`.
 */
internal const val IndicatorStiffness = 240f
internal const val IndicatorXDamping = 0.678f
internal const val IndicatorWidthDamping = 0.775f
