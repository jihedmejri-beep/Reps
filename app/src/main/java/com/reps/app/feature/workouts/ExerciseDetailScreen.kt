package com.reps.app.feature.workouts

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.reps.app.R
import com.reps.app.core.components.ExerciseMediaCard
import com.reps.app.core.components.MuscleTargetDiagram
import com.reps.app.core.components.RepsBackButton
import com.reps.app.core.theme.PillShape
import com.reps.app.core.theme.RepsGreen
import com.reps.app.core.theme.RepsOnGreen
import com.reps.app.core.theme.RepsTheme
import com.reps.app.domain.model.ExerciseDetail
import com.reps.app.domain.model.MuscleDiagram
import com.reps.app.domain.model.MuscleTarget
import com.reps.app.navigation.navBarClearance

@Composable
fun ExerciseDetailScreen(
    onBack: () -> Unit,
    viewModel: ExerciseDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val detail = state.detail

    when {
        detail != null -> ExerciseDetailContent(
            detail = detail,
            diagram = state.diagram,
            onBack = onBack,
        )
        state.notFound -> ExerciseUnavailable(onBack = onBack)
        else -> LoadingState()
    }
}

@Composable
private fun ExerciseDetailContent(
    detail: ExerciseDetail,
    diagram: MuscleDiagram,
    onBack: () -> Unit,
) {
    val dimens = RepsTheme.dimens
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(RepsTheme.colors.background)
            .statusBarsPadding(),
        contentPadding = PaddingValues(
            start = dimens.screenPadding,
            end = dimens.screenPadding,
            top = 8.dp,
            bottom = navBarClearance(),
        ),
        verticalArrangement = Arrangement.spacedBy(dimens.sectionGap),
    ) {
        item { RepsBackButton(onClick = onBack) }

        item { ExerciseMediaTabs(detail, diagram) }

        item {
            Column {
                Text(
                    text = detail.name,
                    style = MaterialTheme.typography.headlineSmall,
                    color = RepsTheme.colors.textPrimary,
                )
                FlowRow(
                    modifier = Modifier.padding(top = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    InfoTag(stringResource(detail.exercise.muscleGroup.labelRes))
                    detail.equipment.forEach { InfoTag(it) }
                    // The catalogue records no difficulty, so this tag simply
                    // does not appear rather than defaulting to "Beginner".
                    detail.exercise.difficulty?.let { InfoTag(stringResource(it.labelRes)) }
                }
            }
        }

        if (detail.allMuscles.isNotEmpty()) {
            item {
                Section(stringResource(R.string.exercise_section_muscles)) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        detail.primaryMuscles.forEach { MuscleTag(it, primary = true) }
                        detail.secondaryMuscles.forEach { MuscleTag(it, primary = false) }
                    }
                }
            }
        }

        if (detail.summary.isNotBlank()) {
            item {
                Section(stringResource(R.string.workouts_description)) {
                    Body(detail.summary)
                }
            }
        }

        if (detail.startingPosition.isNotBlank()) {
            item {
                Section(stringResource(R.string.exercise_section_starting_position)) {
                    Body(detail.startingPosition)
                }
            }
        }

        if (detail.steps.isNotEmpty()) {
            item {
                Section(stringResource(R.string.exercise_section_steps)) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        detail.steps.forEachIndexed { index, step ->
                            NumberedStep(index + 1, step)
                        }
                    }
                }
            }
        }

        if (detail.tips.isNotEmpty()) {
            item {
                Section(stringResource(R.string.exercise_section_tips)) {
                    BulletList(detail.tips, RepsGreen)
                }
            }
        }

        if (detail.notes.isNotEmpty()) {
            item {
                Section(stringResource(R.string.exercise_section_notes)) {
                    BulletList(detail.notes, RepsTheme.colors.textTertiary)
                }
            }
        }

        if (detail.aliases.isNotEmpty()) {
            item {
                Section(stringResource(R.string.exercise_section_aliases)) {
                    Body(detail.aliases.joinToString(" · "))
                }
            }
        }

        if (!detail.hasInstructions && detail.steps.isEmpty()) {
            item {
                Text(
                    text = stringResource(R.string.exercise_no_instructions),
                    style = MaterialTheme.typography.bodySmall,
                    color = RepsTheme.colors.textTertiary,
                )
            }
        }

        if (detail.licenseName.isNotBlank()) {
            item {
                Text(
                    text = stringResource(R.string.exercise_license, detail.licenseName),
                    style = MaterialTheme.typography.labelSmall,
                    color = RepsTheme.colors.textTertiary,
                )
            }
        }
    }
}

/**
 * The demonstration block: the muscle map the catalogue draws for this exercise,
 * and its remote demonstration photo. Muscles lead because they are local and
 * always present; the photo may be missing or offline.
 */
@Composable
private fun ExerciseMediaTabs(detail: ExerciseDetail, diagram: MuscleDiagram) {
    var selected by rememberSaveable(detail.id) { mutableIntStateOf(0) }
    Column {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MediaTab(
                label = stringResource(R.string.exercise_tab_muscles),
                selected = selected == 0,
                modifier = Modifier.weight(1f),
            ) { selected = 0 }
            MediaTab(
                label = stringResource(R.string.exercise_tab_demo),
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
                0 -> MuscleTargetDiagram(
                    frontPrimary = diagram.frontPrimary,
                    frontSecondary = diagram.frontSecondary,
                    backPrimary = diagram.backPrimary,
                    backSecondary = diagram.backSecondary,
                    frontBodyAsset = diagram.frontBodyAsset,
                    backBodyAsset = diagram.backBodyAsset,
                    contentDescription = detail.primaryMuscles
                        .takeIf { it.isNotEmpty() }
                        ?.joinToString(", ") { it.displayName }
                        ?.let { stringResource(R.string.muscle_map_cd, it) },
                )
                else -> ExerciseMediaCard(
                    mediaUrl = detail.exercise.mediaUrl,
                    contentDescription = stringResource(
                        R.string.exercise_media_cd,
                        detail.name,
                    ),
                )
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
private fun Section(title: String, content: @Composable () -> Unit) {
    Column {
        Text(
            text = title.uppercase(),
            style = RepsTheme.textStyles.eyebrow,
            color = RepsGreen,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        content()
    }
}

@Composable
private fun Body(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = RepsTheme.colors.textSecondary,
    )
}

@Composable
private fun NumberedStep(number: Int, text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(
            Modifier
                .size(22.dp)
                .background(RepsGreen, PillShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "$number",
                style = MaterialTheme.typography.labelSmall,
                color = RepsOnGreen,
            )
        }
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = RepsTheme.colors.textSecondary,
        )
    }
}

@Composable
private fun BulletList(items: List<String>, bulletColor: androidx.compose.ui.graphics.Color) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items.forEach { item ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("•", style = MaterialTheme.typography.bodyMedium, color = bulletColor)
                Text(
                    text = item,
                    style = MaterialTheme.typography.bodySmall,
                    color = RepsTheme.colors.textSecondary,
                )
            }
        }
    }
}

/** Primary muscles carry the brand fill; secondary ones only an outline. */
@Composable
private fun MuscleTag(muscle: MuscleTarget, primary: Boolean) {
    Box(
        Modifier
            .background(if (primary) RepsGreen else RepsTheme.colors.surface, PillShape)
            .border(
                1.dp,
                if (primary) RepsGreen else RepsTheme.colors.outline,
                PillShape,
            )
            .padding(horizontal = 12.dp, vertical = 7.dp),
    ) {
        Text(
            text = muscle.displayName,
            style = MaterialTheme.typography.labelSmall,
            color = if (primary) RepsOnGreen else RepsTheme.colors.textSecondary,
        )
    }
}

/** A plain, non-interactive descriptive pill - muscle group / equipment. */
@Composable
private fun InfoTag(text: String) {
    Box(
        Modifier
            .background(RepsTheme.colors.surface, PillShape)
            .border(1.dp, RepsTheme.colors.outline, PillShape)
            .padding(horizontal = 12.dp, vertical = 7.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = RepsTheme.colors.textSecondary,
        )
    }
}

@Composable
private fun LoadingState() {
    Box(
        Modifier.fillMaxSize().background(RepsTheme.colors.background),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(color = RepsGreen, strokeWidth = 2.dp, modifier = Modifier.size(28.dp))
    }
}

@Composable
private fun ExerciseUnavailable(onBack: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .background(RepsTheme.colors.background)
            .statusBarsPadding()
            .padding(RepsTheme.dimens.screenPadding),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        RepsBackButton(onClick = onBack)
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.width(260.dp),
            ) {
                Text(
                    text = stringResource(R.string.exercise_not_found_title),
                    style = MaterialTheme.typography.titleSmall,
                    color = RepsTheme.colors.textPrimary,
                )
                Text(
                    text = stringResource(R.string.exercise_not_found_body),
                    style = MaterialTheme.typography.bodySmall,
                    color = RepsTheme.colors.textSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }
    }
}
