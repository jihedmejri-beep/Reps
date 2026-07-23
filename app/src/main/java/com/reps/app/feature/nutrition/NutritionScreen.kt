package com.reps.app.feature.nutrition

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.reps.app.R
import com.reps.app.core.components.RepsBottomSheet
import com.reps.app.core.components.RepsButton
import com.reps.app.core.components.RepsOutlinedButton
import com.reps.app.core.components.RepsTextField
import com.reps.app.core.components.SectionHeader
import com.reps.app.core.theme.RepsGreen
import com.reps.app.core.theme.RepsOnGreen
import com.reps.app.core.theme.RepsTheme
import com.reps.app.domain.model.FoodItem
import com.reps.app.domain.model.Meal
import com.reps.app.navigation.OnTabReselected
import com.reps.app.navigation.Routes
import com.reps.app.navigation.navBarClearance
import kotlin.math.roundToInt

@Composable
fun NutritionScreen(viewModel: NutritionViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    var showAddMeal by remember { mutableStateOf(false) }

    OnTabReselected(Routes.NUTRITION) { listState.animateScrollToItem(0) }

    val dimens = RepsTheme.dimens
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().background(RepsTheme.colors.background).statusBarsPadding(),
        contentPadding = PaddingValues(
            start = dimens.screenPadding,
            end = dimens.screenPadding,
            bottom = navBarClearance(),
        ),
        verticalArrangement = Arrangement.spacedBy(dimens.sectionGap),
    ) {
        item {
            Row(
                Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                SectionHeader(
                    eyebrow = stringResource(R.string.nutrition_eyebrow),
                    title = stringResource(R.string.nutrition_title),
                )
                Box(
                    Modifier
                        .size(38.dp)
                        .background(RepsGreen, MaterialTheme.shapes.small)
                        .clickable { showAddMeal = true },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.nutrition_add_meal), tint = RepsOnGreen)
                }
            }
        }

        item { MacroSummaryCard(state) }
        item { WaterCard(state, onAdd = viewModel::addWater, onRemove = viewModel::removeWater) }

        if (state.meals.isEmpty() && !state.loading) {
            item {
                Column(Modifier.fillMaxWidth().padding(vertical = 32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(stringResource(R.string.nutrition_empty), style = MaterialTheme.typography.bodyMedium, color = RepsTheme.colors.textTertiary)
                }
            }
        } else {
            items(state.meals, key = { it.id }) { meal -> MealCard(meal) }
        }
    }

    if (showAddMeal) {
        AddMealSheet(
            onDismiss = { showAddMeal = false },
            onSave = { name, items ->
                viewModel.logMeal(name, items)
                showAddMeal = false
            },
        )
    }
}

@Composable
private fun MacroSummaryCard(state: NutritionUiState) {
    val totals = state.totals
    Column(Modifier.fillMaxWidth().background(RepsTheme.colors.surface, MaterialTheme.shapes.large).padding(RepsTheme.dimens.cardPadding)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            CalorieRing(consumed = totals.calories, target = state.target.calories)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                MacroBar(stringResource(R.string.nutrition_protein), totals.protein, state.target.protein)
                MacroBar(stringResource(R.string.nutrition_carbs), totals.carbs, state.target.carbs)
                MacroBar(stringResource(R.string.nutrition_fat), totals.fat, state.target.fat)
            }
        }
    }
}

@Composable
private fun CalorieRing(consumed: Double, target: Double, modifier: Modifier = Modifier) {
    val fraction = if (target <= 0.0) 0f else (consumed / target).toFloat().coerceIn(0f, 1f)
    val animated by animateFloatAsState(fraction, tween(900, easing = FastOutSlowInEasing), label = "calRing")
    // Hoisted out of the Canvas draw scope, which is not composable.
    val trackColor = RepsTheme.colors.outline
    Box(modifier.size(92.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val strokeWidth = 9.dp.toPx()
            val arcSize = Size(size.width - strokeWidth, size.height - strokeWidth)
            val topLeft = Offset(strokeWidth / 2f, strokeWidth / 2f)
            drawArc(trackColor, -90f, 360f, false, topLeft, arcSize, style = Stroke(strokeWidth, cap = StrokeCap.Round))
            drawArc(RepsGreen, -90f, 360f * animated, false, topLeft, arcSize, style = Stroke(strokeWidth, cap = StrokeCap.Round))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(consumed.roundToInt().toString(), style = MaterialTheme.typography.titleMedium, color = RepsTheme.colors.textPrimary)
            Text(
                text = stringResource(R.string.nutrition_of, "${target.roundToInt()} ${stringResource(R.string.nutrition_kcal)}"),
                style = MaterialTheme.typography.labelSmall,
                color = RepsTheme.colors.textSecondary,
            )
        }
    }
}

@Composable
private fun MacroBar(label: String, value: Double, target: Double) {
    val fraction = if (target <= 0.0) 0f else (value / target).toFloat().coerceIn(0f, 1f)
    val animated by animateFloatAsState(fraction, tween(900, easing = FastOutSlowInEasing), label = "macroBar")
    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = RepsTheme.colors.textSecondary)
            Text("${value.roundToInt()}/${target.roundToInt()}g", style = MaterialTheme.typography.labelSmall, color = RepsTheme.colors.textSecondary)
        }
        Box(
            Modifier
                .fillMaxWidth()
                .padding(top = 5.dp)
                .height(6.dp)
                .background(RepsTheme.colors.surfaceElevated, RoundedCornerShape(3.dp)),
        ) {
            Box(Modifier.fillMaxWidth(animated).height(6.dp).background(RepsGreen, RoundedCornerShape(3.dp)))
        }
    }
}

@Composable
private fun WaterCard(state: NutritionUiState, onAdd: () -> Unit, onRemove: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(RepsTheme.colors.surface, MaterialTheme.shapes.large).padding(RepsTheme.dimens.cardPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.WaterDrop, contentDescription = null, tint = RepsGreen, modifier = Modifier.size(20.dp))
        Column(Modifier.weight(1f).padding(start = 10.dp)) {
            Text(stringResource(R.string.nutrition_water), style = MaterialTheme.typography.titleSmall, color = RepsTheme.colors.textPrimary)
            Text(
                text = stringResource(R.string.nutrition_water_glasses, state.waterGlasses, state.waterTargetGlasses),
                style = MaterialTheme.typography.bodySmall,
                color = RepsTheme.colors.textSecondary,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        IconButton(onClick = onRemove) {
            Icon(Icons.Outlined.Remove, contentDescription = stringResource(R.string.nutrition_water_remove_cd), tint = RepsTheme.colors.textSecondary)
        }
        Box(
            Modifier
                .size(34.dp)
                .background(RepsGreen, CircleShape)
                .clickable(onClickLabel = stringResource(R.string.nutrition_water_add_cd), onClick = onAdd),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.Add, contentDescription = null, tint = RepsOnGreen, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun MealCard(meal: Meal) {
    Column(Modifier.fillMaxWidth().background(RepsTheme.colors.surface, MaterialTheme.shapes.large).padding(RepsTheme.dimens.cardPadding)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(meal.name, style = MaterialTheme.typography.titleSmall, color = RepsTheme.colors.textPrimary)
            Text(
                "${meal.macros.calories.roundToInt()} ${stringResource(R.string.nutrition_kcal)}",
                style = MaterialTheme.typography.labelMedium,
                color = RepsTheme.colors.textSecondary,
            )
        }
        Column(Modifier.padding(top = 8.dp)) {
            meal.foodItems.forEach { item ->
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 5.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row {
                        Text(item.name, style = MaterialTheme.typography.bodyMedium, color = RepsTheme.colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(" ${item.grams.roundToInt()}g", style = MaterialTheme.typography.labelSmall, color = RepsTheme.colors.textTertiary)
                    }
                    Text(
                        "${item.calories.roundToInt()} ${stringResource(R.string.nutrition_kcal)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = RepsTheme.colors.textSecondary,
                    )
                }
            }
        }
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun AddMealSheet(onDismiss: () -> Unit, onSave: (String, List<FoodItem>) -> Unit) {
    var mealName by remember { mutableStateOf("") }
    var foodName by remember { mutableStateOf("") }
    var foodGrams by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val draftItems = remember { mutableStateListOf<FoodItem>() }
    val ingredientRequiredError = stringResource(R.string.nutrition_ingredient_required)

    RepsBottomSheet(onDismissRequest = onDismiss, title = stringResource(R.string.nutrition_add_meal)) {
        RepsTextField(value = mealName, onValueChange = { mealName = it }, placeholder = stringResource(R.string.nutrition_meal_name_hint))

        Text(
            text = stringResource(R.string.nutrition_add_food).uppercase(),
            style = RepsTheme.textStyles.eyebrow,
            color = RepsGreen,
            modifier = Modifier.padding(top = 18.dp, bottom = 10.dp),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            RepsTextField(
                value = foodName,
                onValueChange = { foodName = it },
                placeholder = stringResource(R.string.nutrition_food_name),
                modifier = Modifier.weight(2f),
            )
            RepsTextField(
                value = foodGrams,
                onValueChange = { foodGrams = it },
                placeholder = stringResource(R.string.nutrition_grams),
                keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal,
                modifier = Modifier.weight(1f),
            )
        }
        error?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = com.reps.app.core.theme.RepsError, modifier = Modifier.padding(top = 6.dp))
        }
        RepsOutlinedButton(
            text = stringResource(R.string.nutrition_add_food),
            onClick = {
                val grams = foodGrams.toDoubleOrNull()
                if (foodName.isBlank() || grams == null || grams <= 0.0) {
                    error = ingredientRequiredError
                    return@RepsOutlinedButton
                }
                error = null
                // No nutrition database in this build; a flat per-100g estimate keeps
                // the running total meaningful until a real ingredient lookup lands.
                draftItems.add(
                    FoodItem(
                        id = java.util.UUID.randomUUID().toString(),
                        name = foodName,
                        grams = grams,
                        caloriesPer100g = 165.0,
                        proteinPer100g = 12.0,
                        carbsPer100g = 14.0,
                        fatPer100g = 5.0,
                    ),
                )
                foodName = ""
                foodGrams = ""
            },
            modifier = Modifier.padding(top = 10.dp),
        )

        Column(Modifier.padding(top = 6.dp)) {
            draftItems.forEach { item ->
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("${item.name} · ${item.grams.roundToInt()}g", style = MaterialTheme.typography.bodySmall, color = RepsTheme.colors.textPrimary)
                    IconButton(onClick = { draftItems.remove(item) }, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Filled.Close, contentDescription = null, tint = RepsTheme.colors.textTertiary, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }

        RepsButton(
            text = stringResource(R.string.nutrition_save_meal),
            onClick = { onSave(mealName, draftItems.toList()) },
            enabled = mealName.isNotBlank() && draftItems.isNotEmpty(),
            modifier = Modifier.padding(top = 18.dp),
        )
    }
}
