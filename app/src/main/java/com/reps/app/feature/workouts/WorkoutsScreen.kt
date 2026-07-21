package com.reps.app.feature.workouts

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.reps.app.R
import com.reps.app.core.components.RepsChip
import com.reps.app.core.components.RepsTextField
import com.reps.app.core.components.SectionHeader
import com.reps.app.core.theme.RepsGreen
import com.reps.app.core.theme.RepsNearBlack
import com.reps.app.core.theme.RepsOnGreen
import com.reps.app.core.theme.RepsTextSecondary
import com.reps.app.core.theme.RepsTextTertiary
import com.reps.app.core.theme.RepsTheme
import com.reps.app.domain.model.MuscleGroup
import com.reps.app.navigation.OnTabReselected
import com.reps.app.navigation.Routes
import com.reps.app.navigation.navBarClearance

@Composable
fun WorkoutsScreen(
    onOpenExercise: (String) -> Unit,
    onStartWorkout: (String) -> Unit,
    onOpenBuilder: () -> Unit,
    viewModel: WorkoutsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()

    OnTabReselected(Routes.WORKOUTS) { listState.animateScrollToItem(0) }

    val dimens = RepsTheme.dimens
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().background(RepsNearBlack).statusBarsPadding(),
        contentPadding = PaddingValues(
            start = dimens.screenPadding,
            end = dimens.screenPadding,
            bottom = navBarClearance(),
        ),
        verticalArrangement = Arrangement.spacedBy(dimens.sectionGap),
    ) {
        item {
            SectionHeader(
                eyebrow = stringResource(R.string.workouts_eyebrow),
                title = stringResource(R.string.workouts_title),
                modifier = Modifier.padding(top = 8.dp),
            )
        }

        item {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.workouts_my_workouts),
                    style = MaterialTheme.typography.titleSmall,
                    color = com.reps.app.core.theme.RepsTextPrimary,
                )
                Box(
                    Modifier
                        .size(32.dp)
                        .background(RepsGreen, MaterialTheme.shapes.small)
                        .clickable(role = Role.Button, onClick = onOpenBuilder),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.workouts_builder_title), tint = RepsOnGreen, modifier = Modifier.size(18.dp))
                }
            }
        }
        item {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(vertical = 2.dp),
            ) {
                items(state.myWorkouts, key = { it.id }) { workout ->
                    MiniWorkoutCard(workout = workout, onClick = { onStartWorkout(workout.id) })
                }
            }
        }

        item {
            RepsTextField(
                value = state.query,
                onValueChange = viewModel::onQueryChange,
                placeholder = stringResource(R.string.workouts_search),
                leadingIcon = rememberVectorPainter(Icons.Outlined.Search),
                imeAction = ImeAction.Search,
                keyboardType = androidx.compose.ui.text.input.KeyboardType.Text,
            )
        }
        item {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 2.dp),
            ) {
                item {
                    RepsChip(
                        label = stringResource(R.string.workouts_filter_all),
                        selected = state.activeFilter == null,
                        onClick = { viewModel.onFilterChange(null) },
                    )
                }
                items(MuscleGroup.entries.toList()) { group ->
                    RepsChip(
                        label = stringResource(group.labelRes),
                        selected = state.activeFilter == group,
                        onClick = { viewModel.onFilterChange(group) },
                    )
                }
            }
        }

        if (state.exercises.isEmpty() && !state.loading) {
            item { EmptyExerciseState() }
        } else {
            items(state.exercises, key = { it.id }) { exercise ->
                ExerciseRow(exercise = exercise, onClick = { onOpenExercise(exercise.id) })
            }
        }
    }
}

@Composable
private fun EmptyExerciseState() {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(52.dp)
                .background(com.reps.app.core.theme.RepsSurface, androidx.compose.foundation.shape.CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Outlined.SearchOff, contentDescription = null, tint = RepsTextTertiary, modifier = Modifier.size(22.dp))
        }
        Text(
            text = stringResource(R.string.workouts_no_matches_title),
            style = MaterialTheme.typography.titleSmall,
            color = com.reps.app.core.theme.RepsTextPrimary,
            modifier = Modifier.padding(top = 14.dp),
        )
        Text(
            text = stringResource(R.string.workouts_no_matches_subtitle),
            style = MaterialTheme.typography.bodySmall,
            color = RepsTextSecondary,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}
