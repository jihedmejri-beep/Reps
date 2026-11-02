package com.reps.app.feature.workouts.builder

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.reps.app.R
import com.reps.app.core.components.NumericTextField
import com.reps.app.core.components.RepsBackButton
import com.reps.app.core.components.RepsBottomSheet
import com.reps.app.core.components.RepsButton
import com.reps.app.core.components.RepsChip
import com.reps.app.core.components.RepsOutlinedButton
import com.reps.app.core.components.RepsTextField
import com.reps.app.core.components.SectionHeader
import com.reps.app.core.theme.RepsError
import com.reps.app.core.theme.RepsTheme
import com.reps.app.core.util.DateUtils
import com.reps.app.core.util.InputLimits
import com.reps.app.core.util.NumberInput
import com.reps.app.core.util.UnitConverter
import com.reps.app.domain.model.Exercise
import com.reps.app.domain.model.MuscleGroup
import com.reps.app.domain.model.UnitSystem
import com.reps.app.feature.workouts.ExerciseRow
import com.reps.app.navigation.navBarClearance
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale

/** Rest presets the per-exercise chip cycles through, in seconds. */
private val RestPresets = listOf(60, 90, 120, 180, 240)

@Composable
fun WorkoutBuilderScreen(
    onBack: () -> Unit,
    onSaved: () -> Unit,
    viewModel: WorkoutBuilderViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var showPicker by remember { mutableStateOf(false) }

    // Reminders follow the schedule, so a scheduled workout is the moment the
    // notification permission (Android 13+) actually means something to ask for.
    val context = LocalContext.current
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { onSaved() }
    val needsPermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
    fun afterSave() {
        if (state.scheduledDays.isNotEmpty() && needsPermission) {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            onSaved()
        }
    }

    val dimens = RepsTheme.dimens
    LazyColumn(
        modifier = Modifier.fillMaxSize().background(RepsTheme.colors.background).statusBarsPadding(),
        contentPadding = PaddingValues(
            start = dimens.screenPadding,
            end = dimens.screenPadding,
            top = 8.dp,
            bottom = navBarClearance(),
        ),
        verticalArrangement = Arrangement.spacedBy(dimens.sectionGap),
    ) {
        item { RepsBackButton(onClick = onBack) }
        item {
            SectionHeader(
                eyebrow = stringResource(R.string.workouts_builder_eyebrow),
                title = stringResource(if (state.editing) R.string.workouts_builder_edit_title else R.string.workouts_builder_title),
            )
        }
        item {
            RepsTextField(
                value = state.name,
                onValueChange = viewModel::onNameChange,
                placeholder = stringResource(R.string.workouts_builder_name_hint),
            )
        }

        item { ScheduleRow(selected = state.scheduledDays, onToggle = viewModel::toggleDay) }

        item {
            RepsOutlinedButton(
                text = stringResource(R.string.workouts_builder_add),
                onClick = { showPicker = true },
                leadingIcon = rememberVectorPainter(Icons.Outlined.Add),
            )
        }

        if (state.planned.isEmpty()) {
            item {
                Text(
                    text = stringResource(R.string.workouts_builder_empty),
                    style = MaterialTheme.typography.bodySmall,
                    color = RepsTheme.colors.textTertiary,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            }
        } else {
            items(state.planned, key = { it.exerciseId }) { plan ->
                val exercise = state.exercisesById[plan.exerciseId] ?: return@items
                PlannedExerciseCard(
                    exercise = exercise,
                    plan = plan,
                    units = state.units,
                    isFirst = plan == state.planned.first(),
                    isLast = plan == state.planned.last(),
                    onChange = { transform -> viewModel.updatePlan(plan.exerciseId, transform) },
                    onMove = { delta -> viewModel.move(plan.exerciseId, delta) },
                    onRemove = { viewModel.removeExercise(plan.exerciseId) },
                )
            }
            item {
                Text(
                    text = stringResource(R.string.workouts_estimated_min, state.estimatedMinutes),
                    style = MaterialTheme.typography.labelMedium,
                    color = RepsTheme.colors.textSecondary,
                )
            }
        }

        if (state.isAtHardCap) {
            item {
                Text(
                    text = stringResource(R.string.workouts_cap_reached),
                    style = MaterialTheme.typography.bodySmall,
                    color = RepsError,
                )
            }
        } else if (state.isOverWarnThreshold) {
            item {
                Text(
                    text = stringResource(R.string.workouts_warn_over_ten),
                    style = MaterialTheme.typography.bodySmall,
                    color = RepsError,
                )
            }
        }

        item {
            RepsButton(
                text = stringResource(R.string.workouts_builder_save),
                onClick = { scope.launch { if (viewModel.save()) afterSave() } },
                enabled = state.canSave,
                loading = state.saving,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }

    if (showPicker) {
        ExercisePickerSheet(
            state = state,
            onQueryChange = viewModel::onPickerQueryChange,
            onFilterChange = viewModel::onPickerFilterChange,
            onToggle = viewModel::toggleExercise,
            onDismiss = { showPicker = false },
        )
    }
}

/** Weekday chips, Monday first. The schedule drives Home's "today", reminders and the streak. */
@Composable
private fun ScheduleRow(selected: Set<DayOfWeek>, onToggle: (DayOfWeek) -> Unit) {
    val locale = LocalConfiguration.current.locales[0]
    Column {
        Text(stringResource(R.string.workouts_builder_schedule), style = MaterialTheme.typography.titleSmall, color = RepsTheme.colors.textPrimary)
        Text(
            stringResource(R.string.workouts_builder_schedule_hint),
            style = MaterialTheme.typography.bodySmall,
            color = RepsTheme.colors.textSecondary,
            modifier = Modifier.padding(top = 2.dp, bottom = 10.dp),
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(DayOfWeek.entries) { day ->
                RepsChip(
                    label = day.getDisplayName(TextStyle.SHORT, locale),
                    selected = day in selected,
                    onClick = { onToggle(day) },
                )
            }
        }
    }
}

@Composable
private fun PlannedExerciseCard(
    exercise: Exercise,
    plan: PlannedExercise,
    units: UnitSystem,
    isFirst: Boolean,
    isLast: Boolean,
    onChange: ((PlannedExercise) -> PlannedExercise) -> Unit,
    onMove: (Int) -> Unit,
    onRemove: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(RepsTheme.colors.surface, MaterialTheme.shapes.large)
            .padding(start = 14.dp, end = 4.dp, top = 6.dp, bottom = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(exercise.name, style = MaterialTheme.typography.bodyLarge, color = RepsTheme.colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(stringResource(exercise.muscleGroup.labelRes), style = MaterialTheme.typography.labelSmall, color = RepsTheme.colors.textSecondary)
            }
            IconButton(onClick = { onMove(-1) }, enabled = !isFirst) {
                Icon(Icons.Filled.KeyboardArrowUp, contentDescription = stringResource(R.string.workouts_builder_move_up_cd, exercise.name), tint = RepsTheme.colors.textSecondary)
            }
            IconButton(onClick = { onMove(1) }, enabled = !isLast) {
                Icon(Icons.Filled.KeyboardArrowDown, contentDescription = stringResource(R.string.workouts_builder_move_down_cd, exercise.name), tint = RepsTheme.colors.textSecondary)
            }
            IconButton(onClick = onRemove) {
                Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.workouts_builder_remove_cd), tint = RepsTheme.colors.textTertiary)
            }
        }
        Row(
            Modifier.padding(top = 8.dp, end = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SetsStepper(
                sets = plan.sets,
                onChange = { sets -> onChange { it.copy(sets = sets.coerceIn(PlannedExercise.SETS_RANGE)) } },
            )
            NumericTextField(
                value = plan.reps.toString(),
                onValue = { text -> NumberInput.parseIntIn(text, 1..InputLimits.SET_REPS.last)?.let { reps -> onChange { it.copy(reps = reps) } } },
                label = stringResource(R.string.workouts_reps),
                decimal = false,
                modifier = Modifier.weight(1f),
            )
            NumericTextField(
                value = weightText(plan.weightKg, units),
                onValue = { text ->
                    NumberInput.parseDecimal(text)?.let { UnitConverter.weightToKg(it, units) }
                        ?.takeIf { it in InputLimits.SET_WEIGHT_KG }
                        ?.let { kg -> onChange { it.copy(weightKg = kg) } }
                },
                label = stringResource(if (units == UnitSystem.METRIC) R.string.workouts_kg else R.string.workouts_lb),
                modifier = Modifier.weight(1f),
            )
        }
        val restLabel = DateUtils.formatClock(plan.restSeconds)
        Text(
            text = stringResource(R.string.session_rest_chip, restLabel),
            style = MaterialTheme.typography.labelSmall,
            color = RepsTheme.colors.textPrimary,
            modifier = Modifier
                .padding(top = 10.dp)
                .background(RepsTheme.colors.surfaceElevated, CircleShape)
                .clickable(role = Role.Button, onClickLabel = stringResource(R.string.session_rest_chip_cd, restLabel)) {
                    val next = RestPresets.firstOrNull { it > plan.restSeconds } ?: RestPresets.first()
                    onChange { it.copy(restSeconds = next) }
                }
                .padding(horizontal = 12.dp, vertical = 8.dp),
        )
    }
}

@Composable
private fun SetsStepper(sets: Int, onChange: (Int) -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(stringResource(R.string.workouts_builder_sets), style = MaterialTheme.typography.labelSmall, color = RepsTheme.colors.textSecondary)
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { onChange(sets - 1) }, enabled = sets > PlannedExercise.SETS_RANGE.first) {
                Icon(Icons.Outlined.Remove, contentDescription = stringResource(R.string.workouts_builder_fewer_sets_cd), tint = RepsTheme.colors.textPrimary)
            }
            Text(sets.toString(), style = MaterialTheme.typography.titleSmall, color = RepsTheme.colors.textPrimary, modifier = Modifier.width(20.dp))
            IconButton(onClick = { onChange(sets + 1) }, enabled = sets < PlannedExercise.SETS_RANGE.last) {
                Icon(Icons.Outlined.Add, contentDescription = stringResource(R.string.workouts_builder_more_sets_cd), tint = RepsTheme.colors.textPrimary)
            }
        }
    }
}

private fun weightText(kg: Double, units: UnitSystem): String {
    if (kg == 0.0) return "0"
    val value = Math.round(UnitConverter.displayWeight(kg, units) * 10) / 10.0
    return if (value % 1.0 == 0.0) value.toLong().toString() else String.format(Locale.US, "%.1f", value)
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun ExercisePickerSheet(
    state: WorkoutBuilderUiState,
    onQueryChange: (String) -> Unit,
    onFilterChange: (MuscleGroup?) -> Unit,
    onToggle: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    RepsBottomSheet(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.workouts_builder_add),
    ) {
        Text(
            text = stringResource(R.string.workouts_builder_selected, state.selectedExerciseIds.size),
            style = MaterialTheme.typography.labelSmall,
            color = RepsTheme.colors.textSecondary,
        )
        RepsTextField(
            value = state.pickerQuery,
            onValueChange = onQueryChange,
            placeholder = stringResource(R.string.workouts_search),
            leadingIcon = rememberVectorPainter(Icons.Outlined.Search),
            modifier = Modifier.padding(top = 10.dp),
        )
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(vertical = 10.dp),
        ) {
            item {
                RepsChip(
                    label = stringResource(R.string.workouts_filter_all),
                    selected = state.pickerFilter == null,
                    onClick = { onFilterChange(null) },
                )
            }
            items(MuscleGroup.entries) { group ->
                RepsChip(
                    label = stringResource(group.labelRes),
                    selected = state.pickerFilter == group,
                    onClick = { onFilterChange(group) },
                )
            }
        }
        Column(Modifier.height(360.dp)) {
            LazyColumn {
                items(state.pickerResults, key = { it.id }) { exercise ->
                    ExerciseRow(
                        exercise = exercise,
                        onClick = { onToggle(exercise.id) },
                        selected = exercise.id in state.selectedExerciseIds,
                    )
                }
            }
        }
    }
}
