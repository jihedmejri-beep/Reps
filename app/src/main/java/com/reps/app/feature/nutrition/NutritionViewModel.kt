package com.reps.app.feature.nutrition

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.reps.app.core.util.DateTicker
import com.reps.app.core.util.NutritionTargetsCalculator
import com.reps.app.data.datastore.UserPreferencesDataStore
import com.reps.app.domain.model.FoodItem
import com.reps.app.domain.model.Macros
import com.reps.app.domain.model.Meal
import com.reps.app.domain.repository.MealRepository
import com.reps.app.domain.repository.UserRepository
import com.reps.app.domain.repository.WeightRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject

private const val WATER_TARGET_GLASSES = 8

data class NutritionUiState(
    val meals: List<Meal> = emptyList(),
    val target: Macros = NutritionTargetsCalculator.fallback(),
    /** False while [target] is the generic default because the profile is incomplete. */
    val targetPersonalised: Boolean = false,
    val waterGlasses: Int = 0,
    val waterTargetGlasses: Int = WATER_TARGET_GLASSES,
    val loading: Boolean = true,
) {
    val totals: Macros get() = meals.fold(Macros()) { acc, meal -> acc + meal.macros }
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class NutritionViewModel @Inject constructor(
    private val mealRepository: MealRepository,
    private val weightRepository: WeightRepository,
    private val userRepository: UserRepository,
    private val preferences: UserPreferencesDataStore,
) : ViewModel() {

    /** Follows the calendar, so the screen rolls over to a new day at midnight. */
    private val today = DateTicker.today().stateIn(viewModelScope, SharingStarted.Eagerly, LocalDate.now())

    /** Water is per-day, per-account state in DataStore; a new date is a new key, so it resets naturally. */
    private val waterGlasses = today.flatMapLatest { preferences.waterGlasses(it) }

    /** Serialises water writes so quick taps never read the same stale count. */
    private val waterLock = Mutex()

    /** Targets come from the profile's own numbers; the flat default covers a sparse profile. */
    private val target = combine(
        userRepository.observeUser(),
        weightRepository.observeEntries(),
    ) { user, weights ->
        val weightKg = weights.maxByOrNull { it.date }?.weightKg
        NutritionTargetsCalculator.forUser(user, weightKg)?.let { it to true }
            ?: (NutritionTargetsCalculator.fallback() to false)
    }

    val uiState = combine(
        today.flatMapLatest { mealRepository.observeMeals(it) },
        waterGlasses,
        target,
    ) { meals, water, (dailyTarget, personalised) ->
        NutritionUiState(
            meals = meals,
            target = dailyTarget,
            targetPersonalised = personalised,
            waterGlasses = water,
            loading = false,
        )
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
                Meal(id = mealId ?: UUID.randomUUID().toString(), name = name, date = today.value, foodItems = items),
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
        val day = today.value
        viewModelScope.launch {
            waterLock.withLock { preferences.setWaterGlasses(day, transform(preferences.waterGlasses(day).first())) }
        }
    }
}
