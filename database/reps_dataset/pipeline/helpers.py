"""Small, dependency-light utilities shared across the pipeline and providers.

Nothing here is pipeline-stage-specific; anything that reasonably belongs to
more than one module (downloader, cleaner, merger, validator, exporter,
media_downloader, providers) lives in this file to avoid duplication.
"""

from __future__ import annotations

import json
import re
import time
import unicodedata
from contextlib import contextmanager
from pathlib import Path
from typing import Any, Iterable, Iterator, TypeVar

import requests
from requests.adapters import HTTPAdapter

from config import HTTP

T = TypeVar("T")

_WHITESPACE_RE = re.compile(r"[ \t\f\v]+")
_MULTI_BLANK_LINES_RE = re.compile(r"\n{3,}")
_ACRONYM_OR_CODE_RE = re.compile(r"^[A-Z0-9][A-Z0-9./-]*$")


# --------------------------------------------------------------------------
# HTTP
# --------------------------------------------------------------------------


def build_http_session(token: str | None = None) -> requests.Session:
    """Build a `requests.Session` tuned for high-concurrency polite API access.

    Deliberately does *not* configure transport-level (urllib3) retries:
    every caller of this session (`pipeline.downloader`,
    `pipeline.media_downloader`) already implements its own explicit,
    logged retry/backoff loop with 429 `Retry-After` handling. Retrying at
    both layers at once would silently multiply the effective attempt
    count and hide failures from the caller's logging -- so retry policy
    has exactly one owner: the call site.
    """
    session = requests.Session()
    adapter = HTTPAdapter(pool_maxsize=32, pool_connections=32)
    session.mount("https://", adapter)
    session.mount("http://", adapter)

    headers = {"User-Agent": HTTP.user_agent, "Accept": "application/json"}
    if token:
        headers["Authorization"] = f"Token {token}"
    session.headers.update(headers)
    return session


# --------------------------------------------------------------------------
# Filesystem / JSON
# --------------------------------------------------------------------------


def ensure_dir(path: Path) -> Path:
    path.mkdir(parents=True, exist_ok=True)
    return path


def atomic_write_json(path: Path, data: Any, *, indent: int = 2, sort_keys: bool = False) -> None:
    """Write JSON atomically: serialize to a temp file, then replace.

    Prevents readers from ever observing a half-written file, which matters
    for resume/idempotency logic that inspects previously written output.
    """
    ensure_dir(path.parent)
    tmp_path = path.with_suffix(path.suffix + ".tmp")
    with tmp_path.open("w", encoding="utf-8") as handle:
        json.dump(data, handle, ensure_ascii=False, indent=indent, sort_keys=sort_keys)
    tmp_path.replace(path)


def read_json(path: Path) -> Any:
    with path.open("r", encoding="utf-8") as handle:
        return json.load(handle)


def read_json_if_exists(path: Path) -> Any | None:
    if not path.exists():
        return None
    try:
        return read_json(path)
    except (json.JSONDecodeError, OSError):
        return None


# --------------------------------------------------------------------------
# Text normalization
# --------------------------------------------------------------------------


def normalize_unicode(text: str) -> str:
    """Normalize to NFC and strip surrounding whitespace."""
    return unicodedata.normalize("NFC", text).strip()


def normalize_whitespace(text: str) -> str:
    """Collapse runs of horizontal whitespace and excess blank lines.

    Deliberately leaves single newlines and HTML markup untouched, since
    exercise descriptions are legitimately rich HTML content.
    """
    collapsed = _WHITESPACE_RE.sub(" ", text)
    collapsed = _MULTI_BLANK_LINES_RE.sub("\n\n", collapsed)
    return collapsed.strip()


def clean_text(text: str | None) -> str:
    if not text:
        return ""
    return normalize_whitespace(normalize_unicode(text))


def normalize_title_case(text: str | None) -> str:
    """Title-case a name while preserving acronyms/codes (e.g. "EZ-Bar", "T-Bar Row").

    wger exercise names are usually already well-capitalized; this only
    fixes clearly lower/upper-cased outliers instead of blindly calling
    ``str.title()``, which would mangle acronyms and hyphenated words.
    """
    text = clean_text(text)
    if not text:
        return text
    if text != text.lower() and text != text.upper():
        # Mixed case already (e.g. "Step Jack", "T-Bar Row") -> leave as-is.
        return text

    words = text.split(" ")
    normalized_words = []
    for word in words:
        if _ACRONYM_OR_CODE_RE.match(word) and len(word) <= 5:
            normalized_words.append(word)
            continue
        parts = re.split(r"([-'])", word)
        normalized_words.append("".join(p if p in ("-", "'") else p.capitalize() for p in parts))
    return " ".join(normalized_words)


# --------------------------------------------------------------------------
# Collections
# --------------------------------------------------------------------------


def dedupe_preserve_order(items: Iterable[T]) -> list[T]:
    seen: set[T] = set()
    result: list[T] = []
    for item in items:
        if item not in seen:
            seen.add(item)
            result.append(item)
    return result


def dedupe_case_insensitive(items: Iterable[str]) -> list[str]:
    """Dedupe strings case-insensitively, keeping the first-seen casing."""
    seen: set[str] = set()
    result: list[str] = []
    for item in items:
        key = item.lower()
        if key not in seen:
            seen.add(key)
            result.append(item)
    return result


def chunked(items: list[T], size: int) -> Iterator[list[T]]:
    for start in range(0, len(items), size):
        yield items[start : start + size]


def safe_get(data: dict, *keys: str, default: Any = None) -> Any:
    current: Any = data
    for key in keys:
        if not isinstance(current, dict) or key not in current:
            return default
        current = current[key]
    return current


# --------------------------------------------------------------------------
# Misc
# --------------------------------------------------------------------------


def format_bytes(size_bytes: float) -> str:
    for unit in ("B", "KB", "MB", "GB", "TB"):
        if size_bytes < 1024.0:
            return f"{size_bytes:.2f} {unit}"
        size_bytes /= 1024.0
    return f"{size_bytes:.2f} PB"


def sanitize_filename(name: str) -> str:
    return re.sub(r"[^A-Za-z0-9._-]", "_", name)


def resolve_asset_url(local_path: str | None, base_url: str | None) -> str | None:
    """Turn a stored `local_path` (e.g. "assets/exercises/1000/main.png")
    into whatever URL a client should actually fetch: the CDN/object-storage
    URL once `base_url` (`config.CLOUD_ASSETS_BASE_URL`) is configured, or
    the relative path unchanged for local/dev use. This is the one place
    that decision is made -- the API layer and `db/sqlite_export.py` should
    both call this rather than each hand-rolling the same string join, so
    the cutover to cloud storage is a one-line config change, not a
    find-and-replace across every consumer.
    """
    if not local_path:
        return None
    if not base_url:
        return local_path
    return f"{base_url.rstrip('/')}/{local_path.lstrip('/')}"


def svg_dimensions(path: Path) -> tuple[int, int] | None:
    """Best-effort (width, height) for an SVG file: prefer explicit
    width/height attributes, fall back to the viewBox's 3rd/4th numbers.
    Returns None if the file is missing, malformed, or has neither.
    Shared by `pipeline/media_downloader.py` and `pipeline/svg_assets.py`.
    """
    import xml.etree.ElementTree as ET

    if not path.exists():
        return None
    try:
        root = ET.parse(path).getroot()
    except ET.ParseError:
        return None

    def _to_int(value: str | None) -> int | None:
        if not value:
            return None
        match = re.match(r"[\d.]+", value)
        return int(float(match.group())) if match else None

    width = _to_int(root.get("width"))
    height = _to_int(root.get("height"))
    if width and height:
        return width, height

    view_box = root.get("viewBox")
    if view_box:
        parts = view_box.replace(",", " ").split()
        if len(parts) == 4:
            try:
                return int(float(parts[2])), int(float(parts[3]))
            except ValueError:
                return None
    return None


@contextmanager
def timer() -> Iterator["_ElapsedTimer"]:
    elapsed = _ElapsedTimer()
    start = time.perf_counter()
    try:
        yield elapsed
    finally:
        elapsed.seconds = time.perf_counter() - start


class _ElapsedTimer:
    seconds: float = 0.0

    def __str__(self) -> str:  # pragma: no cover - trivial formatting
        return f"{self.seconds:.2f}s"
