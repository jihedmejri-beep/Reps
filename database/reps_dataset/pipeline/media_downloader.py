"""Pipeline Step 6: download every referenced exercise image/video into the
production asset layout and populate integrity metadata.

Mutates `MediaAsset.local_path/width/height/size_bytes/checksum_sha256` on
each `MergedExercise`'s images/videos in place, so the exporter can embed a
local, offline-usable, integrity-checked path in `reps_exercises.json`
alongside the original remote URL. Downloads are concurrent, retried on
failure (with dedicated HTTP 429/`Retry-After` handling), content-type
validated, and skipped when a matching non-empty file already exists on
disk -- which is what makes re-running `python run.py` cheap after the
first full asset pull. A final per-exercise pass removes duplicate assets
(identical sha256 across two entries of the same exercise).

Assets are stored exercise-scoped, not provider/uuid-scoped:

    assets/exercises/{exercise_id}/main.{ext}       (the is_main image)
    assets/exercises/{exercise_id}/image_2.{ext}    (every other image)
    assets/exercises/{exercise_id}/video_1.{ext}    (every video)

See `config.AssetSettings` (`config.ASSET`) for every tunable, and
`data/output/image_download_report.json` (written by `write_report`) for a
full accounting of what happened on the last run.
"""

from __future__ import annotations

import datetime as dt
import hashlib
import threading
import time
from concurrent.futures import ThreadPoolExecutor, as_completed
from pathlib import Path
from urllib.parse import urlsplit

import requests
from tqdm import tqdm

from config import ASSET, EXERCISE_ASSETS_DIR, PROJECT_ROOT
from pipeline.helpers import atomic_write_json, build_http_session, ensure_dir, read_json_if_exists, svg_dimensions
from pipeline.logger import get_logger
from pipeline.models import MediaAsset, MergedExercise, PipelineStatistics

logger = get_logger("pipeline.media_downloader")

_CHUNK_SIZE = 64 * 1024

# Persists which asset uuids were previously identified as intra-exercise
# duplicates (see `_dedupe_all`) so subsequent runs exclude them *before*
# building the job list, instead of re-downloading, re-checksumming, and
# re-deleting the same duplicate file on every single run -- wasted
# bandwidth that, for large video files under concurrent load, was
# observed to occasionally time out (see ENGINEERING_REPORT.md).
_DUPLICATE_MANIFEST_PATH = EXERCISE_ASSETS_DIR / "_duplicate_manifest.json"


class _AssetJob:
    __slots__ = ("asset", "exercise_id", "kind", "basename")

    def __init__(self, asset: MediaAsset, exercise_id: str, kind: str, basename: str) -> None:
        self.asset = asset
        self.exercise_id = exercise_id
        self.kind = kind  # "image" | "video"
        self.basename = basename  # e.g. "main", "image_2", "video_1"


class MediaDownloader:
    """Downloads every image/video referenced by a list of `MergedExercise`
    into the exercise-scoped `assets/` layout, with checksum/dimension
    metadata and per-exercise duplicate removal."""

    def __init__(self, *, workers: int = 12, force_refresh: bool = False) -> None:
        self.workers = max(1, workers)
        self.force_refresh = force_refresh
        self.session = build_http_session()
        self._stats_lock = threading.Lock()
        self._report_lock = threading.Lock()
        self._report = {
            "generatedAt": None,
            "totalImages": 0,
            "totalVideos": 0,
            "downloaded": 0,
            "skippedCached": 0,
            "failed": 0,
            "retried429": 0,
            "invalidContentType": 0,
            "duplicatesRemoved": 0,
            "totalBytes": 0,
            "failures": [],
        }

    # ---------------------------------------------------------------- run

    def download_all(self, exercises: list[MergedExercise], stats: PipelineStatistics) -> None:
        ensure_dir(EXERCISE_ASSETS_DIR)

        known_duplicates = self._load_known_duplicates()
        excluded = self._exclude_known_duplicates(exercises, known_duplicates)
        if excluded:
            logger.info("Excluded %s asset(s) already known to be duplicates from a prior run", excluded)

        jobs: list[_AssetJob] = []
        for exercise in exercises:
            jobs.extend(self._build_jobs(exercise))

        if not jobs:
            logger.info("No media referenced by any exercise; skipping asset download")
            return

        self._report["totalImages"] = sum(1 for job in jobs if job.kind == "image")
        self._report["totalVideos"] = sum(1 for job in jobs if job.kind == "video")
        logger.info("Downloading assets: %s image(s), %s video(s)", self._report["totalImages"], self._report["totalVideos"])

        with tqdm(total=len(jobs), desc="Downloading assets", unit="file") as progress:
            with ThreadPoolExecutor(max_workers=self.workers) as executor:
                futures = {executor.submit(self._download_one, job): job for job in jobs}
                for future in as_completed(futures):
                    job = futures[future]
                    try:
                        outcome = future.result()
                    except Exception as exc:  # noqa: BLE001 - a broken asset must never abort the run
                        logger.error(
                            "Giving up on %s %s (exercise %s, %s): %s",
                            job.kind,
                            job.asset.uuid,
                            job.exercise_id,
                            job.asset.remote_url,
                            exc,
                        )
                        self._record_failure(job, str(exc), stats)
                    else:
                        self._record_outcome(job, outcome, stats)
                    finally:
                        progress.update(1)

        duplicates_removed = self._dedupe_all(exercises)
        with self._report_lock:
            self._report["duplicatesRemoved"] = duplicates_removed
        with self._stats_lock:
            stats.duplicates_removed += duplicates_removed

    def write_report(self, path: Path) -> None:
        with self._report_lock:
            self._report["generatedAt"] = dt.datetime.now(dt.timezone.utc).isoformat()
            payload = dict(self._report)
        atomic_write_json(path, payload)
        logger.info(
            "Asset report: %s downloaded, %s cached, %s failed, %s duplicates removed (%s total)",
            payload["downloaded"],
            payload["skippedCached"],
            payload["failed"],
            payload["duplicatesRemoved"],
            payload["totalImages"] + payload["totalVideos"],
        )

    # ------------------------------------------------------------ jobs

    @staticmethod
    def _build_jobs(exercise: MergedExercise) -> list[_AssetJob]:
        jobs: list[_AssetJob] = []

        # Filename assignment is a *display* convention independent of the
        # sourced `is_main` flag: whichever image is main (or, if none is
        # flagged, the first image by original order) becomes `main.ext`;
        # every other image is `image_2.ext`, `image_3.ext`, ... The
        # sourced `is_main` value itself is never altered.
        images = list(exercise.images)
        main_index = next((i for i, img in enumerate(images) if img.is_main), 0 if images else None)
        image_number = 2
        for i, image in enumerate(images):
            basename = "main" if i == main_index else f"image_{image_number}"
            if i != main_index:
                image_number += 1
            jobs.append(_AssetJob(image, exercise.id, "image", basename))

        for i, video in enumerate(exercise.videos, start=1):
            jobs.append(_AssetJob(video, exercise.id, "video", f"video_{i}"))

        return jobs

    # -------------------------------------------------------- one asset

    def _download_one(self, job: _AssetJob) -> dict:
        """Download (or reuse) a single asset. Returns an outcome dict; also
        mutates `job.asset` in place with local_path/width/height/size/checksum."""
        exercise_dir = ensure_dir(EXERCISE_ASSETS_DIR / job.exercise_id)

        existing = self._find_existing(exercise_dir, job.basename)
        if existing is not None and not self.force_refresh:
            self._populate_from_disk(job.asset, existing)
            return {"downloaded": False, "skipped": True, "failed": False, "invalidContentType": False, "retried429": 0}

        return self._fetch_and_write(job, exercise_dir)

    def _fetch_and_write(self, job: _AssetJob, exercise_dir: Path) -> dict:
        allowed_prefixes = (
            ASSET.content_type_image_prefixes if job.kind == "image" else ASSET.content_type_video_prefixes
        )
        url_suffix = Path(urlsplit(job.asset.remote_url).path).suffix.lower()

        retried_429 = 0
        rate_limit_deadline = time.monotonic() + ASSET.rate_limit_retry_budget_seconds
        last_exc: Exception | None = None

        for attempt in range(1, ASSET.max_retries + 1):
            try:
                response = self.session.get(
                    job.asset.remote_url, timeout=ASSET.request_timeout_seconds, stream=True
                )
                if response.status_code == 429:
                    if time.monotonic() >= rate_limit_deadline:
                        raise RuntimeError("exceeded 429 retry budget")
                    retry_after = float(response.headers.get("Retry-After", ASSET.default_rate_limit_wait_seconds))
                    logger.warning("Rate limited on %s (%s); sleeping %.1fs", job.asset.remote_url, job.kind, retry_after)
                    time.sleep(retry_after)
                    retried_429 += 1
                    continue

                response.raise_for_status()

                content_type = response.headers.get("Content-Type", "").split(";")[0].strip().lower()
                if content_type and not any(content_type.startswith(p) for p in allowed_prefixes):
                    # SVGs are legitimately served as image/svg+xml, text/xml or
                    # application/xml depending on the origin server.
                    if not (job.kind == "image" and content_type in ASSET.content_type_svg_types):
                        return {
                            "downloaded": False,
                            "skipped": False,
                            "failed": True,
                            "invalidContentType": True,
                            "retried429": retried_429,
                            "reason": f"unexpected Content-Type {content_type!r} for {job.kind}",
                        }

                extension = self._resolve_extension(url_suffix, content_type)
                dest_path = exercise_dir / f"{job.basename}{extension}"
                tmp_path = exercise_dir / f".{job.basename}.part"

                hasher = hashlib.sha256()
                size = 0
                with tmp_path.open("wb") as handle:
                    for chunk in response.iter_content(chunk_size=_CHUNK_SIZE):
                        if not chunk:
                            continue
                        handle.write(chunk)
                        hasher.update(chunk)
                        size += len(chunk)

                if size == 0:
                    tmp_path.unlink(missing_ok=True)
                    raise OSError("downloaded file is empty")

                tmp_path.replace(dest_path)
                self._populate_metadata(job.asset, dest_path, size, hasher.hexdigest())
                return {
                    "downloaded": True,
                    "skipped": False,
                    "failed": False,
                    "invalidContentType": False,
                    "retried429": retried_429,
                    "bytes": size,
                }
            except (requests.RequestException, OSError, RuntimeError) as exc:
                last_exc = exc
                logger.warning("Attempt %s/%s failed for %s %s: %s", attempt, ASSET.max_retries, job.kind, job.asset.remote_url, exc)

        return {
            "downloaded": False,
            "skipped": False,
            "failed": True,
            "invalidContentType": False,
            "retried429": retried_429,
            "reason": str(last_exc) if last_exc else "unknown error",
        }

    @staticmethod
    def _resolve_extension(url_suffix: str, content_type: str) -> str:
        known_extensions = set(ASSET.content_type_extensions.values()) | {".jpeg"}
        if url_suffix in known_extensions:
            return ".jpg" if url_suffix == ".jpeg" else url_suffix
        mapped = ASSET.content_type_extensions.get(content_type)
        if mapped:
            return mapped
        return url_suffix or ".bin"

    @staticmethod
    def _find_existing(exercise_dir: Path, basename: str) -> Path | None:
        for candidate in exercise_dir.glob(f"{basename}.*"):
            if candidate.is_file() and candidate.stat().st_size > 0:
                return candidate
        return None

    def _populate_from_disk(self, asset: MediaAsset, path: Path) -> None:
        data = path.read_bytes()
        checksum = hashlib.sha256(data).hexdigest()
        self._populate_metadata(asset, path, len(data), checksum)

    def _populate_metadata(self, asset: MediaAsset, path: Path, size: int, checksum: str) -> None:
        asset.local_path = self._relative_path(path)
        asset.size_bytes = size
        asset.checksum_sha256 = checksum
        width, height = self._read_dimensions(path)
        asset.width = width
        asset.height = height

    @staticmethod
    def _read_dimensions(path: Path) -> tuple[int | None, int | None]:
        suffix = path.suffix.lower()
        if suffix == ".svg":
            return svg_dimensions(path) or (None, None)
        try:
            from PIL import Image  # local import: only needed for raster assets

            with Image.open(path) as img:
                return img.width, img.height
        except Exception:  # noqa: BLE001 - dimensions are best-effort metadata, not fatal
            return None, None

    @staticmethod
    def _relative_path(path: Path) -> str:
        return str(path.relative_to(PROJECT_ROOT)).replace("\\", "/")

    # ----------------------------------------------------------- report

    def _record_outcome(self, job: _AssetJob, outcome: dict, stats: PipelineStatistics) -> None:
        with self._report_lock:
            self._report["retried429"] += outcome.get("retried429", 0)
            if outcome["downloaded"]:
                self._report["downloaded"] += 1
                self._report["totalBytes"] += outcome.get("bytes", 0)
            elif outcome["skipped"]:
                self._report["skippedCached"] += 1
            elif outcome["failed"]:
                self._report["failed"] += 1
                if outcome.get("invalidContentType"):
                    self._report["invalidContentType"] += 1
                self._report["failures"].append(
                    {
                        "exerciseId": job.exercise_id,
                        "uuid": job.asset.uuid,
                        "url": job.asset.remote_url,
                        "kind": job.kind,
                        "reason": outcome.get("reason", "unknown"),
                    }
                )
        with self._stats_lock:
            if job.kind == "image":
                stats.images_downloaded += int(outcome["downloaded"])
                stats.images_skipped_cached += int(outcome["skipped"])
            else:
                stats.videos_downloaded += int(outcome["downloaded"])
                stats.videos_skipped_cached += int(outcome["skipped"])
            if outcome["failed"]:
                stats.broken_media += 1

    def _record_failure(self, job: _AssetJob, reason: str, stats: PipelineStatistics) -> None:
        with self._report_lock:
            self._report["failed"] += 1
            self._report["failures"].append(
                {"exerciseId": job.exercise_id, "uuid": job.asset.uuid, "url": job.asset.remote_url, "kind": job.kind, "reason": reason}
            )
        with self._stats_lock:
            stats.broken_media += 1

    # -------------------------------------------------- known-duplicates manifest

    @staticmethod
    def _load_known_duplicates() -> dict[str, list[str]]:
        return read_json_if_exists(_DUPLICATE_MANIFEST_PATH) or {}

    @staticmethod
    def _exclude_known_duplicates(exercises: list[MergedExercise], known_duplicates: dict[str, list[str]]) -> int:
        """Drop assets already identified as duplicates on a prior run from
        `exercise.images`/`exercise.videos` *before* any job is built for
        them -- so a known duplicate costs zero network calls on every run
        after the one that first found it, instead of being endlessly
        re-downloaded and re-deleted."""
        excluded = 0
        for exercise in exercises:
            duplicate_uuids = set(known_duplicates.get(exercise.id, []))
            if not duplicate_uuids:
                continue
            before = len(exercise.images) + len(exercise.videos)
            exercise.images = [a for a in exercise.images if a.uuid not in duplicate_uuids]
            exercise.videos = [a for a in exercise.videos if a.uuid not in duplicate_uuids]
            excluded += before - (len(exercise.images) + len(exercise.videos))
        return excluded

    # -------------------------------------------------------- dedup pass

    def _dedupe_all(self, exercises: list[MergedExercise]) -> int:
        """Remove duplicate assets *within the same exercise* (identical
        sha256 across two entries), deleting the now-unreferenced file. The
        first occurrence (main image / lowest video index) is always kept.
        Newly-found duplicate uuids are persisted to
        `_DUPLICATE_MANIFEST_PATH` so future runs never re-download them."""
        removed = 0
        known_duplicates = self._load_known_duplicates()
        for exercise in exercises:
            exercise.images, removed_images = self._dedupe_list(exercise.images, exercise.id, known_duplicates)
            exercise.videos, removed_videos = self._dedupe_list(exercise.videos, exercise.id, known_duplicates)
            removed += removed_images + removed_videos
        atomic_write_json(_DUPLICATE_MANIFEST_PATH, known_duplicates)
        return removed

    def _dedupe_list(
        self, assets: list[MediaAsset], exercise_id: str, known_duplicates: dict[str, list[str]]
    ) -> tuple[list[MediaAsset], int]:
        seen_checksums: dict[str, MediaAsset] = {}
        kept: list[MediaAsset] = []
        removed = 0
        for asset in assets:
            checksum = asset.checksum_sha256
            if checksum and checksum in seen_checksums:
                self._delete_asset_file(asset)
                known_duplicates.setdefault(exercise_id, [])
                if asset.uuid not in known_duplicates[exercise_id]:
                    known_duplicates[exercise_id].append(asset.uuid)
                removed += 1
                continue
            if checksum:
                seen_checksums[checksum] = asset
            kept.append(asset)
        return kept, removed

    @staticmethod
    def _delete_asset_file(asset: MediaAsset) -> None:
        if not asset.local_path:
            return
        path = PROJECT_ROOT / asset.local_path
        try:
            path.unlink(missing_ok=True)
        except OSError:
            logger.warning("Could not remove duplicate asset file %s", path)
