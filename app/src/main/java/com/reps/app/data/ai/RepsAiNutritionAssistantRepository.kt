package com.reps.app.data.ai

import com.reps.app.ai.RepsAiApiService
import com.reps.app.domain.model.AssistantError
import com.reps.app.domain.model.Goal
import com.reps.app.domain.model.AssistantConversation
import com.reps.app.domain.model.MealDraft
import com.reps.app.domain.repository.AssistantConversationRepository
import com.reps.app.domain.repository.AssistantResult
import com.reps.app.domain.repository.CoachedAnalysis
import com.reps.app.domain.repository.NutritionAssistantRepository
import com.reps.app.domain.repository.UnderstandingResponse
import com.reps.app.domain.repository.UserRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import java.io.IOException
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
        val user = userRepository.observeUser().firstOrNull() ?: return null

        return RepsAiApiService.UserContext(
            goal = user.goal,
            heightCm = user.heightCm,
            ageYears = user.age,
        )
    }

    private suspend fun ensureConversationId(): String {
        if (!storedConversationId.isNullOrEmpty()) {
            return storedConversationId!!
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
                confidence = 0.0,
                followUpQuestions = emptyList(),
            ),
        )
    }

    override suspend fun understand(
        history: List<com.reps.app.domain.repository.AssistantExchange>,
        message: String,
    ): AssistantResult<UnderstandingResponse> {
        return try {
            val conversationId = ensureConversationId()
            val userContext = mapUserContext()

            val apiResponse = apiService.sendMessageWithContext(
                message = message,
                conversationId = conversationId,
                userContext = userContext,
            )

            saveConversationId(apiResponse.conversationId)
            val understanding = parseResponse(apiResponse)
            AssistantResult.Success(understanding)
        } catch (e: Exception) {
            val error = when (e) {
                is IOException -> AssistantError.Network
                else -> AssistantError.Unknown
            }
            AssistantResult.Failure(error)
        }
    }

    override suspend fun analyseAndCoach(
        draft: MealDraft,
        goal: Goal,
    ): AssistantResult<CoachedAnalysis> {
        return AssistantResult.Failure(AssistantError.ModelUnavailable)
    }

    override suspend fun ask(
        history: List<com.reps.app.domain.repository.AssistantExchange>,
        question: String,
        goal: Goal,
    ): AssistantResult<String> {
        return try {
            val conversationId = ensureConversationId()
            val userContext = mapUserContext()

            val apiResponse = apiService.sendMessageWithContext(
                message = question,
                conversationId = conversationId,
                userContext = userContext,
            )
            AssistantResult.Success(apiResponse.message)
        } catch (e: Exception) {
            val error = when (e) {
                is IOException -> AssistantError.Network
                else -> AssistantError.Unknown
            }
            AssistantResult.Failure(error)
        }
    }
}
