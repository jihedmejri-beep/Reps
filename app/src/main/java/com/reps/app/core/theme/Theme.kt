package com.reps.app.core.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalConfiguration

private val RepsColorScheme = darkColorScheme(
    primary = RepsGreen,
    onPrimary = RepsOnGreen,
    primaryContainer = RepsGreen,
    onPrimaryContainer = RepsOnGreen,
    secondary = RepsDarkGray,
    onSecondary = RepsOffWhite,
    background = RepsNearBlack,
    onBackground = RepsTextPrimary,
    surface = RepsSurface,
    onSurface = RepsTextPrimary,
    surfaceVariant = RepsSurfaceElevated,
    onSurfaceVariant = RepsTextSecondary,
    outline = RepsOutline,
    outlineVariant = RepsOutline,
    error = RepsError,
    onError = RepsOffWhite,
)

private val LocalRepsTextStyles = staticCompositionLocalOf {
    repsTextStyles(displayScale = 1f)
}
private val LocalRepsDimens = staticCompositionLocalOf { dimensFor(WindowWidthClass.MEDIUM) }
private val LocalWindowWidthClass = staticCompositionLocalOf { WindowWidthClass.MEDIUM }

/**
 * REPS is dark-only and brand-fixed: it ignores both the system light/dark
 * setting and Material You dynamic colour on purpose, so the identity is the
 * same on every device.
 *
 * Spacing and the display type scale are derived from the current window width,
 * so a 320dp phone, a 411dp phone and an unfolded foldable each get proportions
 * suited to them rather than one hardcoded set.
 */
@Composable
fun RepsTheme(content: @Composable () -> Unit) {
    val widthDp = LocalConfiguration.current.screenWidthDp
    val widthClass = remember(widthDp) { WindowWidthClass.fromWidthDp(widthDp) }
    val dimens = remember(widthClass) { dimensFor(widthClass) }
    val typography = remember(widthClass) { repsTypography(dimens.typeScale) }
    val textStyles = remember(widthClass) { repsTextStyles(dimens.typeScale) }

    CompositionLocalProvider(
        LocalRepsTextStyles provides textStyles,
        LocalRepsDimens provides dimens,
        LocalWindowWidthClass provides widthClass,
    ) {
        MaterialTheme(
            colorScheme = RepsColorScheme,
            typography = typography,
            shapes = RepsShapes,
            content = content,
        )
    }
}

/** Brand tokens with no Material slot. Reached as `RepsTheme.textStyles`. */
object RepsTheme {
    val textStyles: RepsTextStyles
        @Composable @ReadOnlyComposable get() = LocalRepsTextStyles.current

    val dimens: RepsDimens
        @Composable @ReadOnlyComposable get() = LocalRepsDimens.current

    val widthClass: WindowWidthClass
        @Composable @ReadOnlyComposable get() = LocalWindowWidthClass.current
}
