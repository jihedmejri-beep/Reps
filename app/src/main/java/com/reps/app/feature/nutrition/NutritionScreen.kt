package com.reps.app.feature.nutrition

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
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
import com.reps.app.core.theme.RepsCarbs
import com.reps.app.core.theme.RepsError
import com.reps.app.core.theme.RepsFat
import com.reps.app.core.theme.RepsGreen
import com.reps.app.core.theme.RepsProtein
import com.reps.app.core.theme.RepsOnGreen
import com.reps.app.core.theme.RepsTheme
import com.reps.app.domain.model.FoodItem
import com.reps.app.domain.model.Meal
import com.reps.app.feature.nutrition.assistant.NutritionAssistantFab
import com.reps.app.navigation.OnTabReselected
import com.reps.app.navigation.Routes
import com.reps.app.navigation.navBarClearance
import kotlin.math.roundToInt

@Composable
fun NutritionScreen(
    onOpenAssistant: () -> Unit,
    viewModel: NutritionViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    var showAddMeal by remember { mutableStateOf(false) }

    // The meal a tap opened the action menu for; editingMeal/deletingMeal only
    // get set once the corresponding action is chosen, so at most one of the
    // three is ever driving UI at a time.
    var actionMenuMeal by remember { mutableStateOf<Meal?>(null) }
    var editingMeal by remember { mutableStateOf<Meal?>(null) }
    var deletingMeal by remember { mutableStateOf<Meal?>(null) }

    // The same three phases, for a tap on a single ingredient row. These are
    // what keep an ingredient action off the whole meal: the target carries the
    // one FoodItem that was tapped alongside the meal holding it.
    var actionMenuItem by remember { mutableStateOf<SelectedIngredient?>(null) }
    var editingItem by remember { mutableStateOf<SelectedIngredient?>(null) }
    var deletingItem by remember { mutableStateOf<SelectedIngredient?>(null) }

    OnTabReselected(Routes.NUTRITION) { listState.animateScrollToItem(0) }

    val dimens = RepsTheme.dimens
    val contentBlur by animateDpAsState(
        targetValue = if (actionMenuMeal != null || actionMenuItem != null) 16.dp else 0.dp,
        animationSpec = tween(220),
        label = "mealMenuBlur",
    )

    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().blur(contentBlur)) {
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
                // item { WaterCard(state, onAdd = viewModel::addWater, onRemove = viewModel::removeWater) }

                if (state.meals.isEmpty() && !state.loading) {
                    item {
                        Column(Modifier.fillMaxWidth().padding(vertical = 32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(stringResource(R.string.nutrition_empty), style = MaterialTheme.typography.bodyMedium, color = RepsTheme.colors.textTertiary)
                        }
                    }
                } else {
                    items(state.meals, key = { it.id }) { meal ->
                        MealCard(
                            meal = meal,
                            onClick = { actionMenuMeal = meal },
                            onIngredientClick = { item -> actionMenuItem = SelectedIngredient(meal, item) },
                        )
                    }
                }
            }

            // Sits above the floating nav pill rather than beside it: navBarClearance
            // is the same distance the list reserves at its foot, so the button lands
            // clear of the pill and of the last card.
            NutritionAssistantFab(
                onClick = onOpenAssistant,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = dimens.screenPadding, bottom = navBarClearance()),
            )
        }

        MealActionMenu(
            visible = actionMenuMeal != null,
            editLabel = stringResource(R.string.nutrition_meal_edit),
            deleteLabel = stringResource(R.string.nutrition_meal_delete),
            onEdit = {
                editingMeal = actionMenuMeal
                actionMenuMeal = null
            },
            onDelete = {
                deletingMeal = actionMenuMeal
                actionMenuMeal = null
            },
            onDismiss = { actionMenuMeal = null },
        )

        MealActionMenu(
            visible = actionMenuItem != null,
            editLabel = stringResource(R.string.nutrition_ingredient_edit),
            deleteLabel = stringResource(R.string.nutrition_ingredient_delete),
            onEdit = {
                editingItem = actionMenuItem
                actionMenuItem = null
            },
            onDelete = {
                deletingItem = actionMenuItem
                actionMenuItem = null
            },
            onDismiss = { actionMenuItem = null },
        )
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

    editingMeal?.let { meal ->
        AddMealSheet(
            initialMeal = meal,
            onDismiss = { editingMeal = null },
            onSave = { name, items ->
                viewModel.logMeal(name, items, mealId = meal.id)
                editingMeal = null
            },
        )
    }

    deletingMeal?.let { meal ->
        AlertDialog(
            onDismissRequest = { deletingMeal = null },
            containerColor = RepsTheme.colors.surfaceElevated,
            title = { Text(stringResource(R.string.nutrition_delete_meal_title), color = RepsTheme.colors.textPrimary) },
            text = { Text(stringResource(R.string.nutrition_delete_meal_body), color = RepsTheme.colors.textSecondary) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteMeal(meal.id)
                    deletingMeal = null
                }) {
                    Text(stringResource(R.string.nutrition_delete_meal_confirm), color = RepsError)
                }
            },
            dismissButton = {
                TextButton(onClick = { deletingMeal = null }) {
                    Text(stringResource(R.string.nutrition_delete_meal_cancel), color = RepsTheme.colors.textSecondary)
                }
            },
        )
    }

    editingItem?.let { target ->
        EditIngredientSheet(
            item = target.item,
            onDismiss = { editingItem = null },
            onSave = { updated ->
                viewModel.updateMealItems(
                    meal = target.meal,
                    items = target.meal.foodItems.map { if (it === target.item) updated else it },
                )
                editingItem = null
            },
        )
    }

    deletingItem?.let { target ->
        AlertDialog(
            onDismissRequest = { deletingItem = null },
            containerColor = RepsTheme.colors.surfaceElevated,
            title = { Text(stringResource(R.string.nutrition_delete_ingredient_title), color = RepsTheme.colors.textPrimary) },
            text = {
                Text(
                    stringResource(R.string.nutrition_delete_ingredient_body, target.item.name),
                    color = RepsTheme.colors.textSecondary,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.updateMealItems(
                        meal = target.meal,
                        items = target.meal.foodItems.filterNot { it === target.item },
                    )
                    deletingItem = null
                }) {
                    Text(stringResource(R.string.nutrition_delete_meal_confirm), color = RepsError)
                }
            },
            dismissButton = {
                TextButton(onClick = { deletingItem = null }) {
                    Text(stringResource(R.string.nutrition_delete_meal_cancel), color = RepsTheme.colors.textSecondary)
                }
            },
        )
    }
}

/**
 * The ingredient an action menu is targeting, together with the meal that holds
 * it - both are needed because ingredients are persisted as part of their meal.
 */
private data class SelectedIngredient(val meal: Meal, val item: FoodItem)

@Composable
private fun MacroSummaryCard(state: NutritionUiState) {
    val totals = state.totals
    Column(Modifier.fillMaxWidth().background(RepsTheme.colors.surface, MaterialTheme.shapes.large).padding(RepsTheme.dimens.cardPadding)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            CalorieRing(consumed = totals.calories, target = state.target.calories)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                MacroBar(stringResource(R.string.nutrition_protein), totals.protein, state.target.protein, RepsProtein)
                MacroBar(stringResource(R.string.nutrition_carbs), totals.carbs, state.target.carbs, RepsCarbs)
                MacroBar(stringResource(R.string.nutrition_fat), totals.fat, state.target.fat, RepsFat)
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
private fun MacroBar(label: String, value: Double, target: Double, color: Color) {
    val fraction = if (target <= 0.0) 0f else (value / target).toFloat().coerceIn(0f, 1f)
    val animated by animateFloatAsState(fraction, tween(900, easing = FastOutSlowInEasing), label = "macroBar")
    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                // A small colour dot ties the label to its bar, so the macro is
                // named and colour-coded rather than relying on colour alone.
                Box(Modifier.size(7.dp).background(color, CircleShape))
                Text(label, style = MaterialTheme.typography.labelSmall, color = RepsTheme.colors.textSecondary)
            }
            Text("${value.roundToInt()}/${target.roundToInt()}g", style = MaterialTheme.typography.labelSmall, color = RepsTheme.colors.textSecondary)
        }
        Box(
            Modifier
                .fillMaxWidth()
                .padding(top = 5.dp)
                .height(6.dp)
                .background(RepsTheme.colors.surfaceElevated, RoundedCornerShape(3.dp)),
        ) {
            Box(Modifier.fillMaxWidth(animated).height(6.dp).background(color, RoundedCornerShape(3.dp)))
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

/**
 * [onClick] covers the card itself - the meal-level actions. Each ingredient row
 * has its own [onIngredientClick] target, and because a child click consumes the
 * gesture, tapping an ingredient can never open the meal-level menu.
 */
@Composable
private fun MealCard(meal: Meal, onClick: () -> Unit, onIngredientClick: (FoodItem) -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(RepsTheme.colors.surface, MaterialTheme.shapes.large)
            .clickable(
                onClickLabel = stringResource(R.string.nutrition_meal_options_cd, meal.name),
                role = Role.Button,
                onClick = onClick,
            )
            .padding(RepsTheme.dimens.cardPadding),
    ) {
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
                    Modifier
                        .fillMaxWidth()
                        .clickable(
                            onClickLabel = stringResource(R.string.nutrition_meal_options_cd, item.name),
                            role = Role.Button,
                            onClick = { onIngredientClick(item) },
                        )
                        .padding(vertical = 5.dp),
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

/**
 * Both logging a new meal and editing an existing one go through this sheet -
 * [initialMeal] is the only difference, pre-filling the name and ingredients
 * when set. [onSave] always hands back the full current name/items; the caller
 * decides whether that becomes a new meal or an update to [initialMeal].
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun AddMealSheet(
    onDismiss: () -> Unit,
    onSave: (String, List<FoodItem>) -> Unit,
    initialMeal: Meal? = null,
) {
    var mealName by remember { mutableStateOf(initialMeal?.name.orEmpty()) }
    var foodName by remember { mutableStateOf("") }
    var foodGrams by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val draftItems = remember { mutableStateListOf<FoodItem>().apply { initialMeal?.foodItems?.let(::addAll) } }
    val ingredientRequiredError = stringResource(R.string.nutrition_ingredient_required)

    val sheetTitle = if (initialMeal != null) R.string.nutrition_edit_meal else R.string.nutrition_add_meal
    RepsBottomSheet(onDismissRequest = onDismiss, title = stringResource(sheetTitle)) {
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

/**
 * Edits one ingredient in place, in the same sheet style as [AddMealSheet] but
 * scoped to a single row: name and grams only. The per-100g reference values are
 * carried over untouched, so the macros stay consistent with how the item was
 * first logged.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun EditIngredientSheet(
    item: FoodItem,
    onDismiss: () -> Unit,
    onSave: (FoodItem) -> Unit,
) {
    var name by remember { mutableStateOf(item.name) }
    var grams by remember { mutableStateOf(item.grams.roundToInt().toString()) }
    val gramsValue = grams.toDoubleOrNull()

    RepsBottomSheet(onDismissRequest = onDismiss, title = stringResource(R.string.nutrition_edit_ingredient)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            RepsTextField(
                value = name,
                onValueChange = { name = it },
                placeholder = stringResource(R.string.nutrition_food_name),
                modifier = Modifier.weight(2f),
            )
            RepsTextField(
                value = grams,
                onValueChange = { grams = it },
                placeholder = stringResource(R.string.nutrition_grams),
                keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal,
                modifier = Modifier.weight(1f),
            )
        }

        RepsButton(
            text = stringResource(R.string.nutrition_save_ingredient),
            onClick = { onSave(item.copy(name = name.trim(), grams = gramsValue ?: item.grams)) },
            enabled = name.isNotBlank() && gramsValue != null && gramsValue > 0.0,
            modifier = Modifier.padding(top = 18.dp),
        )
    }
}
