package com.reps.app.core.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf

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

private val LocalRepsTextStyles = staticCompositionLocalOf { RepsTextStyles() }

/**
 * REPS is dark-only and brand-fixed: it ignores both the system light/dark
 * setting and Material You dynamic colour on purpose, so the identity is the
 * same on every device.
 */
@Composable
fun RepsTheme(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalRepsTextStyles provides RepsTextStyles()) {
        MaterialTheme(
            colorScheme = RepsColorScheme,
            typography = RepsTypography,
            shapes = RepsShapes,
            content = content,
        )
    }
}

/** Brand styles with no Material slot. Reached as `RepsTheme.textStyles`. */
object RepsTheme {
    val textStyles: RepsTextStyles
        @Composable @ReadOnlyComposable get() = LocalRepsTextStyles.current
}
