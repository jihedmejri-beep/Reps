"""Check that UserDataDatabase migrations produce exactly the schema Room expects.

Room validates the migrated database against the entity schema on open and
crashes on any mismatch, so this replays each shipped migration over the
previous exported schema and compares the result with a fresh database built
from the next one: columns (type, NOT NULL, default, PK), indices and foreign
keys, table by table. Sample rows are inserted first to confirm data survives.

The migration SQL is read from UserDataMigrations.kt, so the check runs against
what actually ships rather than a copy.

Usage: python tools/validate_user_db_migration.py
"""
import json
import re
import sqlite3
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SCHEMAS = ROOT / "app/schemas/com.reps.app.data.user.db.UserDataDatabase"
MIGRATIONS_KT = ROOT / "app/src/main/java/com/reps/app/data/user/db/UserDataMigrations.kt"


def create_from_schema(conn: sqlite3.Connection, version: int) -> None:
    db = json.loads((SCHEMAS / f"{version}.json").read_text(encoding="utf-8"))["database"]
    for entity in db["entities"]:
        name = entity["tableName"]
        conn.execute(entity["createSql"].replace("${TABLE_NAME}", name))
        for index in entity.get("indices", []):
            conn.execute(index["createSql"].replace("${TABLE_NAME}", name))


def migration_sql(source: str, start: int, end: int) -> list[str]:
    block = re.search(rf"MIGRATION_{start}_{end}_SQL[^=]*=\s*listOf\((.*?)\n    \)", source, re.S)
    if not block:
        sys.exit(f"MIGRATION_{start}_{end}_SQL not found in {MIGRATIONS_KT.name}")
    body = re.sub(r"//[^\n]*", "", block.group(1))
    statements = []
    for item in re.split(r'(?<="),\s*\n', body):
        pieces = re.findall(r'"((?:[^"\\]|\\.)*)"', item)
        if pieces:
            statements.append("".join(pieces))
    return statements


def describe(conn: sqlite3.Connection) -> dict:
    tables = [r[0] for r in conn.execute(
        "SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%' "
        "AND name NOT IN ('room_master_table', 'android_metadata') ORDER BY name")]
    out = {}
    for t in tables:
        columns = sorted((r[1], r[2].upper(), r[3], r[4], r[5]) for r in conn.execute(f"PRAGMA table_info(`{t}`)"))
        indices = []
        for _, iname, unique, origin, _partial in conn.execute(f"PRAGMA index_list(`{t}`)"):
            if origin != "c":
                continue
            cols = [r[2] for r in conn.execute(f"PRAGMA index_info(`{iname}`)")]
            indices.append((iname, unique, tuple(cols)))
        fks = sorted(tuple(r[2:8]) for r in conn.execute(f"PRAGMA foreign_key_list(`{t}`)"))
        out[t] = {"columns": columns, "indices": sorted(indices), "foreign_keys": fks}
    return out


def seed_v1(conn: sqlite3.Connection) -> None:
    conn.execute("INSERT INTO user_profiles VALUES ('u1','Sam','s@x.io','MALE',180.0,30,'MAINTAIN','METRIC','fr',3,'2026-08-20')")
    conn.execute("INSERT INTO workout_templates VALUES ('t1','u1','Push','MONDAY','INTERMEDIATE',35,1)")
    conn.execute("INSERT INTO workout_template_exercises (id, templateId, exerciseId, position) VALUES (1,'t1','ex1',0)")
    conn.execute("INSERT INTO workout_sessions VALUES ('s1','u1','t1','Push','2026-08-20',40,'ex1')")


def main() -> None:
    source = MIGRATIONS_KT.read_text(encoding="utf-8")
    versions = sorted(int(p.stem) for p in SCHEMAS.glob("*.json"))
    failures = 0
    for start, end in zip(versions, versions[1:]):
        migrated = sqlite3.connect(":memory:")
        create_from_schema(migrated, start)
        if start == 1:
            seed_v1(migrated)
        for statement in migration_sql(source, start, end):
            migrated.execute(statement)

        fresh = sqlite3.connect(":memory:")
        create_from_schema(fresh, end)

        got, want = describe(migrated), describe(fresh)
        for table in sorted(set(got) | set(want)):
            if got.get(table) != want.get(table):
                failures += 1
                print(f"[{start}->{end}] {table} differs:\n  migrated: {got.get(table)}\n  expected: {want.get(table)}")

        if start == 1:
            row = migrated.execute("SELECT name, durationMin, startedAtMs, endedAtMs FROM workout_sessions").fetchone()
            rest = migrated.execute("SELECT restSeconds FROM workout_template_exercises").fetchone()[0]
            profile = migrated.execute("SELECT age, birthDateIso FROM user_profiles").fetchone()
            if row != ("Push", 40, 0, 0) or rest != 90 or profile != (30, None):
                failures += 1
                print(f"[1->2] data check failed: session={row} rest={rest} profile={profile}")
        print(f"[{start}->{end}] {'OK' if failures == 0 else 'FAILED'}")

    sys.exit(1 if failures else 0)


if __name__ == "__main__":
    main()
