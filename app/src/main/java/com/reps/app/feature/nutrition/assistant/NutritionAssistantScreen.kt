package com.reps.app.feature.nutrition.assistant

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.reps.app.core.theme.RepsTheme

/**
 * The full-screen conversation with the nutrition assistant.
 *
 * Everything on this screen is presentation: the transcript, the typing state
 * and the seeded history all come from [NutritionAssistantViewModel], which
 * answers from local mock content. No nutrition figure shown here is calculated
 * by the app.
 *
 * [viewModel] is passed in rather than resolved here because the history screen
 * shares this instance - see how the two destinations are wired in the nav host.
 */
@Composable
fun NutritionAssistantScreen(
    onBack: () -> Unit,
    onOpenHistory: () -> Unit,
    viewModel: NutritionAssistantViewModel,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    var input by rememberSaveable { mutableStateOf("") }

    // The newest line is kept in view, including the typing row, which is the
    // last item while a reply is pending. Keyed on the count rather than the
    // list so an unchanged transcript does not re-scroll on every recomposition.
    LaunchedEffect(state.messages.size, state.responding) {
        val lastIndex = state.messages.size - if (state.responding) 0 else 1
        if (lastIndex >= 0) listState.animateScrollToItem(lastIndex)
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(RepsTheme.colors.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            // Lifts the composer above the keyboard. The activity is edge to
            // edge with adjustResize, so this is what keeps the input visible.
            .imePadding(),
    ) {
        AssistantTopBar(
            onBack = onBack,
            onOpenHistory = onOpenHistory,
            onNewChat = {
                viewModel.newChat()
                input = ""
            },
        )

        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (state.showWelcome) {
                AssistantWelcome(
                    onSuggestion = viewModel::send,
                    modifier = Modifier.align(Alignment.Center),
                )
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = RepsTheme.dimens.screenPadding,
                        end = RepsTheme.dimens.screenPadding,
                        top = 16.dp,
                        bottom = 16.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(
                        items = state.messages,
                        key = { it.id },
                    ) { message ->
                        MessageRow(message, Modifier.animateItem())
                    }
                    if (state.responding) {
                        item(key = "typing") { TypingRow(Modifier.animateItem()) }
                    }
                }
            }
        }

        AssistantComposer(
            value = input,
            onValueChange = { input = it },
            onSend = {
                viewModel.send(input)
                input = ""
            },
            canSend = input.isNotBlank() && !state.responding,
        )
    }
}
