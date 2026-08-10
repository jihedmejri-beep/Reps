#!/usr/bin/env python3
"""Prepares (but does not perform) a cloud storage migration for `assets/`.

    python db/generate_upload_manifest.py

Writes `data/output/cloud_upload_manifest.json`: every asset file's local
path, intended object storage key (the same relative path -- see
`config.py` -> "Cloud asset storage"), size, and checksum. No credentials,
bucket, or provider are required to run this -- it only reads the local
filesystem and Postgres, so it can be run and reviewed well before a
bucket exists.

Once a bucket is chosen, upload with whatever tool fits (all preserve the
relative-path-as-key layout this manifest describes):

    aws s3 sync assets/ s3://your-bucket/           # AWS
    gsutil -m rsync -r assets/ gs://your-bucket/     # GCP
    rclone sync assets/ remote:your-bucket/           # R2/B2/anything rclone supports

Then set `REPS_CLOUD_ASSETS_BASE_URL` (e.g. to the bucket's public/CDN
URL) -- `pipeline/helpers.py::resolve_asset_url` is the only code that
needs to know about it.
"""

from __future__ import annotations

import argparse
import datetime as dt
import hashlib
import json
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

from config import ASSETS_DIR, CLOUD_UPLOAD_MANIFEST_PATH, DATABASE_URL, PROJECT_ROOT  # noqa: E402
from db.db_conn import connect  # noqa: E402
from pipeline.logger import get_logger  # noqa: E402

logger = get_logger("db.generate_upload_manifest")


def _checksum(path: Path) -> str:
    hasher = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(64 * 1024), b""):
            hasher.update(chunk)
    return hasher.hexdigest()


def _exercise_image_entries(cur) -> list[dict]:
    """Exercise images already have a checksum from Epic 1 -- reuse it
    instead of re-hashing 336 files (cheap, but pointless work)."""
    cur.execute("SELECT local_path, size_bytes, checksum_sha256 FROM exercise_images WHERE local_path IS NOT NULL")
    return [
        {"objectKey": local_path, "sizeBytes": size, "checksum": checksum, "assetType": "exercise_image"}
        for local_path, size, checksum in cur.fetchall()
    ]


def _filesystem_entries(cur, asset_type: str, query: str) -> list[dict]:
    """Muscle SVGs / body diagrams don't have a stored checksum column
    (see docs/roadmap.md) -- computed here instead, cheap for these small
    files, without adding a migration just for this one-off manifest."""
    cur.execute(query)
    entries = []
    for (local_path,) in cur.fetchall():
        if not local_path:
            continue
        full_path = PROJECT_ROOT / local_path
        if not full_path.exists():
            logger.warning("Referenced in DB but missing on disk, skipping: %s", local_path)
            continue
        entries.append(
            {
                "objectKey": local_path,
                "sizeBytes": full_path.stat().st_size,
                "checksum": _checksum(full_path),
                "assetType": asset_type,
            }
        )
    return entries


def generate(dsn: str = DATABASE_URL) -> dict:
    conn = connect(dsn)
    cur = conn.cursor()
    try:
        entries = _exercise_image_entries(cur)
        entries += _filesystem_entries(
            cur, "muscle_svg", "SELECT local_path FROM muscle_svg_assets WHERE local_path IS NOT NULL"
        )
        entries += _filesystem_entries(cur, "body_diagram", "SELECT local_path FROM body_diagrams WHERE local_path IS NOT NULL")
    finally:
        cur.close()
        conn.close()

    total_bytes = sum(e["sizeBytes"] for e in entries)
    manifest = {
        "generatedAt": dt.datetime.now(dt.timezone.utc).isoformat(),
        "assetsRoot": str(ASSETS_DIR.relative_to(PROJECT_ROOT)),
        "fileCount": len(entries),
        "totalBytes": total_bytes,
        "entries": sorted(entries, key=lambda e: e["objectKey"]),
    }
    CLOUD_UPLOAD_MANIFEST_PATH.write_text(json.dumps(manifest, ensure_ascii=False, indent=2), encoding="utf-8")
    logger.info(
        "Wrote %s: %s file(s), %.1f MB -- no upload performed, see the module docstring for next steps",
        CLOUD_UPLOAD_MANIFEST_PATH,
        len(entries),
        total_bytes / (1024 * 1024),
    )
    return manifest


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--dsn", default=DATABASE_URL)
    args = parser.parse_args(argv)
    generate(args.dsn)
    return 0


if __name__ == "__main__":
    sys.exit(main())
