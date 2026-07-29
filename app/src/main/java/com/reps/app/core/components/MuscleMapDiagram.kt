package com.reps.app.core.components

import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import coil3.compose.AsyncImage
import com.reps.app.core.theme.RepsGreen
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Full-body muscle map (front + back), rendered from [MUSCLE_MAP_ASSET]. Every
 * element in that SVG tagged `data-muscle="<name>"` gets its fill forced to
 * brand green when `<name>` is in [highlightedMuscles]; everything else keeps
 * the illustrated fill.
 *
 * The source SVG mixes a handful of solid, individually-verified muscle
 * shapes (chest, shoulders, abs, back) with coarser accent clusters (arms,
 * legs) - the artwork doesn't separate biceps from triceps or quads from
 * hamstrings as distinct fillable shapes, so those categories highlight the
 * real detail lines within that limb rather than a solid limb silhouette.
 *
 * Names are matched case-insensitively against the tags actually present in
 * the asset: chest, shoulders, abs, back, arms, legs.
 */
@Composable
fun MuscleMapDiagram(
    highlightedMuscles: List<String>,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
) {
    val context = LocalContext.current
    val highlightHex = remember { "#%06X".format(0xFFFFFF and RepsGreen.toArgb()) }
    val targets = remember(highlightedMuscles) {
        highlightedMuscles.map { it.trim().lowercase() }.filter { it.isNotEmpty() }.sorted()
    }
    // Rebuilt on every entry rather than reused if present: a cached file would
    // survive app updates and keep serving a highlight baked from an older copy
    // of the asset. Rewriting also bumps lastModified, which is part of Coil's
    // cache key for file models, so its bitmap cache invalidates with us.
    val renderFile by produceState<File?>(initialValue = null, targets, highlightHex) {
        value = withContext(Dispatchers.IO) {
            val template =
                context.assets.open(MUSCLE_MAP_ASSET).bufferedReader().use { it.readText() }
            val cacheKey = if (targets.isEmpty()) "base" else targets.joinToString("-")
            File(context.cacheDir, "muscle_map_$cacheKey.svg").apply {
                writeText(applyHighlights(template, targets.toSet(), highlightHex))
            }
        }
    }

    AsyncImage(
        model = renderFile,
        contentDescription = contentDescription,
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(VIEWBOX_WIDTH / VIEWBOX_HEIGHT)
            .semantics { contentDescription?.let { this.contentDescription = it } },
    )
}

private const val MUSCLE_MAP_ASSET = "muscle_map_labeled.svg"
private const val VIEWBOX_WIDTH = 406.99026f
private const val VIEWBOX_HEIGHT = 354.43411f

private val TAGGED_ELEMENT_REGEX =
    Regex("""<(path|use)([^>]*?\sdata-muscle="([a-z]+)"[^>]*?)/>""")
private val FILL_REGEX = Regex("""fill:url\(#[^)]*\)|fill:#[0-9a-fA-F]{3,8}""")

private fun applyHighlights(svg: String, targets: Set<String>, highlightHex: String): String {
    if (targets.isEmpty()) return svg
    return TAGGED_ELEMENT_REGEX.replace(svg) { match ->
        val muscle = match.groupValues[3]
        if (muscle in targets) {
            match.value.replace(FILL_REGEX, "fill:$highlightHex")
        } else {
            match.value
        }
    }
}
