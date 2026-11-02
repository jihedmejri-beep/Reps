package com.reps.app.feature.workouts.session

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.outlined.MonitorWeight
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.reps.app.R
import com.reps.app.core.components.ExerciseMediaCard
import com.reps.app.core.components.RepsBackButton
import com.reps.app.core.components.RepsButton
import com.reps.app.core.components.RepsOutlinedButton
import com.reps.app.core.components.RestTimer
import com.reps.app.core.components.StatCard
import com.reps.app.core.theme.RepsError
import com.reps.app.core.theme.RepsGreen
import com.reps.app.core.theme.RepsOnGreen
import com.reps.app.core.theme.RepsTheme
import com.reps.app.core.util.DateUtils
import com.reps.app.core.util.InputLimits
import com.reps.app.core.util.NumberInput
import com.reps.app.core.util.UnitConverter
import com.reps.app.domain.model.UnitSystem
import com.reps.app.feature.progress.presentation
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

/** Rest presets the per-exercise chip cycles through. */
private val RestPresetsSeconds = listOf(60, 90, 120, 180, 240)

@Composable
fun WorkoutSessionScreen(
    onFinished: () -> Unit,
    onBack: () -> Unit,
    viewModel: WorkoutSessionViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    var showNoSetsDialog by remember { mutableStateOf(false) }
    var showLeaveDialog by remember { mutableStateOf(false) }
    var showDiscardDialog by remember { mutableStateOf(false) }
    var completionResult by remember { mutableStateOf<SessionCompletionResult?>(null) }
    var timerExpanded by rememberSaveable { mutableStateOf(false) }

    // Leaving never loses work - the draft is already saved - but it should be a
    // decision, not an accidental swipe.
    BackHandler(enabled = completionResult == null) { showLeaveDialog = true }

    RestBackgroundHandoff(state.rest, viewModel)

    fun finish() {
        scope.launch { viewModel.finish()?.let { completionResult = it } }
    }

    fun requestFinish() {
        if (state.completedSetCount == 0) showNoSetsDialog = true else finish()
    }

    Box(Modifier.fillMaxSize().background(RepsTheme.colors.background)) {
        val dimens = RepsTheme.dimens
        LazyColumn(
            modifier = Modifier.fillMaxSize().statusBarsPadding(),
            contentPadding = PaddingValues(
                start = dimens.screenPadding,
                end = dimens.screenPadding,
                top = 8.dp,
                bottom = if (state.rest != null) 200.dp else 120.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(dimens.sectionGap),
        ) {
            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    RepsBackButton(onClick = { showLeaveDialog = true })
                    Spacer(Modifier.weight(1f))
                    if (state.startedAtMs > 0) ElapsedClock(state.startedAtMs)
                }
            }
            item {
                Text(
                    text = state.workoutName,
                    style = MaterialTheme.typography.headlineSmall,
                    color = RepsTheme.colors.textPrimary,
                )
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatCard(
                        icon = Icons.Outlined.FitnessCenter,
                        label = stringResource(R.string.workouts_title),
                        value = state.exercises.size.toString(),
                        modifier = Modifier.weight(1f),
                    )
                    StatCard(
                        icon = Icons.Outlined.Schedule,
                        label = stringResource(R.string.workouts_set),
                        value = state.completedSetCount.toString(),
                        unit = "/${state.totalSetCount}",
                        modifier = Modifier.weight(1f),
                    )
                    StatCard(
                        icon = Icons.Outlined.MonitorWeight,
                        label = stringResource(R.string.workouts_session_volume_so_far),
                        value = UnitConverter.formatWeight(state.volumeKg, state.units, decimals = 0),
                        unit = weightUnit(state.units),
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            items(state.exercises, key = { it.exercise.id }) { exerciseUi ->
                val id = exerciseUi.exercise.id
                SessionExerciseCard(
                    exerciseUi = exerciseUi,
                    units = state.units,
                    expanded = state.expandedExerciseId == id,
                    rest = state.rest?.takeIf { it.state.exerciseId == id },
                    onToggleExpand = { viewModel.toggleExpanded(id) },
                    onToggleSet = { index -> viewModel.toggleSetComplete(id, index) },
                    onWeightChange = { index, kg -> viewModel.updateWeightKg(id, index, kg) },
                    onRepsChange = { index, reps -> viewModel.updateReps(id, index, reps) },
                    onAddSet = { viewModel.addSet(id) },
                    onRemoveSet = { index -> viewModel.removeSet(id, index) },
                    onCycleRest = {
                        val next = RestPresetsSeconds.firstOrNull { it > exerciseUi.restSeconds } ?: RestPresetsSeconds.first()
                        viewModel.setRestSeconds(id, next)
                    },
                )
            }

            item {
                RepsButton(
                    text = if (state.completedSetCount == 0) {
                        stringResource(R.string.workouts_session_finish)
                    } else {
                        stringResource(R.string.workouts_session_finish_with_count, state.completedSetCount, state.totalSetCount)
                    },
                    onClick = ::requestFinish,
                    enabled = !state.finishing && !state.loading,
                    loading = state.finishing,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }

        state.rest?.let { rest ->
            RestTimer(
                state = rest.state,
                exerciseName = rest.exerciseName,
                nextLabel = if (rest.nextTotalSets > 0) {
                    stringResource(R.string.rest_timer_next, rest.nextExerciseName, rest.nextSetNumber, rest.nextTotalSets)
                } else {
                    stringResource(R.string.rest_timer_all_done)
                },
                expanded = timerExpanded,
                onToggleExpanded = { timerExpanded = !timerExpanded },
                onPause = viewModel::pauseRest,
                onResume = viewModel::resumeRest,
                onAdjust = viewModel::adjustRest,
                onSkip = viewModel::skipRest,
                onDismiss = viewModel::dismissRest,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(horizontal = dimens.screenPadding, vertical = 12.dp),
            )
        }
    }

    if (state.conflictingDraftName != null) {
        AlertDialog(
            onDismissRequest = {},
            containerColor = RepsTheme.colors.surfaceElevated,
            title = { Text(stringResource(R.string.session_conflict_title), color = RepsTheme.colors.textPrimary) },
            text = {
                Text(stringResource(R.string.session_conflict_body, state.conflictingDraftName.orEmpty()), color = RepsTheme.colors.textSecondary)
            },
            confirmButton = {
                TextButton(onClick = { viewModel.resolveConflict(resumeOther = true) }) {
                    Text(stringResource(R.string.session_conflict_resume), color = RepsGreen)
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.resolveConflict(resumeOther = false) }) {
                    Text(stringResource(R.string.session_conflict_discard), color = RepsError)
                }
            },
        )
    }

    if (showLeaveDialog) {
        AlertDialog(
            onDismissRequest = { showLeaveDialog = false },
            containerColor = RepsTheme.colors.surfaceElevated,
            title = { Text(stringResource(R.string.session_leave_title), color = RepsTheme.colors.textPrimary) },
            text = {
                Column {
                    Text(stringResource(R.string.session_leave_body), color = RepsTheme.colors.textSecondary)
                    TextButton(
                        onClick = { showLeaveDialog = false; showDiscardDialog = true },
                        modifier = Modifier.padding(top = 8.dp),
                    ) { Text(stringResource(R.string.session_discard), color = RepsError) }
                }
            },
            confirmButton = {
                TextButton(onClick = { showLeaveDialog = false; onBack() }) {
                    Text(stringResource(R.string.session_leave_confirm), color = RepsGreen)
                }
            },
            dismissButton = {
                TextButton(onClick = { showLeaveDialog = false }) {
                    Text(stringResource(R.string.workouts_session_keep_training), color = RepsTheme.colors.textSecondary)
                }
            },
        )
    }

    if (showDiscardDialog) {
        AlertDialog(
            onDismissRequest = { showDiscardDialog = false },
            containerColor = RepsTheme.colors.surfaceElevated,
            title = { Text(stringResource(R.string.session_discard_title), color = RepsTheme.colors.textPrimary) },
            text = { Text(stringResource(R.string.session_discard_body), color = RepsTheme.colors.textSecondary) },
            confirmButton = {
                TextButton(onClick = { showDiscardDialog = false; viewModel.discard(onBack) }) {
                    Text(stringResource(R.string.session_discard_confirm), color = RepsError)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardDialog = false }) {
                    Text(stringResource(R.string.workouts_session_keep_training), color = RepsTheme.colors.textSecondary)
                }
            },
        )
    }

    if (showNoSetsDialog) {
        AlertDialog(
            onDismissRequest = { showNoSetsDialog = false },
            containerColor = RepsTheme.colors.surfaceElevated,
            title = { Text(stringResource(R.string.workouts_session_no_sets_title), color = RepsTheme.colors.textPrimary) },
            text = { Text(stringResource(R.string.workouts_session_no_sets_body), color = RepsTheme.colors.textSecondary) },
            confirmButton = {
                TextButton(onClick = { showNoSetsDialog = false; finish() }) {
                    Text(stringResource(R.string.workouts_session_finish_anyway), color = RepsGreen)
                }
            },
            dismissButton = {
                TextButton(onClick = { showNoSetsDialog = false }) {
                    Text(stringResource(R.string.workouts_session_keep_training), color = RepsTheme.colors.textSecondary)
                }
            },
        )
    }

    completionResult?.let { result -> CompletionDialog(result, state.units, onFinished) }
}

@Composable
private fun weightUnit(units: UnitSystem): String =
    stringResource(if (units == UnitSystem.METRIC) R.string.workouts_kg else R.string.workouts_lb)

/** Hands a running rest to the system notification while the app is in the background. */
@Composable
private fun RestBackgroundHandoff(rest: RestUi?, viewModel: WorkoutSessionViewModel) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val text = rest?.let {
        RestNotificationText(
            runningTitle = stringResource(R.string.rest_notif_running_title, it.exerciseName),
            runningBody = if (it.nextTotalSets > 0) {
                stringResource(R.string.rest_timer_next, it.nextExerciseName, it.nextSetNumber, it.nextTotalSets)
            } else {
                stringResource(R.string.rest_timer_all_done)
            },
            doneTitle = stringResource(R.string.rest_notif_done_title),
            doneBody = if (it.nextTotalSets > 0) {
                stringResource(R.string.rest_timer_next, it.nextExerciseName, it.nextSetNumber, it.nextTotalSets)
            } else {
                stringResource(R.string.rest_timer_all_done)
            },
        )
    }
    val latestText by rememberUpdatedState(text)
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> latestText?.let(viewModel::onBackgrounded)
                Lifecycle.Event.ON_START -> viewModel.onForegrounded()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
}

/** Time since the session started, from the stored start - correct after any interruption. */
@Composable
private fun ElapsedClock(startedAtMs: Long) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(startedAtMs) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1_000 - now % 1_000)
        }
    }
    val label = DateUtils.formatElapsed((now - startedAtMs) / 1_000)
    val cd = stringResource(R.string.session_elapsed_cd, label)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
            .background(RepsTheme.colors.surface, CircleShape)
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .semantics(mergeDescendants = true) { contentDescription = cd },
    ) {
        Icon(Icons.Outlined.Timer, contentDescription = null, tint = RepsGreen, modifier = Modifier.size(16.dp))
        Text(label, style = MaterialTheme.typography.labelLarge, color = RepsTheme.colors.textPrimary)
    }
}

@Composable
private fun CompletionDialog(result: SessionCompletionResult, units: UnitSystem, onDone: () -> Unit) {
    val unlockedTitles = result.unlocked.map { stringResource(it.presentation().titleRes) }
    AlertDialog(
        onDismissRequest = {},
        containerColor = RepsTheme.colors.surfaceElevated,
        title = { Text(stringResource(R.string.workouts_session_complete_title), color = RepsTheme.colors.textPrimary) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(
                        R.string.session_complete_summary,
                        pluralStringResource(R.plurals.duration_minutes, result.durationMin, result.durationMin),
                        result.completedSetCount,
                        UnitConverter.formatWeight(result.volumeKg, units, decimals = 0),
                        weightUnit(units),
                    ),
                    color = RepsTheme.colors.textPrimary,
                )
                if (result.prExerciseNames.isNotEmpty()) {
                    Text(
                        stringResource(R.string.session_complete_prs, result.prExerciseNames.joinToString(", ")),
                        color = RepsGreen,
                    )
                }
                if (unlockedTitles.isNotEmpty()) {
                    Text(
                        stringResource(R.string.session_complete_unlocked, unlockedTitles.joinToString(", ")),
                        color = com.reps.app.core.theme.RepsAchievement,
                    )
                }
                Text(stringResource(R.string.session_complete_logged), color = RepsTheme.colors.textSecondary)
            }
        },
        confirmButton = {
            TextButton(onClick = onDone) {
                Text(stringResource(R.string.workouts_session_done), color = RepsGreen)
            }
        },
    )
}

@Composable
private fun SessionExerciseCard(
    exerciseUi: SessionExerciseUi,
    units: UnitSystem,
    expanded: Boolean,
    rest: RestUi?,
    onToggleExpand: () -> Unit,
    onToggleSet: (Int) -> Unit,
    onWeightChange: (Int, Double) -> Unit,
    onRepsChange: (Int, Int) -> Unit,
    onAddSet: () -> Unit,
    onRemoveSet: (Int) -> Unit,
    onCycleRest: () -> Unit,
) {
    val exercise = exerciseUi.exercise
    Column(
        Modifier
            .fillMaxWidth()
            .background(RepsTheme.colors.surface, MaterialTheme.shapes.large)
            .padding(RepsTheme.dimens.cardPadding),
    ) {
        Row(
            Modifier.fillMaxWidth().clickable(onClick = onToggleExpand),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (exerciseUi.isComplete) {
                Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = RepsGreen, modifier = Modifier.size(20.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(exercise.name, style = MaterialTheme.typography.titleSmall, color = RepsTheme.colors.textPrimary)
                Text(
                    text = listOfNotNull(
                        stringResource(exercise.muscleGroup.labelRes),
                        exercise.equipment.takeIf { it.isNotBlank() },
                        stringResource(R.string.session_exercise_progress, exerciseUi.completedCount, exerciseUi.sets.size),
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall,
                    color = RepsTheme.colors.textSecondary,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            Icon(
                imageVector = if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                contentDescription = null,
                tint = RepsTheme.colors.textTertiary,
            )
        }

        if (!expanded) return@Column

        ExerciseMediaCard(
            mediaUrl = exercise.mediaUrl,
            contentDescription = stringResource(R.string.exercise_media_cd, exercise.name),
            modifier = Modifier.padding(top = 14.dp),
        )

        Row(
            Modifier.fillMaxWidth().padding(top = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = exerciseUi.bestSet?.let { best ->
                    if (best.weightKg > 0.0) {
                        stringResource(R.string.session_best_weighted, UnitConverter.formatWeight(best.weightKg, units), weightUnit(units), best.reps)
                    } else {
                        stringResource(R.string.session_best_bodyweight, best.reps)
                    }
                }.orEmpty(),
                style = MaterialTheme.typography.labelSmall,
                color = RepsTheme.colors.textSecondary,
            )
            val restLabel = DateUtils.formatClock(exerciseUi.restSeconds)
            val restCd = stringResource(R.string.session_rest_chip_cd, restLabel)
            Text(
                text = stringResource(R.string.session_rest_chip, restLabel),
                style = MaterialTheme.typography.labelSmall,
                color = RepsTheme.colors.textPrimary,
                modifier = Modifier
                    .background(RepsTheme.colors.surfaceElevated, CircleShape)
                    .clickable(role = Role.Button, onClickLabel = restCd, onClick = onCycleRest)
                    .padding(horizontal = 12.dp, vertical = 8.dp)
                    .semantics { contentDescription = restCd },
            )
        }

        Row(Modifier.fillMaxWidth().padding(top = 12.dp)) {
            Spacer(Modifier.width(RailWidth))
            Text(stringResource(R.string.workouts_set), style = MaterialTheme.typography.labelSmall, color = RepsTheme.colors.textSecondary, modifier = Modifier.weight(0.6f))
            Text(weightUnit(units), style = MaterialTheme.typography.labelSmall, color = RepsTheme.colors.textSecondary, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
            Text(stringResource(R.string.workouts_reps), style = MaterialTheme.typography.labelSmall, color = RepsTheme.colors.textSecondary, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
            Spacer(Modifier.size(48.dp))
        }

        val currentIndex = exerciseUi.sets.indexOfFirst { !it.completed }
        exerciseUi.sets.forEachIndexed { index, set ->
            SetRow(
                index = index,
                set = set,
                units = units,
                railState = when {
                    set.completed -> RailState.DONE
                    index == currentIndex -> RailState.CURRENT
                    else -> RailState.UPCOMING
                },
                isLast = index == exerciseUi.sets.lastIndex,
                onToggle = { onToggleSet(index) },
                onWeightChange = { onWeightChange(index, it) },
                onRepsChange = { onRepsChange(index, it) },
                onRemove = { onRemoveSet(index) },
            )
            if (rest != null && rest.state.setIndex == index) RestTimelineRow(rest)
        }

        RepsOutlinedButton(
            text = stringResource(R.string.workouts_add_set),
            onClick = onAddSet,
            leadingIcon = rememberVectorPainter(Icons.Outlined.Add),
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

private enum class RailState { DONE, CURRENT, UPCOMING }

private val RailWidth = 22.dp

/** The timeline rail beside each set: filled when done, outlined when next, muted after. */
@Composable
private fun TimelineDot(state: RailState, isLast: Boolean, modifier: Modifier = Modifier) {
    val dotColor = when (state) {
        RailState.DONE -> RepsGreen
        RailState.CURRENT -> Color.Transparent
        RailState.UPCOMING -> RepsTheme.colors.outline
    }
    Box(modifier.width(RailWidth).fillMaxHeight(), contentAlignment = Alignment.Center) {
        if (!isLast) {
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .width(2.dp)
                    .height(22.dp)
                    .background(if (state == RailState.DONE) RepsGreen.copy(alpha = 0.5f) else RepsTheme.colors.outline),
            )
        }
        Box(
            Modifier
                .size(10.dp)
                .background(dotColor, CircleShape)
                .then(if (state == RailState.CURRENT) Modifier.border(2.dp, RepsGreen, CircleShape) else Modifier),
        )
    }
}

/** The active rest, drawn in the timeline right under the set that started it. */
@Composable
private fun RestTimelineRow(rest: RestUi) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val finished = rest.state.isFinished(now)
    LaunchedEffect(rest.state, finished) {
        while (!rest.state.isPaused && !rest.state.isFinished(System.currentTimeMillis())) {
            now = System.currentTimeMillis()
            delay(250)
        }
        now = System.currentTimeMillis()
    }
    val color by animateColorAsState(if (finished) RepsGreen else RepsError, tween(220), label = "restRow")
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(RailWidth), contentAlignment = Alignment.Center) {
            Box(Modifier.width(2.dp).height(20.dp).background(color))
        }
        Text(
            text = if (finished) {
                stringResource(R.string.session_rest_row_done)
            } else {
                stringResource(R.string.session_rest_row_resting, DateUtils.formatClock(((rest.state.remainingMs(now) + 999) / 1000).toInt()))
            },
            style = MaterialTheme.typography.labelSmall,
            color = color,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SetRow(
    index: Int,
    set: SessionSetUi,
    units: UnitSystem,
    railState: RailState,
    isLast: Boolean,
    onToggle: () -> Unit,
    onWeightChange: (Double) -> Unit,
    onRepsChange: (Int) -> Unit,
    onRemove: () -> Unit,
) {
    val removeLabel = stringResource(R.string.session_set_remove, index + 1)
    Row(
        Modifier.fillMaxWidth().height(52.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TimelineDot(railState, isLast)
        Text(
            text = (index + 1).toString(),
            style = MaterialTheme.typography.bodyMedium,
            color = if (railState == RailState.CURRENT) RepsTheme.colors.textPrimary else RepsTheme.colors.textSecondary,
            modifier = Modifier
                .weight(0.6f)
                .combinedClickable(onClick = {}, onLongClick = onRemove, onLongClickLabel = removeLabel),
        )
        SetValueField(
            externalValue = weightInputText(set.weightKg, units),
            onValueChange = { text ->
                NumberInput.parseDecimal(text)
                    ?.let { UnitConverter.weightToKg(it, units) }
                    ?.takeIf { it in InputLimits.SET_WEIGHT_KG }
                    ?.let(onWeightChange)
            },
            keyboardType = KeyboardType.Decimal,
            modifier = Modifier.weight(1f).padding(horizontal = 4.dp),
        )
        SetValueField(
            externalValue = set.reps.toString(),
            onValueChange = { text -> NumberInput.parseIntIn(text, InputLimits.SET_REPS)?.let(onRepsChange) },
            keyboardType = KeyboardType.Number,
            modifier = Modifier.weight(1f).padding(horizontal = 4.dp),
        )
        val checkColor by animateColorAsState(
            targetValue = if (set.completed) RepsGreen else RepsTheme.colors.surfaceElevated,
            animationSpec = tween(180),
            label = "setCheck",
        )
        val checkCd = stringResource(if (set.completed) R.string.session_set_done_cd else R.string.session_set_open_cd, index + 1)
        // 48 dp touch target around a 32 dp visual box.
        Box(
            Modifier
                .size(48.dp)
                .clickable(role = Role.Checkbox, onClickLabel = checkCd, onClick = onToggle)
                .semantics { contentDescription = checkCd },
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier
                    .size(32.dp)
                    .background(checkColor, MaterialTheme.shapes.small)
                    .border(1.dp, if (set.completed) RepsGreen else RepsTheme.colors.outline, MaterialTheme.shapes.small),
                contentAlignment = Alignment.Center,
            ) {
                if (set.completed) {
                    Icon(Icons.Filled.Check, contentDescription = null, tint = RepsOnGreen, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

/** "20" rather than "20.0", so the field reads like what a person would type. */
private fun weightInputText(kg: Double, units: UnitSystem): String {
    val value = UnitConverter.displayWeight(kg, units)
    val rounded = Math.round(value * 10) / 10.0
    return if (rounded % 1.0 == 0.0) rounded.toLong().toString() else String.format(Locale.US, "%.1f", rounded)
}

/**
 * A numeric cell that keeps exactly what the user is typing. The previous field
 * re-formatted the stored number on every keystroke, so "20." snapped back to
 * "20.0" and the value could not be cleared to type a new one. The text only
 * re-syncs from [externalValue] when that changes for another reason (e.g. a set
 * added with copied values, or a unit switch) and differs from what was typed.
 */
@Composable
private fun SetValueField(
    externalValue: String,
    onValueChange: (String) -> Unit,
    keyboardType: KeyboardType,
    modifier: Modifier = Modifier,
) {
    var text by remember { mutableStateOf(externalValue) }
    LaunchedEffect(externalValue) {
        if (NumberInput.parseDecimal(text) != NumberInput.parseDecimal(externalValue)) text = externalValue
    }
    BasicTextField(
        value = text,
        onValueChange = {
            text = it
            onValueChange(it)
        },
        singleLine = true,
        textStyle = TextStyle(
            color = RepsTheme.colors.textPrimary,
            fontSize = MaterialTheme.typography.bodyMedium.fontSize,
            textAlign = TextAlign.Center,
        ),
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        cursorBrush = SolidColor(RepsGreen),
        modifier = modifier
            .height(40.dp)
            .background(RepsTheme.colors.surfaceElevated, MaterialTheme.shapes.small),
        decorationBox = { inner ->
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { inner() }
        },
    )
}
