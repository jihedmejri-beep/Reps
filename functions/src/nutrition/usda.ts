import { USDA_API_KEY } from "../config";

/**
 * USDA FoodData Central client.
 *
 * Nutrient values in `foodNutrients` are per 100 g across every data type,
 * which is exactly how the app's FoodItem stores them - so there is no unit
 * conversion between this response and the domain model, only selection.
 */

const BASE = "https://api.nal.usda.gov/fdc/v1";

/**
 * Nutrients are identified by `nutrientNumber`, the stable USDA code. The
 * numeric `nutrientId` is also present and also stable, but the code is the one
 * USDA documents against, so it is what we key on.
 */
export const NutrientNumber = {
  ENERGY_KCAL: "208",
  PROTEIN: "203",
  FAT: "204",
  CARBS: "205",
  FIBER: "291",
  SUGAR: "269",
  SATURATED_FAT: "606",
  SODIUM: "307",
  POTASSIUM: "306",
  CALCIUM: "301",
  IRON: "303",
  VITAMIN_C: "401",
  CHOLESTEROL: "601",
} as const;

interface UsdaNutrient {
  nutrientNumber?: string;
  nutrientId?: number;
  nutrientName?: string;
  unitName?: string;
  value?: number;
}

export interface UsdaFood {
  fdcId: number;
  description: string;
  dataType?: string;
  foodNutrients?: UsdaNutrient[];
}

export class UsdaUnavailableError extends Error {
  constructor(status: number) {
    super(`USDA FoodData Central returned ${status}`);
    this.name = "UsdaUnavailableError";
  }
}

/**
 * Foundation and SR Legacy are curated whole-food entries with clean per-100g
 * values, so they are searched first. Branded is the fallback: it covers
 * packaged products but is noisy and full of near-duplicate listings.
 */
const PREFERRED_TYPES = ["Foundation", "SR Legacy"];
const FALLBACK_TYPES = ["Branded"];

export async function searchFood(query: string): Promise<UsdaFood | null> {
  const preferred = await search(query, PREFERRED_TYPES);
  if (preferred !== null) return preferred;
  return search(query, FALLBACK_TYPES);
}

async function search(query: string, dataTypes: string[]): Promise<UsdaFood | null> {
  const url = new URL(`${BASE}/foods/search`);
  url.searchParams.set("query", query);
  url.searchParams.set("pageSize", "5");
  url.searchParams.set("dataType", dataTypes.join(","));
  url.searchParams.set("api_key", USDA_API_KEY.value());

  const response = await fetch(url, {
    signal: AbortSignal.timeout(10_000),
  });

  if (response.status === 429 || response.status >= 500) {
    throw new UsdaUnavailableError(response.status);
  }
  if (!response.ok) return null;

  const body = (await response.json()) as { foods?: UsdaFood[] };
  const foods = body.foods ?? [];

  // USDA orders by its own relevance score, but an entry with no energy value
  // is useless to us no matter how well it matched the words.
  return foods.find((food) => nutrientPer100g(food, NutrientNumber.ENERGY_KCAL) !== null) ?? null;
}

/** Per-100g value for a nutrient, or null when USDA does not report it. */
export function nutrientPer100g(food: UsdaFood, nutrientNumber: string): number | null {
  const match = (food.foodNutrients ?? []).find(
    (nutrient) => nutrient.nutrientNumber === nutrientNumber,
  );
  const value = match?.value;
  return typeof value === "number" && Number.isFinite(value) ? value : null;
}
