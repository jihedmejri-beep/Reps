package com.reps.app.navigation

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.reps.app.core.theme.RepsGreen
import com.reps.app.core.theme.RepsOutline
import com.reps.app.core.theme.RepsSurfaceElevated
import com.reps.app.core.theme.RepsTextTertiary

private val BarHeight = 62.dp

/**
 * Bottom navigation for the five top-level tabs.
 *
 * The selected index is animated as a float rather than snapped, so the
 * highlight slides between tabs instead of cutting. Position is expressed as an
 * alignment bias, which [BiasAlignment] flips for RTL - that is what keeps the
 * slide going the right way in Arabic.
 */
@Composable
fun RepsBottomBar(
    selected: TopLevelTab,
    onSelect: (TopLevelTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    val tabs = remember { TopLevelTab.entries }
    val selectedIndex = tabs.indexOf(selected)

    // With an indicator exactly one slot wide, bias maps to index as index/2 - 1:
    // index 0 lands at bias -1 (start edge) and the last at +1 (end edge).
    val bias by animateFloatAsState(
        targetValue = selectedIndex / 2f - 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "navIndicatorBias",
    )

    Column(modifier = modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(RepsOutline))
        Box(
            Modifier
                .fillMaxWidth()
                .background(Color.Black)
                .navigationBarsPadding()
                .height(BarHeight),
        ) {
            Box(
                Modifier
                    .fillMaxWidth(1f / tabs.size)
                    .fillMaxHeight()
                    .padding(horizontal = 10.dp, vertical = 7.dp)
                    .align(BiasAlignment(horizontalBias = bias, verticalBias = 0f))
                    .background(RepsSurfaceElevated, RoundedCornerShape(12.dp)),
            )
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                tabs.forEach { tab ->
                    TabItem(
                        tab = tab,
                        isSelected = tab == selected,
                        onClick = { onSelect(tab) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun TabItem(
    tab: TopLevelTab,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tint by animateColorAsState(
        targetValue = if (isSelected) RepsGreen else RepsTextTertiary,
        label = "navTint",
    )
    val scale by animateFloatAsState(
        targetValue = if (isSelected) 1.12f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "navScale",
    )
    val label = stringResource(tab.labelRes)

    Column(
        modifier = modifier
            .fillMaxHeight()
            .selectable(
                selected = isSelected,
                onClick = onClick,
                role = Role.Tab,
                interactionSource = remember { MutableInteractionSource() },
                // The sliding highlight is the selection feedback; a ripple on
                // top of it just muddies the motion.
                indication = null,
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            painter = painterResource(tab.iconRes),
            // The label directly beneath already names the tab, so the icon
            // would only repeat it for screen readers.
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(21.dp).scale(scale),
        )
        Text(
            text = label,
            style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
            color = tint,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 4.dp).scale(scale),
        )
    }
}
