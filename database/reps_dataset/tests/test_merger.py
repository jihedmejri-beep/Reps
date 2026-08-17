"""Tests for pipeline/merger.py: merging per-language translations."""

from __future__ import annotations

from pipeline.cleaner import Cleaner
from pipeline.merger import Merger
from pipeline.models import PipelineStatistics, RawExerciseRecord, TranslationRecord


def test_merges_translations_into_localized_dicts(sample_raw_exercise):
    stats = PipelineStatistics()
    [cleaned] = Cleaner(kept_languages=("en", "fr", "ar")).clean([sample_raw_exercise], stats)

    [merged] = Merger().merge([cleaned], stats)

    assert merged.name.en == "Step Jack"
    assert merged.name.fr == "Step Jack"
    assert merged.name.ar == ""
    # Descriptions are legitimately rich HTML content and are left intact by the cleaner.
    assert merged.description.en == "<p>English description.</p>"
    assert merged.description.fr == "<p>Description en francais.</p>"


def test_flattens_and_dedupes_aliases_across_languages(sample_raw_exercise):
    stats = PipelineStatistics()
    [cleaned] = Cleaner().clean([sample_raw_exercise], stats)

    [merged] = Merger().merge([cleaned], stats)

    assert merged.aliases == sorted(["Side Step Jack", "Low Impact Jumping Jack"])


def test_carries_variation_group_through_unchanged():
    record = RawExerciseRecord(
        id="9",
        uuid="99999999-9999-9999-9999-999999999999",
        source="wger",
        category="Chest",
        variation_group="a30b1f92-7b73-477e-abb0-e91993c5fb05",
        translations=[TranslationRecord(language_code="en", name="Bench Press", description="Desc")],
    )
    stats = PipelineStatistics()
    [cleaned] = Cleaner().clean([record], stats)

    [merged] = Merger().merge([cleaned], stats)

    assert merged.variation_group == "a30b1f92-7b73-477e-abb0-e91993c5fb05"


def test_preserves_equipment_and_muscle_references(sample_raw_exercise):
    stats = PipelineStatistics()
    [cleaned] = Cleaner().clean([sample_raw_exercise], stats)

    [merged] = Merger().merge([cleaned], stats)

    assert [e.id for e in merged.equipment] == [7]
    assert [m.id for m in merged.primary_muscles] == [10]
