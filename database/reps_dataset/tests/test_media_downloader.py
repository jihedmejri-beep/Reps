"""Tests for the pure, network-free logic in `pipeline/media_downloader.py`:
job/filename assignment, extension resolution, and checksum-based
duplicate removal. The actual HTTP download path is exercised by the live
integration run documented in `ENGINEERING_REPORT.md` (438/438 real
assets downloaded, 0 failures), not re-mocked here.
"""

from __future__ import annotations

from pipeline.media_downloader import MediaDownloader
from pipeline.models import MediaAsset


def _asset(uuid: str, is_main: bool = False, checksum: str | None = None) -> MediaAsset:
    asset = MediaAsset(uuid=uuid, remote_url=f"https://example.com/{uuid}.png", is_main=is_main)
    asset.checksum_sha256 = checksum
    return asset


def test_build_jobs_assigns_main_then_sequential_image_numbers(sample_merged_exercise):
    sample_merged_exercise.images = [_asset("a", is_main=False), _asset("b", is_main=True), _asset("c", is_main=False)]
    jobs = MediaDownloader._build_jobs(sample_merged_exercise)
    basenames = {job.asset.uuid: job.basename for job in jobs if job.kind == "image"}
    assert basenames == {"b": "main", "a": "image_2", "c": "image_3"}


def test_build_jobs_promotes_first_image_to_main_when_none_flagged(sample_merged_exercise):
    sample_merged_exercise.images = [_asset("x"), _asset("y")]
    jobs = MediaDownloader._build_jobs(sample_merged_exercise)
    basenames = {job.asset.uuid: job.basename for job in jobs if job.kind == "image"}
    assert basenames == {"x": "main", "y": "image_2"}
    # is_main itself is never rewritten -- filename convention only.
    assert sample_merged_exercise.images[0].is_main is False


def test_build_jobs_numbers_videos_from_one(sample_merged_exercise):
    sample_merged_exercise.images = []
    sample_merged_exercise.videos = [_asset("v1"), _asset("v2")]
    jobs = MediaDownloader._build_jobs(sample_merged_exercise)
    basenames = {job.asset.uuid: job.basename for job in jobs}
    assert basenames == {"v1": "video_1", "v2": "video_2"}


def test_resolve_extension_prefers_url_suffix_when_known():
    assert MediaDownloader._resolve_extension(".png", "image/jpeg") == ".png"


def test_resolve_extension_falls_back_to_content_type():
    assert MediaDownloader._resolve_extension("", "image/webp") == ".webp"


def test_resolve_extension_normalizes_jpeg_to_jpg():
    assert MediaDownloader._resolve_extension(".jpeg", "") == ".jpg"


def test_dedupe_list_keeps_first_and_removes_checksum_duplicates(monkeypatch):
    downloader = MediaDownloader.__new__(MediaDownloader)  # bypass __init__ (no network session needed)
    monkeypatch.setattr(MediaDownloader, "_delete_asset_file", staticmethod(lambda asset: None))

    main = _asset("main", is_main=True, checksum="abc123")
    dup = _asset("dup", checksum="abc123")
    unique = _asset("unique", checksum="def456")

    known_duplicates: dict[str, list[str]] = {}
    kept, removed = downloader._dedupe_list([main, dup, unique], "1000", known_duplicates)

    assert [a.uuid for a in kept] == ["main", "unique"]
    assert removed == 1
    assert known_duplicates == {"1000": ["dup"]}


def test_dedupe_list_ignores_assets_without_checksum():
    downloader = MediaDownloader.__new__(MediaDownloader)
    a = _asset("a", checksum=None)
    b = _asset("b", checksum=None)
    kept, removed = downloader._dedupe_list([a, b], "1000", {})
    assert len(kept) == 2
    assert removed == 0


def test_exclude_known_duplicates_drops_previously_removed_assets(sample_merged_exercise):
    sample_merged_exercise.id = "1000"
    sample_merged_exercise.images = [_asset("keep"), _asset("drop")]
    sample_merged_exercise.videos = []

    excluded = MediaDownloader._exclude_known_duplicates([sample_merged_exercise], {"1000": ["drop"]})

    assert excluded == 1
    assert [a.uuid for a in sample_merged_exercise.images] == ["keep"]
