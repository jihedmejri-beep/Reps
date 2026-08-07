import { createHash } from "node:crypto";
import { getFirestore } from "firebase-admin/firestore";
import { CACHE_TTL_MS, Collections } from "../config";
import type { MealDraftWire, NutritionAnalysisWire } from "../contract";
import type { UsdaFood } from "./usda";

/**
 * Two caches, because they miss at different rates.
 *
 * The meal cache only helps when someone logs a meal with the exact same
 * ingredients and weights, which is common for regulars but not for everyone.
 * The ingredient cache helps on nearly every request, because "chicken breast"
 * is "chicken breast" no matter what it is served with. The second is what
 * actually keeps USDA request volume down.
 */

/**
 * A stable fingerprint for a meal: ingredient names and weights, order
 * independent. The meal's *name* is excluded on purpose - "lunch" and "chicken
 * and rice" describing the same food should share a cache entry.
 */
export function mealSignature(draft: MealDraftWire): string {
  const parts = draft.ingredients
    .map((item) => `${item.name.toLowerCase().trim()}@${Math.round(item.grams ?? 0)}`)
    .sort();
  return createHash("sha256").update(parts.join("|")).digest("hex");
}

export async function readMealCache(
  signature: string,
): Promise<NutritionAnalysisWire | null> {
  const snapshot = await getFirestore()
    .collection(Collections.NUTRITION_CACHE)
    .doc(signature)
    .get();

  if (!snapshot.exists) return null;

  const data = snapshot.data() as { cachedAt?: number; analysis?: NutritionAnalysisWire };
  if (data.analysis === undefined || isStale(data.cachedAt)) return null;

  return { ...data.analysis, fromCache: true };
}

export async function writeMealCache(
  signature: string,
  analysis: NutritionAnalysisWire,
): Promise<void> {
  await getFirestore()
    .collection(Collections.NUTRITION_CACHE)
    .doc(signature)
    .set({ cachedAt: Date.now(), analysis: { ...analysis, fromCache: false } });
}

export async function readIngredientCache(name: string): Promise<UsdaFood | null> {
  const snapshot = await getFirestore()
    .collection(Collections.INGREDIENT_CACHE)
    .doc(ingredientKey(name))
    .get();

  if (!snapshot.exists) return null;

  const data = snapshot.data() as { cachedAt?: number; food?: UsdaFood };
  if (data.food === undefined || isStale(data.cachedAt)) return null;

  return data.food;
}

export async function writeIngredientCache(name: string, food: UsdaFood): Promise<void> {
  await getFirestore()
    .collection(Collections.INGREDIENT_CACHE)
    .doc(ingredientKey(name))
    .set({ cachedAt: Date.now(), food, query: name });
}

function ingredientKey(name: string): string {
  return createHash("sha256").update(name.toLowerCase().trim()).digest("hex");
}

function isStale(cachedAt: number | undefined): boolean {
  if (typeof cachedAt !== "number") return true;
  return Date.now() - cachedAt > CACHE_TTL_MS;
}
