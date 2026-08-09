package com.reps.app.feature.nutrition.assistant

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.reps.app.R
import com.reps.app.core.components.RepsBackButton
import com.reps.app.core.components.RepsListRow
import com.reps.app.core.components.SectionHeader
import com.reps.app.core.theme.RepsTheme

/**
 * Past conversations, opened from the assistant's overflow menu.
 *
 * Backed by the same in-memory list the chat holds, so picking one hands it
 * straight back to the transcript. Replacing that list with stored history is a
 * change to the ViewModel alone.
 */
@Composable
fun ConversationHistoryScreen(
    onBack: () -> Unit,
    onOpen: (String) -> Unit,
    viewModel: NutritionAssistantViewModel,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val dimens = RepsTheme.dimens

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(RepsTheme.colors.background)
            .statusBarsPadding()
            .navigationBarsPadding(),
        contentPadding = PaddingValues(
            start = dimens.screenPadding,
            end = dimens.screenPadding,
            top = 8.dp,
            bottom = 24.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(dimens.sectionGap),
    ) {
        item { RepsBackButton(onClick = onBack) }
        item {
            SectionHeader(
                eyebrow = stringResource(R.string.assistant_history_eyebrow),
                title = stringResource(R.string.assistant_history_title),
            )
        }

        if (state.conversations.isEmpty()) {
            item {
                Column(
                    Modifier.fillMaxWidth().padding(vertical = 48.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = stringResource(R.string.assistant_history_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = RepsTheme.colors.textTertiary,
                    )
                }
            }
        } else {
            // One card holding every row, the same treatment Profile gives its
            // settings lists. Stacked rather than spaced so the hairline
            // dividers land between rows instead of floating in a gap.
            item {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .background(RepsTheme.colors.surface, MaterialTheme.shapes.large)
                        .padding(horizontal = dimens.cardPadding),
                ) {
                    state.conversations.forEachIndexed { index, conversation ->
                        RepsListRow(
                            label = stringResource(conversation.titleRes),
                            sub = stringResource(conversation.timeRes),
                            showDivider = index > 0,
                            onClick = { onOpen(conversation.id) },
                        )
                    }
                }
            }
        }
    }
}
