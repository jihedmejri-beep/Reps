package com.reps.app.feature.nutrition.assistant

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
 *
 * Three layers of motion, each with one job: the entrance spring announces the
 * button arriving, the breathing halo marks it as alive without demanding a
 * tap, and the slow sheen sweep is what makes it read as "AI" before the badge
 * is read. Press feedback stays a plain scale-down.
 */
@Composable
fun NutritionAssistantFab(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()

    // Entrance: pops in once with a slight overshoot instead of just appearing.
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    val entrance by animateFloatAsState(
        targetValue = if (shown) 1f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "fabEntrance",
    )

    // Press feedback.
    val pressScale by animateFloatAsState(
        targetValue = if (pressed) 0.92f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "fabScale",
    )

    // The halo breathes on a slow loop; hidden while pressed so the shrink
    // reads clearly against it.
    val pulse = rememberInfiniteTransition(label = "fabPulse")
    val haloScale by pulse.animateFloat(
        initialValue = 1f,
        targetValue = 1.18f,
        animationSpec = infiniteRepeatable(
            animation = tween(1600),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "haloScale",
    )
    val haloAlpha by pulse.animateFloat(
        initialValue = 0.20f,
        targetValue = 0.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(1600),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "haloAlpha",
    )

    // Sheen: a narrow light band orbiting the circle every few seconds.
    val sheen = rememberInfiniteTransition(label = "fabSheen")
    val sheenAngle by sheen.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(4200, easing = LinearEasing)),
        label = "sheenAngle",
    )

    // Sized past the circle so the AI badge has somewhere to sit without being
    // clipped, and so the touch target clears the 48dp minimum comfortably.
    Box(modifier.size(FabSize + 10.dp), contentAlignment = Alignment.Center) {
        if (!pressed) {
            Box(
                Modifier
                    .matchParentSize()
                    .scale(haloScale * entrance)
                    .background(RepsGreen.copy(alpha = haloAlpha), CircleShape),
            )
        }

        Box(
            Modifier
                .size(FabSize)
                .scale(pressScale * entrance)
                .shadow(
                    elevation = 16.dp,
                    shape = CircleShape,
                    ambientColor = RepsGreen,
                    spotColor = RepsGreen,
                )
                .clip(CircleShape)
                .background(RepsGreen)
                .clickable(
                    interactionSource = interactionSource,
                    indication = ripple(color = RepsOnGreen),
                    role = Role.Button,
                    onClick = onClick,
                ),
            contentAlignment = Alignment.Center,
        ) {
            // Rotating highlight painted under the icon - the sparkle reads as
            // part of the surface rather than stuck on top of it.
            SheenOverlay(angleDegrees = sheenAngle)

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

/**
 * A narrow white band sweeping around the button's face. The band lives in an
 * oversized layer so its corners never uncover the circle while rotating; the
 * parent's clip keeps everything inside the button's shape.
 */
@Composable
private fun SheenOverlay(angleDegrees: Float) {
    Box(
        Modifier
            .size(FabSize * 2f)
            .rotate(angleDegrees)
            .background(sheenBand()),
    )
}

/** A narrow white band along one diagonal, transparent elsewhere. */
private fun sheenBand(): Brush = Brush.linearGradient(
    0.44f to Color.Transparent,
    0.50f to Color.White.copy(alpha = 0.26f),
    0.56f to Color.Transparent,
)
