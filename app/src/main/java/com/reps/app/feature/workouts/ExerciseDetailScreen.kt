package com.reps.app.feature.workouts

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.reps.app.R
import com.reps.app.core.components.MuscleMapDiagram
import com.reps.app.core.components.RepsBackButton
import com.reps.app.core.theme.PillShape
import com.reps.app.core.theme.RepsError
import com.reps.app.core.theme.RepsGreen
import com.reps.app.core.theme.RepsOnGreen
import com.reps.app.core.theme.RepsTheme
import com.reps.app.domain.model.Exercise
import com.reps.app.domain.model.MuscleGroup
import com.reps.app.navigation.navBarClearance

@Composable
fun ExerciseDetailScreen(
    onBack: () -> Unit,
    viewModel: ExerciseDetailViewModel = hiltViewModel(),
) {
    val exercise by viewModel.exercise.collectAsStateWithLifecycle()
    exercise?.let { ExerciseDetailContent(exercise = it, onBack = onBack) }
}

@Composable
private fun ExerciseDetailContent(exercise: Exercise, onBack: () -> Unit) {
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

        item { ExerciseMediaTabs(exercise) }

        item {
            Column {
                Text(
                    text = exercise.name,
                    style = MaterialTheme.typography.headlineSmall,
                    color = RepsTheme.colors.textPrimary,
                )
                Row(
                    modifier = Modifier.padding(top = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    InfoTag(stringResource(exercise.muscleGroup.labelRes))
                    InfoTag(exercise.equipment)
                    InfoTag(stringResource(exercise.difficulty.labelRes))
                }
            }
        }

        item {
            Column {
                Text(
                    text = stringResource(R.string.workouts_description).uppercase(),
                    style = RepsTheme.textStyles.eyebrow,
                    color = RepsGreen,
                )
                Text(
                    text = exercise.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = RepsTheme.colors.textSecondary,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }

        if (exercise.mistakes.isNotEmpty()) {
            item {
                Text(
                    text = stringResource(R.string.workouts_common_mistakes).uppercase(),
                    style = RepsTheme.textStyles.eyebrow,
                    color = RepsGreen,
                )
            }
            items(exercise.mistakes) { mistake ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("•", style = MaterialTheme.typography.bodyMedium, color = RepsError)
                    Text(
                        text = mistake,
                        style = MaterialTheme.typography.bodySmall,
                        color = RepsTheme.colors.textSecondary,
                    )
                }
            }
        }
    }
}

/**
 * The demonstration block: two small tabs - a full-body muscle map highlighting
 * the worked group, and a step-by-step how-to (photos supplied later).
 */
@Composable
private fun ExerciseMediaTabs(exercise: Exercise) {
    var selected by rememberSaveable { mutableIntStateOf(0) }
    Column {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MediaTab(
                label = stringResource(R.string.exercise_tab_muscles),
                selected = selected == 0,
                modifier = Modifier.weight(1f),
            ) { selected = 0 }
            MediaTab(
                label = stringResource(R.string.exercise_tab_howto),
                selected = selected == 1,
                modifier = Modifier.weight(1f),
            ) { selected = 1 }
        }
        Box(
            Modifier
                .padding(top = 12.dp)
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.large)
                .background(RepsTheme.colors.surfaceElevated)
                .padding(16.dp),
            contentAlignment = Alignment.Center,
        ) {
            when (selected) {
                0 -> MuscleMapDiagram(
                    highlightedMuscles = exercise.muscleGroup.mapKeys(),
                    contentDescription = stringResource(
                        R.string.muscle_map_cd,
                        stringResource(exercise.muscleGroup.labelRes),
                    ),
                )
                else -> HowToPlaceholder()
            }
        }
    }
}

@Composable
private fun MediaTab(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier
            .clip(PillShape)
            .background(if (selected) RepsGreen else RepsTheme.colors.surface)
            .clickable(onClick = onClick)
            .padding(vertical = 9.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) RepsOnGreen else RepsTheme.colors.textSecondary,
        )
    }
}

@Composable
private fun HowToPlaceholder() {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            repeat(2) {
                Box(
                    Modifier
                        .weight(1f)
                        .aspectRatio(1f)
                        .clip(MaterialTheme.shapes.medium)
                        .background(RepsTheme.colors.surface),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.FitnessCenter,
                        contentDescription = null,
                        tint = RepsTheme.colors.textTertiary,
                        modifier = Modifier.size(28.dp),
                    )
                }
            }
        }
        Text(
            text = stringResource(R.string.exercise_howto_placeholder),
            style = MaterialTheme.typography.bodySmall,
            color = RepsTheme.colors.textTertiary,
            modifier = Modifier.padding(top = 12.dp),
        )
    }
}

/** The map region name(s) this group highlights; CARDIO lights nothing. */
private fun MuscleGroup.mapKeys(): List<String> = when (this) {
    MuscleGroup.CHEST -> listOf("chest")
    MuscleGroup.BACK -> listOf("back")
    MuscleGroup.LEGS -> listOf("legs")
    MuscleGroup.SHOULDERS -> listOf("shoulders")
    MuscleGroup.ARMS -> listOf("arms")
    MuscleGroup.ABS -> listOf("abs")
    // The source art has no separate glutes shape; the back/lower-back region
    // it's attached to is the closest verified match.
    MuscleGroup.GLUTES -> listOf("back")
    MuscleGroup.CARDIO -> emptyList()
}

/** A plain, non-interactive descriptive pill - muscle group / equipment / difficulty. */
@Composable
private fun InfoTag(text: String) {
    Box(
        Modifier
            .background(RepsTheme.colors.surface, PillShape)
            .border(1.dp, RepsTheme.colors.outline, PillShape)
            .padding(horizontal = 12.dp, vertical = 7.dp),
    ) {
        Text(text = text, style = MaterialTheme.typography.labelSmall, color = RepsTheme.colors.textSecondary)
    }
}
