#!/usr/bin/env python3
"""Exports the PostgreSQL system of record into a self-contained SQLite
file for the Android app's offline cache.

    python db/sqlite_export.py

This is a **read-only mirror**: PostgreSQL is always the source of truth
(Epic 3), Android only ever reads this file. Every run fully rebuilds it
(`assets/exercises/`-style local_path strings are copied through
unchanged, since the app ships/downloads the same `assets/` tree), so
there is no incremental-sync state to get out of sync -- rerunning after
a Postgres change just produces a fresh, correct file. Includes an FTS5
virtual table so search keeps working fully offline.
"""

from __future__ import annotations

import argparse
import json
import sqlite3
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

from config import DATABASE_URL, SQLITE_EXPORT_PATH  # noqa: E402
from db.db_conn import connect  # noqa: E402
from pipeline.logger import get_logger  # noqa: E402

logger = get_logger("db.sqlite_export")

_SCHEMA = """
CREATE TABLE exercises (
    id              TEXT PRIMARY KEY,
    external_id     TEXT NOT NULL,
    category        TEXT,
    variation_group TEXT,
    license_name    TEXT,
    license_url     TEXT
);

CREATE TABLE exercise_translations (
    exercise_id       TEXT NOT NULL REFERENCES exercises(id),
    language_code     TEXT NOT NULL,
    name              TEXT NOT NULL,
    summary           TEXT NOT NULL DEFAULT '',
    starting_position TEXT NOT NULL DEFAULT '',
    steps_json        TEXT NOT NULL DEFAULT '[]',
    tips_json         TEXT NOT NULL DEFAULT '[]',
    notes_json        TEXT NOT NULL DEFAULT '[]',
    keywords_json     TEXT NOT NULL DEFAULT '[]',
    PRIMARY KEY (exercise_id, language_code)
);

CREATE TABLE exercise_aliases (
    exercise_id TEXT NOT NULL REFERENCES exercises(id),
    alias       TEXT NOT NULL
);
CREATE INDEX idx_sqlite_aliases_exercise ON exercise_aliases(exercise_id);

CREATE TABLE exercise_equipment (
    exercise_id    TEXT NOT NULL REFERENCES exercises(id),
    equipment_name TEXT NOT NULL
);
CREATE INDEX idx_sqlite_equipment_exercise ON exercise_equipment(exercise_id);

CREATE TABLE exercise_muscles (
    exercise_id TEXT NOT NULL REFERENCES exercises(id),
    muscle_name TEXT NOT NULL,
    name_en     TEXT,
    role        TEXT NOT NULL,
    is_front    INTEGER
);
CREATE INDEX idx_sqlite_muscles_exercise ON exercise_muscles(exercise_id);

CREATE TABLE exercise_images (
    id          TEXT PRIMARY KEY,
    exercise_id TEXT NOT NULL REFERENCES exercises(id),
    is_main     INTEGER NOT NULL,
    local_path  TEXT,
    width       INTEGER,
    height      INTEGER,
    size_bytes  INTEGER,
    checksum    TEXT
);
CREATE INDEX idx_sqlite_images_exercise ON exercise_images(exercise_id);

CREATE TABLE exercise_videos (
    id          TEXT PRIMARY KEY,
    exercise_id TEXT NOT NULL REFERENCES exercises(id),
    local_path  TEXT,
    width       INTEGER,
    height      INTEGER,
    duration_seconds REAL,
    size_bytes  INTEGER,
    checksum    TEXT
);
CREATE INDEX idx_sqlite_videos_exercise ON exercise_videos(exercise_id);

CREATE TABLE muscle_svg_assets (
    muscle_name TEXT NOT NULL,
    variant     TEXT NOT NULL,
    local_path  TEXT NOT NULL,
    width       INTEGER,
    height      INTEGER
);

CREATE TABLE body_diagrams (
    side       TEXT PRIMARY KEY,
    local_path TEXT NOT NULL,
    width      INTEGER,
    height     INTEGER
);

CREATE VIRTUAL TABLE exercise_fts USING fts5(
    exercise_id UNINDEXED,
    language_code UNINDEXED,
    name,
    keywords
);
"""


def _fetch_all(pg_cur, sql: str) -> list[tuple]:
    pg_cur.execute(sql)
    return pg_cur.fetchall()


def export(dsn: str = DATABASE_URL, dest_path: Path = SQLITE_EXPORT_PATH) -> Path:
    pg_conn = connect(dsn)
    pg_cur = pg_conn.cursor()

    dest_path.parent.mkdir(parents=True, exist_ok=True)
    dest_path.unlink(missing_ok=True)
    sqlite_conn = sqlite3.connect(dest_path)
    sqlite_conn.executescript(_SCHEMA)

    try:
        rows = _fetch_all(
            pg_cur,
            "SELECT ex.id, ex.external_id, c.name, ex.variation_group, l.name, l.url "
            "FROM exercises ex "
            "LEFT JOIN categories c ON c.id = ex.category_id "
            "LEFT JOIN licenses l ON l.id = ex.license_id",
        )
        sqlite_conn.executemany(
            "INSERT INTO exercises VALUES (?, ?, ?, ?, ?, ?)",
            [(str(r[0]), r[1], r[2], str(r[3]) if r[3] else None, r[4], r[5]) for r in rows],
        )
        logger.info("exercises: %s row(s)", len(rows))

        rows = _fetch_all(
            pg_cur,
            "SELECT exercise_id, language_code, name, summary, starting_position, steps, tips, notes, keywords "
            "FROM exercise_translations",
        )
        sqlite_conn.executemany(
            "INSERT INTO exercise_translations VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
            [
                (
                    str(r[0]), r[1], r[2], r[3], r[4],
                    json.dumps(r[5]), json.dumps(r[6]), json.dumps(r[7]), json.dumps(list(r[8]) if r[8] else []),
                )
                for r in rows
            ],
        )
        sqlite_conn.executemany(
            "INSERT INTO exercise_fts (exercise_id, language_code, name, keywords) VALUES (?, ?, ?, ?)",
            [(str(r[0]), r[1], r[2], " ".join(r[8] or [])) for r in rows],
        )
        logger.info("exercise_translations / exercise_fts: %s row(s)", len(rows))

        rows = _fetch_all(pg_cur, "SELECT exercise_id, alias FROM exercise_aliases")
        sqlite_conn.executemany("INSERT INTO exercise_aliases VALUES (?, ?)", [(str(r[0]), r[1]) for r in rows])

        rows = _fetch_all(
            pg_cur,
            "SELECT ee.exercise_id, e.name FROM exercise_equipment ee JOIN equipment e ON e.id = ee.equipment_id",
        )
        sqlite_conn.executemany("INSERT INTO exercise_equipment VALUES (?, ?)", [(str(r[0]), r[1]) for r in rows])

        rows = _fetch_all(
            pg_cur,
            "SELECT em.exercise_id, m.name, m.name_en, em.role, m.is_front "
            "FROM exercise_muscles em JOIN muscles m ON m.id = em.muscle_id",
        )
        sqlite_conn.executemany(
            "INSERT INTO exercise_muscles VALUES (?, ?, ?, ?, ?)",
            [(str(r[0]), r[1], r[2], r[3], (1 if r[4] else 0) if r[4] is not None else None) for r in rows],
        )

        rows = _fetch_all(
            pg_cur, "SELECT id, exercise_id, is_main, local_path, width, height, size_bytes, checksum_sha256 FROM exercise_images"
        )
        sqlite_conn.executemany(
            "INSERT INTO exercise_images VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
            [(str(r[0]), str(r[1]), 1 if r[2] else 0, r[3], r[4], r[5], r[6], r[7]) for r in rows],
        )

        rows = _fetch_all(
            pg_cur,
            "SELECT id, exercise_id, local_path, width, height, duration_seconds, size_bytes, checksum_sha256 FROM exercise_videos",
        )
        sqlite_conn.executemany(
            "INSERT INTO exercise_videos VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
            [(str(r[0]), str(r[1]), r[2], r[3], r[4], float(r[5]) if r[5] is not None else None, r[6], r[7]) for r in rows],
        )

        rows = _fetch_all(
            pg_cur,
            "SELECT m.name, msa.variant, msa.local_path, msa.width, msa.height "
            "FROM muscle_svg_assets msa JOIN muscles m ON m.id = msa.muscle_id",
        )
        sqlite_conn.executemany("INSERT INTO muscle_svg_assets VALUES (?, ?, ?, ?, ?)", rows)

        rows = _fetch_all(pg_cur, "SELECT side, local_path, width, height FROM body_diagrams")
        sqlite_conn.executemany("INSERT INTO body_diagrams VALUES (?, ?, ?, ?)", rows)

        sqlite_conn.commit()
    finally:
        pg_cur.close()
        pg_conn.close()
        sqlite_conn.close()

    logger.info("SQLite offline cache written to %s (%s)", dest_path, _human_size(dest_path.stat().st_size))
    return dest_path


def _human_size(size_bytes: int) -> str:
    for unit in ("B", "KB", "MB", "GB"):
        if size_bytes < 1024:
            return f"{size_bytes:.1f} {unit}"
        size_bytes /= 1024
    return f"{size_bytes:.1f} TB"


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--dsn", default=DATABASE_URL)
    parser.add_argument("--out", type=Path, default=SQLITE_EXPORT_PATH)
    args = parser.parse_args(argv)
    export(args.dsn, args.out)
    return 0


if __name__ == "__main__":
    sys.exit(main())
