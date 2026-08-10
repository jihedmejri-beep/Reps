"""Tests for pipeline/validator.py.

Validation must never raise; every assertion here checks the *content* of
the returned issue list instead.
"""

from __future__ import annotations

import dataclasses
import json

from pipeline.models import LocalizedText, PipelineStatistics
from pipeline.validator import Validator


def test_healthy_exercise_produces_no_errors(sample_merged_exercise, tmp_path, monkeypatch):
    media_dir = tmp_path / "data" / "media" / "images"
    media_dir.mkdir(parents=True)
    (media_dir / "74041371-1019-4f89-9ebe-cec792484a46.png").write_bytes(b"fake-image-bytes")
    monkeypatch.setattr("pipeline.validator.PROJECT_ROOT", tmp_path)

    stats = PipelineStatistics()
    issues = Validator().validate([sample_merged_exercise], stats)

    assert [i for i in issues if i.severity == "error"] == []
    assert stats.validation_errors == 0


def test_detects_duplicate_uuid(sample_merged_exercise):
    duplicate = dataclasses.replace(sample_merged_exercise, id="another-id")
    stats = PipelineStatistics()

    issues = Validator().validate([sample_merged_exercise, duplicate], stats)

    assert any(i.code == "duplicate_uuid" for i in issues)


def test_detects_missing_name(sample_merged_exercise):
    broken = dataclasses.replace(sample_merged_exercise, name=LocalizedText())
    stats = PipelineStatistics()

    issues = Validator().validate([broken], stats)

    assert any(i.code == "missing_name" and i.severity == "error" for i in issues)


def test_detects_missing_category(sample_merged_exercise):
    broken = dataclasses.replace(sample_merged_exercise, category="")
    stats = PipelineStatistics()

    issues = Validator().validate([broken], stats)

    assert any(i.code == "missing_category" and i.severity == "error" for i in issues)


def test_detects_broken_media_when_file_missing(sample_merged_exercise, tmp_path, monkeypatch):
    monkeypatch.setattr("pipeline.validator.PROJECT_ROOT", tmp_path)  # empty dir -> local_path never exists
    stats = PipelineStatistics()

    issues = Validator().validate([sample_merged_exercise], stats)

    assert any(i.code == "broken_media" for i in issues)
    assert stats.validation_warnings > 0


def test_detects_invalid_uuid_format(sample_merged_exercise):
    broken = dataclasses.replace(sample_merged_exercise, uuid="not-a-uuid")
    stats = PipelineStatistics()

    issues = Validator().validate([broken], stats)

    assert any(i.code == "invalid_uuid" and i.severity == "error" for i in issues)


def test_write_report_persists_valid_json(sample_merged_exercise, tmp_path, monkeypatch):
    monkeypatch.setattr("pipeline.validator.PROJECT_ROOT", tmp_path)
    monkeypatch.setattr("pipeline.validator.OUTPUT_DIR", tmp_path)
    validator = Validator()
    stats = PipelineStatistics()
    issues = validator.validate([sample_merged_exercise], stats)

    report_path = validator.write_report(issues, stats)

    assert report_path.exists()
    payload = json.loads(report_path.read_text(encoding="utf-8"))
    assert payload["totalIssues"] == len(issues)
    assert payload["errors"] == stats.validation_errors
    assert payload["warnings"] == stats.validation_warnings
