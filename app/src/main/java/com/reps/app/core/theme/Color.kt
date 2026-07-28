package com.reps.app.core.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

// The four brand colours. These are fixed identity and do not change with the
// light/dark theme - the green in particular is the brand and stays put.
val RepsGreen = Color(0xFF77EC04)
val RepsOffWhite = Color(0xFFF4F2EA)
val RepsDarkGray = Color(0xFF3D3C37)
val RepsNearBlack = Color(0xFF0B0B0A)

val RepsGreenPressed = Color(0xFF63C503)
// Text/icons sitting on a green fill. Green is light and acid, so near-black
// reads on it in both themes - this stays constant on purpose.
val RepsOnGreen = RepsNearBlack

// Semantic states, brand-fixed and constant across light/dark.
// Alert red: errors and hard limits. Caution amber: soft warnings.
val RepsError = Color(0xFFE5534B)
val RepsCaution = Color(0xFFF2A93B)

// Weight movement reads as direction, not as good or bad: which way is wanted
// depends on whether the user is cutting or bulking. Green for the wanted
// direction, caution amber for the other.
val RepsWeightDown = RepsGreen
val RepsWeightUp = RepsCaution

// Nutrition macros - one hue each so protein, carbs and fat stay distinguishable
// at a glance. A categorical set, deliberately not derived from the brand green.
val RepsProtein = Color(0xFFE2726B)
val RepsCarbs = Color(0xFFF4B942)
val RepsFat = Color(0xFF4F9DDE)

// ---------------------------------------------------------------------------
// Dark palette - the original brand surfaces. Kept as named constants so the
// dark scheme, previews, and any code that wants an explicit dark value still
// resolve without a composition.
// ---------------------------------------------------------------------------
val RepsSurface = Color(0xFF141413)
val RepsSurfaceElevated = Color(0xFF1C1C1A)
val RepsOutline = Color(0xFF2A2A27)
val RepsTextPrimary = RepsOffWhite
val RepsTextSecondary = Color(0xFF8A8880)
val RepsTextTertiary = Color(0xFF5C5A54)

// ---------------------------------------------------------------------------
// Light palette - a warm paper look rather than a clinical white, so the brand's
// off-white/near-black character carries over. Green is unchanged; on a light
// surface it reads as a punchy accent, with near-black still doing the work on
// green fills.
// ---------------------------------------------------------------------------
val RepsLightBackground = Color(0xFFEDEBE3)
val RepsLightSurface = Color(0xFFF7F5EF)
val RepsLightSurfaceElevated = Color(0xFFFFFFFF)
val RepsLightOutline = Color(0xFFDAD6CA)
val RepsLightTextPrimary = Color(0xFF14130F)
val RepsLightTextSecondary = Color(0xFF57544B)
val RepsLightTextTertiary = Color(0xFF908D83)

/**
 * The surface and text tokens that flip between light and dark. Brand colours
 * (green, error, the weight tints) are not here - they are the same in both
 * themes and stay as top-level constants.
 *
 * Reached in a composable as `RepsTheme.colors.background` etc.; the value comes
 * from [LocalRepsColors], which [RepsTheme] sets from the active [ThemeMode].
 */
@Immutable
data class RepsColors(
    /** The page behind everything. */
    val background: Color,
    /** A card lifted one step off the background. */
    val surface: Color,
    /** A surface lifted a further step - sheets, the nav indicator. */
    val surfaceElevated: Color,
    /** Hairline borders and dividers. */
    val outline: Color,
    /** Body copy and headings. */
    val textPrimary: Color,
    /** Secondary labels, captions. */
    val textSecondary: Color,
    /** The faintest text: hints, disabled, tertiary detail. */
    val textTertiary: Color,
)

val DarkRepsColors = RepsColors(
    background = RepsNearBlack,
    surface = RepsSurface,
    surfaceElevated = RepsSurfaceElevated,
    outline = RepsOutline,
    textPrimary = RepsTextPrimary,
    textSecondary = RepsTextSecondary,
    textTertiary = RepsTextTertiary,
)

val LightRepsColors = RepsColors(
    background = RepsLightBackground,
    surface = RepsLightSurface,
    surfaceElevated = RepsLightSurfaceElevated,
    outline = RepsLightOutline,
    textPrimary = RepsLightTextPrimary,
    textSecondary = RepsLightTextSecondary,
    textTertiary = RepsLightTextTertiary,
)
