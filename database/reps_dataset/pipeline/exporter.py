"""Pipeline Step 8 (export) and Step 9 (statistics).

`Exporter.export` writes the single production database file,
`data/output/reps_exercises.json`, matching the exact schema required by
the REPS mobile application. `Exporter.write_statistics` writes
`data/output/statistics.json` and logs a human-readable summary table.

Exercises are sorted by `id` before serialization so re-running the
pipeline against an unchanged source produces a byte-for-byte identical
file -- useful both for idempotency verification and for clean git diffs
when the dataset is refreshed.
"""

from __future__ import annotations

from pathlib import Path

from config import EXPORT_FILENAME, OUTPUT_DIR, STATISTICS_FILENAME
from pipeline.helpers import atomic_write_json, format_bytes
from pipeline.logger import get_logger
from pipeline.models import MergedExercise, PipelineStatistics

logger = get_logger("pipeline.exporter")


class Exporter:
    """Serializes `MergedExercise` objects to the final REPS JSON schema."""

    def export(self, exercises: list[MergedExercise]) -> Path:
        ordered = sorted(exercises, key=lambda exercise: exercise.id)
        payload = [exercise.to_exported().to_dict() for exercise in ordered]
        path = OUTPUT_DIR / EXPORT_FILENAME
        atomic_write_json(path, payload)
        logger.info("Exported %s exercises to %s", len(payload), path)
        return path

    def write_statistics(self, stats: PipelineStatistics) -> Path:
        # Only report the on-disk file's size when *this* run actually wrote it --
        # otherwise a run that produced zero exercises (e.g. a misconfigured
        # provider selection) would misleadingly report a stale prior build's size.
        export_path = OUTPUT_DIR / EXPORT_FILENAME
        if stats.exercises_exported > 0 and export_path.exists():
            stats.final_database_bytes = export_path.stat().st_size

        path = OUTPUT_DIR / STATISTICS_FILENAME
        atomic_write_json(path, stats.to_dict())

        self._log_summary(stats)
        return path

    @staticmethod
    def _log_summary(stats: PipelineStatistics) -> None:
        logger.info("=" * 60)
        logger.info("REPS exercise dataset build summary")
        logger.info("-" * 60)
        logger.info("Exercises downloaded  : %s", stats.exercises_downloaded)
        logger.info("Exercises exported     : %s", stats.exercises_exported)
        logger.info("Translations removed   : %s", stats.translations_removed)
        logger.info("Duplicates removed      : %s", stats.duplicates_removed)
        logger.info(
            "Images downloaded       : %s (cached: %s)", stats.images_downloaded, stats.images_skipped_cached
        )
        logger.info(
            "Videos downloaded       : %s (cached: %s)", stats.videos_downloaded, stats.videos_skipped_cached
        )
        logger.info("Broken media            : %s", stats.broken_media)
        logger.info("Validation errors       : %s", stats.validation_errors)
        logger.info("Validation warnings     : %s", stats.validation_warnings)
        logger.info("Execution time          : %.2fs", stats.execution_seconds)
        logger.info("Final database size     : %s", format_bytes(stats.final_database_bytes))
        logger.info("=" * 60)
