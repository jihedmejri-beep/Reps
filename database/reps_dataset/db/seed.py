#!/usr/bin/env python3
"""Idempotent seed loader: `reps_exercises_clean.json` + Epic 1's asset
reports -> the normalized PostgreSQL schema (`db/migrations/*.sql`).

    python db/seed.py

Reads two JSON sources and merges them by exercise id, because they carry
complementary information:
  * `data/output/reps_exercises_clean.json` -- the Phase 2 cleaned +
    translated (en/fr/ar) text content, structured descriptions.
  * `data/output/reps_exercises.json` -- the freshest images/videos,
    with the checksum/width/height/localPath metadata Epic 1 computes
    (the clean/translation pass in Phase 2 pre-dates Epic 1 and never
    touched media, so its image objects are stale placeholders).

Every write is an upsert keyed by a stable id (the exercise's own uuid,
the asset's own uuid, or a natural `(source_id, external_id)`/name
unique key for lookup tables) -- re-running this script against an
unchanged export is a no-op change-wise. Writes `data/output/
seed_report.json` with per-table inserted/updated/skipped/error counts.
"""

from __future__ import annotations

import argparse
import datetime as dt
import json
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

from config import ASSETS_DIR, DATABASE_URL, OUTPUT_DIR, PROJECT_ROOT  # noqa: E402
from db.db_conn import connect  # noqa: E402
from pipeline.helpers import read_json_if_exists, svg_dimensions  # noqa: E402
from pipeline.logger import get_logger  # noqa: E402

logger = get_logger("db.seed")

CLEAN_EXPORT_PATH = OUTPUT_DIR / "reps_exercises_clean.json"
RAW_EXPORT_PATH = OUTPUT_DIR / "reps_exercises.json"
MUSCLE_SVG_MAP_PATH = ASSETS_DIR / "muscles" / "muscle_svg_map.json"
SVG_VALIDATION_REPORT_PATH = OUTPUT_DIR / "svg_validation_report.json"
SEED_REPORT_PATH = OUTPUT_DIR / "seed_report.json"

LANGUAGES = ("en", "fr", "ar")


class _Counters:
    def __init__(self) -> None:
        self.tables: dict[str, dict[str, int]] = {}
        self.errors: list[dict] = []

    def bump(self, table: str, key: str, n: int = 1) -> None:
        self.tables.setdefault(table, {"inserted": 0, "updated": 0, "skipped": 0})[key] += n

    def error(self, table: str, ref: str, message: str) -> None:
        self.errors.append({"table": table, "ref": ref, "error": message})
        logger.error("%s (%s): %s", table, ref, message)

    def to_dict(self) -> dict:
        return {
            "generatedAt": dt.datetime.now(dt.timezone.utc).isoformat(),
            "tables": self.tables,
            "errorCount": len(self.errors),
            "errors": self.errors,
        }


def load_inputs() -> tuple[list[dict], dict[str, dict], dict, dict]:
    clean = read_json_if_exists(CLEAN_EXPORT_PATH)
    if clean is None:
        raise SystemExit(f"Missing {CLEAN_EXPORT_PATH} -- run clean_dataset.py --merge first")
    raw = read_json_if_exists(RAW_EXPORT_PATH) or []
    raw_by_id = {e["id"]: e for e in raw}
    muscle_svg_map = read_json_if_exists(MUSCLE_SVG_MAP_PATH) or {}
    svg_report = read_json_if_exists(SVG_VALIDATION_REPORT_PATH) or {}
    return clean, raw_by_id, muscle_svg_map, svg_report


# --------------------------------------------------------------------------
# Lookup upserts (return id maps keyed by the source's natural key)
# --------------------------------------------------------------------------


def upsert_source(cur, slug: str) -> int:
    cur.execute("SELECT id FROM sources WHERE slug = %s", (slug,))
    row = cur.fetchone()
    if row is None:
        raise SystemExit(f"Unknown source slug {slug!r} -- not seeded by 0001_init.sql")
    return row[0]


def upsert_licenses(cur, exercises: list[dict], counters: _Counters) -> dict[str, int]:
    ids: dict[str, int] = {}
    seen: dict[str, str] = {}
    for exercise in exercises:
        license_ = exercise.get("license") or {}
        if license_.get("name"):
            seen[license_["name"]] = license_.get("url") or ""
    for name, url in seen.items():
        cur.execute(
            "INSERT INTO licenses (name, url) VALUES (%s, %s) "
            "ON CONFLICT (name) DO UPDATE SET url = EXCLUDED.url RETURNING id, (xmax = 0) AS inserted",
            (name, url),
        )
        license_id, inserted = cur.fetchone()
        ids[name] = license_id
        counters.bump("licenses", "inserted" if inserted else "updated")
    return ids


def upsert_categories(cur, exercises: list[dict], counters: _Counters) -> dict[str, int]:
    ids: dict[str, int] = {}
    names = sorted({e["category"] for e in exercises if e.get("category")})
    for name in names:
        cur.execute(
            "INSERT INTO categories (name) VALUES (%s) "
            "ON CONFLICT (name) DO UPDATE SET name = EXCLUDED.name RETURNING id, (xmax = 0) AS inserted",
            (name,),
        )
        cat_id, inserted = cur.fetchone()
        ids[name] = cat_id
        counters.bump("categories", "inserted" if inserted else "updated")
    return ids


def upsert_equipment(cur, exercises: list[dict], source_id: int, counters: _Counters) -> dict[str, int]:
    ids: dict[str, int] = {}
    seen: dict[str, str] = {}
    for exercise in exercises:
        for item in exercise.get("equipment", []):
            seen[str(item["id"])] = item["name"]
    for external_id, name in seen.items():
        cur.execute(
            "INSERT INTO equipment (source_id, external_id, name) VALUES (%s, %s, %s) "
            "ON CONFLICT (source_id, external_id) DO UPDATE SET name = EXCLUDED.name "
            "RETURNING id, (xmax = 0) AS inserted",
            (source_id, external_id, name),
        )
        eq_id, inserted = cur.fetchone()
        ids[external_id] = eq_id
        counters.bump("equipment", "inserted" if inserted else "updated")
    return ids


def upsert_muscles(cur, exercises: list[dict], source_id: int, muscle_svg_map: dict, counters: _Counters) -> dict[str, int]:
    ids: dict[str, int] = {}
    seen: dict[str, dict] = {}
    for exercise in exercises:
        for item in exercise.get("primaryMuscles", []) + exercise.get("secondaryMuscles", []):
            seen[str(item["id"])] = item
    for external_id, muscle in seen.items():
        svg_paths = muscle_svg_map.get(external_id, {})
        cur.execute(
            "INSERT INTO muscles (source_id, external_id, name, name_en, is_front, main_svg_local_path, secondary_svg_local_path) "
            "VALUES (%s, %s, %s, %s, %s, %s, %s) "
            "ON CONFLICT (source_id, external_id) DO UPDATE SET "
            "name = EXCLUDED.name, name_en = EXCLUDED.name_en, is_front = EXCLUDED.is_front, "
            "main_svg_local_path = EXCLUDED.main_svg_local_path, secondary_svg_local_path = EXCLUDED.secondary_svg_local_path "
            "RETURNING id, (xmax = 0) AS inserted",
            (
                source_id,
                external_id,
                muscle.get("name", ""),
                muscle.get("nameEn") or None,
                muscle.get("isFront"),
                svg_paths.get("main"),
                svg_paths.get("secondary"),
            ),
        )
        muscle_id, inserted = cur.fetchone()
        ids[external_id] = muscle_id
        counters.bump("muscles", "inserted" if inserted else "updated")
    return ids


def upsert_body_diagrams(cur, svg_report: dict, counters: _Counters) -> None:
    body_diagrams = svg_report.get("bodyDiagrams", {})
    dimensions = svg_report.get("bodyDiagramDimensions", {})
    for side, info in body_diagrams.items():
        if not info.get("downloaded"):
            continue
        dims = dimensions.get(side) or [None, None]
        cur.execute(
            "INSERT INTO body_diagrams (side, local_path, width, height) VALUES (%s, %s, %s, %s) "
            "ON CONFLICT (side) DO UPDATE SET local_path = EXCLUDED.local_path, width = EXCLUDED.width, height = EXCLUDED.height "
            "RETURNING (xmax = 0) AS inserted",
            (side, info["localPath"], dims[0], dims[1]),
        )
        (inserted,) = cur.fetchone()
        counters.bump("body_diagrams", "inserted" if inserted else "updated")


def upsert_muscle_svg_assets(cur, muscle_ids: dict[str, int], muscle_svg_map: dict, counters: _Counters) -> None:
    for external_id, paths in muscle_svg_map.items():
        muscle_id = muscle_ids.get(external_id)
        if muscle_id is None:
            continue
        for variant in ("main", "secondary"):
            local_path = paths.get(variant)
            if not local_path:
                continue
            dims = svg_dimensions(PROJECT_ROOT / local_path) or (None, None)
            cur.execute(
                "INSERT INTO muscle_svg_assets (muscle_id, variant, local_path, width, height) VALUES (%s, %s, %s, %s, %s) "
                "ON CONFLICT (muscle_id, variant) DO UPDATE SET local_path = EXCLUDED.local_path, width = EXCLUDED.width, height = EXCLUDED.height "
                "RETURNING (xmax = 0) AS inserted",
                (muscle_id, variant, local_path, dims[0], dims[1]),
            )
            (inserted,) = cur.fetchone()
            counters.bump("muscle_svg_assets", "inserted" if inserted else "updated")


# --------------------------------------------------------------------------
# Per-exercise upserts
# --------------------------------------------------------------------------


def upsert_exercise(cur, exercise: dict, source_id: int, category_ids: dict, license_ids: dict, counters: _Counters) -> None:
    license_id = license_ids.get((exercise.get("license") or {}).get("name"))
    category_id = category_ids.get(exercise.get("category"))
    variation_group = exercise.get("variationGroup") or None
    cur.execute(
        "INSERT INTO exercises (id, source_id, external_id, variation_group, category_id, license_id) "
        "VALUES (%s, %s, %s, %s, %s, %s) "
        "ON CONFLICT (id) DO UPDATE SET "
        "variation_group = EXCLUDED.variation_group, category_id = EXCLUDED.category_id, license_id = EXCLUDED.license_id "
        "RETURNING (xmax = 0) AS inserted",
        (exercise["uuid"], source_id, exercise["id"], variation_group, category_id, license_id),
    )
    (inserted,) = cur.fetchone()
    counters.bump("exercises", "inserted" if inserted else "updated")


def upsert_translations(cur, exercise: dict, counters: _Counters) -> None:
    exercise_uuid = exercise["uuid"]
    for lang in LANGUAGES:
        name = exercise["name"].get(lang, "")
        desc = exercise["description"].get(lang) or {}
        cur.execute(
            "INSERT INTO exercise_translations "
            "(exercise_id, language_code, name, summary, starting_position, steps, tips, notes) "
            "VALUES (%s, %s, %s, %s, %s, %s, %s, %s) "
            "ON CONFLICT (exercise_id, language_code) DO UPDATE SET "
            "name = EXCLUDED.name, summary = EXCLUDED.summary, starting_position = EXCLUDED.starting_position, "
            "steps = EXCLUDED.steps, tips = EXCLUDED.tips, notes = EXCLUDED.notes "
            "RETURNING (xmax = 0) AS inserted",
            (
                exercise_uuid,
                lang,
                name,
                desc.get("summary", ""),
                desc.get("startingPosition", ""),
                json.dumps(desc.get("steps", [])),
                json.dumps(desc.get("tips", [])),
                json.dumps(desc.get("notes", [])),
            ),
        )
        (inserted,) = cur.fetchone()
        counters.bump("exercise_translations", "inserted" if inserted else "updated")


def replace_aliases(cur, exercise: dict, counters: _Counters) -> None:
    exercise_uuid = exercise["uuid"]
    aliases = sorted(set(exercise.get("aliases", [])))
    cur.execute("DELETE FROM exercise_aliases WHERE exercise_id = %s", (exercise_uuid,))
    for alias in aliases:
        cur.execute("INSERT INTO exercise_aliases (exercise_id, alias) VALUES (%s, %s)", (exercise_uuid, alias))
        counters.bump("exercise_aliases", "inserted")


def replace_equipment(cur, exercise: dict, equipment_ids: dict[str, int], counters: _Counters) -> None:
    exercise_uuid = exercise["uuid"]
    cur.execute("DELETE FROM exercise_equipment WHERE exercise_id = %s", (exercise_uuid,))
    for item in exercise.get("equipment", []):
        eq_id = equipment_ids.get(str(item["id"]))
        if eq_id is None:
            continue
        cur.execute("INSERT INTO exercise_equipment (exercise_id, equipment_id) VALUES (%s, %s)", (exercise_uuid, eq_id))
        counters.bump("exercise_equipment", "inserted")


def replace_muscles(cur, exercise: dict, muscle_ids: dict[str, int], counters: _Counters) -> None:
    exercise_uuid = exercise["uuid"]
    cur.execute("DELETE FROM exercise_muscles WHERE exercise_id = %s", (exercise_uuid,))
    for role, key in (("primary", "primaryMuscles"), ("secondary", "secondaryMuscles")):
        for item in exercise.get(key, []):
            muscle_id = muscle_ids.get(str(item["id"]))
            if muscle_id is None:
                continue
            cur.execute(
                "INSERT INTO exercise_muscles (exercise_id, muscle_id, role) VALUES (%s, %s, %s)",
                (exercise_uuid, muscle_id, role),
            )
            counters.bump("exercise_muscles", "inserted")


def replace_media(cur, exercise_uuid: str, raw_exercise: dict | None, counters: _Counters) -> None:
    for table, key in (("exercise_images", "images"), ("exercise_videos", "videos")):
        cur.execute(f"DELETE FROM {table} WHERE exercise_id = %s", (exercise_uuid,))
        if raw_exercise is None:
            continue
        for asset in raw_exercise.get(key, []):
            if table == "exercise_images":
                cur.execute(
                    "INSERT INTO exercise_images "
                    "(id, exercise_id, is_main, url, local_path, width, height, size_bytes, checksum_sha256) "
                    "VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s)",
                    (
                        asset["uuid"],
                        exercise_uuid,
                        asset.get("isMain", False),
                        asset["url"],
                        asset.get("localPath"),
                        asset.get("width"),
                        asset.get("height"),
                        asset.get("size"),
                        asset.get("checksum"),
                    ),
                )
            else:
                cur.execute(
                    "INSERT INTO exercise_videos "
                    "(id, exercise_id, url, local_path, width, height, duration_seconds, codec, size_bytes, checksum_sha256) "
                    "VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s)",
                    (
                        asset["uuid"],
                        exercise_uuid,
                        asset["url"],
                        asset.get("localPath"),
                        asset.get("width"),
                        asset.get("height"),
                        asset.get("durationSeconds"),
                        asset.get("codec"),
                        asset.get("size"),
                        asset.get("checksum"),
                    ),
                )
            counters.bump(table, "inserted")


# --------------------------------------------------------------------------
# Orchestration
# --------------------------------------------------------------------------


def seed(dsn: str = DATABASE_URL) -> _Counters:
    exercises, raw_by_id, muscle_svg_map, svg_report = load_inputs()
    counters = _Counters()

    conn = connect(dsn)
    conn.autocommit = False
    cur = conn.cursor()
    try:
        source_id = upsert_source(cur, "wger")
        license_ids = upsert_licenses(cur, exercises, counters)
        category_ids = upsert_categories(cur, exercises, counters)
        equipment_ids = upsert_equipment(cur, exercises, source_id, counters)
        muscle_ids = upsert_muscles(cur, exercises, source_id, muscle_svg_map, counters)
        upsert_body_diagrams(cur, svg_report, counters)
        upsert_muscle_svg_assets(cur, muscle_ids, muscle_svg_map, counters)
        conn.commit()

        for exercise in exercises:
            try:
                upsert_exercise(cur, exercise, source_id, category_ids, license_ids, counters)
                upsert_translations(cur, exercise, counters)
                replace_aliases(cur, exercise, counters)
                replace_equipment(cur, exercise, equipment_ids, counters)
                replace_muscles(cur, exercise, muscle_ids, counters)
                replace_media(cur, exercise["uuid"], raw_by_id.get(exercise["id"]), counters)
                conn.commit()
            except Exception as exc:  # noqa: BLE001 - one bad row must never abort the whole seed
                conn.rollback()
                counters.error("exercises", exercise.get("id", "?"), str(exc))
    finally:
        cur.close()
        conn.close()

    return counters


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--dsn", default=DATABASE_URL)
    args = parser.parse_args(argv)

    counters = seed(args.dsn)
    report = counters.to_dict()
    SEED_REPORT_PATH.write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")
    logger.info("Seed report written to %s", SEED_REPORT_PATH)
    for table, stats in report["tables"].items():
        logger.info("%-24s inserted=%-5s updated=%-5s", table, stats["inserted"], stats["updated"])
    if report["errorCount"]:
        logger.error("%s error(s) during seed -- see %s", report["errorCount"], SEED_REPORT_PATH)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
