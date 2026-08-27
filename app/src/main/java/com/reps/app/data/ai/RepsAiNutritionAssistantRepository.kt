package com.reps.app.data.ai

import com.reps.app.R
import com.reps.app.data.user.UserSession
import com.reps.app.domain.model.Goal
import com.reps.app.domain.model.AssistantConversation
import com.reps.app.domain.model.AssistantMessage
import com.reps.app.domain.model.AssistantError
import com.reps.app.domain.model.AssistantResult
import com.reps.app.domain.model.UnderstandingResponse
import com.reps.app.domain.model.MealDraft
import com.reps.app.domain.repository.NutritionAssistantRepository
import com.reps.app.domain.repository.AssistantConversationRepository
import com.reps.app.domain.repository.UserRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RepsAiNutritionAssistantRepository @Inject constructor(
    private val apiService: RepsAiApiService,
    private val conversationRepository: AssistantConversationRepository,
    private val userRepository: UserRepository,
) : NutritionAssistantRepository {

    @Volatile
    private var storedConversationId: String? = null

    private suspend fun loadConversationIdFromStorage(): String {
        val convos = conversationRepository.observeConversations().first()
        val id = convos.firstOrNull()?.id
        storedConversationId = id
        return id ?: UUID.randomUUID().toString()
    }

    private suspend fun mapUserContext(): RepsAiApiService.UserContext? {
        val user = userRepository.observeUser().firstOrNull()
        if (user == null) return null

        return RepsAiApiService.UserContext(
            goal = user.goal ?: null,
            weightKg = user.weightKg ?: null,
            heightCm = user.heightCm ?: null,
            ageYears = user.ageYears ?: null,
            calorieTarget = user.calorieTarget ?: null,
            proteinTarget = user.proteinTarget ?: null,
            carbsTarget = user.carbsTarget ?: null,
            fatTarget = user.fatTarget ?: null,
            caloriesConsumed = user.caloriesConsumed ?: null,
            proteinConsumed = user.proteinConsumed ?: null,
        )
    }

    private suspend fun ensureConversationId(): String {
        if (storedConversationId != null && storedConversationId.isNotEmpty()) {
            return storedConversationId
        }
        return loadConversationIdFromStorage()
    }

    private suspend fun saveConversationId(id: String) {
        storedConversationId = id

        val convos = conversationRepository.observeConversations().first()
        if (convos.firstOrNull()?.id != id) {
            val newConv = AssistantConversation(
                id = id,
                title = "",
                updatedAt = System.currentTimeMillis(),
                messages = emptyList(),
            )
            conversationRepository.upsert(newConv)
        }
    }

    private fun parseResponse(
        response: RepsAiApiService.ChatResponse,
    ): UnderstandingResponse {
        return UnderstandingResponse(
            message = response.message,
            draft = MealDraft(
                mealName = null,
                ingredients = emptyList(),
                readyForAnalysis = false,
                confidence = 0.0f,
                followUpQuestions = emptyList(),
            ),
        )
    }

    override suspend fun understand(
        history: List<com.reps.app.domain.repository.AssistantExchange>,
        message: String,
    ): AssistantResult<UnderstandingResponse> {
        suspendCancellableCoroutine { cont ->
            val conversationId = ensureConversationId()
            val userContext = mapUserContext()

            apiService.sendMessageWithContext(
                message = message,
                conversationId = conversationId,
                userContext = userContext,
            }.let { apiResponse ->
                val understanding = parseResponse(apiResponse)

                launch {
                    withContext(Dispatchers.IO) {
                        saveConversationId(apiResponse.conversationId)
                    }
                }

                cont.resume(AssistantResult.Success(understanding))
            }.catch { e ->
                val error = when (e) {
                    is IOException -> when (e.message) {
                        "AI service unreachable" -> AssistantError.Network
                        "AI service returned 413" -> AssistantError.ModelUnavailable
                        "AI service returned 422" -> AssistantError.Unknown
                        "AI service returned 429" -> AssistantError.RateLimited
                        else -> AssistantError.Unknown
                    }
                    is java.lang.AssertionError -> AssistantError.Unknown
                    else -> AssistantError.Unknown
                }
                cont.resume(AssistantResult.Failure(error))
            }
        }
    }

    override suspend fun analyseAndCoach(
        draft: MealDraft,
        goal: Goal,
    ): AssistantResult<CoachedAnalysis> {
        suspendCancellableCoroutine { cont ->
            cont.resume(
                AssistantResult.Failure(
                    AssistantError.ModelUnavailable
                )
            )
        }
    }

    override suspend fun ask(
        history: List<com.reps.app.domain.repository.AssistantExchange>,
        question: String,
        goal: Goal,
    ): AssistantResult<String> {
        suspendCancellableCoroutine { cont ->
            val conversationId = ensureConversationId()
            val userContext = mapUserContext()

            apiService.sendMessageWithContext(
                message = question,
                conversationId = conversationId,
                userContext = userContext,
            ).let { apiResponse ->
                cont.resume(AssistantResult.Success(apiResponse.message))
            }.catch { e ->
                val error = when (e) {
                    is IOException -> AssistantError.Network
                    else -> AssistantError.Unknown
                }
                cont.resume(AssistantResult.Failure(error))
            }
        }
    }
}