package com.reps.app.feature.progress

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.TrendingUp
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.reps.app.R
import com.reps.app.core.components.DeltaDirection
import com.reps.app.core.components.RepsChip
import com.reps.app.core.components.RepsListRow
import com.reps.app.core.components.SectionHeader
import com.reps.app.core.components.StatCard
import com.reps.app.core.theme.RepsCaution
import com.reps.app.core.theme.RepsGreen
import com.reps.app.core.theme.RepsTheme
import com.reps.app.core.util.UnitConverter
import com.reps.app.domain.model.UnitSystem
import com.reps.app.navigation.OnTabReselected
import com.reps.app.navigation.Routes
import com.reps.app.navigation.navBarClearance
import java.time.DayOfWeek
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun ProgressScreen(
    onOpenHistory: () -> Unit,
    viewModel: ProgressViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    var showAddWeight by remember { mutableStateOf(false) }

    OnTabReselected(Routes.PROGRESS) { listState.animateScrollToItem(0) }

    val strengthExerciseIds = remember(state.sessions) {
        state.sessions.flatMap { it.exercises }.filter { we -> we.sets.any { it.completed } }.map { it.exerciseId }.distinct()
    }
    val muscleShares = remember(state.sessions, state.exercisesById) {
        muscleDistribution(state.sessions, state.exercisesById)
    }

    val dimens = RepsTheme.dimens
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().background(RepsTheme.colors.background).statusBarsPadding(),
        contentPadding = PaddingValues(
            start = dimens.screenPadding,
            end = dimens.screenPadding,
            bottom = navBarClearance(),
        ),
        verticalArrangement = Arrangement.spacedBy(dimens.sectionGap),
    ) {
        item {
            SectionHeader(
                eyebrow = stringResource(R.string.progress_eyebrow),
                title = stringResource(R.string.progress_title),
                modifier = Modifier.padding(top = 8.dp),
            )
        }

        item { HeroCard(state) }
        item { AnalyticsSection(state) }
        state.totals?.takeIf { it.workouts > 0 }?.let { totals -> item { LifetimeCard(totals, state.units) } }
        item {
            Box(
                Modifier
                    .fillMaxWidth()
                    .background(RepsTheme.colors.surface, MaterialTheme.shapes.large)
                    .padding(horizontal = dimens.cardPadding),
            ) {
                RepsListRow(
                    label = stringResource(R.string.history_title),
                    icon = Icons.Outlined.History,
                    value = pluralStringResource(R.plurals.history_count, state.sessions.size, state.sessions.size),
                    onClick = onOpenHistory,
                )
            }
        }
        item { WeightChartCard(state, onAddWeight = { showAddWeight = true }) }

        if (strengthExerciseIds.isNotEmpty()) {
            item { StrengthChartCard(state, strengthExerciseIds) }
        }

        item { FrequencyCard(state.sessions, state.today) }

        if (muscleShares.isNotEmpty()) {
            item { MuscleDistributionCard(muscleShares) }
        }

        if (state.records.isNotEmpty()) {
            item { PersonalRecordsSection(state.records, state.exercisesById, state.units) }
        }

        item { AchievementsSection(state.achievements, state.unlockedAt) }
    }

    if (showAddWeight) {
        AddWeightSheet(
            units = state.units,
            currentWeightKg = state.currentWeightKg,
            onDismiss = { showAddWeight = false },
            onSave = { kg ->
                viewModel.addWeight(kg)
                showAddWeight = false
            },
        )
    }
}

@Composable
internal fun currentLocale(): Locale = LocalConfiguration.current.locales[0]

@Composable
internal fun weightUnitLabel(units: UnitSystem): String =
    stringResource(if (units == UnitSystem.METRIC) R.string.workouts_kg else R.string.workouts_lb)

@Composable
private fun HeroCard(state: ProgressUiState) {
    val today = state.today
    val todaysSession = state.sessions.lastOrNull { it.date == today }
    val plannedWorkout = state.templates.firstOrNull { today.dayOfWeek in it.scheduledDays }
    val (completed, scheduled) = weeklyCompletion(state.sessions, state.templates, today)

    Column(
        Modifier
            .fillMaxWidth()
            .background(RepsTheme.colors.surface, MaterialTheme.shapes.large)
            .padding(RepsTheme.dimens.cardPadding),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            androidx.compose.material3.Icon(
                imageVector = Icons.AutoMirrored.Outlined.TrendingUp,
                contentDescription = null,
                tint = RepsGreen,
                modifier = Modifier.size(16.dp),
            )
            Text(
                text = when {
                    todaysSession != null -> stringResource(R.string.progress_hero_logged, todaysSession.name)
                    plannedWorkout != null -> stringResource(R.string.progress_hero_planned, plannedWorkout.name)
                    else -> stringResource(R.string.home_rest_day)
                },
                style = MaterialTheme.typography.titleSmall,
                color = RepsTheme.colors.textPrimary,
            )
        }
        Text(
            text = when {
                todaysSession != null -> stringResource(
                    R.string.progress_hero_logged_detail,
                    if (todaysSession.durationMeasured) {
                        pluralStringResource(R.plurals.duration_minutes, todaysSession.durationMin, todaysSession.durationMin)
                    } else {
                        stringResource(R.string.progress_duration_not_measured)
                    },
                    UnitConverter.formatWeight(todaysSession.completedSets.sumOf { it.volume }, state.units, decimals = 0),
                    weightUnitLabel(state.units),
                )
                plannedWorkout != null -> stringResource(R.string.progress_hero_planned_sub)
                else -> stringResource(R.string.progress_hero_rest_day_sub)
            },
            style = MaterialTheme.typography.bodySmall,
            color = RepsTheme.colors.textSecondary,
            modifier = Modifier.padding(top = 4.dp, bottom = 14.dp),
        )

        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Text(
                text = stringResource(R.string.progress_weekly_completion),
                style = MaterialTheme.typography.titleSmall,
                color = RepsTheme.colors.textPrimary,
            )
            Text(
                text = if (scheduled > 0) {
                    stringResource(R.string.progress_this_week, completed, scheduled)
                } else {
                    pluralStringResource(R.plurals.progress_sessions_this_week, completed, completed)
                },
                style = MaterialTheme.typography.labelMedium,
                color = RepsTheme.colors.textSecondary,
            )
        }
        WeekMeter(state = state, modifier = Modifier.padding(top = 10.dp))
    }
}

@Composable
private fun WeekMeter(state: ProgressUiState, modifier: Modifier = Modifier) {
    val today = state.today
    val weekStart = today.with(DayOfWeek.MONDAY)
    val planned = remember(state.templates) { state.templates.flatMap { it.scheduledDays }.toSet() }
    val locale = currentLocale()
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        repeat(7) { offset ->
            val day = weekStart.plusDays(offset.toLong())
            val done = state.sessions.any { it.date == day }
            val isToday = day == today
            val isPlanned = day.dayOfWeek in planned
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(28.dp)
                        .background(
                            when {
                                done -> RepsGreen
                                isPlanned -> RepsGreen.copy(alpha = if (isToday) 0.32f else 0.22f)
                                else -> RepsGreen.copy(alpha = if (isToday) 0.14f else 0.07f)
                            },
                            RoundedCornerShape(4.dp),
                        ),
                )
                Text(
                    text = day.dayOfWeek.getDisplayName(TextStyle.NARROW, locale),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isToday) RepsTheme.colors.textPrimary else RepsTheme.colors.textSecondary,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun AnalyticsSection(state: ProgressUiState) {
    var showLastMonth by remember { mutableStateOf(false) }
    val thisMonth = YearMonth.from(state.today)
    val month = if (showLastMonth) thisMonth.minusMonths(1) else thisMonth
    val previousMonth = month.minusMonths(1)
    val current = remember(state.sessions, month) { monthlyStats(state.sessions, month) }
    val previous = remember(state.sessions, previousMonth) { monthlyStats(state.sessions, previousMonth) }
    val unit = weightUnitLabel(state.units)

    Column {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.progress_eyebrow), style = MaterialTheme.typography.titleSmall, color = RepsTheme.colors.textPrimary)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RepsChip(
                    label = stringResource(R.string.progress_this_month),
                    selected = !showLastMonth,
                    onClick = { showLastMonth = false },
                )
                RepsChip(
                    label = stringResource(R.string.progress_last_month),
                    selected = showLastMonth,
                    onClick = { showLastMonth = true },
                    tintColor = RepsCaution,
                )
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            StatCard(
                icon = Icons.Outlined.LocalFireDepartment,
                label = stringResource(R.string.progress_workouts),
                value = current.workouts.toString(),
                deltaDirection = deltaDirection(current.workouts.toDouble(), previous.workouts.toDouble()),
                deltaText = deltaLabel(current.workouts.toDouble(), previous.workouts.toDouble()),
                modifier = Modifier.weight(1f),
            )
            StatCard(
                icon = Icons.Outlined.Schedule,
                label = stringResource(R.string.progress_duration),
                value = if (current.minutes > 0) String.format(Locale.US, "%.1f", current.minutes / 60f) else "—",
                unit = stringResource(R.string.progress_hours_unit),
                deltaText = current.unmeasuredWorkouts.takeIf { it > 0 }?.let {
                    pluralStringResource(R.plurals.progress_unmeasured_sessions, it, it)
                },
                modifier = Modifier.weight(1f),
            )
        }
        Row(
            Modifier.fillMaxWidth().padding(top = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            StatCard(
                icon = Icons.AutoMirrored.Outlined.TrendingUp,
                label = stringResource(R.string.progress_volume),
                value = UnitConverter.formatWeight(current.volumeKg, state.units, decimals = 0),
                unit = unit,
                deltaDirection = deltaDirection(current.volumeKg, previous.volumeKg),
                deltaText = deltaLabel(current.volumeKg, previous.volumeKg),
                modifier = Modifier.weight(1f),
            )
            StatCard(
                icon = Icons.Outlined.EmojiEvents,
                label = stringResource(R.string.progress_avg_per_workout),
                value = if (current.workouts > 0) {
                    UnitConverter.formatWeight(current.volumeKg / current.workouts, state.units, decimals = 0)
                } else {
                    "—"
                },
                unit = unit,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/** All-time totals, each a plain sum over completed sets. */
@Composable
private fun LifetimeCard(totals: com.reps.app.domain.progress.TrainingTotals, units: UnitSystem) {
    Column {
        Text(
            stringResource(R.string.progress_lifetime),
            style = MaterialTheme.typography.titleSmall,
            color = RepsTheme.colors.textPrimary,
            modifier = Modifier.padding(bottom = 12.dp),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatCard(
                icon = Icons.Outlined.LocalFireDepartment,
                label = stringResource(R.string.progress_workouts),
                value = totals.workouts.toString(),
                modifier = Modifier.weight(1f),
            )
            StatCard(
                icon = Icons.Outlined.Repeat,
                label = stringResource(R.string.progress_sets_reps),
                value = totals.completedSets.toString(),
                unit = "/ ${totals.reps}",
                modifier = Modifier.weight(1f),
            )
            StatCard(
                icon = Icons.AutoMirrored.Outlined.TrendingUp,
                label = stringResource(R.string.progress_volume),
                value = UnitConverter.formatWeight(totals.volumeKg, units, decimals = 0),
                unit = weightUnitLabel(units),
                modifier = Modifier.weight(1f),
            )
        }
    }
}

private fun deltaDirection(curr: Double, prev: Double): DeltaDirection = when {
    prev <= 0.0 || kotlin.math.abs(curr - prev) < 0.01 -> DeltaDirection.FLAT
    curr > prev -> DeltaDirection.UP
    else -> DeltaDirection.DOWN
}

@Composable
private fun deltaLabel(curr: Double, prev: Double): String? {
    if (prev <= 0.0) return null
    val pct = ((curr - prev) / prev * 100).roundToInt()
    if (pct == 0) return null
    val signed = "${if (pct > 0) "+" else ""}$pct"
    return stringResource(R.string.progress_delta_vs_last_month, signed)
}
