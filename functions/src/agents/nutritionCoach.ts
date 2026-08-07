import { COACH_MODEL } from "../config";
import type { ExchangeWire, GoalWire, NutritionAnalysisWire } from "../contract";
import { completeText, type ChatTurn } from "../groq/client";

/**
 * Agent 2 - Nutrition Coach.
 *
 * Receives only verified figures from the engine and explains them. It returns
 * prose and nothing else, which is the second half of the enforcement the
 * understanding agent's schema starts: the app renders every number from the
 * analysis object, so there is no path by which this agent's text can become a
 * displayed value. It can describe the numbers; it cannot become them.
 */

const GOAL_CONTEXT: Record<GoalWire, string> = {
  BULK: "The user is trying to gain muscle, so they are eating at a surplus.",
  CUT: "The user is trying to lose fat, so they are eating at a deficit.",
  MAINTAIN: "The user is maintaining their current weight.",
};

const SYSTEM_PROMPT = `You are a nutrition coach in a fitness app. You are given nutrition figures that have already been computed from the USDA FoodData Central database. They are verified and final.

Your job is to explain what they mean and help the user act on them.

Rules that matter:
- Treat the figures you are given as fact. Never restate a number that was not given to you, and never recalculate or correct one. If a value is missing, say it is not available rather than filling it in.
- Be encouraging and specific. "Good protein for recovery" beats "looks healthy".
- Never be judgemental about what someone ate. No food is a moral failure.
- Tie your advice to their goal.
- You are not a doctor. Do not diagnose, and do not give medical advice.

Write two or three short paragraphs in plain language. No headings, no bullet lists, no markdown.`;

export async function runNutritionCoach(input: {
  analysis: NutritionAnalysisWire;
  goal: GoalWire;
}): Promise<string> {
  const messages: ChatTurn[] = [
    { role: "system", content: SYSTEM_PROMPT },
    { role: "user", content: describeAnalysis(input.analysis, input.goal) },
  ];

  return completeText({ model: COACH_MODEL.value(), messages, temperature: 0.6 });
}

/** A free-form nutrition question, answered without a meal in hand. */
export async function runCoachQuestion(input: {
  history: ExchangeWire[];
  question: string;
  goal: GoalWire;
}): Promise<string> {
  const messages: ChatTurn[] = [
    {
      role: "system",
      content: `${SYSTEM_PROMPT}

The user is asking a general question rather than logging a meal, so you have no figures for it. Answer from general nutrition knowledge, keep it brief, and if the answer depends on amounts you do not know, say so instead of guessing. ${GOAL_CONTEXT[input.goal]}`,
    },
    ...input.history.map(
      (turn): ChatTurn => ({
        role: turn.fromUser ? "user" : "assistant",
        content: turn.text,
      }),
    ),
    { role: "user", content: input.question },
  ];

  return completeText({ model: COACH_MODEL.value(), messages, temperature: 0.6 });
}

/**
 * Renders the analysis as the only nutrition context the model sees. Built from
 * the analysis object rather than passed through from anywhere upstream, so the
 * coach cannot be shown a figure the engine did not produce.
 */
function describeAnalysis(analysis: NutritionAnalysisWire, goal: GoalWire): string {
  const totals = totalsOf(analysis);

  const lines = [
    `Meal: ${analysis.mealName}`,
    "",
    "Verified nutrition:",
    `- Calories: ${totals.calories} kcal`,
    `- Protein: ${totals.protein} g`,
    `- Carbohydrates: ${totals.carbs} g`,
    `- Fat: ${totals.fat} g`,
  ];

  const micros: [string, number | null, string][] = [
    ["Fibre", analysis.micros.fiber, "g"],
    ["Sugars", analysis.micros.sugar, "g"],
    ["Saturated fat", analysis.micros.saturatedFat, "g"],
    ["Sodium", analysis.micros.sodiumMg, "mg"],
    ["Potassium", analysis.micros.potassiumMg, "mg"],
    ["Calcium", analysis.micros.calciumMg, "mg"],
    ["Iron", analysis.micros.ironMg, "mg"],
    ["Vitamin C", analysis.micros.vitaminCMg, "mg"],
    ["Cholesterol", analysis.micros.cholesterolMg, "mg"],
  ];
  for (const [label, value, unit] of micros) {
    if (value !== null) lines.push(`- ${label}: ${value} ${unit}`);
  }

  lines.push("", "Ingredients:");
  for (const item of analysis.items) {
    lines.push(
      `- ${item.foodItem.name}, ${Math.round(item.foodItem.grams)} g` +
        ` (${Math.round((item.foodItem.caloriesPer100g * item.foodItem.grams) / 100)} kcal)`,
    );
  }

  if (analysis.unmatched.length > 0) {
    lines.push(
      "",
      `Not included in these totals, because the food database had no match: ${analysis.unmatched.join(", ")}.`,
      "Mention this, so the user knows the totals are incomplete.",
    );
  }

  lines.push("", GOAL_CONTEXT[goal]);
  return lines.join("\n");
}

function totalsOf(analysis: NutritionAnalysisWire): {
  calories: number;
  protein: number;
  carbs: number;
  fat: number;
} {
  let calories = 0;
  let protein = 0;
  let carbs = 0;
  let fat = 0;

  for (const { foodItem } of analysis.items) {
    const factor = foodItem.grams / 100;
    calories += foodItem.caloriesPer100g * factor;
    protein += foodItem.proteinPer100g * factor;
    carbs += foodItem.carbsPer100g * factor;
    fat += foodItem.fatPer100g * factor;
  }

  return {
    calories: Math.round(calories),
    protein: Math.round(protein),
    carbs: Math.round(carbs),
    fat: Math.round(fat),
  };
}
