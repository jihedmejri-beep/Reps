package com.reps.app.feature.progress

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Whatshot
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.reps.app.R
import com.reps.app.core.components.RepsChip
import com.reps.app.core.components.RepsLineChart
import com.reps.app.core.theme.RepsAchievement
import com.reps.app.core.theme.RepsGreen
import com.reps.app.core.theme.RepsTheme
import com.reps.app.core.theme.RepsWeightDown
import com.reps.app.core.util.UnitConverter
import com.reps.app.domain.model.Exercise
import com.reps.app.domain.model.UnitSystem
import com.reps.app.domain.model.WorkoutSession
import kotlin.math.roundToInt

@Composable
internal fun ChartHeader(eyebrow: String, title: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(eyebrow.uppercase(), style = RepsTheme.textStyles.eyebrow, color = RepsGreen)
        Text(title, style = MaterialTheme.typography.titleMedium, color = RepsTheme.colors.textPrimary, modifier = Modifier.padding(top = 2.dp))
    }
}

@Composable
internal fun WeightChartCard(state: ProgressUiState, onAddWeight: () -> Unit) {
    var rangeDays by remember { mutableStateOf(60) }
    val entries = remember(state.weightEntries, rangeDays) {
        state.weightEntries.sortedBy { it.date }.takeLast(rangeDays)
    }
    val unitLabel = stringResource(if (state.units == UnitSystem.METRIC) R.string.workouts_kg else R.string.workouts_lb)

    Column(Modifier.fillMaxWidth().background(RepsTheme.colors.surface, MaterialTheme.shapes.large).padding(RepsTheme.dimens.cardPadding)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
            ChartHeader(
                eyebrow = stringResource(R.string.progress_weight_progression),
                title = state.currentWeightKg?.let { "${UnitConverter.formatWeight(it, state.units)} $unitLabel" } ?: "—",
            )
            RepsChip(label = "+", selected = false, onClick = onAddWeight)
        }
        if (entries.size >= 2) {
            Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(30, 60, 90).forEach { days ->
                    RepsChip(label = "${days}D", selected = rangeDays == days, onClick = { rangeDays = days })
                }
            }
            RepsLineChart(
                values = entries.map { UnitConverter.displayWeight(it.weightKg, state.units).toFloat() },
                modifier = Modifier.padding(top = 10.dp),
                valueFormatter = { "${it.roundToInt()} $unitLabel" },
            )
        } else {
            Text(
                text = stringResource(R.string.progress_chart_empty),
                style = MaterialTheme.typography.bodySmall,
                color = RepsTheme.colors.textTertiary,
                modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
            )
        }
    }
}

@Composable
internal fun StrengthChartCard(state: ProgressUiState, exerciseIds: List<String>) {
    var selected by remember(exerciseIds) { mutableStateOf(exerciseIds.first()) }
    val exercise = state.exercisesById[selected]
    val series = remember(state.sessions, selected) { strengthSeries(state.sessions, selected) }
    val unitLabel = stringResource(if (state.units == UnitSystem.METRIC) R.string.workouts_kg else R.string.workouts_lb)

    Column(Modifier.fillMaxWidth().background(RepsTheme.colors.surface, MaterialTheme.shapes.large).padding(RepsTheme.dimens.cardPadding)) {
        ChartHeader(
            eyebrow = stringResource(R.string.progress_strength_progression),
            title = exercise?.name.orEmpty(),
        )
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(vertical = 10.dp),
        ) {
            items(exerciseIds, key = { it }) { id ->
                RepsChip(
                    label = state.exercisesById[id]?.name.orEmpty(),
                    selected = id == selected,
                    onClick = { selected = id },
                )
            }
        }
        if (series.size >= 2) {
            RepsLineChart(
                values = series.map { UnitConverter.displayWeight(it.weightKg, state.units).toFloat() },
                valueFormatter = { "${it.roundToInt()} $unitLabel" },
            )
        } else {
            Text(
                text = stringResource(R.string.progress_chart_empty),
                style = MaterialTheme.typography.bodySmall,
                color = RepsTheme.colors.textTertiary,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }
    }
}

@Composable
internal fun FrequencyCard(sessions: List<WorkoutSession>) {
    val buckets = remember(sessions) { weeklyFrequency(sessions) }
    val max = (buckets.maxOfOrNull { it.count } ?: 0).coerceAtLeast(1)

    Column(Modifier.fillMaxWidth().background(RepsTheme.colors.surface, MaterialTheme.shapes.large).padding(RepsTheme.dimens.cardPadding)) {
        ChartHeader(
            eyebrow = stringResource(R.string.progress_frequency),
            title = stringResource(R.string.progress_frequency_caption),
        )
        Row(
            Modifier.fillMaxWidth().padding(top = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            buckets.forEach { bucket ->
                val targetFraction = bucket.count / max.toFloat()
                var revealed by remember { mutableStateOf(false) }
                LaunchedEffect(bucket) { revealed = true }
                val animated by androidx.compose.animation.core.animateFloatAsState(
                    targetValue = if (revealed) targetFraction else 0f,
                    animationSpec = androidx.compose.animation.core.tween(700, easing = androidx.compose.animation.core.FastOutSlowInEasing),
                    label = "freqBar",
                )
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        contentAlignment = Alignment.BottomCenter,
                    ) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height((48.dp * animated).coerceAtLeast(3.dp))
                                .background(RepsGreen, RoundedCornerShape(3.dp)),
                        )
                    }
                    Text(
                        text = bucket.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = RepsTheme.colors.textSecondary,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }
    }
}

@Composable
internal fun MuscleDistributionCard(shares: List<MuscleShare>) {
    Column(Modifier.fillMaxWidth().background(RepsTheme.colors.surface, MaterialTheme.shapes.large).padding(RepsTheme.dimens.cardPadding)) {
        ChartHeader(
            eyebrow = stringResource(R.string.progress_muscle_distribution),
            title = stringResource(R.string.progress_muscle_distribution_caption),
        )
        Column(Modifier.padding(top = 14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            shares.forEach { share ->
                var revealed by remember { mutableStateOf(false) }
                LaunchedEffect(share) { revealed = true }
                val animated by androidx.compose.animation.core.animateFloatAsState(
                    targetValue = if (revealed) share.pct / 100f else 0f,
                    animationSpec = androidx.compose.animation.core.tween(800, easing = androidx.compose.animation.core.FastOutSlowInEasing),
                    label = "muscleBar",
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = stringResource(share.group.labelRes),
                        style = MaterialTheme.typography.labelMedium,
                        color = RepsTheme.colors.textSecondary,
                        modifier = Modifier.width(66.dp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Box(
                        Modifier
                            .weight(1f)
                            .height(8.dp)
                            .background(RepsTheme.colors.surfaceElevated, RoundedCornerShape(4.dp)),
                    ) {
                        Box(
                            Modifier
                                .fillMaxWidth(animated)
                                .height(8.dp)
                                .background(RepsGreen, RoundedCornerShape(4.dp)),
                        )
                    }
                    Text(
                        text = "${share.pct}%",
                        style = MaterialTheme.typography.labelMedium,
                        color = RepsTheme.colors.textPrimary,
                        modifier = Modifier.width(36.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.End,
                    )
                }
            }
        }
    }
}

@Composable
internal fun PersonalRecordsSection(prs: List<PersonalRecord>, exercisesById: Map<String, Exercise>) {
    Column {
        Text(
            text = stringResource(R.string.progress_personal_records),
            style = MaterialTheme.typography.titleSmall,
            color = RepsTheme.colors.textPrimary,
            modifier = Modifier.padding(bottom = 10.dp),
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(prs, key = { it.exerciseId }) { pr ->
                val exercise = exercisesById[pr.exerciseId] ?: return@items
                Column(
                    Modifier
                        .width(160.dp)
                        .background(RepsTheme.colors.surface, MaterialTheme.shapes.medium)
                        .padding(14.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(Icons.Outlined.EmojiEvents, contentDescription = null, tint = RepsAchievement, modifier = Modifier.size(12.dp))
                        Text(
                            text = stringResource(R.string.workouts_new_pr).uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            color = RepsAchievement,
                        )
                    }
                    Text(
                        text = exercise.name,
                        style = MaterialTheme.typography.bodyMedium,
                        color = RepsTheme.colors.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    Text(
                        text = "${pr.weightKg.roundToInt()} kg × ${pr.reps}",
                        style = MaterialTheme.typography.titleSmall,
                        color = RepsTheme.colors.textPrimary,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                    Text(
                        text = pr.deltaKg?.let { stringResource(R.string.progress_pr_beat, it.roundToInt().toString()) }
                            ?: stringResource(R.string.progress_pr_first),
                        style = MaterialTheme.typography.labelSmall,
                        color = RepsWeightDown,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }
    }
}

@Composable
internal fun AchievementsSection(sessionCount: Int, streakCount: Int, prCount: Int) {
    val achievements = remember(sessionCount, streakCount, prCount) {
        computeAchievements(sessionCount, streakCount, prCount)
    }
    Column {
        Text(
            text = stringResource(R.string.progress_achievements),
            style = MaterialTheme.typography.titleSmall,
            color = RepsTheme.colors.textPrimary,
            modifier = Modifier.padding(bottom = 10.dp),
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(achievements, key = { it.id }) { achievement ->
                val (icon, titleRes) = achievementMeta(achievement.id)
                Column(Modifier.width(84.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(84.dp)
                            .background(RepsTheme.colors.surfaceElevated, MaterialTheme.shapes.medium),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = if (achievement.unlocked) RepsAchievement else RepsTheme.colors.textTertiary.copy(alpha = 0.6f),
                            modifier = Modifier.size(28.dp),
                        )
                        if (!achievement.unlocked) {
                            Box(
                                Modifier
                                    .align(Alignment.BottomEnd)
                                    .size(20.dp)
                                    .background(RepsTheme.colors.surface, CircleShape)
                                    .padding(4.dp),
                            ) {
                                Icon(Icons.Filled.Lock, contentDescription = null, tint = RepsTheme.colors.textTertiary, modifier = Modifier.size(10.dp))
                            }
                        }
                    }
                    Text(
                        text = stringResource(titleRes),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (achievement.unlocked) RepsTheme.colors.textPrimary else RepsTheme.colors.textTertiary,
                        maxLines = 2,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        }
    }
}

private fun achievementMeta(id: AchievementId) = when (id) {
    AchievementId.FIRST_WORKOUT -> Icons.Outlined.FitnessCenter to R.string.ach_first_workout_title
    AchievementId.STREAK_7 -> Icons.Outlined.Whatshot to R.string.ach_streak7_title
    AchievementId.TEN_WORKOUTS -> Icons.Outlined.Schedule to R.string.ach_ten_workouts_title
    AchievementId.FIRST_PR -> Icons.Outlined.EmojiEvents to R.string.ach_first_pr_title
}


