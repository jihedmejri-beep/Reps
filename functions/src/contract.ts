/**
 * The wire contract between the app and the backend.
 *
 * These types mirror the Kotlin models in com.reps.app.domain.model one for
 * one. If you change a field here, change it there in the same commit - the
 * callable boundary is untyped JSON and will not catch the drift for you.
 *
 * Note the deliberate absence of any nutrition field on MealDraftWire. The
 * understanding agent cannot report calories because the shape it must fill has
 * nowhere to put them.
 */

export interface DraftIngredientWire {
  name: string;
  rawText: string;
  grams: number | null;
  quantityText: string | null;
  cookingMethod: string | null;
}

export interface MealDraftWire {
  mealName: string | null;
  ingredients: DraftIngredientWire[];
  readyForAnalysis: boolean;
  confidence: number;
  followUpQuestions: string[];
  ambiguities: string[];
}

export interface UnderstandResponseWire {
  message: string;
  draft: MealDraftWire;
}

export interface FoodItemWire {
  id: string;
  name: string;
  grams: number;
  caloriesPer100g: number;
  proteinPer100g: number;
  carbsPer100g: number;
  fatPer100g: number;
}

export interface AnalysedItemWire {
  foodItem: FoodItemWire;
  fdcId: number | null;
  matchedDescription: string;
}

export interface MicrosWire {
  fiber: number | null;
  sugar: number | null;
  saturatedFat: number | null;
  sodiumMg: number | null;
  potassiumMg: number | null;
  calciumMg: number | null;
  ironMg: number | null;
  vitaminCMg: number | null;
  cholesterolMg: number | null;
}

export interface NutritionAnalysisWire {
  mealName: string;
  items: AnalysedItemWire[];
  micros: MicrosWire;
  unmatched: string[];
  fromCache: boolean;
}

export interface AnalyseResponseWire {
  analysis: NutritionAnalysisWire;
  /** Prose only. The app renders every number from `analysis`. */
  coaching: string;
}

/** A prior conversation turn, as sent up by the app. */
export interface ExchangeWire {
  fromUser: boolean;
  text: string;
}

export type GoalWire = "BULK" | "CUT" | "MAINTAIN";
