"""GymVisual provider scaffold.

GymVisual is a commercial vendor of exercise illustrations/animations. As
with `providers/exercisedb.py`, its API has not been inspected as part of
this project (only the wger API was in scope) and its schema is
deliberately not guessed at.

This module proves the provider stays pluggable for a media-focused
vendor: registers under `"gymvisual"`, shares the same
`ExerciseProvider` contract, and fails fast with an actionable message
instead of a silent placeholder. See `providers/exercisedb.py` for the
detailed steps required to wire in a real vendor.
"""

from __future__ import annotations

import os
from typing import Iterator

from pipeline.models import RawExerciseRecord
from providers.base import ExerciseProvider, register_provider

GYMVISUAL_BASE_URL = os.environ.get("REPS_GYMVISUAL_BASE_URL", "")
GYMVISUAL_API_KEY = os.environ.get("REPS_GYMVISUAL_API_KEY")


@register_provider("gymvisual")
class GymVisualProvider(ExerciseProvider):
    """Plug-in scaffold for the GymVisual data source (not yet wired up)."""

    def fetch_raw(self) -> None:
        raise NotImplementedError(
            "GymVisualProvider.fetch_raw() is a scaffold. Inspect the live GymVisual API "
            "response shapes first (never assume field names), then implement pagination and "
            "raw-page caching under self.raw_dir, following providers/wger.py as a reference."
        )

    def normalize(self) -> Iterator[RawExerciseRecord]:
        raise NotImplementedError(
            "GymVisualProvider.normalize() is a scaffold. Implement it once fetch_raw() caches "
            "real GymVisual data, mapping its verified field names onto RawExerciseRecord."
        )
