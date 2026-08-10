"""Pipeline Step 7: validation.

Runs a battery of structural and referential checks over the fully
merged, media-downloaded exercise list and produces
`data/output/validation_report.json`. Validation never raises or halts
the pipeline: every issue is collected and the run continues, per the
project's explicit "never stop because of validation errors" requirement
-- broken or incomplete records still make it into `reps_exercises.json`
so the app team can see (and fix) them through the report rather than
silently losing data.
"""

from __future__ import annotations

import datetime as dt
import uuid as uuid_lib
from pathlib import Path

from config import KEPT_LANGUAGES, OUTPUT_DIR, PROJECT_ROOT, VALIDATION_REPORT_FILENAME
from pipeline.helpers import atomic_write_json
from pipeline.logger import get_logger
from pipeline.models import MergedExercise, PipelineStatistics, ValidationIssue

logger = get_logger("pipeline.validator")


class Validator:
    """Collects `ValidationIssue`s across an exercise list without ever raising."""

    def __init__(self, kept_languages: tuple[str, ...] = KEPT_LANGUAGES) -> None:
        self.kept_languages = kept_languages

    def validate(self, exercises: list[MergedExercise], stats: PipelineStatistics) -> list[ValidationIssue]:
        issues: list[ValidationIssue] = list(self._check_duplicates(exercises))
        for exercise in exercises:
            issues.extend(self._check_exercise(exercise))

        stats.validation_errors = sum(1 for issue in issues if issue.severity == "error")
        stats.validation_warnings = sum(1 for issue in issues if issue.severity == "warning")
        logger.info(
            "Validation complete: %s error(s), %s warning(s) across %s exercises",
            stats.validation_errors,
            stats.validation_warnings,
            len(exercises),
        )
        return issues

    def write_report(self, issues: list[ValidationIssue], stats: PipelineStatistics) -> Path:
        report = {
            "generatedAt": dt.datetime.now(dt.timezone.utc).isoformat(),
            "totalIssues": len(issues),
            "errors": stats.validation_errors,
            "warnings": stats.validation_warnings,
            "issues": [issue.to_dict() for issue in issues],
        }
        path = OUTPUT_DIR / VALIDATION_REPORT_FILENAME
        atomic_write_json(path, report)
        logger.info("Validation report written to %s", path)
        return path

    # -- duplicate detection (dataset-wide) ------------------------------------

    @staticmethod
    def _check_duplicates(exercises: list[MergedExercise]) -> list[ValidationIssue]:
        issues: list[ValidationIssue] = []
        uuid_counts: dict[str, int] = {}
        id_counts: dict[str, int] = {}
        for exercise in exercises:
            uuid_counts[exercise.uuid] = uuid_counts.get(exercise.uuid, 0) + 1
            id_counts[exercise.id] = id_counts.get(exercise.id, 0) + 1
        for value, count in uuid_counts.items():
            if count > 1:
                issues.append(ValidationIssue("error", "duplicate_uuid", value, f"UUID {value} appears {count} times"))
        for value, count in id_counts.items():
            if count > 1:
                issues.append(ValidationIssue("error", "duplicate_id", value, f"id {value} appears {count} times"))
        return issues

    # -- per-exercise checks ----------------------------------------------------

    def _check_exercise(self, exercise: MergedExercise) -> list[ValidationIssue]:
        return [
            *self._check_uuid_format(exercise),
            *self._check_names(exercise),
            *self._check_descriptions(exercise),
            *self._check_category(exercise),
            *self._check_muscles_and_equipment(exercise),
            *self._check_media(exercise),
            *self._check_aliases(exercise),
        ]

    @staticmethod
    def _check_uuid_format(exercise: MergedExercise) -> list[ValidationIssue]:
        issues: list[ValidationIssue] = []
        try:
            uuid_lib.UUID(exercise.uuid)
        except (ValueError, AttributeError, TypeError):
            issues.append(ValidationIssue("error", "invalid_uuid", exercise.id, f"UUID {exercise.uuid!r} is not a valid UUID"))
        if exercise.variation_group:
            try:
                uuid_lib.UUID(exercise.variation_group)
            except (ValueError, AttributeError, TypeError):
                issues.append(
                    ValidationIssue(
                        "warning",
                        "invalid_variation_group",
                        exercise.id,
                        f"variationGroup {exercise.variation_group!r} is not a valid UUID",
                    )
                )
        return issues

    def _check_names(self, exercise: MergedExercise) -> list[ValidationIssue]:
        if exercise.name.is_empty():
            return [ValidationIssue("error", "missing_name", exercise.id, "Exercise has no name in any kept language")]
        return [
            ValidationIssue("warning", "missing_name_language", exercise.id, f"Missing {lang} name")
            for lang in self.kept_languages
            if not getattr(exercise.name, lang)
        ]

    def _check_descriptions(self, exercise: MergedExercise) -> list[ValidationIssue]:
        if exercise.description.is_empty():
            return [
                ValidationIssue(
                    "warning", "missing_description", exercise.id, "Exercise has no description in any kept language"
                )
            ]
        return [
            ValidationIssue("warning", "missing_description_language", exercise.id, f"Missing {lang} description")
            for lang in self.kept_languages
            if not getattr(exercise.description, lang)
        ]

    @staticmethod
    def _check_category(exercise: MergedExercise) -> list[ValidationIssue]:
        if not exercise.category:
            return [ValidationIssue("error", "missing_category", exercise.id, "Exercise has no category")]
        return []

    @staticmethod
    def _check_muscles_and_equipment(exercise: MergedExercise) -> list[ValidationIssue]:
        issues: list[ValidationIssue] = []
        if not exercise.primary_muscles and not exercise.secondary_muscles:
            issues.append(ValidationIssue("warning", "missing_muscles", exercise.id, "Exercise has no primary or secondary muscles"))
        if not exercise.equipment:
            issues.append(ValidationIssue("warning", "missing_equipment", exercise.id, "Exercise has no equipment listed"))
        for ref in (*exercise.primary_muscles, *exercise.secondary_muscles):
            if ref.id <= 0 or not ref.name:
                issues.append(ValidationIssue("error", "broken_muscle_reference", exercise.id, f"Muscle reference id={ref.id} name={ref.name!r} is invalid"))
        for ref in exercise.equipment:
            if ref.id <= 0 or not ref.name:
                issues.append(ValidationIssue("error", "broken_equipment_reference", exercise.id, f"Equipment reference id={ref.id} name={ref.name!r} is invalid"))
        return issues

    @staticmethod
    def _check_media(exercise: MergedExercise) -> list[ValidationIssue]:
        issues: list[ValidationIssue] = []
        for kind, assets in (("image", exercise.images), ("video", exercise.videos)):
            for asset in assets:
                if not asset.remote_url:
                    issues.append(ValidationIssue("error", "broken_media_reference", exercise.id, f"{kind} has no remote URL"))
                    continue
                if asset.local_path is None or not (PROJECT_ROOT / asset.local_path).exists():
                    issues.append(
                        ValidationIssue("warning", "broken_media", exercise.id, f"{kind} {asset.uuid} was not downloaded successfully")
                    )
        return issues

    @staticmethod
    def _check_aliases(exercise: MergedExercise) -> list[ValidationIssue]:
        lowered = [alias.lower() for alias in exercise.aliases]
        if len(lowered) != len(set(lowered)):
            return [ValidationIssue("warning", "duplicate_alias", exercise.id, "Exercise has duplicate aliases (case-insensitive)")]
        return []
