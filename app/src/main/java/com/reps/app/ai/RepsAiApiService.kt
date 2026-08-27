package com.reps.app.ai

import com.reps.app.domain.model.Goal
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class RepsAiApiService {
    companion object {
        const val DEFAULT_BASE_URL = "http://127.0.0.1:8000"
        const val HEALTH_ENDPOINT = "/health"
        const val CHAT_ENDPOINT = "/api/v1/chat"
        const val CONNECT_TIMEOUT_SECONDS = 10L
        const val READ_TIMEOUT_SECONDS = 30L
    }

    @Volatile
    var baseUrl: String = DEFAULT_BASE_URL

    private val client: OkHttpClient

    private val GSON = com.google.gson.Gson()

    init {
        val trustAllManager = object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<java.security.cert.X509Certificate>, authType: String) {}
            override fun checkServerTrusted(chain: Array<java.security.cert.X509Certificate>, authType: String) {}
            override fun getAcceptedIssuers(): Array<java.security.cert.X509Certificate> = emptyArray()
        }

        val sslContext = try {
            SSLContext.getInstance("TLS").also {
                it.init(null, arrayOf<TrustManager>(trustAllManager), null)
            }
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

        return suspendCancellableCoroutine { cont ->
            val call = client.newCall(request)
            cont.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    cont.resumeWithException(IOException("AI service unreachable: ${e.message}"))
                }

                override fun onResponse(call: Call, response: Response) {
                    if (!response.isSuccessful) {
                        cont.resumeWithException(IOException("AI service returned ${response.code}"))
                        return
                    }
                    val body = response.body?.string()
                    if (body.isNullOrEmpty()) {
                        cont.resumeWithException(IOException("Empty response"))
                        return
                    }
                    try {
                        cont.resume(GSON.fromJson(body, HealthResponse::class.java))
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
        val body = json.toRequestBody("application/json; charset=utf-8".toMediaType())
        val httpRequest = Request.Builder()
            .url(baseUrl + CHAT_ENDPOINT)
            .post(body)
            .build()

        return suspendCancellableCoroutine { cont ->
            val call = client.newCall(httpRequest)
            cont.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    cont.resumeWithException(IOException("Network error contacting AI service: ${e.message}"))
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
                            cont.resume(GSON.fromJson(responseBody, ChatResponse::class.java))
                        } catch (e: Exception) {
                            cont.resumeWithException(e)
                        }
                    } else {
                        cont.resumeWithException(IOException("AI service error: $code"))
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
        return sendMessage(
            ChatRequest(
                message = message,
                conversationId = conversationId,
                userContext = userContext,
            )
        )
    }

    data class ChatResponse(
        val conversationId: String,
        val message: String,
        val type: String = "general",
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
