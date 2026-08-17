"""Typed data structures shared across the pipeline and provider modules.

Two families of models live here:

* **Intermediate models** (`RawExerciseRecord` and friends) — the common
  contract every provider must normalize its raw API data into. The
  cleaner, merger, media downloader, validator and exporter only ever
  operate on these, never on a provider's native JSON shape.
* **Export models** (`ExportedExercise`) — a typed mirror of the exact
  `reps_exercises.json` schema required by REPS.
"""

from __future__ import annotations

from dataclasses import dataclass, field
from typing import Any


# --------------------------------------------------------------------------
# Intermediate (provider -> pipeline) models
# --------------------------------------------------------------------------


@dataclass
class LocalizedText:
    en: str = ""
    fr: str = ""
    ar: str = ""

    def to_dict(self) -> dict[str, str]:
        return {"en": self.en, "fr": self.fr, "ar": self.ar}

    def is_empty(self) -> bool:
        return not (self.en or self.fr or self.ar)

    def set(self, language_code: str, value: str) -> None:
        if language_code not in ("en", "fr", "ar"):
            raise ValueError(f"Unsupported language code: {language_code!r}")
        setattr(self, language_code, value)


@dataclass
class LicenseInfo:
    name: str = ""
    url: str = ""

    def to_dict(self) -> dict[str, str]:
        return {"name": self.name, "url": self.url}


@dataclass
class MuscleRef:
    id: int
    name: str
    name_en: str = ""
    is_front: bool | None = None

    def to_dict(self) -> dict[str, Any]:
        payload: dict[str, Any] = {"id": self.id, "name": self.name}
        if self.name_en:
            payload["nameEn"] = self.name_en
        if self.is_front is not None:
            payload["isFront"] = self.is_front
        return payload

    def sort_key(self) -> tuple:
        return (self.id,)


@dataclass
class EquipmentRef:
    id: int
    name: str

    def to_dict(self) -> dict[str, Any]:
        return {"id": self.id, "name": self.name}

    def sort_key(self) -> tuple:
        return (self.id,)


@dataclass
class MediaAsset:
    uuid: str
    remote_url: str
    is_main: bool = False
    local_path: str | None = None
    width: int | None = None
    height: int | None = None
    size_bytes: int | None = None
    checksum_sha256: str | None = None
    extra: dict[str, Any] = field(default_factory=dict)

    def to_dict(self) -> dict[str, Any]:
        # `uuid`/`isMain` are kept alongside the width/height/size/checksum
        # fields required by the asset system spec: `uuid` is the stable id
        # the database layer references, and the filename convention alone
        # (main.ext vs image_2.ext) is not enough for API consumers that
        # never touch the filesystem -- dropping `isMain` would be a
        # functional regression for picking a thumbnail.
        payload: dict[str, Any] = {
            "uuid": self.uuid,
            "url": self.remote_url,
            "isMain": self.is_main,
            "localPath": self.local_path,
            "width": self.width,
            "height": self.height,
            "size": self.size_bytes,
            "checksum": self.checksum_sha256,
        }
        payload.update(self.extra)
        return payload


@dataclass
class TranslationRecord:
    """One language's worth of translation data, prior to merging."""

    language_code: str
    name: str
    description: str
    aliases: list[str] = field(default_factory=list)
    source_id: int | str | None = None
    created: str | None = None


@dataclass
class RawExerciseRecord:
    """Common intermediate representation produced by every provider.

    This is the contract between `providers/*` and the rest of the
    pipeline (cleaner -> merger -> media_downloader -> validator ->
    exporter). Providers must converge their native API shape into this
    structure inside their `normalize()` implementation.
    """

    id: str
    uuid: str
    source: str
    variation_group: str = ""
    category: str = ""
    equipment: list[EquipmentRef] = field(default_factory=list)
    primary_muscles: list[MuscleRef] = field(default_factory=list)
    secondary_muscles: list[MuscleRef] = field(default_factory=list)
    translations: list[TranslationRecord] = field(default_factory=list)
    images: list[MediaAsset] = field(default_factory=list)
    videos: list[MediaAsset] = field(default_factory=list)
    license: LicenseInfo = field(default_factory=LicenseInfo)


@dataclass
class MergedExercise:
    """Output of pipeline Step 4 (merge): one localized record per exercise.

    Still holds rich objects rather than plain dicts (`MediaAsset`,
    `EquipmentRef`, `MuscleRef`, `LicenseInfo`) so `pipeline/media_downloader.py`
    can populate `MediaAsset.local_path` in place before the exporter
    flattens everything to the final JSON schema via `to_exported()`.
    """

    id: str
    uuid: str
    source: str
    variation_group: str
    category: str
    equipment: list[EquipmentRef]
    primary_muscles: list[MuscleRef]
    secondary_muscles: list[MuscleRef]
    name: LocalizedText
    description: LocalizedText
    aliases: list[str]
    images: list[MediaAsset]
    videos: list[MediaAsset]
    license: LicenseInfo

    def to_exported(self) -> "ExportedExercise":
        return ExportedExercise(
            id=self.id,
            uuid=self.uuid,
            variation_group=self.variation_group,
            name=self.name,
            description=self.description,
            category=self.category,
            equipment=[e.to_dict() for e in self.equipment],
            primary_muscles=[m.to_dict() for m in self.primary_muscles],
            secondary_muscles=[m.to_dict() for m in self.secondary_muscles],
            images=[i.to_dict() for i in self.images],
            videos=[v.to_dict() for v in self.videos],
            aliases=self.aliases,
            license=self.license,
            source=self.source,
        )


# --------------------------------------------------------------------------
# Export model
# --------------------------------------------------------------------------


@dataclass
class ExportedExercise:
    """Typed mirror of the exact schema required for `reps_exercises.json`."""

    id: str
    uuid: str
    variation_group: str
    name: LocalizedText
    description: LocalizedText
    category: str
    equipment: list[dict[str, Any]]
    primary_muscles: list[dict[str, Any]]
    secondary_muscles: list[dict[str, Any]]
    images: list[dict[str, Any]]
    videos: list[dict[str, Any]]
    aliases: list[str]
    license: LicenseInfo
    source: str

    def to_dict(self) -> dict[str, Any]:
        return {
            "id": self.id,
            "uuid": self.uuid,
            "variationGroup": self.variation_group,
            "name": self.name.to_dict(),
            "description": self.description.to_dict(),
            "category": self.category,
            "equipment": self.equipment,
            "primaryMuscles": self.primary_muscles,
            "secondaryMuscles": self.secondary_muscles,
            "images": self.images,
            "videos": self.videos,
            "aliases": self.aliases,
            "license": self.license.to_dict(),
            "source": self.source,
        }


# --------------------------------------------------------------------------
# Validation / statistics models
# --------------------------------------------------------------------------


@dataclass
class ValidationIssue:
    severity: str  # "error" | "warning"
    code: str
    exercise_id: str
    message: str

    def to_dict(self) -> dict[str, Any]:
        return {
            "severity": self.severity,
            "code": self.code,
            "exerciseId": self.exercise_id,
            "message": self.message,
        }


@dataclass
class PipelineStatistics:
    exercises_downloaded: int = 0
    exercises_cleaned: int = 0
    exercises_exported: int = 0
    translations_removed: int = 0
    duplicates_removed: int = 0
    images_downloaded: int = 0
    images_skipped_cached: int = 0
    videos_downloaded: int = 0
    videos_skipped_cached: int = 0
    broken_media: int = 0
    validation_errors: int = 0
    validation_warnings: int = 0
    execution_seconds: float = 0.0
    final_database_bytes: int = 0

    def to_dict(self) -> dict[str, Any]:
        return {
            "exercisesDownloaded": self.exercises_downloaded,
            "exercisesCleaned": self.exercises_cleaned,
            "exercisesExported": self.exercises_exported,
            "translationsRemoved": self.translations_removed,
            "duplicatesRemoved": self.duplicates_removed,
            "imagesDownloaded": self.images_downloaded,
            "imagesSkippedCached": self.images_skipped_cached,
            "videosDownloaded": self.videos_downloaded,
            "videosSkippedCached": self.videos_skipped_cached,
            "brokenMedia": self.broken_media,
            "validationErrors": self.validation_errors,
            "validationWarnings": self.validation_warnings,
            "executionSeconds": round(self.execution_seconds, 2),
            "finalDatabaseBytes": self.final_database_bytes,
        }
