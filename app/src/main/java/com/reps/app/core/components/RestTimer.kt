package com.reps.app.core.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.reps.app.R
import com.reps.app.core.theme.RepsError
import com.reps.app.core.theme.RepsGreen
import com.reps.app.core.theme.RepsOnGreen
import com.reps.app.core.theme.RepsTheme
import com.reps.app.core.util.DateUtils
import com.reps.app.domain.model.RestState

private const val AdjustStepSeconds = 15

private val ArcDiameter = 200.dp
private val ArcStrokeWidth = 12.dp
private val PrimaryButtonSize = 56.dp
private val ChipButtonSize = 44.dp

/**
 * The current time, advancing every frame while [running] and frozen otherwise.
 * Display only: the remaining time itself always comes from the [RestState]
 * timestamps, so a dropped frame or a trip to the background cannot skew it.
 */
@Composable
private fun rememberNow(running: Boolean): Long {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(running) {
        now = System.currentTimeMillis()
        while (running) {
            withFrameMillis { }
            now = System.currentTimeMillis()
        }
    }
    return now
}

/**
 * The rest between sets. RED while resting, GREEN once it is time for the next
 * set, and it stays green until the user dismisses it or ticks another set, so a
 * finished rest is never missed.
 *
 * Collapsed it is a slim bar docked above the session controls (countdown,
 * what is next, pause/+15/skip) so the set list stays usable; tapping it expands
 * the full arc with every control.
 *
 * Stateless: [state] comes from the session ViewModel, which owns the timer.
 */
@Composable
fun RestTimer(
    state: RestState,
    exerciseName: String,
    nextLabel: String,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onAdjust: (seconds: Int) -> Unit,
    onSkip: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptic = LocalHapticFeedback.current
    val ticking = !state.isPaused && state.endsAtMs != null && !state.isFinished(System.currentTimeMillis())
    val now = rememberNow(ticking)
    val finished = state.isFinished(now)

    LaunchedEffect(finished) {
        if (finished) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
    }

    val accent by animateColorAsState(
        targetValue = if (finished) RepsGreen else RepsError,
        animationSpec = tween(220, easing = FastOutSlowInEasing),
        label = "restAccent",
    )
    val pulse = remember { Animatable(1f) }
    LaunchedEffect(finished) {
        if (finished) {
            pulse.animateTo(1.04f, tween(140, easing = FastOutSlowInEasing))
            pulse.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = 300f))
        }
    }

    val timeLabel = DateUtils.formatClock(((state.remainingMs(now) + 999) / 1000).toInt())
    val status = stringResource(
        when {
            finished -> R.string.rest_timer_complete
            state.isPaused -> R.string.rest_timer_paused
            else -> R.string.rest_timer_resting
        },
    )

    Column(
        modifier = modifier
            .scale(pulse.value)
            .fillMaxWidth()
            .background(RepsTheme.colors.surfaceElevated, MaterialTheme.shapes.large)
            .border(1.dp, accent.copy(alpha = 0.55f), MaterialTheme.shapes.large)
            .semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClickLabel = stringResource(if (expanded) R.string.rest_timer_collapse_cd else R.string.rest_timer_expand_cd), onClick = onToggleExpanded)
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            MiniRing(progress = state.progress(now), color = accent, finished = finished)
            Column(Modifier.weight(1f)) {
                Text(
                    text = if (finished) stringResource(R.string.rest_timer_go).uppercase() else timeLabel,
                    style = RepsTheme.textStyles.statValue,
                    color = if (finished) RepsGreen else RepsTheme.colors.textPrimary,
                )
                Text(
                    text = nextLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = RepsTheme.colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (finished) {
                TimerChip(Icons.Filled.Check, stringResource(R.string.rest_timer_dismiss_cd), onDismiss, ChipButtonSize, fill = RepsGreen)
            } else if (!expanded) {
                TimerChip(
                    icon = if (state.isPaused) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                    contentDescription = stringResource(if (state.isPaused) R.string.rest_timer_resume_cd else R.string.rest_timer_pause_cd),
                    onClick = if (state.isPaused) onResume else onPause,
                    size = ChipButtonSize,
                )
                TimerTextChip(stringResource(R.string.rest_timer_plus15), stringResource(R.string.rest_timer_add_cd), { onAdjust(AdjustStepSeconds) }, ChipButtonSize)
                TimerChip(Icons.Filled.SkipNext, stringResource(R.string.rest_timer_skip_cd), onSkip, ChipButtonSize)
            }
            Icon(
                imageVector = if (expanded) Icons.Filled.KeyboardArrowDown else Icons.Filled.KeyboardArrowUp,
                contentDescription = null,
                tint = RepsTheme.colors.textTertiary,
            )
        }

        AnimatedContent(
            targetState = expanded && !finished,
            transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(150)) },
            label = "restExpanded",
        ) { showFull ->
            if (!showFull) {
                Spacer(Modifier.height(0.dp))
                return@AnimatedContent
            }
            Column(
                Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = "${stringResource(R.string.rest_timer_eyebrow).uppercase()} · $exerciseName",
                    style = RepsTheme.textStyles.eyebrow,
                    color = accent,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth(),
                )
                RestTimerArc(
                    progress = state.progress(now),
                    timeLabel = timeLabel,
                    statusLabel = status,
                    arcColor = accent,
                    durationLabel = stringResource(R.string.rest_timer_of_total, DateUtils.formatClock((state.durationMs / 1000).toInt())),
                    modifier = Modifier.padding(top = 12.dp),
                )
                Row(
                    Modifier.padding(top = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TimerTextChip(stringResource(R.string.rest_timer_minus15), stringResource(R.string.rest_timer_subtract_cd), { onAdjust(-AdjustStepSeconds) }, ChipButtonSize)
                    TimerChip(
                        icon = if (state.isPaused) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                        contentDescription = stringResource(if (state.isPaused) R.string.rest_timer_resume_cd else R.string.rest_timer_pause_cd),
                        onClick = if (state.isPaused) onResume else onPause,
                        size = PrimaryButtonSize,
                        fill = accent,
                    )
                    TimerTextChip(stringResource(R.string.rest_timer_plus15), stringResource(R.string.rest_timer_add_cd), { onAdjust(AdjustStepSeconds) }, ChipButtonSize)
                    TimerChip(Icons.Filled.SkipNext, stringResource(R.string.rest_timer_skip_cd), onSkip, ChipButtonSize)
                }
            }
        }
    }
}

@Composable
private fun MiniRing(progress: Float, color: Color, finished: Boolean) {
    val track = RepsTheme.colors.outline
    Box(Modifier.size(36.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(36.dp)) {
            val stroke = 4.dp.toPx()
            val arc = Size(size.width - stroke, size.height - stroke)
            val topLeft = Offset(stroke / 2, stroke / 2)
            drawArc(track, -90f, 360f, false, topLeft, arc, style = Stroke(stroke))
            drawArc(color, -90f, 360f * (if (finished) 1f else progress), false, topLeft, arc, style = Stroke(stroke, cap = StrokeCap.Round))
        }
        if (finished) Icon(Icons.Filled.Check, contentDescription = null, tint = RepsGreen, modifier = Modifier.size(16.dp))
    }
}

@Composable
private fun RestTimerArc(
    progress: Float,
    timeLabel: String,
    statusLabel: String,
    arcColor: Color,
    durationLabel: String,
    modifier: Modifier = Modifier,
) {
    val strokeWidthPx = with(LocalDensity.current) { ArcStrokeWidth.toPx() }
    // The draw scope below is not composable, so the track colour is read here.
    val trackColor = RepsTheme.colors.outline
    Box(modifier.size(ArcDiameter + ArcStrokeWidth, ArcDiameter / 2 + ArcStrokeWidth + 18.dp)) {
        Canvas(Modifier.fillMaxWidth().height(ArcDiameter / 2 + ArcStrokeWidth)) {
            val diameter = size.width - strokeWidthPx
            val arcSize = Size(diameter, diameter)
            val topLeft = Offset(strokeWidthPx / 2f, strokeWidthPx / 2f)
            drawArc(trackColor, 180f, 180f, false, topLeft, arcSize, style = Stroke(strokeWidthPx, cap = StrokeCap.Round))
            drawArc(arcColor, 180f, 180f * progress, false, topLeft, arcSize, style = Stroke(strokeWidthPx, cap = StrokeCap.Round))
        }
        Column(
            modifier = Modifier.align(Alignment.BottomCenter),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(text = timeLabel, style = RepsTheme.textStyles.statValue, color = RepsTheme.colors.textPrimary)
            Text(text = statusLabel, style = RepsTheme.textStyles.eyebrow, color = RepsTheme.colors.textSecondary, modifier = Modifier.padding(top = 2.dp))
            Text(text = durationLabel, style = MaterialTheme.typography.labelSmall, color = RepsTheme.colors.textTertiary)
        }
    }
}

@Composable
private fun TimerChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    size: Dp,
    fill: Color? = null,
) {
    Box(
        modifier = Modifier
            .size(size)
            .then(
                if (fill != null) {
                    Modifier.background(fill, CircleShape)
                } else {
                    Modifier.background(RepsTheme.colors.surface, CircleShape).border(1.dp, RepsTheme.colors.outline, CircleShape)
                },
            )
            .clickable(role = Role.Button, onClickLabel = contentDescription, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (fill != null) RepsOnGreen else RepsTheme.colors.textPrimary,
            modifier = Modifier.size(if (size >= PrimaryButtonSize) 26.dp else 18.dp),
        )
    }
}

@Composable
private fun TimerTextChip(text: String, contentDescription: String, onClick: () -> Unit, size: Dp) {
    Box(
        modifier = Modifier
            .size(size)
            .background(RepsTheme.colors.surface, CircleShape)
            .border(1.dp, RepsTheme.colors.outline, CircleShape)
            .clickable(role = Role.Button, onClickLabel = contentDescription, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = text, style = MaterialTheme.typography.labelSmall, color = RepsTheme.colors.textPrimary)
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0B0B0A)
@Composable
private fun RestTimerRunningPreview() {
    RepsTheme {
        val now = System.currentTimeMillis()
        Box(Modifier.padding(16.dp).width(360.dp)) {
            RestTimer(
                state = RestState("bench", 0, 90_000, endsAtMs = now + 62_000),
                exerciseName = "Bench Press",
                nextLabel = "Next: Bench Press · set 2 of 4",
                expanded = true,
                onToggleExpanded = {}, onPause = {}, onResume = {}, onAdjust = {}, onSkip = {}, onDismiss = {},
            )
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0B0B0A)
@Composable
private fun RestTimerFinishedPreview() {
    RepsTheme {
        val now = System.currentTimeMillis()
        Box(Modifier.padding(16.dp).width(360.dp)) {
            RestTimer(
                state = RestState("bench", 0, 90_000, endsAtMs = now - 1),
                exerciseName = "Bench Press",
                nextLabel = "Next: Bench Press · set 2 of 4",
                expanded = false,
                onToggleExpanded = {}, onPause = {}, onResume = {}, onAdjust = {}, onSkip = {}, onDismiss = {},
            )
        }
    }
}
