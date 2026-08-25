package com.reps.app.domain.repository

import com.reps.app.domain.model.AssistantError
import com.reps.app.domain.model.Goal
import com.reps.app.domain.model.MealDraft
import com.reps.app.domain.model.NutritionAnalysis

/**
 * The app's view of the AI agent - this is the seam to implement when plugging
 * a real assistant in. Everything above it (ViewModel, screens, history) is
 * already written against these three methods.
 *
 * Each method is one hop in the documented flow, which keeps the pipeline
 * inspectable: a failure belongs to a named stage rather than to one opaque
 * "ask the AI" call. The ViewModel drives the hops in order - [understand]
 * until the draft reports ready, then [analyseAndCoach]; [ask] handles
 * everything outside meal logging.
 *
 * To go live: deploy /functions, write an implementation backed by its
 * callables, and swap the binding in `RepositoryModule`. Adding an agent later
 * (photo analysis, barcode, weekly report) means adding a method here and a
 * handler in the backend registry - no existing call changes.
 */
interface NutritionAssistantRepository {

    /**
     * One turn with the Meal Understanding Agent. [history] is the conversation
     * so far; the agent either answers with more questions or reports
     * [MealDraft.readyForAnalysis].
     */
    suspend fun understand(
        history: List<AssistantExchange>,
        message: String,
    ): AssistantResult<UnderstandingResponse>

    /**
     * Runs the backend nutrition engine over a ready draft, then hands the
     * verified figures to the Nutrition Coach Agent.
     */
    suspend fun analyseAndCoach(
        draft: MealDraft,
        goal: Goal,
    ): AssistantResult<CoachedAnalysis>

    /** A standalone nutrition question for the coach, outside meal logging. */
    suspend fun ask(
        history: List<AssistantExchange>,
        question: String,
        goal: Goal,
    ): AssistantResult<String>
}

/** A prior turn, flattened to what the model needs to see. */
data class AssistantExchange(
    val fromUser: Boolean,
    val text: String,
)

data class UnderstandingResponse(
    val message: String,
    val draft: MealDraft,
)

data class CoachedAnalysis(
    val analysis: NutritionAnalysis,
    /** Prose only. Never a source of numbers - see [NutritionAnalysis]. */
    val coaching: String,
)

sealed interface AssistantResult<out T> {
    data class Success<T>(val value: T) : AssistantResult<T>
    data class Failure(val error: AssistantError) : AssistantResult<Nothing>
}

inline fun <T, R> AssistantResult<T>.map(transform: (T) -> R): AssistantResult<R> = when (this) {
    is AssistantResult.Success -> AssistantResult.Success(transform(value))
    is AssistantResult.Failure -> this
}
