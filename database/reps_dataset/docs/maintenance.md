# Maintenance guide

## Routine refresh (new/updated exercises from WGER)

```bash
python run.py                                    # incremental: only new/changed data re-downloaded
python db/migrate.py apply                        # no-op unless a new migration file was added
python db/seed.py                                  # upserts; safe to re-run
python pipeline/search_indexer.py
python db/sqlite_export.py
```

Every step is idempotent (see `docs/deployment.md` → "Updating an existing
deployment"). There is no separate "full rebuild" vs "incremental update"
mode to choose between — it's the same command either way, and cost scales
with what actually changed (cached pages/files are skipped).

## Translations

New/changed English content that has no French/Arabic counterpart shows up
in `data/output/translation_worklist.json` after running
`clean_dataset.py --extract`. Translations are added as flat JSON files
under `data/output/translations/` (see the existing `desc_ar_*.json` /
`name_fr_*.json` files for the exact shape), then merged with
`clean_dataset.py --merge`, which also re-validates zero data loss and
regenerates `cleaning_report.json`. This step is manual by design —
translation quality is not something to automate without review.

## Assets

Re-running `run.py` (without `--skip-media`) only downloads assets that
don't already exist on disk or that changed source URL. To force a full
re-download (e.g. after a manual `assets/` cleanup), pass `--force`.
`data/output/image_download_report.json` and `data/output/
svg_validation_report.json` are the first place to check after any asset
refresh — `failures`/`issues` should be empty; if not, they include enough
detail (exercise id, url, reason) to investigate directly.

## Database

- **Schema changes**: add a new `db/migrations/NNNN_description.sql` file
  (never edit an existing one), run `python db/migrate.py apply --dump` to
  apply it and regenerate `db/schema.sql` in one step.
- **Monitoring**: `data/output/seed_report.json`'s `errorCount` should be 0
  after every seed run; a non-zero count means specific exercises failed to
  load (each error entry names the exercise id and the exact exception) —
  investigate before assuming the rest of the seed succeeded (it does: one
  bad row rolls back only that row's transaction, see `db/seed.py::seed`).
- **Backups**: standard PostgreSQL backup practice (e.g. `pg_dump` /
  managed-provider automated backups) applies; nothing in this pipeline
  replaces that. The pipeline can always fully reconstruct the database from
  `data/output/reps_exercises_clean.json` + `assets/`, which is itself a
  disaster-recovery path independent of Postgres backups.

## Known operational limitations (see `ENGINEERING_REPORT.md` for the full list)

- `db/seed.py`'s alias/equipment/muscle join tables are rebuilt via
  delete-then-insert per exercise on every run, not diffed — correct and
  idempotent, but not the cheapest possible update for very large datasets.
- The legacy `data/media/{images,videos}/` cache (pre-Epic-1) is not
  automatically cleaned up; it's safe to delete manually once `assets/` is
  confirmed populated.
