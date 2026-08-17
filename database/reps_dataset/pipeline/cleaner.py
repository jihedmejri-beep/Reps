"""Pipeline Step 3 (language filter) and Step 5 (normalization + dedup).

Both steps operate on the provider-agnostic `RawExerciseRecord` model
*before* translations are merged into a single localized object, which is
the only point at which "duplicate translation" or "duplicate alias"
are still meaningful, per-language concepts. Merging happens afterwards,
in `pipeline/merger.py`.

This module never talks to a provider or the network -- it is pure data
transformation, which keeps it trivially unit-testable.
"""

from __future__ import annotations

from config import KEPT_LANGUAGES, VIDEOS_ENABLED
from pipeline.helpers import clean_text, dedupe_case_insensitive, normalize_title_case
from pipeline.logger import get_logger
from pipeline.models import EquipmentRef, MediaAsset, MuscleRef, PipelineStatistics, RawExerciseRecord, TranslationRecord

logger = get_logger("pipeline.cleaner")

_LANGUAGE_ORDER = {code: index for index, code in enumerate(KEPT_LANGUAGES)}


class Cleaner:
    """Filters languages and normalizes/deduplicates a stream of `RawExerciseRecord`."""

    def __init__(self, kept_languages: tuple[str, ...] = KEPT_LANGUAGES) -> None:
        self.kept_languages = set(kept_languages)

    def clean(self, records: list[RawExerciseRecord], stats: PipelineStatistics) -> list[RawExerciseRecord]:
        cleaned: list[RawExerciseRecord] = []
        for record in records:
            cleaned_record, removed_translations, removed_dupes = self._clean_record(record)
            stats.translations_removed += removed_translations
            stats.duplicates_removed += removed_dupes
            if not cleaned_record.translations:
                logger.debug(
                    "Dropping exercise %s (uuid=%s): no translation left in kept languages %s",
                    cleaned_record.id,
                    cleaned_record.uuid,
                    sorted(self.kept_languages),
                )
                continue
            cleaned.append(cleaned_record)

        stats.exercises_cleaned = len(cleaned)
        logger.info(
            "Cleaned %s -> %s exercises (%s dropped for having no kept-language translation)",
            len(records),
            len(cleaned),
            len(records) - len(cleaned),
        )
        return cleaned

    def _clean_record(self, record: RawExerciseRecord) -> tuple[RawExerciseRecord, int, int]:
        removed_translations = 0
        removed_dupes = 0

        # --- Step 3: keep only the configured languages ---------------------------
        kept_translations = [t for t in record.translations if t.language_code in self.kept_languages]
        removed_translations += len(record.translations) - len(kept_translations)

        # --- Step 5: normalize text, drop empty names, dedupe per language --------
        normalized_translations: list[TranslationRecord] = []
        seen_languages: set[str] = set()
        for translation in sorted(kept_translations, key=lambda t: t.created or "", reverse=True):
            name = normalize_title_case(translation.name)
            if not name:
                removed_translations += 1
                continue
            if translation.language_code in seen_languages:
                removed_dupes += 1
                continue
            seen_languages.add(translation.language_code)
            raw_aliases = [clean_text(a) for a in translation.aliases if clean_text(a)]
            deduped_aliases = sorted(dedupe_case_insensitive(raw_aliases))
            removed_dupes += len(raw_aliases) - len(deduped_aliases)
            normalized_translations.append(
                TranslationRecord(
                    language_code=translation.language_code,
                    name=name,
                    description=clean_text(translation.description),
                    aliases=deduped_aliases,
                    source_id=translation.source_id,
                    created=translation.created,
                )
            )
        normalized_translations.sort(key=lambda t: _LANGUAGE_ORDER.get(t.language_code, len(_LANGUAGE_ORDER)))

        equipment, equipment_removed = self._dedupe_equipment(record.equipment)
        primary_muscles, primary_removed = self._dedupe_muscles(record.primary_muscles)
        secondary_muscles, secondary_removed = self._dedupe_muscles(record.secondary_muscles)
        images, images_removed = self._dedupe_media(record.images)
        # Videos are a disabled feature (see `config.VIDEOS_ENABLED`), not a
        # per-record data-quality issue -- stripped here, before dedup, so
        # every downstream stage simply never receives any. Flipping the
        # flag back to True restores the old behavior with no other code
        # changes required.
        if VIDEOS_ENABLED:
            videos, videos_removed = self._dedupe_media(record.videos)
        else:
            videos, videos_removed = [], 0
        removed_dupes += equipment_removed + primary_removed + secondary_removed + images_removed + videos_removed

        cleaned_record = RawExerciseRecord(
            id=record.id,
            uuid=record.uuid,
            source=record.source,
            variation_group=record.variation_group,
            category=clean_text(record.category),
            equipment=equipment,
            primary_muscles=primary_muscles,
            secondary_muscles=secondary_muscles,
            translations=normalized_translations,
            images=images,
            videos=videos,
            license=record.license,
        )
        return cleaned_record, removed_translations, removed_dupes

    @staticmethod
    def _dedupe_equipment(refs: list[EquipmentRef]) -> tuple[list[EquipmentRef], int]:
        valid = [r for r in refs if r.id and clean_text(r.name)]
        seen: set[int] = set()
        result: list[EquipmentRef] = []
        for ref in sorted(valid, key=lambda r: r.sort_key()):
            if ref.id in seen:
                continue
            seen.add(ref.id)
            result.append(EquipmentRef(id=ref.id, name=clean_text(ref.name)))
        return result, len(refs) - len(result)

    @staticmethod
    def _dedupe_muscles(refs: list[MuscleRef]) -> tuple[list[MuscleRef], int]:
        valid = [r for r in refs if r.id and clean_text(r.name)]
        seen: set[int] = set()
        result: list[MuscleRef] = []
        for ref in sorted(valid, key=lambda r: r.sort_key()):
            if ref.id in seen:
                continue
            seen.add(ref.id)
            result.append(MuscleRef(id=ref.id, name=clean_text(ref.name), name_en=clean_text(ref.name_en), is_front=ref.is_front))
        return result, len(refs) - len(result)

    @staticmethod
    def _dedupe_media(assets: list[MediaAsset]) -> tuple[list[MediaAsset], int]:
        valid = [a for a in assets if a.remote_url]
        seen: set[str] = set()
        result: list[MediaAsset] = []
        for asset in valid:
            key = asset.uuid or asset.remote_url
            if key in seen:
                continue
            seen.add(key)
            result.append(asset)
        return result, len(assets) - len(result)
