#!/usr/bin/env python3
"""Builds the app's bundled exercise catalogue from the REPS dataset.

    python tools/build_exercise_db.py

Reads, without ever writing to them:

  * ``database/reps_dataset/data/output/reps_offline.sqlite`` - the dataset's
    Android export. Source of truth for structure: ids, relationships,
    categories, muscle targeting, equipment, instructions, asset paths.
  * ``database/reps_dataset/data/output/reps_exercises_clean.json`` - source of
    truth for the *remote media URLs*, which ``reps_offline.sqlite`` drops
    (its ``exercise_images`` table keeps only the CDN-relative ``local_path``).
  * ``app/schemas/...ExerciseCatalogDatabase/1.json`` - Room's exported schema.

Writes:

  * ``app/src/main/assets/reps_exercises.db`` - a Room-openable SQLite file.
  * ``app/src/main/assets/svg/`` - the muscle + body SVGs (~800 KB).
  * ``tools/exercise_db_report.json`` - the data-integrity report.

The DDL is not written by hand: every ``CREATE TABLE``/``CREATE INDEX`` and the
``room_master_table`` identity hash are taken verbatim from Room's exported
schema, so the generated file cannot drift out of sync with the entities. Change
an entity, rebuild (``gradlew :app:kspDebugKotlin``), re-run this script.

Demonstration images and videos are deliberately NOT copied: they are ~208 MB
and stay remote. Only their references are stored.

Nothing in the source dataset is modified, renamed, or deleted. Records with
missing optional data are carried through as-is and counted in the report.
"""

from __future__ import annotations

import argparse
import json
import shutil
import sqlite3
import sys
import unicodedata
from collections import Counter, defaultdict
from pathlib import Path

REPO = Path(__file__).resolve().parent.parent
DATASET = REPO / "database" / "reps_dataset"
SOURCE_DB = DATASET / "data" / "output" / "reps_offline.sqlite"
SOURCE_JSON = DATASET / "data" / "output" / "reps_exercises_clean.json"
SCHEMA_JSON = (
    REPO / "app" / "schemas"
    / "com.reps.app.data.exercise.db.ExerciseCatalogDatabase" / "1.json"
)
APP_ASSETS = REPO / "app" / "src" / "main" / "assets"
OUT_DB = APP_ASSETS / "reps_exercises.db"
OUT_SVG_DIR = APP_ASSETS / "svg"
REPORT = REPO / "tools" / "exercise_db_report.json"

LANGUAGES = ("en", "fr", "ar")

# Arabic orthographic variants users type interchangeably. Must stay identical
# to TextFolding.ARABIC_EQUIVALENTS in the app.
ARABIC_EQUIVALENTS = {
    "أ": "ا",  # alef hamza above -> alef
    "إ": "ا",  # alef hamza below -> alef
    "آ": "ا",  # alef madda      -> alef
    "ٱ": "ا",  # alef wasla      -> alef
    "ى": "ي",  # alef maksura    -> yeh
    "ئ": "ي",  # yeh hamza       -> yeh
    "ة": "ه",  # teh marbuta     -> heh
    "ؤ": "و",  # waw hamza       -> waw
}
TATWEEL = "ـ"


def fold_text(value: str) -> str:
    """Mirror of ``TextFolding.fold`` in the app. Keep the two in lockstep."""
    if not value:
        return ""
    decomposed = unicodedata.normalize("NFD", value.lower())
    out: list[str] = []
    last_was_space = False
    for char in decomposed:
        if unicodedata.category(char) == "Mn" or char == TATWEEL:
            continue
        if char.isspace():
            if not last_was_space and out:
                out.append(" ")
            last_was_space = True
            continue
        out.append(ARABIC_EQUIVALENTS.get(char, char))
        last_was_space = False
    return "".join(out).rstrip()


# --------------------------------------------------------------------------
# schema
# --------------------------------------------------------------------------

def load_room_schema() -> tuple[list[str], str]:
    """Room's own DDL plus the identity hash it will validate against."""
    if not SCHEMA_JSON.exists():
        sys.exit(
            f"Room schema not found at {SCHEMA_JSON}.\n"
            "Run `gradlew :app:kspDebugKotlin` first - this script copies Room's\n"
            "generated DDL rather than duplicating it."
        )
    schema = json.loads(SCHEMA_JSON.read_text(encoding="utf-8"))["database"]
    statements: list[str] = []
    for entity in schema["entities"]:
        table = entity["tableName"]
        statements.append(entity["createSql"].replace("${TABLE_NAME}", table))
        for index in entity.get("indices", []):
            statements.append(index["createSql"].replace("${TABLE_NAME}", table))
    statements.extend(schema["setupQueries"])
    return statements, schema["identityHash"]


# --------------------------------------------------------------------------
# sources
# --------------------------------------------------------------------------

def open_source_db() -> sqlite3.Connection:
    if not SOURCE_DB.exists():
        sys.exit(f"Source database not found: {SOURCE_DB}")
    con = sqlite3.connect(f"file:{SOURCE_DB}?mode=ro", uri=True)
    con.row_factory = sqlite3.Row
    return con


def load_media_urls() -> dict[str, dict]:
    """image uuid -> {url, thumb_small, thumb_medium}, from the exported JSON.

    ``reps_offline.sqlite`` stores only ``local_path`` for each image, but the
    app has no CDN to resolve that against yet, and the real source URLs are
    right there in the pipeline's JSON export. Carrying them through is the
    difference between the demonstration media working and not.
    """
    if not SOURCE_JSON.exists():
        sys.exit(f"Source JSON not found: {SOURCE_JSON}")
    records = json.loads(SOURCE_JSON.read_text(encoding="utf-8"))
    urls: dict[str, dict] = {}
    for record in records:
        for image in record.get("images") or []:
            uuid = image.get("uuid")
            if not uuid:
                continue
            thumbs = image.get("thumbnails") or {}
            urls[uuid] = {
                "url": image.get("url"),
                "thumb_small": thumbs.get("small"),
                "thumb_medium": thumbs.get("medium"),
            }
    return urls


# --------------------------------------------------------------------------
# SVG assets
# --------------------------------------------------------------------------

def copy_svg_assets(rows: list[sqlite3.Row], report: dict) -> dict[str, str]:
    """Copy each referenced SVG into the APK and map its path.

    Filenames are preserved exactly (they carry the pipeline's content hash);
    only the directory root changes from ``assets/`` to ``svg/``.
    """
    mapping: dict[str, str] = {}
    missing: list[str] = []
    copied = 0
    for row in rows:
        source_rel = row["local_path"]
        if not source_rel:
            continue
        source = DATASET / source_rel
        # assets/muscles/main/x.svg -> svg/muscles/main/x.svg
        target_rel = "svg/" + source_rel.split("assets/", 1)[-1]
        target = APP_ASSETS / target_rel
        if not source.exists():
            missing.append(source_rel)
            continue
        target.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(source, target)
        mapping[source_rel] = target_rel
        copied += 1
    report["svgCopied"] = copied
    report["svgMissing"] = missing
    return mapping


# --------------------------------------------------------------------------
# build
# --------------------------------------------------------------------------

def build(verbose: bool = False) -> dict:
    statements, identity_hash = load_room_schema()
    src = open_source_db()
    media_urls = load_media_urls()

    report: dict = {
        "identityHash": identity_hash,
        "source": {
            "sqlite": str(SOURCE_DB.relative_to(REPO)).replace("\\", "/"),
            "json": str(SOURCE_JSON.relative_to(REPO)).replace("\\", "/"),
        },
        "problems": [],
    }

    APP_ASSETS.mkdir(parents=True, exist_ok=True)
    if OUT_SVG_DIR.exists():
        shutil.rmtree(OUT_SVG_DIR)
    OUT_DB.unlink(missing_ok=True)

    out = sqlite3.connect(OUT_DB)
    for statement in statements:
        out.execute(statement)

    # ---- exercises -------------------------------------------------------
    exercises = src.execute("SELECT * FROM exercises").fetchall()
    out.executemany(
        "INSERT INTO exercises (id, external_id, category, variation_group,"
        " license_name, license_url) VALUES (?, ?, ?, ?, ?, ?)",
        [
            (r["id"], r["external_id"], r["category"], r["variation_group"],
             r["license_name"], r["license_url"])
            for r in exercises
        ],
    )
    report["exercises"] = len(exercises)
    valid_ids = {r["id"] for r in exercises}

    # ---- translations ----------------------------------------------------
    translations = src.execute("SELECT * FROM exercise_translations").fetchall()
    translation_rows = []
    for r in translations:
        keywords_json = r["keywords_json"] or "[]"
        try:
            keywords = json.loads(keywords_json)
        except json.JSONDecodeError:
            keywords = []
            report["problems"].append(
                f"malformed keywords_json for {r['exercise_id']}/{r['language_code']}"
            )
        translation_rows.append((
            r["exercise_id"], r["language_code"], r["name"], fold_text(r["name"]),
            r["summary"] or "", r["starting_position"] or "",
            r["steps_json"] or "[]", r["tips_json"] or "[]", r["notes_json"] or "[]",
            keywords_json, fold_text(" ".join(str(k) for k in keywords)),
        ))
    out.executemany(
        "INSERT INTO exercise_translations (exercise_id, language_code, name,"
        " name_folded, summary, starting_position, steps_json, tips_json,"
        " notes_json, keywords_json, keywords_folded)"
        " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
        translation_rows,
    )
    report["translations"] = len(translation_rows)

    # ---- muscles ---------------------------------------------------------
    # The source table has no primary key, so identical (exercise, muscle, role)
    # triples are possible. Room's composite key rejects them, so collapse here
    # and report rather than letting the insert blow up.
    muscles = src.execute("SELECT * FROM exercise_muscles").fetchall()
    seen: set[tuple[str, str, str]] = set()
    muscle_rows = []
    duplicate_muscles = 0
    for r in muscles:
        key = (r["exercise_id"], r["muscle_name"], r["role"])
        if key in seen:
            duplicate_muscles += 1
            continue
        seen.add(key)
        muscle_rows.append((
            r["exercise_id"], r["muscle_name"], r["name_en"], r["role"], r["is_front"],
        ))
    out.executemany(
        "INSERT INTO exercise_muscles (exercise_id, muscle_name, name_en, role,"
        " is_front) VALUES (?, ?, ?, ?, ?)",
        muscle_rows,
    )
    report["muscles"] = len(muscle_rows)
    report["duplicateMuscleRowsCollapsed"] = duplicate_muscles

    # ---- equipment -------------------------------------------------------
    equipment = src.execute("SELECT * FROM exercise_equipment").fetchall()
    equipment_rows = sorted({(r["exercise_id"], r["equipment_name"]) for r in equipment})
    out.executemany(
        "INSERT INTO exercise_equipment (exercise_id, equipment_name) VALUES (?, ?)",
        equipment_rows,
    )
    report["equipment"] = len(equipment_rows)
    report["duplicateEquipmentRowsCollapsed"] = len(equipment) - len(equipment_rows)

    # ---- aliases ---------------------------------------------------------
    aliases = src.execute("SELECT * FROM exercise_aliases").fetchall()
    alias_rows = sorted({(r["exercise_id"], r["alias"]) for r in aliases})
    out.executemany(
        "INSERT INTO exercise_aliases (exercise_id, alias) VALUES (?, ?)",
        alias_rows,
    )
    report["aliases"] = len(alias_rows)

    # ---- images ----------------------------------------------------------
    images = src.execute(
        "SELECT * FROM exercise_images ORDER BY exercise_id, is_main DESC, id"
    ).fetchall()
    per_exercise: dict[str, int] = defaultdict(int)
    image_rows = []
    missing_url = 0
    for r in images:
        urls = media_urls.get(r["id"])
        if urls is None:
            missing_url += 1
            urls = {"url": None, "thumb_small": None, "thumb_medium": None}
        order = per_exercise[r["exercise_id"]]
        per_exercise[r["exercise_id"]] += 1
        image_rows.append((
            r["id"], r["exercise_id"], 1 if r["is_main"] else 0,
            r["local_path"], urls["url"], urls["thumb_small"], urls["thumb_medium"],
            r["width"], r["height"], order,
        ))
    out.executemany(
        "INSERT INTO exercise_images (id, exercise_id, is_main, asset_path,"
        " remote_url, thumb_small_url, thumb_medium_url, width, height, sort_order)"
        " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
        image_rows,
    )
    report["images"] = len(image_rows)
    report["imagesWithoutRemoteUrl"] = missing_url
    if missing_url:
        report["problems"].append(
            f"{missing_url} image row(s) had no matching URL in {SOURCE_JSON.name};"
            " they will fall back to the asset-path resolver"
        )

    # ---- SVG assets ------------------------------------------------------
    svg_rows = src.execute("SELECT * FROM muscle_svg_assets").fetchall()
    body_rows = src.execute("SELECT * FROM body_diagrams").fetchall()
    svg_map = copy_svg_assets(list(svg_rows) + list(body_rows), report)

    muscle_svg_inserts = []
    for r in svg_rows:
        target = svg_map.get(r["local_path"])
        if target is None:
            report["problems"].append(f"muscle SVG missing on disk: {r['local_path']}")
            continue
        muscle_svg_inserts.append(
            (r["muscle_name"], r["variant"], target, r["width"], r["height"])
        )
    out.executemany(
        "INSERT INTO muscle_svg_assets (muscle_name, variant, asset_path, width,"
        " height) VALUES (?, ?, ?, ?, ?)",
        muscle_svg_inserts,
    )
    report["muscleSvgAssets"] = len(muscle_svg_inserts)

    body_inserts = []
    for r in body_rows:
        target = svg_map.get(r["local_path"])
        if target is None:
            report["problems"].append(f"body diagram missing on disk: {r['local_path']}")
            continue
        body_inserts.append((r["side"], target, r["width"], r["height"]))
    out.executemany(
        "INSERT INTO body_diagrams (side, asset_path, width, height) VALUES (?, ?, ?, ?)",
        body_inserts,
    )
    report["bodyDiagrams"] = len(body_inserts)

    out.commit()
    out.execute("VACUUM")
    out.close()

    report.update(integrity_pass(src, valid_ids))
    src.close()

    report["outputBytes"] = OUT_DB.stat().st_size
    report["outputPath"] = str(OUT_DB.relative_to(REPO)).replace("\\", "/")
    return report


def integrity_pass(src: sqlite3.Connection, valid_ids: set[str]) -> dict:
    """Data-quality facts about the source, reported and never silently fixed."""
    def scalar(sql: str) -> int:
        return src.execute(sql).fetchone()[0]

    categories = Counter(
        r["category"] for r in src.execute("SELECT category FROM exercises")
    )
    return {
        "integrity": {
            "sourceIntegrityCheck": src.execute("PRAGMA integrity_check").fetchone()[0],
            "duplicateExerciseIds": scalar(
                "SELECT COUNT(*) FROM (SELECT id FROM exercises GROUP BY id HAVING COUNT(*) > 1)"
            ),
            "duplicateExternalIds": scalar(
                "SELECT COUNT(*) FROM (SELECT external_id FROM exercises"
                " GROUP BY external_id HAVING COUNT(*) > 1)"
            ),
            "exercisesMissingAnyTranslation": scalar(
                "SELECT COUNT(*) FROM exercises e WHERE (SELECT COUNT(*) FROM"
                " exercise_translations t WHERE t.exercise_id = e.id) < 3"
            ),
            "exercisesWithoutMuscles": scalar(
                "SELECT COUNT(*) FROM exercises e WHERE NOT EXISTS (SELECT 1 FROM"
                " exercise_muscles m WHERE m.exercise_id = e.id)"
            ),
            "exercisesWithoutPrimaryMuscle": scalar(
                "SELECT COUNT(*) FROM exercises e WHERE NOT EXISTS (SELECT 1 FROM"
                " exercise_muscles m WHERE m.exercise_id = e.id AND m.role = 'primary')"
            ),
            "exercisesWithoutEquipment": scalar(
                "SELECT COUNT(*) FROM exercises e WHERE NOT EXISTS (SELECT 1 FROM"
                " exercise_equipment q WHERE q.exercise_id = e.id)"
            ),
            "exercisesWithoutImage": scalar(
                "SELECT COUNT(*) FROM exercises e WHERE NOT EXISTS (SELECT 1 FROM"
                " exercise_images i WHERE i.exercise_id = e.id)"
            ),
            "exercisesWithMultipleMainImages": scalar(
                "SELECT COUNT(*) FROM (SELECT exercise_id FROM exercise_images"
                " WHERE is_main = 1 GROUP BY exercise_id HAVING COUNT(*) > 1)"
            ),
            "orphanRows": {
                "translations": scalar(
                    "SELECT COUNT(*) FROM exercise_translations t WHERE NOT EXISTS"
                    " (SELECT 1 FROM exercises e WHERE e.id = t.exercise_id)"
                ),
                "muscles": scalar(
                    "SELECT COUNT(*) FROM exercise_muscles m WHERE NOT EXISTS"
                    " (SELECT 1 FROM exercises e WHERE e.id = m.exercise_id)"
                ),
                "equipment": scalar(
                    "SELECT COUNT(*) FROM exercise_equipment q WHERE NOT EXISTS"
                    " (SELECT 1 FROM exercises e WHERE e.id = q.exercise_id)"
                ),
                "images": scalar(
                    "SELECT COUNT(*) FROM exercise_images i WHERE NOT EXISTS"
                    " (SELECT 1 FROM exercises e WHERE e.id = i.exercise_id)"
                ),
            },
            "muscleNamesWithoutSvg": [
                r["muscle_name"] for r in src.execute(
                    "SELECT DISTINCT muscle_name FROM exercise_muscles em WHERE NOT"
                    " EXISTS (SELECT 1 FROM muscle_svg_assets s"
                    " WHERE s.muscle_name = em.muscle_name)"
                )
            ],
            "categories": dict(categories),
            # The dataset has no difficulty field at all - see the report in the PR.
            "hasDifficultyData": "difficulty" in {
                d[1] for d in src.execute("PRAGMA table_info(exercises)")
            },
        }
    }


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("-v", "--verbose", action="store_true")
    args = parser.parse_args(argv)

    report = build(args.verbose)
    REPORT.write_text(json.dumps(report, indent=2, ensure_ascii=False), encoding="utf-8")

    print(f"exercises          {report['exercises']}")
    print(f"translations       {report['translations']}")
    print(f"muscles            {report['muscles']}")
    print(f"equipment          {report['equipment']}")
    print(f"aliases            {report['aliases']}")
    print(f"images (refs only) {report['images']}")
    print(f"muscle SVGs        {report['muscleSvgAssets']}")
    print(f"body diagrams      {report['bodyDiagrams']}")
    print(f"SVG files copied   {report['svgCopied']}")
    print(f"output             {report['outputPath']} "
          f"({report['outputBytes'] / 1024 / 1024:.1f} MB)")
    if report["problems"]:
        print("\nPROBLEMS:")
        for problem in report["problems"]:
            print(f"  - {problem}")
    print(f"\nfull report -> {REPORT.relative_to(REPO)}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
