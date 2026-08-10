"""Tests for the pure keyword-building logic in
`pipeline/search_indexer.py::build_keywords`. Normalization itself
(accent/diacritic stripping) lives in SQL (`db/migrations/0002_search.sql`)
by design and was verified live against Postgres -- see
`ENGINEERING_REPORT.md` for the exact English/French/Arabic queries run.
"""

from __future__ import annotations

from pipeline.search_indexer import build_keywords


def test_build_keywords_combines_and_dedupes_aliases_equipment_muscles():
    entry = {
        "aliases": ["Side Step Jack", "Low Impact Jumping Jack"],
        "equipment": ["Barbell", "Barbell"],  # duplicate on purpose
        "muscles": ["Quadriceps femoris", "Quads"],
        "category": "Legs",
    }
    keywords = build_keywords(entry)
    assert keywords == ["Side Step Jack", "Low Impact Jumping Jack", "Barbell", "Quadriceps femoris", "Quads", "Legs"]


def test_build_keywords_handles_missing_category():
    entry = {"aliases": [], "equipment": ["Dumbbell"], "muscles": [], "category": None}
    assert build_keywords(entry) == ["Dumbbell"]


def test_build_keywords_preserves_first_occurrence_order_when_deduping():
    entry = {"aliases": ["B", "A", "B"], "equipment": [], "muscles": [], "category": None}
    assert build_keywords(entry) == ["B", "A"]
