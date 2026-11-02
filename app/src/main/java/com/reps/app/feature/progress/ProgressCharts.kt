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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
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
import com.reps.app.domain.progress.AchievementId
import com.reps.app.domain.progress.AchievementProgress
import com.reps.app.domain.progress.RecordEvent
import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import kotlin.math.roundToInt

@Composable
internal fun ChartHeader(eyebrow: String, title: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(eyebrow.uppercase(), style = RepsTheme.textStyles.eyebrow, color = RepsGreen)
        Text(title, style = MaterialTheme.typography.titleMedium, color = RepsTheme.colors.textPrimary, modifier = Modifier.padding(top = 2.dp))
    }
}

/** Range chips filter by calendar days, which is what "30D" says - not by the last N entries. */
private val WeightRangesDays = listOf(30, 90, 365)

@Composable
internal fun WeightChartCard(state: ProgressUiState, onAddWeight: () -> Unit) {
    var rangeDays by remember { mutableIntStateOf(90) }
    val entries = remember(state.weightEntries, rangeDays, state.today) {
        val from = state.today.minusDays(rangeDays.toLong())
        state.weightEntries.filter { !it.date.isBefore(from) }.sortedBy { it.date }
    }
    val unitLabel = weightUnitLabel(state.units)

    Column(Modifier.fillMaxWidth().background(RepsTheme.colors.surface, MaterialTheme.shapes.large).padding(RepsTheme.dimens.cardPadding)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
            ChartHeader(
                eyebrow = stringResource(R.string.progress_weight_progression),
                title = state.currentWeightKg?.let { "${UnitConverter.formatWeight(it, state.units)} $unitLabel" } ?: "—",
            )
            RepsChip(label = stringResource(R.string.progress_add_weight_short), selected = false, onClick = onAddWeight)
        }
        if (state.weightEntries.size >= 2) {
            Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                WeightRangesDays.forEach { days ->
                    RepsChip(label = stringResource(R.string.progress_range_days, days), selected = rangeDays == days, onClick = { rangeDays = days })
                }
            }
        }
        if (entries.size >= 2) {
            RepsLineChart(
                values = entries.map { UnitConverter.displayWeight(it.weightKg, state.units).toFloat() },
                modifier = Modifier.padding(top = 10.dp),
                valueFormatter = { "${it.roundToInt()} $unitLabel" },
            )
        } else {
            Text(
                text = stringResource(if (state.weightEntries.size >= 2) R.string.progress_weight_range_empty else R.string.progress_chart_empty),
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
    val unitLabel = weightUnitLabel(state.units)
    val repsLabel = stringResource(R.string.workouts_reps)

    Column(Modifier.fillMaxWidth().background(RepsTheme.colors.surface, MaterialTheme.shapes.large).padding(RepsTheme.dimens.cardPadding)) {
        ChartHeader(
            eyebrow = stringResource(R.string.progress_strength_progression),
            title = exercise?.name.orEmpty(),
        )
        Text(
            text = stringResource(if (series.byReps) R.string.progress_strength_by_reps else R.string.progress_strength_by_weight),
            style = MaterialTheme.typography.labelSmall,
            color = RepsTheme.colors.textSecondary,
            modifier = Modifier.padding(top = 4.dp),
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
        if (series.points.size >= 2) {
            RepsLineChart(
                values = series.points.map {
                    if (series.byReps) it.value.toFloat() else UnitConverter.displayWeight(it.value, state.units).toFloat()
                },
                valueFormatter = { if (series.byReps) "${it.roundToInt()} $repsLabel" else "${it.roundToInt()} $unitLabel" },
            )
        } else {
            Text(
                text = stringResource(R.string.progress_strength_empty),
                style = MaterialTheme.typography.bodySmall,
                color = RepsTheme.colors.textTertiary,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }
    }
}

@Composable
internal fun FrequencyCard(sessions: List<WorkoutSession>, today: LocalDate) {
    val buckets = remember(sessions, today) { weeklyFrequency(sessions, today = today) }
    val max = (buckets.maxOfOrNull { it.count } ?: 0).coerceAtLeast(1)
    val formatter = remember(currentLocale()) { DateTimeFormatter.ofPattern("d/M") }

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
                val label = bucket.weekStart.format(formatter)
                val cd = stringResource(R.string.progress_frequency_bar_cd, label, bucket.count)
                Column(
                    Modifier.weight(1f).semantics(mergeDescendants = true) { contentDescription = cd },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = bucket.count.takeIf { it > 0 }?.toString().orEmpty(),
                        style = MaterialTheme.typography.labelSmall,
                        color = RepsTheme.colors.textSecondary,
                    )
                    Box(Modifier.fillMaxWidth().height(48.dp), contentAlignment = Alignment.BottomCenter) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height((48.dp * animated).coerceAtLeast(3.dp))
                                .background(RepsGreen, RoundedCornerShape(3.dp)),
                        )
                    }
                    Text(
                        text = label,
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
                        modifier = Modifier.width(72.dp),
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
                        text = stringResource(R.string.progress_percent, share.pct),
                        style = MaterialTheme.typography.labelMedium,
                        color = RepsTheme.colors.textPrimary,
                        modifier = Modifier.width(40.dp),
                        textAlign = TextAlign.End,
                    )
                }
            }
        }
    }
}

/**
 * Standing best per exercise. A best that has beaten an earlier one is labelled
 * a record with what it beat by; a first-ever set is labelled as a first best.
 */
@Composable
internal fun PersonalRecordsSection(records: List<RecordEvent>, exercisesById: Map<String, Exercise>, units: UnitSystem) {
    val unitLabel = weightUnitLabel(units)
    Column {
        Text(
            text = stringResource(R.string.progress_personal_records),
            style = MaterialTheme.typography.titleSmall,
            color = RepsTheme.colors.textPrimary,
            modifier = Modifier.padding(bottom = 10.dp),
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(records, key = { it.exerciseId }) { record ->
                val exercise = exercisesById[record.exerciseId] ?: return@items
                Column(
                    Modifier
                        .width(168.dp)
                        .background(RepsTheme.colors.surface, MaterialTheme.shapes.medium)
                        .padding(14.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(
                            Icons.Outlined.EmojiEvents,
                            contentDescription = null,
                            tint = if (record.isImprovement) RepsAchievement else RepsTheme.colors.textTertiary,
                            modifier = Modifier.size(12.dp),
                        )
                        Text(
                            text = stringResource(if (record.isImprovement) R.string.workouts_new_pr else R.string.progress_first_best).uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (record.isImprovement) RepsAchievement else RepsTheme.colors.textTertiary,
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
                        text = if (record.isBodyweight) {
                            stringResource(R.string.progress_record_bodyweight, record.set.reps)
                        } else {
                            stringResource(R.string.progress_record_weighted, UnitConverter.formatWeight(record.set.weightKg, units), unitLabel, record.set.reps)
                        },
                        style = MaterialTheme.typography.titleSmall,
                        color = RepsTheme.colors.textPrimary,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                    Text(
                        text = when {
                            record.weightGainKg != null -> stringResource(
                                R.string.progress_pr_beat_weight,
                                UnitConverter.formatWeight(record.weightGainKg!!, units),
                                unitLabel,
                            )
                            record.repGain != null -> stringResource(R.string.progress_pr_beat_reps, record.repGain!!)
                            record.isImprovement -> stringResource(R.string.progress_pr_beat_generic)
                            else -> stringResource(R.string.progress_pr_first)
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = if (record.isImprovement) RepsWeightDown else RepsTheme.colors.textTertiary,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }
    }
}

/**
 * Every achievement with its rule, live progress, and - once earned - the date
 * it was first unlocked.
 */
@Composable
internal fun AchievementsSection(achievements: List<AchievementProgress>, unlockedAt: Map<AchievementId, Long>) {
    val locale = currentLocale()
    val dateFormat = remember(locale) { DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale) }
    val numbers = remember(locale) { NumberFormat.getIntegerInstance(Locale.US) }
    val unlockedCount = achievements.count { it.unlocked }
    Column {
        Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                text = stringResource(R.string.progress_achievements),
                style = MaterialTheme.typography.titleSmall,
                color = RepsTheme.colors.textPrimary,
            )
            Text(
                text = stringResource(R.string.progress_achievements_count, unlockedCount, achievements.size),
                style = MaterialTheme.typography.labelMedium,
                color = RepsTheme.colors.textSecondary,
            )
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(achievements, key = { it.id }) { achievement ->
                val meta = achievement.id.presentation()
                val title = stringResource(meta.titleRes)
                val description = stringResource(meta.descriptionRes)
                val footer = unlockedAt[achievement.id]?.let { ms ->
                    stringResource(
                        R.string.ach_unlocked_on,
                        Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).toLocalDate().format(dateFormat),
                    )
                } ?: stringResource(
                    R.string.ach_progress,
                    numbers.format(achievement.current.coerceAtMost(achievement.id.target)),
                    numbers.format(achievement.id.target),
                )
                Column(
                    Modifier
                        .width(148.dp)
                        .background(RepsTheme.colors.surface, MaterialTheme.shapes.medium)
                        .padding(14.dp)
                        .semantics(mergeDescendants = true) { contentDescription = "$title. $description. $footer" },
                ) {
                    Box(
                        Modifier
                            .size(44.dp)
                            .background(RepsTheme.colors.surfaceElevated, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = meta.icon,
                            contentDescription = null,
                            tint = if (achievement.unlocked) RepsAchievement else RepsTheme.colors.textTertiary.copy(alpha = 0.6f),
                            modifier = Modifier.size(22.dp),
                        )
                        if (!achievement.unlocked) {
                            Icon(
                                Icons.Filled.Lock,
                                contentDescription = null,
                                tint = RepsTheme.colors.textTertiary,
                                modifier = Modifier.align(Alignment.BottomEnd).size(12.dp),
                            )
                        }
                    }
                    Text(
                        text = title,
                        style = MaterialTheme.typography.labelLarge,
                        color = if (achievement.unlocked) RepsTheme.colors.textPrimary else RepsTheme.colors.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 10.dp),
                    )
                    Text(
                        text = description,
                        style = MaterialTheme.typography.labelSmall,
                        color = RepsTheme.colors.textTertiary,
                        maxLines = 3,
                        minLines = 3,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                    if (!achievement.unlocked) {
                        Box(
                            Modifier
                                .padding(top = 8.dp)
                                .fillMaxWidth()
                                .height(4.dp)
                                .background(RepsTheme.colors.surfaceElevated, RoundedCornerShape(2.dp)),
                        ) {
                            Box(
                                Modifier
                                    .fillMaxWidth(achievement.fraction)
                                    .height(4.dp)
                                    .background(RepsGreen, RoundedCornerShape(2.dp)),
                            )
                        }
                    }
                    Text(
                        text = footer,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (achievement.unlocked) RepsAchievement else RepsTheme.colors.textSecondary,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }
        }
    }
}
