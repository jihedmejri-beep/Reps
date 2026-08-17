# How future providers integrate

`README.md` → "Adding a new provider" already documents the 5-step contract
for `providers/*.py` (implement `fetch_raw()` + `normalize()`, register with
`@register_provider`, produce `RawExerciseRecord`s). This document covers
what happens **after** that — how a new provider's data flows into the
database without any schema change.

## The mapping is mechanical, not structural

Every provider's `normalize()` output already converges on the same
`RawExerciseRecord`/`MediaAsset` shape (see `pipeline/models.py`), which
`db/seed.py` and the schema (`db/migrations/0001_init.sql`) are built
around:

| `RawExerciseRecord` field | Database destination |
|---|---|
| `id`, `uuid`, `source` | `exercises.external_id`, `exercises.id`, `exercises.source_id` (via `sources.slug`) |
| `category` | `categories.name` (shared across providers — `"Abs"` from wger and `"Abs"` from ExerciseDB are the same category row) |
| `equipment[]` | `equipment` rows keyed by `(source_id, external_id)` — a provider's own equipment ids never collide with another's |
| `primary_muscles[]`/`secondary_muscles[]` | `muscles` rows, same `(source_id, external_id)` scoping, joined via `exercise_muscles.role` |
| `translations[]` | `exercise_translations`, one row per `(exercise_id, language_code)` |
| `images[]`/`videos[]` | `exercise_images`/`exercise_videos` |

## Fields a new provider has that WGER doesn't

This is exactly what `tags`, `body_regions`, `movement_patterns`,
`difficulties`, `exercise_types`, `forces`, `mechanics` exist for (see
`docs/database.md` → "Why this shape"). For example, ExerciseDB's API
returns a `bodyPart` and `target` field per exercise — mapping those in is:

1. Add rows to `body_regions`/`movement_patterns` as needed (a handful of
   `INSERT ... ON CONFLICT DO NOTHING` statements, not a migration).
2. In `providers/exercisedb.py::normalize()`, populate the corresponding
   field on `RawExerciseRecord` (this does require adding the field to the
   dataclass if it doesn't map onto an existing one — that's a one-time,
   additive dataclass change, not a schema redesign).
3. In `db/seed.py::upsert_exercise`, set `body_region_id`/`movement_pattern_id`
   from a lookup, exactly like `category_id` is set today.

No table is dropped or altered, no existing column changes meaning, no
migration touches `exercises`' primary key or any FK target — this is the
concrete guarantee "the schema never needs redesigning" cashes out to.

## Fields WGER has that a new provider doesn't

Leave the corresponding column `NULL` for that provider's rows. `db/seed.py`
already does this everywhere (e.g. `difficulty_id`/`is_warmup`/`is_stretch`
are `NULL` for every current wger-sourced exercise) — never invent a value
to fill a gap.

## Multiple providers, one exercise

If a future provider's data should be *merged into* an existing wger
exercise (rather than coexist as a separate row) — e.g. richer step-by-step
instructions for the same physical exercise — that's a `pipeline/merger.py`
concern (cross-provider deduplication/merging), not a database concern; the
database only ever sees whatever `MergedExercise` objects come out of the
merger. This is out of scope for the current pipeline (which runs a single
provider, `wger`, by default) and is called out in `docs/roadmap.md`.
