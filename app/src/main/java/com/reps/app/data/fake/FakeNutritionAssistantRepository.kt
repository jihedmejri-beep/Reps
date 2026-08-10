package com.reps.app.data.fake

import com.reps.app.domain.model.AnalysedItem
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
import kotlinx.coroutines.delay
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * An offline stand-in for the assistant, in the same spirit as the other fakes:
 * enough real behaviour that the screen exercises the paths it will use against
 * the backend.
 *
 * It reproduces the part of the flow that actually shapes the UI - the agent
 * asking for a missing quantity, then accepting it on a later turn - so the
 * multi-turn chat can be built and clicked through with no deployed functions,
 * no Groq key, and no USDA key.
 */
@Singleton
class FakeNutritionAssistantRepository @Inject constructor() : NutritionAssistantRepository {

    override suspend fun understand(
        history: List<AssistantExchange>,
        message: String,
    ): AssistantResult<UnderstandingResponse> {
        delay(700) // stand in for a model call, so the typing indicator is visible

        val named = FOODS.filter { (keyword, _) -> message.contains(keyword, ignoreCase = true) }
        if (named.isEmpty()) {
            return AssistantResult.Success(
                UnderstandingResponse(
                    message = "I didn't catch a food in that - what did you eat?",
                    draft = MealDraft(followUpQuestions = listOf("What did you eat?")),
                ),
            )
        }

        // Any number in the message is read as the portion for everything named
        // so far. Crude, but it is what makes the second turn behave like the
        // real agent accepting an answer.
        val grams = Regex("""\d+""").find(message)?.value?.toDoubleOrNull()
        val ingredients = named.map { (keyword, food) ->
            DraftIngredient(
                name = food.usdaName,
                rawText = keyword,
                grams = grams,
                quantityText = grams?.let { "${it.toInt()}g" },
                cookingMethod = null,
            )
        }

        val ready = grams != null
        return AssistantResult.Success(
            UnderstandingResponse(
                message = if (ready) {
                    "Got it - ${ingredients.joinToString { it.rawText }}. Let me work that out."
                } else {
                    "Roughly how much ${ingredients.first().rawText} did you have?"
                },
                draft = MealDraft(
                    mealName = ingredients.joinToString(" and ") { it.rawText }
                        .replaceFirstChar { it.uppercase() },
                    ingredients = ingredients,
                    readyForAnalysis = ready,
                    confidence = if (ready) 0.92 else 0.55,
                    followUpQuestions = if (ready) {
                        emptyList()
                    } else {
                        listOf("How much ${ingredients.first().rawText}?")
                    },
                ),
            ),
        )
    }

    override suspend fun analyseAndCoach(
        draft: MealDraft,
        goal: Goal,
    ): AssistantResult<CoachedAnalysis> {
        delay(900)

        val items = draft.ingredients.mapNotNull { ingredient ->
            val grams = ingredient.grams ?: return@mapNotNull null
            val food = FOODS.entries.firstOrNull { (keyword, _) ->
                ingredient.rawText.contains(keyword, ignoreCase = true)
            }?.value ?: return@mapNotNull null

            AnalysedItem(
                foodItem = FoodItem(
                    id = UUID.randomUUID().toString(),
                    name = ingredient.rawText,
                    grams = grams,
                    caloriesPer100g = food.calories,
                    proteinPer100g = food.protein,
                    carbsPer100g = food.carbs,
                    fatPer100g = food.fat,
                ),
                fdcId = food.fdcId,
                matchedDescription = food.usdaName,
            )
        }

        val analysis = NutritionAnalysis(
            mealName = draft.mealName ?: "Meal",
            items = items,
            micros = Micros(fiber = 2.4, sugar = 1.1, sodiumMg = 320.0),
            unmatched = emptyList(),
            fromCache = false,
        )

        val goalNote = when (goal) {
            Goal.BULK -> "That supports the surplus you're aiming for."
            Goal.CUT -> "That fits a deficit day without leaving you short on protein."
            Goal.MAINTAIN -> "That sits comfortably in your maintenance range."
        }

        return AssistantResult.Success(
            CoachedAnalysis(
                analysis = analysis,
                coaching = "This is a solid, protein-forward meal - good for recovery after " +
                    "training. $goalNote\n\nIf you want a bit more staying power, adding a " +
                    "vegetable would bring fibre up without moving the calories much.",
            ),
        )
    }

    override suspend fun ask(
        history: List<AssistantExchange>,
        question: String,
        goal: Goal,
    ): AssistantResult<String> {
        delay(600)
        return AssistantResult.Success(
            "Good question. In general, spreading protein across the day works better than " +
                "putting it all in one meal - aim for a serving at each. Log a meal and I can " +
                "give you numbers rather than generalities.",
        )
    }

    private data class Food(
        val usdaName: String,
        val fdcId: Long,
        val calories: Double,
        val protein: Double,
        val carbs: Double,
        val fat: Double,
    )

    private companion object {
        /** Per-100g values, matching how USDA publishes and how [FoodItem] stores. */
        val FOODS = mapOf(
            "chicken" to Food("Chicken, breast, roasted", 171077, 165.0, 31.0, 0.0, 3.6),
            "rice" to Food("Rice, white, cooked", 169756, 130.0, 2.7, 28.2, 0.3),
            "egg" to Food("Egg, whole, cooked", 173424, 155.0, 13.0, 1.1, 11.0),
            "pasta" to Food("Pasta, cooked", 168927, 158.0, 5.8, 30.9, 0.9),
            "salmon" to Food("Fish, salmon, cooked", 175168, 206.0, 22.1, 0.0, 12.4),
            "broccoli" to Food("Broccoli, cooked", 170379, 35.0, 2.4, 7.2, 0.4),
            "couscous" to Food("Couscous, cooked", 168821, 112.0, 3.8, 23.2, 0.2),
            "banana" to Food("Banana, raw", 173944, 89.0, 1.1, 22.8, 0.3),
        )
    }
}
