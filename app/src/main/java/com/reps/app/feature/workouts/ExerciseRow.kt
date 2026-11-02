package com.reps.app.feature.workouts

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.reps.app.R
import com.reps.app.core.theme.RepsError
import com.reps.app.core.theme.RepsGreen
import com.reps.app.core.theme.RepsTheme
import com.reps.app.domain.model.Exercise
import com.reps.app.domain.model.Workout
import java.time.DayOfWeek

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
                .clip(MaterialTheme.shapes.small)
                .background(RepsTheme.colors.surfaceElevated),
            contentAlignment = Alignment.Center,
        ) {
            // The dumbbell glyph is the resting state, not a spinner: two thirds
            // of the catalogue has no photo, and a row that flickers through a
            // loading state on every scroll reads worse than one that simply
            // shows the icon until a thumbnail arrives.
            Icon(
                painter = painterResource(R.drawable.ic_workouts),
                contentDescription = null,
                tint = RepsGreen,
                modifier = Modifier.size(18.dp),
            )
            if (exercise.thumbnailUrl.isNotBlank()) {
                AsyncImage(
                    model = exercise.thumbnailUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        Column(Modifier.weight(1f)) {
            Text(
                text = exercise.name,
                style = MaterialTheme.typography.bodyLarge,
                color = RepsTheme.colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                // 181 catalogue exercises record no equipment, so the separator
                // is only drawn when there is something on the other side of it.
                text = listOf(
                    stringResource(exercise.muscleGroup.labelRes),
                    exercise.equipment,
                ).filter { it.isNotBlank() }.joinToString(" · "),
                style = MaterialTheme.typography.labelSmall,
                color = RepsTheme.colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 1.dp),
            )
        }
        // Difficulty is null for everything in the catalogue - it records none -
        // so the trailing slot stays empty rather than claiming a level.
        val difficulty = exercise.difficulty
        if (selected == null) {
            if (difficulty != null) {
                Text(
                    text = stringResource(difficulty.labelRes),
                    style = MaterialTheme.typography.labelSmall,
                    color = RepsTheme.colors.textSecondary,
                )
            }
        } else {
            Box(
                Modifier
                    .size(22.dp)
                    .background(if (selected) RepsGreen else androidx.compose.ui.graphics.Color.Transparent, CircleShape)
                    .border(1.dp, if (selected) RepsGreen else RepsTheme.colors.outline, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                if (selected) {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = null,
                        tint = RepsTheme.colors.background,
                        modifier = Modifier.size(14.dp),
                    )
                }
            }
        }
    }
}

/**
 * Horizontally-scrolled card for a saved workout template. Tapping starts it;
 * the overflow menu edits, duplicates or deletes it.
 *
 * The eyebrow shows the real schedule. It used to show a difficulty, but every
 * template was saved as "Intermediate" - a label nobody chose.
 */
@Composable
fun MiniWorkoutCard(
    workout: Workout,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Column(
        modifier
            .width(176.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .background(RepsTheme.colors.surface, MaterialTheme.shapes.medium)
            .padding(start = 14.dp, top = 6.dp, bottom = 14.dp, end = 4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = scheduleLabel(workout.scheduledDays).uppercase(),
                style = RepsTheme.textStyles.eyebrow,
                color = if (workout.scheduledDays.isEmpty()) RepsTheme.colors.textTertiary else RepsGreen,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(
                        Icons.Filled.MoreVert,
                        contentDescription = stringResource(R.string.workouts_template_options_cd, workout.name),
                        tint = RepsTheme.colors.textTertiary,
                    )
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(text = { Text(stringResource(R.string.workouts_template_start)) }, onClick = { menuOpen = false; onClick() })
                    DropdownMenuItem(text = { Text(stringResource(R.string.workouts_template_edit)) }, onClick = { menuOpen = false; onEdit() })
                    DropdownMenuItem(text = { Text(stringResource(R.string.workouts_template_duplicate)) }, onClick = { menuOpen = false; onDuplicate() })
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.workouts_template_delete), color = RepsError) },
                        onClick = { menuOpen = false; onDelete() },
                    )
                }
            }
        }
        Text(
            text = workout.name,
            style = MaterialTheme.typography.titleSmall,
            color = RepsTheme.colors.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(end = 10.dp),
        )
        Text(
            text = pluralStringResource(R.plurals.exercise_count, workout.exercises.size, workout.exercises.size) +
                " · " + stringResource(R.string.workouts_estimated_min, workout.estimatedMinutes),
            style = MaterialTheme.typography.labelSmall,
            color = RepsTheme.colors.textSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 4.dp, end = 10.dp),
        )
    }
}

/** "Mon · Thu" in the app's language, or "Not scheduled". */
@Composable
fun scheduleLabel(days: Set<DayOfWeek>): String {
    if (days.isEmpty()) return stringResource(R.string.workouts_not_scheduled)
    val locale = LocalConfiguration.current.locales[0]
    return days.sorted().joinToString(" · ") { it.getDisplayName(java.time.format.TextStyle.SHORT, locale) }
}
