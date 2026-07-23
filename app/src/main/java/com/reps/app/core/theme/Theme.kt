package com.reps.app.core.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalConfiguration
import com.reps.app.domain.model.ThemeMode

private val RepsDarkColorScheme = darkColorScheme(
    primary = RepsGreen,
    onPrimary = RepsOnGreen,
    primaryContainer = RepsGreen,
    onPrimaryContainer = RepsOnGreen,
    secondary = RepsDarkGray,
    onSecondary = RepsOffWhite,
    background = DarkRepsColors.background,
    onBackground = DarkRepsColors.textPrimary,
    surface = DarkRepsColors.surface,
    onSurface = DarkRepsColors.textPrimary,
    surfaceVariant = DarkRepsColors.surfaceElevated,
    onSurfaceVariant = DarkRepsColors.textSecondary,
    outline = DarkRepsColors.outline,
    outlineVariant = DarkRepsColors.outline,
    error = RepsError,
    onError = RepsOffWhite,
)

private val RepsLightColorScheme = lightColorScheme(
    primary = RepsGreen,
    onPrimary = RepsOnGreen,
    primaryContainer = RepsGreen,
    onPrimaryContainer = RepsOnGreen,
    secondary = RepsDarkGray,
    onSecondary = RepsOffWhite,
    background = LightRepsColors.background,
    onBackground = LightRepsColors.textPrimary,
    surface = LightRepsColors.surface,
    onSurface = LightRepsColors.textPrimary,
    surfaceVariant = LightRepsColors.surfaceElevated,
    onSurfaceVariant = LightRepsColors.textSecondary,
    outline = LightRepsColors.outline,
    outlineVariant = LightRepsColors.outline,
    error = RepsError,
    onError = RepsOffWhite,
)

private val LocalRepsColors = staticCompositionLocalOf { DarkRepsColors }
private val LocalRepsTextStyles = staticCompositionLocalOf {
    repsTextStyles(displayScale = 1f)
}
private val LocalRepsDimens = staticCompositionLocalOf { dimensFor(WindowWidthClass.MEDIUM) }
private val LocalWindowWidthClass = staticCompositionLocalOf { WindowWidthClass.MEDIUM }

/**
 * REPS is brand-fixed - it ignores Material You dynamic colour so the identity
 * is the same on every device - but it does support a light and a dark palette.
 * [themeMode] chooses between them: [ThemeMode.SYSTEM] follows the phone's own
 * setting, while [ThemeMode.LIGHT]/[ThemeMode.DARK] force one.
 *
 * Spacing and the display type scale are derived from the current window width,
 * so a 320dp phone, a 411dp phone and an unfolded foldable each get proportions
 * suited to them rather than one hardcoded set.
 */
@Composable
fun RepsTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit,
) {
    val dark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    val widthDp = LocalConfiguration.current.screenWidthDp
    val widthClass = remember(widthDp) { WindowWidthClass.fromWidthDp(widthDp) }
    val dimens = remember(widthClass) { dimensFor(widthClass) }
    val typography = remember(widthClass) { repsTypography(dimens.typeScale) }
    val textStyles = remember(widthClass) { repsTextStyles(dimens.typeScale) }

    val colors = if (dark) DarkRepsColors else LightRepsColors
    val colorScheme = if (dark) RepsDarkColorScheme else RepsLightColorScheme

    CompositionLocalProvider(
        LocalRepsColors provides colors,
        LocalRepsTextStyles provides textStyles,
        LocalRepsDimens provides dimens,
        LocalWindowWidthClass provides widthClass,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = typography,
            shapes = RepsShapes,
            content = content,
        )
    }
}

/** Brand tokens with no Material slot. Reached as `RepsTheme.colors` etc. */
object RepsTheme {
    val colors: RepsColors
        @Composable @ReadOnlyComposable get() = LocalRepsColors.current

    val textStyles: RepsTextStyles
        @Composable @ReadOnlyComposable get() = LocalRepsTextStyles.current

    val dimens: RepsDimens
        @Composable @ReadOnlyComposable get() = LocalRepsDimens.current

    val widthClass: WindowWidthClass
        @Composable @ReadOnlyComposable get() = LocalWindowWidthClass.current
}
