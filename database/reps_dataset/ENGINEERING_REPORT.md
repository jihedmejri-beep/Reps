# REPS Backend Data Platform — Engineering Report

Scope: Epics 1–4 from the backend-platform brief (asset system, frozen
schema, PostgreSQL database layer, multilingual search), built on top of
the existing ETL pipeline (Phase 1) and the cleaned/translated dataset
(Phase 2). This report covers what was actually built and verified in this
session — every claim below was executed against real data, not asserted.

## Addendum (follow-up session): videos dropped, cloud image prep added

- **Videos disabled by product decision** (`config.VIDEOS_ENABLED = False`),
  to reduce asset size. `pipeline/cleaner.py` now strips `videos` off every
  exercise right after download — the single point of control, so nothing
  downstream needed a code change. Executed: 73 video files (2.9 GB)
  deleted from `assets/exercises/`, all `exercise_videos` rows cleared from
  Postgres, `db/seed.py`/`pipeline/search_indexer.py`/`db/sqlite_export.py`
  re-run and verified — `assets/` dropped from 3.1 GB to 210 MB, DB/SQLite
  now show 0 video rows. The schema/dataclass fields were kept (not
  dropped) so this is a reversible policy flag, not a redesign — text
  content (translations) was explicitly **not** touched, per direction that
  hardcoding it into the app is an app-side decision, not a pipeline one.
- **Cloud image prep added, no upload performed** (no bucket/provider was
  chosen): `db/generate_upload_manifest.py` (reads Postgres + the
  filesystem, writes `data/output/cloud_upload_manifest.json` — 368 files,
  ~206 MB, each with its intended object key/size/checksum, verified run)
  and `pipeline/helpers.py::resolve_asset_url()` (the one place a future
  API layer resolves `local_path` to either a local or CDN URL, gated by
  `REPS_CLOUD_ASSETS_BASE_URL`). See `docs/assets.md` → "Cloud migration".

## Completed tasks

### Epic 1 — Asset system
- `pipeline/media_downloader.py` rewritten: exercise-scoped `assets/
  exercises/{id}/main.{ext}` layout, streamed SHA-256 checksum, Pillow-based
  width/height, Content-Type validation, dedicated HTTP 429/`Retry-After`
  handling, per-exercise duplicate removal, resumable/skip-cached.
- `pipeline/svg_assets.py` (new): downloads every muscle's main/secondary
  SVG + the front/back body diagrams (located via direct probing of WGER's
  static asset paths — not exposed by the REST API — both confirmed
  reachable), validates canvas alignment.
- **Verified live**: 828 exercises processed; 414 assets (336 images + 78
  videos) accounted for with **0 failures** in the final run; 3.6 GB
  downloaded; 24 duplicate images correctly detected and removed; 30/30
  muscle SVGs + both body diagrams downloaded; **0 alignment issues**.
- `data/output/image_download_report.json`, `data/output/
  svg_validation_report.json`, `assets/muscles/muscle_svg_map.json`
  generated as specified.

### Epic 2 — Frozen schema
- Every requested entity (Exercise, Translation, Muscle, Equipment,
  Category, Images, SVG assets, Aliases, Licenses, Tags, Body regions,
  Movement patterns, Difficulty, Exercise type, Force, Mechanic, Version,
  Source, Search keywords) has a table in `db/migrations/0001_init.sql`,
  every one with a stable id, `created_at`/`updated_at`, and
  `schema_migrations`-tracked versioning. Taxonomy entities no current
  provider supplies (`tags`, `body_regions`, `movement_patterns`,
  `difficulties`, `exercise_types`, `forces`, `mechanics`) exist as
  ready-to-populate tables with nullable FKs — left `NULL`, never
  backfilled with invented data. `Warmup`/`Stretch` are represented as
  `exercises.is_warmup`/`is_stretch` booleans (nullable, `NULL` today) —
  the brief listed them as entities but the underlying data is inherently
  a per-exercise flag, not a separate lookup table, so that's the shape
  they took.

### Epic 3 — Database layer
- `db/migrations/0001_init.sql` + `0002_search.sql`, applied via
  `db/migrate.py` against a disposable Docker `postgres:16` instance.
  `db/schema.sql` is **generated** (`python db/migrate.py dump-schema`)
  from the migrations, never hand-duplicated.
- `db/seed.py`: loads `reps_exercises_clean.json` (Phase 2 text) + Epit 1's
  fresh asset metadata, upserts every table by stable id.
- `db/sqlite_export.py`: full Postgres → SQLite mirror for Android,
  including an FTS5 virtual table.
- **Verified live** (see "What was actually executed" below): schema
  applied cleanly and idempotently; seed loaded 828 exercises / 2,484
  translations / 336 images / 78 videos / 1,736 muscle links / 710
  equipment links / 95 aliases / 30 muscle SVGs / 2 body diagrams with
  **0 errors**; re-running both migration apply and seed a second time
  produced byte-identical row counts (idempotency confirmed); SQLite
  export row counts matched Postgres exactly.

### Epic 4 — Search
- `db/migrations/0002_search.sql`: `pg_trgm`/`unaccent` extensions,
  `normalize_arabic()`/`normalize_latin()`, trigger-maintained
  `search_vector`/`keywords` on `exercise_translations`, GIN indexes.
- `pipeline/search_indexer.py`: builds per-exercise keywords (aliases,
  equipment, muscles, category), writes them into Postgres and to
  `data/output/exercise_search_index.json`.
- **Verified live** with real queries in all three languages (exact
  queries below) — English full-text ranking, French accent-insensitive
  trigram fuzzy matching, Arabic diacritic-insensitive substring matching
  all returned correct results. SQLite FTS5 offline search also verified.

### Documentation & final review
- `docs/{database,assets,search,deployment,maintenance,provider-
  integration,android-integration,roadmap}.md`; `README.md` extended
  (pipeline stage table, folder structure, doc cross-links) rather than
  duplicated.
- 19 new tests added (`test_media_downloader.py`, `test_svg_assets.py`,
  `test_search_indexer.py`) covering the new pure logic (job/filename
  assignment, extension resolution, checksum dedup, SVG dimension
  parsing/alignment validation, keyword building) without network or DB
  dependencies, consistent with the existing suite's "never hits the
  network" rule. **47/47 tests pass.** `python -m py_compile` clean across
  every module. No TODO/FIXME/dead code found.
- A real bug found and fixed during verification (see "Problems found and
  fixed" below): duplicate assets were being permanently re-downloaded and
  re-deleted on every run instead of being remembered.

## What was actually executed (not simulated)

```
python run.py --skip-download                          # full asset pipeline, real network calls
REPS_DATABASE_URL=... python db/migrate.py apply        # against docker postgres:16
REPS_DATABASE_URL=... python db/seed.py
REPS_DATABASE_URL=... python pipeline/search_indexer.py
REPS_DATABASE_URL=... python db/sqlite_export.py
```

Search queries verified live:
```sql
-- English (found Push-Up, Push-Ups | Incline/Decline/Parallettes, Sled Push)
select name from exercise_translations
where language_code='en' and search_vector @@ plainto_tsquery('english', 'push up');

-- French, accent-insensitive fuzzy (query "developpe" -> found "Développé couché/Arnold/Larsen...")
select name, similarity(search_normalized, normalize_latin('developpe')) as sim
from exercise_translations
where language_code='fr' and search_normalized % normalize_latin('developpe')
order by sim desc;

-- Arabic, diacritic-insensitive (query "عضلات" -> found matching exercise names)
select name from exercise_translations
where language_code='ar' and search_normalized like '%' || normalize_arabic('عضلات') || '%';
```

## Problems found and fixed during verification

1. **Duplicate assets re-downloaded every run.** After the first asset
   run, 24 images were correctly identified and removed as intra-exercise
   duplicates — but because the file was deleted and nothing remembered
   *why*, every subsequent run re-downloaded, re-checksummed, and
   re-deleted the same 24 files. Under concurrent load this wasted
   bandwidth occasionally caused a large (300MB+) video to time out. Fixed
   by persisting a `_duplicate_manifest.json` of known-duplicate uuids per
   exercise, checked *before* any job is built — confirmed fixed (asset
   count correctly dropped from 438 candidate jobs to 414 real ones on
   the next run, 0 duplicates re-processed).
2. **`psycopg2-binary` has no prebuilt wheel for this environment**
   (32-bit Python 3.10) and failed to build from source (no `pg_config`).
   Switched to `pg8000`, a pure-Python driver with the same DB-API 2.0
   surface — documented in `requirements.txt` and `db/db_conn.py`.
3. Two transient large-video download failures were observed while
   repeatedly re-running the full pipeline against the real wger.de
   servers within a short window (this session's own testing load, not a
   production access pattern) — root-caused to concurrency contention
   (confirmed: the same file downloaded cleanly when run single-threaded
   in isolation), not a code defect. Mitigated by raising
   `request_timeout_seconds` (60s → 180s) and defaulting to gentler
   concurrency for large-media-heavy runs; the pipeline is resumable by
   design, so this self-heals on the next run regardless. Final verified
   state: **0 failures.**

## Architecture decisions

- **`reps_exercises.json` (images/videos) + `reps_exercises_clean.json`
  (translated text) are merged in `db/seed.py`, not unified into one
  file.** The two were produced by separate phases of this project
  (Phase 2's translation pass predates Epic 1's asset rework) and merging
  them at seed time avoids re-running either expensive stage just to keep
  a single file in sync.
- **`assets/` (new, exercise-scoped) coexists with `data/media/` (legacy,
  uuid-scoped).** Rather than migrate/delete the old cache, it's left in
  place and documented as superseded — safer than a destructive rename
  given the old cache still has value as a raw fallback.
- **pg8000 over psycopg2** — see "Problems found and fixed" above. Same
  DB-API 2.0 interface, zero behavior change to any SQL, but installs
  everywhere without a C toolchain.
- **Normalization logic lives in SQL, not Python** (`normalize_arabic()`/
  `normalize_latin()`) — one implementation, called by
  `pipeline/search_indexer.py`'s `UPDATE` statements and by any future API
  query layer, instead of risking Python and SQL normalization logic
  drifting apart.
- **Image export object keeps `uuid`+`isMain` alongside the 6 requested
  fields** (`url`/`localPath`/`width`/`height`/`size`/`checksum`) — flagged
  and justified in the approved plan before implementation (stable id for
  the DB layer; thumbnail selection without touching the filesystem).

## Tradeoffs

- `db/seed.py`'s join tables (aliases/equipment/muscles) are rebuilt via
  delete-then-insert per exercise, not diffed. Simple and correct, cheap
  enough at 828 exercises; would need to become a real diff at a
  materially larger scale.
- The SQLite export is a full rebuild every run, not incremental — correct
  and simple, but means every refresh ships the whole file to Android
  rather than a delta (see `docs/roadmap.md`).
- Arabic search uses trigram fuzzy + tokenization, not real morphological
  stemming (Postgres has no Arabic dictionary) — documented honestly in
  `docs/search.md` rather than overclaimed.

## Known limitations

- No HTTP API layer exists yet — this platform produces the database and
  the offline cache; a service to query them in real time is not part of
  this scope (see `docs/roadmap.md`).
- Taxonomy tables (difficulty, exercise type, force, mechanic, movement
  pattern, body region, tags) are schema-ready but **empty** — no current
  provider supplies this data.
- Only one provider (`wger`) has real data; `docs/provider-
  integration.md`'s claim that a second provider fits without a schema
  redesign is a design property, not yet proven by a second real
  integration.
- SQLite's FTS5 offline search has no fuzzy/accent-insensitive matching
  today (only Postgres does) — a real, documented capability gap between
  online and offline search.
- No CI is configured to run `pytest` or the pipeline automatically.

## Technical debt

- `pipeline/media_downloader.py` and `pipeline/svg_assets.py` both know
  how to download-with-retry; the retry loop itself isn't shared (though
  the SVG dimension parser now is, via `pipeline/helpers.py::svg_dimensions`,
  after a mid-build fix). A shared retry/backoff helper would remove the
  remaining duplication if a third asset-downloading module is ever added.
- `db/seed.py` reads two JSON files and merges them by id in Python; if a
  third data source needs merging in, that logic should move into its own
  module rather than growing inline in `seed()`.

## Risk analysis

| Risk | Likelihood | Impact | Mitigation |
|---|---|---|---|
| Large asset downloads timing out under production concurrency | Medium | Low | Retried automatically; resumable; timeout already raised once based on real evidence |
| Arabic search quality perceived as insufficient by users | Medium | Medium | Documented gap + concrete upgrade path (`docs/roadmap.md`) |
| Second provider integration reveals the schema needs a real change | Low-Medium | Medium | Design reviewed against ExerciseDB's known field shape in `docs/provider-integration.md`; not proven until actually attempted |
| `assets/` (3.6GB) served from app-server disk in production | High if deployed as-is | Medium | `docs/deployment.md` explicitly calls for CDN/object storage, not local disk |
| No API layer / no CI | Certain (not built) | High for shipping, zero for this scope | Explicitly out of scope, first two roadmap items |

## Readiness score: **6.5 / 10**

**What earns the score**: every piece that was built was verified against
real data end-to-end, not mocked — real wger downloads, a real Postgres
instance, real multilingual search queries, a real SQLite export with
matching row counts, a real bug found and fixed under verification. The
schema, migrations, and seed/export tooling are genuinely production-shaped
(idempotent, versioned, environment-configurable, documented).

**What holds it back from higher**: no HTTP API sits in front of any of
this yet, so nothing here is reachable by the mobile app today. Only one
provider has real data, so the schema's central "never redesign" claim is
architecturally sound but not battle-tested. No CI, no production Postgres
was ever targeted (only a disposable local one), and the taxonomy tables
are empty. This is a solid, verified data platform foundation — not yet a
deployed backend.
