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
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

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

        val targets = if (user.sex != null && latestWeight != null && user.heightCm != null && user.age != null) {
            NutritionTargetsCalculator.daily(
                sex = user.sex,
                weightKg = latestWeight,
                heightCm = user.heightCm,
                age = user.age,
                goal = user.goal,
            )
        } else {
            null
        }

        return RepsAiApiService.UserContext(
            goal = user.goal.toRepsAiGoal(),
            weightKg = latestWeight,
            heightCm = user.heightCm,
            ageYears = user.age,
            calorieTarget = targets?.calories,
            proteinTarget = targets?.protein,
            carbsTarget = targets?.carbs,
            fatTarget = targets?.fat,
            caloriesConsumed = totals.calories.takeIf { it > 0.0 },
            proteinConsumed = totals.protein.takeIf { it > 0.0 },
        )
    }

    override suspend fun understand(
        history: List<AssistantExchange>,
        message: String,
        conversationId: String?,
    ): AssistantResult<UnderstandingResponse> {
        return try {
            val userContext = mapUserContext()

            val apiResponse = apiService.sendMessageWithContext(
                message = message,
                conversationId = conversationId,
                userContext = userContext,
            )

            val understanding = UnderstandingResponse(
                message = apiResponse.message,
                draft = MealDraft(),
                conversationId = apiResponse.conversationId,
                responseType = apiResponse.type,
            )
            AssistantResult.Success(understanding)
        } catch (e: Exception) {
            AssistantResult.Failure(mapExceptionToError(e))
        }
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
    ): AssistantResult<String> {
        return try {
            val userContext = mapUserContext()

            val apiResponse = apiService.sendMessageWithContext(
                message = question,
                conversationId = conversationId,
                userContext = userContext,
            )
            AssistantResult.Success(apiResponse.message)
        } catch (e: Exception) {
            AssistantResult.Failure(mapExceptionToError(e))
        }
    }

    private fun mapExceptionToError(e: Exception): AssistantError {
        return when (e) {
            is RepsAiApiException -> when (e.statusCode) {
                429 -> AssistantError.RateLimited
                502, 503 -> AssistantError.ModelUnavailable
                else -> AssistantError.Network
            }
            is IOException -> AssistantError.Network
            else -> AssistantError.Unknown
        }
    }
}
