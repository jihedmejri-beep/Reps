package com.reps.app.feature.nutrition.assistant

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.reps.app.domain.model.AssistantError
import com.reps.app.domain.model.AssistantTurn
import com.reps.app.domain.model.Goal
import com.reps.app.domain.model.Meal
import com.reps.app.domain.model.MealDraft
import com.reps.app.domain.repository.AssistantExchange
import com.reps.app.domain.repository.AssistantResult
import com.reps.app.domain.repository.MealRepository
import com.reps.app.domain.repository.NutritionAssistantRepository
import com.reps.app.domain.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject

data class AssistantUiState(
    val turns: List<AssistantTurn> = emptyList(),
    /** True while any stage of the pipeline is in flight. */
    val busy: Boolean = false,
    /** Set while the engine and coach run, which is the slow half. */
    val analysing: Boolean = false,
) {
    val canSend: Boolean get() = !busy
}

@HiltViewModel
class AssistantViewModel @Inject constructor(
    private val assistant: NutritionAssistantRepository,
    private val userRepository: UserRepository,
    private val mealRepository: MealRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AssistantUiState())
    val uiState = _uiState.asStateFlow()

    /** The last message sent, so a failed turn can be retried without retyping. */
    private var lastMessage: String? = null

    fun send(text: String) {
        val message = text.trim()
        if (message.isEmpty() || _uiState.value.busy) return

        lastMessage = message
        _uiState.update {
            it.copy(
                turns = it.turns + AssistantTurn.User(newId(), message),
                busy = true,
            )
        }
        viewModelScope.launch { runUnderstanding(message) }
    }

    /** Re-runs the last message after a retryable failure. */
    fun retry() {
        val message = lastMessage ?: return
        if (_uiState.value.busy) return

        // Drop the failure so the conversation does not accumulate dead ends.
        _uiState.update {
            it.copy(turns = it.turns.filterNot { turn -> turn is AssistantTurn.Failed }, busy = true)
        }
        viewModelScope.launch { runUnderstanding(message) }
    }

    private suspend fun runUnderstanding(message: String) {
        when (val result = assistant.understand(history(), message)) {
            is AssistantResult.Failure -> fail(result.error)

            is AssistantResult.Success -> {
                val response = result.value
                _uiState.update {
                    it.copy(
                        turns = it.turns + AssistantTurn.Understanding(
                            id = newId(),
                            message = response.message,
                            draft = response.draft,
                        ),
                    )
                }

                // The agent has everything it needs, so continue straight into
                // analysis rather than making the user say "ok, now work it out".
                if (response.draft.readyForAnalysis) {
                    runAnalysis(response.draft)
                } else {
                    _uiState.update { it.copy(busy = false) }
                }
            }
        }
    }

    private suspend fun runAnalysis(draft: MealDraft) {
        _uiState.update { it.copy(analysing = true) }

        val goal = userRepository.observeUser().first()?.goal ?: Goal.MAINTAIN

        when (val result = assistant.analyseAndCoach(draft, goal)) {
            is AssistantResult.Failure -> {
                _uiState.update { it.copy(analysing = false) }
                fail(result.error)
            }

            is AssistantResult.Success -> {
                val coached = result.value
                _uiState.update {
                    it.copy(
                        turns = it.turns + AssistantTurn.Coaching(
                            id = newId(),
                            message = coached.coaching,
                            analysis = coached.analysis,
                        ),
                        busy = false,
                        analysing = false,
                    )
                }

                // Logged through the existing meal repository rather than written
                // by the backend, so an assistant-logged meal is the same record
                // as a hand-entered one and shows up on the Nutrition tab
                // unchanged. The difference is that these numbers came from USDA.
                mealRepository.logMeal(
                    Meal(
                        id = UUID.randomUUID().toString(),
                        name = coached.analysis.mealName,
                        date = LocalDate.now(),
                        foodItems = coached.analysis.items.map { it.foodItem },
                    ),
                )
            }
        }
    }

    private fun fail(error: AssistantError) {
        _uiState.update {
            it.copy(
                turns = it.turns + AssistantTurn.Failed(newId(), error),
                busy = false,
                analysing = false,
            )
        }
    }

    /**
     * The conversation as the agents need to see it. Failures are dropped, and
     * a coaching turn contributes only its prose - the analysis travels as
     * structured data, not as text for a model to re-read.
     */
    private fun history(): List<AssistantExchange> =
        _uiState.value.turns.mapNotNull { turn ->
            when (turn) {
                is AssistantTurn.User -> AssistantExchange(true, turn.text)
                is AssistantTurn.Understanding -> AssistantExchange(false, turn.message)
                is AssistantTurn.Coaching -> AssistantExchange(false, turn.message)
                is AssistantTurn.Failed -> null
            }
        }

    private fun newId(): String = UUID.randomUUID().toString()
}
