"""Generic, resumable, paginated downloader for wger-style DRF APIs.

Pagination is *discovered*, never hardcoded: the first page's `count`
field determines how many further pages exist for a given `limit`. Pages
are cached individually on disk, downloaded concurrently through a
`ThreadPoolExecutor`, and skipped on subsequent runs unless
`force_refresh` is set -- this is what makes `python run.py` idempotent
and interruption-resumable for the download stage.
"""

from __future__ import annotations

import datetime as dt
import threading
import time
from concurrent.futures import ThreadPoolExecutor, as_completed
from pathlib import Path
from typing import Any, Iterator

import requests
from tqdm import tqdm

from config import CONCURRENCY, HTTP
from pipeline.helpers import atomic_write_json, ensure_dir, read_json_if_exists
from pipeline.logger import get_logger

logger = get_logger("pipeline.downloader")


class DownloadError(RuntimeError):
    """Raised when a single page could not be downloaded after all retries."""


class PaginatedApiDownloader:
    """Downloads every page of a single limit/offset-paginated DRF endpoint."""

    def __init__(
        self,
        session: requests.Session,
        base_url: str,
        endpoint_path: str,
        destination_dir: Path,
        *,
        page_size: int = HTTP.page_size,
        workers: int = CONCURRENCY.download_workers,
        force_refresh: bool = False,
        extra_params: dict[str, Any] | None = None,
    ) -> None:
        self.session = session
        self.base_url = base_url.rstrip("/")
        self.endpoint_path = endpoint_path.strip("/")
        self.destination_dir = ensure_dir(destination_dir)
        self.page_size = page_size
        self.workers = max(1, workers)
        self.force_refresh = force_refresh
        self.extra_params = extra_params or {}
        self._manifest_path = self.destination_dir / "_manifest.json"
        self._manifest_lock = threading.Lock()

    @property
    def url(self) -> str:
        return f"{self.base_url}/{self.endpoint_path}/"

    def _page_path(self, offset: int) -> Path:
        return self.destination_dir / f"page_{offset:07d}.json"

    def _load_manifest(self) -> dict[str, Any]:
        return read_json_if_exists(self._manifest_path) or {}

    def _save_manifest(self, manifest: dict[str, Any]) -> None:
        with self._manifest_lock:
            atomic_write_json(self._manifest_path, manifest)

    def _fetch_page(self, offset: int) -> dict[str, Any]:
        params = {"limit": self.page_size, "offset": offset, "format": "json", **self.extra_params}
        last_exc: Exception | None = None
        for attempt in range(1, HTTP.max_retries + 1):
            try:
                response = self.session.get(
                    self.url,
                    params=params,
                    timeout=(HTTP.connect_timeout_seconds, HTTP.timeout_seconds),
                )
                if response.status_code == 429:
                    retry_after = float(response.headers.get("Retry-After", HTTP.backoff_factor * attempt))
                    logger.warning(
                        "Rate limited on %s (offset=%s); sleeping %.1fs", self.endpoint_path, offset, retry_after
                    )
                    time.sleep(retry_after)
                    continue
                response.raise_for_status()
                return response.json()
            except (requests.RequestException, ValueError) as exc:
                last_exc = exc
                sleep_for = HTTP.backoff_factor**attempt
                logger.warning(
                    "Attempt %s/%s failed for %s (offset=%s): %s -- retrying in %.1fs",
                    attempt,
                    HTTP.max_retries,
                    self.endpoint_path,
                    offset,
                    exc,
                    sleep_for,
                )
                time.sleep(sleep_for)
        raise DownloadError(
            f"Failed to download {self.endpoint_path} offset={offset} after {HTTP.max_retries} attempts"
        ) from last_exc

    def _download_offset(self, offset: int, progress: tqdm | None = None) -> dict[str, Any]:
        page_path = self._page_path(offset)
        if page_path.exists() and not self.force_refresh:
            payload = read_json_if_exists(page_path)
            if payload is not None:
                if progress is not None:
                    progress.update(1)
                return payload
        payload = self._fetch_page(offset)
        atomic_write_json(page_path, payload)
        if progress is not None:
            progress.update(1)
        return payload

    def download_all(self) -> dict[str, Any]:
        """Download every page for this endpoint. Returns the final manifest."""
        manifest = self._load_manifest()
        if manifest.get("completed") and not self.force_refresh:
            logger.info("Skipping %s: already fully downloaded (%s items)", self.endpoint_path, manifest.get("count"))
            return manifest

        try:
            first_payload = self._download_offset(0)
        except DownloadError as exc:
            logger.error("Giving up on %s: could not fetch the first page: %s", self.endpoint_path, exc)
            manifest = {
                "endpoint": self.endpoint_path,
                "count": 0,
                "page_size": self.page_size,
                "total_pages": 0,
                "failed_offsets": [0],
                "completed": False,
                "updated_at": dt.datetime.now(dt.timezone.utc).isoformat(),
            }
            self._save_manifest(manifest)
            return manifest

        count = int(first_payload.get("count", len(first_payload.get("results", []))))
        offsets = list(range(self.page_size, count, self.page_size)) if count > self.page_size else []

        logger.info("Downloading %s: %s items across %s page(s)", self.endpoint_path, count, len(offsets) + 1)

        failures: list[int] = []
        with tqdm(total=len(offsets) + 1, desc=f"GET {self.endpoint_path}", unit="page", leave=False) as progress:
            progress.update(1)  # page 0 already fetched above
            if offsets:
                with ThreadPoolExecutor(max_workers=self.workers) as executor:
                    future_to_offset = {
                        executor.submit(self._download_offset, offset, progress): offset for offset in offsets
                    }
                    for future in as_completed(future_to_offset):
                        offset = future_to_offset[future]
                        try:
                            future.result()
                        except DownloadError as exc:
                            logger.error("Giving up on %s offset=%s: %s", self.endpoint_path, offset, exc)
                            failures.append(offset)

        manifest = {
            "endpoint": self.endpoint_path,
            "count": count,
            "page_size": self.page_size,
            "total_pages": len(offsets) + 1,
            "failed_offsets": sorted(failures),
            "completed": not failures,
            "updated_at": dt.datetime.now(dt.timezone.utc).isoformat(),
        }
        self._save_manifest(manifest)
        if failures:
            logger.warning("%s: %s page(s) failed to download and were skipped", self.endpoint_path, len(failures))
        return manifest

    def iter_records(self) -> Iterator[dict[str, Any]]:
        """Yield every individual record across all cached pages, in order."""
        yield from iter_cached_records(self.destination_dir)


def iter_cached_records(directory: Path) -> Iterator[dict[str, Any]]:
    """Yield every record from cached `page_*.json` files in a directory, in order.

    Pure filesystem read, no network and no `PaginatedApiDownloader`
    instance required -- used by provider `normalize()` implementations
    that only need to replay already-downloaded pages.
    """
    for page_file in sorted(directory.glob("page_*.json")):
        payload = read_json_if_exists(page_file)
        if not payload:
            continue
        yield from payload.get("results", [])
