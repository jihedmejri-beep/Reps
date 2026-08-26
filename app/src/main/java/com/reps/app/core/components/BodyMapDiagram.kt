package com.reps.app.core.components

import android.graphics.Region
import android.graphics.RegionIterator
import android.graphics.Rect
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import coil3.compose.AsyncImage
import com.reps.app.core.svg.OverlaySvg
import com.reps.app.core.theme.RepsGreen
import kotlin.math.ceil
import kotlin.math.min
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** One tappable muscle on the body map: its lookup name and overlay asset. */
data class BodyMapRegion(
    val name: String,
    val overlayAsset: String,
)

/**
 * An interactive body diagram: the greyscale illustration with every muscle's
 * shapes painted faintly on top, the selected one lit up, and tap-to-select.
 *
 * Unlike [MuscleTargetDiagram] - which hands recoloured SVG files to Coil -
 * this parses each overlay into real paths so taps can be hit-tested against
 * the actual anatomy rather than a bounding box. The artwork is identical;
 * only the renderer differs, because Coil gives no access to shape geometry.
 *
 * Tapping empty space changes nothing; tapping the selected muscle again
 * clears it. Where two muscles overlap, the smaller of the two wins, which is
 * what a finger aiming between biceps and brachialis almost always means.
 */
@Composable
fun BodyMapDiagram(
    bodyAsset: String?,
    muscles: List<BodyMapRegion>,
    selectedMuscle: String?,
    onMuscleSelect: (String?) -> Unit,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
) {
    val parsed = rememberParsedMuscles(muscles)

    Box(
        modifier
            .aspectRatio(BODY_ASPECT)
            .semantics { contentDescription?.let { this.contentDescription = it } },
        contentAlignment = Alignment.Center,
    ) {
        if (bodyAsset != null) {
            AsyncImage(
                model = "file:///android_asset/$bodyAsset",
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
            )
        }

        Canvas(
            Modifier
                .fillMaxSize()
                .pointerInput(parsed, selectedMuscle) {
                    detectTapGestures { tap ->
                        val hit = hitTest(
                            tap,
                            parsed,
                            size.width.toFloat(),
                            size.height.toFloat(),
                        ) ?: return@detectTapGestures
                        onMuscleSelect(if (hit.name == selectedMuscle) null else hit.name)
                    }
                },
        ) {
            // Unselected first, so the selection reads even where shapes overlap.
            for (muscle in parsed.sortedBy { it.name == selectedMuscle }) {
                val alpha = if (muscle.name == selectedMuscle) SELECTED_ALPHA else IDLE_ALPHA
                drawMuscle(muscle, alpha)
            }
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawMuscle(
    muscle: ParsedMuscle,
    alpha: Float,
) {
    val scale = min(size.width / muscle.canvasWidth, size.height / muscle.canvasHeight)
    val offsetX = (size.width - muscle.canvasWidth * scale) / 2f
    val offsetY = (size.height - muscle.canvasHeight * scale) / 2f
    val color = RepsGreen.copy(alpha = alpha)
    withTransform({
        translate(offsetX, offsetY)
        scale(scale, scale, pivot = Offset.Zero)
    }) {
        for (path in muscle.paths) {
            drawPath(path, color)
        }
    }
}

/** Smallest-area match wins; null means the tap landed on no muscle. */
private fun hitTest(
    tap: Offset,
    parsed: List<ParsedMuscle>,
    boxWidth: Float,
    boxHeight: Float,
): ParsedMuscle? {
    var best: ParsedMuscle? = null
    for (muscle in parsed) {
        val scale = min(boxWidth / muscle.canvasWidth, boxHeight / muscle.canvasHeight)
        val svgX = (tap.x - (boxWidth - muscle.canvasWidth * scale) / 2f) / scale
        val svgY = (tap.y - (boxHeight - muscle.canvasHeight * scale) / 2f) / scale
        val inside = muscle.regions.any { it.contains(svgX.toInt(), svgY.toInt()) }
        if (inside && (best == null || muscle.hitArea < best.hitArea)) best = muscle
    }
    return best
}

private class ParsedMuscle(
    val name: String,
    val paths: List<Path>,
    /** Hit-test regions in the overlay's own canvas units. */
    val regions: List<Region>,
    val canvasWidth: Float,
    val canvasHeight: Float,
    /** Approximate filled area in canvas units - the overlap tiebreaker. */
    val hitArea: Float,
)

@Composable
private fun rememberParsedMuscles(muscles: List<BodyMapRegion>): List<ParsedMuscle> {
    val context = LocalContext.current
    val state = produceState(initialValue = emptyList(), muscles) {
        value = withContext(Dispatchers.IO) {
            muscles.mapNotNull { region ->
                runCatching {
                    val svg = context.assets.open(region.overlayAsset)
                        .bufferedReader().use { it.readText() }
                    parseMuscle(region.name, svg)
                }.getOrNull()
            }
        }
    }
    return state.value
}

private fun parseMuscle(name: String, svgText: String): ParsedMuscle? {
    val geometry = OverlaySvg.geometry(svgText) ?: return null
    val androidPaths = OverlaySvg.toPaths(geometry)
    if (androidPaths.isEmpty()) return null

    var area = 0f
    val clip = Region(0, 0, ceil(geometry.canvasWidth).toInt(), ceil(geometry.canvasHeight).toInt())
    val regions = androidPaths.map { path ->
        val region = Region()
        region.setPath(path, clip)
        area += regionArea(region)
        region
    }

    return ParsedMuscle(
        name = name,
        paths = androidPaths.map { it.asComposePath() },
        regions = regions,
        canvasWidth = geometry.canvasWidth,
        canvasHeight = geometry.canvasHeight,
        hitArea = area,
    )
}

private fun regionArea(region: Region): Float {
    val iterator = RegionIterator(region)
    val rect = Rect()
    var total = 0f
    while (iterator.next(rect)) total += rect.width().toFloat() * rect.height().toFloat()
    return total
}

private const val BODY_ASPECT = 200f / 369f
private const val IDLE_ALPHA = 0.16f
private const val SELECTED_ALPHA = 0.95f
