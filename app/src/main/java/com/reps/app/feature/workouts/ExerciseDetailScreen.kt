package com.reps.app.feature.workouts

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.reps.app.R
import com.reps.app.core.components.ExerciseMediaCard
import com.reps.app.core.components.RepsBackButton
import com.reps.app.core.theme.PillShape
import com.reps.app.core.theme.RepsError
import com.reps.app.core.theme.RepsGreen
import com.reps.app.core.theme.RepsNearBlack
import com.reps.app.core.theme.RepsOutline
import com.reps.app.core.theme.RepsSurface
import com.reps.app.core.theme.RepsTextPrimary
import com.reps.app.core.theme.RepsTextSecondary
import com.reps.app.core.theme.RepsTheme
import com.reps.app.domain.model.Exercise
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
            ExerciseMediaCard(
                mediaUrl = exercise.mediaUrl,
                contentDescription = stringResource(R.string.exercise_media_cd, exercise.name),
            )
        }

        item {
            Column {
                Text(
                    text = exercise.name,
                    style = MaterialTheme.typography.headlineSmall,
                    color = RepsTextPrimary,
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
                    color = RepsTextSecondary,
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
                        color = RepsTextSecondary,
                    )
                }
            }
        }
    }
}

/** A plain, non-interactive descriptive pill - muscle group / equipment / difficulty. */
@Composable
private fun InfoTag(text: String) {
    Box(
        Modifier
            .background(RepsSurface, PillShape)
            .border(1.dp, RepsOutline, PillShape)
            .padding(horizontal = 12.dp, vertical = 7.dp),
    ) {
        Text(text = text, style = MaterialTheme.typography.labelSmall, color = RepsTextSecondary)
    }
}
