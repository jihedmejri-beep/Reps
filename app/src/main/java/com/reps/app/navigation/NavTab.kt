package com.reps.app.navigation

import androidx.annotation.DrawableRes
import androidx.compose.animation.animateColor
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import com.reps.app.core.theme.RepsGreen
import com.reps.app.core.theme.RepsTextTertiary
import com.reps.app.core.theme.RepsTheme
import kotlin.math.roundToInt

/**
 * One tab in the floating pill.
 *
 * Inactive it is icon-only; selected it grows to reveal its label beside the
 * icon. The label is not faded in place - it is genuinely clipped from zero
 * width outward, which is what makes the surrounding tabs slide aside.
 */
@Composable
internal fun NavTab(
    label: String,
    @DrawableRes iconRes: Int,
    isSelected: Boolean,
    labelMaxWidth: Dp,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dimens = RepsTheme.dimens
    val transition = updateTransition(isSelected, label = "navTab")

    val tint by transition.animateColor(
        transitionSpec = { tween(TintMs) },
        label = "tint",
    ) { selected -> if (selected) RepsGreen else RepsTextTertiary }

    val iconScale by transition.animateFloat(
        transitionSpec = { tween(IconScaleMs, easing = BackOut) },
        label = "iconScale",
    ) { selected -> if (selected) ActiveIconScale else 1f }

    val reveal by transition.animateFloat(
        transitionSpec = { tween(LabelRevealMs, easing = ExpoOut) },
        label = "labelReveal",
    ) { selected -> if (selected) 1f else 0f }

    val labelAlpha by transition.animateFloat(
        transitionSpec = { tween(LabelFadeMs) },
        label = "labelAlpha",
    ) { selected -> if (selected) 1f else 0f }

    val labelShift by transition.animateFloat(
        transitionSpec = { tween(LabelSlideMs, easing = ExpoOut) },
        label = "labelShift",
    ) { selected -> if (selected) 0f else -1f }

    Row(
        modifier = modifier
            .fillMaxHeight()
            .clip(PillShape)
            .selectable(
                selected = isSelected,
                onClick = onClick,
                role = Role.Tab,
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(color = RepsGreen),
            )
            .padding(horizontal = dimens.navTabPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(iconRes),
            // The label is clipped away while inactive, so the icon has to carry
            // the tab's name for screen readers on its own.
            contentDescription = label,
            tint = tint,
            modifier = Modifier.size(dimens.navIconSize).scale(iconScale),
        )

        // Present even at zero label width, matching the flex `gap` that stays
        // between the icon and a collapsed label in the prototype.
        Spacer(Modifier.width(IconLabelGap))

        Text(
            text = label,
            style = RepsTheme.textStyles.navLabel,
            color = tint,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Clip,
            modifier = Modifier
                // The icon already announces the tab; repeating it here would
                // make every tab read out twice.
                .clearAndSetSemantics { }
                .clipToBounds()
                .layout { measurable, constraints ->
                    // Measured at its natural width, capped, then reported at a
                    // fraction of that so the parent Row reflows as it reveals.
                    val placeable = measurable.measure(
                        constraints.copy(minWidth = 0, maxWidth = labelMaxWidth.roundToPx()),
                    )
                    val width = (placeable.width * reveal).roundToInt().coerceAtLeast(0)
                    layout(width, placeable.height) { placeable.place(0, 0) }
                }
                .graphicsLayer {
                    alpha = labelAlpha
                    translationX = labelShift * LabelSlide.toPx()
                },
        )
    }
}
