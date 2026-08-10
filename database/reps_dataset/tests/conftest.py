"""Shared pytest fixtures for the REPS pipeline test suite.

Fixtures are built from real field shapes observed on the live wger API
(exercise 1962, "Step Jack") so tests exercise realistic data rather than
synthetic toy values.
"""

from __future__ import annotations

import sys
from pathlib import Path

import pytest

PROJECT_ROOT = Path(__file__).resolve().parent.parent
if str(PROJECT_ROOT) not in sys.path:
    sys.path.insert(0, str(PROJECT_ROOT))

from pipeline.models import (  # noqa: E402
    EquipmentRef,
    LicenseInfo,
    LocalizedText,
    MediaAsset,
    MergedExercise,
    MuscleRef,
    RawExerciseRecord,
    TranslationRecord,
)


@pytest.fixture
def sample_raw_exercise() -> RawExerciseRecord:
    return RawExerciseRecord(
        id="1962",
        uuid="2f10d91f-6c12-471b-bb9e-80840a56ce01",
        source="wger",
        variation_group="",
        category="Cardio",
        equipment=[EquipmentRef(id=7, name="none (bodyweight exercise)")],
        primary_muscles=[MuscleRef(id=10, name="Quadriceps femoris", name_en="Quads", is_front=True)],
        secondary_muscles=[MuscleRef(id=2, name="Anterior deltoid", name_en="Shoulders", is_front=True)],
        translations=[
            TranslationRecord(
                language_code="en",
                name="Step Jack",
                description="<p>English description.</p>",
                aliases=["Side Step Jack", "side step jack", "Low Impact Jumping Jack"],
                source_id=3117,
                created="2026-05-06T07:01:37+02:00",
            ),
            TranslationRecord(
                language_code="fr",
                name="Step Jack",
                description="<p>Description en francais.</p>",
                aliases=[],
                source_id=4394,
                created="2026-06-19T18:42:39+02:00",
            ),
            TranslationRecord(
                language_code="de",
                name="Step Jack",
                description="<p>Deutsche Beschreibung.</p>",
                aliases=[],
                source_id=3556,
                created="2026-06-19T17:51:50+02:00",
            ),
        ],
        images=[
            MediaAsset(
                uuid="74041371-1019-4f89-9ebe-cec792484a46",
                remote_url="https://wger.de/media/exercise-images/1962/74041371-1019-4f89-9ebe-cec792484a46.png",
                is_main=True,
            )
        ],
        videos=[],
        license=LicenseInfo(
            name="Creative Commons Attribution Share Alike 4",
            url="https://creativecommons.org/licenses/by-sa/4.0/deed.en",
        ),
    )


@pytest.fixture
def sample_merged_exercise() -> MergedExercise:
    return MergedExercise(
        id="1962",
        uuid="2f10d91f-6c12-471b-bb9e-80840a56ce01",
        source="wger",
        variation_group="",
        category="Cardio",
        equipment=[EquipmentRef(id=7, name="None (Bodyweight Exercise)")],
        primary_muscles=[MuscleRef(id=10, name="Quadriceps femoris", name_en="Quads", is_front=True)],
        secondary_muscles=[MuscleRef(id=2, name="Anterior deltoid", name_en="Shoulders", is_front=True)],
        name=LocalizedText(en="Step Jack", fr="Step Jack", ar="ستيب جاك"),
        description=LocalizedText(en="English description.", fr="Description en francais.", ar="وصف بالعربية."),
        aliases=["Side Step Jack", "Low Impact Jumping Jack"],
        images=[
            MediaAsset(
                uuid="74041371-1019-4f89-9ebe-cec792484a46",
                remote_url="https://wger.de/media/exercise-images/1962/74041371-1019-4f89-9ebe-cec792484a46.png",
                is_main=True,
                local_path="data/media/images/74041371-1019-4f89-9ebe-cec792484a46.png",
            )
        ],
        videos=[],
        license=LicenseInfo(
            name="Creative Commons Attribution Share Alike 4",
            url="https://creativecommons.org/licenses/by-sa/4.0/deed.en",
        ),
    )
