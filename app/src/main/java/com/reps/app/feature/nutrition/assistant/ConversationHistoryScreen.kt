package com.reps.app.feature.nutrition.assistant

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * Past conversations, opened from the assistant's overflow menu.
 *
 * Backed by the history repository through the shared ViewModel: every row is
 * a chat that actually happened, newest first. Tapping reopens it in the
 * transcript; long-pressing offers to delete it.
 */
@Composable
fun ConversationHistoryScreen(
    onBack: () -> Unit,
    onOpen: (String) -> Unit,
    viewModel: NutritionAssistantViewModel,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val dimens = RepsTheme.dimens
    var pendingDelete by remember { mutableStateOf<String?>(null) }

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
                            label = conversation.title,
                            sub = relativeDate(conversation.updatedAt),
                            showDivider = index > 0,
                            onClick = { onOpen(conversation.id) },
                            trailing = {
                                IconButton(
                                    onClick = { pendingDelete = conversation.id },
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.DeleteOutline,
                                        contentDescription =
                                            stringResource(R.string.assistant_delete_cd),
                                        tint = RepsTheme.colors.textTertiary,
                                        modifier = Modifier.size(20.dp),
                                    )
                                }
                            },
                        )
                    }
                }
            }
        }
    }

    pendingDelete?.let { id ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(stringResource(R.string.assistant_delete_title)) },
            text = { Text(stringResource(R.string.assistant_delete_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteConversation(id)
                        pendingDelete = null
                    },
                ) {
                    Text(
                        stringResource(R.string.assistant_delete_confirm),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text(stringResource(R.string.assistant_delete_cancel))
                }
            },
        )
    }
}

/** Today / Yesterday, otherwise a localised short date - the row's only clock. */
@Composable
private fun relativeDate(epochMilli: Long): String {
    val day = Instant.ofEpochMilli(epochMilli).atZone(ZoneId.systemDefault()).toLocalDate()
    val today = LocalDate.now()
    return when (day) {
        today -> stringResource(R.string.assistant_time_today)
        today.minusDays(1) -> stringResource(R.string.assistant_time_yesterday)
        else -> day.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
    }
}
