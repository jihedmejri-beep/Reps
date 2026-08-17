# Future roadmap

Ordered roughly by expected value; none of this blocks the current system
being production-usable (see `ENGINEERING_REPORT.md`'s readiness score for
the honest current-state assessment).

## Near-term

- **Actually upload `assets/` to a cloud bucket** and set
  `REPS_CLOUD_ASSETS_BASE_URL` — `db/generate_upload_manifest.py` prepares
  everything needed (see `docs/assets.md` → "Cloud migration") but no
  provider/bucket was chosen yet, so the upload itself hasn't run.
- **Video re-enablement**, if the product decides it needs video again:
  flip `config.VIDEOS_ENABLED = True` — the DB schema, dataclasses, and
  SQLite export were deliberately kept rather than dropped specifically so
  this needs no migration, just re-downloading the media.
- **Checksum columns for `muscle_svg_assets`/`body_diagrams`** — unlike
  `exercise_images`, these don't store a checksum today;
  `db/generate_upload_manifest.py` computes one ad hoc for its manifest.
  Worth a small migration if these files start changing independently of
  the exercise images.

- **Backend API layer.** Nothing in this repo currently serves HTTP —
  it produces the database and the offline cache. A thin API (REST or
  GraphQL) over the Postgres queries in `docs/search.md` is the next
  concrete step to make this consumable by the mobile app in real time.
- **CI**: run `pytest -q` on every PR; a scheduled job to re-run `run.py`
  against upstream WGER and open a PR when the dataset changes (see
  `docs/deployment.md` → "CI recommendations").
- **SQLite offline versioning/diffing**: today `db/sqlite_export.py` always
  fully rebuilds `reps_offline.sqlite`; a lightweight version marker (and
  eventually incremental sync) would reduce app download size on refresh.

## Medium-term

- **A second provider actually wired in** (ExerciseDB is the most
  API-accessible candidate). This is the first real test of
  `docs/provider-integration.md`'s claims — until a second provider exists,
  "the schema doesn't need redesigning" is a design property, not yet a
  proven one.
- **Populate the taxonomy tables** (`difficulties`, `exercise_types`,
  `forces`, `mechanics`, `movement_patterns`, `body_regions`, `tags`) once a
  provider or manual curation effort actually supplies this data — the
  columns exist and are `NULL`-safe today specifically so this can happen
  without a migration.
- **Real Arabic morphological search** (see `docs/search.md`'s honesty
  note) — either a Postgres extension with an Arabic dictionary, or an
  external search engine (Meilisearch/Typesense/Elasticsearch) in front of
  the same normalized data, if trigram + `simple`-config search proves
  insufficient in practice.
- **SQLite FTS5 fuzzy matching** via `spellfix1` (or equivalent), to close
  the offline-vs-online search capability gap noted in
  `docs/android-integration.md`.

## Longer-term

- **Cross-provider exercise merging** (`pipeline/merger.py` today merges
  per-language translations within one provider's records, not across
  providers) — needed once two providers describe the same physical
  exercise and should collapse into one `exercises` row instead of two.
- **User-generated content** (custom exercises, community translations) —
  the `custom` provider slug and `sources` table already anticipate this,
  but no ingestion/moderation path exists yet.
- **Row-level versioning beyond `updated_at`** (e.g. a full history/audit
  table) if the product ever needs "what did this exercise look like on
  date X" — `exercises.schema_version` today only tracks the schema's own
  version, not row history.
