package com.reps.app.ai

import com.reps.app.data.ai.RepsAiNutritionAssistantRepository
import com.reps.app.domain.model.AssistantError
import com.reps.app.domain.model.Meal
import com.reps.app.domain.model.User
import com.reps.app.domain.model.WeightEntry
import com.reps.app.domain.repository.AssistantResult
import com.reps.app.domain.repository.MealRepository
import com.reps.app.domain.repository.UserRepository
import com.reps.app.domain.repository.WeightRepository
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.net.InetSocketAddress
import java.time.LocalDate
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * The client and repository against a real HTTP server on localhost, so status
 * mapping, timeouts and cancellation are exercised through OkHttp rather than
 * assumed.
 */
class RepsAiApiServiceHttpTest {

    private lateinit var server: HttpServer

    /** What the next request gets: status code, body, and a delay before answering. */
    @Volatile private var status = 200
    @Volatile private var body = OK_BODY
    @Volatile private var delayMs = 0L
    private val release = CountDownLatch(1)

    @Before
    fun startServer() {
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext(RepsAiApiService.CHAT_ENDPOINT) { exchange ->
            exchange.requestBody.readBytes()
            if (delayMs > 0) release.await(delayMs, TimeUnit.MILLISECONDS)
            val bytes = body.toByteArray()
            runCatching {
                exchange.sendResponseHeaders(status, bytes.size.toLong())
                exchange.responseBody.use { it.write(bytes) }
            }
            exchange.close()
        }
        server.executor = java.util.concurrent.Executors.newCachedThreadPool()
        server.start()
    }

    @After
    fun stopServer() {
        release.countDown()
        server.stop(0)
    }

    private fun service(readTimeoutMs: Long = 5_000) = RepsAiApiService(
        initialBaseUrl = "http://127.0.0.1:${server.address.port}",
        readTimeoutMs = readTimeoutMs,
        callTimeoutMs = readTimeoutMs + 2_000,
    )

    private fun repository(api: RepsAiApiService) = RepsAiNutritionAssistantRepository(
        apiService = api,
        userRepository = object : UserRepository {
            override fun observeUser(): Flow<User?> = flowOf(null)
            override suspend fun updateUser(user: User) = Unit
        },
        weightRepository = object : WeightRepository {
            override fun observeEntries(): Flow<List<WeightEntry>> = flowOf(emptyList())
            override suspend fun logWeight(entry: WeightEntry) = Unit
            override suspend fun deleteEntry(entryId: String) = Unit
        },
        mealRepository = object : MealRepository {
            override fun observeMeals(date: LocalDate): Flow<List<Meal>> = flowOf(emptyList())
            override suspend fun logMeal(meal: Meal) = Unit
            override suspend fun deleteMeal(mealId: String) = Unit
        },
    )

    private fun understand(api: RepsAiApiService, message: String = "hi") = runBlocking {
        repository(api).understand(emptyList(), message, null)
    }

    private fun errorOf(result: AssistantResult<*>): AssistantError =
        (result as? AssistantResult.Failure)?.error ?: error("expected failure, got $result")

    @Test
    fun success_parsesAnswerAndConversationId() {
        val result = understand(service()) as AssistantResult.Success
        assertEquals("Eat more protein.", result.value.message)
        assertEquals("conv-1", result.value.conversationId)
    }

    @Test
    fun twoSequentialCalls_bothSucceed() {
        val api = service()
        assertTrue(understand(api, "one") is AssistantResult.Success)
        assertTrue(understand(api, "two") is AssistantResult.Success)
    }

    @Test
    fun serviceUnavailable_mapsToModelUnavailable() {
        status = 503
        body = """{"detail":"down"}"""
        assertEquals(AssistantError.ModelUnavailable, errorOf(understand(service())))
    }

    @Test
    fun rateLimited_mapsToRateLimited() {
        status = 429
        body = """{"detail":"slow down"}"""
        assertEquals(AssistantError.RateLimited, errorOf(understand(service())))
    }

    @Test
    fun internalError_mapsToUnknown() {
        status = 500
        body = """{"detail":"boom"}"""
        assertEquals(AssistantError.Unknown, errorOf(understand(service())))
    }

    @Test
    fun missingMessageField_isAFailureNotACrash() {
        body = """{"conversation_id":"conv-1"}"""
        assertEquals(AssistantError.Network, errorOf(understand(service())))
    }

    @Test
    fun slowServer_mapsToTimeout() {
        delayMs = 3_000
        assertEquals(AssistantError.Timeout, errorOf(understand(service(readTimeoutMs = 300))))
    }

    @Test
    fun unreachableServer_mapsToNetwork() {
        val port = server.address.port
        server.stop(0)
        val api = RepsAiApiService(initialBaseUrl = "http://127.0.0.1:$port", connectTimeoutMs = 1_000)
        assertEquals(AssistantError.Network, errorOf(understand(api)))
    }

    @Test
    fun blankBaseUrl_reportsNotConfigured_withoutCallingOut() {
        assertEquals(AssistantError.NotConfigured, errorOf(understand(RepsAiApiService(initialBaseUrl = ""))))
    }

    @Test
    fun overlongMessage_isRejectedBeforeSending() {
        val text = "x".repeat(RepsAiApiService.MAX_MESSAGE_LENGTH + 1)
        assertEquals(AssistantError.MessageTooLong, errorOf(understand(service(), text)))
    }

    @Test
    fun cancellation_propagatesInsteadOfBecomingAFailure() = runBlocking {
        delayMs = 10_000
        val repo = repository(service(readTimeoutMs = 10_000))
        val call = async { repo.understand(emptyList(), "hi", null) }
        kotlinx.coroutines.delay(200)
        call.cancel()
        val outcome = runCatching { withTimeout(2_000) { call.await() } }
        assertTrue(
            "expected cancellation, got ${outcome.getOrNull() ?: outcome.exceptionOrNull()}",
            outcome.exceptionOrNull() is kotlinx.coroutines.CancellationException,
        )
    }

    private companion object {
        const val OK_BODY = """{"conversation_id":"conv-1","message":"Eat more protein.","type":"general","data":{},"sources":[]}"""
    }
}
