package com.reps.app.navigation

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.reps.app.core.components.BackdropState
import com.reps.app.core.components.backdropBlur
import com.reps.app.core.components.rememberBackdropState
import com.reps.app.core.theme.RepsCarbs
import com.reps.app.core.theme.RepsFat
import com.reps.app.core.theme.RepsGreen
import com.reps.app.core.theme.RepsNavNutrition
import com.reps.app.core.theme.RepsProtein
import com.reps.app.core.theme.RepsOffWhite
import com.reps.app.core.theme.RepsTheme

/** `border: 1px solid rgba(244, 242, 234, 0.07)` */
private val PillBorder = RepsOffWhite.copy(alpha = 0.07f)

/** `box-shadow: inset 0 0 0 1px rgba(244, 242, 234, 0.05)` on the indicator. */
private val IndicatorBorder = RepsOffWhite.copy(alpha = 0.05f)

/**
 * CSS `blur(22px)` in Android's Gaussian sigma, which runs at about half the
 * radius for a visually equivalent result.
 */
private val BackdropBlurRadius = 11.dp

/** Extra travel so the pill's shadow clears the screen edge when hidden. */
private val HiddenOvershoot = 20.dp

/** Inset of the sliding indicator from the edges of its tab slot. */
private val IndicatorInsetX = 8.dp
private val IndicatorInsetY = 8.dp

/**
 * The floating bottom navigation: a translucent, blurred pill sitting above the
 * content rather than a bar docked to the bottom edge.
 *
 * Icon-only, in the Instagram register - no labels. Selection reads two ways: a
 * neutral indicator slides behind the active icon, and the icon tints green and
 * pops. The indicator stays neutral on purpose; green on green would bury the
 * icon it is meant to be highlighting. With no labels every tab is the same
 * width, so the indicator is one fixed slot that only ever slides.
 *
 * @param hidden true while the user is scrolling down, which slides the whole
 *   pill off the bottom edge instead of leaving it over the content.
 */
@Composable
fun RepsFloatingNavBar(
    selected: TopLevelTab,
    onSelect: (TopLevelTab) -> Unit,
    backdrop: BackdropState,
    hidden: Boolean,
    modifier: Modifier = Modifier,
) {
    val dimens = RepsTheme.dimens
    val tabs = remember { TopLevelTab.entries }
    val selectedIndex = tabs.indexOf(selected).coerceAtLeast(0)

    // The pill fill is the theme surface let through the blur at 0.72 alpha, so
    // it tracks light/dark. Computed here rather than as a top-level constant
    // because the theme colour is only available inside a composition.
    val pillFill = RepsTheme.colors.surface.copy(alpha = 0.72f)

    val slide by animateFloatAsState(
        targetValue = if (hidden) 1f else 0f,
        animationSpec = tween(NavSlideMs, easing = ExpoOut),
        label = "navSlide",
    )
    val fade by animateFloatAsState(
        targetValue = if (hidden) 0f else 1f,
        animationSpec = tween(NavFadeMs),
        label = "navFade",
    )

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                translationY = slide * (size.height + HiddenOvershoot.toPx())
                alpha = fade
            }
            .navigationBarsPadding()
            .padding(
                start = dimens.navSideMargin,
                end = dimens.navSideMargin,
                bottom = dimens.navBottomMargin,
            ),
        contentAlignment = Alignment.Center,
    ) {
        // Icons only, so the tabs split the pill evenly and the indicator is a
        // single slot that slides - no per-label width to solve for.
        val innerWidth = maxWidth - dimens.navPillPadding * 2
        val tabWidth = innerWidth / tabs.size

        val indicatorX by animateDpAsState(
            targetValue = tabWidth * selectedIndex,
            animationSpec = spring(IndicatorXDamping, IndicatorStiffness),
            label = "indicatorX",
        )

        val indicatorColor by animateColorAsState(
            targetValue = selected.accentColor(),
            animationSpec = tween(TintMs),
            label = "indicatorColor",
        )

        Box(
            Modifier
                .height(dimens.navHeight)
                .pillShadow()
                .backdropBlur(backdrop, BackdropBlurRadius, PillShape)
                .background(pillFill, PillShape)
                .border(1.dp, PillBorder, PillShape)
                // Keeps each tab's ripple inside the pill's rounded ends.
                .clip(PillShape)
                .padding(dimens.navPillPadding),
        ) {
            // The neutral highlight. Dp offsets mirror under RTL, so it travels
            // the right way in Arabic without a second code path.
            Box(
                Modifier
                    .offset(x = indicatorX)
                    .width(tabWidth)
                    .fillMaxHeight()
                    .padding(horizontal = IndicatorInsetX, vertical = IndicatorInsetY)
                    .background(RepsTheme.colors.surfaceElevated, PillShape)
                    .background(indicatorColor.copy(alpha = 0.08f), PillShape)
                    .border(1.dp, indicatorColor.copy(alpha = 0.12f), PillShape),
            )

            Row(Modifier.fillMaxSize()) {
                tabs.forEach { tab ->
                    NavIconTab(
                        tab = tab,
                        isSelected = tab == selected,
                        onClick = { onSelect(tab) },
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                    )
                }
            }
        }
    }
}

/**
 * Each tab gets its own accent so the icon tint identifies the section at a
 * glance. The hues are drawn from the existing palette to stay harmonious.
 */
private fun TopLevelTab.accentColor() = when (this) {
    TopLevelTab.HOME      -> RepsGreen       // brand green  – home base
    TopLevelTab.PROGRESS  -> RepsCarbs       // golden amber – charts, growth
    TopLevelTab.WORKOUTS  -> RepsProtein     // warm coral   – energy, intensity
    TopLevelTab.NUTRITION -> RepsNavNutrition // fresh teal   – food, health
    TopLevelTab.PROFILE   -> RepsFat         // calm blue    – personal, identity
}

@Composable
private fun NavIconTab(
    tab: TopLevelTab,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dimens = RepsTheme.dimens
    val tint by animateColorAsState(
        targetValue = if (isSelected) tab.accentColor() else RepsTheme.colors.textTertiary,
        animationSpec = tween(TintMs),
        label = "navTint",
    )
    val scale by animateFloatAsState(
        targetValue = if (isSelected) ActiveIconScale else 1f,
        animationSpec = tween(IconScaleMs, easing = BackOut),
        label = "navIconScale",
    )

    Box(
        modifier = modifier
            .clip(PillShape)
            .selectable(
                selected = isSelected,
                onClick = onClick,
                role = Role.Tab,
                interactionSource = remember { MutableInteractionSource() },
                // The sliding indicator is the selection feedback; a ripple on
                // top of it just muddies the motion.
                indication = null,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(tab.iconRes),
            // No visible label, so the icon has to name the tab for a screen
            // reader on its own.
            contentDescription = stringResource(tab.labelRes),
            tint = tint,
            modifier = Modifier.size(dimens.navIconSize).scale(scale),
        )
    }
}

@Preview(widthDp = 360, heightDp = 120, backgroundColor = 0xFF0B0B0A, showBackground = true)
@Composable
private fun NavBarPreview() {
    RepsTheme {
        var selected by remember { mutableStateOf(TopLevelTab.HOME) }
        RepsFloatingNavBar(
            selected = selected,
            onSelect = { selected = it },
            backdrop = rememberBackdropState(),
            hidden = false,
        )
    }
}

@Preview(widthDp = 320, heightDp = 120, backgroundColor = 0xFF0B0B0A, showBackground = true)
@Composable
private fun NavBarCompactPreview() {
    RepsTheme {
        RepsFloatingNavBar(
            selected = TopLevelTab.NUTRITION,
            onSelect = {},
            backdrop = rememberBackdropState(),
            hidden = false,
        )
    }
}
