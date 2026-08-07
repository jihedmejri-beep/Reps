package com.reps.app.data.remote

import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.FirebaseFunctionsException
import com.reps.app.domain.model.AnalysedItem
import com.reps.app.domain.model.AssistantError
import com.reps.app.domain.model.DraftIngredient
import com.reps.app.domain.model.FoodItem
import com.reps.app.domain.model.Goal
import com.reps.app.domain.model.MealDraft
import com.reps.app.domain.model.Micros
import com.reps.app.domain.model.NutritionAnalysis
import com.reps.app.domain.repository.AssistantExchange
import com.reps.app.domain.repository.AssistantResult
import com.reps.app.domain.repository.CoachedAnalysis
import com.reps.app.domain.repository.NutritionAssistantRepository
import com.reps.app.domain.repository.UnderstandingResponse
import kotlinx.coroutines.tasks.await
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Talks to the Cloud Functions in /functions.
 *
 * Callables hand back plain maps and lists, so the parsing is done by hand
 * rather than by pulling in a JSON library the project does not otherwise use.
 * Every read goes through the helpers at the bottom, which treat a missing or
 * wrongly-typed field as absent instead of throwing - a backend that adds a
 * field should not crash an app that has not shipped support for it yet.
 */
@Singleton
class FunctionsNutritionAssistantRepository @Inject constructor(
    private val functions: FirebaseFunctions,
) : NutritionAssistantRepository {

    override suspend fun understand(
        history: List<AssistantExchange>,
        message: String,
    ): AssistantResult<UnderstandingResponse> = call(
        name = "understandMeal",
        payload = mapOf("history" to history.toWire(), "message" to message),
    ) { data ->
        UnderstandingResponse(
            message = data.string("message").orEmpty(),
            draft = data.map("draft").toMealDraft(),
        )
    }

    override suspend fun analyseAndCoach(
        draft: MealDraft,
        goal: Goal,
    ): AssistantResult<CoachedAnalysis> = call(
        name = "analyseMealAndCoach",
        payload = mapOf("draft" to draft.toWire(), "goal" to goal.name),
    ) { data ->
        CoachedAnalysis(
            analysis = data.map("analysis").toAnalysis(),
            coaching = data.string("coaching").orEmpty(),
        )
    }

    override suspend fun ask(
        history: List<AssistantExchange>,
        question: String,
        goal: Goal,
    ): AssistantResult<String> = call(
        name = "askCoach",
        payload = mapOf(
            "history" to history.toWire(),
            "question" to question,
            "goal" to goal.name,
        ),
    ) { data ->
        data.string("message").orEmpty()
    }

    private suspend fun <T> call(
        name: String,
        payload: Map<String, Any?>,
        parse: (Map<*, *>) -> T,
    ): AssistantResult<T> = try {
        val result = functions.getHttpsCallable(name).call(payload).await()
        val data = result.getData() as? Map<*, *>
            ?: return AssistantResult.Failure(AssistantError.Unknown)
        AssistantResult.Success(parse(data))
    } catch (e: FirebaseFunctionsException) {
        AssistantResult.Failure(e.toAssistantError())
    } catch (e: IOException) {
        AssistantResult.Failure(AssistantError.Network)
    }
}

/**
 * The backend names the cause in `details.reason`, which is more specific than
 * the transport-level code. The code is the fallback for failures that never
 * reached our handler.
 */
private fun FirebaseFunctionsException.toAssistantError(): AssistantError {
    val reason = (details as? Map<*, *>)?.get("reason") as? String
    if (reason != null) {
        AssistantError.entries.firstOrNull { it.name == reason }?.let { return it }
    }
    return when (code) {
        FirebaseFunctionsException.Code.UNAUTHENTICATED,
        FirebaseFunctionsException.Code.PERMISSION_DENIED,
        -> AssistantError.NotSignedIn

        FirebaseFunctionsException.Code.RESOURCE_EXHAUSTED -> AssistantError.RateLimited
        FirebaseFunctionsException.Code.UNAVAILABLE -> AssistantError.ModelUnavailable
        FirebaseFunctionsException.Code.DEADLINE_EXCEEDED -> AssistantError.Network
        FirebaseFunctionsException.Code.NOT_FOUND -> AssistantError.FoodDatabaseUnavailable
        else -> AssistantError.Unknown
    }
}

// ---- outbound ----

private fun List<AssistantExchange>.toWire(): List<Map<String, Any?>> =
    map { mapOf("fromUser" to it.fromUser, "text" to it.text) }

private fun MealDraft.toWire(): Map<String, Any?> = mapOf(
    "mealName" to mealName,
    "ingredients" to ingredients.map {
        mapOf(
            "name" to it.name,
            "rawText" to it.rawText,
            "grams" to it.grams,
            "quantityText" to it.quantityText,
            "cookingMethod" to it.cookingMethod,
        )
    },
    "readyForAnalysis" to readyForAnalysis,
    "confidence" to confidence,
    "followUpQuestions" to followUpQuestions,
    "ambiguities" to ambiguities,
)

// ---- inbound ----

private fun Map<*, *>?.toMealDraft(): MealDraft {
    if (this == null) return MealDraft()
    return MealDraft(
        mealName = string("mealName"),
        ingredients = list("ingredients").mapNotNull { entry ->
            val item = entry as? Map<*, *> ?: return@mapNotNull null
            DraftIngredient(
                name = item.string("name").orEmpty(),
                rawText = item.string("rawText").orEmpty(),
                grams = item.double("grams"),
                quantityText = item.string("quantityText"),
                cookingMethod = item.string("cookingMethod"),
            )
        },
        readyForAnalysis = boolean("readyForAnalysis") ?: false,
        confidence = double("confidence") ?: 0.0,
        followUpQuestions = list("followUpQuestions").filterIsInstance<String>(),
        ambiguities = list("ambiguities").filterIsInstance<String>(),
    )
}

private fun Map<*, *>?.toAnalysis(): NutritionAnalysis {
    if (this == null) return NutritionAnalysis()
    return NutritionAnalysis(
        mealName = string("mealName").orEmpty(),
        items = list("items").mapNotNull { entry ->
            val item = entry as? Map<*, *> ?: return@mapNotNull null
            val food = item.map("foodItem") ?: return@mapNotNull null
            AnalysedItem(
                foodItem = FoodItem(
                    id = food.string("id").orEmpty(),
                    name = food.string("name").orEmpty(),
                    grams = food.double("grams") ?: 0.0,
                    caloriesPer100g = food.double("caloriesPer100g") ?: 0.0,
                    proteinPer100g = food.double("proteinPer100g") ?: 0.0,
                    carbsPer100g = food.double("carbsPer100g") ?: 0.0,
                    fatPer100g = food.double("fatPer100g") ?: 0.0,
                ),
                fdcId = item.double("fdcId")?.toLong(),
                matchedDescription = item.string("matchedDescription").orEmpty(),
            )
        },
        micros = map("micros").toMicros(),
        unmatched = list("unmatched").filterIsInstance<String>(),
        fromCache = boolean("fromCache") ?: false,
    )
}

private fun Map<*, *>?.toMicros(): Micros {
    if (this == null) return Micros()
    return Micros(
        fiber = double("fiber"),
        sugar = double("sugar"),
        saturatedFat = double("saturatedFat"),
        sodiumMg = double("sodiumMg"),
        potassiumMg = double("potassiumMg"),
        calciumMg = double("calciumMg"),
        ironMg = double("ironMg"),
        vitaminCMg = double("vitaminCMg"),
        cholesterolMg = double("cholesterolMg"),
    )
}

private fun Map<*, *>.string(key: String): String? = this[key] as? String

private fun Map<*, *>.boolean(key: String): Boolean? = this[key] as? Boolean

/** Callables decode JSON numbers as Int, Long, or Double depending on the value. */
private fun Map<*, *>.double(key: String): Double? = (this[key] as? Number)?.toDouble()

private fun Map<*, *>.map(key: String): Map<*, *>? = this[key] as? Map<*, *>

private fun Map<*, *>.list(key: String): List<*> = this[key] as? List<*> ?: emptyList<Any?>()
