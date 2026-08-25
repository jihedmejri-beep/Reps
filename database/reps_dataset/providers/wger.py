"""wger REST API v2 provider.

wger (https://wger.de) is a free/open exercise, workout and nutrition
manager. Its API restructured exercises into a base `exercise` resource
(muscles, equipment, category, variation group) plus one `exercise
-translation` per language, and exposes `exerciseinfo` as a fully
denormalized read view that nests everything -- including translations,
aliases, notes, images and videos -- into a single record per exercise
UUID.

This provider treats `exerciseinfo` as the canonical source (see
README.md -> "Architecture / Canonical data source" for the full
rationale) and additionally downloads the atomic endpoints
(`exercise`, `exercise-translation`, `exercisealias`, `exercisecomment`,
`exercisecategory`, `muscle`, `equipment`, `language`, `license`,
`exerciseimage`, `video`) as supplementary raw datasets for lookups,
cross-validation and future flexibility, per the project's Step 2
requirement to fetch every useful dataset.

Note: wger has no standalone "variation" endpoint. Exercises that are
variations of one another instead share a `variation_group` UUID on the
base `exercise` resource, which this provider carries through verbatim.
"""

from __future__ import annotations

from typing import Iterator

from config import CANONICAL_ENDPOINT, CONCURRENCY, WGER_API_TOKEN, WGER_BASE_URL, WGER_ENDPOINTS
from pipeline.downloader import PaginatedApiDownloader, iter_cached_records
from pipeline.helpers import build_http_session, safe_get
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

logger = get_logger("providers.wger")


@register_provider("wger")
class WgerProvider(ExerciseProvider):
    """Downloads and normalizes exercise data from the wger REST API v2."""

    def __init__(self, *, force_refresh: bool = False, download_workers: int = CONCURRENCY.download_workers) -> None:
        super().__init__(force_refresh=force_refresh, download_workers=download_workers)
        self._session = build_http_session(token=WGER_API_TOKEN)

    def fetch_raw(self) -> None:
        for endpoint_key, endpoint_path in WGER_ENDPOINTS.items():
            downloader = PaginatedApiDownloader(
                self._session,
                WGER_BASE_URL,
                endpoint_path,
                self.raw_dir / endpoint_key,
                workers=self.download_workers,
                force_refresh=self.force_refresh,
            )
            downloader.download_all()

    def normalize(self) -> Iterator[RawExerciseRecord]:
        """Yield every exercise from the cached `exerciseinfo` dump.

        Every translation present on wger is carried through unfiltered
        here; language filtering is the cleaner's job (pipeline Step 3),
        keeping this provider a faithful, lossless mirror of the source
        API.
        """
        language_codes = self._language_code_map()
        directory = self.raw_dir / CANONICAL_ENDPOINT
        for record in iter_cached_records(directory):
            try:
                yield self._to_raw_exercise(record, language_codes)
            except (KeyError, TypeError) as exc:
                logger.error("Skipping malformed exerciseinfo record %s: %s", record.get("id", "?"), exc)

    def _language_code_map(self) -> dict[int, str]:
        directory = self.raw_dir / "language"
        return {record["id"]: record["short_name"] for record in iter_cached_records(directory)}

    @staticmethod
    def _to_raw_exercise(record: dict, language_codes: dict[int, str]) -> RawExerciseRecord:
        category = safe_get(record, "category", "name", default="") or ""

        equipment = [EquipmentRef(id=item["id"], name=item["name"]) for item in record.get("equipment", [])]
        primary_muscles = [
            MuscleRef(id=m["id"], name=m["name"], name_en=m.get("name_en", ""), is_front=m.get("is_front"))
            for m in record.get("muscles", [])
        ]
        secondary_muscles = [
            MuscleRef(id=m["id"], name=m["name"], name_en=m.get("name_en", ""), is_front=m.get("is_front"))
            for m in record.get("muscles_secondary", [])
        ]

        license_info = LicenseInfo(
            name=safe_get(record, "license", "full_name", default="") or "",
            url=safe_get(record, "license", "url", default="") or "",
        )

        images = [
            MediaAsset(
                uuid=image["uuid"],
                remote_url=image["image"],
                is_main=bool(image.get("is_main")),
                extra={
                    "thumbnails": image.get("thumbnails", {}),
                    "style": image.get("style"),
                    "isAiGenerated": image.get("is_ai_generated", False),
                },
            )
            for image in record.get("images", [])
            if image.get("image")
        ]
        videos = [
            MediaAsset(
                uuid=video["uuid"],
                remote_url=video["video"],
                is_main=bool(video.get("is_main")),
                extra={
                    "durationSeconds": _safe_float(video.get("duration")),
                    "width": video.get("width"),
                    "height": video.get("height"),
                    "codec": video.get("codec"),
                },
            )
            for video in record.get("videos", [])
            if video.get("video")
        ]

        translations: list[TranslationRecord] = []
        for translation in record.get("translations", []):
            language_id = translation.get("language")
            language_code = language_codes.get(language_id)
            if not language_code:
                logger.warning(
                    "Exercise %s translation %s references unknown language id=%s; skipping",
                    record.get("uuid"),
                    translation.get("id"),
                    language_id,
                )
                continue
            translations.append(
                TranslationRecord(
                    language_code=language_code,
                    name=translation.get("name", "") or "",
                    description=translation.get("description", "") or "",
                    aliases=[a["alias"] for a in translation.get("aliases", []) if a.get("alias")],
                    source_id=translation.get("id"),
                    created=translation.get("created"),
                )
            )

        return RawExerciseRecord(
            id=str(record["id"]),
            uuid=record["uuid"],
            source="wger",
            variation_group=record.get("variation_group") or "",
            category=category,
            equipment=equipment,
            primary_muscles=primary_muscles,
            secondary_muscles=secondary_muscles,
            translations=translations,
            images=images,
            videos=videos,
            license=license_info,
        )


def _safe_float(value: object) -> float | None:
    try:
        return float(value) if value is not None else None
    except (TypeError, ValueError):
        return None
