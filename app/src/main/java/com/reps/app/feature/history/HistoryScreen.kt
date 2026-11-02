package com.reps.app.feature.history

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.reps.app.R
import com.reps.app.core.components.RepsBackButton
import com.reps.app.core.components.SectionHeader
import com.reps.app.core.theme.RepsAchievement
import com.reps.app.core.theme.RepsTheme
import com.reps.app.core.util.UnitConverter
import com.reps.app.domain.model.Exercise
import com.reps.app.domain.model.UnitSystem
import com.reps.app.domain.model.WorkoutSession
import com.reps.app.feature.progress.currentLocale
import com.reps.app.feature.progress.weightUnitLabel
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
fun HistoryScreen(onBack: () -> Unit, viewModel: HistoryViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val dimens = RepsTheme.dimens
    val dateFormat = DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(currentLocale())

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(RepsTheme.colors.background).statusBarsPadding(),
        contentPadding = PaddingValues(start = dimens.screenPadding, end = dimens.screenPadding, top = 8.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(dimens.sectionGap),
    ) {
        item { RepsBackButton(onClick = onBack) }
        item {
            SectionHeader(
                eyebrow = pluralStringResource(R.plurals.history_count, state.sessions.size, state.sessions.size),
                title = stringResource(R.string.history_title),
            )
        }
        if (!state.loading && state.sessions.isEmpty()) {
            item {
                Column(Modifier.fillMaxWidth().padding(vertical = 32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(stringResource(R.string.history_empty_title), style = MaterialTheme.typography.titleSmall, color = RepsTheme.colors.textPrimary)
                    Text(
                        stringResource(R.string.history_empty_body),
                        style = MaterialTheme.typography.bodySmall,
                        color = RepsTheme.colors.textSecondary,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }
        }
        items(state.sessions, key = { it.id }) { session ->
            SessionCard(session, state.exercisesById, state.units, session.date.format(dateFormat))
        }
        item { androidx.compose.foundation.layout.Spacer(Modifier.navigationBarsPadding()) }
    }
}

@Composable
private fun SessionCard(session: WorkoutSession, exercisesById: Map<String, Exercise>, units: UnitSystem, dateLabel: String) {
    var expanded by rememberSaveable(session.id) { mutableStateOf(false) }
    val unit = weightUnitLabel(units)
    Column(
        Modifier
            .fillMaxWidth()
            .background(RepsTheme.colors.surface, MaterialTheme.shapes.large)
            .clickable { expanded = !expanded }
            .padding(RepsTheme.dimens.cardPadding)
            .animateContentSize(),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(session.name, style = MaterialTheme.typography.titleSmall, color = RepsTheme.colors.textPrimary)
                Text(dateLabel, style = MaterialTheme.typography.labelSmall, color = RepsTheme.colors.textSecondary)
                Text(
                    text = stringResource(
                        R.string.history_session_meta,
                        if (session.durationMeasured) {
                            pluralStringResource(R.plurals.duration_minutes, session.durationMin, session.durationMin)
                        } else {
                            stringResource(R.string.progress_duration_not_measured)
                        },
                        session.completedSets.size,
                        UnitConverter.formatWeight(session.completedSets.sumOf { it.volume }, units, decimals = 0),
                        unit,
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = RepsTheme.colors.textTertiary,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            if (session.prsHit.isNotEmpty()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .background(RepsAchievement.copy(alpha = 0.12f), CircleShape)
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                ) {
                    Icon(Icons.Outlined.EmojiEvents, contentDescription = null, tint = RepsAchievement, modifier = Modifier.size(12.dp))
                    Text(
                        " ${stringResource(R.string.history_pr_badge)} ${session.prsHit.size}",
                        style = MaterialTheme.typography.labelSmall,
                        color = RepsAchievement,
                    )
                }
            }
            Icon(
                imageVector = if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                contentDescription = null,
                tint = RepsTheme.colors.textTertiary,
            )
        }
        if (expanded) {
            session.exercises.sortedBy { it.position }.forEach { exercise ->
                val name = exercisesById[exercise.exerciseId]?.name ?: return@forEach
                Row(Modifier.padding(top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(name, style = MaterialTheme.typography.bodyMedium, color = RepsTheme.colors.textPrimary, modifier = Modifier.weight(1f))
                    if (exercise.exerciseId in session.prsHit) {
                        Icon(Icons.Outlined.EmojiEvents, contentDescription = stringResource(R.string.history_pr_badge), tint = RepsAchievement, modifier = Modifier.size(14.dp))
                    }
                }
                exercise.sets.forEachIndexed { index, set ->
                    Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
                        Text("${index + 1}", style = MaterialTheme.typography.labelMedium, color = RepsTheme.colors.textTertiary, modifier = Modifier.weight(0.3f))
                        Text(
                            text = if (set.weightKg > 0.0) {
                                stringResource(R.string.progress_record_weighted, UnitConverter.formatWeight(set.weightKg, units), unit, set.reps)
                            } else {
                                stringResource(R.string.history_bodyweight_set, set.reps)
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = if (set.completed) RepsTheme.colors.textPrimary else RepsTheme.colors.textTertiary,
                            modifier = Modifier.weight(1f),
                        )
                        if (!set.completed) {
                            Text(stringResource(R.string.history_not_done), style = MaterialTheme.typography.labelSmall, color = RepsTheme.colors.textTertiary)
                        }
                    }
                }
            }
        }
    }
}
