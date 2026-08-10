# Deployment guide

## Components

1. **The pipeline** (`run.py`, `pipeline/*.py`, `providers/*.py`) — a batch
   job, not a long-running service. Produces `data/output/reps_exercises*.json`,
   the `assets/` tree, and the search index.
2. **PostgreSQL** — the system of record. Schema in `db/migrations/*.sql`.
3. **The SQLite offline export** — a build artifact (`data/output/
   reps_offline.sqlite`), shipped to/downloaded by the Android app.
4. **`assets/`** — static files. In production these belong behind a CDN or
   object storage (S3/GCS/R2) + CDN, not served from the app server's disk;
   `local_path` values are relative paths designed to map 1:1 onto object
   storage keys.

## Environment variables

| Variable | Used by | Default |
|---|---|---|
| `REPS_WGER_API_TOKEN` | `providers/wger.py` | none (public endpoints work unauthenticated) |
| `REPS_WGER_BASE_URL` | `config.py` | `https://wger.de/api/v2` |
| `REPS_DATABASE_URL` | `db/*.py`, `pipeline/search_indexer.py` | `postgresql://reps:reps@localhost:5433/reps` (dev only) |

**Never commit a real production `REPS_DATABASE_URL`.** Set it in the
deployment environment's secret manager.

## First deploy

```bash
pip install -r requirements.txt

# 1. Build the dataset (or reuse an existing data/output/reps_exercises*.json)
python run.py

# 2. Apply the schema to the target Postgres instance
REPS_DATABASE_URL=postgresql://user:pass@prod-host:5432/reps python db/migrate.py apply

# 3. Load the data
REPS_DATABASE_URL=postgresql://user:pass@prod-host:5432/reps python db/seed.py

# 4. Build the search index
REPS_DATABASE_URL=postgresql://user:pass@prod-host:5432/reps python pipeline/search_indexer.py

# 5. Publish the SQLite offline cache for the Android app
REPS_DATABASE_URL=postgresql://user:pass@prod-host:5432/reps python db/sqlite_export.py
# -> upload data/output/reps_offline.sqlite to wherever the app downloads it from

# 6. (once a cloud bucket is chosen) prepare + run the asset upload -- see docs/assets.md -> "Cloud migration"
REPS_DATABASE_URL=postgresql://user:pass@prod-host:5432/reps python db/generate_upload_manifest.py
aws s3 sync assets/ s3://your-bucket/   # or gsutil/rclone -- whichever preserves the relative path layout
```

## Verified locally against a disposable Docker Postgres

This exact sequence was run end-to-end against `postgres:16` in Docker
during development (see `ENGINEERING_REPORT.md` for full output): schema
applied cleanly, seeded 828 exercises with zero errors, idempotent on a
second run, search verified in all three languages, SQLite export row
counts matched Postgres exactly. No cloud infrastructure was provided for
this project, so a production Postgres instance itself was never targeted —
only the tooling that would target one.

```bash
docker run -d --name reps-postgres \
  -e POSTGRES_USER=reps -e POSTGRES_PASSWORD=reps -e POSTGRES_DB=reps \
  -p 5433:5432 postgres:16
```

## Updating an existing deployment

Re-running `run.py` → `db/migrate.py apply` → `db/seed.py` →
`pipeline/search_indexer.py` → `db/sqlite_export.py` is always safe: every
stage is idempotent (skip-cached downloads, upsert-by-stable-id seeding,
no-op migrations if already applied). There is no destructive step in this
sequence. See `docs/maintenance.md` for the recommended cadence.

## CI recommendations (not yet implemented — see `docs/roadmap.md`)

- Run `pytest -q` on every PR.
- On a schedule (e.g. weekly): `run.py` against upstream WGER, diff the
  resulting `reps_exercises.json` against the previous run, open a PR if it
  changed, require human review before merging (translation quality can't be
  auto-verified).
- On merge to main: apply migrations + seed + reindex against staging,
  smoke-test, then repeat against production.
