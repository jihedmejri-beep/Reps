"""Tests for pipeline/downloader.py: pagination discovery, caching/resume,
retries and 429 handling -- all against a scripted fake session, never a
real network call.
"""

from __future__ import annotations

import requests

from config import HttpSettings
from pipeline.downloader import PaginatedApiDownloader


class FakeResponse:
    def __init__(self, status_code=200, json_data=None, headers=None):
        self.status_code = status_code
        self._json_data = json_data if json_data is not None else {}
        self.headers = headers or {}

    def raise_for_status(self):
        if self.status_code >= 400 and self.status_code != 429:
            raise requests.HTTPError(f"HTTP {self.status_code}")

    def json(self):
        return self._json_data


class FakeSession:
    """Replays a scripted sequence of responses/exceptions per offset."""

    def __init__(self, script: dict):
        self.script = {offset: list(events) for offset, events in script.items()}
        self.calls: list[int] = []

    def get(self, url, params=None, timeout=None):
        offset = params["offset"]
        self.calls.append(offset)
        events = self.script.get(offset)
        if not events:
            raise AssertionError(f"No scripted response left for offset={offset}")
        event = events.pop(0)
        if isinstance(event, Exception):
            raise event
        return event


def _page(count, results):
    return FakeResponse(200, {"count": count, "next": None, "previous": None, "results": results})


def test_download_all_discovers_pagination_and_caches_pages(tmp_path):
    session = FakeSession(
        {
            0: [_page(25, [{"id": i} for i in range(10)])],
            10: [_page(25, [{"id": i} for i in range(10, 20)])],
            20: [_page(25, [{"id": i} for i in range(20, 25)])],
        }
    )
    downloader = PaginatedApiDownloader(
        session, "https://example.test/api/v2", "exercise", tmp_path, page_size=10, workers=2
    )

    manifest = downloader.download_all()

    assert manifest["completed"] is True
    assert manifest["count"] == 25
    assert manifest["total_pages"] == 3
    assert len(list(tmp_path.glob("page_*.json"))) == 3
    assert len(list(downloader.iter_records())) == 25
    assert sorted(session.calls) == [0, 10, 20]


def test_rerun_skips_already_cached_pages(tmp_path):
    session = FakeSession({0: [_page(5, [{"id": 1}])]})
    PaginatedApiDownloader(session, "https://example.test/api/v2", "muscle", tmp_path, page_size=100).download_all()
    assert session.calls == [0]

    session2 = FakeSession({})  # any .get() call here would raise AssertionError
    downloader2 = PaginatedApiDownloader(session2, "https://example.test/api/v2", "muscle", tmp_path, page_size=100)

    manifest = downloader2.download_all()

    assert session2.calls == []
    assert manifest["completed"] is True


def test_force_refresh_redownloads_even_when_cached(tmp_path):
    session = FakeSession({0: [_page(1, [{"id": 1}])]})
    PaginatedApiDownloader(session, "https://example.test/api/v2", "language", tmp_path, page_size=100).download_all()

    session2 = FakeSession({0: [_page(1, [{"id": 2}])]})
    downloader2 = PaginatedApiDownloader(
        session2, "https://example.test/api/v2", "language", tmp_path, page_size=100, force_refresh=True
    )
    downloader2.download_all()

    assert session2.calls == [0]
    [record] = list(downloader2.iter_records())
    assert record["id"] == 2


def test_retries_transient_connection_errors_then_succeeds(tmp_path, monkeypatch):
    monkeypatch.setattr("pipeline.downloader.time.sleep", lambda _seconds: None)
    session = FakeSession({0: [requests.ConnectionError("boom"), _page(1, [{"id": 1}])]})
    downloader = PaginatedApiDownloader(session, "https://example.test/api/v2", "equipment", tmp_path, page_size=100)

    manifest = downloader.download_all()

    assert manifest["completed"] is True
    assert len(session.calls) == 2


def test_honors_429_retry_after_header(tmp_path, monkeypatch):
    sleeps: list[float] = []
    monkeypatch.setattr("pipeline.downloader.time.sleep", lambda seconds: sleeps.append(seconds))
    session = FakeSession({0: [FakeResponse(429, headers={"Retry-After": "2"}), _page(1, [{"id": 1}])]})
    downloader = PaginatedApiDownloader(session, "https://example.test/api/v2", "video", tmp_path, page_size=100)

    manifest = downloader.download_all()

    assert manifest["completed"] is True
    assert 2.0 in sleeps


def test_giving_up_after_max_retries_never_raises_and_is_recorded(tmp_path, monkeypatch):
    monkeypatch.setattr("pipeline.downloader.time.sleep", lambda _seconds: None)
    fast_http = HttpSettings(max_retries=2, backoff_factor=0.01, timeout_seconds=1.0, connect_timeout_seconds=1.0)
    monkeypatch.setattr("pipeline.downloader.HTTP", fast_http)
    session = FakeSession({0: [requests.ConnectionError("still down"), requests.ConnectionError("still down")]})
    downloader = PaginatedApiDownloader(session, "https://example.test/api/v2", "license", tmp_path, page_size=100)

    manifest = downloader.download_all()  # must not raise

    assert manifest["completed"] is False
    assert manifest["failed_offsets"] == [0]


def test_offset_page_failure_is_recorded_without_aborting_other_pages(tmp_path, monkeypatch):
    monkeypatch.setattr("pipeline.downloader.time.sleep", lambda _seconds: None)
    fast_http = HttpSettings(max_retries=1, backoff_factor=0.01, timeout_seconds=1.0, connect_timeout_seconds=1.0)
    monkeypatch.setattr("pipeline.downloader.HTTP", fast_http)
    session = FakeSession(
        {
            0: [_page(30, [{"id": i} for i in range(10)])],
            10: [requests.ConnectionError("down")],
            20: [_page(30, [{"id": i} for i in range(20, 30)])],
        }
    )
    downloader = PaginatedApiDownloader(session, "https://example.test/api/v2", "exercisecomment", tmp_path, page_size=10)

    manifest = downloader.download_all()

    assert manifest["completed"] is False
    assert manifest["failed_offsets"] == [10]
    assert len(list(tmp_path.glob("page_*.json"))) == 2  # offsets 0 and 20 still cached
