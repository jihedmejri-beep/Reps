package com.reps.app.feature.workouts.session

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.outlined.MonitorWeight
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.reps.app.R
import com.reps.app.core.components.ExerciseMediaCard
import com.reps.app.core.components.RepsBackButton
import com.reps.app.core.components.RepsButton
import com.reps.app.core.components.RepsOutlinedButton
import com.reps.app.core.components.RestTimer
import com.reps.app.core.components.StatCard
import com.reps.app.core.components.rememberRestTimerState
import com.reps.app.core.theme.RepsGreen
import com.reps.app.core.theme.RepsOnGreen
import com.reps.app.core.theme.RepsTheme
import com.reps.app.core.util.UnitConverter
import com.reps.app.domain.model.UnitSystem
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun WorkoutSessionScreen(
    onFinished: () -> Unit,
    onBack: () -> Unit,
    viewModel: WorkoutSessionViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val restSignal by viewModel.restSignal.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    var showNoSetsDialog by remember { mutableStateOf(false) }
    var completionResult by remember { mutableStateOf<SessionCompletionResult?>(null) }
    var showRestTimer by remember { mutableStateOf(false) }

    val restTimerState = key(restSignal) {
        rememberRestTimerState(initialSeconds = 90, autoStart = restSignal > 0)
    }
    LaunchedEffect(restSignal) { if (restSignal > 0) showRestTimer = true }
    LaunchedEffect(restTimerState.completed) {
        if (restTimerState.completed) {
            delay(3_000)
            showRestTimer = false
        }
    }

    fun requestFinish() {
        if (state.completedSetCount == 0) {
            showNoSetsDialog = true
        } else {
            scope.launch { completionResult = viewModel.finish() }
        }
    }

    Box(Modifier.fillMaxSize()) {
        val dimens = RepsTheme.dimens
        LazyColumn(
            modifier = Modifier.fillMaxSize().background(RepsTheme.colors.background).statusBarsPadding(),
            contentPadding = PaddingValues(
                start = dimens.screenPadding,
                end = dimens.screenPadding,
                top = 8.dp,
                bottom = 120.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(dimens.sectionGap),
        ) {
            item { RepsBackButton(onClick = onBack) }
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
                        unit = stringResource(if (state.units == UnitSystem.METRIC) R.string.workouts_kg else R.string.workouts_lb),
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            items(state.exercises, key = { it.exercise.id }) { exerciseUi ->
                SessionExerciseCard(
                    exerciseUi = exerciseUi,
                    units = state.units,
                    expanded = state.expandedExerciseId == exerciseUi.exercise.id,
                    onToggleExpand = { viewModel.toggleExpanded(exerciseUi.exercise.id) },
                    onToggleSet = { index -> viewModel.toggleSetComplete(exerciseUi.exercise.id, index) },
                    onWeightChange = { index, kg -> viewModel.updateWeightKg(exerciseUi.exercise.id, index, kg) },
                    onRepsChange = { index, reps -> viewModel.updateReps(exerciseUi.exercise.id, index, reps) },
                    onAddSet = { viewModel.addSet(exerciseUi.exercise.id) },
                )
            }

            item {
                RepsButton(
                    text = if (state.completedSetCount == 0) {
                        stringResource(R.string.workouts_session_finish)
                    } else {
                        stringResource(
                            R.string.workouts_session_finish_with_count,
                            state.completedSetCount,
                            state.totalSetCount,
                        )
                    },
                    onClick = ::requestFinish,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }

        if (showRestTimer) {
            RestTimer(
                state = restTimerState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 24.dp),
            )
        }
    }

    if (showNoSetsDialog) {
        AlertDialog(
            onDismissRequest = { showNoSetsDialog = false },
            containerColor = RepsTheme.colors.surfaceElevated,
            title = { Text(stringResource(R.string.workouts_session_no_sets_title), color = RepsTheme.colors.textPrimary) },
            text = { Text(stringResource(R.string.workouts_session_no_sets_body), color = RepsTheme.colors.textSecondary) },
            confirmButton = {
                TextButton(onClick = {
                    showNoSetsDialog = false
                    scope.launch { completionResult = viewModel.finish() }
                }) {
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

    completionResult?.let { result ->
        AlertDialog(
            onDismissRequest = {},
            containerColor = RepsTheme.colors.surfaceElevated,
            title = { Text(stringResource(R.string.workouts_session_complete_title), color = RepsTheme.colors.textPrimary) },
            text = {
                Text(
                    text = stringResource(
                        R.string.workouts_session_complete_body,
                        result.volumeKg.roundToInt().toString(),
                        result.completedSetCount,
                    ),
                    color = RepsTheme.colors.textSecondary,
                )
            },
            confirmButton = {
                TextButton(onClick = onFinished) {
                    Text(stringResource(R.string.workouts_session_done), color = RepsGreen)
                }
            },
        )
    }
}

@Composable
private fun SessionExerciseCard(
    exerciseUi: SessionExerciseUi,
    units: UnitSystem,
    expanded: Boolean,
    onToggleExpand: () -> Unit,
    onToggleSet: (Int) -> Unit,
    onWeightChange: (Int, Double) -> Unit,
    onRepsChange: (Int, Int) -> Unit,
    onAddSet: () -> Unit,
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
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(exercise.name, style = MaterialTheme.typography.titleSmall, color = RepsTheme.colors.textPrimary)
                Text(
                    text = "${stringResource(exercise.muscleGroup.labelRes)} · ${exercise.equipment}",
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

        if (expanded) {
            ExerciseMediaCard(
                mediaUrl = exercise.mediaUrl,
                contentDescription = stringResource(R.string.exercise_media_cd, exercise.name),
                modifier = Modifier.padding(top = 14.dp),
            )

            Row(Modifier.fillMaxWidth().padding(top = 14.dp)) {
                Text(
                    stringResource(R.string.workouts_set),
                    style = MaterialTheme.typography.labelSmall,
                    color = RepsTheme.colors.textSecondary,
                    modifier = Modifier.weight(0.7f),
                )
                Text(
                    stringResource(if (units == UnitSystem.METRIC) R.string.workouts_kg else R.string.workouts_lb),
                    style = MaterialTheme.typography.labelSmall,
                    color = RepsTheme.colors.textSecondary,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                )
                Text(
                    stringResource(R.string.workouts_reps),
                    style = MaterialTheme.typography.labelSmall,
                    color = RepsTheme.colors.textSecondary,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.size(36.dp))
            }

            exerciseUi.sets.forEachIndexed { index, set ->
                SetRow(
                    index = index,
                    set = set,
                    units = units,
                    onToggle = { onToggleSet(index) },
                    onWeightChange = { onWeightChange(index, it) },
                    onRepsChange = { onRepsChange(index, it) },
                )
            }

            RepsOutlinedButton(
                text = stringResource(R.string.workouts_add_set),
                onClick = onAddSet,
                leadingIcon = rememberVectorPainter(Icons.Outlined.Add),
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}

@Composable
private fun SetRow(
    index: Int,
    set: SessionSetUi,
    units: UnitSystem,
    onToggle: () -> Unit,
    onWeightChange: (Double) -> Unit,
    onRepsChange: (Int) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = (index + 1).toString(),
            style = MaterialTheme.typography.bodyMedium,
            color = RepsTheme.colors.textSecondary,
            modifier = Modifier.weight(0.7f),
        )
        SetValueField(
            value = UnitConverter.formatWeight(set.weightKg, units, decimals = 1),
            onValueChange = { text ->
                val typed = text.toDoubleOrNull() ?: return@SetValueField
                onWeightChange(UnitConverter.weightToKg(typed, units))
            },
            modifier = Modifier.weight(1f).padding(horizontal = 4.dp),
        )
        SetValueField(
            value = set.reps.toString(),
            onValueChange = { text -> text.toIntOrNull()?.let(onRepsChange) },
            modifier = Modifier.weight(1f).padding(horizontal = 4.dp),
        )
        val checkColor by animateColorAsState(
            targetValue = if (set.completed) RepsGreen else RepsTheme.colors.surfaceElevated,
            animationSpec = tween(180),
            label = "setCheck",
        )
        Box(
            Modifier
                .size(32.dp)
                .background(checkColor, MaterialTheme.shapes.small)
                .border(1.dp, if (set.completed) RepsGreen else RepsTheme.colors.outline, MaterialTheme.shapes.small)
                .clickable(onClick = onToggle),
            contentAlignment = Alignment.Center,
        ) {
            if (set.completed) {
                Icon(Icons.Filled.Check, contentDescription = null, tint = RepsOnGreen, modifier = Modifier.size(16.dp))
            }
        }
    }
}

@Composable
private fun SetValueField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = TextStyle(
            color = RepsTheme.colors.textPrimary,
            fontSize = MaterialTheme.typography.bodyMedium.fontSize,
            textAlign = TextAlign.Center,
        ),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        cursorBrush = SolidColor(RepsGreen),
        modifier = modifier
            .height(36.dp)
            .background(RepsTheme.colors.surfaceElevated, MaterialTheme.shapes.small),
        decorationBox = { inner ->
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { inner() }
        },
    )
}
