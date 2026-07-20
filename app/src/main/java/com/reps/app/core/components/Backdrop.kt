package com.reps.app.core.components

import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.toIntSize

/**
 * Frosted-glass backdrop, the CSS `backdrop-filter: blur()` the web prototype's
 * floating nav pill is built on.
 *
 * Compose has no equivalent: `Modifier.blur` blurs a node's *own* content, not
 * what sits behind it. So the screen is recorded into a [GraphicsLayer] as it
 * draws, a second layer replays that recording through a [BlurEffect], and the
 * pill samples the blurred copy at its own position.
 *
 * Two layers rather than one because a layer's render effect is read by the
 * render thread when the layer is drawn. The screen and the pill draw the same
 * source in a single frame, so toggling the effect between those two draws would
 * be a race - the second layer just holds the blurred variant permanently.
 *
 * [BlurEffect] needs RenderEffect, which is API 31+. Below that the modifier is
 * a no-op and the pill falls back to a flat translucent fill, which is why the
 * caller passes an opaque-enough background colour to stand on its own.
 */
@Stable
class BackdropState internal constructor(
    internal val source: GraphicsLayer,
    internal val blurred: GraphicsLayer,
) {
    internal var sourceOrigin by mutableStateOf(Offset.Zero)
    internal var sourceSize by mutableStateOf(IntSize.Zero)

    internal companion object {
        val Supported: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    }
}

@Composable
fun rememberBackdropState(): BackdropState {
    val source = rememberGraphicsLayer()
    val blurred = rememberGraphicsLayer()
    return remember(source, blurred) { BackdropState(source, blurred) }
}

/**
 * Marks this node's content as the thing a [backdropBlur] samples.
 *
 * Anything that reads the backdrop must be a *sibling* drawn after this node,
 * never a descendant - a descendant would be recorded into the source and blur
 * itself, frame over frame.
 */
fun Modifier.backdropSource(state: BackdropState): Modifier = this
    .onGloballyPositioned {
        state.sourceOrigin = it.positionInWindow()
        state.sourceSize = it.size
    }
    .drawWithContent {
        if (!BackdropState.Supported) {
            drawContent()
            return@drawWithContent
        }
        // Recorded at the draw size rather than the size stashed by
        // onGloballyPositioned: that callback has not necessarily run before the
        // first draw, and recording at zero would blank the whole screen.
        state.source.record(this, layoutDirection, size.toIntSize()) {
            this@drawWithContent.drawContent()
        }
        drawLayer(state.source)
    }

/**
 * Draws the blurred backdrop behind this node, clipped to [shape].
 *
 * [radius] is the Android blur sigma. CSS blur radii are roughly twice that, so
 * the prototype's `blur(22px)` maps to about 11dp here.
 */
@Composable
fun Modifier.backdropBlur(
    state: BackdropState,
    radius: Dp,
    shape: Shape,
): Modifier {
    if (!BackdropState.Supported) return this

    var origin by remember { mutableStateOf(Offset.Zero) }

    return this
        .onGloballyPositioned { origin = it.positionInWindow() }
        .drawBehind {
            val sourceSize = state.sourceSize
            if (sourceSize.width == 0 || sourceSize.height == 0) return@drawBehind

            val sigma = radius.toPx()
            // Decal rather than Clamp: clamping smears the screen's edge pixels
            // outward, which reads as a bright halo along the pill's rim.
            state.blurred.renderEffect = BlurEffect(sigma, sigma, TileMode.Decal)
            state.blurred.record(this, layoutDirection, sourceSize) {
                drawLayer(state.source)
            }

            val path = Path().apply {
                when (val outline = shape.createOutline(size, layoutDirection, this@drawBehind)) {
                    is Outline.Rectangle -> addRect(outline.rect)
                    is Outline.Rounded -> addRoundRect(outline.roundRect)
                    is Outline.Generic -> addPath(outline.path)
                }
            }

            // The recording is in the source's coordinate space, so shift it by
            // this node's offset within that space before sampling.
            val dx = origin.x - state.sourceOrigin.x
            val dy = origin.y - state.sourceOrigin.y
            clipPath(path) {
                translate(-dx, -dy) { drawLayer(state.blurred) }
            }
        }
}
