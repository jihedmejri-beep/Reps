package com.reps.app.feature.nutrition

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.reps.app.core.util.NutritionTargetsCalculator
import com.reps.app.data.datastore.UserPreferencesDataStore
import com.reps.app.domain.model.FoodItem
import com.reps.app.domain.model.Macros
import com.reps.app.domain.model.Meal
import com.reps.app.domain.repository.MealRepository
import com.reps.app.domain.repository.UserRepository
import com.reps.app.domain.repository.WeightRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject

private const val WATER_TARGET_GLASSES = 8

data class NutritionUiState(
    val meals: List<Meal> = emptyList(),
    val target: Macros = NutritionTargetsCalculator.fallback(),
    val waterGlasses: Int = 0,
    val waterTargetGlasses: Int = WATER_TARGET_GLASSES,
    val loading: Boolean = true,
) {
    val totals: Macros get() = meals.fold(Macros()) { acc, meal -> acc + meal.macros }
}

@HiltViewModel
class NutritionViewModel @Inject constructor(
    private val mealRepository: MealRepository,
    private val weightRepository: WeightRepository,
    private val userRepository: UserRepository,
    private val preferences: UserPreferencesDataStore,
) : ViewModel() {

    private val today = LocalDate.now()

    /** Water is per-day device state in DataStore; the key is the date, so it resets naturally. */
    private val waterGlasses = MutableStateFlow(0)

    init {
        viewModelScope.launch {
            waterGlasses.value = preferences.waterGlasses(today).first()
        }
    }

    /** Targets come from the profile's own numbers; the flat default covers a sparse profile. */
    private val target = combine(
        userRepository.observeUser(),
        weightRepository.observeEntries(),
    ) { user, weights ->
        val weightKg = weights.maxByOrNull { it.date }?.weightKg
        val sex = user?.sex
        val heightCm = user?.heightCm
        val age = user?.age
        if (user != null && sex != null && heightCm != null && age != null && weightKg != null) {
            NutritionTargetsCalculator.daily(sex, weightKg, heightCm, age, user.goal)
        } else {
            NutritionTargetsCalculator.fallback()
        }
    }

    val uiState = combine(
        mealRepository.observeMeals(today),
        waterGlasses,
        target,
    ) { meals, water, dailyTarget ->
        NutritionUiState(meals = meals, target = dailyTarget, waterGlasses = water, loading = false)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = NutritionUiState(),
    )

    fun addWater() {
        updateWater { (it + 1).coerceAtMost(WATER_TARGET_GLASSES * 2) }
    }

    fun removeWater() {
        updateWater { (it - 1).coerceAtLeast(0) }
    }

    /**
     * Caller (the screen) is responsible for ensuring [name] is non-blank before
     * calling this. [mealId] is null when logging a new meal; passing an
     * existing meal's id turns this into an edit, since [MealRepository.logMeal]
     * upserts by id.
     */
    fun logMeal(name: String, items: List<FoodItem>, mealId: String? = null) {
        if (items.isEmpty() || name.isBlank()) return
        viewModelScope.launch {
            mealRepository.logMeal(
                Meal(id = mealId ?: UUID.randomUUID().toString(), name = name, date = today, foodItems = items),
            )
        }
    }

    fun deleteMeal(mealId: String) {
        viewModelScope.launch { mealRepository.deleteMeal(mealId) }
    }

    /**
     * Edits and removals of a single ingredient go through here: the repository
     * stores whole meals, so the change is persisted as an upsert of [meal] with
     * a new item list. The meal itself always survives - removing its last
     * ingredient leaves an empty meal, never deletes it; that stays an explicit
     * meal-level action.
     */
    fun updateMealItems(meal: Meal, items: List<FoodItem>) {
        viewModelScope.launch { mealRepository.logMeal(meal.copy(foodItems = items)) }
    }

    private fun updateWater(transform: (Int) -> Int) {
        waterGlasses.update(transform)
        viewModelScope.launch { preferences.setWaterGlasses(today, waterGlasses.value) }
    }
}
