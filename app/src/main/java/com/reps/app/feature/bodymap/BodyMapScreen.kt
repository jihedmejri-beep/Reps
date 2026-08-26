package com.reps.app.feature.bodymap

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.reps.app.R
import com.reps.app.core.components.BodyMapDiagram
import com.reps.app.core.components.BodyMapRegion
import com.reps.app.core.components.RepsBackButton
import com.reps.app.core.components.RepsChip
import com.reps.app.core.components.SectionHeader
import com.reps.app.core.theme.RepsTheme
import com.reps.app.core.util.AnatomyNames
import com.reps.app.feature.workouts.ExerciseRow

@Composable
fun BodyMapScreen(
    onBack: () -> Unit,
    onOpenExercise: (String) -> Unit,
    viewModel: BodyMapViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val dimens = RepsTheme.dimens

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(RepsTheme.colors.background).statusBarsPadding(),
        contentPadding = PaddingValues(
            start = dimens.screenPadding,
            end = dimens.screenPadding,
            top = 8.dp,
            bottom = 24.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(dimens.sectionGap),
    ) {
        item { RepsBackButton(onClick = onBack) }
        item {
            SectionHeader(
                eyebrow = stringResource(R.string.body_map_eyebrow),
                title = stringResource(R.string.body_map_title),
            )
        }
        item {
            Text(
                text = stringResource(R.string.body_map_hint),
                style = MaterialTheme.typography.bodySmall,
                color = RepsTheme.colors.textSecondary,
            )
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RepsChip(
                    label = stringResource(BodySide.FRONT.labelRes),
                    selected = state.side == BodySide.FRONT,
                    onClick = { viewModel.onSideChange(BodySide.FRONT) },
                )
                RepsChip(
                    label = stringResource(BodySide.BACK.labelRes),
                    selected = state.side == BodySide.BACK,
                    onClick = { viewModel.onSideChange(BodySide.BACK) },
                )
            }
        }

        item {
            // The artwork is tall (200x369), so the diagram is capped in height
            // and centred rather than allowed to push the results off screen.
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                val muscles = if (state.side == BodySide.FRONT) state.frontMuscles else state.backMuscles
                val bodyAsset = if (state.side == BodySide.FRONT) state.frontBodyAsset else state.backBodyAsset
                if (!state.loading && muscles.isEmpty()) {
                    Text(
                        text = stringResource(R.string.exercise_no_muscle_data),
                        style = MaterialTheme.typography.bodySmall,
                        color = RepsTheme.colors.textTertiary,
                        modifier = Modifier.padding(vertical = 32.dp),
                    )
                } else {
                    BodyMapDiagram(
                        bodyAsset = bodyAsset,
                        muscles = muscles.map { BodyMapRegion(it.name, it.overlayAsset) },
                        selectedMuscle = state.selectedMuscle?.name,
                        onMuscleSelect = viewModel::onMuscleTap,
                        modifier = Modifier.heightIn(max = DIAGRAM_MAX_HEIGHT_DP.dp),
                        contentDescription = state.selectedMuscle?.let {
                            stringResource(R.string.muscle_map_cd, it.displayName)
                        },
                    )
                }
            }
        }

        item {
            val selected = state.selectedMuscle
            if (selected == null) {
                Text(
                    text = stringResource(R.string.body_map_pick_prompt),
                    style = MaterialTheme.typography.titleSmall,
                    color = RepsTheme.colors.textTertiary,
                )
            } else {
                Column {
                    Text(
                        text = localizedMuscleName(selected.name, selected.displayName),
                        style = MaterialTheme.typography.titleMedium,
                        color = RepsTheme.colors.textPrimary,
                    )
                    Text(
                        text = stringResource(R.string.body_map_exercise_count, state.exercises.size),
                        style = MaterialTheme.typography.labelSmall,
                        color = RepsTheme.colors.textSecondary,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
        }

        items(state.exercises, key = { it.id }) { exercise ->
            ExerciseRow(exercise = exercise, onClick = { onOpenExercise(exercise.id) })
        }
    }
}

@Composable
private fun localizedMuscleName(name: String, fallback: String): String =
    AnatomyNames.resOf(name)?.let { stringResource(it) } ?: fallback

private const val DIAGRAM_MAX_HEIGHT_DP = 440
