package com.reps.app.ai

import com.reps.app.domain.model.AssistantError
import com.reps.app.domain.model.AssistantResult
import com.reps.app.domain.model.Goal
import com.reps.app.domain.model.NutritionAnalysis
import com.reps.app.domain.repository.NutritionAssistantRepository
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.IOException
import java.util.concurrent.TimeUnit
import okhttp3.*
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager
import kotlin.math.abs
import kotlin.math.significantDigits

class RepsAiApiService @Inject constructor() {
    companion object {
        const val DEFAULT_BASE_URL = "http://127.0.0.1:8000"
        const val HEALTH_ENDPOINT = "/health"
        const val CHAT_ENDPOINT = "/api/v1/chat"
        const val CONNECT_TIMEOUT_SECONDS = 10
        const val READ_TIMEOUT_SECONDS = 30
    }

    @Volatile
    var baseUrl: String = DEFAULT_BASE_URL
        get() = _baseUrl
        set(value) {
            _baseUrl = value
            client.baseUrl(value)
        }

    private var _baseUrl: String = DEFAULT_BASE_URL
    private val client: OkHttpClient

    private val GSON = com.google.gson.Gson()

    @Inject
    init {
        // Trust all SSL certificates for local development testing
        val trustAllManager = object : X509TrustManager() {
            override fun checkClientCertificate(x509Certificate: java.security.certificate.X509Certificate?) {}
            override fun checkServerTrusted(
                chain: java.security.certificate.X509Certificate[],
                authType: String,
            ) {}
            override fun checkServerTrusted(chain: Array<java.security.certificate.X509Certificate>, authType: String) {}
        }

        val sslContext = try {
            val sslContext = SSLContext.getInstance("TLS")
            sslContext.init(null, arrayOf<TrustManager>(trustAllManager), null)
            sslContext
        } catch (e: Exception) {
            throw RuntimeException("Failed to initialize SSL context", e)
        }

        client = OkHttpClient.Builder()
            .sslSocketFactory(sslContext.socketFactory, trustAllManager)
            .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .build()
    }

    suspend fun healthCheck(): HealthResponse {
        val request = Request.Builder()
            .url(baseUrl + HEALTH_ENDPOINT)
            .get()
            .build()

        suspendCancellableCoroutine { cont ->
            client.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    cont.resumeWithException(
                        IOException("AI service unreachable: ${e.message}")
                    )
                }

                override fun onResponse(call: Call, response: Response) {
                    if (!response.isSuccessful) {
                        cont.resumeWithException(
                            IOException("AI service returned ${response.code}")
                        )
                        return
                    }
                    val body = response.body?.string()
                    if (body.isNullOrEmpty()) {
                        cont.resumeWithException(IOException("Empty response"))
                        return
                    }
                    try {
                        val health = GSON.fromJson(body, HealthResponse::class.java)
                        cont.resume(health)
                    } catch (e: Exception) {
                        cont.resumeWithException(e)
                    }
                }
            })
        }
    }

    data class HealthResponse(
        val status: String,
        val service: String,
        val version: String,
    )

    suspend fun sendMessage(request: ChatRequest): ChatResponse {
        val json = GSON.toJson(request)
        val body = RequestBody.create(
            json,
            MediaType.get("application/json; charset=utf-8")
        )

        val chatRequestUrl = baseUrl + CHAT_ENDPOINT
        val request = Request.Builder()
            .url(chatRequestUrl)
            .post(body)
            .build()

        suspendCancellableCoroutine { cont ->
            client.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    cont.resumeWithException(
                        IOException("Network error contacting AI service: ${e.message}")
                    )
                }

                override fun onResponse(call: Call, response: Response) {
                    val code = response.code
                    if (code == 200) {
                        val responseBody = response.body?.string()
                        if (responseBody.isNullOrEmpty()) {
                            cont.resumeWithException(IOException("Empty response"))
                            return
                        }
                        try {
                            val chatResponse = GSON.fromJson(responseBody, ChatResponse::class.java)
                            cont.resume(chatResponse)
                        } catch (e: Exception) {
                            cont.resumeWithException(e)
                        }
                    } else if (code == 413) {
                        cont.resumeWithException(
                            AssistantResult.Failure(
                                AssistantError.ModelUnavailable
                            )
                        )
                    } else if (code == 422) {
                        cont.resumeWithException(
                            AssistantResult.Failure(
                                AssistantError.Unknown
                            )
                        )
                    } else if (code == 429) {
                        cont.resumeWithException(
                            AssistantResult.Failure(
                                AssistantError.RateLimited
                            )
                        )
                    } else if (code in 502..503) {
                        cont.resumeWithException(
                            AssistantResult.Failure(
                                AssistantError.ModelUnavailable
                            )
                        )
                    } else {
                        cont.resumeWithException(
                            IOException("AI service error: $code")
                        )
                    }
                }
            })
        }
    }

    data class ChatRequest(
        val message: String,
        val conversationId: String? = null,
        val userContext: UserContext? = null,
    )

    data class UserContext(
        val goal: Goal? = null,
        val weightKg: Double? = null,
        val heightCm: Double? = null,
        val ageYears: Int? = null,
        val calorieTarget: Double? = null,
        val proteinTarget: Double? = null,
        val carbsTarget: Double? = null,
        val fatTarget: Double? = null,
        val caloriesConsumed: Double? = null,
        val proteinConsumed: Double? = null,
    )

    suspend fun sendMessageWithContext(
        message: String,
        conversationId: String? = null,
        userContext: UserContext? = null,
    ): ChatResponse {
        val request = ChatRequest(
            message = message,
            conversationId = conversationId,
            userContext = userContext,
        )
        return sendMessage(request)
    }

    data class ChatResponse(
        val conversationId: String,
        val message: String,
        val `type`: String = "general",
        val data: Map<String, Any> = emptyMap(),
        val sources: List<SourceRef> = emptyList(),
    )

    data class SourceRef(
        val documentId: String? = null,
        val title: String? = null,
        val source: String? = null,
        val sourceUrl: String? = null,
        val topic: String? = null,
        val section: String? = null,
    )
}