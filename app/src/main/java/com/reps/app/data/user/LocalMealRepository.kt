package com.reps.app.data.user

import androidx.room.withTransaction
import com.reps.app.data.auth.UserSession
import com.reps.app.data.user.db.MealEntity
import com.reps.app.data.user.db.MealItemEntity
import com.reps.app.data.user.db.UserDataDatabase
import com.reps.app.data.user.db.toDomain
import com.reps.app.domain.model.Meal
import com.reps.app.domain.repository.MealRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The food diary. Meals persist as a parent row plus one row per ingredient;
 * saving replaces the item set inside one transaction so an edit of a single
 * ingredient is still atomic.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class LocalMealRepository @Inject constructor(
    private val session: UserSession,
    private val database: UserDataDatabase,
) : MealRepository {

    private val dao get() = database.mealDao()

    override fun observeMeals(date: LocalDate): Flow<List<Meal>> =
        session.uidFlow.flatMapLatest { uid ->
            if (uid == null) {
                flowOf(emptyList())
            } else {
                dao.observeForDate(uid, date.toString()).map { rows -> rows.map { it.toDomain() } }
            }
        }

    override suspend fun logMeal(meal: Meal) {
        val uid = session.currentUid ?: return
        val entity = MealEntity(
            id = meal.id,
            uid = uid,
            name = meal.name,
            dateIso = meal.date.toString(),
            createdAtMs = System.currentTimeMillis(),
        )
        val items = meal.foodItems.map { item ->
            MealItemEntity(
                mealId = meal.id,
                name = item.name,
                grams = item.grams,
                caloriesPer100g = item.caloriesPer100g,
                proteinPer100g = item.proteinPer100g,
                carbsPer100g = item.carbsPer100g,
                fatPer100g = item.fatPer100g,
            )
        }
        database.withTransaction {
            // Upsert-by-id: clear the old tree, write the new one.
            dao.deleteMealRow(meal.id)
            dao.insertMeal(entity)
            if (items.isNotEmpty()) dao.insertItems(items)
        }
    }

    override suspend fun deleteMeal(mealId: String) {
        val uid = session.currentUid ?: return
        dao.deleteMealRowScoped(mealId, uid)
    }
}
