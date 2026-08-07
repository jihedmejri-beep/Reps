import { randomUUID } from "node:crypto";
import type {
  AnalysedItemWire,
  MealDraftWire,
  MicrosWire,
  NutritionAnalysisWire,
} from "../contract";
import {
  readIngredientCache,
  readMealCache,
  writeIngredientCache,
  writeMealCache,
  mealSignature,
} from "./cache";
import { NutrientNumber, nutrientPer100g, searchFood, type UsdaFood } from "./usda";

/**
 * The backend nutrition engine.
 *
 * This is the only place in the system that produces a nutrition figure. Both
 * agents sit either side of it: one feeds it structured input, the other reads
 * its output. Nothing else computes.
 */
export async function analyseMeal(draft: MealDraftWire): Promise<NutritionAnalysisWire> {
  const signature = mealSignature(draft);

  const cached = await readMealCache(signature);
  if (cached !== null) return cached;

  const items: AnalysedItemWire[] = [];
  const unmatched: string[] = [];
  // Kept alongside `items` because micros live on the USDA record, not on the
  // app's FoodItem, which only carries the four macros.
  const matchedFoods: { food: UsdaFood; grams: number }[] = [];

  // Sequential rather than parallel: USDA rate-limits by key, and a meal is
  // rarely more than a handful of ingredients. Fanning out here would trade a
  // little latency for 429s under load.
  for (const ingredient of draft.ingredients) {
    const grams = ingredient.grams;
    if (grams === null || grams <= 0) {
      unmatched.push(ingredient.rawText || ingredient.name);
      continue;
    }

    const food = await lookup(ingredient.name);
    if (food === null) {
      unmatched.push(ingredient.rawText || ingredient.name);
      continue;
    }

    const energy = nutrientPer100g(food, NutrientNumber.ENERGY_KCAL);
    if (energy === null) {
      unmatched.push(ingredient.rawText || ingredient.name);
      continue;
    }

    items.push({
      foodItem: {
        id: randomUUID(),
        // The user's own wording, so the logged meal reads back the way they
        // said it rather than as a USDA description.
        name: ingredient.rawText || ingredient.name,
        grams,
        caloriesPer100g: energy,
        proteinPer100g: nutrientPer100g(food, NutrientNumber.PROTEIN) ?? 0,
        carbsPer100g: nutrientPer100g(food, NutrientNumber.CARBS) ?? 0,
        fatPer100g: nutrientPer100g(food, NutrientNumber.FAT) ?? 0,
      },
      fdcId: food.fdcId,
      matchedDescription: food.description,
    });
    matchedFoods.push({ food, grams });
  }

  const analysis: NutritionAnalysisWire = {
    mealName: draft.mealName?.trim() || "Meal",
    items,
    micros: aggregateMicros(matchedFoods),
    unmatched,
    fromCache: false,
  };

  // Only cache a complete result. A partial analysis is worth returning once,
  // but caching it would make a transient USDA miss permanent for that meal.
  if (unmatched.length === 0 && items.length > 0) {
    await writeMealCache(signature, analysis);
  }

  return analysis;
}

async function lookup(name: string): Promise<UsdaFood | null> {
  const cached = await readIngredientCache(name);
  if (cached !== null) return cached;

  const food = await searchFood(name);
  if (food !== null) {
    await writeIngredientCache(name, food);
  }
  return food;
}

/**
 * Sums each micronutrient across matched ingredients, scaling per-100g values
 * to the logged weight.
 *
 * A micronutrient stays null unless at least one ingredient reported it.
 * Summing absent values as zero would turn "USDA has no fibre figure for this"
 * into a confident "0 g of fibre", which is a different and wrong claim.
 */
function aggregateMicros(matched: { food: UsdaFood; grams: number }[]): MicrosWire {
  const sum = (nutrientNumber: string): number | null => {
    let total = 0;
    let sawValue = false;
    for (const { food, grams } of matched) {
      const per100g = nutrientPer100g(food, nutrientNumber);
      if (per100g === null) continue;
      sawValue = true;
      total += (per100g * grams) / 100;
    }
    return sawValue ? round(total) : null;
  };

  return {
    fiber: sum(NutrientNumber.FIBER),
    sugar: sum(NutrientNumber.SUGAR),
    saturatedFat: sum(NutrientNumber.SATURATED_FAT),
    sodiumMg: sum(NutrientNumber.SODIUM),
    potassiumMg: sum(NutrientNumber.POTASSIUM),
    calciumMg: sum(NutrientNumber.CALCIUM),
    ironMg: sum(NutrientNumber.IRON),
    vitaminCMg: sum(NutrientNumber.VITAMIN_C),
    cholesterolMg: sum(NutrientNumber.CHOLESTEROL),
  };
}

function round(value: number): number {
  return Math.round(value * 10) / 10;
}
