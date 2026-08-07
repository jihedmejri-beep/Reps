import Groq from "groq-sdk";
import { GROQ_API_KEY } from "../config";

let cached: Groq | null = null;

/**
 * Built lazily and reused across warm invocations. Constructing it at module
 * scope would read the secret at deploy-analysis time, when it is not bound.
 */
export function groq(): Groq {
  if (cached === null) {
    cached = new Groq({ apiKey: GROQ_API_KEY.value(), maxRetries: 2 });
  }
  return cached;
}

export interface ChatTurn {
  role: "system" | "user" | "assistant";
  content: string;
}

/**
 * A chat completion constrained to `schema`.
 *
 * `strict: true` is the point of this helper: Groq applies constrained decoding
 * so the response is guaranteed to match the schema at the token level, rather
 * than the model being asked nicely and usually complying. It is only supported
 * on the gpt-oss models - see UNDERSTANDING_MODEL in config.ts.
 *
 * Groq's strict mode rejects several JSON Schema keywords (`oneOf`, `allOf`,
 * `not`, `pattern`, `patternProperties`), requires `additionalProperties: false`
 * on every object, and requires every property to appear in `required`.
 * Optionality is expressed as a null union - `{"type": ["string", "null"]}` -
 * not by omission from `required`.
 */
export async function completeStructured<T>(options: {
  model: string;
  messages: ChatTurn[];
  schemaName: string;
  schema: Record<string, unknown>;
  temperature?: number;
}): Promise<T> {
  const response = await createCompletion({
    model: options.model,
    messages: options.messages,
    temperature: options.temperature ?? 0.2,
    response_format: {
      type: "json_schema",
      json_schema: {
        name: options.schemaName,
        strict: true,
        schema: options.schema,
      },
    },
  });

  const content = extractContent(response);
  try {
    return JSON.parse(content) as T;
  } catch {
    // Constrained decoding should make this unreachable. If it fires, the model
    // in config no longer supports strict mode - fail loudly rather than let a
    // half-parsed meal reach the nutrition engine.
    throw new Error(
      `Structured output was not valid JSON. Is ${options.model} still a strict-mode model?`,
    );
  }
}

/** A plain prose completion. Used by the coach, which has no schema. */
export async function completeText(options: {
  model: string;
  messages: ChatTurn[];
  temperature?: number;
  maxTokens?: number;
}): Promise<string> {
  const response = await createCompletion({
    model: options.model,
    messages: options.messages,
    temperature: options.temperature ?? 0.6,
    max_tokens: options.maxTokens ?? 700,
  });

  return extractContent(response).trim();
}

/**
 * The SDK's published parameter types trail its supported request fields -
 * `response_format: {type: "json_schema"}` in particular. Groq accepts it on
 * the wire regardless, so the request body is built here as a plain object and
 * the response is narrowed by hand in extractContent. Going through the SDK
 * still buys us auth, retries, and error shapes.
 */
function createCompletion(body: Record<string, unknown>): Promise<unknown> {
  const completions = groq().chat.completions as unknown as {
    create: (params: Record<string, unknown>) => Promise<unknown>;
  };
  return completions.create(body);
}

function extractContent(response: unknown): string {
  const choice = (response as { choices?: { message?: { content?: string | null } }[] })
    .choices?.[0];
  const content = choice?.message?.content;
  if (typeof content !== "string" || content.length === 0) {
    throw new Error("Groq returned an empty completion.");
  }
  return content;
}
