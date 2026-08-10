"""Tests for pipeline/cleaner.py: language filtering + normalization/dedup."""

from __future__ import annotations

import pipeline.cleaner as cleaner_module
from pipeline.cleaner import Cleaner
from pipeline.models import EquipmentRef, MediaAsset, PipelineStatistics, RawExerciseRecord, TranslationRecord


def _record_with_videos() -> RawExerciseRecord:
    return RawExerciseRecord(
        id="4",
        uuid="44444444-4444-4444-4444-444444444444",
        source="wger",
        category="Cardio",
        videos=[MediaAsset(uuid="v1", remote_url="https://example.com/v1.mp4")],
        translations=[TranslationRecord(language_code="en", name="Jumping Jack", description="Desc")],
    )


def test_filters_out_languages_not_kept(sample_raw_exercise):
    cleaner = Cleaner(kept_languages=("en", "fr", "ar"))
    stats = PipelineStatistics()

    [cleaned] = cleaner.clean([sample_raw_exercise], stats)

    kept_codes = {t.language_code for t in cleaned.translations}
    assert kept_codes == {"en", "fr"}
    assert stats.translations_removed == 1  # the German translation


def test_drops_exercise_with_no_kept_language_translation():
    record = RawExerciseRecord(
        id="1",
        uuid="11111111-1111-1111-1111-111111111111",
        source="wger",
        category="Cardio",
        translations=[TranslationRecord(language_code="de", name="Foo", description="Bar")],
    )
    cleaner = Cleaner()
    stats = PipelineStatistics()

    result = cleaner.clean([record], stats)

    assert result == []
    assert stats.exercises_cleaned == 0


def test_dedupes_aliases_case_insensitively(sample_raw_exercise):
    cleaner = Cleaner()
    stats = PipelineStatistics()

    [cleaned] = cleaner.clean([sample_raw_exercise], stats)

    en_translation = next(t for t in cleaned.translations if t.language_code == "en")
    assert en_translation.aliases == sorted(["Side Step Jack", "Low Impact Jumping Jack"])
    assert stats.duplicates_removed >= 1


def test_normalizes_whitespace_unicode_and_capitalization():
    record = RawExerciseRecord(
        id="2",
        uuid="22222222-2222-2222-2222-222222222222",
        source="wger",
        category="  Chest  ",
        translations=[TranslationRecord(language_code="en", name="bench   press", description="  Some   text  ")],
    )
    cleaner = Cleaner()
    stats = PipelineStatistics()

    [cleaned] = cleaner.clean([record], stats)

    assert cleaned.category == "Chest"
    en = cleaned.translations[0]
    assert en.name == "Bench Press"
    assert en.description == "Some text"


def test_deduplicates_equipment_by_id():
    record = RawExerciseRecord(
        id="3",
        uuid="33333333-3333-3333-3333-333333333333",
        source="wger",
        category="Chest",
        equipment=[EquipmentRef(id=1, name="Barbell"), EquipmentRef(id=1, name="Barbell")],
        translations=[TranslationRecord(language_code="en", name="Bench Press", description="Desc")],
    )
    cleaner = Cleaner()
    stats = PipelineStatistics()

    [cleaned] = cleaner.clean([record], stats)

    assert len(cleaned.equipment) == 1
    assert stats.duplicates_removed >= 1


def test_videos_stripped_when_disabled(monkeypatch):
    monkeypatch.setattr(cleaner_module, "VIDEOS_ENABLED", False)
    cleaner = Cleaner()
    stats = PipelineStatistics()

    [cleaned] = cleaner.clean([_record_with_videos()], stats)

    assert cleaned.videos == []


def test_videos_kept_when_enabled(monkeypatch):
    monkeypatch.setattr(cleaner_module, "VIDEOS_ENABLED", True)
    cleaner = Cleaner()
    stats = PipelineStatistics()

    [cleaned] = cleaner.clean([_record_with_videos()], stats)

    assert [v.uuid for v in cleaned.videos] == ["v1"]


def test_translations_sorted_in_kept_language_order(sample_raw_exercise):
    cleaner = Cleaner(kept_languages=("en", "fr", "ar"))
    stats = PipelineStatistics()

    [cleaned] = cleaner.clean([sample_raw_exercise], stats)

    assert [t.language_code for t in cleaned.translations] == ["en", "fr"]
