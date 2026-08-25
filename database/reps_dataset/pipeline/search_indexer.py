#!/usr/bin/env python3
"""Epic 4: builds per-exercise search keywords, writes them into Postgres
(`exercise_translations.keywords`/`search_normalized`, which drives the
`search_vector` trigger from `db/migrations/0002_search.sql`), and
exports `data/output/exercise_search_index.json` for lightweight
client-side/offline search (also what `db/sqlite_export.py` mirrors into
SQLite FTS5).

    python pipeline/search_indexer.py

Normalization (accent/diacritic stripping) is deliberately done in SQL,
not Python, via `normalize_arabic()`/`normalize_latin()` (see
`0002_search.sql`) -- this script only computes *which* keywords apply to
each exercise; there is exactly one place text normalization logic lives.
"""

from __future__ import annotations

import argparse
import datetime as dt
import json
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

from config import DATABASE_URL, OUTPUT_DIR  # noqa: E402
from db.db_conn import connect  # noqa: E402
from pipeline.logger import get_logger  # noqa: E402

logger = get_logger("pipeline.search_indexer")

SEARCH_INDEX_PATH = OUTPUT_DIR / "exercise_search_index.json"

_NORMALIZE_SQL = "CASE %s WHEN 'ar' THEN normalize_arabic(%s) ELSE normalize_latin(%s) END"


def _fetch_exercise_context(cur) -> dict[str, dict]:
    """One row per exercise: aliases, equipment/muscle/category names --
    the language-agnostic keyword material available in the current
    schema (none of those entities carry per-language translations yet)."""
    context: dict[str, dict] = {}

    cur.execute("SELECT id, external_id FROM exercises")
    for exercise_id, external_id in cur.fetchall():
        context[exercise_id] = {"externalId": external_id, "aliases": [], "equipment": [], "muscles": [], "category": None}

    cur.execute("SELECT exercise_id, alias FROM exercise_aliases")
    for exercise_id, alias in cur.fetchall():
        context[exercise_id]["aliases"].append(alias)

    cur.execute(
        "SELECT ee.exercise_id, e.name FROM exercise_equipment ee JOIN equipment e ON e.id = ee.equipment_id"
    )
    for exercise_id, name in cur.fetchall():
        context[exercise_id]["equipment"].append(name)

    cur.execute(
        "SELECT em.exercise_id, m.name, m.name_en FROM exercise_muscles em JOIN muscles m ON m.id = em.muscle_id"
    )
    for exercise_id, name, name_en in cur.fetchall():
        context[exercise_id]["muscles"].append(name)
        if name_en:
            context[exercise_id]["muscles"].append(name_en)

    cur.execute(
        "SELECT ex.id, c.name FROM exercises ex JOIN categories c ON c.id = ex.category_id"
    )
    for exercise_id, name in cur.fetchall():
        context[exercise_id]["category"] = name

    return context


def build_keywords(entry: dict) -> list[str]:
    keywords = list(dict.fromkeys(entry["aliases"] + entry["equipment"] + entry["muscles"]))
    if entry["category"]:
        keywords.append(entry["category"])
    return keywords


def run(dsn: str = DATABASE_URL) -> dict:
    conn = connect(dsn)
    conn.autocommit = False
    cur = conn.cursor()
    export_index: dict[str, dict] = {}

    try:
        context = _fetch_exercise_context(cur)

        cur.execute("SELECT exercise_id, language_code, name FROM exercise_translations")
        translations = cur.fetchall()

        updated = 0
        for exercise_id, language_code, name in translations:
            entry = context.get(exercise_id)
            if entry is None:
                continue
            keywords = build_keywords(entry)
            cur.execute(
                f"UPDATE exercise_translations SET keywords = %s, "
                f"search_normalized = {_NORMALIZE_SQL} "
                f"WHERE exercise_id = %s AND language_code = %s",
                (keywords, language_code, name, name, exercise_id, language_code),
            )
            updated += 1

            exercise_uuid = str(exercise_id)
            export_index.setdefault(entry["externalId"], {"id": entry["externalId"], "uuid": exercise_uuid, "languages": {}})
            export_index[entry["externalId"]]["languages"][language_code] = {
                "name": name,
                "keywords": keywords,
            }

        conn.commit()
        logger.info("Updated search keywords for %s exercise/language row(s)", updated)
    finally:
        cur.close()
        conn.close()

    payload = {
        "generatedAt": dt.datetime.now(dt.timezone.utc).isoformat(),
        "exerciseCount": len(export_index),
        "exercises": list(export_index.values()),
    }
    SEARCH_INDEX_PATH.write_text(json.dumps(payload, ensure_ascii=False, indent=2), encoding="utf-8")
    logger.info("Search index written to %s (%s exercises)", SEARCH_INDEX_PATH, len(export_index))
    return payload


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--dsn", default=DATABASE_URL)
    args = parser.parse_args(argv)
    run(args.dsn)
    return 0


if __name__ == "__main__":
    sys.exit(main())
