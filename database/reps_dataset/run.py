#!/usr/bin/env python3
"""REPS exercise dataset pipeline entry point.

    python run.py                       # download + build the full database (wger provider)
    python run.py --providers wger custom
    python run.py --skip-media          # fast iteration: skip image/video downloads
    python run.py --skip-download       # rebuild from already-cached raw data, no network calls
    python run.py --force               # ignore every cache: re-download and re-process everything
    python run.py --languages en fr     # keep only a subset of {en, fr, ar} for this run
    python run.py --verbose             # debug-level console logging

Re-running this command is always safe: every stage skips work it has
already completed (see README.md -> "Idempotency").
"""

from __future__ import annotations

import argparse
import dataclasses
import logging
import sys

import config
from pipeline.cleaner import Cleaner
from pipeline.exporter import Exporter
from pipeline.helpers import atomic_write_json, timer
from pipeline.logger import get_logger, set_verbosity
from pipeline.media_downloader import MediaDownloader
from pipeline.merger import Merger
from pipeline.models import MergedExercise, PipelineStatistics, RawExerciseRecord
from pipeline.svg_assets import SvgAssetDownloader
from pipeline.validator import Validator
from providers import available_providers, get_provider_class

logger = get_logger("run")


def parse_args(argv: list[str] | None = None) -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Build the REPS exercise database from one or more providers.",
        formatter_class=argparse.ArgumentDefaultsHelpFormatter,
    )
    parser.add_argument(
        "--providers",
        nargs="+",
        default=list(config.DEFAULT_ENABLED_PROVIDERS),
        choices=available_providers(),
        help="Which providers to run.",
    )
    parser.add_argument(
        "--languages",
        nargs="+",
        default=list(config.KEPT_LANGUAGES),
        choices=list(config.KEPT_LANGUAGES),
        help="Subset of the supported languages to keep for this run.",
    )
    parser.add_argument("--skip-media", action="store_true", help="Skip downloading images/videos.")
    parser.add_argument(
        "--skip-download", action="store_true", help="Skip network downloads; rebuild from cached raw data only."
    )
    parser.add_argument("--force", action="store_true", help="Ignore every cache: re-download and re-process everything.")
    parser.add_argument("--download-workers", type=int, default=config.CONCURRENCY.download_workers)
    parser.add_argument("--media-workers", type=int, default=config.CONCURRENCY.media_workers)
    parser.add_argument("--verbose", "-v", action="store_true", help="Enable debug-level console logging.")
    return parser.parse_args(argv)


def run(args: argparse.Namespace) -> PipelineStatistics:
    config.ensure_directories()
    stats = PipelineStatistics()
    exporter = Exporter()
    merged: list[MergedExercise] = []

    with timer() as elapsed:
        raw_records: list[RawExerciseRecord] = []
        for provider_name in args.providers:
            provider_cls = get_provider_class(provider_name)
            provider = provider_cls(force_refresh=args.force, download_workers=args.download_workers)
            logger.info("=== Provider: %s ===", provider_name)
            if args.skip_download:
                logger.info("Skipping download stage for %s (--skip-download)", provider_name)
            else:
                provider.fetch_raw()
            provider_records = list(provider.normalize())
            logger.info("%s: normalized %s exercise(s)", provider_name, len(provider_records))
            raw_records.extend(provider_records)

        stats.exercises_downloaded = len(raw_records)

        if not raw_records:
            logger.error("No exercises were produced by any enabled provider (%s)", ", ".join(args.providers))
        else:
            cleaner = Cleaner(kept_languages=tuple(args.languages))
            cleaned = cleaner.clean(raw_records, stats)
            atomic_write_json(config.CLEANED_DIR / config.CLEANED_FILENAME, [dataclasses.asdict(r) for r in cleaned])

            merger = Merger()
            merged = merger.merge(cleaned, stats)
            atomic_write_json(config.MERGED_DIR / config.MERGED_FILENAME, [dataclasses.asdict(m) for m in merged])

            if args.skip_media:
                logger.info("Skipping media/asset download stage (--skip-media)")
            else:
                media_downloader = MediaDownloader(workers=args.media_workers, force_refresh=args.force)
                media_downloader.download_all(merged, stats)
                media_downloader.write_report(config.OUTPUT_DIR / "image_download_report.json")

                svg_downloader = SvgAssetDownloader(workers=args.media_workers, force_refresh=args.force)
                svg_report = svg_downloader.run()
                atomic_write_json(config.OUTPUT_DIR / "svg_validation_report.json", svg_report)

            validator = Validator(kept_languages=tuple(args.languages))
            issues = validator.validate(merged, stats)
            validator.write_report(issues, stats)

            exporter.export(merged)
            stats.exercises_exported = len(merged)

    stats.execution_seconds = elapsed.seconds
    exporter.write_statistics(stats)
    return stats


def main(argv: list[str] | None = None) -> int:
    args = parse_args(argv)
    if args.verbose:
        set_verbosity(logging.DEBUG)
    try:
        run(args)
    except Exception:
        logger.exception("Pipeline run failed")
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
