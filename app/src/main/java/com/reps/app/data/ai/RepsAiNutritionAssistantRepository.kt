package com.reps.app.data.ai

import com.reps.app.ai.RepsAiApiException
import com.reps.app.ai.RepsAiApiService
import com.reps.app.ai.toRepsAiGoal
import com.reps.app.core.util.NutritionTargetsCalculator
import com.reps.app.domain.model.AssistantError
import com.reps.app.domain.model.Goal
import com.reps.app.domain.model.Macros
import com.reps.app.domain.model.MealDraft
import com.reps.app.domain.repository.AssistantExchange
import com.reps.app.domain.repository.AssistantResult
import com.reps.app.domain.repository.CoachedAnalysis
import com.reps.app.domain.repository.MealRepository
import com.reps.app.domain.repository.NutritionAssistantRepository
import com.reps.app.domain.repository.UnderstandingResponse
import com.reps.app.domain.repository.UserRepository
import com.reps.app.domain.repository.WeightRepository
import kotlinx.coroutines.flow.firstOrNull
import java.io.IOException
import java.io.InterruptedIOException
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException

@Singleton
class RepsAiNutritionAssistantRepository @Inject constructor(
    private val apiService: RepsAiApiService,
    private val userRepository: UserRepository,
    private val weightRepository: WeightRepository,
    private val mealRepository: MealRepository,
) : NutritionAssistantRepository {

    private suspend fun mapUserContext(): RepsAiApiService.UserContext? {
        val user = userRepository.observeUser().firstOrNull() ?: return null
        val latestWeight = weightRepository.observeEntries().firstOrNull()?.maxByOrNull { it.date }?.weightKg
        val today = LocalDate.now()
        val todaysMeals = mealRepository.observeMeals(today).firstOrNull() ?: emptyList()
        val totals = todaysMeals.fold(Macros()) { acc, meal -> acc + meal.macros }

        val targets = NutritionTargetsCalculator.forUser(user, latestWeight, today)

        return RepsAiApiService.UserContext(
            goal = user.goal.toRepsAiGoal(),
            weightKg = latestWeight,
            heightCm = user.heightCm,
            ageYears = user.ageOn(today),
            calorieTarget = targets?.calories,
            proteinTarget = targets?.protein,
            carbsTarget = targets?.carbs,
            fatTarget = targets?.fat,
            caloriesConsumed = totals.calories.takeIf { it > 0.0 },
            proteinConsumed = totals.protein.takeIf { it > 0.0 },
        )
    }

    /**
     * The backend returns a plain answer, never a meal draft, so the draft is
     * always empty here and the ViewModel never moves on to [analyseAndCoach].
     */
    override suspend fun understand(
        history: List<AssistantExchange>,
        message: String,
        conversationId: String?,
    ): AssistantResult<UnderstandingResponse> = chat(message, conversationId) { response ->
        UnderstandingResponse(
            message = response.message,
            draft = MealDraft(),
            conversationId = response.conversationId,
            responseType = response.type,
        )
    }

    override suspend fun analyseAndCoach(
        draft: MealDraft,
        goal: Goal,
    ): AssistantResult<CoachedAnalysis> {
        return AssistantResult.Failure(AssistantError.ModelUnavailable)
    }

    override suspend fun ask(
        history: List<AssistantExchange>,
        question: String,
        goal: Goal,
        conversationId: String?,
    ): AssistantResult<String> = chat(question, conversationId) { it.message }

    /**
     * One request to the backend, with every outcome mapped to a result.
     * Cancellation is rethrown, not reported: a reply the user abandoned (New
     * Chat, leaving the screen) must stop quietly instead of posting an error
     * bubble into whatever is on screen next.
     */
    private suspend fun <T> chat(
        message: String,
        conversationId: String?,
        transform: (RepsAiApiService.ChatResponse) -> T,
    ): AssistantResult<T> {
        if (!apiService.isConfigured) return AssistantResult.Failure(AssistantError.NotConfigured)
        if (message.length > RepsAiApiService.MAX_MESSAGE_LENGTH) {
            return AssistantResult.Failure(AssistantError.MessageTooLong)
        }
        return try {
            val response = apiService.sendMessageWithContext(
                message = message,
                conversationId = conversationId,
                userContext = mapUserContext(),
            )
            AssistantResult.Success(transform(response))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AssistantResult.Failure(mapExceptionToError(e))
        }
    }

    /**
     * The whole cause chain is searched: the client wraps OkHttp's exception, and
     * coroutine stack-trace recovery can wrap it once more, so the interesting
     * type is rarely the outermost one.
     */
    private fun mapExceptionToError(e: Exception): AssistantError {
        val chain = generateSequence<Throwable>(e) { it.cause }.take(8).toList()
        val apiError = chain.firstNotNullOfOrNull { it as? RepsAiApiException }
        return when {
            apiError != null -> when (apiError.statusCode) {
                413, 422 -> AssistantError.MessageTooLong
                429 -> AssistantError.RateLimited
                502, 503 -> AssistantError.ModelUnavailable
                else -> AssistantError.Unknown
            }
            chain.any { it is InterruptedIOException } -> AssistantError.Timeout
            chain.any { it is IOException } -> AssistantError.Network
            else -> AssistantError.Unknown
        }
    }
}
