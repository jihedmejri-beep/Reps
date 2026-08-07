import { initializeApp } from "firebase-admin/app";
import { HttpsError } from "firebase-functions/v2/https";
import { defineAgent } from "./agents/registry";
import { runMealUnderstanding } from "./agents/mealUnderstanding";
import { runCoachQuestion, runNutritionCoach } from "./agents/nutritionCoach";
import { analyseMeal } from "./nutrition/engine";
import type {
  AnalyseResponseWire,
  ExchangeWire,
  GoalWire,
  MealDraftWire,
  UnderstandResponseWire,
} from "./contract";

initializeApp();

/**
 * The three hops of the documented flow, exposed one per callable rather than
 * hidden behind a single endpoint. Each stage is separately observable, and a
 * failure names the stage that produced it.
 *
 *   understandMeal   user text        -> structured MealDraft (Agent 1)
 *   analyseMeal      MealDraft        -> verified nutrition (engine) + coaching (Agent 2)
 *   askCoach         question         -> coaching prose (Agent 2)
 */

export const understandMeal = defineAgent<
  { history?: ExchangeWire[]; message?: string },
  UnderstandResponseWire
>("understandMeal", async (payload) => {
  const message = payload.message?.trim();
  if (message === undefined || message.length === 0) {
    throw new HttpsError("invalid-argument", "message is required.");
  }

  return runMealUnderstanding({
    history: trimHistory(payload.history),
    message,
  });
});

export const analyseMealAndCoach = defineAgent<
  { draft?: MealDraftWire; goal?: GoalWire },
  AnalyseResponseWire
>("analyseMealAndCoach", async (payload) => {
  const draft = payload.draft;
  if (draft === undefined || !Array.isArray(draft.ingredients) || draft.ingredients.length === 0) {
    throw new HttpsError("invalid-argument", "draft must contain ingredients.");
  }

  // The engine runs first and its output is the only nutrition context the
  // coach receives. Reversing this - letting the coach see the raw draft -
  // would give it room to reason about food it has no verified figures for.
  const analysis = await analyseMeal(draft);

  if (analysis.items.length === 0) {
    throw new HttpsError("not-found", "No ingredient could be matched in the food database.", {
      reason: "FoodDatabaseUnavailable",
    });
  }

  const coaching = await runNutritionCoach({ analysis, goal: payload.goal ?? "MAINTAIN" });
  return { analysis, coaching };
});

export const askCoach = defineAgent<
  { history?: ExchangeWire[]; question?: string; goal?: GoalWire },
  { message: string }
>("askCoach", async (payload) => {
  const question = payload.question?.trim();
  if (question === undefined || question.length === 0) {
    throw new HttpsError("invalid-argument", "question is required.");
  }

  const message = await runCoachQuestion({
    history: trimHistory(payload.history),
    question,
    goal: payload.goal ?? "MAINTAIN",
  });
  return { message };
});

/**
 * Caps how much conversation is replayed to the model. A long-running chat
 * would otherwise grow the prompt without bound, and the understanding agent
 * only needs the recent turns to resolve what is still missing.
 */
function trimHistory(history: ExchangeWire[] | undefined): ExchangeWire[] {
  if (!Array.isArray(history)) return [];
  return history
    .filter((turn) => typeof turn?.text === "string" && turn.text.trim().length > 0)
    .slice(-12);
}
