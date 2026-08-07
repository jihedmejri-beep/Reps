import { defineSecret, defineString } from "firebase-functions/params";

/**
 * Both keys are Secret Manager secrets, never environment literals. This is the
 * entire reason the agents run here instead of on-device: anything shipped in
 * the APK is extractable, so neither key can live in the app.
 *
 * Set them once with:
 *   firebase functions:secrets:set GROQ_API_KEY
 *   firebase functions:secrets:set USDA_API_KEY
 */
export const GROQ_API_KEY = defineSecret("GROQ_API_KEY");
export const USDA_API_KEY = defineSecret("USDA_API_KEY");

/**
 * Model choice is config, not a constant, so it can be rolled without a code
 * deploy.
 *
 * The understanding model must be one that supports Groq's *strict* structured
 * outputs, because the whole contract with the app depends on the schema being
 * guaranteed rather than hoped for. At time of writing that is the gpt-oss
 * family only; a llama model here would silently fall back to best-effort JSON
 * and start returning shapes the client cannot parse.
 */
export const UNDERSTANDING_MODEL = defineString("UNDERSTANDING_MODEL", {
  default: "openai/gpt-oss-120b",
});

/**
 * The coach returns prose, so it has no schema requirement and can use the
 * stronger general model.
 */
export const COACH_MODEL = defineString("COACH_MODEL", {
  default: "llama-3.3-70b-versatile",
});

export const REGION = "us-central1";

/** Firestore collections owned by the backend. */
export const Collections = {
  /**
   * Analysed meals keyed by ingredient signature. Checked before any USDA
   * request, which is what keeps a popular meal from costing a lookup per user.
   */
  NUTRITION_CACHE: "nutritionCache",
  /** Per-ingredient USDA matches, so a repeat ingredient skips search entirely. */
  INGREDIENT_CACHE: "ingredientCache",
} as const;

/** How long a cached analysis stays trustworthy. USDA data moves slowly. */
export const CACHE_TTL_MS = 1000 * 60 * 60 * 24 * 30;
