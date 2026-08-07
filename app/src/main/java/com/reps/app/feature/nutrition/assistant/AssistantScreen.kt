package com.reps.app.feature.nutrition.assistant

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.reps.app.R
import com.reps.app.core.components.RepsBackButton
import com.reps.app.core.components.RepsTextField
import com.reps.app.core.components.SectionHeader
import com.reps.app.core.theme.RepsCarbs
import com.reps.app.core.theme.RepsError
import com.reps.app.core.theme.RepsFat
import com.reps.app.core.theme.RepsGreen
import com.reps.app.core.theme.RepsOnGreen
import com.reps.app.core.theme.RepsProtein
import com.reps.app.core.theme.RepsTheme
import com.reps.app.domain.model.AssistantError
import com.reps.app.domain.model.AssistantTurn
import com.reps.app.domain.model.NutritionAnalysis
import kotlin.math.roundToInt

@Composable
fun AssistantScreen(
    onBack: () -> Unit,
    viewModel: AssistantViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    var input by remember { mutableStateOf("") }

    // A new turn should be the thing you are looking at, including while the
    // busy row is showing - otherwise the answer arrives off-screen.
    LaunchedEffect(state.turns.size, state.busy) {
        val lastIndex = state.turns.lastIndex + if (state.busy) 1 else 0
        if (lastIndex >= 0) listState.animateScrollToItem(lastIndex)
    }

    val dimens = RepsTheme.dimens
    Column(
        Modifier
            .fillMaxSize()
            .background(RepsTheme.colors.background)
            .statusBarsPadding()
            .imePadding(),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = dimens.screenPadding, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            RepsBackButton(onClick = onBack)
            SectionHeader(
                eyebrow = stringResource(R.string.assistant_eyebrow),
                title = stringResource(R.string.assistant_title),
            )
        }

        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(
                start = dimens.screenPadding,
                end = dimens.screenPadding,
                top = 8.dp,
                bottom = 16.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (state.turns.isEmpty()) {
                item { IntroBubble() }
            }

            items(count = state.turns.size, key = { state.turns[it].id }) { index ->
                when (val turn = state.turns[index]) {
                    is AssistantTurn.User -> UserBubble(turn.text)
                    is AssistantTurn.Understanding -> AssistantBubble(turn.message)
                    is AssistantTurn.Coaching -> Column(
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        AnalysisCard(turn.analysis)
                        AssistantBubble(turn.message)
                    }

                    is AssistantTurn.Failed -> ErrorBubble(
                        error = turn.reason,
                        onRetry = viewModel::retry,
                    )
                }
            }

            if (state.busy) {
                item {
                    BusyRow(
                        label = if (state.analysing) {
                            stringResource(R.string.assistant_analysing)
                        } else {
                            stringResource(R.string.assistant_thinking)
                        },
                    )
                }
            }
        }

        Row(
            Modifier
                .fillMaxWidth()
                .background(RepsTheme.colors.surface)
                .navigationBarsPadding()
                .padding(horizontal = dimens.screenPadding, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            RepsTextField(
                value = input,
                onValueChange = { input = it },
                placeholder = stringResource(R.string.assistant_hint),
                enabled = state.canSend,
                modifier = Modifier.weight(1f),
            )
            val sendEnabled = state.canSend && input.isNotBlank()
            Box(
                Modifier
                    .size(48.dp)
                    .alpha(if (sendEnabled) 1f else 0.4f)
                    .background(RepsGreen, CircleShape)
                    .clickableWhen(sendEnabled) {
                        viewModel.send(input)
                        input = ""
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.Send,
                    contentDescription = stringResource(R.string.assistant_send),
                    tint = RepsOnGreen,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

/**
 * `clickable` with `enabled = false` still reserves the semantics of a disabled
 * button; this keeps the send affordance out of the tree entirely when there is
 * nothing to send.
 */
private fun Modifier.clickableWhen(enabled: Boolean, onClick: () -> Unit): Modifier =
    if (enabled) this.clickable(role = Role.Button, onClick = onClick) else this

@Composable
private fun IntroBubble() {
    AssistantBubble(stringResource(R.string.assistant_intro))
}

@Composable
private fun UserBubble(text: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = RepsOnGreen,
            modifier = Modifier
                .widthIn(max = 300.dp)
                .background(RepsGreen, MaterialTheme.shapes.large)
                .padding(horizontal = 14.dp, vertical = 10.dp),
        )
    }
}

@Composable
private fun AssistantBubble(text: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = RepsTheme.colors.textPrimary,
            modifier = Modifier
                .widthIn(max = 320.dp)
                .background(RepsTheme.colors.surface, MaterialTheme.shapes.large)
                .padding(horizontal = 14.dp, vertical = 10.dp),
        )
    }
}

@Composable
private fun BusyRow(label: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.padding(start = 4.dp),
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(14.dp),
            strokeWidth = 2.dp,
            color = RepsGreen,
        )
        Text(label, style = MaterialTheme.typography.bodySmall, color = RepsTheme.colors.textSecondary)
    }
}

@Composable
private fun ErrorBubble(error: AssistantError, onRetry: () -> Unit) {
    val message = stringResource(
        when (error) {
            AssistantError.Network -> R.string.assistant_error_network
            AssistantError.NotSignedIn -> R.string.assistant_error_signin
            AssistantError.RateLimited -> R.string.assistant_error_rate_limited
            AssistantError.ModelUnavailable -> R.string.assistant_error_model
            AssistantError.FoodDatabaseUnavailable -> R.string.assistant_error_food_db
            AssistantError.Unknown -> R.string.assistant_error_unknown
        },
    )

    Column(
        Modifier
            .fillMaxWidth()
            .background(RepsTheme.colors.surface, MaterialTheme.shapes.large)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(message, style = MaterialTheme.typography.bodyMedium, color = RepsError)
        if (error.isRetryable) {
            Text(
                text = stringResource(R.string.assistant_retry),
                style = MaterialTheme.typography.labelLarge,
                color = RepsGreen,
                modifier = Modifier.clickableWhen(true, onRetry),
            )
        }
    }
}

/**
 * Every figure here comes from [analysis]. The coach's prose is rendered
 * separately, above, and is never parsed for numbers - which is what keeps the
 * displayed nutrition traceable to USDA rather than to a model.
 */
@Composable
private fun AnalysisCard(analysis: NutritionAnalysis) {
    val macros = analysis.macros
    Column(
        Modifier
            .fillMaxWidth()
            .background(RepsTheme.colors.surface, MaterialTheme.shapes.large)
            .padding(RepsTheme.dimens.cardPadding),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                analysis.mealName,
                style = MaterialTheme.typography.titleSmall,
                color = RepsTheme.colors.textPrimary,
            )
            Text(
                "${macros.calories.roundToInt()} ${stringResource(R.string.nutrition_kcal)}",
                style = MaterialTheme.typography.labelMedium,
                color = RepsGreen,
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            MacroPill(stringResource(R.string.nutrition_protein), macros.protein, RepsProtein)
            MacroPill(stringResource(R.string.nutrition_carbs), macros.carbs, RepsCarbs)
            MacroPill(stringResource(R.string.nutrition_fat), macros.fat, RepsFat)
        }

        analysis.items.forEach { item ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    "${item.foodItem.name} · ${item.foodItem.grams.roundToInt()}g",
                    style = MaterialTheme.typography.bodySmall,
                    color = RepsTheme.colors.textSecondary,
                )
                Text(
                    "${item.foodItem.calories.roundToInt()} ${stringResource(R.string.nutrition_kcal)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = RepsTheme.colors.textTertiary,
                )
            }
        }

        if (analysis.isPartial) {
            Text(
                stringResource(R.string.assistant_partial, analysis.unmatched.joinToString(", ")),
                style = MaterialTheme.typography.bodySmall,
                color = RepsError,
            )
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                stringResource(R.string.assistant_source),
                style = MaterialTheme.typography.labelSmall,
                color = RepsTheme.colors.textTertiary,
            )
            Text(
                stringResource(
                    if (analysis.fromCache) R.string.assistant_from_cache else R.string.assistant_logged,
                ),
                style = MaterialTheme.typography.labelSmall,
                color = RepsTheme.colors.textTertiary,
            )
        }
    }
}

@Composable
private fun MacroPill(label: String, grams: Double, color: androidx.compose.ui.graphics.Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.size(7.dp).background(color, CircleShape))
        Text(
            "$label ${grams.roundToInt()}g",
            style = MaterialTheme.typography.labelSmall,
            color = RepsTheme.colors.textSecondary,
        )
    }
}
