package com.reps.app.core.theme

import androidx.compose.ui.graphics.Color

// The four brand colours. Everything else in the palette is derived from these.
val RepsGreen = Color(0xFF77EC04)
val RepsOffWhite = Color(0xFFF4F2EA)
val RepsDarkGray = Color(0xFF3D3C37)
val RepsNearBlack = Color(0xFF0B0B0A)

// Surfaces. The app is dark-only: near-black page, cards lifted a step above it.
val RepsSurface = Color(0xFF141413)
val RepsSurfaceElevated = Color(0xFF1C1C1A)
val RepsOutline = Color(0xFF2A2A27)

// Text. Off-white carries copy; the muted tones step down for secondary labels.
val RepsTextPrimary = RepsOffWhite
val RepsTextSecondary = Color(0xFF8A8880)
val RepsTextTertiary = Color(0xFF5C5A54)

val RepsGreenPressed = Color(0xFF63C503)
val RepsOnGreen = RepsNearBlack

// Weight movement reads as direction, not as good or bad: which way is wanted
// depends on whether the user is cutting or bulking.
val RepsWeightDown = RepsGreen
val RepsWeightUp = Color(0xFFE8B33A)
val RepsError = Color(0xFFE5484D)
