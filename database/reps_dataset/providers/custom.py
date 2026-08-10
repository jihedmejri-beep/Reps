"""Custom / locally curated dataset provider.

Unlike `providers/exercisedb.py` and `providers/gymvisual.py`, this
provider is fully functional: it lets REPS ingest hand-curated or
vendor-exported exercises (e.g. a one-off LiftManual export, an internal
photoshoot, corrections to wger data) without waiting on a dedicated
integration for that source.

Usage: drop one or more `*.json` files under
`data/raw/custom/input/`, each containing a JSON array of exercise
objects in the schema documented below, then enable this provider via
`python run.py --providers wger custom`.

Custom exercise schema (one object per array entry)::

    {
      "id": "custom-001",                 // optional, auto-generated if omitted
      "uuid": "…",                        // optional, auto-generated if omitted
      "source": "liftmanual",             // optional, defaults to "custom"
      "variationGroup": "",               // optional
      "category": "Chest",
      "equipment": [{"id": 1, "name": "Barbell"}],
      "primaryMuscles": [{"id": 11, "name": "Pectoralis major", "nameEn": "Chest"}],
      "secondaryMuscles": [],
      "translations": [
        {"language": "en", "name": "…", "description": "…", "aliases": ["…"]},
        {"language": "fr", "name": "…", "description": "…"},
        {"language": "ar", "name": "…", "description": "…"}
      ],
      "images": [{"uuid": "…", "url": "https://…", "isMain": true}],
      "videos": [],
      "license": {"name": "All rights reserved", "url": ""}
    }

Only `category` and `translations` are semantically required; every other
field degrades gracefully to an empty default.
"""

from __future__ import annotations

import uuid as uuid_lib
from typing import Any, Iterator

from pipeline.helpers import read_json_if_exists
from pipeline.logger import get_logger
from pipeline.models import (
    EquipmentRef,
    LicenseInfo,
    MediaAsset,
    MuscleRef,
    RawExerciseRecord,
    TranslationRecord,
)
from providers.base import ExerciseProvider, register_provider

logger = get_logger("providers.custom")


@register_provider("custom")
class CustomDatasetProvider(ExerciseProvider):
    """Ingests hand-curated or vendor-exported exercise JSON files."""

    @property
    def input_dir(self):
        directory = self.raw_dir / "input"
        directory.mkdir(parents=True, exist_ok=True)
        return directory

    def fetch_raw(self) -> None:
        """No network I/O: verifies the input directory and reports what's there.

        Custom data is supplied locally by a human, not downloaded, so
        this step is a validation pass rather than an HTTP fetch.
        """
        files = sorted(self.input_dir.glob("*.json"))
        if not files:
            logger.info("No custom dataset files found under %s (nothing to ingest)", self.input_dir)
            return
        logger.info("Found %s custom dataset file(s) under %s", len(files), self.input_dir)

    def normalize(self) -> Iterator[RawExerciseRecord]:
        for file_path in sorted(self.input_dir.glob("*.json")):
            payload = read_json_if_exists(file_path)
            if not isinstance(payload, list):
                logger.error("Skipping %s: expected a top-level JSON array of exercises", file_path)
                continue
            for index, entry in enumerate(payload):
                try:
                    yield self._to_raw_exercise(entry)
                except (KeyError, TypeError, ValueError) as exc:
                    logger.error("Skipping malformed custom exercise #%s in %s: %s", index, file_path, exc)

    @staticmethod
    def _to_raw_exercise(entry: dict[str, Any]) -> RawExerciseRecord:
        source = entry.get("source") or "custom"
        record_id = str(entry.get("id") or uuid_lib.uuid4())
        record_uuid = str(entry.get("uuid") or uuid_lib.uuid4())

        equipment = [EquipmentRef(id=int(e["id"]), name=e["name"]) for e in entry.get("equipment", [])]
        primary_muscles = [
            MuscleRef(id=int(m["id"]), name=m["name"], name_en=m.get("nameEn", ""), is_front=m.get("isFront"))
            for m in entry.get("primaryMuscles", [])
        ]
        secondary_muscles = [
            MuscleRef(id=int(m["id"]), name=m["name"], name_en=m.get("nameEn", ""), is_front=m.get("isFront"))
            for m in entry.get("secondaryMuscles", [])
        ]

        images = [
            MediaAsset(uuid=str(img.get("uuid") or uuid_lib.uuid4()), remote_url=img["url"], is_main=bool(img.get("isMain")))
            for img in entry.get("images", [])
            if img.get("url")
        ]
        videos = [
            MediaAsset(uuid=str(vid.get("uuid") or uuid_lib.uuid4()), remote_url=vid["url"], is_main=bool(vid.get("isMain")))
            for vid in entry.get("videos", [])
            if vid.get("url")
        ]

        license_data = entry.get("license") or {}
        license_info = LicenseInfo(name=license_data.get("name", ""), url=license_data.get("url", ""))

        translations = [
            TranslationRecord(
                language_code=translation["language"],
                name=translation.get("name", "") or "",
                description=translation.get("description", "") or "",
                aliases=list(translation.get("aliases", [])),
            )
            for translation in entry.get("translations", [])
        ]

        return RawExerciseRecord(
            id=record_id,
            uuid=record_uuid,
            source=source,
            variation_group=entry.get("variationGroup") or "",
            category=entry.get("category", "") or "",
            equipment=equipment,
            primary_muscles=primary_muscles,
            secondary_muscles=secondary_muscles,
            translations=translations,
            images=images,
            videos=videos,
            license=license_info,
        )
