# Asset pipeline (Epic 1)

## Layout

```
assets/
  exercises/{exercise_id}/
    main.{ext}          # the is_main image (or the first image, if none is flagged main)
    image_2.{ext}, image_3.{ext}, ...
    video_1.{ext}, video_2.{ext}, ...
  muscles/
    main/muscle-{id}.{hash}.svg
    secondary/muscle-{id}.{hash}.svg
    muscle_svg_map.json
  body/
    front.svg
    back.svg
```

This is distinct from the legacy `data/media/{images,videos}/` cache, which
is keyed by remote uuid and predates this system — `assets/` is what the
mobile app and the database's `local_path` columns point at.

## What `pipeline/media_downloader.py` guarantees per file

- Original quality and extension preserved: the extension comes from the
  URL when it's a known one, otherwise from a validated `Content-Type`
  response header (`config.ASSET.content_type_extensions`) — never guessed.
- Resumable: a non-empty file already at the expected path is reused as-is
  on the next run without a network call (`--force` bypasses this).
- Retried on transient failures (`config.ASSET.max_retries`), with HTTP 429
  handled separately from other failures: it honors `Retry-After`, doesn't
  count against the retry budget, and is bounded instead by a wall-clock
  budget (`rate_limit_retry_budget_seconds`) so a misbehaving server can't
  hang the run forever.
- `Content-Type` validated against the expected kind (`image/*`/`video/*`,
  with SVG's several legitimate content-types allow-listed); a mismatch is
  recorded as a failure with `invalidContentType: true`, never silently
  written to disk.
- Every file gets a streamed SHA-256 checksum, byte size, and (for images
  and SVGs) width/height, all computed while writing — see
  `MediaDownloader._fetch_and_write`.
- Duplicate detection: after all downloads for an exercise complete, assets
  sharing an identical checksum are deduplicated (first occurrence — main
  image, or lowest video index — is kept; the physical duplicate file is
  deleted). Verified run: 24 duplicate images removed across 828 exercises.

Report: `data/output/image_download_report.json` — totals, failures (with
reason), retry/duplicate/invalid-content-type counts, total bytes.

## Muscle SVGs and body diagrams

`pipeline/svg_assets.py` downloads every muscle's `image_url_main`/
`image_url_secondary` (from wger's `muscle` endpoint) and writes
`assets/muscles/muscle_svg_map.json` (`{muscle_id: {main, secondary}}`).

WGER's REST API does **not** expose the front/back body diagrams the muscle
overlays are drawn on. They were located by directly probing WGER's static
asset paths (`https://wger.de/static/images/muscles/muscular_system_{front,
back}.svg`, both confirmed reachable) — if WGER ever moves or removes them,
`svg_validation_report.json` records `"downloaded": false` honestly instead
of the pipeline pretending they exist.

"Overlay alignment" is validated as: every muscle SVG must share the body
diagrams' canvas width exactly, and height within a small tolerance (wger's
own muscle SVGs vary a few px in height from each other legitimately, e.g.
362px vs 369px) — this is an automatable proxy for visual alignment, not a
substitute for an actual visual QA pass, and the report says so.

Verified run: 30/30 muscle SVGs downloaded, both body diagrams downloaded,
**0 alignment issues**.

## Videos (disabled by policy)

Video support exists in every layer (dataclasses, DB schema, SQLite export)
but is switched off: `config.VIDEOS_ENABLED = False`. `pipeline/cleaner.py`
strips `videos` off every exercise immediately after download — the single
point of control, so nothing downstream (merger, media downloader,
validator, exporter, `db/seed.py`, `db/sqlite_export.py`) ever sees a video,
with no per-module changes required. All 73 previously-downloaded video
files (2.9 GB) were deleted from `assets/exercises/` and every
`exercise_videos` row was cleared from Postgres.

This was a deliberate reversibility choice, not a schema change: flipping
`VIDEOS_ENABLED` back to `True` restores the old behavior with zero other
code changes, because the `exercise_videos` table/`MediaAsset` fields were
never dropped (see `docs/database.md`).

## Cloud migration (images)

Every stored `local_path` (e.g. `assets/exercises/1000/main.png`) is
already a relative path designed to double as an object-storage key
unchanged — moving to the cloud is a config change, not a data migration:

1. `python db/generate_upload_manifest.py` — reads Postgres + the
   filesystem, writes `data/output/cloud_upload_manifest.json` (every
   file's object key / size / checksum). **Uploads nothing** — safe to run
   with no bucket, credentials, or provider chosen yet.
2. Pick a provider and sync `assets/` to a bucket with any tool that
   preserves the relative path layout (`aws s3 sync`, `gsutil rsync`,
   `rclone sync`, ...) — see the module docstring in
   `db/generate_upload_manifest.py` for exact commands.
3. Set `REPS_CLOUD_ASSETS_BASE_URL` (e.g. to the bucket's CDN URL).
   `pipeline/helpers.py::resolve_asset_url(local_path, base_url)` is the
   **only** place that needs to know about it — the API layer should call
   it instead of hand-joining strings, so this stays a one-line cutover.

Not yet done (per explicit scope — no bucket/provider was chosen): the
actual upload, and wiring `resolve_asset_url` into a real API response.
`muscle_svg_assets`/`body_diagrams` don't have a stored checksum column
(only `exercise_images` does, from Epic 1) — `generate_upload_manifest.py`
computes theirs on the fly since they're small files; adding the columns
properly is a candidate follow-up migration if that's ever needed beyond
this one-off manifest (see `docs/roadmap.md`).

## Adding a new provider's assets

Nothing here is wger-specific except `pipeline/svg_assets.py`'s muscle-SVG
source (which reads `data/raw/wger/muscle/*.json` directly, since that's
currently the only source of muscle SVG data). `pipeline/media_downloader.py`
is fully provider-agnostic: it operates on `MergedExercise.images/videos`
(`MediaAsset` objects), which every provider's `normalize()` already
produces (see `README.md` → "Adding a new provider").
