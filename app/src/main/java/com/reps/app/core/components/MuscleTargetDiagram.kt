package com.reps.app.core.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.reps.app.R
import com.reps.app.core.theme.RepsGreen
import com.reps.app.core.theme.RepsTheme
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * A body diagram with this exercise's worked muscles painted on it.
 *
 * The catalogue ships wger's anatomical artwork: two body illustrations
 * (`svg/body/front.svg`, `svg/body/back.svg`) and, for each of the 15 muscles,
 * a small overlay SVG containing just that muscle's shapes on the same 200-unit
 * canvas. Stacking an overlay on the matching body side is what the two were
 * drawn for.
 *
 * The overlays arrive filled wger red (`#fc0000`). Rather than ship a recoloured
 * copy - which would mean modifying the source artwork - the fill is rewritten
 * on the way to the renderer, the same technique [MuscleMapDiagram] already uses
 * for the bundled full-body map: primary muscles take brand green, secondary
 * ones a muted variant, so the distinction survives even in greyscale.
 *
 * Both sides are drawn whenever the exercise works muscles on both, because
 * hiding one would silently drop half the targeting information.
 */
@Composable
fun MuscleTargetDiagram(
    /** Overlay asset paths for primary muscles, e.g. `svg/muscles/main/muscle-4...svg`. */
    frontPrimary: List<String>,
    frontSecondary: List<String>,
    backPrimary: List<String>,
    backSecondary: List<String>,
    frontBodyAsset: String?,
    backBodyAsset: String?,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
) {
    val hasFront = frontBodyAsset != null && (frontPrimary + frontSecondary).isNotEmpty()
    val hasBack = backBodyAsset != null && (backPrimary + backSecondary).isNotEmpty()

    // Nothing to draw: 122 of the catalogue's exercises record no muscles at all.
    if (!hasFront && !hasBack) {
        MuscleTargetUnavailable(modifier)
        return
    }

    Row(
        modifier.fillMaxWidth().semantics {
            contentDescription?.let { this.contentDescription = it }
        },
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (hasFront) {
            BodySide(
                bodyAsset = frontBodyAsset!!,
                primary = frontPrimary,
                secondary = frontSecondary,
                label = stringResource(R.string.exercise_body_front),
                modifier = Modifier.weight(1f),
            )
        }
        if (hasBack) {
            BodySide(
                bodyAsset = backBodyAsset!!,
                primary = backPrimary,
                secondary = backSecondary,
                label = stringResource(R.string.exercise_body_back),
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/** One body illustration with its overlays stacked on top, plus a caption. */
@Composable
private fun BodySide(
    bodyAsset: String,
    primary: List<String>,
    secondary: List<String>,
    label: String,
    modifier: Modifier = Modifier,
) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .fillMaxWidth()
                // The catalogue records every body diagram as 200x369.
                .aspectRatio(BODY_ASPECT),
            contentAlignment = Alignment.Center,
        ) {
            AsyncImage(
                model = remember(bodyAsset) { AssetSvg(bodyAsset) },
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
            )
            // Secondary first so a primary overlay wins where the two intersect.
            // Both roles use the brand green and separate on strength: the
            // artwork underneath is greyscale, so a grey secondary tint would
            // disappear into it entirely.
            secondary.forEach { asset ->
                MuscleOverlay(asset, RepsGreen, SECONDARY_ALPHA)
            }
            primary.forEach { asset ->
                MuscleOverlay(asset, RepsGreen, PRIMARY_ALPHA)
            }
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = RepsTheme.colors.textSecondary,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

/**
 * One muscle's shapes, recoloured and faded in.
 *
 * The recoloured file is written to the cache on every entry rather than reused
 * if present: a stale file would survive an app update and keep serving a tint
 * baked from an older palette. Rewriting also bumps `lastModified`, which is
 * part of Coil's cache key for file models, so its bitmap cache invalidates
 * along with it - the same reasoning as [MuscleMapDiagram].
 */
@Composable
private fun MuscleOverlay(assetPath: String, tint: Color, targetAlpha: Float) {
    val context = LocalContext.current
    val tintHex = remember(tint) { "#%06X".format(0xFFFFFF and tint.toArgb()) }

    val file by produceState<File?>(initialValue = null, assetPath, tintHex) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val svg = context.assets.open(assetPath).bufferedReader().use { it.readText() }
                val name = assetPath.substringAfterLast('/').substringBeforeLast('.')
                File(context.cacheDir, "muscle_${name}_${tintHex.drop(1)}.svg").apply {
                    writeText(recolour(svg, tintHex))
                }
            }.getOrNull()
        }
    }

    // A missing or unreadable overlay leaves the body diagram intact rather than
    // failing the whole screen.
    val current = file ?: return
    val alpha by animateFloatAsState(targetValue = targetAlpha, label = "muscleOverlayAlpha")
    AsyncImage(
        model = current,
        contentDescription = null,
        modifier = Modifier.fillMaxSize().alpha(alpha),
    )
}

@Composable
private fun MuscleTargetUnavailable(modifier: Modifier = Modifier) {
    Box(
        modifier.fillMaxWidth().padding(vertical = 24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(R.string.exercise_no_muscle_data),
            style = MaterialTheme.typography.bodySmall,
            color = RepsTheme.colors.textTertiary,
        )
    }
}

/**
 * Wraps an asset path so Coil's SVG decoder is handed a stream it can sniff.
 * Coil resolves `file:///android_asset/...` natively.
 */
private fun AssetSvg(assetPath: String) = "file:///android_asset/$assetPath"

private const val BODY_ASPECT = 200f / 369f
private const val PRIMARY_ALPHA = 0.95f
private const val SECONDARY_ALPHA = 0.42f

/**
 * wger's overlays carry their colour in a `style="...;fill:#fc0000;..."`
 * declaration on each path, plus a baked-in `opacity`. Both are replaced: the
 * opacity is handled by the composable so the two roles can differ.
 */
private val FILL_REGEX = Regex("""fill:\s*#[0-9a-fA-F]{3,8}""")
private val OPACITY_REGEX = Regex("""(?<![-\w])opacity:\s*[0-9.]+""")

private fun recolour(svg: String, tintHex: String): String = svg
    .replace(FILL_REGEX, "fill:$tintHex")
    .replace(OPACITY_REGEX, "opacity:1")
