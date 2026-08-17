package com.reps.app.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.reps.app.R
import com.reps.app.core.components.MotivationQuoteCard
import com.reps.app.core.components.StreakBadge
import com.reps.app.core.components.TodayWorkoutCard
import com.reps.app.core.components.WeightWidget
import com.reps.app.core.theme.RepsGreen
import com.reps.app.core.theme.RepsOnGreen
import com.reps.app.core.theme.RepsTheme
import com.reps.app.core.util.DateUtils
import com.reps.app.domain.model.Difficulty
import com.reps.app.domain.model.Streak
import com.reps.app.domain.model.UnitSystem
import com.reps.app.navigation.OnTabReselected
import com.reps.app.navigation.Routes
import com.reps.app.navigation.navBarClearance

@Composable
fun HomeScreen(
    onStartWorkout: (String) -> Unit,
    onOpenWeight: () -> Unit,
    onOpenNotifications: () -> Unit,
    onOpenProfile: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    HomeContent(
        state = state,
        onStartWorkout = onStartWorkout,
        onOpenWeight = onOpenWeight,
        onOpenNotifications = onOpenNotifications,
        onOpenProfile = onOpenProfile,
    )
}

@Composable
private fun HomeContent(
    state: HomeUiState,
    onStartWorkout: (String) -> Unit,
    onOpenWeight: () -> Unit,
    onOpenNotifications: () -> Unit,
    onOpenProfile: () -> Unit,
) {
    val dimens = RepsTheme.dimens
    val listState = rememberLazyListState()

    OnTabReselected(Routes.HOME) { listState.animateScrollToItem(0) }

    LazyColumn(
        state = listState,
        // The status bar inset sits on the viewport, not on the header inside
        // it, so content scrolls up to the status bar and stops rather than
        // sliding underneath the clock. Mirrors the prototype's status-spacer,
        // which is a sibling of the scroll area rather than part of it.
        modifier = Modifier
            .fillMaxSize()
            .background(RepsTheme.colors.background)
            // A gentle green wash fading down from the top gives the home tab
            // a warm, welcoming ambient glow without overwhelming the cards.
            .background(
                Brush.verticalGradient(
                    colorStops = arrayOf(
                        0f to RepsGreen.copy(alpha = 0.06f),
                        0.3f to Color.Transparent,
                        1f to Color.Transparent,
                    ),
                ),
            )
            .statusBarsPadding(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = dimens.screenPadding,
            end = dimens.screenPadding,
            // The nav pill floats over the content rather than sitting below it,
            // so the last card has to reserve its own room to scroll clear.
            bottom = navBarClearance(),
        ),
        verticalArrangement = Arrangement.spacedBy(dimens.sectionGap),
    ) {
        item {
            HomeHeader(
                userName = state.userName,
                onOpenNotifications = onOpenNotifications,
                onOpenProfile = onOpenProfile,
            )
        }

        // Only shown once a streak actually exists, per the brief.
        if (state.streak.isActive) {
            item { StreakBadge(state.streak) }
        }

        item {
            val workout = state.todayWorkout
            if (workout != null) {
                // map is inline, so stringResource is legal inside it;
                // joinToString's transform is not.
                val names = state.todayMuscleGroups.map { stringResource(it.labelRes) }
                // "Chest, Shoulders & Arms" rather than chaining every group
                // with "&", which reads badly past two.
                val muscles = when (names.size) {
                    0 -> ""
                    1 -> names.first()
                    else -> names.dropLast(1).joinToString(", ") + " & " + names.last()
                }
                TodayWorkoutCard(
                    workoutName = workout.name,
                    muscleGroups = muscles,
                    exerciseCount = workout.exercises.size,
                    setCount = state.todaySetCount,
                    durationMin = workout.estimatedMinutes,
                    difficulty = workout.difficulty,
                    exercises = state.todayExercises,
                    onStart = { onStartWorkout(workout.id) },
                )
            } else {
                RestDayCard()
            }
        }

        item {
            WeightWidget(
                weightKg = state.currentWeightKg,
                deltaKg = state.weeklyDeltaKg,
                units = state.units,
                onClick = onOpenWeight,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        item {
            Spacer(Modifier.height(2.dp))
            MotivationQuoteCard(state.quote)
        }
    }
}

@Composable
private fun HomeHeader(
    userName: String,
    onOpenNotifications: () -> Unit,
    onOpenProfile: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = stringResource(DateUtils.greetingFor()),
                style = MaterialTheme.typography.bodyMedium,
                color = RepsTheme.colors.textSecondary,
            )
            Text(
                text = userName,
                style = MaterialTheme.typography.headlineMedium,
                color = RepsTheme.colors.textPrimary,
            )
        }
        IconButton(onClick = onOpenNotifications) {
            Icon(
                imageVector = Icons.Outlined.Notifications,
                contentDescription = stringResource(R.string.home_notifications),
                tint = RepsTheme.colors.textPrimary,
                modifier = Modifier.size(22.dp),
            )
        }
        Box(
            Modifier
                .size(RepsTheme.dimens.avatarSize)
                .clip(CircleShape)
                .background(RepsGreen)
                .clickable(onClick = onOpenProfile),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = userName.firstOrNull()?.uppercase().orEmpty(),
                style = MaterialTheme.typography.titleMedium,
                color = RepsOnGreen,
            )
        }
    }
}

@Composable
private fun RestDayCard() {
    Column(
        Modifier
            .fillMaxWidth()
            .background(RepsTheme.colors.surface, MaterialTheme.shapes.large)
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        RepsGreen.copy(alpha = 0.05f),
                        Color.Transparent,
                    ),
                ),
                shape = MaterialTheme.shapes.large,
            )
            .padding(16.dp),
    ) {
        Text(
            text = stringResource(R.string.home_today_workout).uppercase(),
            style = RepsTheme.textStyles.eyebrow,
            color = RepsGreen,
        )
        Text(
            text = stringResource(R.string.home_rest_day).uppercase(),
            style = RepsTheme.textStyles.sectionTitle,
            color = RepsTheme.colors.textPrimary,
            modifier = Modifier.padding(top = 4.dp),
        )
        Text(
            text = stringResource(R.string.home_rest_day_subtext),
            style = MaterialTheme.typography.bodyMedium,
            color = RepsTheme.colors.textSecondary,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0B0B0A)
@Composable
private fun HomePreview() {
    com.reps.app.core.theme.RepsTheme {
        HomeContent(
            state = HomeUiState(
                userName = "Alex Rivera",
                streak = Streak(count = 12),
                todayWorkout = com.reps.app.data.fake.SampleData.pushDay,
                todayMuscleGroups = listOf(
                    com.reps.app.domain.model.MuscleGroup.CHEST,
                    com.reps.app.domain.model.MuscleGroup.SHOULDERS,
                ),
                todayExercises = listOf(
                    TodayExercise("Bench Press", 3, 8),
                    TodayExercise("Incline DB Press", 3, 10),
                    TodayExercise("Cable Fly", 3, 12),
                    TodayExercise("Overhead Press", 3, 8),
                ),
                todaySetCount = 12,
                currentWeightKg = 78.4,
                weeklyDeltaKg = -0.6,
                units = UnitSystem.METRIC,
                quote = "Every rep counts.\nEvery set matters.",
                loading = false,
            ),
            onStartWorkout = {},
            onOpenWeight = {},
            onOpenNotifications = {},
            onOpenProfile = {},
        )
    }
}
