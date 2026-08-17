"""Shared PostgreSQL connection helper for every `db/*.py` tool.

Centralizes DSN parsing so `db/migrate.py`, `db/seed.py`,
`db/sqlite_export.py` and `pipeline/search_indexer.py` don't each
reimplement it. Uses `pg8000` (pure Python, no libpq/C toolchain
required) rather than `psycopg2`, but exposes the same DB-API 2.0 surface
(`connect/cursor/execute/commit/rollback`) every caller here relies on.
"""

from __future__ import annotations

from urllib.parse import urlsplit

import pg8000.dbapi


def connect(dsn: str):
    """Parse a `postgresql://user:pass@host:port/dbname` DSN and open a
    DB-API 2.0 connection via pg8000."""
    parts = urlsplit(dsn)
    if parts.scheme not in ("postgres", "postgresql"):
        raise ValueError(f"Unsupported DSN scheme: {parts.scheme!r} (expected postgres/postgresql)")
    return pg8000.dbapi.connect(
        host=parts.hostname or "localhost",
        port=parts.port or 5432,
        user=parts.username or "postgres",
        password=parts.password or "",
        database=(parts.path or "/postgres").lstrip("/"),
    )
