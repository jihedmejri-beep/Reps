package com.reps.app.core.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Width buckets the layout reacts to.
 *
 * The app is portrait-locked, so width is what actually varies between devices:
 * a 5" budget phone reports ~320dp, a Pixel ~411dp, and an unfolded foldable
 * ~670dp. Height is handled by scrolling, not by branching.
 */
enum class WindowWidthClass {
    /** < 360dp - small/older phones. Tighten padding, shrink display type. */
    COMPACT,

    /** 360-599dp - the great majority of phones. The reference design. */
    MEDIUM,

    /** >= 600dp - large phones, unfolded foldables, tablets. */
    EXPANDED,
    ;

    companion object {
        fun fromWidthDp(widthDp: Int): WindowWidthClass = when {
            widthDp < 360 -> COMPACT
            widthDp < 600 -> MEDIUM
            else -> EXPANDED
        }
    }
}

/**
 * Spacing and sizing tokens that vary by screen width.
 *
 * Every screen pulls its padding and gaps from here rather than hardcoding dp,
 * so a single set of values controls how the app breathes on each device.
 */
@Immutable
data class RepsDimens(
    /** Horizontal page margin. */
    val screenPadding: Dp,
    /** Padding inside a card. */
    val cardPadding: Dp,
    /** Vertical gap between the major blocks on a screen. */
    val sectionGap: Dp,
    /** Gap between closely related items. */
    val itemGap: Dp,
    val buttonHeight: Dp,
    val fieldHeight: Dp,
    val iconSize: Dp,
    val navIconSize: Dp,
    val avatarSize: Dp,
    /** Cap on content width, so text never runs edge to edge on a tablet. */
    val maxContentWidth: Dp,
    /** Multiplier applied to the display/headline type scale. */
    val typeScale: Float,

    // ---- Floating bottom navigation -------------------------------------
    /** Height of the nav pill itself, excluding its margins. */
    val navHeight: Dp,
    /** Gap between the pill and the left/right screen edges. */
    val navSideMargin: Dp,
    /** Gap between the pill and the bottom inset. */
    val navBottomMargin: Dp,
    /** Inset between the pill edge and the tabs, which the indicator sits in. */
    val navPillPadding: Dp,
    /**
     * Preferred horizontal padding inside one tab. A narrow screen tightens
     * this before it will clip a label - see [NavBarLayout].
     */
    val navTabPadding: Dp,
) {
    /**
     * Bottom padding a scrolling tab screen needs so its last item can clear the
     * floating pill. Excludes the system navigation inset, which the screen adds
     * itself - see `navBarClearance()`.
     */
    val navClearance: Dp get() = navHeight + navBottomMargin + 22.dp
}

private val CompactDimens = RepsDimens(
    screenPadding = 12.dp,
    cardPadding = 12.dp,
    sectionGap = 10.dp,
    itemGap = 6.dp,
    buttonHeight = 48.dp,
    fieldHeight = 52.dp,
    iconSize = 18.dp,
    navIconSize = 19.dp,
    avatarSize = 36.dp,
    maxContentWidth = Dp.Infinity,
    typeScale = 0.86f,
    navHeight = 64.dp,
    navSideMargin = 14.dp,
    navBottomMargin = 18.dp,
    navPillPadding = 6.dp,
    navTabPadding = 11.dp,
)

private val MediumDimens = RepsDimens(
    screenPadding = 16.dp,
    cardPadding = 16.dp,
    sectionGap = 14.dp,
    itemGap = 8.dp,
    buttonHeight = 54.dp,
    fieldHeight = 56.dp,
    iconSize = 20.dp,
    navIconSize = 21.dp,
    avatarSize = 40.dp,
    maxContentWidth = Dp.Infinity,
    typeScale = 1.0f,
    navHeight = 64.dp,
    navSideMargin = 14.dp,
    navBottomMargin = 18.dp,
    navPillPadding = 6.dp,
    navTabPadding = 14.dp,
)

private val ExpandedDimens = RepsDimens(
    screenPadding = 24.dp,
    cardPadding = 20.dp,
    sectionGap = 18.dp,
    itemGap = 10.dp,
    buttonHeight = 58.dp,
    fieldHeight = 60.dp,
    iconSize = 22.dp,
    navIconSize = 23.dp,
    avatarSize = 44.dp,
    // A form wider than this stops reading as a column and starts feeling
    // stretched, which is how a phone layout looks broken on a foldable.
    maxContentWidth = 460.dp,
    typeScale = 1.1f,
    // The pill keeps its phone geometry on a tablet. Stretching it to a 600dp+
    // width would put the tabs beyond a thumb's reach, which is the one thing a
    // bottom bar exists to avoid.
    navHeight = 64.dp,
    navSideMargin = 14.dp,
    navBottomMargin = 18.dp,
    navPillPadding = 6.dp,
    navTabPadding = 14.dp,
)

fun dimensFor(widthClass: WindowWidthClass): RepsDimens = when (widthClass) {
    WindowWidthClass.COMPACT -> CompactDimens
    WindowWidthClass.MEDIUM -> MediumDimens
    WindowWidthClass.EXPANDED -> ExpandedDimens
}
