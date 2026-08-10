import { UNDERSTANDING_MODEL } from "../config";
import type {
  ExchangeWire,
  MealDraftWire,
  UnderstandResponseWire,
} from "../contract";
import { completeStructured, type ChatTurn } from "../groq/client";

/**
 * Agent 1 - Meal Understanding.
 *
 * Turns natural language into a structured, USDA-ready ingredient list, asking
 * follow-up questions until it has enough. It does not compute nutrition, and
 * structurally cannot: the schema below has no field for a calorie, a macro, or
 * any other figure. That is enforcement, not instruction - a prompt can be
 * argued with, a constrained-decoding schema cannot.
 */

/**
 * Written to Groq's strict-mode rules: every property is in `required`,
 * every object sets `additionalProperties: false`, and optional values are null
 * unions rather than omissions.
 */
const MEAL_DRAFT_SCHEMA: Record<string, unknown> = {
  type: "object",
  additionalProperties: false,
  required: [
    "assistant_message",
    "meal_name",
    "ingredients",
    "ready_for_analysis",
    "confidence",
    "follow_up_questions",
    "ambiguities",
  ],
  properties: {
    assistant_message: {
      type: "string",
      description:
        "What to say to the user in this turn. Either the follow-up questions phrased conversationally, or a short confirmation of what you understood.",
    },
    meal_name: {
      type: ["string", "null"],
      description: "A short name for the whole meal, or null if not yet clear.",
    },
    ingredients: {
      type: "array",
      description: "Every food and beverage identified so far.",
      items: {
        type: "object",
        additionalProperties: false,
        required: ["name", "raw_text", "grams", "quantity_text", "cooking_method"],
        properties: {
          name: {
            type: "string",
            description:
              "Normalised toward USDA FoodData Central vocabulary, e.g. 'Chicken, breast, meat only, roasted' rather than 'chicken'.",
          },
          raw_text: {
            type: "string",
            description: "The user's own words for this item, kept verbatim.",
          },
          grams: {
            type: ["number", "null"],
            description:
              "Weight in grams. Null unless the user gave an amount. Converting a stated household measure (e.g. '1 cup rice') to grams is allowed; inventing an unstated portion is not.",
          },
          quantity_text: {
            type: ["string", "null"],
            description: "The user's phrasing of the amount, or null.",
          },
          cooking_method: {
            type: ["string", "null"],
            description:
              "grilled, fried, baked, steamed, boiled, raw, roasted, or null if not stated.",
          },
        },
      },
    },
    ready_for_analysis: {
      type: "boolean",
      description:
        "True only when every ingredient is identified and has a grams value.",
    },
    confidence: {
      type: "number",
      description: "0 to 1, how confident you are in the extraction.",
    },
    follow_up_questions: {
      type: "array",
      description: "Empty when ready_for_analysis is true.",
      items: { type: "string" },
    },
    ambiguities: {
      type: "array",
      description: "Anything genuinely unclear, surfaced rather than guessed.",
      items: { type: "string" },
    },
  },
};

const SYSTEM_PROMPT = `You are the meal understanding stage of a nutrition app. Your only job is to work out exactly what the user ate and turn it into a structured ingredient list.

What you do:
- Identify every food and beverage, including ones implied by a dish name (a burger implies a bun and a patty).
- Detect the cooking method when stated.
- Normalise each name toward USDA FoodData Central vocabulary, because a database lookup happens downstream.
- Record amounts the user gave you. If they gave a household measure, convert it to grams.
- Ask concise follow-up questions whenever something you need is missing.

What you must never do:
- Never state or estimate calories, macros, or any other nutrition figure. A separate system computes those from a verified database, and a number from you would be a fabrication.
- Never give dietary advice. A separate coach does that, after the real numbers exist.
- Never invent an ingredient the user did not mention or that the dish does not clearly imply.
- Never invent a portion size. If you do not know how much, ask.

Set ready_for_analysis to true only when every ingredient has a grams value. Until then, keep asking - but ask about the things that matter most first, and ask at most three questions in one turn. Users abandon long interrogations.

Keep assistant_message short and natural. You are a helpful person taking down an order, not a form.`;

export async function runMealUnderstanding(input: {
  history: ExchangeWire[];
  message: string;
}): Promise<UnderstandResponseWire> {
  const messages: ChatTurn[] = [
    { role: "system", content: SYSTEM_PROMPT },
    ...input.history.map(
      (turn): ChatTurn => ({
        role: turn.fromUser ? "user" : "assistant",
        content: turn.text,
      }),
    ),
    { role: "user", content: input.message },
  ];

  const raw = await completeStructured<RawMealDraft>({
    model: UNDERSTANDING_MODEL.value(),
    messages,
    schemaName: "meal_draft",
    schema: MEAL_DRAFT_SCHEMA,
    temperature: 0.2,
  });

  return { message: raw.assistant_message, draft: toWire(raw) };
}

/** The snake_case shape the model fills, before it is mapped to the app's contract. */
interface RawMealDraft {
  assistant_message: string;
  meal_name: string | null;
  ingredients: {
    name: string;
    raw_text: string;
    grams: number | null;
    quantity_text: string | null;
    cooking_method: string | null;
  }[];
  ready_for_analysis: boolean;
  confidence: number;
  follow_up_questions: string[];
  ambiguities: string[];
}

function toWire(raw: RawMealDraft): MealDraftWire {
  const ingredients = raw.ingredients
    .filter((item) => item.name.trim().length > 0)
    .map((item) => ({
      name: item.name.trim(),
      rawText: item.raw_text.trim(),
      // A zero or negative weight is not a quantity; treat it as missing so the
      // readiness check below asks for it rather than sending 0 g to USDA.
      grams: item.grams !== null && item.grams > 0 ? item.grams : null,
      quantityText: item.quantity_text,
      cookingMethod: item.cooking_method,
    }));

  // The model reports readiness, but the engine is what has to live with the
  // answer, so re-derive it here. This is the one place a mistaken `true` can
  // be caught before it becomes a meal logged with a missing portion.
  const everyItemWeighed =
    ingredients.length > 0 && ingredients.every((item) => item.grams !== null);
  const readyForAnalysis = raw.ready_for_analysis && everyItemWeighed;

  return {
    mealName: raw.meal_name,
    ingredients,
    readyForAnalysis,
    confidence: clamp01(raw.confidence),
    // If we overrode readiness, the model's (empty) question list is no longer
    // right - fall back to naming what is actually missing.
    followUpQuestions: readyForAnalysis
      ? []
      : raw.follow_up_questions.length > 0
        ? raw.follow_up_questions
        : missingQuantityQuestions(ingredients),
    ambiguities: raw.ambiguities,
  };
}

function missingQuantityQuestions(
  ingredients: MealDraftWire["ingredients"],
): string[] {
  const unweighed = ingredients.filter((item) => item.grams === null);
  if (unweighed.length === 0) return ["What did you have?"];
  return unweighed
    .slice(0, 3)
    .map((item) => `Roughly how much ${item.rawText || item.name} did you have?`);
}

function clamp01(value: number): number {
  if (!Number.isFinite(value)) return 0;
  return Math.min(1, Math.max(0, value));
}
