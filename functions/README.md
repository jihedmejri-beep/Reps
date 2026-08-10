# Reps nutrition backend

The server half of the two-agent nutrition assistant. Three callable Cloud
Functions implement the documented flow:

```
user text ──▶ understandMeal ──▶ MealDraft ──▶ analyseMealAndCoach ──▶ verified nutrition + coaching ──▶ app
                (Agent 1)                        (engine, then Agent 2)
```

- `understandMeal` — Agent 1. Turns a free-text meal description into a
  structured draft (ingredients, grams, cooking methods, follow-up questions).
  It never sees or produces a nutrition figure.
- `analyseMealAndCoach` — the nutrition engine runs first (cache → USDA →
  totals), then Agent 2 is handed **only** the engine's verified output and
  returns prose.
- `askCoach` — Agent 2 answering a follow-up question.

All three require a signed-in Firebase user.

## Why the agents live here

The Groq and USDA keys must never reach the APK — anything shipped to a device
is extractable. Both are Secret Manager secrets read server-side, which is the
entire reason this tier exists.

## Prerequisites

- **Node.js 22** and npm. *Neither is currently installed on the dev machine
  this was written on, so the TypeScript here has not been compiled — run
  `npm run typecheck` before the first deploy.*
- Firebase CLI: `npm install -g firebase-tools`, then `firebase login`.
- The Blaze plan (Cloud Functions v2 and Secret Manager both require it).
- A [Groq API key](https://console.groq.com/keys).
- A [USDA FoodData Central key](https://fdc.nal.usda.gov/api-key-signup.html).

## First-time setup

```bash
npm --prefix functions install

firebase functions:secrets:set GROQ_API_KEY   # paste the key when prompted
firebase functions:secrets:set USDA_API_KEY

npm --prefix functions run typecheck
firebase deploy --only functions
```

The project (`reps-92a5e`) is already pinned in `.firebaserc`, and the region
(`us-central1`) is fixed in `src/config.ts` — it must stay in sync with
`AppConstants.Functions.REGION` on the Android side.

## Turning it on in the app

The app currently binds the **offline fake**, so the assistant works end to end
without a deploy. Switching to the real backend is one line in
`app/src/main/java/com/reps/app/di/RepositoryModule.kt`:

```kotlin
// from
fun bindNutritionAssistantRepository(impl: FakeNutritionAssistantRepository): NutritionAssistantRepository
// to
fun bindNutritionAssistantRepository(impl: FunctionsNutritionAssistantRepository): NutritionAssistantRepository
```

(and fix the import). Nothing else in the app changes.

## Models

Set as deploy-time parameters, so they roll without a code change:

| Parameter             | Default                  | Constraint |
| --------------------- | ------------------------ | ---------- |
| `UNDERSTANDING_MODEL` | `openai/gpt-oss-120b`    | **Must support Groq strict structured outputs** — currently the `openai/gpt-oss-*` family only. A llama model here silently degrades to best-effort JSON and starts returning shapes the client cannot parse. |
| `COACH_MODEL`         | `llama-3.3-70b-versatile` | None; the coach returns prose. |

Override at deploy time:

```bash
firebase deploy --only functions \
  --set-env-vars UNDERSTANDING_MODEL=openai/gpt-oss-20b
```

## Caching

Two tiers, both in Firestore, both checked before any USDA request:

- `nutritionCache` — a whole analysed meal, keyed by a sha256 of its sorted
  `name@grams` pairs (the meal *name* is deliberately excluded, so "chicken and
  rice" and "my lunch" share an entry). Written only when every ingredient
  matched, so a partial result never poisons the cache.
- `ingredientCache` — per-ingredient USDA matches. This is the tier that
  actually keeps request volume down, since meals rarely repeat exactly but
  ingredients do.

Entries older than 30 days (`CACHE_TTL_MS`) are re-fetched.

## Local development

```bash
npm --prefix functions run serve      # emulator on :5001
npm --prefix functions run build:watch
npm --prefix functions run logs
```

Secrets are not available to the emulator by default; export them into the
shell or use a `.secret.local` file for local runs.

## Adding a fourth agent

`src/agents/registry.ts` is the extension point. `defineAgent` supplies the
region, secrets, auth check, instance caps and error mapping, so a new agent is
a handler plus one export:

```ts
export const analyseMealPhoto = defineAgent<PhotoRequest, MealDraftWire>(
  "analyseMealPhoto",
  async (payload, uid) => { /* ... */ },
);
```

Existing callables are untouched by this — which is the point of one function
per stage rather than one endpoint that branches.

## Error contract

Errors carry a `details.reason` the app maps to a typed `AssistantError`:

| `reason`                   | Cause |
| -------------------------- | ----- |
| `NotSignedIn`              | No Firebase auth on the call |
| `RateLimited`              | Groq or USDA returned 429 |
| `ModelUnavailable`         | Groq 5xx |
| `FoodDatabaseUnavailable`  | USDA unreachable, or nothing matched |

Anything else falls back to the raw callable code.
