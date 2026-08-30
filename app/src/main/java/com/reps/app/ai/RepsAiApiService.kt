package com.reps.app.ai

import com.google.gson.FieldNamingPolicy
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.annotations.SerializedName
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

/**
 * HTTP Client connecting the REPS Android Application to the RepsAI FastAPI Backend.
 *
 * Base URL Resolution:
 * - Default on Android Emulator: "http://10.0.2.2:8000" (routes to Windows host 127.0.0.1:8000)
 * - Can be overridden via system property `reps.ai.base_url` or at runtime via [baseUrl].
 */
class RepsAiApiService(
    initialBaseUrl: String? = null,
) {
    companion object {
        const val EMULATOR_BASE_URL = "http://10.0.2.2:8000"
        const val LOCALHOST_BASE_URL = "http://127.0.0.1:8000"
        const val DEFAULT_BASE_URL = EMULATOR_BASE_URL

        const val HEALTH_ENDPOINT = "/health"
        const val CHAT_ENDPOINT = "/api/v1/chat"
        const val CONNECT_TIMEOUT_SECONDS = 10L
        const val READ_TIMEOUT_SECONDS = 30L

        private fun logDebug(tag: String, message: String) {
            try {
                android.util.Log.d(tag, message)
            } catch (_: Throwable) {
                // In non-Android JVM environments (e.g. unit tests), fallback to standard output
                println("[$tag] $message")
            }
        }

        private fun logError(tag: String, message: String, throwable: Throwable? = null) {
            try {
                android.util.Log.e(tag, message, throwable)
            } catch (_: Throwable) {
                System.err.println("[$tag] $message ${throwable?.message.orEmpty()}")
            }
        }
    }

    @Volatile
    var baseUrl: String = initialBaseUrl
        ?: System.getProperty("reps.ai.base_url")
        ?: DEFAULT_BASE_URL

    private val client: OkHttpClient

    private val gson: Gson = GsonBuilder()
        .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
        .create()

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

    enum class HealthStatus {
        CONNECTED,
        UNAVAILABLE,
    }

    suspend fun checkHealthStatus(): HealthStatus {
        return try {
            val response = healthCheck()
            if (response.status.equals("healthy", ignoreCase = true)) {
                HealthStatus.CONNECTED
            } else {
                HealthStatus.UNAVAILABLE
            }
        } catch (_: Exception) {
            HealthStatus.UNAVAILABLE
        }
    }

    suspend fun healthCheck(): HealthResponse {
        val url = baseUrl.trimEnd('/') + HEALTH_ENDPOINT
        logDebug("RepsAi", "AI REQUEST: GET $HEALTH_ENDPOINT to $url")

        val request = Request.Builder()
            .url(url)
            .get()
            .build()

        return suspendCancellableCoroutine { cont ->
            val call = client.newCall(request)
            cont.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    logError("RepsAi", "AI HEALTH FAILURE: ${e.message}")
                    cont.resumeWithException(IOException("AI service unreachable: ${e.message}", e))
                }

                override fun onResponse(call: Call, response: Response) {
                    val code = response.code
                    logDebug("RepsAi", "AI HEALTH RESPONSE: HTTP $code")
                    if (!response.isSuccessful) {
                        cont.resumeWithException(IOException("AI service returned HTTP $code"))
                        return
                    }
                    val body = response.body?.string()
                    if (body.isNullOrEmpty()) {
                        cont.resumeWithException(IOException("Empty response from AI service"))
                        return
                    }
                    try {
                        val parsed = gson.fromJson(body, HealthResponse::class.java)
                        cont.resume(parsed)
                    } catch (e: Exception) {
                        logError("RepsAi", "AI HEALTH PARSE ERROR: ${e.message}")
                        cont.resumeWithException(e)
                    }
                }
            })
        }
    }

    suspend fun sendMessage(request: ChatRequest): ChatResponse {
        val json = gson.toJson(request)
        val url = baseUrl.trimEnd('/') + CHAT_ENDPOINT
        logDebug("RepsAi", "AI REQUEST: POST $CHAT_ENDPOINT (conversation_id=${request.conversationId ?: "new"})")

        val body = json.toRequestBody("application/json; charset=utf-8".toMediaType())
        val httpRequest = Request.Builder()
            .url(url)
            .post(body)
            .build()

        return suspendCancellableCoroutine { cont ->
            val call = client.newCall(httpRequest)
            cont.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    logError("RepsAi", "AI REQUEST NETWORK ERROR: ${e.message}")
                    cont.resumeWithException(IOException("Network error contacting AI service: ${e.message}", e))
                }

                override fun onResponse(call: Call, response: Response) {
                    val code = response.code
                    logDebug("RepsAi", "AI RESPONSE: HTTP $code")
                    val responseBody = response.body?.string()

                    if (response.isSuccessful) {
                        if (responseBody.isNullOrEmpty()) {
                            cont.resumeWithException(IOException("Empty response body from AI service"))
                            return
                        }
                        try {
                            val chatResponse = gson.fromJson(responseBody, ChatResponse::class.java)
                            logDebug(
                                "RepsAi",
                                "AI RESPONSE SUCCESS: type=${chatResponse.type} sources=${chatResponse.sources.size} conv=${chatResponse.conversationId}"
                            )
                            cont.resume(chatResponse)
                        } catch (e: Exception) {
                            logError("RepsAi", "AI RESPONSE JSON PARSE ERROR: ${e.message}")
                            cont.resumeWithException(e)
                        }
                    } else {
                        val errorDetail = when (code) {
                            400 -> "Bad Request (400)"
                            413 -> "Request payload too large (413)"
                            422 -> "Validation error (422): $responseBody"
                            429 -> "Rate limit exceeded (429)"
                            502 -> "Upstream AI provider gateway error (502)"
                            503 -> "Upstream AI provider unavailable (503)"
                            500 -> "Internal AI service error (500)"
                            else -> "AI service error ($code)"
                        }
                        logError("RepsAi", "AI SERVICE ERROR: $errorDetail")
                        cont.resumeWithException(RepsAiApiException(code, errorDetail))
                    }
                }
            })
        }
    }

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

    data class HealthResponse(
        @SerializedName("status") val status: String,
        @SerializedName("service") val service: String,
        @SerializedName("version") val version: String,
    )

    data class ChatRequest(
        @SerializedName("message") val message: String,
        @SerializedName("conversation_id") val conversationId: String? = null,
        @SerializedName("user_context") val userContext: UserContext? = null,
    )

    data class UserContext(
        @SerializedName("goal") val goal: String? = null,
        @SerializedName("weight_kg") val weightKg: Double? = null,
        @SerializedName("height_cm") val heightCm: Double? = null,
        @SerializedName("age_years") val ageYears: Int? = null,
        @SerializedName("calorie_target") val calorieTarget: Double? = null,
        @SerializedName("protein_target") val proteinTarget: Double? = null,
        @SerializedName("carbs_target") val carbsTarget: Double? = null,
        @SerializedName("fat_target") val fatTarget: Double? = null,
        @SerializedName("calories_consumed") val caloriesConsumed: Double? = null,
        @SerializedName("protein_consumed") val proteinConsumed: Double? = null,
        @SerializedName("calories_remaining") val caloriesRemaining: Int? = null,
        @SerializedName("protein_remaining") val proteinRemaining: Int? = null,
    )

    data class ChatResponse(
        @SerializedName("conversation_id") val conversationId: String,
        @SerializedName("message") val message: String,
        @SerializedName("type") val type: String = "general",
        @SerializedName("data") val data: Map<String, Any> = emptyMap(),
        @SerializedName("sources") val sources: List<SourceRef> = emptyList(),
    )

    data class SourceRef(
        @SerializedName("document_id") val documentId: String? = null,
        @SerializedName("title") val title: String? = null,
        @SerializedName("source") val source: String? = null,
        @SerializedName("source_url") val sourceUrl: String? = null,
        @SerializedName("topic") val topic: String? = null,
        @SerializedName("section") val section: String? = null,
    )
}

class RepsAiApiException(
    val statusCode: Int,
    message: String,
) : IOException("HTTP $statusCode: $message")

fun Goal.toRepsAiGoal(): String = when (this) {
    Goal.BULK -> "muscle_gain"
    Goal.CUT -> "fat_loss"
    Goal.MAINTAIN -> "maintenance"
}
