package com.reps.app.feature.nutrition.assistant

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import com.reps.app.R
import com.reps.app.core.theme.PillShape
import com.reps.app.core.theme.RepsGreen
import com.reps.app.core.theme.RepsOnGreen
import com.reps.app.core.theme.RepsTheme

private val FabSize = 58.dp

/**
 * Opens the nutrition assistant from the Nutrition tab.
 *
 * Built from a green circle and a shadow rather than Material's
 * `FloatingActionButton` for the same reason the buttons in `RepsButton` are:
 * the design wants the brand green tinting its own glow, which the Material
 * component's elevation does not do.
 */
@Composable
fun NutritionAssistantFab(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.92f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "fabScale",
    )

    // Sized past the circle so the AI badge has somewhere to sit without being
    // clipped, and so the touch target clears the 48dp minimum comfortably.
    Box(modifier.size(FabSize + 10.dp), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .size(FabSize)
                .scale(scale)
                .shadow(
                    elevation = 16.dp,
                    shape = CircleShape,
                    ambientColor = RepsGreen,
                    spotColor = RepsGreen,
                )
                .background(RepsGreen, CircleShape)
                .clickable(
                    interactionSource = interactionSource,
                    indication = ripple(color = RepsOnGreen),
                    role = Role.Button,
                    onClick = onClick,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Outlined.AutoAwesome,
                contentDescription = stringResource(R.string.assistant_fab_cd),
                tint = RepsOnGreen,
                modifier = Modifier.size(24.dp),
            )
        }

        // Decorative: the button already announces itself as the AI assistant,
        // so the badge is taken out of the accessibility tree rather than read
        // out as a second, contextless "AI".
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .clearAndSetSemantics {}
                .background(RepsTheme.colors.surfaceElevated, PillShape)
                .border(1.dp, RepsGreen, PillShape)
                .padding(horizontal = 5.dp, vertical = 1.dp),
        ) {
            Text(
                text = stringResource(R.string.assistant_badge),
                style = MaterialTheme.typography.labelSmall,
                color = RepsGreen,
            )
        }
    }
}
