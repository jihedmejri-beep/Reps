package com.reps.app.navigation

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.foundation.shape.RoundedCornerShape

/**
 * Motion and geometry constants for the floating nav bar, lifted from the web
 * prototype's css/nav.css so the two stay comparable side by side.
 */

/** `border-radius: 999px` - fully round ends at any height. */
internal val PillShape = RoundedCornerShape(percent = 50)

/** `cubic-bezier(.22, 1, .36, 1)` - the prototype's standard ease-out. */
internal val ExpoOut = CubicBezierEasing(0.22f, 1f, 0.36f, 1f)

/** `cubic-bezier(.34, 1.56, .64, 1)` - overshoots, used for the icon pop. */
internal val BackOut = CubicBezierEasing(0.34f, 1.56f, 0.64f, 1f)

internal const val IconScaleMs = 380
internal const val TintMs = 250
internal const val NavSlideMs = 420
internal const val NavFadeMs = 300

/** `.reps-nav__tab.is-active .reps-nav__tab-icon { transform: scale(1.12) }` */
internal const val ActiveIconScale = 1.12f

/**
 * The prototype drives the indicator with a hand-rolled spring at
 * `stiffness: 240, damping: 21`, mass 1. Compose takes a damping *ratio*
 * instead, which for those values is `damping / (2 * sqrt(stiffness * mass))`.
 */
internal const val IndicatorStiffness = 240f
internal const val IndicatorXDamping = 0.678f
