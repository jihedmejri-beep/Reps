package com.reps.app.core.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.reps.app.R

// Anton is the condensed display face: big screen titles and the section titles
// under each eyebrow. Archivo Black handles shorter, wider headline work.
// Poppins carries body copy and labels; ExtraBold is reserved for emphasis and
// buttons, so the family ships real weights rather than letting the platform
// synthesise them from a single file.
val AntonFamily = FontFamily(Font(R.font.anton_regular, FontWeight.Normal))
val ArchivoBlackFamily = FontFamily(Font(R.font.archivo_black, FontWeight.Black))
val PoppinsFamily = FontFamily(
    Font(R.font.poppins_regular, FontWeight.Normal),
    Font(R.font.poppins_medium, FontWeight.Medium),
    Font(R.font.poppins_semibold, FontWeight.SemiBold),
    Font(R.font.poppins_extrabold, FontWeight.ExtraBold),
)

/**
 * Extra styles that Material's [Typography] has no slot for but the design
 * leans on repeatedly.
 */
data class RepsTextStyles(
    /** Small green uppercase label sitting above a section title. */
    val eyebrow: TextStyle,
    /** The large condensed title directly under an eyebrow. */
    val sectionTitle: TextStyle,
    /** Numerals that need to dominate their card: weight, timer, BMR. */
    val statValue: TextStyle,
    val buttonLabel: TextStyle,
    /** Label beside the icon on the selected bottom-nav tab. */
    val navLabel: TextStyle,
)

/**
 * Display type scales with the screen; body copy deliberately does not scale
 * as hard. Shrinking a 48sp headline by 14% on a small phone keeps it from
 * wrapping badly, but shrinking 15sp body text by the same amount would just
 * make it hard to read.
 */
private fun bodyScaleFrom(displayScale: Float) = 1f + (displayScale - 1f) * 0.4f

fun repsTypography(displayScale: Float = 1f): Typography {
    val d = displayScale
    val b = bodyScaleFrom(displayScale)

    fun sp(value: Number, scale: Float): TextUnit = (value.toFloat() * scale).sp

    return Typography(
        displayLarge = TextStyle(
            fontFamily = AntonFamily,
            fontSize = sp(48, d),
            lineHeight = sp(50, d),
            letterSpacing = 0.sp,
        ),
        displayMedium = TextStyle(
            fontFamily = AntonFamily,
            fontSize = sp(40, d),
            lineHeight = sp(42, d),
        ),
        displaySmall = TextStyle(
            fontFamily = AntonFamily,
            fontSize = sp(34, d),
            lineHeight = sp(36, d),
        ),
        headlineLarge = TextStyle(
            fontFamily = ArchivoBlackFamily,
            fontWeight = FontWeight.Black,
            fontSize = sp(30, d),
            lineHeight = sp(34, d),
        ),
        headlineMedium = TextStyle(
            fontFamily = ArchivoBlackFamily,
            fontWeight = FontWeight.Black,
            fontSize = sp(24, d),
            lineHeight = sp(28, d),
        ),
        headlineSmall = TextStyle(
            fontFamily = ArchivoBlackFamily,
            fontWeight = FontWeight.Black,
            fontSize = sp(20, d),
            lineHeight = sp(24, d),
        ),
        titleLarge = TextStyle(
            fontFamily = PoppinsFamily,
            fontWeight = FontWeight.ExtraBold,
            fontSize = sp(18, b),
            lineHeight = sp(22, b),
        ),
        titleMedium = TextStyle(
            fontFamily = PoppinsFamily,
            fontWeight = FontWeight.ExtraBold,
            fontSize = sp(16, b),
            lineHeight = sp(20, b),
        ),
        titleSmall = TextStyle(
            fontFamily = PoppinsFamily,
            fontWeight = FontWeight.ExtraBold,
            fontSize = sp(14, b),
            lineHeight = sp(18, b),
        ),
        bodyLarge = TextStyle(
            fontFamily = PoppinsFamily,
            fontWeight = FontWeight.Normal,
            fontSize = sp(15, b),
            lineHeight = sp(22, b),
        ),
        bodyMedium = TextStyle(
            fontFamily = PoppinsFamily,
            fontWeight = FontWeight.Normal,
            fontSize = sp(13, b),
            lineHeight = sp(19, b),
        ),
        bodySmall = TextStyle(
            fontFamily = PoppinsFamily,
            fontWeight = FontWeight.Normal,
            fontSize = sp(12, b),
            lineHeight = sp(16, b),
        ),
        labelLarge = TextStyle(
            fontFamily = PoppinsFamily,
            fontWeight = FontWeight.ExtraBold,
            fontSize = sp(14, b),
            lineHeight = sp(18, b),
            letterSpacing = 0.2.sp,
        ),
        labelMedium = TextStyle(
            fontFamily = PoppinsFamily,
            fontWeight = FontWeight.ExtraBold,
            fontSize = sp(12, b),
            lineHeight = sp(16, b),
            letterSpacing = 0.4.sp,
        ),
        labelSmall = TextStyle(
            fontFamily = PoppinsFamily,
            fontWeight = FontWeight.ExtraBold,
            fontSize = sp(10, b),
            lineHeight = sp(14, b),
            letterSpacing = 0.6.sp,
        ),
    )
}

fun repsTextStyles(displayScale: Float = 1f): RepsTextStyles {
    val d = displayScale
    val b = bodyScaleFrom(displayScale)
    return RepsTextStyles(
        eyebrow = TextStyle(
            fontFamily = PoppinsFamily,
            fontWeight = FontWeight.ExtraBold,
            fontSize = (11 * b).sp,
            lineHeight = (14 * b).sp,
            letterSpacing = 1.6.sp,
        ),
        sectionTitle = TextStyle(
            fontFamily = AntonFamily,
            fontSize = (34 * d).sp,
            lineHeight = (36 * d).sp,
            letterSpacing = 0.5.sp,
        ),
        statValue = TextStyle(
            fontFamily = ArchivoBlackFamily,
            fontWeight = FontWeight.Black,
            fontSize = (32 * d).sp,
            lineHeight = (34 * d).sp,
        ),
        buttonLabel = TextStyle(
            fontFamily = PoppinsFamily,
            fontWeight = FontWeight.ExtraBold,
            fontSize = (15 * b).sp,
            lineHeight = (18 * b).sp,
            letterSpacing = 0.2.sp,
        ),
        // Deliberately not scaled by the width class. The nav pill keeps one
        // fixed geometry on every device, so a label that grew with the tier
        // would be the only thing in it that moved. Line height is left to the
        // font's own metrics: at 11sp an explicit 1.0 ratio clips descenders.
        navLabel = TextStyle(
            fontFamily = PoppinsFamily,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 11.sp,
            letterSpacing = 0.2.sp,
        ),
    )
}
