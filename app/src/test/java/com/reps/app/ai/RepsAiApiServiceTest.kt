package com.reps.app.ai

import com.google.gson.FieldNamingPolicy
import com.google.gson.GsonBuilder
import com.reps.app.domain.model.Goal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RepsAiApiServiceTest {

    private val gson = GsonBuilder()
        .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
        .create()

    @Test
    fun goalMapping_mapsCorrectlyToBackendAllowedValues() {
        assertEquals("muscle_gain", Goal.BULK.toRepsAiGoal())
        assertEquals("fat_loss", Goal.CUT.toRepsAiGoal())
        assertEquals("maintenance", Goal.MAINTAIN.toRepsAiGoal())
    }

    @Test
    fun chatRequest_serializesToSnakeCaseMatchingRepsAiContract() {
        val request = RepsAiApiService.ChatRequest(
            message = "What is a calorie deficit?",
            conversationId = "conv-12345",
            userContext = RepsAiApiService.UserContext(
                goal = Goal.CUT.toRepsAiGoal(),
                weightKg = 75.5,
                heightCm = 178.0,
                ageYears = 28,
                calorieTarget = 2100.0,
                proteinTarget = 160.0,
                carbsTarget = 200.0,
                fatTarget = 55.0,
                caloriesConsumed = 650.0,
                proteinConsumed = 45.0,
            ),
        )

        val json = gson.toJson(request)

        assertTrue(json.contains("\"message\":\"What is a calorie deficit?\""))
        assertTrue(json.contains("\"conversation_id\":\"conv-12345\""))
        assertTrue(json.contains("\"user_context\":{"))
        assertTrue(json.contains("\"goal\":\"fat_loss\""))
        assertTrue(json.contains("\"weight_kg\":75.5"))
        assertTrue(json.contains("\"height_cm\":178.0"))
        assertTrue(json.contains("\"age_years\":28"))
        assertTrue(json.contains("\"calorie_target\":2100.0"))
        assertTrue(json.contains("\"protein_target\":160.0"))
        assertTrue(json.contains("\"carbs_target\":200.0"))
        assertTrue(json.contains("\"fat_target\":55.0"))
        assertTrue(json.contains("\"calories_consumed\":650.0"))
        assertTrue(json.contains("\"protein_consumed\":45.0"))
    }

    @Test
    fun chatResponse_deserializesFromRepsAiJson() {
        val backendJson = """
            {
                "conversation_id": "conv-abcdef-123456",
                "message": "A calorie deficit occurs when you consume fewer calories than you expend.",
                "type": "nutrition_answer",
                "data": {"status": "ok"},
                "sources": [
                    {
                        "document_id": "doc-01",
                        "title": "Energy Balance",
                        "source": "knowledge/nutrition.md",
                        "source_url": null,
                        "topic": "energy_balance",
                        "section": "Deficit"
                    }
                ]
            }
        """.trimIndent()

        val response = gson.fromJson(backendJson, RepsAiApiService.ChatResponse::class.java)

        assertNotNull(response)
        assertEquals("conv-abcdef-123456", response.conversationId)
        assertTrue(response.message.startsWith("A calorie deficit"))
        assertEquals("nutrition_answer", response.type)
        assertEquals(1, response.sources.size)
        assertEquals("doc-01", response.sources[0].documentId)
        assertEquals("Energy Balance", response.sources[0].title)
        assertEquals("knowledge/nutrition.md", response.sources[0].source)
        assertEquals("energy_balance", response.sources[0].topic)
    }

    @Test
    fun healthResponse_deserializesFromRepsAiJson() {
        val backendJson = """
            {
                "status": "healthy",
                "service": "reps-ai",
                "version": "0.2.0"
            }
        """.trimIndent()

        val response = gson.fromJson(backendJson, RepsAiApiService.HealthResponse::class.java)

        assertNotNull(response)
        assertEquals("healthy", response.status)
        assertEquals("reps-ai", response.service)
        assertEquals("0.2.0", response.version)
    }
}
