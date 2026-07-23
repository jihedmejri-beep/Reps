package com.reps.app.core.components

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.reps.app.core.theme.RepsGreen
import com.reps.app.core.theme.RepsTheme
import kotlin.math.roundToInt

/**
 * Single-series animated line chart: weight and strength progression.
 *
 * One hue only (sequential, per the data-viz brief a single series needs no
 * legend) - the caller's card title already names the series. The line draws
 * itself in on first composition ("chart drawing" from the motion brief)
 * rather than appearing fully formed, and a press-drag scrubs a value chip
 * along the series, which is this chart's touch equivalent of a hover tooltip.
 */
@Composable
fun RepsLineChart(
    values: List<Float>,
    modifier: Modifier = Modifier,
    lineColor: Color = RepsGreen,
    valueFormatter: (Float) -> String = { it.roundToInt().toString() },
) {
    if (values.size < 2) return

    val progress = remember(values) { Animatable(0f) }
    LaunchedEffect(values) { progress.snapTo(0f); progress.animateTo(1f, tween(900, easing = FastOutSlowInEasing)) }

    var scrubIndex by remember(values) { mutableStateOf<Int?>(null) }

    val density = LocalDensity.current
    val textColorArgb = RepsTheme.colors.textPrimary.toArgb()
    val chipColorArgb = RepsTheme.colors.surfaceElevated.toArgb()
    // Read here, in the composition, because the Canvas draw scope below is not
    // a composable and cannot look the theme colour up itself.
    val gridColor = RepsTheme.colors.outline

    Canvas(
        modifier
            .fillMaxWidth()
            .height(150.dp)
            .pointerInput(values) {
                fun update(x: Float) {
                    val fraction = (x / size.width).coerceIn(0f, 1f)
                    scrubIndex = (fraction * (values.size - 1)).roundToInt().coerceIn(0, values.size - 1)
                }
                detectDragGestures(
                    onDragStart = { update(it.x) },
                    onDragEnd = { scrubIndex = null },
                    onDragCancel = { scrubIndex = null },
                ) { change, _ -> update(change.position.x) }
            },
    ) {
        val minValue = values.min()
        val maxValue = values.max()
        val range = (maxValue - minValue).let { if (it < 0.001f) 1f else it }
        val topInset = 20.dp.toPx()
        val bottomInset = 4.dp.toPx()
        val plotHeight = size.height - topInset - bottomInset
        val stepX = size.width / (values.size - 1)

        fun pointFor(i: Int): Offset {
            val y = topInset + plotHeight - ((values[i] - minValue) / range) * plotHeight
            return Offset(stepX * i, y)
        }

        // Recessive grid: three flat guides, never competing with the line.
        repeat(3) { i ->
            val y = topInset + plotHeight * (i / 2f)
            drawLine(
                color = gridColor.copy(alpha = 0.5f),
                start = Offset(0f, y),
                end = Offset(size.width, y),
                strokeWidth = 1.dp.toPx(),
            )
        }

        val fullLine = Path().apply {
            values.indices.forEach { i ->
                val p = pointFor(i)
                if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y)
            }
        }

        val measure = PathMeasure().apply { setPath(fullLine, false) }
        val revealed = Path()
        measure.getSegment(0f, measure.length * progress.value, revealed, true)

        val revealedEndX = stepX * (values.size - 1) * progress.value
        val area = Path().apply {
            addPath(revealed)
            lineTo(revealedEndX, size.height - bottomInset)
            lineTo(0f, size.height - bottomInset)
            close()
        }
        drawPath(
            path = area,
            brush = Brush.verticalGradient(
                colors = listOf(lineColor.copy(alpha = 0.22f), lineColor.copy(alpha = 0f)),
                startY = topInset,
                endY = size.height,
            ),
        )
        drawPath(
            path = revealed,
            color = lineColor,
            style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
        )

        if (progress.value > 0.98f) {
            drawCircle(lineColor, radius = 4.dp.toPx(), center = pointFor(values.size - 1))
        }

        scrubIndex?.let { i ->
            val p = pointFor(i)
            drawLine(
                color = gridColor,
                start = Offset(p.x, topInset),
                end = Offset(p.x, size.height - bottomInset),
                strokeWidth = 1.dp.toPx(),
            )
            drawCircle(lineColor, radius = 5.dp.toPx(), center = p)
            drawCircle(Color.White, radius = 2.dp.toPx(), center = p)

            drawIntoCanvas { canvas ->
                val label = valueFormatter(values[i])
                val textPaint = Paint().apply {
                    color = textColorArgb
                    textSize = with(density) { 12.sp.toPx() }
                    isAntiAlias = true
                    textAlign = Paint.Align.CENTER
                    typeface = Typeface.DEFAULT_BOLD
                }
                val chipPaint = Paint().apply { color = chipColorArgb; isAntiAlias = true }
                val hPad = with(density) { 8.dp.toPx() }
                val chipHeight = with(density) { 22.dp.toPx() }
                val chipWidth = textPaint.measureText(label) + hPad * 2
                val chipLeft = (p.x - chipWidth / 2f).coerceIn(0f, size.width - chipWidth)
                val chipTop = (p.y - chipHeight - with(density) { 10.dp.toPx() }).coerceAtLeast(0f)
                val cornerRadius = with(density) { 6.dp.toPx() }
                canvas.nativeCanvas.drawRoundRect(
                    chipLeft,
                    chipTop,
                    chipLeft + chipWidth,
                    chipTop + chipHeight,
                    cornerRadius,
                    cornerRadius,
                    chipPaint,
                )
                canvas.nativeCanvas.drawText(
                    label,
                    chipLeft + chipWidth / 2f,
                    chipTop + chipHeight / 2f + textPaint.textSize * 0.32f,
                    textPaint,
                )
            }
        }
    }
}
