package com.reps.app.ai

import com.reps.app.domain.model.Goal
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * End-to-end integration tests validating that REPS communicates directly
 * with the live RepsAI FastAPI server running locally on http://127.0.0.1:8000.
 */
class RepsAiIntegrationTest {

    private lateinit var apiService: RepsAiApiService

    @Before
    fun setUp() {
        apiService = RepsAiApiService(RepsAiApiService.LOCALHOST_BASE_URL)
    }

    private suspend fun <T> withRetry(maxAttempts: Int = 3, block: suspend () -> T): T {
        var lastException: Exception? = null
        for (attempt in 1..maxAttempts) {
            try {
                return block()
            } catch (e: Exception) {
                lastException = e
                if (attempt < maxAttempts) {
                    delay(1500L * attempt)
                }
            }
        }
        throw lastException ?: RuntimeException("Retry failed")
    }

    @Test
    fun testHealthCheck_returnsHealthyStatus() = runBlocking {
        val health = apiService.healthCheck()
        assertEquals("healthy", health.status)
        assertEquals("reps-ai", health.service)

        val status = apiService.checkHealthStatus()
        assertEquals(RepsAiApiService.HealthStatus.CONNECTED, status)
    }

    @Test
    fun test1_basicConnection_receivesRealResponse() = runBlocking {
        val response = withRetry {
            apiService.sendMessageWithContext(
                message = "Hello",
                conversationId = null,
                userContext = null,
            )
        }

        assertNotNull(response)
        assertNotNull(response.conversationId)
        assertTrue(response.conversationId.isNotBlank())
        assertNotNull(response.message)
        assertTrue(response.message.isNotBlank())
        println("TEST 1 Basic Connection Response: ${response.message}")
    }

    @Test
    fun test2_ragQuestion_receivesAnswerAndParsesSources() = runBlocking {
        val response = withRetry {
            apiService.sendMessageWithContext(
                message = "What is a calorie deficit?",
                conversationId = null,
                userContext = RepsAiApiService.UserContext(
                    goal = Goal.CUT.toRepsAiGoal(),
                ),
            )
        }

        assertNotNull(response)
        assertTrue("Expected non-empty response", response.message.isNotBlank())
        println("TEST 2 RAG Response: ${response.message}")
        println("TEST 2 Sources count: ${response.sources.size}")
        for (source in response.sources) {
            println("  Source: ${source.title} (${source.source}) topic=${source.topic}")
        }
    }

    @Test
    fun test3_nutritionToolQuestion_calculatesMacros() = runBlocking {
        val response = withRetry {
            apiService.sendMessageWithContext(
                message = "Calculate the macros for 200g of chicken breast.",
                conversationId = null,
                userContext = null,
            )
        }

        assertNotNull(response)
        assertTrue(response.message.isNotBlank())
        println("TEST 3 Nutrition Tool Response: ${response.message}")
        println("TEST 3 Response Type: ${response.type}")
        println("TEST 3 Data: ${response.data}")
    }

    @Test
    fun test4_and_5_conversationPersistenceAndNewConversation() = runBlocking {
        // First turn
        val turn1 = withRetry {
            apiService.sendMessageWithContext(
                message = "My goal is fat loss.",
                conversationId = null,
                userContext = RepsAiApiService.UserContext(
                    goal = Goal.CUT.toRepsAiGoal(),
                ),
            )
        }
        val convId = turn1.conversationId
        assertNotNull(convId)
        assertTrue(convId.isNotBlank())
        println("TEST 4 Turn 1 ConvId: $convId | Message: ${turn1.message}")

        // Second turn with same conversation_id
        val turn2 = withRetry {
            apiService.sendMessageWithContext(
                message = "What should I focus on?",
                conversationId = convId,
                userContext = RepsAiApiService.UserContext(
                    goal = Goal.CUT.toRepsAiGoal(),
                ),
            )
        }
        assertEquals("Expected conversation_id to be preserved across multi-turn chat", convId, turn2.conversationId)
        println("TEST 4 Turn 2 ConvId: ${turn2.conversationId} | Message: ${turn2.message}")

        // TEST 5: New conversation (omitting conversationId)
        val newConvTurn = withRetry {
            apiService.sendMessageWithContext(
                message = "How much protein in an egg?",
                conversationId = null,
                userContext = null,
            )
        }
        assertNotNull(newConvTurn.conversationId)
        assertNotEquals("New conversation should have a different conversationId", convId, newConvTurn.conversationId)
        println("TEST 5 New ConvId: ${newConvTurn.conversationId} | Message: ${newConvTurn.message}")
    }

    @Test
    fun test6_backendOffline_handlesGracefully() = runBlocking {
        val offlineService = RepsAiApiService("http://127.0.0.1:9999") // non-existent port
        val status = offlineService.checkHealthStatus()
        assertEquals(RepsAiApiService.HealthStatus.UNAVAILABLE, status)

        var caughtNetworkException = false
        try {
            offlineService.sendMessageWithContext("Hello")
        } catch (e: Exception) {
            caughtNetworkException = true
        }
        assertTrue("Expected network exception when backend is offline", caughtNetworkException)
    }
}
