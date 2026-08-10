"""Central configuration for the REPS exercise dataset pipeline.

Every path, endpoint, and tunable constant used across the pipeline and
provider modules is defined here so behaviour can be changed in a single
place. Nothing outside this module should hardcode a path or an endpoint
name.
"""

from __future__ import annotations

import os
from dataclasses import dataclass
from pathlib import Path

# --------------------------------------------------------------------------
# Filesystem layout
# --------------------------------------------------------------------------

PROJECT_ROOT: Path = Path(__file__).resolve().parent
DATA_DIR: Path = PROJECT_ROOT / "data"

RAW_DIR: Path = DATA_DIR / "raw"
CLEANED_DIR: Path = DATA_DIR / "cleaned"
MERGED_DIR: Path = DATA_DIR / "merged"
OUTPUT_DIR: Path = DATA_DIR / "output"
MEDIA_DIR: Path = DATA_DIR / "media"
IMAGES_DIR: Path = MEDIA_DIR / "images"
VIDEOS_DIR: Path = MEDIA_DIR / "videos"
LOGS_DIR: Path = DATA_DIR / "logs"

# Production asset system (Epic 1). Distinct from `MEDIA_DIR` above, which is
# the legacy per-provider download cache keyed by remote uuid; `ASSETS_DIR`
# is the stable, exercise-scoped layout the mobile app and the database
# `localPath`/asset tables point at.
ASSETS_DIR: Path = PROJECT_ROOT / "assets"
EXERCISE_ASSETS_DIR: Path = ASSETS_DIR / "exercises"
MUSCLE_ASSETS_DIR: Path = ASSETS_DIR / "muscles"
MUSCLE_MAIN_SVG_DIR: Path = MUSCLE_ASSETS_DIR / "main"
MUSCLE_SECONDARY_SVG_DIR: Path = MUSCLE_ASSETS_DIR / "secondary"
BODY_ASSETS_DIR: Path = ASSETS_DIR / "body"

ALL_DATA_DIRS: tuple[Path, ...] = (
    RAW_DIR,
    CLEANED_DIR,
    MERGED_DIR,
    OUTPUT_DIR,
    IMAGES_DIR,
    VIDEOS_DIR,
    LOGS_DIR,
    EXERCISE_ASSETS_DIR,
    MUSCLE_MAIN_SVG_DIR,
    MUSCLE_SECONDARY_SVG_DIR,
    BODY_ASSETS_DIR,
)


def ensure_directories() -> None:
    """Create every directory the pipeline writes to, if missing."""
    for directory in ALL_DATA_DIRS:
        directory.mkdir(parents=True, exist_ok=True)


# --------------------------------------------------------------------------
# Output file names
# --------------------------------------------------------------------------

CLEANED_FILENAME = "exercises_cleaned.json"
MERGED_FILENAME = "exercises_merged.json"
EXPORT_FILENAME = "reps_exercises.json"
VALIDATION_REPORT_FILENAME = "validation_report.json"
STATISTICS_FILENAME = "statistics.json"

# --------------------------------------------------------------------------
# Language policy
# --------------------------------------------------------------------------

# ISO 639-1 codes, matching the wger `language.short_name` field.
KEPT_LANGUAGES: tuple[str, ...] = ("en", "fr", "ar")
DEFAULT_LANGUAGE: str = "en"

# --------------------------------------------------------------------------
# HTTP / networking
# --------------------------------------------------------------------------


@dataclass(frozen=True)
class HttpSettings:
    timeout_seconds: float = 30.0
    connect_timeout_seconds: float = 10.0
    max_retries: int = 6
    backoff_factor: float = 1.5
    retry_statuses: tuple[int, ...] = (429, 500, 502, 503, 504)
    page_size: int = 100
    user_agent: str = "REPS-ExerciseDatasetBuilder/1.0 (+https://reps.app; contact: data@reps.app)"
    # Extra courtesy delay (seconds) applied between successive requests to the
    # same host, on top of retry/backoff handling. 0 disables it.
    min_request_interval_seconds: float = 0.0


@dataclass(frozen=True)
class ConcurrencySettings:
    download_workers: int = 8
    media_workers: int = 12


HTTP = HttpSettings()
CONCURRENCY = ConcurrencySettings()

# --------------------------------------------------------------------------
# WGER provider configuration
# --------------------------------------------------------------------------

WGER_BASE_URL: str = os.environ.get("REPS_WGER_BASE_URL", "https://wger.de/api/v2").rstrip("/")

# Optional. All endpoints consumed by this pipeline are public read-only
# resources, so a token is not required, but wger honours one (higher rate
# limits) if provided via the REPS_WGER_API_TOKEN environment variable.
WGER_API_TOKEN: str | None = os.environ.get("REPS_WGER_API_TOKEN")

# Endpoint names as exposed by the wger API root (https://wger.de/api/v2/).
# `exerciseinfo` is the canonical, fully denormalized source for exercise
# records: a single record combines the base exercise, every kept
# translation, category, muscles, equipment, license and media in one
# object. The remaining endpoints are downloaded as supplementary raw
# datasets used for lookup tables, cross-validation and future flexibility.
# See README.md -> "Architecture / Canonical data source" for the rationale.
WGER_ENDPOINTS: dict[str, str] = {
    "exerciseinfo": "exerciseinfo",
    "exercise": "exercise",
    "exercise-translation": "exercise-translation",
    "exercisecategory": "exercisecategory",
    "muscle": "muscle",
    "equipment": "equipment",
    "language": "language",
    "license": "license",
    "exerciseimage": "exerciseimage",
    "video": "video",
    "exercisealias": "exercisealias",
    "exercisecomment": "exercisecomment",
}

CANONICAL_ENDPOINT: str = "exerciseinfo"

# Endpoints that are small, non-paginated-in-practice lookup/reference
# tables. They are still downloaded through the same paginated downloader
# for consistency, but the pipeline treats them as in-memory lookups.
LOOKUP_ENDPOINTS: tuple[str, ...] = (
    "exercisecategory",
    "muscle",
    "equipment",
    "language",
    "license",
)

# --------------------------------------------------------------------------
# Media download configuration
# --------------------------------------------------------------------------

MEDIA_ALLOWED_IMAGE_EXTENSIONS: tuple[str, ...] = (".png", ".jpg", ".jpeg", ".webp", ".gif", ".svg")
MEDIA_ALLOWED_VIDEO_EXTENSIONS: tuple[str, ...] = (".mp4", ".mov", ".webm", ".avi", ".mkv")
MEDIA_DOWNLOAD_TIMEOUT_SECONDS: float = 60.0
MEDIA_MAX_RETRIES: int = 4

# Product decision: videos are dropped entirely (they dominated asset disk
# usage -- 3+ GB across 78 files -- for a feature not currently used).
# Disabling this is the single point of control: `pipeline/cleaner.py`
# strips `videos` off every exercise right after download, so nothing
# downstream (merger, media_downloader, validator, exporter, db/seed.py,
# db/sqlite_export.py) ever sees a video again, with no per-module changes
# needed. The `exercise_videos` table/dataclass fields are intentionally
# kept (not dropped) so this is a reversible policy switch, not a schema
# redesign -- see docs/database.md.
VIDEOS_ENABLED: bool = False


@dataclass(frozen=True)
class AssetSettings:
    """Tunables for the production asset pipeline (`pipeline/media_downloader.py`,
    `pipeline/svg_assets.py`) -- separate from the legacy `MEDIA_*` constants
    above, which only govern the raw provider media cache."""

    max_retries: int = 5
    # Retries specifically for HTTP 429 are uncapped by `max_retries` and
    # instead bounded by this wall-clock budget, honoring `Retry-After`.
    rate_limit_retry_budget_seconds: float = 120.0
    default_rate_limit_wait_seconds: float = 5.0
    # `requests`' single timeout value applies to gaps between reads on a
    # streamed response too, not just connect -- some wger exercise videos
    # are 300MB+, and under concurrent download load a 60s gap between
    # chunks was observed often enough to matter (see ENGINEERING_REPORT.md).
    request_timeout_seconds: float = 180.0
    content_type_image_prefixes: tuple[str, ...] = ("image/",)
    content_type_video_prefixes: tuple[str, ...] = ("video/",)
    content_type_svg_types: tuple[str, ...] = ("image/svg+xml", "text/xml", "application/xml")
    # Maps a normalized Content-Type to the extension used for the file
    # written to disk, when the URL itself doesn't already carry one.
    content_type_extensions: dict[str, str] = None  # set in __post_init__


def _asset_settings_with_extension_map() -> "AssetSettings":
    settings = AssetSettings()
    object.__setattr__(
        settings,
        "content_type_extensions",
        {
            "image/png": ".png",
            "image/jpeg": ".jpg",
            "image/webp": ".webp",
            "image/gif": ".gif",
            "image/svg+xml": ".svg",
            "video/mp4": ".mp4",
            "video/webm": ".webm",
            "video/quicktime": ".mov",
            "video/x-msvideo": ".avi",
            "video/x-matroska": ".mkv",
        },
    )
    return settings


ASSET = _asset_settings_with_extension_map()

# --------------------------------------------------------------------------
# Database (Epic 3). Same env-var-override pattern as `WGER_API_TOKEN`:
# nothing here is hardcoded to a real credential, and the disposable local
# Docker Postgres used for development/verification is just the default.
# --------------------------------------------------------------------------

DATABASE_URL: str = os.environ.get(
    "REPS_DATABASE_URL", "postgresql://reps:reps@localhost:5433/reps"
)

DB_DIR: Path = PROJECT_ROOT / "db"
MIGRATIONS_DIR: Path = DB_DIR / "migrations"
SCHEMA_SQL_PATH: Path = DB_DIR / "schema.sql"
SQLITE_EXPORT_PATH: Path = OUTPUT_DIR / "reps_offline.sqlite"

# --------------------------------------------------------------------------
# Cloud asset storage (images are moving off local/app-server disk onto
# object storage + CDN; see docs/assets.md -> "Cloud migration"). Every
# `local_path` stored in the DB/exports (e.g. "assets/exercises/1000/
# main.png") is already a relative path designed to double as an object
# storage key unchanged -- setting this env var is the entire cutover, no
# data migration of the paths themselves is needed.
# --------------------------------------------------------------------------

CLOUD_ASSETS_BASE_URL: str | None = os.environ.get("REPS_CLOUD_ASSETS_BASE_URL")
CLOUD_UPLOAD_MANIFEST_PATH: Path = OUTPUT_DIR / "cloud_upload_manifest.json"

# --------------------------------------------------------------------------
# Providers enabled for a default `python run.py` invocation.
# --------------------------------------------------------------------------

DEFAULT_ENABLED_PROVIDERS: tuple[str, ...] = ("wger",)
