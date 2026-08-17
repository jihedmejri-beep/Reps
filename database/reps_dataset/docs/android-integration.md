# How Android consumes the data

## Two paths, one source of truth

```
                     ┌─────────────┐
                     │ PostgreSQL  │   <- system of record, always
                     └──────┬──────┘
                            │
              ┌─────────────┴─────────────┐
              │                           │
      backend API (live)          db/sqlite_export.py
              │                           │
              ▼                           ▼
     Android (online reads,        reps_offline.sqlite
      writes, fresh search)      (Android bundles/downloads,
                                   read-only offline cache)
```

- **Online**: the app talks to a backend API that queries PostgreSQL
  directly — full Epic 4 search (fuzzy, accent-insensitive, ranked),
  always-current data.
- **Offline**: the app reads `reps_offline.sqlite` (see `docs/database.md`
  → "SQLite offline export" for its exact table list). Search offline uses
  the bundled `exercise_fts` FTS5 virtual table — real search with zero
  connectivity, at the cost of no fuzzy/accent-insensitive matching (SQLite
  FTS5's trigram-equivalent, `spellfix1`, isn't wired in yet — see
  `docs/roadmap.md`).

Android **never writes** to `reps_offline.sqlite` and never talks to
Postgres directly — it is strictly a read-only local cache, refreshed by
downloading a new copy of the file (see "Refresh strategy" below).

## Loading the asset tree

`local_path` values in every table (`exercise_images.local_path`,
`muscle_svg_assets.local_path`, `body_diagrams.local_path`, etc.) are
relative paths rooted at the `assets/` directory (e.g.
`assets/exercises/1000/main.png`). In production these map 1:1 onto CDN/
object-storage keys (see `docs/deployment.md`) — the Android app resolves
`local_path` against whatever base URL the backend configures, exactly the
same way whether the file came from the live API or the offline SQLite
cache. This is why `local_path` is stored as a path, not a full URL: the
base can change (CDN migration, region) without touching every row.

## Refresh strategy

`reps_offline.sqlite` is a full-rebuild artifact (see `db/sqlite_export.py`'s
module docstring) — there is no incremental sync protocol today. The
practical pattern: the backend exposes the file's current checksum/version
(e.g. `data/output/seed_report.json`'s `generatedAt`, or a dedicated
version endpoint — not yet built, see `docs/roadmap.md`); the app downloads
a fresh copy when it detects a newer version, replaces the local file
atomically, and otherwise keeps using the cached one. This is the same
"replace, don't patch" model as the pipeline itself uses for every JSON
artifact.

## Rendering muscle overlays

`muscle_svg_assets` (main + secondary variant per muscle) and
`body_diagrams` (front/back) share a validated common canvas size (see
`docs/assets.md` → "Muscle SVGs and body diagrams", `svg_validation_report.json`)
— the app can stack a muscle's `main` SVG directly on top of the matching
`body_diagrams` side without independently scaling either, as long as it
respects the stored `width`/`height`.
