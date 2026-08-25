"""Pipeline Step 4: merge per-language translations into one localized object.

Consumes cleaned `RawExerciseRecord`s (already language-filtered,
normalized and deduplicated by `pipeline/cleaner.py`) and produces exactly
one `MergedExercise` per exercise UUID, with `name`/`description` merged
into `{en, fr, ar}` dicts and aliases flattened across every kept
language into a single deduplicated list.

Exercises that are variations of one another keep their own separate
UUID/id -- wger only ever asks REPS to *group* them via the shared
`variation_group` UUID, not collapse them into one record -- so that
field is simply carried through unchanged onto `MergedExercise.variation_group`.
"""

from __future__ import annotations

from pipeline.helpers import dedupe_preserve_order
from pipeline.logger import get_logger
from pipeline.models import LocalizedText, MergedExercise, PipelineStatistics, RawExerciseRecord

logger = get_logger("pipeline.merger")


class Merger:
    """Merges cleaned `RawExerciseRecord`s into `MergedExercise`s."""

    def merge(self, records: list[RawExerciseRecord], stats: PipelineStatistics) -> list[MergedExercise]:
        merged = [self._merge_record(record) for record in records]
        logger.info("Merged %s exercises into localized name/description objects", len(merged))
        return merged

    @staticmethod
    def _merge_record(record: RawExerciseRecord) -> MergedExercise:
        name = LocalizedText()
        description = LocalizedText()
        aliases: list[str] = []

        for translation in record.translations:
            name.set(translation.language_code, translation.name)
            description.set(translation.language_code, translation.description)
            aliases.extend(translation.aliases)

        return MergedExercise(
            id=record.id,
            uuid=record.uuid,
            source=record.source,
            variation_group=record.variation_group,
            category=record.category,
            equipment=record.equipment,
            primary_muscles=record.primary_muscles,
            secondary_muscles=record.secondary_muscles,
            name=name,
            description=description,
            aliases=sorted(dedupe_preserve_order(aliases)),
            images=record.images,
            videos=record.videos,
            license=record.license,
        )
