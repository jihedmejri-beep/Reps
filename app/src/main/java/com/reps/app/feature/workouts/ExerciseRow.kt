package com.reps.app.feature.workouts

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.reps.app.R
import com.reps.app.core.theme.RepsGreen
import com.reps.app.core.theme.RepsNearBlack
import com.reps.app.core.theme.RepsOutline
import com.reps.app.core.theme.RepsSurface
import com.reps.app.core.theme.RepsSurfaceElevated
import com.reps.app.core.theme.RepsTextPrimary
import com.reps.app.core.theme.RepsTextSecondary
import com.reps.app.domain.model.Exercise
import com.reps.app.domain.model.Workout

/**
 * One row in the exercise library, shared by the Workouts tab and the
 * builder's exercise picker sheet. [selected] switches the trailing content
 * from the difficulty label to a pick/unpick indicator; leave it null to read
 * as a plain library row.
 */
@Composable
fun ExerciseRow(
    exercise: Exercise,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean? = null,
) {
    Row(
        modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier
                .size(40.dp)
                .background(RepsSurfaceElevated, MaterialTheme.shapes.small),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_workouts),
                contentDescription = null,
                tint = RepsGreen,
                modifier = Modifier.size(18.dp),
            )
        }
        Column(Modifier.weight(1f)) {
            Text(
                text = exercise.name,
                style = MaterialTheme.typography.bodyLarge,
                color = RepsTextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "${stringResource(exercise.muscleGroup.labelRes)} · ${exercise.equipment}",
                style = MaterialTheme.typography.labelSmall,
                color = RepsTextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 1.dp),
            )
        }
        if (selected == null) {
            Text(
                text = stringResource(exercise.difficulty.labelRes),
                style = MaterialTheme.typography.labelSmall,
                color = RepsTextSecondary,
            )
        } else {
            Box(
                Modifier
                    .size(22.dp)
                    .background(if (selected) RepsGreen else androidx.compose.ui.graphics.Color.Transparent, CircleShape)
                    .border(1.dp, if (selected) RepsGreen else RepsOutline, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                if (selected) {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = null,
                        tint = RepsNearBlack,
                        modifier = Modifier.size(14.dp),
                    )
                }
            }
        }
    }
}

/** Horizontally-scrolled card for a saved workout template; tapping starts it. */
@Composable
fun MiniWorkoutCard(
    workout: Workout,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .width(160.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .background(RepsSurface, MaterialTheme.shapes.medium)
            .padding(14.dp),
    ) {
        Text(
            text = stringResource(workout.difficulty.labelRes).uppercase(),
            style = com.reps.app.core.theme.RepsTheme.textStyles.eyebrow,
            color = RepsGreen,
        )
        Text(
            text = workout.name,
            style = MaterialTheme.typography.titleSmall,
            color = RepsTextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 4.dp),
        )
        Text(
            text = stringResource(R.string.home_exercise_count, workout.exercises.size) +
                " · " + stringResource(R.string.home_duration_min, workout.estimatedMinutes),
            style = MaterialTheme.typography.labelSmall,
            color = RepsTextSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}
