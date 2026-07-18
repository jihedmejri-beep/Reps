package com.reps.app.feature.splash

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.reps.app.core.theme.RepsGreen

/** Width of a bar relative to the gap between bars, measured from the video. */
private const val BAR_TO_GAP = 6f / 14f

/** Phase the static mark is frozen at: the peak of the leading bar. */
private const val STATIC_PHASE = 0.25f

/**
 * The three ascending bars from the REPS mark, animated as an equaliser.
 *
 * The motion itself lives in [RepsBarsModel] so it can be verified against the
 * reference video in a unit test; this composable only draws it.
 */
@Composable
fun RepsBarsAnimation(
    modifier: Modifier = Modifier,
    color: Color = RepsGreen,
    running: Boolean = true,
) {
    val transition = rememberInfiniteTransition(label = "bars")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(RepsBarsModel.CYCLE_MS, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "barPhase",
    )

    Canvas(modifier) {
        drawBars(phase = if (running) phase else STATIC_PHASE, color = color)
    }
}

private fun DrawScope.drawBars(phase: Float, color: Color) {
    val maxHeight = size.height
    // The tallest bar's lean pushes it right, so that slack is reserved before
    // laying the bars out - otherwise the last bar would clip.
    val slantRoom = maxHeight * RepsBarsModel.SLANT
    val baselineWidth = (size.width - slantRoom).coerceAtLeast(1f)

    val count = RepsBarsModel.BAR_COUNT
    val barWidth = baselineWidth / (count + (count - 1) * BAR_TO_GAP)
    val gap = barWidth * BAR_TO_GAP
    val baseline = size.height

    repeat(count) { index ->
        val height = (RepsBarsModel.heightFraction(index, phase) * maxHeight).coerceAtLeast(1f)
        val left = index * (barWidth + gap)
        val lean = height * RepsBarsModel.SLANT

        val path = Path().apply {
            moveTo(left, baseline)
            lineTo(left + barWidth, baseline)
            lineTo(left + barWidth + lean, baseline - height)
            lineTo(left + lean, baseline - height)
            close()
        }
        drawPath(path, color)
    }
}

/** Static version of the mark, for places that must not animate. */
fun DrawScope.drawRepsBars(color: Color = RepsGreen) =
    drawBars(phase = STATIC_PHASE, color = color)
