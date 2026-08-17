#!/usr/bin/env python3
"""Validates the generated app catalogue. Exits non-zero if anything is wrong.

    python tools/validate_exercise_db.py

Checks structure (Room can open it), completeness (nothing was dropped relative
to the source dataset), referential integrity, that every asset path the
database points at actually exists inside the APK's assets, and that search
works in all three shipped languages.

Data-quality facts that are simply true of the upstream dataset - exercises with
no image, no equipment or no muscles - are reported, not failed. They are the
dataset's shape, not a bug in the conversion.
"""

from __future__ import annotations

import json
import sqlite3
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from build_exercise_db import fold_text  # noqa: E402

REPO = Path(__file__).resolve().parent.parent
APP_DB = REPO / "app" / "src" / "main" / "assets" / "reps_exercises.db"
APP_ASSETS = REPO / "app" / "src" / "main" / "assets"
SOURCE_DB = (
    REPO / "database" / "reps_dataset" / "data" / "output" / "reps_offline.sqlite"
)
SCHEMA_JSON = (
    REPO / "app" / "schemas"
    / "com.reps.app.data.exercise.db.ExerciseCatalogDatabase" / "1.json"
)

failures: list[str] = []
notes: list[str] = []


def check(condition: bool, message: str) -> None:
    if condition:
        print(f"  PASS  {message}")
    else:
        print(f"  FAIL  {message}")
        failures.append(message)


def note(message: str) -> None:
    print(f"  note  {message}")
    notes.append(message)


def section(title: str) -> None:
    print(f"\n== {title} ==")


def main() -> int:
    if not APP_DB.exists():
        print(f"FATAL: {APP_DB} does not exist. Run tools/build_exercise_db.py.")
        return 1

    app = sqlite3.connect(f"file:{APP_DB}?mode=ro", uri=True)
    app.row_factory = sqlite3.Row
    src = sqlite3.connect(f"file:{SOURCE_DB}?mode=ro", uri=True)
    src.row_factory = sqlite3.Row

    def a(sql, *args):
        return app.execute(sql, args).fetchone()[0]

    def s(sql, *args):
        return src.execute(sql, args).fetchone()[0]

    section("structure")
    check(a("PRAGMA integrity_check") == "ok", "sqlite integrity_check is ok")

    identity = json.loads(SCHEMA_JSON.read_text(encoding="utf-8"))["database"]["identityHash"]
    stored = a("SELECT identity_hash FROM room_master_table WHERE id = 42")
    check(
        stored == identity,
        f"room_master_table identity hash matches Room's schema ({identity})",
    )

    tables = {r["name"] for r in app.execute(
        "SELECT name FROM sqlite_master WHERE type='table'"
    )}
    for table in (
        "exercises", "exercise_translations", "exercise_muscles",
        "exercise_equipment", "exercise_aliases", "exercise_images",
        "muscle_svg_assets", "body_diagrams", "room_master_table",
    ):
        check(table in tables, f"table {table} present")

    section("completeness against the source dataset")
    pairs = [
        ("exercises", "exercises"),
        ("exercise_translations", "exercise_translations"),
        ("exercise_aliases", "exercise_aliases"),
        ("exercise_images", "exercise_images"),
        ("muscle_svg_assets", "muscle_svg_assets"),
        ("body_diagrams", "body_diagrams"),
    ]
    for table, source_table in pairs:
        got, want = a(f"SELECT COUNT(*) FROM {table}"), s(f"SELECT COUNT(*) FROM {source_table}")
        check(got == want, f"{table}: {got} rows == source {want}")

    # Muscles and equipment are de-duplicated by the composite primary key, so
    # they are compared on distinct tuples rather than raw row count.
    got = a("SELECT COUNT(*) FROM exercise_muscles")
    want = s("SELECT COUNT(*) FROM (SELECT DISTINCT exercise_id, muscle_name, role FROM exercise_muscles)")
    check(got == want, f"exercise_muscles: {got} == source distinct {want}")

    got = a("SELECT COUNT(*) FROM exercise_equipment")
    want = s("SELECT COUNT(*) FROM (SELECT DISTINCT exercise_id, equipment_name FROM exercise_equipment)")
    check(got == want, f"exercise_equipment: {got} == source distinct {want}")

    section("ids and relationships preserved")
    app_ids = {r[0] for r in app.execute("SELECT id FROM exercises")}
    src_ids = {r[0] for r in src.execute("SELECT id FROM exercises")}
    check(app_ids == src_ids, "every source exercise id is present and unchanged")

    app_ext = {r[0] for r in app.execute("SELECT external_id FROM exercises")}
    src_ext = {r[0] for r in src.execute("SELECT external_id FROM exercises")}
    check(app_ext == src_ext, "every source external_id is preserved")

    for table in ("exercise_translations", "exercise_muscles", "exercise_equipment",
                  "exercise_aliases", "exercise_images"):
        orphans = a(
            f"SELECT COUNT(*) FROM {table} t "
            "WHERE NOT EXISTS (SELECT 1 FROM exercises e WHERE e.id = t.exercise_id)"
        )
        check(orphans == 0, f"{table} has no orphan rows")

    section("languages")
    for lang in ("en", "fr", "ar"):
        missing = a(
            "SELECT COUNT(*) FROM exercises e WHERE NOT EXISTS (SELECT 1 FROM "
            "exercise_translations t WHERE t.exercise_id = e.id AND t.language_code = ?)",
            lang,
        )
        check(missing == 0, f"every exercise has a {lang} translation")
    blank = a("SELECT COUNT(*) FROM exercise_translations WHERE TRIM(name) = ''")
    check(blank == 0, "no blank exercise names")

    section("media references (remote, never bundled)")
    total = a("SELECT COUNT(*) FROM exercise_images")
    with_remote = a("SELECT COUNT(*) FROM exercise_images WHERE remote_url IS NOT NULL AND remote_url != ''")
    check(with_remote == total, f"all {total} image rows carry a remote url")
    with_asset = a("SELECT COUNT(*) FROM exercise_images WHERE asset_path IS NOT NULL")
    check(with_asset == total, f"all {total} image rows carry a CDN asset key")

    bundled = list((APP_ASSETS / "exercises").rglob("*")) if (APP_ASSETS / "exercises").exists() else []
    check(len(bundled) == 0, "no demonstration media was bundled into the APK")

    no_thumb = a("SELECT COUNT(*) FROM exercise_images WHERE thumb_medium_url IS NULL")
    if no_thumb:
        note(f"{no_thumb} image(s) have no thumbnail rendition upstream; they fall back to the full image")

    dupe_main = a(
        "SELECT COUNT(*) FROM (SELECT exercise_id FROM exercise_images "
        "WHERE is_main = 1 GROUP BY exercise_id HAVING COUNT(*) > 1)"
    )
    if dupe_main:
        note(f"{dupe_main} exercise(s) mark more than one image as main; sort_order breaks the tie")
    unordered = a(
        "SELECT COUNT(*) FROM (SELECT exercise_id FROM exercise_images "
        "GROUP BY exercise_id HAVING COUNT(DISTINCT sort_order) != COUNT(*))"
    )
    check(unordered == 0, "sort_order is unique within every exercise")

    section("SVG assets are bundled and reachable")
    missing_assets = []
    for row in app.execute("SELECT asset_path FROM muscle_svg_assets UNION SELECT asset_path FROM body_diagrams"):
        if not (APP_ASSETS / row[0]).exists():
            missing_assets.append(row[0])
    check(not missing_assets, f"all {a('SELECT COUNT(*) FROM muscle_svg_assets') + a('SELECT COUNT(*) FROM body_diagrams')} SVG assets exist on disk")
    for path in missing_assets:
        print(f"        missing: {path}")

    referenced_muscles = {r[0] for r in app.execute("SELECT DISTINCT muscle_name FROM exercise_muscles")}
    with_art = {r[0] for r in app.execute("SELECT DISTINCT muscle_name FROM muscle_svg_assets")}
    check(
        referenced_muscles <= with_art,
        f"every targeted muscle has overlay artwork ({len(referenced_muscles)} muscles)",
    )
    for variant in ("main", "secondary"):
        count = a("SELECT COUNT(*) FROM muscle_svg_assets WHERE variant = ?", variant)
        check(count == len(with_art), f"every muscle has a '{variant}' overlay ({count})")
    sides = {r[0] for r in app.execute("SELECT side FROM body_diagrams")}
    check(sides == {"front", "back"}, "both body diagrams present")

    section("search")
    def search(lang: str, term: str) -> int:
        folded = fold_text(term)
        return app.execute(
            "SELECT COUNT(*) FROM exercises e JOIN exercise_translations t "
            "ON t.exercise_id = e.id AND t.language_code = ? "
            "WHERE t.name_folded LIKE '%'||?||'%' OR t.keywords_folded LIKE '%'||?||'%'",
            (lang, folded, folded),
        ).fetchone()[0]

    check(search("en", "bench") > 0, "en: 'bench' returns results")
    check(search("en", "BENCH") > 0, "en: search is case-insensitive")
    check(search("fr", "developpe") > 0, "fr: unaccented 'developpe' matches 'Développé'")
    check(
        search("fr", "developpe") == search("fr", "développé"),
        "fr: accented and unaccented queries agree",
    )
    check(search("ar", "ضغط") > 0, "ar: arabic query returns results")
    check(search("en", "zzzznotathing") == 0, "a nonsense query returns nothing")

    section("folding parity fixture (must match TextFoldingTest.kt)")
    fixtures = {
        "Bench Press": "bench press",
        "Développé couché": "developpe couche",
        "  Bench   press  ": "bench press",
        "4-Count Burpees": "4-count burpees",
        "": "",
    }
    for raw, expected in fixtures.items():
        got = fold_text(raw)
        check(got == expected, f"fold({raw!r}) == {expected!r}")
    check(fold_text("ضغط") == fold_text("ضَغْط"), "fold strips arabic diacritics")
    check(fold_text("امام") == fold_text("أمام"), "fold normalises alef forms")
    check(fold_text("حركه") == fold_text("حركة"), "fold normalises teh marbuta")
    check(fold_text("ضغط") == fold_text("ضـغـط"), "fold removes tatweel")

    section("dataset shape (reported, not failed)")
    for label, sql in [
        ("exercises with no demonstration image", "SELECT COUNT(*) FROM exercises e WHERE NOT EXISTS (SELECT 1 FROM exercise_images i WHERE i.exercise_id = e.id)"),
        ("exercises with no muscle targeting", "SELECT COUNT(*) FROM exercises e WHERE NOT EXISTS (SELECT 1 FROM exercise_muscles m WHERE m.exercise_id = e.id)"),
        ("exercises with no primary muscle", "SELECT COUNT(*) FROM exercises e WHERE NOT EXISTS (SELECT 1 FROM exercise_muscles m WHERE m.exercise_id = e.id AND m.role = 'primary')"),
        ("exercises with no equipment", "SELECT COUNT(*) FROM exercises e WHERE NOT EXISTS (SELECT 1 FROM exercise_equipment q WHERE q.exercise_id = e.id)"),
        ("exercises with no how-to steps (en)", "SELECT COUNT(*) FROM exercise_translations WHERE language_code='en' AND steps_json = '[]'"),
    ]:
        note(f"{label}: {a(sql)} of {a('SELECT COUNT(*) FROM exercises')}")
    note("difficulty: not present in the dataset for any exercise")

    print(f"\nDB size: {APP_DB.stat().st_size / 1024 / 1024:.1f} MB")
    svg_bytes = sum(p.stat().st_size for p in (APP_ASSETS / "svg").rglob("*.svg"))
    print(f"bundled SVG: {svg_bytes / 1024:.0f} KB")

    app.close()
    src.close()

    print("\n" + "=" * 60)
    if failures:
        print(f"FAILED: {len(failures)} check(s)")
        for failure in failures:
            print(f"  - {failure}")
        return 1
    print(f"All checks passed ({len(notes)} informational note(s)).")
    return 0


if __name__ == "__main__":
    sys.exit(main())
