#!/usr/bin/env python3
"""Idempotent migration runner for the REPS PostgreSQL schema.

    python db/migrate.py apply          # apply every unapplied migration
    python db/migrate.py dump-schema    # regenerate db/schema.sql from migrations/*.sql
    python db/migrate.py apply --dump   # both, in order

`db/schema.sql` is never hand-written: it is the concatenation of every
file in `db/migrations/` in order, produced by `dump-schema` below. This
is what keeps the single "frozen schema" file (Epic 2's deliverable) from
ever drifting out of sync with what's actually been applied to a real
database (Epic 3's deliverable) -- there is exactly one place DDL is
authored.

Connects via `config.DATABASE_URL` (override with the `REPS_DATABASE_URL`
env var), matching the same override pattern already used for
`REPS_WGER_API_TOKEN` in `config.py`.
"""

from __future__ import annotations

import argparse
import sys
from pathlib import Path

import pg8000.dbapi

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

from config import DATABASE_URL, MIGRATIONS_DIR, SCHEMA_SQL_PATH  # noqa: E402
from db.db_conn import connect  # noqa: E402
from pipeline.logger import get_logger  # noqa: E402

logger = get_logger("db.migrate")

_SCHEMA_MIGRATIONS_EXISTS_SQL = """
    SELECT EXISTS (
        SELECT 1 FROM information_schema.tables
        WHERE table_schema = 'public' AND table_name = 'schema_migrations'
    )
"""


def _migration_files() -> list[Path]:
    return sorted(MIGRATIONS_DIR.glob("*.sql"))


def _applied_versions(conn) -> set[str]:
    cur = conn.cursor()
    try:
        cur.execute(_SCHEMA_MIGRATIONS_EXISTS_SQL)
        (exists,) = cur.fetchone()
        if not exists:
            return set()
        cur.execute("SELECT version FROM schema_migrations")
        return {row[0] for row in cur.fetchall()}
    finally:
        cur.close()


def apply_migrations(dsn: str = DATABASE_URL) -> list[str]:
    """Apply every migration file whose version isn't already recorded in
    `schema_migrations`, in filename order. Returns the list of versions
    actually applied (empty if the database was already up to date)."""
    conn = connect(dsn)
    conn.autocommit = True  # each migration file manages its own BEGIN/COMMIT
    applied: list[str] = []
    try:
        already_applied = _applied_versions(conn)
        for migration_path in _migration_files():
            version = migration_path.stem
            if version in already_applied:
                logger.info("Skipping %s (already applied)", version)
                continue
            logger.info("Applying %s", version)
            sql = migration_path.read_text(encoding="utf-8")
            cur = conn.cursor()
            try:
                cur.execute(sql)
            finally:
                cur.close()
            applied.append(version)
        logger.info("Migrations applied: %s", applied or "(none, already up to date)")
        return applied
    finally:
        conn.close()


def dump_schema() -> Path:
    """Regenerate `db/schema.sql` as the ordered concatenation of every
    migration file -- the single frozen-schema artifact Epic 2 requires."""
    parts = [
        "-- schema.sql -- GENERATED FILE. Do not hand-edit.",
        "-- Regenerate with: python db/migrate.py dump-schema",
        "-- Source of truth: db/migrations/*.sql, applied in filename order.",
        "",
    ]
    for migration_path in _migration_files():
        parts.append(f"-- ==== {migration_path.name} ====")
        parts.append(migration_path.read_text(encoding="utf-8").strip())
        parts.append("")
    SCHEMA_SQL_PATH.write_text("\n".join(parts), encoding="utf-8")
    logger.info("Wrote %s", SCHEMA_SQL_PATH)
    return SCHEMA_SQL_PATH


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("action", choices=["apply", "dump-schema"], nargs="?", default="apply")
    parser.add_argument("--dump", action="store_true", help="Also dump-schema after applying.")
    parser.add_argument("--dsn", default=DATABASE_URL, help="Postgres connection string (default: config.DATABASE_URL).")
    args = parser.parse_args(argv)

    try:
        if args.action == "apply":
            apply_migrations(args.dsn)
            if args.dump:
                dump_schema()
        else:
            dump_schema()
    except (pg8000.dbapi.InterfaceError, pg8000.dbapi.DatabaseError, OSError) as exc:
        logger.error("Could not connect to %s: %s", args.dsn, exc)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
