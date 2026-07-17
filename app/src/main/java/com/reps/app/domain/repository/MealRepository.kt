package com.reps.app.domain.repository

import com.reps.app.domain.model.Meal
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

interface MealRepository {
    fun observeMeals(date: LocalDate): Flow<List<Meal>>
    suspend fun logMeal(meal: Meal)
    suspend fun deleteMeal(mealId: String)
}
