package com.reps.app.feature.workouts.builder

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.reps.app.R
import com.reps.app.core.components.RepsBackButton
import com.reps.app.core.components.RepsBottomSheet
import com.reps.app.core.components.RepsButton
import com.reps.app.core.components.RepsChip
import com.reps.app.core.components.RepsOutlinedButton
import com.reps.app.core.components.RepsTextField
import com.reps.app.core.components.SectionHeader
import com.reps.app.core.theme.RepsError
import com.reps.app.core.theme.RepsNearBlack
import com.reps.app.core.theme.RepsSurfaceElevated
import com.reps.app.core.theme.RepsTextPrimary
import com.reps.app.core.theme.RepsTextSecondary
import com.reps.app.core.theme.RepsTextTertiary
import com.reps.app.core.theme.RepsTheme
import com.reps.app.domain.model.Exercise
import com.reps.app.domain.model.MuscleGroup
import com.reps.app.feature.workouts.ExerciseRow
import com.reps.app.navigation.navBarClearance
import kotlinx.coroutines.launch

@Composable
fun WorkoutBuilderScreen(
    onBack: () -> Unit,
    onSaved: () -> Unit,
    viewModel: WorkoutBuilderViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var showPicker by remember { mutableStateOf(false) }

    val dimens = RepsTheme.dimens
    LazyColumn(
        modifier = Modifier.fillMaxSize().background(RepsNearBlack).statusBarsPadding(),
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
                title = stringResource(R.string.workouts_builder_title),
            )
        }
        item {
            RepsTextField(
                value = state.name,
                onValueChange = viewModel::onNameChange,
                placeholder = stringResource(R.string.workouts_builder_name_hint),
            )
        }

        item {
            RepsOutlinedButton(
                text = stringResource(R.string.workouts_builder_add),
                onClick = { showPicker = true },
                leadingIcon = rememberVectorPainter(Icons.Outlined.Add),
            )
        }

        if (state.selectedExercises.isEmpty()) {
            item {
                Text(
                    text = stringResource(R.string.workouts_builder_empty),
                    style = MaterialTheme.typography.bodySmall,
                    color = RepsTextTertiary,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            }
        } else {
            items(state.selectedExercises, key = { it.id }) { exercise ->
                SelectedExerciseRow(exercise = exercise, onRemove = { viewModel.removeExercise(exercise.id) })
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
                onClick = { scope.launch { if (viewModel.save()) onSaved() } },
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

@Composable
private fun SelectedExerciseRow(exercise: Exercise, onRemove: () -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = exercise.name,
                style = MaterialTheme.typography.bodyLarge,
                color = RepsTextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = stringResource(exercise.muscleGroup.labelRes),
                style = MaterialTheme.typography.labelSmall,
                color = RepsTextSecondary,
            )
        }
        IconButton(onClick = onRemove) {
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = stringResource(R.string.workouts_builder_remove_cd),
                tint = RepsTextTertiary,
            )
        }
    }
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
            color = RepsTextSecondary,
        )
        RepsTextField(
            value = state.pickerQuery,
            onValueChange = onQueryChange,
            placeholder = stringResource(R.string.workouts_search),
            leadingIcon = rememberVectorPainter(Icons.Outlined.Search),
            modifier = Modifier.padding(top = 10.dp),
        )
        androidx.compose.foundation.lazy.LazyRow(
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
            items(MuscleGroup.entries.toList()) { group ->
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
