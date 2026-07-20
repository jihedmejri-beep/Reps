package com.reps.app.navigation

/**
 * Resolved horizontal geometry for the nav pill. All values are dp.
 */
data class NavBarGeometry(
    /** Horizontal padding inside one tab. */
    val tabPadding: Float,
    /** Space between a tab's icon and its label. */
    val iconGap: Float,
    /** Widest a label may draw before it is clipped. */
    val labelCap: Float,
) {
    /** Width of a tab whose label is collapsed. */
    fun inactiveTabWidth(iconSize: Float): Float = tabPadding * 2 + iconSize + iconGap
}

/**
 * Works out how five tabs plus one expanded label fit the width actually
 * available, rather than assuming the reference padding always fits.
 *
 * The naive version reserved every tab at full padding and gave the label
 * whatever was left, which on a 360dp screen is about 32dp - enough to render
 * "Workouts" as "Work". Padding is the right place to absorb a shortfall:
 * trimming 2dp off each side of five tabs frees 20dp, where the same 20dp taken
 * from the label costs three characters.
 *
 * So the cascade is padding, then the icon-to-label gap, and only then the label
 * itself. Clipping a word is the last resort, not the first.
 */
object NavBarLayout {

    fun solve(
        /** Width for the tabs: pill width less its own padding and inter-tab gaps. */
        availableWidth: Float,
        tabCount: Int,
        iconSize: Float,
        /**
         * The widest label of any tab, not the selected one. Solving for the
         * selected label would re-derive the padding on every switch, so all
         * five tabs would twitch each time the user changed tab.
         */
        widestLabel: Float,
        referencePadding: Float,
        referenceGap: Float,
        minPadding: Float,
        minGap: Float,
    ): NavBarGeometry {
        require(tabCount > 0) { "tabCount must be positive" }

        // 1. Tighten the tab padding until the widest label fits.
        val paddingFit =
            (availableWidth - (iconSize + referenceGap) * tabCount - widestLabel) / (2 * tabCount)
        val tabPadding = paddingFit.coerceIn(minPadding, referencePadding)

        // 2. Still short, so close the icon-to-label gap.
        val gapRoom = availableWidth - (tabPadding * 2 + iconSize) * tabCount - widestLabel
        val iconGap = (gapRoom / tabCount).coerceIn(minGap, referenceGap)

        // 3. Out of room to give: clip whatever is left over.
        val inactiveTabWidth = tabPadding * 2 + iconSize + iconGap
        val labelCap = (availableWidth - inactiveTabWidth * tabCount).coerceAtLeast(0f)

        return NavBarGeometry(
            tabPadding = tabPadding,
            iconGap = iconGap,
            labelCap = labelCap,
        )
    }
}
