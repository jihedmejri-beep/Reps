package com.reps.app.feature.nutrition

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.reps.app.domain.model.FoodItem
import com.reps.app.domain.model.Macros
import com.reps.app.domain.model.Meal
import com.reps.app.domain.repository.MealRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject

/** No per-user nutrition goal exists in the domain yet; a flat daily target stands in. */
private val DailyTarget = Macros(calories = 2400.0, protein = 180.0, carbs = 260.0, fat = 70.0)
private const val WATER_TARGET_GLASSES = 8

data class NutritionUiState(
    val meals: List<Meal> = emptyList(),
    val target: Macros = DailyTarget,
    val waterGlasses: Int = 0,
    val waterTargetGlasses: Int = WATER_TARGET_GLASSES,
    val loading: Boolean = true,
) {
    val totals: Macros get() = meals.fold(Macros()) { acc, meal -> acc + meal.macros }
}

@HiltViewModel
class NutritionViewModel @Inject constructor(
    private val mealRepository: MealRepository,
) : ViewModel() {

    private val today = LocalDate.now()
    private val waterGlasses = MutableStateFlow(0)

    val uiState = combine(
        mealRepository.observeMeals(today),
        waterGlasses,
    ) { meals, water ->
        NutritionUiState(meals = meals, waterGlasses = water, loading = false)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = NutritionUiState(),
    )

    fun addWater() {
        waterGlasses.update { (it + 1).coerceAtMost(WATER_TARGET_GLASSES * 2) }
    }

    fun removeWater() {
        waterGlasses.update { (it - 1).coerceAtLeast(0) }
    }

    /** Caller (the screen) is responsible for ensuring [name] is non-blank before calling this. */
    fun logMeal(name: String, items: List<FoodItem>) {
        if (items.isEmpty() || name.isBlank()) return
        viewModelScope.launch {
            mealRepository.logMeal(
                Meal(id = UUID.randomUUID().toString(), name = name, date = today, foodItems = items),
            )
        }
    }
}
