"""Tests for the SVG dimension parsing (`pipeline/helpers.py::svg_dimensions`,
shared by `media_downloader.py` and `svg_assets.py`) and the alignment
validation logic in `pipeline/svg_assets.py::SvgAssetDownloader._validate`.
Network downloads themselves were verified live (30/30 muscle SVGs + both
body diagrams, 0 alignment issues -- see `ENGINEERING_REPORT.md`).
"""

from __future__ import annotations

from pathlib import Path

from pipeline.helpers import svg_dimensions
from pipeline.svg_assets import SvgAssetDownloader


def _write_svg(path: Path, width: int, height: int) -> None:
    path.write_text(f'<svg xmlns="http://www.w3.org/2000/svg" width="{width}" height="{height}"></svg>', encoding="utf-8")


def test_svg_dimensions_from_width_height_attributes(tmp_path):
    path = tmp_path / "a.svg"
    _write_svg(path, 200, 369)
    assert svg_dimensions(path) == (200, 369)


def test_svg_dimensions_falls_back_to_viewbox(tmp_path):
    path = tmp_path / "b.svg"
    path.write_text('<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 150 300"></svg>', encoding="utf-8")
    assert svg_dimensions(path) == (150, 300)


def test_svg_dimensions_returns_none_for_malformed_svg(tmp_path):
    path = tmp_path / "c.svg"
    path.write_text("<svg><unclosed>", encoding="utf-8")
    assert svg_dimensions(path) is None


def test_svg_dimensions_returns_none_for_missing_file(tmp_path):
    assert svg_dimensions(tmp_path / "does_not_exist.svg") is None


def test_validate_flags_no_issues_when_dimensions_match_within_tolerance(tmp_path):
    # `_validate` resolves stored paths as `PROJECT_ROOT / rel_path`; pathlib's
    # `/` returns the right-hand side unchanged when it's already absolute,
    # so passing absolute tmp_path paths directly resolves correctly here.
    # A missing (None) variant path is itself flagged as `download_failed`,
    # so both main/secondary are given real files here to isolate the
    # dimension-alignment check this test targets.
    body_front = tmp_path / "front.svg"
    _write_svg(body_front, 200, 369)
    muscle_main = tmp_path / "muscle-2-main.svg"
    _write_svg(muscle_main, 200, 362)  # within the 15px tolerance, matches real wger data
    muscle_secondary = tmp_path / "muscle-2-secondary.svg"
    _write_svg(muscle_secondary, 200, 369)

    downloader = SvgAssetDownloader.__new__(SvgAssetDownloader)
    report = downloader._validate(
        {"2": {"main": str(muscle_main), "secondary": str(muscle_secondary)}},
        {"front": str(body_front)},
    )
    assert report["alignmentIssueCount"] == 0
    assert report["muscleSvgCount"] == 2


def test_validate_flags_width_mismatch(tmp_path):
    body_front = tmp_path / "front.svg"
    _write_svg(body_front, 200, 369)
    muscle_main = tmp_path / "muscle-9-main.svg"
    _write_svg(muscle_main, 100, 369)  # wrong width
    muscle_secondary = tmp_path / "muscle-9-secondary.svg"
    _write_svg(muscle_secondary, 200, 369)

    downloader = SvgAssetDownloader.__new__(SvgAssetDownloader)
    report = downloader._validate(
        {"9": {"main": str(muscle_main), "secondary": str(muscle_secondary)}},
        {"front": str(body_front)},
    )
    assert report["alignmentIssueCount"] == 1
    assert report["issues"][0]["issue"] == "dimension_mismatch"


def test_validate_flags_download_failed_when_variant_missing(tmp_path):
    body_front = tmp_path / "front.svg"
    _write_svg(body_front, 200, 369)

    downloader = SvgAssetDownloader.__new__(SvgAssetDownloader)
    report = downloader._validate(
        {"9": {"main": None, "secondary": None}},
        {"front": str(body_front)},
    )
    assert report["alignmentIssueCount"] == 2
    assert all(issue["issue"] == "download_failed" for issue in report["issues"])
