"""Tests for pipeline/exporter.py: final schema shape and statistics output."""

from __future__ import annotations

import dataclasses
import json

from pipeline.exporter import Exporter
from pipeline.models import PipelineStatistics

_EXPECTED_KEYS = {
    "id",
    "uuid",
    "variationGroup",
    "name",
    "description",
    "category",
    "equipment",
    "primaryMuscles",
    "secondaryMuscles",
    "images",
    "videos",
    "aliases",
    "license",
    "source",
}


def test_export_writes_exact_required_schema(sample_merged_exercise, tmp_path, monkeypatch):
    monkeypatch.setattr("pipeline.exporter.OUTPUT_DIR", tmp_path)

    path = Exporter().export([sample_merged_exercise])

    payload = json.loads(path.read_text(encoding="utf-8"))
    assert len(payload) == 1
    exercise = payload[0]
    assert set(exercise.keys()) == _EXPECTED_KEYS
    assert set(exercise["name"].keys()) == {"en", "fr", "ar"}
    assert set(exercise["description"].keys()) == {"en", "fr", "ar"}
    assert set(exercise["license"].keys()) == {"name", "url"}
    assert exercise["source"] == "wger"


def test_export_preserves_utf8_for_arabic_text(sample_merged_exercise, tmp_path, monkeypatch):
    monkeypatch.setattr("pipeline.exporter.OUTPUT_DIR", tmp_path)

    path = Exporter().export([sample_merged_exercise])

    raw_text = path.read_text(encoding="utf-8")
    assert "ستيب جاك" in raw_text  # not escaped to \uXXXX, human-readable in the file


def test_export_sorts_exercises_by_id(sample_merged_exercise, tmp_path, monkeypatch):
    monkeypatch.setattr("pipeline.exporter.OUTPUT_DIR", tmp_path)
    first = dataclasses.replace(sample_merged_exercise, id="b-id", uuid="bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb")
    second = dataclasses.replace(sample_merged_exercise, id="a-id", uuid="aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa")

    path = Exporter().export([first, second])

    payload = json.loads(path.read_text(encoding="utf-8"))
    assert [item["id"] for item in payload] == ["a-id", "b-id"]


def test_write_statistics_records_final_database_size(sample_merged_exercise, tmp_path, monkeypatch):
    monkeypatch.setattr("pipeline.exporter.OUTPUT_DIR", tmp_path)
    exporter = Exporter()
    exporter.export([sample_merged_exercise])
    stats = PipelineStatistics(exercises_exported=1)

    stats_path = exporter.write_statistics(stats)

    assert stats.final_database_bytes > 0
    payload = json.loads(stats_path.read_text(encoding="utf-8"))
    assert payload["finalDatabaseBytes"] == stats.final_database_bytes
    assert payload["exercisesExported"] == 1
