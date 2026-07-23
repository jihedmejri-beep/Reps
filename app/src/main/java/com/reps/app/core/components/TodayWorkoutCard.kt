package com.reps.app.core.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.unit.dp
import com.reps.app.R
import com.reps.app.core.theme.RepsGreen
import com.reps.app.core.theme.RepsTheme
import com.reps.app.domain.model.Difficulty

/**
 * The headline card on Home: what is scheduled today and a way straight into it.
 */
@Composable
fun TodayWorkoutCard(
    workoutName: String,
    muscleGroups: String,
    exerciseCount: Int,
    durationMin: Int,
    difficulty: Difficulty,
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
