package com.reps.app.navigation

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.reps.app.core.theme.RepsGreen

/**
 * One layer of the pill's `box-shadow` stack.
 *
 * [spread] follows the CSS sign convention: negative shrinks the shape before it
 * is blurred, which is how the prototype keeps a wide, soft shadow from bleeding
 * out past the pill's own edges.
 */
private data class ShadowLayer(
    val blur: Dp,
    val offsetY: Dp,
    val spread: Dp,
    val color: Color,
)

/**
 * The three shadows from `.reps-nav__pill`, outermost first.
 *
 * CSS blur radii are about twice Android's, which takes a Gaussian sigma, so
 * each `blur` here is the prototype's value halved: 40 -> 20, 14 -> 7, 26 -> 13.
 */
private val NavPillShadows = listOf(
    ShadowLayer(20.dp, 16.dp, (-8).dp, Color.Black.copy(alpha = 0.55f)),
    ShadowLayer(7.dp, 4.dp, 0.dp, Color.Black.copy(alpha = 0.35f)),
    // The green rim-light that lifts the pill off a near-black background.
    ShadowLayer(13.dp, 0.dp, (-10).dp, RepsGreen.copy(alpha = 0.18f)),
)

/**
 * Draws the layered drop shadow behind a pill-shaped node.
 *
 * `Modifier.shadow` cannot express this: it gives one shadow with a single
 * elevation, and no way to tint one layer green while the others stay black.
 * Painting them by hand means going through the framework paint, which is the
 * only place `setShadowLayer` is exposed.
 *
 * Hardware-accelerated canvases only honour `setShadowLayer` for non-text shapes
 * from API 28. On 26 and 27 the pill simply renders without its shadow - it
 * still reads correctly against the near-black background, just flatter.
 */
internal fun Modifier.pillShadow(): Modifier = drawBehind {
    drawIntoCanvas { canvas ->
        val paint = Paint().asFrameworkPaint()
        NavPillShadows.forEach { layer ->
            val inset = -layer.spread.toPx()
            val left = inset
            val top = inset
            val right = size.width - inset
            val bottom = size.height - inset
            if (right <= left || bottom <= top) return@forEach

            paint.reset()
            paint.isAntiAlias = true
            // Only the shadow should land on the canvas; the pill's own fill is
            // drawn separately, on top, by the background modifier.
            paint.color = android.graphics.Color.TRANSPARENT
            paint.setShadowLayer(
                layer.blur.toPx(),
                0f,
                layer.offsetY.toPx(),
                layer.color.toArgb(),
            )

            val radius = (bottom - top) / 2f
            canvas.nativeCanvas.drawRoundRect(left, top, right, bottom, radius, radius, paint)
        }
        paint.reset()
    }
}
