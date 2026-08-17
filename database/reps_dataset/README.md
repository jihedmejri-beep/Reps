# REPS Exercise Dataset & Backend Data Platform

The ETL pipeline **and production backend data platform** for the REPS
mobile application. It downloads exercise data from [wger](https://wger.de)
(and, in the future, other providers), cleans/translates/normalizes it,
downloads and integrity-checks every image/video/SVG asset, loads
everything into a normalized PostgreSQL database, exports a multilingual
search index, and produces a read-only SQLite offline cache for Android.

```
Download -> Clean -> Merge -> Download Assets (images/videos/muscle+body SVGs)
         -> Validate -> Export JSON -> Load PostgreSQL -> Index Search -> Export SQLite (Android)
```

This file covers the JSON-producing pipeline (unchanged since Phase 1/2).
The database/asset/search layer built on top of it is documented in
**[`docs/`](docs/)**:

| Doc | Covers |
|---|---|
| [`docs/database.md`](docs/database.md) | ER diagram, table-by-table design, migrations, seeding |
| [`docs/assets.md`](docs/assets.md) | Image/video/SVG download, checksums, dedup, alignment validation |
| [`docs/search.md`](docs/search.md) | Multilingual full-text/fuzzy/accent-insensitive search |
| [`docs/deployment.md`](docs/deployment.md) | First deploy, env vars, verified command sequence |
| [`docs/maintenance.md`](docs/maintenance.md) | Routine refresh, translations, asset/DB upkeep |
| [`docs/provider-integration.md`](docs/provider-integration.md) | How a new provider's data reaches the DB with zero schema change |
| [`docs/android-integration.md`](docs/android-integration.md) | Online vs. offline data path, asset resolution, muscle overlays |
| [`docs/roadmap.md`](docs/roadmap.md) | What's next |
| [`ENGINEERING_REPORT.md`](ENGINEERING_REPORT.md) | What was built/verified, tradeoffs, known limitations, readiness score |

## Table of contents

- [Installation](#installation)
- [Running the project](#running-the-project)
- [Architecture](#architecture)
- [Configuration](#configuration)
- [Folder structure](#folder-structure)
- [Updating data](#updating-data)
- [Idempotency](#idempotency)
- [Future providers](#future-providers)
- [Testing](#testing)
- [Troubleshooting](#troubleshooting)

## Installation

Requires Python 3.11+.

```bash
python -m venv .venv
.venv\Scripts\activate        # Windows
source .venv/bin/activate     # macOS / Linux
pip install -r requirements.txt
```

## Running the project

```bash
python run.py
```

This downloads every dataset needed from wger, filters to English/French/
Arabic, merges translations, downloads all images and videos, validates
the result, and writes:

- `data/output/reps_exercises.json` — the production database
- `data/output/validation_report.json` — every data-quality issue found
- `data/output/statistics.json` — build statistics

Useful flags:

| Flag | Effect |
| --- | --- |
| `--providers wger custom` | Choose which registered providers to run (default: `wger`) |
| `--languages en fr` | Keep only a subset of `{en, fr, ar}` for this run |
| `--skip-media` | Skip image/video downloads (fast iteration on the JSON structure) |
| `--skip-download` | Rebuild from already-cached raw data; no network calls at all |
| `--force` | Ignore every cache: re-download and re-process everything from scratch |
| `--download-workers N` / `--media-workers N` | Tune concurrency |
| `--verbose` / `-v` | Debug-level console logging |

Run `python run.py --help` for the full, current list.

## Architecture

### Canonical data source

wger's API v2 splits exercise data across several resources: a base
`exercise` (uuid, category, muscles, equipment, `variation_group`), one
`exercise-translation` per language, plus `exercisealias`,
`exercisecomment`, `exerciseimage` and `video`, each linked by foreign
key. wger also exposes **`exerciseinfo`**, a fully denormalized read view
that nests all of the above — every kept translation (with its aliases
and notes already inlined), the resolved category/muscle/equipment
objects, the license, and every image/video — into one JSON object per
exercise UUID.

**`exerciseinfo` is REPS's canonical source.** Building the database from
it means every relationship is already resolved by the API itself, so the
pipeline never has to join FK ids across five different endpoints or risk
drifting out of sync with wger's own relational integrity. The atomic
endpoints (`exercise`, `exercise-translation`, `exercisealias`,
`exercisecomment`, `exercisecategory`, `muscle`, `equipment`, `language`,
`license`, `exerciseimage`, `video`) are still downloaded in full as
supplementary raw datasets — used for the `language` id → ISO code lookup
that `exerciseinfo` itself doesn't inline, for cross-validation, and to
keep the door open for a future pipeline stage that needs the atomic
shape without a second network round-trip.

wger has no standalone "variation" endpoint. Exercises that are
variations of one another (e.g. barbell vs. dumbbell bench press) instead
share a `variation_group` UUID on the base `exercise` resource. REPS keeps
each variation as its own separate exported record — it does not collapse
them into one object — and simply carries `variation_group` through as
`variationGroup`, so the app can group and offer swaps between them.

### Pipeline stages

| Stage | Module | Responsibility |
| --- | --- | --- |
| Download | `pipeline/downloader.py`, `providers/*.py` | Paginate every configured endpoint, cache pages on disk, resume on interruption |
| Clean | `pipeline/cleaner.py` | Keep only `en`/`fr`/`ar` translations; normalize unicode/whitespace/capitalization; dedupe images, videos, equipment, muscles, aliases and translations; drop malformed references |
| Merge | `pipeline/merger.py` | Merge per-language translations into `name{en,fr,ar}` / `description{en,fr,ar}`; flatten+dedupe aliases across languages; carry `variationGroup` through |
| Download assets | `pipeline/media_downloader.py` | Concurrently download every image/video into `assets/exercises/{id}/`, with sha256 checksum, width/height, Content-Type validation, HTTP 429 handling, and per-exercise duplicate removal (see [`docs/assets.md`](docs/assets.md)) |
| Download SVG assets | `pipeline/svg_assets.py` | Download every muscle's main/secondary SVG + the front/back body diagrams, validate canvas alignment (see [`docs/assets.md`](docs/assets.md)) |
| Validate | `pipeline/validator.py` | Flag duplicate UUIDs/ids, missing fields, broken references/media, etc. Never raises or halts the run |
| Export | `pipeline/exporter.py` | Write the final `reps_exercises.json` in the exact required schema, sorted by id for deterministic diffs |
| Statistics | `pipeline/exporter.py` | Write `statistics.json` and print a build summary |
| *(separate scripts, run after the above)* | `db/migrate.py`, `db/seed.py`, `pipeline/search_indexer.py`, `db/sqlite_export.py` | Apply the PostgreSQL schema, load it, build the search index, export the Android SQLite cache — see [`docs/database.md`](docs/database.md) and [`docs/deployment.md`](docs/deployment.md) |

> **Videos are currently disabled** (`config.VIDEOS_ENABLED = False`) — a
> product decision to drop the ~3GB of video assets. `pipeline/cleaner.py`
> strips them immediately after download so nothing downstream ever sees
> one; the DB/dataclass fields are kept, not dropped, so this is reversible
> with a single config flag. See [`docs/assets.md`](docs/assets.md) →
> "Videos (disabled by policy)".

Cleaning and merging are deliberately two separate stages: normalizing,
filtering and deduplicating translations only makes sense while each
language still has its own distinct entry, so all of that happens in
`cleaner.py` *before* `merger.py` collapses them into one localized
object per exercise.

### Provider system

Every data source implements the same contract
(`providers/base.py::ExerciseProvider`): `fetch_raw()` downloads into
`data/raw/<provider>/`, and `normalize()` reads that cache and yields
`pipeline.models.RawExerciseRecord` objects. The rest of the pipeline
(cleaner, merger, media downloader, validator, exporter) only ever
consumes `RawExerciseRecord` — it has no idea wger, or any other vendor,
exists. See [Future providers](#future-providers).

### Data model

`pipeline/models.py` defines two families of dataclasses:

- **Intermediate** (`RawExerciseRecord`, `TranslationRecord`,
  `MuscleRef`, `EquipmentRef`, `MediaAsset`, `LicenseInfo`) — the
  provider → pipeline contract, and the shape cleaner/merger operate on.
- **Export** (`MergedExercise`, `ExportedExercise`) — `MergedExercise` is
  the post-merge, pre-media-download record (still holding rich objects
  so the media downloader can set `MediaAsset.local_path` in place);
  `ExportedExercise.to_dict()` is a typed mirror of the exact
  `reps_exercises.json` schema.

## Configuration

Everything tunable lives in `config.py`: paths, the wger base URL and
endpoint map, HTTP retry/backoff/pagination settings, concurrency, kept
languages, and default enabled providers. Two environment variables are
read at import time:

| Variable | Purpose |
| --- | --- |
| `REPS_WGER_BASE_URL` | Override the wger API base URL (default: `https://wger.de/api/v2`) |
| `REPS_WGER_API_TOKEN` | Optional wger API token (all endpoints used here are public/read-only, but a token raises wger's rate limit) |

## Folder structure

```
reps_dataset/
├── README.md
├── requirements.txt
├── config.py                 # single source of truth for paths/endpoints/tuning
├── run.py                    # CLI entry point / orchestrator
├── .gitignore
│
├── pipeline/
│   ├── downloader.py          # generic resumable paginated API downloader
│   ├── cleaner.py             # language filter + normalization + dedup
│   ├── merger.py               # translation merge -> localized objects
│   ├── media_downloader.py    # exercise image/video download: checksums, dims, dedup (Epic 1)
│   ├── svg_assets.py           # muscle + body diagram SVG download/validation (Epic 1)
│   ├── search_indexer.py       # builds search keywords, writes exercise_search_index.json (Epic 4)
│   ├── validator.py           # validation_report.json generation
│   ├── exporter.py            # reps_exercises.json + statistics.json
│   ├── logger.py               # centralized logging setup
│   ├── helpers.py              # HTTP session, text normalization, JSON I/O utilities
│   └── models.py               # dataclasses shared across every stage
│
├── db/                          # PostgreSQL layer (Epic 2/3/4) -- see docs/database.md
│   ├── migrations/               # 0001_init.sql, 0002_search.sql (never hand-edited elsewhere)
│   ├── migrate.py                 # apply migrations / regenerate schema.sql
│   ├── schema.sql                  # GENERATED: full frozen schema (concatenated migrations)
│   ├── seed.py                      # loads reps_exercises_clean.json + asset reports into Postgres
│   ├── sqlite_export.py              # Postgres -> reps_offline.sqlite (Android cache)
│   └── db_conn.py                     # shared DSN-parsing connection helper
│
├── assets/                       # production asset tree (Epic 1) -- see docs/assets.md
│   ├── exercises/{id}/main.{ext}, image_2.{ext}, video_1.{ext}, ...
│   ├── muscles/{main,secondary}/*.svg, muscle_svg_map.json
│   └── body/front.svg, back.svg
│
├── docs/                         # database/asset/search/deployment/... documentation
│
├── providers/
│   ├── base.py                 # ExerciseProvider contract + registry
│   ├── wger.py                 # wger REST API v2 (fully implemented)
│   ├── exercisedb.py           # scaffold for a future ExerciseDB integration
│   ├── gymvisual.py            # scaffold for a future GymVisual integration
│   └── custom.py                # ingests hand-curated / vendor-exported JSON
│
├── data/
│   ├── raw/                    # one subfolder per provider per endpoint, paged
│   ├── cleaned/                # exercises_cleaned.json (post Step 3+5)
│   ├── merged/                 # exercises_merged.json (post Step 4)
│   ├── output/                 # reps_exercises*.json, *_report.json, reps_offline.sqlite
│   ├── media/{images,videos}/  # LEGACY pre-Epic-1 cache, keyed by asset uuid -- superseded by assets/
│   └── logs/                   # rotating pipeline.log
│
└── tests/
    ├── conftest.py
    ├── test_downloader.py
    ├── test_cleaner.py
    ├── test_merger.py
    ├── test_validator.py
    └── test_exporter.py
```

## Updating data

Just run `python run.py` again. Already-downloaded raw pages, already
-downloaded media and completed provider manifests are reused, so a
routine refresh only fetches what changed upstream — new pages report a
different `count` in the first paginated response, which naturally
extends the offset list; edited exercises are still re-fetched on their
existing page whenever that page is re-downloaded. To force a full,
byte-for-byte refresh (e.g. after a wger schema change), run
`python run.py --force`.

## Idempotency

Re-running `python run.py` never duplicates data:

- **Downloads** are cached per page (`data/raw/<provider>/<endpoint>/page_*.json`)
  behind a `_manifest.json` that records completion; a completed endpoint
  is skipped entirely unless `--force` is passed.
- **Media** files are named after their stable source `uuid`, and a
  non-empty existing file is skipped.
- **Export** is a full, deterministic rebuild from the cleaned/merged
  in-memory state on every run (sorted by `id`), not an incremental
  append — so there is no accumulation path for duplicates to begin with.

## Future providers

`providers/exercisedb.py` and `providers/gymvisual.py` are registered,
fully wired into `run.py --providers`, and share the exact same
`ExerciseProvider` contract as `providers/wger.py` — but their
`fetch_raw()`/`normalize()` intentionally raise a `NotImplementedError`
with the concrete next steps, rather than guessing at those vendors'
field names. This project's rule of never assuming a JSON schema applies
just as much to future integrations as it did to wger: inspect the real
API first, then implement.

To wire in a new provider (ExerciseDB, GymVisual, LiftManual, or
anything else):

1. Inspect the vendor's real API responses.
2. Implement `fetch_raw()` — typically by reusing
   `pipeline.downloader.PaginatedApiDownloader` if it's limit/offset
   paginated, or a bespoke fetch loop otherwise — caching raw pages under
   `self.raw_dir`.
3. Implement `normalize()` to map verified field names onto
   `pipeline.models.RawExerciseRecord`.
4. Register the class with `@register_provider("your-provider-name")`
   and add the module to the imports in `providers/__init__.py`.
5. Run `python run.py --providers wger your-provider-name`.

No other pipeline file needs to change — that's the point of the
`RawExerciseRecord` contract.

If a source has no API at all (a one-off export, a hand-curated
correction set), use `providers/custom.py` instead: drop a JSON file
matching its documented schema into `data/raw/custom/input/` and enable
`--providers wger custom`.

## Testing

```bash
pytest
```

Tests never hit the network: `test_downloader.py` replays a scripted
fake `requests.Session`, and every other test operates on in-memory
dataclasses or a `tmp_path`-redirected filesystem.

## Troubleshooting

**`KeyError: Unknown provider '...'`** — check `providers/__init__.py`
imports your module (so `@register_provider` has run) and that the name
matches what you pass to `--providers`.

**A wger endpoint download never completes / keeps failing** — check
`data/logs/pipeline.log` for the specific offset and status code; wger
returns `429` under heavy load, which the downloader retries with
backoff automatically, but a real outage will eventually show up as
`failed_offsets` in `data/raw/<provider>/<endpoint>/_manifest.json` and a
`completed: false` state that the next run will retry from.

**Media downloads are slow** — tune `--media-workers`, or run once with
`--skip-media` to validate the JSON structure quickly, then a follow-up
run to backfill media (already-downloaded files are skipped).

**Stale data after a wger fix upstream** — run
`python run.py --force` to bypass every cache and rebuild from scratch.

**Arabic/French text looks garbled** — `reps_exercises.json` is UTF-8
with `ensure_ascii=False`; make sure whatever you open it with treats the
file as UTF-8 (most editors auto-detect this, but a `Get-Content` in
PowerShell without `-Encoding utf8` can mis-render it).
