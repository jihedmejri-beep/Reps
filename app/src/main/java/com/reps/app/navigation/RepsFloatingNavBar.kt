package com.reps.app.navigation

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.reps.app.core.components.BackdropState
import com.reps.app.core.components.backdropBlur
import com.reps.app.core.components.rememberBackdropState
import com.reps.app.core.theme.RepsOffWhite
import com.reps.app.core.theme.RepsSurface
import com.reps.app.core.theme.RepsSurfaceElevated
import com.reps.app.core.theme.RepsTheme

/** `rgba(20, 20, 19, 0.72)` - RepsSurface, let through by the backdrop blur. */
private val PillFill = RepsSurface.copy(alpha = 0.72f)

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

/**
 * The floating bottom navigation: a translucent, blurred pill sitting above the
 * content rather than a bar docked to the bottom edge.
 *
 * Selection is shown three ways at once - a neutral indicator slides and resizes
 * behind the active tab, the icon tints green and pops, and the label unfolds
 * beside it. The indicator stays neutral on purpose: green on green would bury
 * the icon it is meant to be highlighting.
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
    val density = LocalDensity.current
    val tabs = remember { TopLevelTab.entries }
    val selectedIndex = tabs.indexOf(selected).coerceAtLeast(0)

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

    val labels = tabs.map { stringResource(it.labelRes) }
    val labelStyle = RepsTheme.textStyles.navLabel
    val measurer = rememberTextMeasurer()

    // Measured up front rather than read back from layout. Knowing every final
    // width analytically means the indicator can spring straight to where the
    // tab will end up, instead of chasing a box that is still resizing.
    val naturalLabelWidths = remember(labels, labelStyle, density, measurer) {
        labels.map { with(density) { measurer.measure(it, labelStyle).size.width.toDp() } }
    }

    // Measured off a string with an ascender and a descender so the height does
    // not depend on which labels happen to have tall glyphs.
    val labelHeight = remember(labelStyle, density, measurer) {
        with(density) { measurer.measure("Ay", labelStyle).size.height.toDp() }
    }

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
        val gapTotal = TabGap * (tabs.size - 1)
        val available = maxWidth - dimens.navPillPadding * 2 - gapTotal

        // Solved rather than assumed: at the reference padding, five tabs leave
        // roughly 32dp for the label on a 360dp screen, which renders "Workouts"
        // as "Work". Padding gives way first, then the icon gap, then the label.
        val geometry = NavBarLayout.solve(
            availableWidth = available.value,
            tabCount = tabs.size,
            iconSize = dimens.navIconSize.value,
            widestLabel = (naturalLabelWidths.maxOrNull() ?: 0.dp).value,
            referencePadding = dimens.navTabPadding.value,
            referenceGap = IconLabelGap.value,
            minPadding = MinTabPadding.value,
            minGap = MinIconLabelGap.value,
        )
        val tabPadding = geometry.tabPadding.dp
        val iconGap = geometry.iconGap.dp
        val labelCap = geometry.labelCap.dp

        val inactiveTabWidth = geometry.inactiveTabWidth(dimens.navIconSize.value).dp
        val labelWidths = naturalLabelWidths.map { it.coerceAtMost(labelCap) }

        // Every tab before the selected one is collapsed, so the indicator's
        // resting offset is a plain multiple of the collapsed width.
        val indicatorTargetX = (inactiveTabWidth + TabGap) * selectedIndex
        val indicatorTargetWidth = inactiveTabWidth + labelWidths[selectedIndex]

        val indicatorX by animateDpAsState(
            targetValue = indicatorTargetX,
            animationSpec = spring(IndicatorXDamping, IndicatorStiffness),
            label = "indicatorX",
        )
        val indicatorWidth by animateDpAsState(
            targetValue = indicatorTargetWidth,
            animationSpec = spring(IndicatorWidthDamping, IndicatorStiffness),
            label = "indicatorWidth",
        )

        // A large system font scale grows the label past the fixed 64dp pill, so
        // the height is the greater of the design value and what the content
        // actually needs. Kept exact rather than heightIn(min = ) because the
        // indicator fills this height and cannot resolve against a wrap.
        val pillHeight = maxOf(
            dimens.navHeight,
            maxOf(dimens.navIconSize * ActiveIconScale, labelHeight) +
                dimens.navPillPadding * 2 + 16.dp,
        )

        Box(
            Modifier
                .height(pillHeight)
                .pillShadow()
                .backdropBlur(backdrop, BackdropBlurRadius, PillShape)
                .background(PillFill, PillShape)
                .border(1.dp, PillBorder, PillShape)
                // Keeps each tab's ripple inside the pill's rounded ends.
                .clip(PillShape)
                .padding(dimens.navPillPadding),
        ) {
            Box(
                Modifier
                    // Dp offsets mirror under RTL, so the indicator travels the
                    // right way in Arabic without a second code path.
                    .offset(x = indicatorX)
                    .width(indicatorWidth)
                    .fillMaxHeight()
                    .background(RepsSurfaceElevated, PillShape)
                    .border(1.dp, IndicatorBorder, PillShape),
            )

            Row(horizontalArrangement = Arrangement.spacedBy(TabGap)) {
                tabs.forEachIndexed { index, tab ->
                    NavTab(
                        label = labels[index],
                        iconRes = tab.iconRes,
                        isSelected = index == selectedIndex,
                        labelMaxWidth = labelCap,
                        tabPadding = tabPadding,
                        iconGap = iconGap,
                        onClick = { onSelect(tab) },
                    )
                }
            }
        }
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
