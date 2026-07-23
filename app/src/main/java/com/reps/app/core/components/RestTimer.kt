package com.reps.app.core.components

import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.reps.app.R
import com.reps.app.core.theme.RepsGreen
import com.reps.app.core.theme.RepsOffWhite
import com.reps.app.core.theme.RepsOnGreen
import com.reps.app.core.theme.RepsTheme
import com.reps.app.core.util.DateUtils
import kotlinx.coroutines.isActive

private const val AdjustStepSeconds = 15

/**
 * Hoisted, ticking countdown. Frame-driven rather than second-stepped, so the
 * arc in [RestTimer] can sweep smoothly instead of jumping once a second.
 *
 * Not itself a [Composable] - [rememberRestTimerState] is the entry point, and
 * only [RestTimer] drives the actual ticking, so a state that is not currently
 * on screen does not advance in the background.
 */
@Stable
class RestTimerState internal constructor(
    initialSeconds: Int,
    private val onComplete: () -> Unit,
) {
    var totalSeconds by mutableIntStateOf(initialSeconds.coerceAtLeast(1))
        private set
    var remainingSeconds by mutableFloatStateOf(totalSeconds.toFloat())
        private set
    var running by mutableStateOf(false)
        private set
    var completed by mutableStateOf(false)
        private set

    val remainingWholeSeconds: Int get() = kotlin.math.ceil(remainingSeconds).toInt()

    /** 1 at the start of the rest, 0 once it is spent. */
    val progress: Float get() = (remainingSeconds / totalSeconds).coerceIn(0f, 1f)

    /** Starts counting down, restarting from [totalSeconds] if it had already finished. */
    fun start() {
        if (remainingSeconds <= 0f) remainingSeconds = totalSeconds.toFloat()
        completed = false
        running = true
    }

    fun pause() {
        running = false
    }

    fun resume() {
        if (!completed) running = true
    }

    fun restart() {
        remainingSeconds = totalSeconds.toFloat()
        completed = false
        running = true
    }

    /** Ends the rest early, e.g. the user is ready for the next set right away. */
    fun skip() {
        if (!completed) finish()
    }

    /** Adds or removes time on the fly; a negative delta can trigger completion. */
    fun adjust(deltaSeconds: Int) {
        if (completed) return
        remainingSeconds = (remainingSeconds + deltaSeconds).coerceAtLeast(0f)
        if (remainingSeconds <= 0f) finish()
    }

    internal fun tick(deltaSeconds: Float) {
        if (!running) return
        remainingSeconds = (remainingSeconds - deltaSeconds).coerceAtLeast(0f)
        if (remainingSeconds <= 0f) finish()
    }

    private fun finish() {
        running = false
        completed = true
        remainingSeconds = 0f
        onComplete()
    }
}

/**
 * @param initialSeconds rest duration. Changing it starts a fresh state - callers
 *   that want a new rest for every set should key their call site accordingly
 *   (e.g. wrap in `key(setId) { rememberRestTimerState(...) }`).
 * @param autoStart begins counting down as soon as this enters composition,
 *   which is how a workout session starts rest automatically after a set.
 */
@Composable
fun rememberRestTimerState(
    initialSeconds: Int = 90,
    autoStart: Boolean = false,
    onComplete: () -> Unit = {},
): RestTimerState {
    val latestOnComplete by rememberUpdatedState(onComplete)
    val state = remember(initialSeconds) {
        RestTimerState(initialSeconds) { latestOnComplete() }
    }
    LaunchedEffect(state) { if (autoStart) state.start() }
    return state
}

private val ArcDiameter = 224.dp
private val ArcStrokeWidth = 14.dp
private val PrimaryButtonSize = 64.dp
private val ChipButtonSize = 44.dp

/**
 * The floating rest timer shown during an active workout: a half-circle dial
 * that sweeps green while resting, settles to a neutral outline once the rest
 * is spent, with pause/resume/restart/skip and +-15s controls underneath.
 *
 * A self-contained card - the caller positions it (e.g. `Modifier.align(...)`
 * in a `Box` over the session content), same as [com.reps.app.navigation.RepsFloatingNavBar].
 */
@Composable
fun RestTimer(
    state: RestTimerState,
    modifier: Modifier = Modifier,
) {
    val haptic = LocalHapticFeedback.current

    // Frame-driven so the arc sweeps continuously; restarts whenever `running`
    // flips, and stops itself the moment the state does.
    LaunchedEffect(state.running) {
        if (!state.running) return@LaunchedEffect
        var lastFrame = withFrameNanos { it }
        while (isActive && state.running) {
            val frame = withFrameNanos { it }
            state.tick((frame - lastFrame) / 1_000_000_000f)
            lastFrame = frame
        }
    }

    LaunchedEffect(state.completed) {
        if (state.completed) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
    }

    val arcColor by animateColorAsState(
        targetValue = if (state.completed) RepsTheme.colors.outline else RepsGreen,
        animationSpec = tween(220, easing = FastOutSlowInEasing),
        label = "restTimerArcColor",
    )

    val pulse = remember { Animatable(1f) }
    LaunchedEffect(state.completed) {
        if (state.completed) {
            pulse.animateTo(1.06f, tween(140, easing = FastOutSlowInEasing))
            pulse.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = 300f))
        }
    }

    Column(
        modifier = modifier
            .scale(pulse.value)
            .shadow(
                elevation = 24.dp,
                shape = MaterialTheme.shapes.large,
                ambientColor = RepsGreen,
                spotColor = RepsGreen,
            )
            .background(RepsTheme.colors.surfaceElevated, MaterialTheme.shapes.large)
            .border(1.dp, RepsOffWhite.copy(alpha = 0.07f), MaterialTheme.shapes.large)
            .padding(horizontal = RepsTheme.dimens.cardPadding, vertical = 18.dp)
            .width(ArcDiameter + ArcStrokeWidth + 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.rest_timer_eyebrow).uppercase(),
            style = RepsTheme.textStyles.eyebrow,
            color = RepsGreen,
            modifier = Modifier.fillMaxWidth(),
        )

        RestTimerArc(
            progress = state.progress,
            timeLabel = DateUtils.formatClock(state.remainingWholeSeconds),
            statusLabel = statusLabel(state),
            arcColor = arcColor,
            modifier = Modifier.padding(top = 14.dp),
        )

        Crossfade(
            targetState = state.completed,
            animationSpec = tween(220),
            label = "restTimerControls",
            modifier = Modifier.padding(top = 18.dp),
        ) { isCompleted ->
            if (isCompleted) {
                RestTimerIconChip(
                    icon = Icons.Filled.Replay,
                    contentDescription = stringResource(R.string.rest_timer_restart_cd),
                    onClick = state::restart,
                    size = PrimaryButtonSize,
                    primary = true,
                )
            } else {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RestTimerIconChip(
                        icon = Icons.Filled.Replay,
                        contentDescription = stringResource(R.string.rest_timer_restart_cd),
                        onClick = state::restart,
                        size = ChipButtonSize,
                    )
                    RestTimerTextChip(
                        text = stringResource(R.string.rest_timer_minus15),
                        contentDescription = stringResource(R.string.rest_timer_subtract_cd),
                        onClick = { state.adjust(-AdjustStepSeconds) },
                        size = ChipButtonSize,
                    )
                    RestTimerIconChip(
                        icon = if (state.running) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = stringResource(
                            if (state.running) R.string.rest_timer_pause_cd else R.string.rest_timer_resume_cd,
                        ),
                        onClick = { if (state.running) state.pause() else state.resume() },
                        size = PrimaryButtonSize,
                        primary = true,
                    )
                    RestTimerTextChip(
                        text = stringResource(R.string.rest_timer_plus15),
                        contentDescription = stringResource(R.string.rest_timer_add_cd),
                        onClick = { state.adjust(AdjustStepSeconds) },
                        size = ChipButtonSize,
                    )
                    RestTimerIconChip(
                        icon = Icons.Filled.SkipNext,
                        contentDescription = stringResource(R.string.rest_timer_skip_cd),
                        onClick = state::skip,
                        size = ChipButtonSize,
                    )
                }
            }
        }
    }
}

@Composable
private fun statusLabel(state: RestTimerState): String = stringResource(
    when {
        state.completed -> R.string.rest_timer_complete
        state.running -> R.string.rest_timer_resting
        state.remainingSeconds < state.totalSeconds -> R.string.rest_timer_paused
        else -> R.string.rest_timer_ready
    },
)

@Composable
private fun RestTimerArc(
    progress: Float,
    timeLabel: String,
    statusLabel: String,
    arcColor: Color,
    modifier: Modifier = Modifier,
) {
    val strokeWidthPx = with(androidx.compose.ui.platform.LocalDensity.current) { ArcStrokeWidth.toPx() }
    // The draw scope below is not composable, so the track colour is read here.
    val trackColor = RepsTheme.colors.outline
    Box(modifier.size(ArcDiameter + ArcStrokeWidth, ArcDiameter / 2 + ArcStrokeWidth)) {
        Canvas(Modifier.fillMaxWidth().height(ArcDiameter / 2 + ArcStrokeWidth)) {
            val diameter = size.width - strokeWidthPx
            val arcSize = Size(diameter, diameter)
            val topLeft = androidx.compose.ui.geometry.Offset(strokeWidthPx / 2f, strokeWidthPx / 2f)
            drawArc(
                color = trackColor,
                startAngle = 180f,
                sweepAngle = 180f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeWidthPx, cap = StrokeCap.Round),
            )
            drawArc(
                color = arcColor,
                startAngle = 180f,
                sweepAngle = 180f * progress,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeWidthPx, cap = StrokeCap.Round),
            )
        }
        Column(
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(text = timeLabel, style = RepsTheme.textStyles.statValue, color = RepsTheme.colors.textPrimary)
            Text(
                text = statusLabel,
                style = RepsTheme.textStyles.eyebrow,
                color = RepsTheme.colors.textSecondary,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

@Composable
private fun RestTimerIconChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    size: Dp,
    primary: Boolean = false,
) {
    Box(
        modifier = Modifier
            .size(size)
            .then(
                if (primary) {
                    Modifier.shadow(12.dp, CircleShape, ambientColor = RepsGreen, spotColor = RepsGreen)
                        .background(RepsGreen, CircleShape)
                } else {
                    Modifier.background(RepsTheme.colors.surfaceElevated, CircleShape)
                        .border(1.dp, RepsTheme.colors.outline, CircleShape)
                },
            )
            .clickable(role = Role.Button, onClickLabel = contentDescription, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (primary) RepsOnGreen else RepsTheme.colors.textPrimary,
            modifier = Modifier.size(if (primary) 28.dp else 18.dp),
        )
    }
}

@Composable
private fun RestTimerTextChip(
    text: String,
    contentDescription: String,
    onClick: () -> Unit,
    size: Dp,
) {
    Box(
        modifier = Modifier
            .size(size)
            .background(RepsTheme.colors.surfaceElevated, CircleShape)
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
        val state = rememberRestTimerState(initialSeconds = 90, autoStart = true)
        Box(Modifier.padding(24.dp)) { RestTimer(state) }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0B0B0A)
@Composable
private fun RestTimerPausedPreview() {
    RepsTheme {
        val state = rememberRestTimerState(initialSeconds = 90)
        LaunchedEffect(state) {
            state.start()
            state.adjust(-30)
            state.pause()
        }
        Box(Modifier.padding(24.dp)) { RestTimer(state) }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0B0B0A)
@Composable
private fun RestTimerCompletedPreview() {
    RepsTheme {
        val state = rememberRestTimerState(initialSeconds = 90)
        LaunchedEffect(state) { state.skip() }
        Box(Modifier.padding(24.dp)) { RestTimer(state) }
    }
}
