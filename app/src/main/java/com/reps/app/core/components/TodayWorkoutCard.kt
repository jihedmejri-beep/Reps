package com.reps.app.core.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.reps.app.R
import com.reps.app.core.theme.RepsGreen
import com.reps.app.core.theme.RepsTheme
import com.reps.app.domain.model.Difficulty
import com.reps.app.feature.home.TodayExercise

/** How many exercises the breakdown shows before collapsing the rest into "+N more". */
private const val MaxVisibleExercises = 5

/**
 * The headline card on Home: what is scheduled today, a breakdown of the work it
 * contains, and a way straight into it.
 */
@Composable
fun TodayWorkoutCard(
    workoutName: String,
    muscleGroups: String,
    exerciseCount: Int,
    setCount: Int,
    durationMin: Int,
    difficulty: Difficulty,
    exercises: List<TodayExercise>,
    onStart: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .fillMaxWidth()
            .background(RepsTheme.colors.surface, MaterialTheme.shapes.large)
            .background(
                // A faint green wash from the top-right lifts the card off the
                // page without resorting to a border.
                brush = Brush.radialGradient(
                    colors = listOf(RepsGreen.copy(alpha = 0.10f), androidx.compose.ui.graphics.Color.Transparent),
                    center = Offset(Float.POSITIVE_INFINITY, 0f),
                    radius = 620f,
                ),
                shape = MaterialTheme.shapes.large,
            ),
    ) {
        Column(Modifier.padding(RepsTheme.dimens.cardPadding)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.home_today_workout).uppercase(),
                        style = RepsTheme.textStyles.eyebrow,
                        color = RepsGreen,
                    )
                    Text(
                        text = workoutName.uppercase(),
                        style = RepsTheme.textStyles.sectionTitle,
                        color = RepsTheme.colors.textPrimary,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                    Text(
                        text = muscleGroups,
                        style = MaterialTheme.typography.bodyMedium,
                        color = RepsTheme.colors.textSecondary,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
                Icon(
                    painter = painterResource(R.drawable.ic_progress),
                    contentDescription = null,
                    tint = RepsGreen,
                    modifier = Modifier.size(30.dp),
                )
            }

            Row(
                Modifier.padding(top = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MetaChip(
                    painter = painterResource(R.drawable.ic_workouts),
                    text = stringResource(R.string.home_exercise_count, exerciseCount),
                )
                MetaChip(
                    imageVector = Icons.Outlined.Schedule,
                    text = stringResource(R.string.home_duration_min, durationMin),
                )
                MetaChip(
                    painter = painterResource(R.drawable.ic_progress),
                    text = stringResource(difficulty.labelRes),
                )
            }

            if (exercises.isNotEmpty()) {
                ExerciseBreakdown(
                    exercises = exercises,
                    setCount = setCount,
                    modifier = Modifier.padding(top = 16.dp),
                )
            }

            RepsButton(
                text = stringResource(R.string.home_start_workout),
                onClick = onStart,
                leadingIcon = rememberPlayIcon(),
                modifier = Modifier.padding(top = 16.dp),
            )
        }
    }
}

@Composable
private fun ExerciseBreakdown(
    exercises: List<TodayExercise>,
    setCount: Int,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth()) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(RepsTheme.colors.outline),
        )
        Row(
            Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.home_exercises_label).uppercase(),
                style = RepsTheme.textStyles.eyebrow,
                color = RepsTheme.colors.textSecondary,
            )
            Text(
                text = stringResource(R.string.home_set_count, setCount),
                style = MaterialTheme.typography.labelMedium,
                color = RepsTheme.colors.textSecondary,
            )
        }

        exercises.take(MaxVisibleExercises).forEachIndexed { index, exercise ->
            ExerciseRow(number = index + 1, exercise = exercise)
        }

        val hidden = exercises.size - MaxVisibleExercises
        if (hidden > 0) {
            Text(
                text = stringResource(R.string.home_more_exercises, hidden),
                style = MaterialTheme.typography.labelMedium,
                color = RepsGreen,
                modifier = Modifier.padding(top = 6.dp, start = 32.dp),
            )
        }
    }
}

@Composable
private fun ExerciseRow(number: Int, exercise: TodayExercise) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(22.dp)
                .background(RepsGreen.copy(alpha = 0.12f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = number.toString(),
                style = MaterialTheme.typography.labelSmall,
                color = RepsGreen,
            )
        }
        Text(
            text = exercise.name,
            style = MaterialTheme.typography.bodyMedium,
            color = RepsTheme.colors.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(start = 10.dp),
        )
        Text(
            text = stringResource(R.string.home_set_scheme, exercise.setCount, exercise.reps),
            style = MaterialTheme.typography.labelMedium,
            color = RepsTheme.colors.textSecondary,
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}

@Composable
private fun rememberPlayIcon() = androidx.compose.ui.graphics.vector.rememberVectorPainter(
    image = Icons.Filled.PlayArrow,
)

@Composable
private fun MetaChip(
    text: String,
    modifier: Modifier = Modifier,
    painter: androidx.compose.ui.graphics.painter.Painter? = null,
    imageVector: androidx.compose.ui.graphics.vector.ImageVector? = null,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        when {
            painter != null -> Icon(painter, null, tint = RepsGreen, modifier = Modifier.size(13.dp))
            imageVector != null -> Icon(imageVector, null, tint = RepsGreen, modifier = Modifier.size(13.dp))
        }
        Text(text, style = MaterialTheme.typography.labelMedium, color = RepsTheme.colors.textPrimary)
    }
}
