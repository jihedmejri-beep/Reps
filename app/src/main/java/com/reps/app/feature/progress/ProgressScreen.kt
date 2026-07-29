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
import androidx.compose.material.icons.outlined.LocalFireDepartment
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.reps.app.R
import com.reps.app.core.components.DeltaDirection
import com.reps.app.core.components.SectionHeader
import com.reps.app.core.components.StatCard
import com.reps.app.core.theme.RepsGreen
import com.reps.app.core.theme.RepsTheme
import com.reps.app.core.util.UnitConverter
import com.reps.app.navigation.OnTabReselected
import com.reps.app.navigation.Routes
import com.reps.app.navigation.navBarClearance
import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.roundToInt

@Composable
fun ProgressScreen(viewModel: ProgressViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    var showAddWeight by remember { mutableStateOf(false) }

    OnTabReselected(Routes.PROGRESS) { listState.animateScrollToItem(0) }

    val strengthExerciseIds = remember(state.sessions) {
        state.sessions.flatMap { it.exercises }.map { it.exerciseId }.distinct()
    }
    val muscleShares = remember(state.sessions, state.exercisesById) {
        muscleDistribution(state.sessions, state.exercisesById)
    }
    val prs = remember(state.sessions) { personalRecords(state.sessions) }

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
        item { WeightChartCard(state, onAddWeight = { showAddWeight = true }) }

        if (strengthExerciseIds.isNotEmpty()) {
            item { StrengthChartCard(state, strengthExerciseIds) }
        }

        item { FrequencyCard(state.sessions) }

        if (muscleShares.isNotEmpty()) {
            item { MuscleDistributionCard(muscleShares) }
        }

        if (prs.isNotEmpty()) {
            item { PersonalRecordsSection(prs, state.exercisesById) }
        }

        item { AchievementsSection(state.sessions.size, state.streakCount, prs.size) }

        if (state.sessions.isNotEmpty()) {
            item { RecentActivitySection(state.sessions, state.exercisesById) }
        }
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
private fun HeroCard(state: ProgressUiState) {
    val today = LocalDate.now()
    val todaysSession = state.sessions.firstOrNull { it.date == today }
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
                    R.string.progress_hero_logged_sub,
                    todaysSession.durationMin,
                    todaysSession.exercises.flatMap { it.sets }.filter { it.completed }
                        .sumOf { it.volume }.roundToInt().toString(),
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
                text = stringResource(R.string.progress_this_week, completed, scheduled),
                style = MaterialTheme.typography.labelMedium,
                color = RepsTheme.colors.textSecondary,
            )
        }
        WeekMeter(sessions = state.sessions, today = today, modifier = Modifier.padding(top = 10.dp))
    }
}

@Composable
private fun WeekMeter(sessions: List<com.reps.app.domain.model.WorkoutSession>, today: LocalDate, modifier: Modifier = Modifier) {
    val weekStart = today.with(java.time.DayOfWeek.MONDAY)
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        repeat(7) { offset ->
            val day = weekStart.plusDays(offset.toLong())
            val done = sessions.any { it.date == day }
            val isToday = day == today
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(28.dp)
                        .background(
                            if (done) RepsGreen else RepsGreen.copy(alpha = if (isToday) 0.18f else 0.10f),
                            RoundedCornerShape(4.dp),
                        ),
                )
                Text(
                    text = day.dayOfWeek.name.take(1),
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
    val month = if (showLastMonth) YearMonth.now().minusMonths(1) else YearMonth.now()
    val previousMonth = YearMonth.now().minusMonths(if (showLastMonth) 2 else 1)
    val current = remember(state.sessions, month) { monthlyStats(state.sessions, month) }
    val previous = remember(state.sessions, previousMonth) { monthlyStats(state.sessions, previousMonth) }

    Column {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.progress_eyebrow), style = MaterialTheme.typography.titleSmall, color = RepsTheme.colors.textPrimary)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                com.reps.app.core.components.RepsChip(
                    label = stringResource(R.string.progress_this_month),
                    selected = !showLastMonth,
                    onClick = { showLastMonth = false },
                )
                com.reps.app.core.components.RepsChip(
                    label = stringResource(R.string.progress_last_month),
                    selected = showLastMonth,
                    onClick = { showLastMonth = true },
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
                value = (current.minutes / 60f).let { "%.1f".format(it) },
                unit = "h",
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
                unit = stringResource(if (state.units == com.reps.app.domain.model.UnitSystem.METRIC) R.string.workouts_kg else R.string.workouts_lb),
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
                unit = stringResource(if (state.units == com.reps.app.domain.model.UnitSystem.METRIC) R.string.workouts_kg else R.string.workouts_lb),
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
